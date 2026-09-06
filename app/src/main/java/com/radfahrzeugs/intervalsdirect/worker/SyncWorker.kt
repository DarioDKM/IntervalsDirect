package com.radfahrzeugs.intervalsdirect.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.radfahrzeugs.intervalsdirect.data.RingConnProviderRepository
import com.radfahrzeugs.intervalsdirect.data.IntervalsRepository
import com.radfahrzeugs.intervalsdirect.data.PreferencesManager
import com.radfahrzeugs.intervalsdirect.data.SyncLogEntry
import com.radfahrzeugs.intervalsdirect.data.SyncLogManager
import com.radfahrzeugs.intervalsdirect.engine.MetricCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        val logManager = SyncLogManager(applicationContext)
        val athleteId = prefs.athleteIdFlow.first()
        val apiKey = prefs.apiKeyFlow.first()

        if (apiKey.isBlank() || athleteId.isBlank()) {
            return Result.failure()
        }

        val ringConnRepo = RingConnProviderRepository(applicationContext)
        val intervalsRepo = IntervalsRepository()

        // 1. Geister-Sync (Hintergrund-Cloud-Sync für RingConn Dev anstoßen)
        val ghostSyncTriggered = ringConnRepo.triggerGhostSync()

        val today = LocalDate.now()
        val datesToSync = listOf(today.minusDays(1), today)

        var hasFailure = false
        var failureReason: String? = null
        val syncedSummaryList = mutableListOf<String>()
        var lastPayloadString: String? = null

        // Fetch recent server data (including CTL, ATL, TSB)
        val serverMap = runCatching {
            intervalsRepo.fetchWellnessRange(athleteId, apiKey, today.minusDays(10), today).getOrDefault(emptyMap())
        }.getOrDefault(emptyMap())

        try {
            for (date in datesToSync) {
                val prevDay = date.minusDays(1)
                val prevJson = serverMap[prevDay]
                val load = if (prevJson != null) {
                    if (prevJson.has("ctlLoad") && !prevJson.isNull("ctlLoad")) prevJson.optDouble("ctlLoad").toFloat()
                    else if (prevJson.has("atlLoad") && !prevJson.isNull("atlLoad")) prevJson.optDouble("atlLoad").toFloat()
                    else null
                } else null

                val dailyData = ringConnRepo.fetchDailyData(date)
                val finalSleep = if (dailyData.sleepMetrics != null && load != null) {
                    dailyData.sleepMetrics.copy(yesterdayLoad = load)
                } else {
                    dailyData.sleepMetrics
                }
                val dataToUpload = dailyData.copy(sleepMetrics = finalSleep)

                if (dataToUpload.sleepMetrics != null || dataToUpload.restingHR != null || dataToUpload.hrv != null) {
                    val payload = MetricCalculator.buildPayload(
                        date = date,
                        sleepMetrics = dataToUpload.sleepMetrics,
                        restingHR = dataToUpload.restingHR,
                        avgSleepingHR = dataToUpload.avgSleepingHR,
                        spO2 = dataToUpload.spO2,
                        respiration = dataToUpload.respiration,
                        skinTemp = dataToUpload.skinTemp,
                        hrv = dataToUpload.hrv
                    )
                    lastPayloadString = payload.toJsonString()

                    val uploadResult = intervalsRepo.uploadWellness(athleteId, apiKey, payload)
                    if (uploadResult.isSuccess) {
                        val sleepStr = dataToUpload.sleepMetrics?.let { "${it.sleepSecs / 3600}h ${(it.sleepSecs % 3600) / 60}m" } ?: "No sleep"
                        syncedSummaryList.add("$date: Sleep $sleepStr, HRV ${dataToUpload.hrv ?: "--"}ms, RHR ${dataToUpload.restingHR ?: "--"}bpm, Temp ${dataToUpload.skinTemp ?: "--"}°C")
                    } else {
                        hasFailure = true
                        failureReason = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                    }
                }
            }
        } catch (e: Exception) {
            hasFailure = true
            failureReason = "Exception: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }

        val logEntry = SyncLogEntry(
            triggerSource = "Background",
            targetDates = datesToSync.map { it.toString() },
            statusCode = if (hasFailure) 500 else 200,
            isSuccess = !hasFailure && syncedSummaryList.isNotEmpty(),
            summary = if (hasFailure) {
                "Failed: ${failureReason ?: "Unknown error"}"
            } else if (syncedSummaryList.isEmpty()) {
                "No new data for ${datesToSync.joinToString()}"
            } else {
                "Successfully synced: " + syncedSummaryList.joinToString("; ")
            },
            payloadJson = lastPayloadString
        )
        logManager.addLog(logEntry)

        return if (hasFailure) Result.retry() else Result.success()
    }
}
