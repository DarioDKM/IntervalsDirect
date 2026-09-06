package com.radfahrzeugs.intervalsdirect

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.radfahrzeugs.intervalsdirect.data.*
import com.radfahrzeugs.intervalsdirect.engine.AutoBackupManager
import com.radfahrzeugs.intervalsdirect.engine.MetricCalculator
import com.radfahrzeugs.intervalsdirect.engine.RingConnCsvParser
import com.radfahrzeugs.intervalsdirect.ui.MainScreen
import com.radfahrzeugs.intervalsdirect.ui.theme.RingConnIntervalsSyncTheme
import com.radfahrzeugs.intervalsdirect.worker.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private lateinit var ringConnRepo: RingConnProviderRepository
    private lateinit var intervalsRepo: IntervalsRepository
    private lateinit var prefsManager: PreferencesManager
    private lateinit var syncLogManager: SyncLogManager

    // File picker launcher for RingConn ZIP / CSV imports (fallback)
    private val pickRingConnFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                importRingConnUri(it)
            }
        }
    }

    // Folder picker launcher for Google Drive / SAF Auto-Backup
    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            lifecycleScope.launch {
                prefsManager.saveBackupFolder(it.toString())
                val history = if (historyDataState.isNotEmpty()) historyDataState else ringConnRepo.fetchHistoryDays(30)
                AutoBackupManager.performAutoBackup(this@MainActivity, history)
            }
        }
    }

    private var todayDataState by mutableStateOf<DailyInspectionData?>(null)
    private var yesterdayDataState by mutableStateOf<DailyInspectionData?>(null)
    private var historyDataState by mutableStateOf<List<DailyInspectionData>>(emptyList())
    private var isHistoryLoadingState by mutableStateOf(false)
    private var syncLogsState by mutableStateOf<List<SyncLogEntry>>(emptyList())
    private var syncState by mutableStateOf<SyncState>(SyncState.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ringConnRepo = RingConnProviderRepository(this)
        intervalsRepo = IntervalsRepository()
        prefsManager = PreferencesManager(this)
        syncLogManager = SyncLogManager(this)

        setContent {
            RingConnIntervalsSyncTheme {
                val athleteId by prefsManager.athleteIdFlow.collectAsState(initial = PreferencesManager.DEFAULT_ATHLETE_ID)
                val apiKey by prefsManager.apiKeyFlow.collectAsState(initial = PreferencesManager.DEFAULT_API_KEY)
                val autoSync by prefsManager.autoSyncFlow.collectAsState(initial = true)
                val syncIntervalHours by prefsManager.syncIntervalFlow.collectAsState(initial = 3)
                val autoBackup by prefsManager.autoBackupFlow.collectAsState(initial = false)
                val backupFolderUri by prefsManager.backupFolderUriFlow.collectAsState(initial = null)
                val lastSync by prefsManager.lastSyncFlow.collectAsState(initial = 0L)

                MainScreen(
                    hasPermissions = true,
                    onRequestPermissions = { },
                    todayData = todayDataState,
                    yesterdayData = yesterdayDataState,
                    historyData = historyDataState,
                    isHistoryLoading = isHistoryLoadingState,
                    syncLogs = syncLogsState,
                    athleteId = athleteId,
                    apiKey = apiKey,
                    autoSync = autoSync,
                    syncIntervalHours = syncIntervalHours,
                    autoBackup = autoBackup,
                    backupFolderUri = backupFolderUri,
                    lastSyncTimestamp = lastSync,
                    syncState = syncState,
                    onSaveSettings = { newAthlete, newKey, newAutoSync, newInterval, newAutoBackup ->
                        lifecycleScope.launch {
                            prefsManager.saveSettings(newAthlete, newKey, newAutoSync, newInterval, newAutoBackup)
                            SyncScheduler.scheduleDailySync(this@MainActivity, newAutoSync, newInterval.toLong())
                        }
                    },
                    onManualSync = { triggerManualSync() },
                    onSyncSingleDay = { date -> syncSingleDate(date) },
                    onSyncAllDays = { syncAllHistoricalDays() },
                    onRefreshData = { lifecycleScope.launch { loadDailyData() } },
                    onRefreshHistory = { lifecycleScope.launch { loadHistory() } },
                    onRefreshLogs = { lifecycleScope.launch { loadLogs() } },
                    onClearLogs = {
                        lifecycleScope.launch {
                            syncLogManager.clearLogs()
                            syncLogsState = emptyList()
                        }
                    },
                    onExportData = { format -> handleExport(format) },
                    onImportRingConnFile = {
                        pickRingConnFileLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "text/csv",
                                "text/comma-separated-values",
                                "*/*"
                            )
                        )
                    },
                    onPickBackupFolder = {
                        pickFolderLauncher.launch(null)
                    },
                    onTriggerGhostSync = { triggerGhostSync() }
                )
            }
        }

        lifecycleScope.launch {
            val autoSync = prefsManager.autoSyncFlow.first()
            val interval = prefsManager.syncIntervalFlow.first()
            SyncScheduler.scheduleDailySync(this@MainActivity, autoSync, interval.toLong())
            loadDailyData()
            loadHistory()
            loadLogs()

            handleIncomingIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch {
            handleIncomingIntent(intent)
        }
    }

    private suspend fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val uri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) {
            importRingConnUri(uri)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            loadDailyData()
            loadLogs()
        }
    }

    private fun triggerGhostSync() {
        lifecycleScope.launch {
            syncState = SyncState.Syncing
            val success = ringConnRepo.triggerGhostSync()
            if (success) {
                kotlinx.coroutines.delay(1000)
                loadDailyData()
                loadHistory()
                syncState = SyncState.Success("Ghost Sync triggered successfully in RingConn!")
            } else {
                syncState = SyncState.Error("Ghost Sync failed. RingConn provider not reachable.")
            }
        }
    }

    private suspend fun loadDailyData() {
        val athleteId = prefsManager.athleteIdFlow.first()
        val apiKey = prefsManager.apiKeyFlow.first()

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val oldest = today.minusDays(7)

        val serverMap = intervalsRepo.fetchWellnessRange(athleteId, apiKey, oldest, today).getOrDefault(emptyMap())

        fun getYesterdayLoad(targetDate: LocalDate): Float? {
            val prev = targetDate.minusDays(1)
            val json = serverMap[prev] ?: return null
            return if (json.has("ctlLoad") && !json.isNull("ctlLoad")) json.optDouble("ctlLoad").toFloat()
            else if (json.has("atlLoad") && !json.isNull("atlLoad")) json.optDouble("atlLoad").toFloat()
            else null
        }

        val rawToday = ringConnRepo.fetchDailyData(today).let { d ->
            val load = getYesterdayLoad(today)
            if (d.sleepMetrics != null && load != null) d.copy(sleepMetrics = d.sleepMetrics.copy(yesterdayLoad = load)) else d
        }
        val rawYesterday = ringConnRepo.fetchDailyData(yesterday).let { d ->
            val load = getYesterdayLoad(yesterday)
            if (d.sleepMetrics != null && load != null) d.copy(sleepMetrics = d.sleepMetrics.copy(yesterdayLoad = load)) else d
        }

        todayDataState = rawToday.withServerOrLocalFallback(serverMap[today], RingConnLocalStore.get(this@MainActivity, today))
        yesterdayDataState = rawYesterday.withServerOrLocalFallback(serverMap[yesterday], RingConnLocalStore.get(this@MainActivity, yesterday))
    }

    private suspend fun loadHistory() {
        isHistoryLoadingState = true
        val athleteId = prefsManager.athleteIdFlow.first()
        val apiKey = prefsManager.apiKeyFlow.first()

        val rawList = ringConnRepo.fetchHistoryDays(30)
        val today = LocalDate.now()
        val oldest = today.minusDays(32)

        val serverMap = intervalsRepo.fetchWellnessRange(athleteId, apiKey, oldest, today).getOrDefault(emptyMap())
        val localStoreMap = RingConnLocalStore.getAll(this@MainActivity)

        val enhanced = rawList.map { d ->
            val prevDay = d.date.minusDays(1)
            val prevJson = serverMap[prevDay]
            val load = if (prevJson != null) {
                if (prevJson.has("ctlLoad") && !prevJson.isNull("ctlLoad")) prevJson.optDouble("ctlLoad").toFloat()
                else if (prevJson.has("atlLoad") && !prevJson.isNull("atlLoad")) prevJson.optDouble("atlLoad").toFloat()
                else null
            } else null

            val updatedSleep = d.sleepMetrics?.let { s ->
                val scoreResult = MetricCalculator.calculateSleepScore(
                    sleepSecs = s.sleepSecs,
                    deepMin = s.deepSleepMinutes,
                    remMin = s.remSleepMinutes,
                    lightMin = s.lightSleepMinutes,
                    awakeMin = s.awakeMinutes,
                    yesterdayLoad = load
                )
                s.copy(
                    sleepScore = scoreResult.score,
                    sleepScoreLabel = scoreResult.label,
                    dynamicNeedMin = scoreResult.dynamicNeedMin,
                    strainAdditionMin = scoreResult.strainAdditionMin,
                    baseNeedMin = scoreResult.baseNeedMin,
                    fulfillmentScore = scoreResult.fulfillmentScore,
                    deepScore = scoreResult.deepScore,
                    remScore = scoreResult.remScore,
                    efficiencyScore = scoreResult.continuityScore,
                    continuityScore = scoreResult.continuityScore,
                    wasoScore = scoreResult.wasoScore,
                    effRatioScore = scoreResult.effRatioScore,
                    fulfillmentRatio = scoreResult.fulfillmentRatio,
                    yesterdayLoad = scoreResult.yesterdayLoad
                )
            }
            d.copy(sleepMetrics = updatedSleep).withServerOrLocalFallback(serverMap[d.date], localStoreMap[d.date])
        }
        historyDataState = enhanced
        isHistoryLoadingState = false
    }

    private suspend fun loadLogs() {
        syncLogsState = syncLogManager.getLogs()
    }

    private fun triggerManualSync() {
        lifecycleScope.launch {
            syncState = SyncState.Syncing
            val athleteId = prefsManager.athleteIdFlow.first()
            val apiKey = prefsManager.apiKeyFlow.first()

            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val dayBeforeYesterday = yesterday.minusDays(1)
            val dates = listOf(yesterday, today)

            val serverMap = intervalsRepo.fetchWellnessRange(athleteId, apiKey, dayBeforeYesterday, today).getOrDefault(emptyMap())
            val localStoreMap = RingConnLocalStore.getAll(this@MainActivity)

            var successCount = 0
            var errorMessage: String? = null
            val summaryItems = mutableListOf<String>()
            var lastPayloadString: String? = null

            for (date in dates) {
                val prevDay = date.minusDays(1)
                val prevJson = serverMap[prevDay]
                val load = if (prevJson != null) {
                    if (prevJson.has("ctlLoad") && !prevJson.isNull("ctlLoad")) prevJson.optDouble("ctlLoad").toFloat()
                    else if (prevJson.has("atlLoad") && !prevJson.isNull("atlLoad")) prevJson.optDouble("atlLoad").toFloat()
                    else null
                } else null

                val raw = ringConnRepo.fetchDailyData(date).let { d ->
                    if (d.sleepMetrics != null && load != null) d.copy(sleepMetrics = d.sleepMetrics.copy(yesterdayLoad = load)) else d
                }
                val data = raw.withServerOrLocalFallback(serverMap[date], localStoreMap[date])
                if (data.sleepMetrics != null || data.restingHR != null || data.hrv != null) {
                    val payload = MetricCalculator.buildPayload(
                        date = date,
                        sleepMetrics = data.sleepMetrics,
                        restingHR = data.restingHR,
                        avgSleepingHR = data.avgSleepingHR,
                        spO2 = data.spO2,
                        respiration = data.respiration,
                        skinTemp = data.skinTemp,
                        hrv = data.hrv
                    )
                    lastPayloadString = payload.toJsonString()

                    val result = intervalsRepo.uploadWellness(athleteId, apiKey, payload)
                    if (result.isSuccess) {
                        successCount++
                        val sleepStr = data.sleepMetrics?.let { "${it.sleepSecs / 3600}h ${(it.sleepSecs % 3600) / 60}m" } ?: "No sleep"
                        summaryItems.add("$date: Sleep $sleepStr, HRV ${data.hrv ?: "--"}ms, RHR ${data.restingHR ?: "--"}bpm")
                    } else {
                        errorMessage = result.exceptionOrNull()?.message
                    }
                }
            }

            val isSuccess = errorMessage == null && successCount > 0
            syncLogManager.addLog(
                SyncLogEntry(
                    triggerSource = "Manual",
                    targetDates = dates.map { it.toString() },
                    statusCode = if (isSuccess) 200 else 500,
                    isSuccess = isSuccess,
                    summary = if (isSuccess) summaryItems.joinToString(" | ") else (errorMessage ?: "No data found to sync"),
                    payloadJson = lastPayloadString
                )
            )
            loadLogs()

            if (isSuccess) {
                val now = System.currentTimeMillis()
                prefsManager.updateLastSync(now)
                syncState = SyncState.Success("Synced $successCount day(s) to Intervals.icu", now)

                val history = if (historyDataState.isNotEmpty()) historyDataState else ringConnRepo.fetchHistoryDays(30)
                AutoBackupManager.performAutoBackup(this@MainActivity, history)
            } else {
                syncState = SyncState.Error(errorMessage ?: "No metrics recorded to upload")
            }
        }
    }

    private fun syncSingleDate(date: LocalDate) {
        lifecycleScope.launch {
            val athleteId = prefsManager.athleteIdFlow.first()
            val apiKey = prefsManager.apiKeyFlow.first()

            val serverJson = intervalsRepo.fetchWellness(athleteId, apiKey, date).getOrNull()
            val prevServerJson = intervalsRepo.fetchWellness(athleteId, apiKey, date.minusDays(1)).getOrNull()
            val prevLoad = prevServerJson?.let {
                if (it.has("ctlLoad") && !it.isNull("ctlLoad")) it.optDouble("ctlLoad").toFloat()
                else if (it.has("atlLoad") && !it.isNull("atlLoad")) it.optDouble("atlLoad").toFloat()
                else null
            }

            val localItem = RingConnLocalStore.get(this@MainActivity, date)
            val raw = ringConnRepo.fetchDailyData(date).let { d ->
                if (d.sleepMetrics != null && prevLoad != null) d.copy(sleepMetrics = d.sleepMetrics.copy(yesterdayLoad = prevLoad)) else d
            }
            val data = raw.withServerOrLocalFallback(serverJson, localItem)

            val payload = MetricCalculator.buildPayload(
                date = date,
                sleepMetrics = data.sleepMetrics,
                restingHR = data.restingHR,
                avgSleepingHR = data.avgSleepingHR,
                spO2 = data.spO2,
                respiration = data.respiration,
                skinTemp = data.skinTemp,
                hrv = data.hrv
            )

            val result = intervalsRepo.uploadWellness(athleteId, apiKey, payload)
            val isSuccess = result.isSuccess
            val sleepStr = data.sleepMetrics?.let { "${it.sleepSecs / 3600}h ${(it.sleepSecs % 3600) / 60}m" } ?: "No sleep"

            syncLogManager.addLog(
                SyncLogEntry(
                    triggerSource = "History Single ($date)",
                    targetDates = listOf(date.toString()),
                    statusCode = if (isSuccess) 200 else 500,
                    isSuccess = isSuccess,
                    summary = if (isSuccess) "$date: Sleep $sleepStr, HRV ${data.hrv ?: "--"}ms, RHR ${data.restingHR ?: "--"}bpm" else (result.exceptionOrNull()?.message ?: "Upload failed"),
                    payloadJson = payload.toJsonString()
                )
            )
            loadLogs()
            loadHistory()

            if (isSuccess) {
                val history = if (historyDataState.isNotEmpty()) historyDataState else ringConnRepo.fetchHistoryDays(30)
                AutoBackupManager.performAutoBackup(this@MainActivity, history)
            }
        }
    }

    private fun syncAllHistoricalDays() {
        lifecycleScope.launch {
            val athleteId = prefsManager.athleteIdFlow.first()
            val apiKey = prefsManager.apiKeyFlow.first()

            var count = 0
            val list = if (historyDataState.isNotEmpty()) historyDataState else ringConnRepo.fetchHistoryDays(30)
            for (d in list) {
                val payload = MetricCalculator.buildPayload(
                    date = d.date,
                    sleepMetrics = d.sleepMetrics,
                    restingHR = d.restingHR,
                    avgSleepingHR = d.avgSleepingHR,
                    spO2 = d.spO2,
                    respiration = d.respiration,
                    skinTemp = d.skinTemp,
                    hrv = d.hrv
                )
                val res = intervalsRepo.uploadWellness(athleteId, apiKey, payload)
                if (res.isSuccess) count++
            }

            syncLogManager.addLog(
                SyncLogEntry(
                    triggerSource = "Backfill All",
                    targetDates = list.map { it.date.toString() },
                    statusCode = 200,
                    isSuccess = true,
                    summary = "Backfilled $count historical day(s) to Intervals.icu"
                )
            )
            loadLogs()
            loadHistory()

            AutoBackupManager.performAutoBackup(this@MainActivity, list)
        }
    }

    private suspend fun importRingConnUri(uri: Uri) {
        syncState = SyncState.Syncing
        val athleteId = prefsManager.athleteIdFlow.first()
        val apiKey = prefsManager.apiKeyFlow.first()

        val parsedMap = RingConnCsvParser.parseUri(this, uri)
        if (parsedMap.isEmpty()) {
            syncState = SyncState.Error("Could not find any RingConn CSV records in selected file.")
            return
        }

        RingConnLocalStore.saveAll(this, parsedMap)

        var uploadedCount = 0
        val targetDateStrs = mutableListOf<String>()

        for ((date, parsed) in parsedMap) {
            targetDateStrs.add(date.toString())
            val sqlData = runCatching { ringConnRepo.fetchDailyData(date) }.getOrNull()

            val finalSleep = if (parsed.sleepSecs != null && parsed.sleepSecs!! > 0) {
                RingConnCsvParser.toDailyInspectionData(parsed).sleepMetrics
            } else {
                sqlData?.sleepMetrics
            }

            val finalRhr = parsed.restingHR ?: sqlData?.restingHR
            val finalSleepHr = parsed.avgHR ?: sqlData?.avgSleepingHR
            val finalSpo2 = parsed.spO2 ?: sqlData?.spO2
            val finalHrv = parsed.hrv ?: sqlData?.hrv

            val payload = MetricCalculator.buildPayload(
                date = date,
                sleepMetrics = finalSleep,
                restingHR = finalRhr,
                avgSleepingHR = finalSleepHr,
                spO2 = finalSpo2,
                respiration = sqlData?.respiration,
                skinTemp = sqlData?.skinTemp,
                hrv = finalHrv
            )

            val res = intervalsRepo.uploadWellness(athleteId, apiKey, payload)
            if (res.isSuccess) {
                uploadedCount++
            }
        }

        syncLogManager.addLog(
            SyncLogEntry(
                triggerSource = "RingConn Import",
                targetDates = targetDateStrs,
                statusCode = 200,
                isSuccess = uploadedCount > 0,
                summary = "Imported $uploadedCount day(s) from RingConn Export (HRV, Sleep & Vitals synced)"
            )
        )

        loadDailyData()
        loadHistory()
        loadLogs()
        syncState = SyncState.Success("Imported $uploadedCount day(s) from RingConn Export (incl. HRV)!", System.currentTimeMillis())

        val history = if (historyDataState.isNotEmpty()) historyDataState else ringConnRepo.fetchHistoryDays(30)
        AutoBackupManager.performAutoBackup(this@MainActivity, history)
    }

    private fun handleExport(format: String) {
        lifecycleScope.launch {
            val allData = if (historyDataState.isNotEmpty()) historyDataState else ringConnRepo.fetchHistoryDays(30)
            val exportText = if (format == "CSV") {
                MetricCalculator.exportToCsv(allData)
            } else {
                MetricCalculator.exportToJson(allData)
            }

            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, exportText)
                putExtra(Intent.EXTRA_TITLE, "RingConn_Health_Export_${LocalDate.now()}.$format")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Export Health Data ($format)")
            startActivity(shareIntent)
        }
    }
}
