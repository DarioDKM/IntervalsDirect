package com.radfahrzeugs.intervalsdirect.data

import android.content.Context
import android.net.Uri
import com.radfahrzeugs.intervalsdirect.engine.MetricCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RingConnProviderRepository(private val context: Context) {

    companion object {
        const val AUTH_DEBUG = "com.gdjztech.ringconn.debug.provider"
        const val AUTH_STANDARD = "com.gdjztech.ringconn.provider"

        val CANDIDATES = listOf(AUTH_STANDARD, AUTH_DEBUG)

        // Legacy compatibility
        val AUTHORITY_URI: Uri = Uri.parse("content://$AUTH_DEBUG")
        val SLEEP_URI: Uri = Uri.parse("content://$AUTH_DEBUG/sleep")
        val DAILY_URI: Uri = Uri.parse("content://$AUTH_DEBUG/daily")
        val HISTORY_HR_URI: Uri = Uri.parse("content://$AUTH_DEBUG/history_hr")
    }

    private var cachedAuthority: String? = null

    private fun resolveAuthority(): String {
        cachedAuthority?.let { return it }
        for (candidate in CANDIDATES) {
            try {
                val testUri = Uri.parse("content://$candidate/sleep")
                val cursor = context.contentResolver.query(testUri, arrayOf("dateSleep"), null, null, null)
                if (cursor != null) {
                    cursor.close()
                    cachedAuthority = candidate
                    return candidate
                }
            } catch (e: Exception) {
                // Try next
            }
        }
        return AUTH_DEBUG
    }

    val dynamicAuthorityUri: Uri get() = Uri.parse("content://${resolveAuthority()}")
    val dynamicSleepUri: Uri get() = Uri.parse("content://${resolveAuthority()}/sleep")
    val dynamicDailyUri: Uri get() = Uri.parse("content://${resolveAuthority()}/daily")
    val dynamicHistoryHrUri: Uri get() = Uri.parse("content://${resolveAuthority()}/history_hr")

    suspend fun triggerGhostSync(): Boolean = withContext(Dispatchers.IO) {
        try {
            val bundle = context.contentResolver.call(dynamicAuthorityUri, "triggerSync", null, null)
            bundle?.getBoolean("success", false) == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun fetchIntradayHrv(
        targetDate: LocalDate,
        bedtimeStartUtc: Long? = null,
        bedtimeEndUtc: Long? = null
    ): List<IntradayHrvPoint> = withContext(Dispatchers.IO) {
        val list = mutableListOf<IntradayHrvPoint>()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        try {
            val projection = arrayOf("utcTs", "hrv", "pr", "resprate", "actiCount")
            val startUtc = bedtimeStartUtc ?: targetDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
            val endUtc = bedtimeEndUtc ?: targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

            val selection = "utcTs >= ? AND utcTs <= ? AND hrv > 0"
            val selectionArgs = arrayOf(startUtc.toString(), endUtc.toString())
            val sortOrder = "utcTs ASC"

            val cursor = context.contentResolver.query(
                dynamicHistoryHrUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val utcIdx = it.getColumnIndex("utcTs")
                val hrvIdx = it.getColumnIndex("hrv")
                val prIdx = it.getColumnIndex("pr")
                val rrIdx = it.getColumnIndex("resprate")
                val actiIdx = it.getColumnIndex("actiCount")

                while (it.moveToNext()) {
                    val utc = it.getLong(utcIdx)
                    val hrv = it.getInt(hrvIdx)
                    val pr = it.getInt(prIdx)
                    val rawRr = if (rrIdx >= 0 && !it.isNull(rrIdx)) it.getFloat(rrIdx) else null
                    val rr = rawRr?.let { r -> if (r > 0) Math.round(r / 8.0f * 10f) / 10f else null }
                    val acti = if (actiIdx >= 0 && !it.isNull(actiIdx)) it.getInt(actiIdx) else null

                    val timeStr = timeFormatter.format(Instant.ofEpochSecond(utc).atZone(ZoneId.systemDefault()))
                    list.add(IntradayHrvPoint(utc, timeStr, hrv, pr, rr, acti))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun fetchDailyData(targetDate: LocalDate): DailyInspectionData = withContext(Dispatchers.IO) {
        val dateStr = targetDate.toString() // YYYY-MM-DD
        var restingHr: Int? = null
        var avgSleepingHr: Int? = null
        var hrv: Float? = null
        var respiration: Float? = null
        var skinTemp: Float? = null
        var tempBenchmark: Float? = null
        var tempOffset: Float? = null
        var spo2: Float? = null
        var sleepScore: Int? = null
        var sleepSecs: Int? = null
        var deepMin: Int? = null
        var remMin: Int? = null
        var lightMin: Int? = null
        var awakeMin: Int? = null
        var bedtimeStartUtc: Long? = null
        var bedtimeEndUtc: Long? = null
        var blobHrvs: ByteArray? = null

        val projection = arrayOf(
            "dateSleep",
            "restingHr",
            "hrAvg",
            "hrvAvg",
            "rrAvg",
            "tempBenchmark",
            "tempOffset",
            "deepDuration",
            "remDuration",
            "lightDuration",
            "awakeDuration",
            "sleepDuration",
            "spo2Avg",
            "sleepScore",
            "bedtimeStart",
            "bedtimeEnd",
            "tsList36hrhrvs"
        )

        try {
            val cursor = context.contentResolver.query(
                dynamicSleepUri,
                projection,
                "dateSleep = ?",
                arrayOf(dateStr),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val rhrIdx = it.getColumnIndex("restingHr")
                    val hrAvgIdx = it.getColumnIndex("hrAvg")
                    val hrvIdx = it.getColumnIndex("hrvAvg")
                    val rrIdx = it.getColumnIndex("rrAvg")
                    val tempBenchIdx = it.getColumnIndex("tempBenchmark")
                    val tempOffsetIdx = it.getColumnIndex("tempOffset")
                    val deepIdx = it.getColumnIndex("deepDuration")
                    val remIdx = it.getColumnIndex("remDuration")
                    val lightIdx = it.getColumnIndex("lightDuration")
                    val awakeIdx = it.getColumnIndex("awakeDuration")
                    val sleepDurIdx = it.getColumnIndex("sleepDuration")
                    val spo2Idx = it.getColumnIndex("spo2Avg")
                    val scoreIdx = it.getColumnIndex("sleepScore")
                    val bStartIdx = it.getColumnIndex("bedtimeStart")
                    val bEndIdx = it.getColumnIndex("bedtimeEnd")
                    val blobIdx = it.getColumnIndex("tsList36hrhrvs")

                    if (rhrIdx >= 0 && !it.isNull(rhrIdx)) restingHr = it.getInt(rhrIdx)
                    if (hrAvgIdx >= 0 && !it.isNull(hrAvgIdx)) avgSleepingHr = it.getFloat(hrAvgIdx).toInt()
                    if (hrvIdx >= 0 && !it.isNull(hrvIdx)) hrv = it.getFloat(hrvIdx)
                    if (rrIdx >= 0 && !it.isNull(rrIdx)) {
                        val rawRr = it.getFloat(rrIdx)
                        if (rawRr > 0) respiration = Math.round((rawRr / 8.0f) * 10.0f) / 10.0f
                    }
                    if (tempBenchIdx >= 0 && !it.isNull(tempBenchIdx)) {
                        tempBenchmark = Math.round(it.getFloat(tempBenchIdx) * 100.0f) / 100.0f
                    }
                    if (tempOffsetIdx >= 0 && !it.isNull(tempOffsetIdx)) {
                        tempOffset = Math.round(it.getFloat(tempOffsetIdx) * 100.0f) / 100.0f
                    }
                    if (tempBenchmark != null && tempOffset != null) {
                        skinTemp = Math.round((tempBenchmark!! + tempOffset!!) * 100.0f) / 100.0f
                    }
                    if (deepIdx >= 0 && !it.isNull(deepIdx)) deepMin = it.getFloat(deepIdx).toInt()
                    if (remIdx >= 0 && !it.isNull(remIdx)) remMin = it.getFloat(remIdx).toInt()
                    if (lightIdx >= 0 && !it.isNull(lightIdx)) lightMin = it.getFloat(lightIdx).toInt()
                    if (awakeIdx >= 0 && !it.isNull(awakeIdx)) awakeMin = it.getFloat(awakeIdx).toInt()
                    if (sleepDurIdx >= 0 && !it.isNull(sleepDurIdx)) {
                        val durMin = it.getFloat(sleepDurIdx).toInt()
                        sleepSecs = durMin * 60
                    }
                    if (spo2Idx >= 0 && !it.isNull(spo2Idx)) spo2 = it.getFloat(spo2Idx)
                    if (scoreIdx >= 0 && !it.isNull(scoreIdx)) sleepScore = it.getFloat(scoreIdx).toInt()
                    if (bStartIdx >= 0 && !it.isNull(bStartIdx)) bedtimeStartUtc = it.getLong(bStartIdx)
                    if (bEndIdx >= 0 && !it.isNull(bEndIdx)) bedtimeEndUtc = it.getLong(bEndIdx)
                    if (blobIdx >= 0 && !it.isNull(blobIdx)) blobHrvs = it.getBlob(blobIdx)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fetch intraday HRV time series: first try HistoryHrSyncInfo via ContentProvider, fallback to blob
        val intradayPoints = fetchIntradayHrv(targetDate, bedtimeStartUtc, bedtimeEndUtc).ifEmpty {
            if (blobHrvs != null && blobHrvs!!.isNotEmpty()) {
                MetricCalculator.decodeTsList36hrHrvs(blobHrvs, bedtimeStartUtc ?: 0L)
            } else emptyList()
        }

        val parasympatheticAnalysis = if (intradayPoints.isNotEmpty()) {
            MetricCalculator.analyzeParasympatheticRecovery(intradayPoints, bedtimeStartUtc, bedtimeEndUtc)
        } else null

        val sleepMetrics = if (sleepSecs != null && sleepSecs!! > 0) {
            ProcessedSleepMetrics(
                sleepSecs = sleepSecs!!,
                deepSleepMinutes = deepMin ?: 0,
                remSleepMinutes = remMin ?: 0,
                lightSleepMinutes = lightMin ?: 0,
                awakeMinutes = awakeMin ?: 0,
                sleepScore = sleepScore,
                sleepScoreLabel = when {
                    sleepScore == null -> null
                    sleepScore!! >= 85 -> "Optimal"
                    sleepScore!! >= 70 -> "Gut"
                    else -> "Aufholbedarf"
                },
                onsetIso = null,
                wakeIso = null,
                isBlanketRemoved = false
            )
        } else null

        DailyInspectionData(
            date = targetDate,
            sleepMetrics = sleepMetrics,
            restingHR = restingHr,
            avgSleepingHR = avgSleepingHr,
            spO2 = spo2,
            respiration = respiration,
            skinTemp = skinTemp,
            tempBenchmark = tempBenchmark,
            tempOffset = tempOffset,
            hrv = hrv,
            intradayHrv = intradayPoints.takeIf { it.isNotEmpty() },
            parasympatheticAnalysis = parasympatheticAnalysis
        )
    }

    suspend fun fetchHistoryDays(count: Int): List<DailyInspectionData> = withContext(Dispatchers.IO) {
        val list = mutableListOf<DailyInspectionData>()
        val today = LocalDate.now()
        for (i in 0 until count) {
            val date = today.minusDays(i.toLong())
            val data = fetchDailyData(date)
            if (data.sleepMetrics != null || data.restingHR != null || data.hrv != null) {
                list.add(data)
            }
        }
        list
    }
}
