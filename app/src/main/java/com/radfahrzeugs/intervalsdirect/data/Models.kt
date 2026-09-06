package com.radfahrzeugs.intervalsdirect.data

import org.json.JSONObject
import java.time.LocalDate

data class IntervalsWellnessPayload(
    val id: String, // YYYY-MM-DD
    val sleepSecs: Int? = null,
    val sleepScore: Int? = null,       // Intervals.icu Sleep Score (1-100)
    val sleepQuality: Int? = null,     // Intervals.icu Sleep Quality (1=GREAT, 2=GOOD, 3=AVG, 4=POOR)
    val restingHR: Int? = null,
    val avgSleepingHR: Int? = null,
    val spO2: Float? = null,
    val hrv: Float? = null,
    val respiration: Float? = null,    // Breaths per minute
    val skinTemp: Float? = null,       // Skin temperature deviation
    val deepSleep: Int? = null,        // Custom field: DeepSleep (minutes)
    val remSleep: Int? = null,         // Custom field: RemSleep (minutes)
    val lightSleep: Int? = null,       // Custom field: LightSleep (minutes)
    val clearStepsOnServer: Boolean = true
) {
    fun toJsonString(): String {
        val json = JSONObject()
        if (sleepSecs != null && sleepSecs > 0) {
            json.put("sleepSecs", sleepSecs)
        }
        sleepScore?.let { if (it > 0) json.put("sleepScore", it) }
        sleepQuality?.let { if (it in 1..4) json.put("sleepQuality", it) }
        restingHR?.let { if (it > 0) json.put("restingHR", it) }
        avgSleepingHR?.let { if (it > 0) json.put("avgSleepingHR", it) }
        spO2?.let { if (it > 0) json.put("spO2", it.toDouble()) }
        if (clearStepsOnServer) {
            json.put("steps", -1) // Clear steps from intervals.icu completely
        }
        hrv?.let { if (it > 0) json.put("hrv", it.toDouble()) }
        respiration?.let { if (it > 0) json.put("respiration", it.toDouble()) }
        deepSleep?.let { if (it > 0) json.put("DeepSleep", it) }
        remSleep?.let { if (it > 0) json.put("RemSleep", it) }
        lightSleep?.let { if (it > 0) json.put("LightSleep", it) }
        return json.toString()
    }
}

data class ProcessedSleepMetrics(
    val sleepSecs: Int,
    val deepSleepMinutes: Int,
    val remSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val awakeMinutes: Int,
    val sleepScore: Int? = null,
    val sleepScoreLabel: String? = null,
    val onsetIso: String?,
    val wakeIso: String?,
    val isBlanketRemoved: Boolean,
    val dynamicNeedMin: Int? = null,
    val strainAdditionMin: Int? = null,
    val baseNeedMin: Int? = null,
    val fulfillmentScore: Double? = null,
    val deepScore: Double? = null,
    val remScore: Double? = null,
    val efficiencyScore: Double? = null,
    val continuityScore: Double? = null,
    val wasoScore: Double? = null,
    val effRatioScore: Double? = null,
    val fulfillmentRatio: Double? = null,
    val yesterdayLoad: Float? = null
)

enum class WarningLevel {
    NORMAL,
    ELEVATED_TEMP,
    HRV_DEPRESSED,
    ACUTE_INFECTION_OVERLOAD
}

data class InfectionWarningResult(
    val level: WarningLevel,
    val isOverheating: Boolean,
    val isHrvCrash: Boolean,
    val isRhrSpike: Boolean,
    val currentTempOffset: Float?,
    val baselineTempOffset: Float?,
    val currentHrv: Float?,
    val mean7dHrv: Float?,
    val sd7dHrv: Float?,
    val hrvZScore: Float?,
    val currentRhr: Int?,
    val mean7dRhr: Float?,
    val title: String,
    val message: String,
    val cyclingPrescription: String
)

enum class ReadinessLevel {
    OPTIMAL,
    GOOD,
    FATIGUED,
    RECOVERY_NEED
}

data class PlannedWorkout(
    val id: Long,
    val date: LocalDate,
    val name: String,
    val description: String? = null,
    val movingTimeSecs: Int? = null,
    val trainingLoad: Int? = null,
    val averageWatts: Int? = null,
    val normalizedPower: Int? = null,
    val category: String? = null
)

data class AcuteCyclingLoad(
    val loadLast24h: Float = 0f,
    val loadLast48h: Float = 0f,
    val kjLast24h: Float = 0f,
    val kjLast48h: Float = 0f,
    val lastRideDate: LocalDate? = null,
    val lastRideName: String? = null
)

data class IntervalsTrainingForm(
    val ctl: Float? = null,
    val atl: Float? = null,
    val tsb: Float? = null,
    val rampRate: Float? = null,
    val eftp: Float? = null,
    val acuteCyclingLoad: AcuteCyclingLoad? = null,
    val todayWorkout: PlannedWorkout? = null,
    val tomorrowWorkout: PlannedWorkout? = null
)

data class ReadinessScoreResult(
    val score: Int,
    val level: ReadinessLevel,
    val label: String,
    val hrvScore: Double,
    val rhrScore: Double,
    val deepSleepScore: Double,
    val workloadScore: Double,
    val acuteWorkScore: Double = 25.0,
    val rampRateScore: Double = 20.0,
    val autonomicScore: Double = 30.0,
    val ctl: Float?,
    val atl: Float?,
    val tsb: Float?,
    val rampRate: Float? = null,
    val eftp: Float? = null,
    val acuteCyclingLoad: AcuteCyclingLoad? = null,
    val todayWorkout: PlannedWorkout? = null,
    val tomorrowWorkout: PlannedWorkout? = null,
    val coachApproval: String = "Freigabe: 100% Go",
    val isTsbRestricted: Boolean,
    val cyclingPrescription: String,
    val wattCorridor: String,
    val upcomingNotice: String? = null
)


data class IntradayHrvPoint(
    val utcTs: Long,
    val timeFormatted: String,
    val hrv: Int,
    val pr: Int,
    val resprate: Float? = null,
    val actiCount: Int? = null
)

data class ParasympatheticRecoveryAnalysis(
    val totalPoints: Int,
    val meanHrv: Float,
    val earlySleepHrv: Float,
    val lateSleepHrv: Float,
    val recoveryRatio: Float,
    val restorativeMinutes: Int,
    val assessment: String
)

data class DailyInspectionData(
    val date: LocalDate,
    val sleepMetrics: ProcessedSleepMetrics?,
    val restingHR: Int?,
    val avgSleepingHR: Int?,
    val spO2: Float?,
    val steps: Int? = null,
    val respiration: Float? = null,
    val skinTemp: Float? = null,
    val tempBenchmark: Float? = null,
    val tempOffset: Float? = null,
    val hrv: Float? = null,
    val intervalsExistingData: JSONObject? = null,
    val isSynced: Boolean = false,
    val syncError: String? = null,
    val infectionWarning: InfectionWarningResult? = null,
    val readinessScore: ReadinessScoreResult? = null,
    val intradayHrv: List<IntradayHrvPoint>? = null,
    val parasympatheticAnalysis: ParasympatheticRecoveryAnalysis? = null
) {
    fun withServerOrLocalFallback(
        serverJson: JSONObject?,
        localRingConn: com.radfahrzeugs.intervalsdirect.engine.RingConnCsvParser.ParsedRingConnData? = null
    ): DailyInspectionData {
        val hrvVal = this.hrv
            ?: localRingConn?.hrv
            ?: if (serverJson?.has("hrv") == true && !serverJson.isNull("hrv")) serverJson.optDouble("hrv").toFloat() else null

        val rhrVal = this.restingHR
            ?: localRingConn?.restingHR
            ?: if (serverJson?.has("restingHR") == true && !serverJson.isNull("restingHR")) serverJson.optInt("restingHR") else null

        val sleepHrVal = this.avgSleepingHR
            ?: localRingConn?.avgHR
            ?: if (serverJson?.has("avgSleepingHR") == true && !serverJson.isNull("avgSleepingHR")) serverJson.optInt("avgSleepingHR") else null

        val spo2Val = this.spO2
            ?: localRingConn?.spO2
            ?: if (serverJson?.has("spO2") == true && !serverJson.isNull("spO2")) serverJson.optDouble("spO2").toFloat() else null

        val respVal = this.respiration
            ?: if (serverJson?.has("respiration") == true && !serverJson.isNull("respiration")) serverJson.optDouble("respiration").toFloat() else null

        val tempVal = this.skinTemp
            ?: if (serverJson?.has("skinTemp") == true && !serverJson.isNull("skinTemp")) serverJson.optDouble("skinTemp").toFloat() else null

        val serverSleepSecs = if (serverJson?.has("sleepSecs") == true && !serverJson.isNull("sleepSecs")) serverJson.optInt("sleepSecs") else null
        val serverSleepScore = if (serverJson?.has("sleepScore") == true && !serverJson.isNull("sleepScore")) serverJson.optInt("sleepScore") else null

        val mergedSleep = if (this.sleepMetrics != null) {
            this.sleepMetrics
        } else if (localRingConn?.sleepSecs != null && localRingConn.sleepSecs!! > 0) {
            com.radfahrzeugs.intervalsdirect.engine.RingConnCsvParser.toDailyInspectionData(localRingConn).sleepMetrics
        } else if (serverSleepSecs != null && serverSleepSecs > 0) {
            ProcessedSleepMetrics(
                sleepSecs = serverSleepSecs,
                deepSleepMinutes = if (serverJson?.has("DeepSleep") == true) serverJson.optInt("DeepSleep") else 0,
                remSleepMinutes = if (serverJson?.has("RemSleep") == true) serverJson.optInt("RemSleep") else 0,
                lightSleepMinutes = if (serverJson?.has("LightSleep") == true) serverJson.optInt("LightSleep") else 0,
                awakeMinutes = if (serverJson?.has("sleepAwake") == true) serverJson.optInt("sleepAwake") / 60 else 0,
                sleepScore = serverSleepScore,
                sleepScoreLabel = if (serverSleepScore != null && serverSleepScore >= 85) "Optimal" else "Good",
                onsetIso = null,
                wakeIso = null,
                isBlanketRemoved = false
            )
        } else null

        return this.copy(
            sleepMetrics = mergedSleep,
            restingHR = rhrVal,
            avgSleepingHR = sleepHrVal,
            spO2 = spo2Val,
            steps = null,
            respiration = respVal,
            skinTemp = tempVal,
            hrv = hrvVal,
            intervalsExistingData = serverJson
        )
    }
}

data class SyncLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val triggerSource: String, // "Manual", "Background", "Resume"
    val targetDates: List<String>,
    val statusCode: Int,
    val isSuccess: Boolean,
    val summary: String,
    val payloadJson: String? = null
)

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val message: String, val timestamp: Long = System.currentTimeMillis()) : SyncState()
    data class Error(val message: String) : SyncState()
}
