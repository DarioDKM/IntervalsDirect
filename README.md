# Intervals Direct

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)

A native Android application (Kotlin + Jetpack Compose) that connects to a Morphe-patched **RingConn** application, reads high-resolution sleep and recovery vitals directly from the local database via ContentProvider, and synchronizes them to **Intervals.icu**.

100% on-device, zero third-party cloud dependencies, and zero subscription fees.

---

## Overview

Smart rings like RingConn record comprehensive physiological data including sleep stages, resting heart rate, heart rate variability (rMSSD), skin temperature deviation, and daily steps. However, standard synchronization options often involve manual CSV exports, lossy third-party intermediaries, or closed ecosystems.

**Intervals Direct** solves this by establishing a direct on-device pipeline:
1. The official RingConn APK is patched (via **Morphe** or LSPatch) to expose an internal ContentProvider (`content://com.ringconn.app.provider/raw`) and enable debug access.
2. **Intervals Direct** queries this provider locally to extract validated sleep stages, overnight heart rate nadirs, rMSSD, skin temperature offsets, and activity counts.
3. The data is sanitized, validated, and uploaded directly to the **Intervals.icu** REST API.

```
┌─────────────────┐       Local ContentProvider       ┌──────────────────────┐       HTTPS REST       ┌─────────────────┐
│ RingConn App    │ ─────────────────────────────────>│ Intervals Direct     │ ──────────────────────>│  Intervals.icu  │
│ (Morphe-Patched)│    content://com.ringconn...      │ (Background Worker)  │    /api/v1/wellness    │                 │
└─────────────────┘                                   └──────────────────────┘                        └─────────────────┘
```

---

## Features

- **Direct On-Device Extraction:** Reads directly from RingConn's local database via ContentProvider without requiring ADB or USB cables after initial installation.
- **Precision Sleep Sanitation:** 
  - Resolves sleep stage overlaps (Deep, REM, Light, Awake).
  - Matches exact sleep onset and resting heart rate timestamps.
  - Excludes awake micro-arousals from sleeping HR and SpO2 calculations.
- **Multi-Metric Synchronization:**
  - Total sleep time, time in bed, sleep quality score.
  - Sleep stages: Deep, REM, Light sleep in seconds.
  - Resting Heart Rate (RHR) and average sleeping heart rate.
  - Heart Rate Variability (HRV rMSSD).
  - Skin temperature deviation (°C).
  - Blood oxygen saturation (SpO2).
  - Daily total step counts.
- **Ghost Sync (Historical Backfill):** Scans Intervals.icu for missing historical dates and automatically synchronizes past RingConn metrics in batches.
- **Diagnostic Logbook:** Complete in-app execution log recording query results, HTTP status codes, payload summaries, and network errors.
- **Background Automation:** Built with Android `WorkManager` for scheduled, battery-efficient background synchronization with network constraints.
- **Privacy First:** All data processing occurs exclusively on your device. Credentials and biometric data are never transmitted to any third party.

---

## Morphe Patching Guide

Standard Android security isolates SQLite databases between different apps. To allow Intervals Direct to query RingConn biometrics, the RingConn APK must be patched:

### Prerequisites
- Official RingConn APK (downloaded from an APK mirror or extracted from your device).
- **Morphe** (or CLI APK patch tool / LSPatch).

### Patching Steps
1. Open **Morphe** on your device or computer.
2. Select the official **RingConn APK**.
3. Enable the **Debug & ContentProvider Export** patch module:
   - Sets `android:debuggable="true"` in `AndroidManifest.xml`.
   - Declares the ContentProvider: `com.ringconn.app.provider.RingConnProvider` mapped to authority `com.ringconn.app.provider`.
4. Build and sign the patched APK.
5. Uninstall the original RingConn app (ensure your data is synced with RingConn cloud first) and install the patched APK.
6. Log in to your RingConn account and sync your ring as usual.

---

## Setup & Configuration

1. Download or build `IntervalsDirect.apk` and install it on your Android device.
2. Launch **Intervals Direct**.
3. Tap the **⚙️ (Settings)** icon in the top app bar:
   - **Athlete ID:** Enter your Intervals.icu Athlete ID (e.g. `i123456` or `0` for self).
   - **API Key:** Enter your Intervals.icu API Key (found in *Settings -> Developer Settings* on Intervals.icu).
   - **Periodic Background Sync:** Toggle on to enable automated background synchronization.
4. Return to the main screen:
   - View live data under **Live Sync**.
   - Tap **Sync Now** to immediately upload yesterday's and today's wellness records.
   - Use **Ghost Sync** to backfill any missing days in the selected date window.

---

## Building from Source

### Prerequisites
- Android Studio Ladybug (2024.2+) or IntelliJ IDEA.
- JDK 17 or higher.
- Android SDK 34+.

### Build Commands
```bash
# Clone the repository
git clone https://github.com/your-username/IntervalsDirect.git
cd IntervalsDirect

# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```
The compiled APK will be at: `app/build/outputs/apk/debug/app-debug.apk`.

To install directly to a connected device:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Structure

```
IntervalsDirect/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/radfahrzeugs/intervalsdirect/
│       │       ├── MainActivity.kt
│       │       ├── data/
│       │       │   ├── Models.kt
│       │       │   ├── PreferencesManager.kt
│       │       │   ├── IntervalsRepository.kt
│       │       │   └── RingConnProviderRepository.kt
│       │       ├── engine/
│       │       │   └── MetricCalculator.kt
│       │       ├── worker/
│       │       │   ├── SyncWorker.kt
│       │       │   └── SyncScheduler.kt
│       │       └── ui/
│       │           ├── MainScreen.kt
│       │           ├── InspectionCard.kt
│       │           └── theme/
│       └── test/
│           └── java/com/radfahrzeugs/intervalsdirect/
│               └── MetricCalculatorTest.kt
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE
└── README.md
```

---

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.
