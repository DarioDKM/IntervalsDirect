package com.radfahrzeugs.intervalsdirect.engine

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.radfahrzeugs.intervalsdirect.data.DailyInspectionData
import com.radfahrzeugs.intervalsdirect.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.util.Locale

object AutoBackupManager {

    suspend fun performAutoBackup(
        context: Context,
        dataList: List<DailyInspectionData>
    ): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)
        val isEnabled = prefs.autoBackupFlow.first()
        val folderUriStr = prefs.backupFolderUriFlow.first()

        if (!isEnabled || folderUriStr.isNullOrBlank() || dataList.isEmpty()) {
            return@withContext false
        }

        try {
            val treeUri = Uri.parse(folderUriStr)
            val rootDir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            if (!rootDir.canWrite()) return@withContext false

            // 1. Write Unified Master CSV
            val masterCsv = MetricCalculator.exportToCsv(dataList)
            writeDocumentFile(context, rootDir, "IntervalsConnect_Master_Wellness.csv", "text/csv", masterCsv)

            // 2. Write Unified Master JSON
            val masterJson = MetricCalculator.exportToJson(dataList)
            writeDocumentFile(context, rootDir, "IntervalsConnect_Master_Wellness.json", "application/json", masterJson)

            // 3. Write Health-Sync compatible Sleep CSV
            val sleepCsv = generateSleepCsv(dataList)
            writeDocumentFile(context, rootDir, "Sleep_Summary.csv", "text/csv", sleepCsv)

            // 4. Write Vital Signs CSV (HR, RHR, SpO2, HRV)
            val vitalsCsv = generateVitalsCsv(dataList)
            writeDocumentFile(context, rootDir, "Vital_Signs.csv", "text/csv", vitalsCsv)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun writeDocumentFile(
        context: Context,
        dir: DocumentFile,
        fileName: String,
        mimeType: String,
        content: String
    ) {
        var file = dir.findFile(fileName)
        if (file == null) {
            file = dir.createFile(mimeType, fileName)
        }
        if (file != null) {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }
        }
    }

    private fun generateSleepCsv(dataList: List<DailyInspectionData>): String {
        val sb = StringBuilder()
        sb.append("Date,Sleep_Hours,Sleep_Score,Deep_Sleep_Min,REM_Sleep_Min,Light_Sleep_Min,Awake_Min,Onset_Time,Wake_Time\n")
        for (d in dataList.sortedByDescending { it.date }) {
            val sm = d.sleepMetrics ?: continue
            val sleepHours = String.format(Locale.US, "%.2f", sm.sleepSecs / 3600.0)
            val score = sm.sleepScore ?: ""
            val deep = sm.deepSleepMinutes
            val rem = sm.remSleepMinutes
            val light = sm.lightSleepMinutes
            val awake = sm.awakeMinutes
            val onset = sm.onsetIso ?: ""
            val wake = sm.wakeIso ?: ""
            sb.append("${d.date},$sleepHours,$score,$deep,$rem,$light,$awake,$onset,$wake\n")
        }
        return sb.toString()
    }

    private fun generateVitalsCsv(dataList: List<DailyInspectionData>): String {
        val sb = StringBuilder()
        sb.append("Date,Resting_HR_bpm,Sleep_Avg_HR_bpm,SpO2_pct,HRV_rMSSD_ms,Respiration_bpm,Skin_Temp_C\n")
        for (d in dataList.sortedByDescending { it.date }) {
            val rhr = d.restingHR ?: ""
            val sleepHr = d.avgSleepingHR ?: ""
            val spo2 = d.spO2?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val hrv = d.hrv?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val resp = d.respiration?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val temp = d.skinTemp?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            sb.append("${d.date},$rhr,$sleepHr,$spo2,$hrv,$resp,$temp\n")
        }
        return sb.toString()
    }
}
