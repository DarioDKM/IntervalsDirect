"""
Core Processing Engine: Transforms Health Connect Records into Intervals.icu Wellness Metrics.
"""

from datetime import datetime, timedelta
from typing import List, Optional, Tuple, Dict
from models import (
    SleepSessionRecord,
    SleepStage,
    SleepStageType,
    HeartRateRecord,
    RestingHeartRateRecord,
    OxygenSaturationRecord,
    StepsRecord,
    IntervalsWellnessPayload
)


def round_half_up(n: float) -> int:
    """Standard commercial rounding (kaufmännische Rundung)."""
    return int(n + 0.5) if n >= 0 else int(n - 0.5)


class HealthConnectProcessor:
    """
    Implements exact data processing and sanitation rules for RingConn -> Health Connect -> Intervals.icu.
    """

    @staticmethod
    def clean_sleep_stages(
        stages: List[SleepStage],
        session_start: Optional[datetime] = None,
        session_end: Optional[datetime] = None
    ) -> List[SleepStage]:
        """
        Strips blanket summary blocks and deduplicates/chains overlapping segments.
        """
        if not stages:
            return []

        session_duration = (session_end - session_start).total_seconds() if (session_start and session_end) else 0

        # Check if detailed breakdown exists (presence of multiple stages or deep/rem)
        has_detailed_stages = any(s.stage in (SleepStageType.DEEP, SleepStageType.REM) for s in stages)

        valid_stages = []
        for s in stages:
            dur = s.duration_seconds
            is_blanket = False

            # Blanket detection rule:
            # If detailed stages exist, a stage is a blanket summary if it covers >= 80% of the entire session
            # or if its duration is >= 25,000s and spans from session start to near session end.
            if has_detailed_stages and session_duration > 0:
                if dur >= 0.80 * session_duration and s.stage in (SleepStageType.LIGHT, SleepStageType.SLEEPING):
                    # Check if it starts close to session start
                    if session_start and abs((s.start_time - session_start).total_seconds()) < 600:
                        is_blanket = True

            if not is_blanket:
                valid_stages.append(s)

        # 2. Sort by start_time
        valid_stages.sort(key=lambda x: (x.start_time, -x.duration_seconds))

        # 3. Greedy chaining to resolve overlapping segments
        chained_stages: List[SleepStage] = []
        current_cursor: Optional[datetime] = None

        for s in valid_stages:
            if current_cursor is None or s.start_time >= current_cursor:
                chained_stages.append(s)
                current_cursor = s.end_time
            elif s.end_time > current_cursor:
                # Partially overlapping: truncate start to cursor
                adjusted_stage = SleepStage(
                    start_time=current_cursor,
                    end_time=s.end_time,
                    stage=s.stage
                )
                if adjusted_stage.duration_seconds > 0:
                    chained_stages.append(adjusted_stage)
                    current_cursor = s.end_time

        return chained_stages

    @classmethod
    def compute_sleep_metrics(
        cls,
        session: SleepSessionRecord
    ) -> Tuple[int, int, int, int, Optional[datetime], Optional[datetime], List[Tuple[datetime, datetime]]]:
        """
        Computes:
        - sleep_secs (total seconds in DEEP, REM, LIGHT)
        - deep_minutes (rounded)
        - rem_minutes (rounded)
        - light_minutes (rounded)
        - sleep_onset (datetime of first non-awake stage)
        - sleep_wake (datetime of last non-awake stage)
        - awake_intervals (list of (start, end) for awake periods)
        """
        cleaned_stages = cls.clean_sleep_stages(session.stages, session.start_time, session.end_time)

        deep_secs = 0
        rem_secs = 0
        light_secs = 0
        awake_intervals = []

        sleep_onset = None
        sleep_wake = None

        for s in cleaned_stages:
            dur = s.duration_seconds
            if s.stage == SleepStageType.DEEP:
                deep_secs += dur
                if sleep_onset is None:
                    sleep_onset = s.start_time
                sleep_wake = s.end_time
            elif s.stage == SleepStageType.REM:
                rem_secs += dur
                if sleep_onset is None:
                    sleep_onset = s.start_time
                sleep_wake = s.end_time
            elif s.stage == SleepStageType.LIGHT:
                light_secs += dur
                if sleep_onset is None:
                    sleep_onset = s.start_time
                sleep_wake = s.end_time
            elif s.stage == SleepStageType.AWAKE:
                awake_intervals.append((s.start_time, s.end_time))

        sleep_secs = deep_secs + rem_secs + light_secs

        # Rule: Each phase is rounded INDEPENDENTLY to whole minutes (round-half-to-even)
        deep_min = round(deep_secs / 60.0)
        rem_min = round(rem_secs / 60.0)
        light_min = round(light_secs / 60.0)

        return sleep_secs, deep_min, rem_min, light_min, sleep_onset, sleep_wake, awake_intervals

    @staticmethod
    def compute_avg_sleeping_hr(
        hr_record: Optional[HeartRateRecord],
        sleep_onset: Optional[datetime],
        sleep_wake: Optional[datetime],
        awake_intervals: List[Tuple[datetime, datetime]]
    ) -> Optional[int]:
        """
        Computes average heart rate during sleep window, strictly excluding awake intervals.
        """
        if not hr_record or not sleep_onset or not sleep_wake:
            return None

        sleeping_samples = []
        for sample in hr_record.samples:
            t = sample.time
            if sleep_onset <= t <= sleep_wake:
                # Check if within any awake interval
                is_awake = any(start <= t <= end for start, end in awake_intervals)
                if not is_awake:
                    sleeping_samples.append(sample.beats_per_minute)

        if not sleeping_samples:
            return None

        avg_bpm = sum(sleeping_samples) / len(sleeping_samples)
        return round_half_up(avg_bpm)

    @staticmethod
    def compute_sleeping_spo2(
        spo2_records: List[OxygenSaturationRecord],
        sleep_onset: Optional[datetime],
        sleep_wake: Optional[datetime],
        min_samples: int = 10
    ) -> Optional[float]:
        """
        Computes average SpO2 during sleep window. Requires >= min_samples.
        """
        if not spo2_records or not sleep_onset or not sleep_wake:
            return None

        samples_in_window = [
            r.percentage for r in spo2_records
            if sleep_onset <= r.time <= sleep_wake
        ]

        if len(samples_in_window) < min_samples:
            return None

        avg_val = sum(samples_in_window) / len(samples_in_window)
        return float(round_half_up(avg_val))

    @staticmethod
    def extract_resting_hr(
        rhr_records: List[RestingHeartRateRecord],
        sleep_onset: Optional[datetime]
    ) -> Optional[int]:
        """
        Gets resting HR record corresponding to sleep onset.
        If multiple records, chooses the lowest value or the one closest to sleep onset.
        """
        if not rhr_records:
            return None

        # If sleep onset is provided, filter records within +/- 4 hours of onset
        if sleep_onset:
            candidates = [
                r for r in rhr_records
                if abs((r.time - sleep_onset).total_seconds()) <= 14400
            ]
            if candidates:
                return min(r.beats_per_minute for r in candidates)

        return min(r.beats_per_minute for r in rhr_records)

    @staticmethod
    def compute_daily_steps(
        step_records: List[StepsRecord],
        target_date: datetime.date
    ) -> Optional[int]:
        """
        Sums steps for the given date.
        """
        if not step_records:
            return None

        total = sum(
            r.count for r in step_records
            if r.start_time.date() == target_date
        )
        return total if total > 0 else None

    @classmethod
    def process_day(
        cls,
        date_str: str,  # YYYY-MM-DD
        sleep_session: Optional[SleepSessionRecord] = None,
        hr_record: Optional[HeartRateRecord] = None,
        rhr_records: Optional[List[RestingHeartRateRecord]] = None,
        spo2_records: Optional[List[OxygenSaturationRecord]] = None,
        step_records: Optional[List[StepsRecord]] = None,
        hrv_val: Optional[float] = None
    ) -> IntervalsWellnessPayload:
        """
        Full pipeline to assemble the IntervalsWellnessPayload for a given date.
        """
        target_date = datetime.strptime(date_str, "%Y-%m-%d").date()

        sleep_secs, deep_min, rem_min, light_min = None, None, None, None
        sleep_onset, sleep_wake = None, None
        awake_intervals = []

        if sleep_session:
            (
                sleep_secs,
                deep_min,
                rem_min,
                light_min,
                sleep_onset,
                sleep_wake,
                awake_intervals
            ) = cls.compute_sleep_metrics(sleep_session)

        avg_sleeping_hr = cls.compute_avg_sleeping_hr(
            hr_record, sleep_onset, sleep_wake, awake_intervals
        )

        spo2 = cls.compute_sleeping_spo2(
            spo2_records or [], sleep_onset, sleep_wake
        )

        resting_hr = cls.extract_resting_hr(
            rhr_records or [], sleep_onset
        )

        steps = cls.compute_daily_steps(
            step_records or [], target_date
        )

        return IntervalsWellnessPayload(
            id=date_str,
            sleep_secs=sleep_secs,
            resting_hr=resting_hr,
            avg_sleeping_hr=avg_sleeping_hr,
            sp_o2=spo2,
            steps=steps,
            hrv=hrv_val,
            deep_sleep=deep_min,
            rem_sleep=rem_min,
            light_sleep=light_min
        )
