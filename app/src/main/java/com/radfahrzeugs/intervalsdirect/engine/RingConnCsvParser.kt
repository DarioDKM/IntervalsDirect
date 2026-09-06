package com.radfahrzeugs.intervalsdirect.engine

import android.content.Context
import android.net.Uri
import com.radfahrzeugs.intervalsdirect.data.DailyInspectionData
import com.radfahrzeugs.intervalsdirect.data.ProcessedSleepMetrics
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

object RingConnCsvParser {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    data class ParsedRingConnData(
        val date: LocalDate,
        var sleepSecs: Int? = null,
        var deepSleepMin: Int? = null,
        var remSleepMin: Int? = null,
        var lightSleepMin: Int? = null,
        var awakeMin: Int? = null,
        var onsetIso: String? = null,
        var wakeIso: String? = null,
        var restingHR: Int? = null,
        var avgHR: Int? = null,
        var hrv: Float? = null,
        var spO2: Float? = null,
        var steps: Int? = null
    )

    /**
     * Parses a RingConn ZIP file or single CSV from an input stream.
     */
    fun parseUri(context: Context, uri: Uri): Map<LocalDate, ParsedRingConnData> {
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                if (uri.path != null) java.io.File(uri.path!!).inputStream() else null
            } catch (e2: Exception) {
                e2.printStackTrace()
                null
            }
        } ?: return emptyMap()

        // Check if ZIP or CSV
        return try {
            val bytes = inputStream.readBytes()
            if (isZipFile(bytes)) {
                parseZip(bytes.inputStream())
            } else {
                // Try parsing as single CSV
                val map = mutableMapOf<LocalDate, ParsedRingConnData>()
                parseCsvReader(BufferedReader(InputStreamReader(bytes.inputStream())), map, "unknown")
                map
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        } finally {
            inputStream.close()
        }
    }

    private fun isZipFile(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()
    }

    fun parseZip(inputStream: InputStream): Map<LocalDate, ParsedRingConnData> {
        val map = mutableMapOf<LocalDate, ParsedRingConnData>()
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry

        while (entry != null) {
            val name = entry.name.lowercase()
            if (name.endsWith(".csv") && !name.contains("__macosx")) {
                val reader = BufferedReader(InputStreamReader(zip))
                parseCsvReader(reader, map, name)
            }
            entry = zip.nextEntry
        }
        return map
    }

    private fun parseCsvReader(
        reader: BufferedReader,
        map: MutableMap<LocalDate, ParsedRingConnData>,
        fileName: String
    ) {
        val lines = reader.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return

        val header = lines[0].split(",").map { it.trim().lowercase() }

        for (i in 1 until lines.size) {
            val line = lines[i]
            val cols = line.split(",").map { it.trim() }
            if (cols.isEmpty()) continue

            // Determine file type from header
            if (header.any { it.contains("hrv") } || fileName.contains("vital")) {
                parseVitalSignsRow(header, cols, map)
            } else if (header.any { it.contains("sleep") } || fileName.contains("sleep")) {
                parseSleepRow(header, cols, map)
            } else if (header.any { it.contains("step") } || fileName.contains("activity")) {
                parseActivityRow(header, cols, map)
            }
        }
    }

    private fun parseVitalSignsRow(
        header: List<String>,
        cols: List<String>,
        map: MutableMap<LocalDate, ParsedRingConnData>
    ) {
        // Date,Avg. Heart Rate(bpm),Min. Heart Rate(bpm),Max. Heart Rate(bpm),Avg. Spo2(%),Min. Spo2(%),Max. Spo2(%),Avg. HRV(ms),Min. HRV(ms),Max. HRV(ms)
        val dateIdx = header.indexOfFirst { it == "date" }
        if (dateIdx == -1 || dateIdx >= cols.size) return
        val date = runCatching { LocalDate.parse(cols[dateIdx], dateFormatter) }.getOrNull() ?: return

        val entry = map.getOrPut(date) { ParsedRingConnData(date) }

        val avgHrIdx = header.indexOfFirst { it.contains("avg. heart rate") || it.contains("avg heart rate") }
        val minHrIdx = header.indexOfFirst { it.contains("min. heart rate") || it.contains("min heart rate") }
        val avgSpo2Idx = header.indexOfFirst { it.contains("avg. spo2") || it.contains("avg spo2") }
        val avgHrvIdx = header.indexOfFirst { it.contains("avg. hrv") || it.contains("avg hrv") }

        if (avgHrIdx != -1 && avgHrIdx < cols.size) {
            cols[avgHrIdx].toIntOrNull()?.let { entry.avgHR = it }
        }
        if (minHrIdx != -1 && minHrIdx < cols.size) {
            cols[minHrIdx].toIntOrNull()?.let { entry.restingHR = it }
        }
        if (avgSpo2Idx != -1 && avgSpo2Idx < cols.size) {
            val spo2Clean = cols[avgSpo2Idx].replace("%", "").trim()
            spo2Clean.toFloatOrNull()?.let { entry.spO2 = it }
        }
        if (avgHrvIdx != -1 && avgHrvIdx < cols.size) {
            cols[avgHrvIdx].toFloatOrNull()?.let { entry.hrv = it }
        }
    }

    private fun parseSleepRow(
        header: List<String>,
        cols: List<String>,
        map: MutableMap<LocalDate, ParsedRingConnData>
    ) {
        // Start Time,End Time,Falling Asleep Time,Wake-up time,Sleep Time Ratio(%),Time Asleep(min),Sleep Stages - Awake(min),Sleep Stages - REM(min),Sleep Stages - Light Sleep(min),Sleep Stages - Deep Sleep(min)
        val wakeIdx = header.indexOfFirst { it.contains("wake-up") || it.contains("wake up") || it.contains("end time") }
        val startIdx = header.indexOfFirst { it.contains("start time") || it.contains("falling asleep") }
        if (wakeIdx == -1 || wakeIdx >= cols.size) return

        val wakeTimeStr = cols[wakeIdx]
        val wakeDt = runCatching { LocalDateTime.parse(wakeTimeStr, dateTimeFormatter) }.getOrNull() ?: return
        val date = wakeDt.toLocalDate()

        val entry = map.getOrPut(date) { ParsedRingConnData(date) }

        if (startIdx != -1 && startIdx < cols.size) {
            val startDt = runCatching { LocalDateTime.parse(cols[startIdx], dateTimeFormatter) }.getOrNull()
            entry.onsetIso = startDt?.toString()
        }
        entry.wakeIso = wakeDt.toString()

        val timeAsleepIdx = header.indexOfFirst { it.contains("time asleep") }
        val awakeIdx = header.indexOfFirst { it.contains("awake") }
        val remIdx = header.indexOfFirst { it.contains("rem") }
        val lightIdx = header.indexOfFirst { it.contains("light") }
        val deepIdx = header.indexOfFirst { it.contains("deep") }

        if (timeAsleepIdx != -1 && timeAsleepIdx < cols.size) {
            cols[timeAsleepIdx].toIntOrNull()?.let { entry.sleepSecs = it * 60 }
        }
        if (awakeIdx != -1 && awakeIdx < cols.size) {
            cols[awakeIdx].toIntOrNull()?.let { entry.awakeMin = it }
        }
        if (remIdx != -1 && remIdx < cols.size) {
            cols[remIdx].toIntOrNull()?.let { entry.remSleepMin = it }
        }
        if (lightIdx != -1 && lightIdx < cols.size) {
            cols[lightIdx].toIntOrNull()?.let { entry.lightSleepMin = it }
        }
        if (deepIdx != -1 && deepIdx < cols.size) {
            cols[deepIdx].toIntOrNull()?.let { entry.deepSleepMin = it }
        }
    }

    private fun parseActivityRow(
        header: List<String>,
        cols: List<String>,
        map: MutableMap<LocalDate, ParsedRingConnData>
    ) {
        // Date,Steps,Calories(kcal)
        val dateIdx = header.indexOfFirst { it == "date" }
        if (dateIdx == -1 || dateIdx >= cols.size) return
        val date = runCatching { LocalDate.parse(cols[dateIdx], dateFormatter) }.getOrNull() ?: return

        val entry = map.getOrPut(date) { ParsedRingConnData(date) }
        val stepsIdx = header.indexOfFirst { it.contains("step") }
        if (stepsIdx != -1 && stepsIdx < cols.size) {
            cols[stepsIdx].toIntOrNull()?.let { entry.steps = it }
        }
    }

    /**
     * Converts parsed data into DailyInspectionData with computed sleep scores.
     */
    fun toDailyInspectionData(
        parsed: ParsedRingConnData,
        yesterdayLoad: Float? = null,
        sleepDebtMin: Int? = null
    ): DailyInspectionData {
        val sleepMetrics = if (parsed.sleepSecs != null && parsed.sleepSecs!! > 0) {
            val deep = parsed.deepSleepMin ?: 0
            val rem = parsed.remSleepMin ?: 0
            val light = parsed.lightSleepMin ?: 0
            val awake = parsed.awakeMin ?: 0
            val scoreResult = MetricCalculator.calculateSleepScore(
                sleepSecs = parsed.sleepSecs!!,
                deepMin = deep,
                remMin = rem,
                lightMin = light,
                awakeMin = awake,
                yesterdayLoad = yesterdayLoad,
                sleepDebtMin = sleepDebtMin
            )

            ProcessedSleepMetrics(
                sleepSecs = parsed.sleepSecs!!,
                deepSleepMinutes = deep,
                remSleepMinutes = rem,
                lightSleepMinutes = light,
                awakeMinutes = awake,
                sleepScore = scoreResult.score,
                sleepScoreLabel = scoreResult.label,
                onsetIso = parsed.onsetIso,
                wakeIso = parsed.wakeIso,
                isBlanketRemoved = false,
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
        } else {
            null
        }

        return DailyInspectionData(
            date = parsed.date,
            sleepMetrics = sleepMetrics,
            restingHR = parsed.restingHR,
            avgSleepingHR = parsed.avgHR,
            spO2 = parsed.spO2,
            hrv = parsed.hrv,
            respiration = null,
            skinTemp = null
        )
    }
}
