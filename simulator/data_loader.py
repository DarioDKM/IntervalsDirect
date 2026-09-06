"""
Data Loader & Test Fixtures: Loads raw Health Connect segment data and reference ground truth.
"""

from datetime import datetime, timedelta
from typing import List, Dict, Any
from models import (
    SleepSessionRecord,
    SleepStage,
    SleepStageType,
    HeartRateRecord,
    HeartRateSample,
    RestingHeartRateRecord,
    OxygenSaturationRecord,
    StepsRecord
)

# Raw segments from historical hypnogram data (including blanket layer flag)
RAW_HYPNOGRAM_NIGHTS = [
    {
        "date": "2026-08-10",
        "start": "2026-08-10T00:27:59",
        "end": "2026-08-10T11:48:04",
        "has_blanket": True,
        "blanket_duration": 40805, # Overarching blanket session
        "segs": [
            ["2026-08-10T00:27:59", 1952, "awake"],
            ["2026-08-10T01:00:31", 300, "light"],
            ["2026-08-10T01:05:31", 1800, "deep"],
            ["2026-08-10T01:35:31", 300, "light"],
            ["2026-08-10T01:40:31", 4050, "deep"],
            ["2026-08-10T02:48:01", 150, "awake"],
            ["2026-08-10T02:50:31", 300, "light"],
            ["2026-08-10T02:55:31", 1050, "deep"],
            ["2026-08-10T03:13:01", 150, "awake"],
            ["2026-08-10T03:15:31", 900, "light"],
            ["2026-08-10T03:30:31", 300, "deep"],
            ["2026-08-10T03:35:31", 300, "light"],
            ["2026-08-10T03:40:31", 300, "deep"],
            ["2026-08-10T03:45:31", 3000, "light"],
            ["2026-08-10T04:35:31", 300, "awake"],
            ["2026-08-10T04:40:31", 300, "light"],
            ["2026-08-10T04:45:31", 300, "deep"],
            ["2026-08-10T04:50:31", 300, "light"],
            ["2026-08-10T04:55:31", 300, "awake"],
            ["2026-08-10T05:00:31", 900, "light"],
            ["2026-08-10T05:15:31", 1500, "rem"],
            ["2026-08-10T05:40:31", 2700, "light"],
            ["2026-08-10T06:25:31", 600, "rem"],
            ["2026-08-10T06:35:31", 300, "light"],
            ["2026-08-10T06:40:31", 300, "rem"],
            ["2026-08-10T06:45:31", 1800, "light"],
            ["2026-08-10T07:15:31", 2400, "rem"],
            ["2026-08-10T07:55:31", 600, "light"],
            ["2026-08-10T08:05:31", 300, "awake"],
            ["2026-08-10T08:10:31", 300, "light"],
            ["2026-08-10T08:15:31", 600, "rem"],
            ["2026-08-10T08:25:31", 1200, "light"],
            ["2026-08-10T08:45:31", 150, "awake"],
            ["2026-08-10T08:48:01", 2852, "light"],
            ["2026-08-10T09:35:33", 300, "awake"],
            ["2026-08-10T09:40:33", 300, "light"],
            ["2026-08-10T09:45:33", 900, "rem"],
            ["2026-08-10T10:00:33", 300, "light"],
            ["2026-08-10T10:05:33", 1200, "rem"],
            ["2026-08-10T10:25:33", 300, "light"],
            ["2026-08-10T10:30:33", 900, "rem"],
            ["2026-08-10T10:45:33", 596, "light"],
            ["2026-08-10T10:55:29", 3155, "awake"]
        ]
    },
    {
        "date": "2026-08-11",
        "start": "2026-08-11T00:27:01",
        "end": "2026-08-11T11:04:31",
        "has_blanket": True,
        "blanket_duration": 38250,
        "segs": [
            ["2026-08-11T00:27:01", 3600, "awake"],
            ["2026-08-11T01:27:01", 300, "light"],
            ["2026-08-11T01:32:01", 2550, "deep"],
            ["2026-08-11T02:14:31", 300, "awake"],
            ["2026-08-11T02:19:31", 450, "light"],
            ["2026-08-11T02:27:01", 750, "deep"],
            ["2026-08-11T02:39:31", 150, "awake"],
            ["2026-08-11T02:42:01", 600, "light"],
            ["2026-08-11T02:52:01", 1200, "deep"],
            ["2026-08-11T03:12:01", 1200, "light"],
            ["2026-08-11T03:32:01", 300, "deep"],
            ["2026-08-11T03:37:01", 300, "awake"],
            ["2026-08-11T03:42:01", 300, "light"],
            ["2026-08-11T03:47:01", 1500, "deep"],
            ["2026-08-11T04:12:01", 600, "light"],
            ["2026-08-11T04:22:01", 900, "deep"],
            ["2026-08-11T04:37:01", 900, "light"],
            ["2026-08-11T04:52:01", 150, "awake"],
            ["2026-08-11T04:54:31", 3900, "light"],
            ["2026-08-11T05:59:31", 150, "awake"],
            ["2026-08-11T06:02:01", 300, "light"],
            ["2026-08-11T06:07:01", 1200, "rem"],
            ["2026-08-11T06:27:01", 5400, "light"],
            ["2026-08-11T07:57:01", 3900, "rem"],
            ["2026-08-11T09:02:01", 600, "light"],
            ["2026-08-11T09:12:01", 900, "rem"],
            ["2026-08-11T09:27:01", 300, "light"],
            ["2026-08-11T09:32:01", 600, "rem"],
            ["2026-08-11T09:42:01", 300, "light"],
            ["2026-08-11T09:47:01", 900, "rem"],
            ["2026-08-11T10:02:01", 300, "awake"],
            ["2026-08-11T10:07:01", 300, "light"],
            ["2026-08-11T10:12:01", 1800, "rem"],
            ["2026-08-11T10:42:01", 1350, "awake"]
        ]
    },
    {
        "date": "2026-08-13",
        "start": "2026-08-13T00:45:01",
        "end": "2026-08-13T11:40:01",
        "has_blanket": True,
        "blanket_duration": 39300,
        "segs": [
            ["2026-08-13T00:45:01", 900, "awake"],
            ["2026-08-13T01:00:01", 300, "light"],
            ["2026-08-13T01:05:01", 2550, "deep"],
            ["2026-08-13T01:47:31", 150, "awake"],
            ["2026-08-13T01:50:01", 600, "light"],
            ["2026-08-13T02:00:01", 900, "deep"],
            ["2026-08-13T02:15:01", 150, "awake"],
            ["2026-08-13T02:17:31", 450, "light"],
            ["2026-08-13T02:25:01", 2400, "deep"],
            ["2026-08-13T03:05:01", 300, "awake"],
            ["2026-08-13T03:10:01", 300, "light"],
            ["2026-08-13T03:15:01", 900, "deep"],
            ["2026-08-13T03:30:01", 300, "light"],
            ["2026-08-13T03:35:01", 300, "deep"],
            ["2026-08-13T03:40:01", 300, "light"],
            ["2026-08-13T03:45:01", 300, "deep"],
            ["2026-08-13T03:50:01", 450, "light"],
            ["2026-08-13T03:57:31", 150, "awake"],
            ["2026-08-13T04:00:01", 2250, "light"],
            ["2026-08-13T04:37:31", 150, "awake"],
            ["2026-08-13T04:40:01", 1350, "light"],
            ["2026-08-13T05:02:31", 150, "awake"],
            ["2026-08-13T05:05:01", 1800, "light"],
            ["2026-08-13T05:35:01", 300, "rem"],
            ["2026-08-13T05:40:01", 3600, "light"],
            ["2026-08-13T06:40:01", 300, "rem"],
            ["2026-08-13T06:45:01", 3300, "light"],
            ["2026-08-13T07:40:01", 900, "rem"],
            ["2026-08-13T07:55:01", 900, "light"],
            ["2026-08-13T08:10:01", 1500, "rem"],
            ["2026-08-13T08:35:01", 300, "light"],
            ["2026-08-13T08:40:01", 900, "rem"],
            ["2026-08-13T08:55:01", 150, "awake"],
            ["2026-08-13T08:57:31", 2550, "light"],
            ["2026-08-13T09:40:01", 3600, "rem"],
            ["2026-08-13T10:40:01", 300, "light"],
            ["2026-08-13T10:45:01", 1800, "rem"],
            ["2026-08-13T11:15:01", 1500, "awake"]
        ]
    },
    {
        "date": "2026-08-15",
        "start": "2026-08-15T00:34:07",
        "end": "2026-08-15T06:06:37",
        "has_blanket": False,
        "segs": [
            ["2026-08-15T00:34:07", 3300, "awake"],
            ["2026-08-15T01:29:07", 301, "light"],
            ["2026-08-15T01:34:08", 150, "awake"],
            ["2026-08-15T01:36:38", 450, "light"],
            ["2026-08-15T01:44:08", 600, "deep"],
            ["2026-08-15T01:54:08", 300, "light"],
            ["2026-08-15T01:59:08", 900, "deep"],
            ["2026-08-15T02:14:08", 300, "light"],
            ["2026-08-15T02:19:08", 450, "deep"],
            ["2026-08-15T02:26:38", 150, "awake"],
            ["2026-08-15T02:29:08", 300, "light"],
            ["2026-08-15T02:34:08", 300, "deep"],
            ["2026-08-15T02:39:08", 300, "light"],
            ["2026-08-15T02:44:08", 150, "awake"],
            ["2026-08-15T02:46:38", 1050, "light"],
            ["2026-08-15T03:04:08", 300, "deep"],
            ["2026-08-15T03:09:08", 750, "light"],
            ["2026-08-15T03:21:38", 150, "awake"],
            ["2026-08-15T03:24:08", 600, "light"],
            ["2026-08-15T03:34:08", 900, "rem"],
            ["2026-08-15T03:49:08", 2700, "light"],
            ["2026-08-15T04:34:08", 300, "rem"],
            ["2026-08-15T04:39:08", 300, "light"],
            ["2026-08-15T04:44:08", 300, "awake"],
            ["2026-08-15T04:49:08", 2400, "light"],
            ["2026-08-15T05:29:08", 2099, "rem"],
            ["2026-08-15T06:04:07", 150, "awake"]
        ]
    },
    {
        "date": "2026-08-18",
        "start": "2026-08-17T23:52:39",
        "end": "2026-08-18T08:12:39",
        "has_blanket": True,
        "blanket_duration": 30000,
        "segs": [
            ["2026-08-17T23:52:39", 450, "awake"],
            ["2026-08-18T00:00:09", 300, "light"],
            ["2026-08-18T00:05:09", 300, "deep"],
            ["2026-08-18T00:10:09", 300, "light"],
            ["2026-08-18T00:15:09", 450, "deep"],
            ["2026-08-18T00:22:39", 150, "awake"],
            ["2026-08-18T00:25:09", 600, "light"],
            ["2026-08-18T00:35:09", 1200, "deep"],
            ["2026-08-18T00:55:09", 150, "awake"],
            ["2026-08-18T00:57:39", 450, "light"],
            ["2026-08-18T01:05:09", 900, "deep"],
            ["2026-08-18T01:20:09", 300, "awake"],
            ["2026-08-18T01:25:09", 300, "light"],
            ["2026-08-18T01:30:09", 1500, "deep"],
            ["2026-08-18T01:55:09", 300, "awake"],
            ["2026-08-18T02:00:09", 600, "light"],
            ["2026-08-18T02:10:09", 750, "deep"],
            ["2026-08-18T02:22:39", 150, "awake"],
            ["2026-08-18T02:25:09", 300, "light"],
            ["2026-08-18T02:30:09", 300, "deep"],
            ["2026-08-18T02:35:09", 1500, "light"],
            ["2026-08-18T03:00:09", 300, "rem"],
            ["2026-08-18T03:05:09", 5400, "light"],
            ["2026-08-18T04:35:09", 3000, "rem"],
            ["2026-08-18T05:25:09", 2700, "light"],
            ["2026-08-18T06:10:09", 2700, "rem"],
            ["2026-08-18T06:55:09", 300, "light"],
            ["2026-08-18T07:00:09", 600, "rem"],
            ["2026-08-18T07:10:09", 600, "light"],
            ["2026-08-18T07:20:09", 900, "rem"],
            ["2026-08-18T07:35:09", 300, "awake"],
            ["2026-08-18T07:40:09", 600, "light"],
            ["2026-08-18T07:50:09", 300, "rem"],
            ["2026-08-18T07:55:09", 600, "light"],
            ["2026-08-18T08:05:09", 300, "rem"],
            ["2026-08-18T08:10:09", 150, "awake"]
        ]
    },
    {
        "date": "2026-08-21",
        "start": "2026-08-21T00:21:26",
        "end": "2026-08-21T10:33:56",
        "has_blanket": True,
        "blanket_duration": 36750,
        "segs": [
            ["2026-08-21T00:21:26", 600, "awake"],
            ["2026-08-21T00:31:26", 6900, "deep"],
            ["2026-08-21T02:26:26", 20250, "light"],
            ["2026-08-21T08:03:56", 5850, "rem"],
            ["2026-08-21T09:41:26", 1140, "awake"],
            ["2026-08-21T10:00:26", 2010, "awake"]
        ]
    }
]

# Verified ground-truth data from ringconn-referenz.md
REFERENCE_GROUND_TRUTH: Dict[str, Dict[str, Any]] = {
    "2026-08-10": {
        "sleep_secs": 34020,
        "deep_sleep": 130,
        "rem_sleep": 140,
        "light_sleep": 297,
        "steps": 2085,
        "hrv": 63.0,
        "sp_o2": 97.0,
        "resting_hr": 43,
        "avg_sleeping_hr": 62
    },
    "2026-08-11": {
        "sleep_secs": 31980,
        "deep_sleep": 120,
        "rem_sleep": 155,
        "light_sleep": 258,
        "steps": 3931,
        "hrv": 49.0,
        "sp_o2": 96.0,
        "resting_hr": 44,
        "avg_sleeping_hr": 69
    },
    "2026-08-13": {
        "sleep_secs": 35700,
        "deep_sleep": 122,
        "rem_sleep": 155,
        "light_sleep": 318,
        "steps": 5248,
        "hrv": 62.0,
        "sp_o2": 96.0,
        "resting_hr": 43,
        "avg_sleeping_hr": 61
    },
    "2026-08-15": {
        "sleep_secs": 15600,
        "deep_sleep": 42,
        "rem_sleep": 55,
        "light_sleep": 163,
        "steps": 11400,
        "hrv": 64.0,
        "sp_o2": 97.0,
        "resting_hr": 43,
        "avg_sleeping_hr": 62
    },
    "2026-08-18": {
        "sleep_secs": 28020,
        "deep_sleep": 90,
        "rem_sleep": 135,
        "light_sleep": 242,
        "steps": 2853,
        "hrv": 58.0,
        "sp_o2": 97.0,
        "resting_hr": 42,
        "avg_sleeping_hr": 70
    },
    "2026-08-21": {
        "sleep_secs": 33000, # 33000s in intervals.icu, 33060 in raw rounded min sum
        "deep_sleep": 115,
        "rem_sleep": 98,
        "light_sleep": 338,
        "steps": 3239,
        "hrv": 54.0,
        "sp_o2": 98.0,
        "resting_hr": 46,
        "avg_sleeping_hr": 54
    }
}


def build_mock_health_connect_records(night_data: Dict[str, Any]) -> SleepSessionRecord:
    """
    Constructs a simulated Health Connect SleepSessionRecord with both valid stages
    and (if applicable) the problematic blanket summary layer.
    """
    session_start = datetime.fromisoformat(night_data["start"])
    session_end = datetime.fromisoformat(night_data["end"])

    stages: List[SleepStage] = []

    # If night has a blanket layer, inject it (as Health Sync / RingConn sometimes does)
    if night_data.get("has_blanket"):
        blanket_dur = night_data.get("blanket_duration", 35000)
        stages.append(SleepStage(
            start_time=session_start,
            end_time=session_start + timedelta(seconds=blanket_dur),
            stage=SleepStageType.LIGHT
        ))

    # Add detailed segments
    for start_iso, dur_secs, stage_str in night_data["segs"]:
        st = datetime.fromisoformat(start_iso)
        et = st + timedelta(seconds=dur_secs)
        stage_enum = {
            "deep": SleepStageType.DEEP,
            "rem": SleepStageType.REM,
            "light": SleepStageType.LIGHT,
            "awake": SleepStageType.AWAKE
        }.get(stage_str, SleepStageType.UNKNOWN)

        stages.append(SleepStage(
            start_time=st,
            end_time=et,
            stage=stage_enum
        ))

    return SleepSessionRecord(
        id=f"session_{night_data['date']}",
        start_time=session_start,
        end_time=session_end,
        stages=stages
    )
