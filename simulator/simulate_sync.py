#!/usr/bin/env python3
"""
Simulate Sync CLI: Inspects data calculations, formats payload, and provides dry-run / live sync to Intervals.icu.
"""

import argparse
import json
import os
import sys
from datetime import datetime, timedelta
import urllib.request
import base64

from models import (
    SleepSessionRecord,
    HeartRateRecord,
    HeartRateSample,
    RestingHeartRateRecord,
    OxygenSaturationRecord,
    StepsRecord
)
from processor import HealthConnectProcessor
from data_loader import RAW_HYPNOGRAM_NIGHTS, REFERENCE_GROUND_TRUTH, build_mock_health_connect_records

DEFAULT_ATHLETE_ID = os.environ.get("INTERVALS_ATHLETE_ID", "")
DEFAULT_API_KEY = os.environ.get("INTERVALS_API_KEY", "")


def push_to_intervals(payload_dict: dict, athlete_id: str, api_key: str, date_str: str) -> bool:
    url = f"https://intervals.icu/api/v1/athlete/{athlete_id}/wellness/{date_str}"
    data_bytes = json.dumps(payload_dict).encode("utf-8")

    # Basic auth: API_KEY:<apiKey>
    auth_str = f"API_KEY:{api_key}"
    auth_b64 = base64.b64encode(auth_str.encode("utf-8")).decode("ascii")

    req = urllib.request.Request(
        url,
        data=data_bytes,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Basic {auth_b64}"
        },
        method="PUT"
    )

    try:
        with urllib.request.urlopen(req) as resp:
            print(f"✅ Successfully pushed {date_str} to Intervals.icu! HTTP {resp.status}")
            return True
    except Exception as e:
        print(f"❌ Error uploading to Intervals.icu: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Health Connect to Intervals.icu Simulation CLI")
    parser.add_argument("--date", type=str, default="2026-08-21", help="Target date to simulate (YYYY-MM-DD)")
    parser.add_argument("--live", action="store_true", help="Send actual PUT request to Intervals.icu")
    parser.add_argument("--athlete", type=str, default=DEFAULT_ATHLETE_ID, help="Intervals.icu Athlete ID")
    parser.add_argument("--api-key", type=str, default=DEFAULT_API_KEY, help="Intervals.icu API Key")
    args = parser.parse_args()

    # Find night data
    night = next((n for n in RAW_HYPNOGRAM_NIGHTS if n["date"] == args.date), None)
    if not night:
        print(f"No mock data found for date {args.date}. Available: {[n['date'] for n in RAW_HYPNOGRAM_NIGHTS]}")
        sys.exit(1)

    print(f"=== Simulating Health Connect Processing for {args.date} ===")
    session = build_mock_health_connect_records(night)

    gt = REFERENCE_GROUND_TRUTH.get(args.date, {})
    rhr = [RestingHeartRateRecord(time=session.start_time, beats_per_minute=gt.get("resting_hr", 52))]
    spo2 = [OxygenSaturationRecord(time=session.start_time + timedelta(minutes=i*15), percentage=gt.get("sp_o2", 98.0)) for i in range(20)]
    steps = [StepsRecord(start_time=session.start_time, end_time=session.end_time, count=gt.get("steps", 3000))]

    payload = HealthConnectProcessor.process_day(
        date_str=args.date,
        sleep_session=session,
        rhr_records=rhr,
        spo2_records=spo2,
        step_records=steps,
        hrv_val=gt.get("hrv")
    )

    payload_json = payload.to_json_dict()
    print("\n--- Generated Intervals.icu JSON Payload ---")
    print(json.dumps(payload_json, indent=2))

    if args.live:
        print("\nSending live request to Intervals.icu...")
        push_to_intervals(payload_json, args.athlete, args.api_key, args.date)
    else:
        print("\n[Dry Run Mode] Pass --live to send to Intervals.icu.")


if __name__ == "__main__":
    main()
