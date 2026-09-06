"""
Data models mirroring Android Health Connect SDK classes and Intervals.icu API.
"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import List, Optional, Dict, Any


class SleepStageType(str, Enum):
    UNKNOWN = "unknown"
    AWAKE = "awake"
    LIGHT = "light"
    DEEP = "deep"
    REM = "rem"
    SLEEPING = "sleeping"  # Generic / blanket stage
    OUT_OF_BED = "out_of_bed"


@dataclass
class SleepStage:
    start_time: datetime
    end_time: datetime
    stage: SleepStageType

    @property
    def duration_seconds(self) -> int:
        return int((self.end_time - self.start_time).total_seconds())


@dataclass
class SleepSessionRecord:
    id: str
    start_time: datetime
    end_time: datetime
    stages: List[SleepStage] = field(default_factory=list)
    title: Optional[str] = None
    notes: Optional[str] = None

    @property
    def duration_seconds(self) -> int:
        return int((self.end_time - self.start_time).total_seconds())


@dataclass
class HeartRateSample:
    time: datetime
    beats_per_minute: int


@dataclass
class HeartRateRecord:
    start_time: datetime
    end_time: datetime
    samples: List[HeartRateSample] = field(default_factory=list)


@dataclass
class RestingHeartRateRecord:
    time: datetime
    beats_per_minute: int


@dataclass
class OxygenSaturationRecord:
    time: datetime
    percentage: float  # e.g., 98.0


@dataclass
class StepsRecord:
    start_time: datetime
    end_time: datetime
    count: int


@dataclass
class IntervalsWellnessPayload:
    id: str  # YYYY-MM-DD
    sleep_secs: Optional[int] = None
    resting_hr: Optional[int] = None
    avg_sleeping_hr: Optional[int] = None
    sp_o2: Optional[float] = None
    steps: Optional[int] = None
    hrv: Optional[float] = None
    deep_sleep: Optional[int] = None   # Custom field (minutes)
    rem_sleep: Optional[int] = None    # Custom field (minutes)
    light_sleep: Optional[int] = None  # Custom field (minutes)

    def to_json_dict(self) -> Dict[str, Any]:
        """Convert to Intervals.icu payload format."""
        d = {}
        if self.sleep_secs is not None:
            d["sleepSecs"] = self.sleep_secs
        if self.resting_hr is not None:
            d["restingHR"] = self.resting_hr
        if self.avg_sleeping_hr is not None:
            d["avgSleepingHR"] = self.avg_sleeping_hr
        if self.sp_o2 is not None:
            d["spO2"] = self.sp_o2
        if self.steps is not None:
            d["steps"] = self.steps
        if self.hrv is not None:
            d["hrv"] = self.hrv
        if self.deep_sleep is not None:
            d["DeepSleep"] = self.deep_sleep
        if self.rem_sleep is not None:
            d["RemSleep"] = self.rem_sleep
        if self.light_sleep is not None:
            d["LightSleep"] = self.light_sleep
        return d
