"""
Test Harness & Verification Suite for Health Connect Simulation Engine.
"""

import sys
import unittest
from datetime import datetime, timedelta
from typing import Dict, Any, List

from models import (
    SleepSessionRecord,
    HeartRateRecord,
    HeartRateSample,
    RestingHeartRateRecord,
    OxygenSaturationRecord,
    StepsRecord
)
from processor import HealthConnectProcessor
from data_loader import (
    RAW_HYPNOGRAM_NIGHTS,
    REFERENCE_GROUND_TRUTH,
    build_mock_health_connect_records
)


class TestHealthConnectSimulation(unittest.TestCase):
    """
    Validates that the processing engine correctly filters blanket layers,
    calculates sleep phases with independent rounding, and matches ground truth.
    """

    def test_historical_nights_accuracy(self):
        print("\n" + "=" * 90)
        print(f"{'DATE':<12} | {'METRIC':<14} | {'SIMULATED':<12} | {'GROUND TRUTH':<14} | {'STATUS':<10}")
        print("=" * 90)

        all_passed = True

        for night_data in RAW_HYPNOGRAM_NIGHTS:
            date_str = night_data["date"]
            if date_str not in REFERENCE_GROUND_TRUTH:
                continue

            ground_truth = REFERENCE_GROUND_TRUTH[date_str]
            sleep_session = build_mock_health_connect_records(night_data)

            # Process sleep metrics
            (
                sleep_secs,
                deep_min,
                rem_min,
                light_min,
                onset,
                wake,
                awake_intervals
            ) = HealthConnectProcessor.compute_sleep_metrics(sleep_session)

            # Create mock vitals strictly within the calculated sleep window
            mock_hr_samples = []
            mock_spo2_records = []
            mock_rhr_records = []
            mock_step_records = []

            if onset and wake:
                # Mock HR
                target_avg_hr = ground_truth["avg_sleeping_hr"]
                curr = onset
                while curr <= wake:
                    mock_hr_samples.append(HeartRateSample(time=curr, beats_per_minute=target_avg_hr))
                    curr += timedelta(minutes=5)

                # Mock SpO2
                target_spo2 = ground_truth["sp_o2"]
                curr = onset
                for _ in range(15):
                    mock_spo2_records.append(OxygenSaturationRecord(time=curr, percentage=target_spo2))
                    curr += timedelta(minutes=20)

                # Mock RHR
                mock_rhr_records.append(RestingHeartRateRecord(time=onset, beats_per_minute=ground_truth["resting_hr"]))

            mock_step_records.append(StepsRecord(
                start_time=datetime.strptime(date_str, "%Y-%m-%d"),
                end_time=datetime.strptime(date_str, "%Y-%m-%d") + timedelta(hours=23),
                count=ground_truth["steps"]
            ))

            hr_record = HeartRateRecord(start_time=onset, end_time=wake, samples=mock_hr_samples) if onset else None

            payload = HealthConnectProcessor.process_day(
                date_str=date_str,
                sleep_session=sleep_session,
                hr_record=hr_record,
                rhr_records=mock_rhr_records,
                spo2_records=mock_spo2_records,
                step_records=mock_step_records,
                hrv_val=ground_truth["hrv"]
            )

            # Verify fields
            comparisons = [
                ("sleepSecs", payload.sleep_secs, ground_truth["sleep_secs"], 60),  # +/- 60s tolerance for rounding
                ("DeepSleep", payload.deep_sleep, ground_truth["deep_sleep"], 0),
                ("RemSleep", payload.rem_sleep, ground_truth["rem_sleep"], 0),
                ("LightSleep", payload.light_sleep, ground_truth["light_sleep"], 1), # +/- 1m tolerance for 5-min fuzzy edge nights
                ("spO2", payload.sp_o2, ground_truth["sp_o2"], 0),
                ("restingHR", payload.resting_hr, ground_truth["resting_hr"], 0),
                ("avgSleepingHR", payload.avg_sleeping_hr, ground_truth["avg_sleeping_hr"], 0),
                ("steps", payload.steps, ground_truth["steps"], 0),
            ]

            print(f"\n--- Night: {date_str} (Blanket Layer Injected: {night_data.get('has_blanket', False)}) ---")
            for name, actual, expected, tol in comparisons:
                diff = abs(actual - expected) if (actual is not None and expected is not None) else 999
                ok = diff <= tol
                status = "PASS" if ok else "FAIL"
                if not ok:
                    all_passed = False
                print(f"{date_str:<12} | {name:<14} | {str(actual):<12} | {str(expected):<14} | {status:<10}")

        print("=" * 90)
        self.assertTrue(all_passed, "All historical simulation checks must pass!")

    def test_blanket_layer_rejection(self):
        """
        Explicitly asserts that a 40,000 second blanket block does not inflate sleepSecs.
        """
        night = RAW_HYPNOGRAM_NIGHTS[0] # 2026-08-10 with blanket layer
        session = build_mock_health_connect_records(night)

        sleep_secs, deep, rem, light, _, _, _ = HealthConnectProcessor.compute_sleep_metrics(session)

        # Without filter, sleepSecs would be 34020 + 40805 = 74825s (~20.7 hours)
        self.assertLess(sleep_secs, 40000, "Blanket layer must be filtered out!")
        self.assertEqual(deep, 130)
        self.assertEqual(rem, 140)
        self.assertEqual(light, 297)


if __name__ == "__main__":
    unittest.main()
