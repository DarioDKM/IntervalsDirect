package com.radfahrzeugs.intervalsdirect.engine

import com.radfahrzeugs.intervalsdirect.data.*
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object MetricCalculator {

    data class SleepScoreResult(
        val score: Int,
        val label: String,
        val dynamicNeedMin: Int,
        val strainAdditionMin: Int,
        val baseNeedMin: Int,
        val fulfillmentScore: Double,
        val deepScore: Double,
        val remScore: Double,
        val continuityScore: Double,
        val wasoScore: Double,
        val effRatioScore: Double,
        val fulfillmentRatio: Double,
        val yesterdayLoad: Float?
    )

    /**
     * Calculates dynamic athletic sleep need (in minutes) inspired by Whoop:
     * Baseline (~7.75h = 465 min) + Strain (from yesterday's training load) + Sleep Debt.
     */
    fun calculateDynamicSleepNeed(
        yesterdayLoad: Float? = null,
        sleepDebtMin: Int? = null
    ): Pair<Int, Int> {
        val baselineMin = 465 // 7h 45m
        val strainMin = when {
            yesterdayLoad == null || yesterdayLoad < 40f -> 0
            yesterdayLoad < 80f -> 25
            yesterdayLoad < 120f -> 50
            yesterdayLoad < 160f -> 75
            else -> 90 // 160+ load
        }
        val debtAddition = (sleepDebtMin?.coerceAtLeast(0) ?: 0).times(0.25).toInt().coerceAtMost(45)
        val totalNeed = baselineMin + strainMin + debtAddition
        return Pair(totalNeed, strainMin)
    }

    /**
     * Calculates an evidence-based Whoop-Style Athletic Sleep Score (1-100):
     * - Need Fulfillment (45 pts max, based on dynamic sleep need with strain adjustment)
     * - Deep Sleep Volume (20 pts max, absolute restorative minutes: target >= 90m)
     * - REM Sleep Volume (20 pts max, absolute restorative minutes: target >= 120m)
     * - Sleep Efficiency / Wakefulness (15 pts max, target >= 88%)
     */
    fun calculateSleepScore(
        sleepSecs: Int,
        deepMin: Int,
        remMin: Int,
        lightMin: Int,
        awakeMin: Int,
        yesterdayLoad: Float? = null,
        sleepDebtMin: Int? = null
    ): SleepScoreResult {
        val actualSleepMin = sleepSecs / 60.0
        val inBedMin = actualSleepMin + awakeMin

        // 1. Dynamic Sleep Need (45 points max)
        val (totalNeedMin, strainAdditionMin) = calculateDynamicSleepNeed(yesterdayLoad, sleepDebtMin)
        val fulfillmentRatio = if (totalNeedMin > 0) (actualSleepMin / totalNeedMin) else 1.0
        val fulfillmentScore = (fulfillmentRatio * 45.0).coerceIn(0.0, 45.0)

        // 2. Deep Sleep Volume (20 points max)
        val deepTargetMin = 90.0
        val deepScore = ((deepMin / deepTargetMin) * 20.0).coerceIn(0.0, 20.0)

        // 3. REM Sleep Volume (20 points max)
        val remTargetMin = 120.0
        val remScore = ((remMin / remTargetMin) * 20.0).coerceIn(0.0, 20.0)

        // 4. Sleep Efficiency & Wakefulness Continuity (15 points max)
        val efficiency = if (inBedMin > 0) (actualSleepMin / inBedMin) else 1.0
        val effRatioScore = ((efficiency / 0.88) * 8.0).coerceIn(0.0, 8.0)
        val wasoScore = when {
            awakeMin <= 20 -> 7.0
            awakeMin <= 40 -> 5.0
            awakeMin <= 60 -> 3.0
            else -> 1.0
        }
        val continuityScore = (effRatioScore + wasoScore).coerceIn(0.0, 15.0)

        val totalScoreRaw = fulfillmentScore + deepScore + remScore + continuityScore
        val finalScore = totalScoreRaw.roundToInt().coerceIn(1, 100)

        val label = when {
            finalScore >= 85 -> "Optimal"
            finalScore >= 70 -> "Gut"
            finalScore >= 50 -> "Befriedigend"
            else -> "Aufholbedarf"
        }

        return SleepScoreResult(
            score = finalScore,
            label = label,
            dynamicNeedMin = totalNeedMin,
            strainAdditionMin = strainAdditionMin,
            baseNeedMin = 465,
            fulfillmentScore = fulfillmentScore,
            deepScore = deepScore,
            remScore = remScore,
            continuityScore = continuityScore,
            wasoScore = wasoScore,
            effRatioScore = effRatioScore,
            fulfillmentRatio = fulfillmentRatio,
            yesterdayLoad = yesterdayLoad
        )
    }

    /**
     * Maps numeric Sleep Score (1-100) to Intervals.icu Sleep Quality (1=GREAT, 2=GOOD, 3=AVG, 4=POOR).
     */
    fun computeSleepQuality(sleepScore: Int?): Int? {
        if (sleepScore == null) return null
        return when {
            sleepScore >= 85 -> 1 // GREAT
            sleepScore >= 70 -> 2 // GOOD
            sleepScore >= 50 -> 3 // AVG
            else -> 4             // POOR
        }
    }

    /**
     * Builds the complete IntervalsWellnessPayload.
     */
    fun buildPayload(
        date: LocalDate,
        sleepMetrics: ProcessedSleepMetrics?,
        restingHR: Int?,
        avgSleepingHR: Int?,
        spO2: Float?,
        respiration: Float? = null,
        skinTemp: Float? = null,
        hrv: Float? = null
    ): IntervalsWellnessPayload {
        val quality = computeSleepQuality(sleepMetrics?.sleepScore)
        return IntervalsWellnessPayload(
            id = date.toString(),
            sleepSecs = sleepMetrics?.sleepSecs,
            sleepScore = sleepMetrics?.sleepScore,
            sleepQuality = quality,
            restingHR = restingHR,
            avgSleepingHR = avgSleepingHR,
            spO2 = spO2,
            respiration = respiration,
            skinTemp = skinTemp,
            hrv = hrv,
            deepSleep = sleepMetrics?.deepSleepMinutes,
            remSleep = sleepMetrics?.remSleepMinutes,
            lightSleep = sleepMetrics?.lightSleepMinutes
        )
    }

    /**
     * Exports a list of DailyInspectionData to CSV format.
     */
    fun exportToCsv(dataList: List<DailyInspectionData>): String {
        val sb = StringBuilder()
        sb.append("Date,Sleep_Hours,Sleep_Score,Deep_Sleep_Min,REM_Sleep_Min,Light_Sleep_Min,Awake_Min,Resting_HR,Sleep_Avg_HR,SpO2,HRV_rMSSD,Respiration,Skin_Temp\n")
        for (d in dataList.sortedByDescending { it.date }) {
            val sleepHours = d.sleepMetrics?.let { String.format(Locale.US, "%.2f", it.sleepSecs / 3600.0) } ?: ""
            val sleepScore = d.sleepMetrics?.sleepScore ?: ""
            val deep = d.sleepMetrics?.deepSleepMinutes ?: ""
            val rem = d.sleepMetrics?.remSleepMinutes ?: ""
            val light = d.sleepMetrics?.lightSleepMinutes ?: ""
            val awake = d.sleepMetrics?.awakeMinutes ?: ""
            val rhr = d.restingHR ?: ""
            val sleepHr = d.avgSleepingHR ?: ""
            val spo2 = d.spO2?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val hrv = d.hrv?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val resp = d.respiration?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val temp = d.skinTemp?.let { String.format(Locale.US, "%.2f", it) } ?: ""

            sb.append("${d.date},$sleepHours,$sleepScore,$deep,$rem,$light,$awake,$rhr,$sleepHr,$spo2,$hrv,$resp,$temp\n")
        }
        return sb.toString()
    }

    /**
     * Exports a list of DailyInspectionData to formatted JSON.
     */
    fun exportToJson(dataList: List<DailyInspectionData>): String {
        val jsonArray = org.json.JSONArray()
        for (d in dataList.sortedByDescending { it.date }) {
            val payload = buildPayload(
                date = d.date,
                sleepMetrics = d.sleepMetrics,
                restingHR = d.restingHR,
                avgSleepingHR = d.avgSleepingHR,
                spO2 = d.spO2,
                respiration = d.respiration,
                skinTemp = d.skinTemp,
                hrv = d.hrv
            )
            jsonArray.put(org.json.JSONObject(payload.toJsonString()))
        }
        return jsonArray.toString(2)
    }




    /**
     * Feature 3: Intraday-HRV-Analyse (36-Stunden-Zeitreihe)
     * Entschlüsselt das BLOB-Feld tsList36hrhrvs oder die 2.5-minütige Zeitreihe.
     */
    fun decodeTsList36hrHrvs(blob: ByteArray?, baseUtc: Long = 0L): List<IntradayHrvPoint> {
        if (blob == null || blob.isEmpty()) return emptyList()

        val decompressed = try {
            if (blob.size >= 2 && blob[0] == 0x1f.toByte() && blob[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(blob)).readBytes()
            } else if (blob.size >= 2 && blob[0] == 0x78.toByte()) {
                InflaterInputStream(ByteArrayInputStream(blob)).readBytes()
            } else {
                blob
            }
        } catch (e: Exception) {
            blob
        }

        val text = runCatching { String(decompressed, Charsets.UTF_8).trim() }.getOrNull()
        if (text != null && (text.startsWith("[") || text.startsWith("{"))) {
            val list = mutableListOf<IntradayHrvPoint>()
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            // Regex extraction works reliably on both Android runtime and JVM unit test runner
            val pointRegex = Regex("""(?:\{[^{}]*"utc"\s*:\s*(\d+)[^{}]*"hrv"\s*:\s*(\d+)|"hrv"\s*:\s*(\d+)[^{}]*"utc"\s*:\s*(\d+))""")
            val matches = pointRegex.findAll(text).toList()
            if (matches.isNotEmpty()) {
                for (m in matches) {
                    val utc = (m.groups[1]?.value ?: m.groups[4]?.value)?.toLongOrNull() ?: 0L
                    val hrv = (m.groups[2]?.value ?: m.groups[3]?.value)?.toIntOrNull() ?: 0
                    val instant = Instant.ofEpochSecond(utc)
                    val timeStr = timeFormatter.format(instant.atZone(ZoneId.systemDefault()))
                    list.add(IntradayHrvPoint(utc, timeStr, hrv, 0))
                }
                return list
            }

            if (text.startsWith("[") && !text.contains("{")) {
                val numbers = text.trim('[', ']').split(",").mapNotNull { it.trim().toIntOrNull() }
                if (numbers.isNotEmpty()) {
                    for ((idx, hrv) in numbers.withIndex()) {
                        val utc = baseUtc + idx * 150L
                        val instant = Instant.ofEpochSecond(utc)
                        val timeStr = timeFormatter.format(instant.atZone(ZoneId.systemDefault()))
                        list.add(IntradayHrvPoint(utc, timeStr, hrv, 0))
                    }
                    return list
                }
            }
        }

        val list = mutableListOf<IntradayHrvPoint>()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        if (decompressed.size % 2 == 0 && decompressed.size >= 20) {
            val buf = ByteBuffer.wrap(decompressed).order(ByteOrder.LITTLE_ENDIAN)
            var idx = 0
            while (buf.remaining() >= 2) {
                val hrvVal = buf.short.toInt() and 0xFFFF
                val utc = baseUtc + idx * 150L
                val instant = Instant.ofEpochSecond(utc)
                val timeStr = timeFormatter.format(instant.atZone(ZoneId.systemDefault()))
                if (hrvVal in 5..300) {
                    list.add(IntradayHrvPoint(utc, timeStr, hrvVal, 0))
                }
                idx++
            }
        } else {
            for (idx in decompressed.indices) {
                val hrvVal = decompressed[idx].toInt() and 0xFF
                val utc = baseUtc + idx * 150L
                val instant = Instant.ofEpochSecond(utc)
                val timeStr = timeFormatter.format(instant.atZone(ZoneId.systemDefault()))
                if (hrvVal in 5..250) {
                    list.add(IntradayHrvPoint(utc, timeStr, hrvVal, 0))
                }
            }
        }
        return list
    }

    /**
     * Berechnet die Parasympathikus-Aktivierung und Erholungs-Dynamik während des Schlafs.
     */
    fun analyzeParasympatheticRecovery(
        points: List<IntradayHrvPoint>,
        bedtimeStartUtc: Long? = null,
        bedtimeEndUtc: Long? = null
    ): ParasympatheticRecoveryAnalysis {
        val filtered = if (bedtimeStartUtc != null && bedtimeEndUtc != null && bedtimeEndUtc > bedtimeStartUtc) {
            points.filter { it.utcTs in bedtimeStartUtc..bedtimeEndUtc && it.hrv > 0 }
        } else {
            points.filter { it.hrv > 0 }
        }

        if (filtered.size < 4) {
            return ParasympatheticRecoveryAnalysis(
                totalPoints = filtered.size,
                meanHrv = filtered.map { it.hrv }.average().toFloat().takeIf { !it.isNaN() } ?: 0.0f,
                earlySleepHrv = 0.0f,
                lateSleepHrv = 0.0f,
                recoveryRatio = 1.0f,
                restorativeMinutes = 0,
                assessment = "Unzureichende Datenpunkte für eine nächtliche Zeitreihenanalyse."
            )
        }

        val half = filtered.size / 2
        val firstHalf = filtered.subList(0, half)
        val secondHalf = filtered.subList(half, filtered.size)

        val earlyAvg = firstHalf.map { it.hrv }.average().toFloat()
        val lateAvg = secondHalf.map { it.hrv }.average().toFloat()
        val totalAvg = filtered.map { it.hrv }.average().toFloat()

        val ratio = if (earlyAvg > 0f) lateAvg / earlyAvg else 1.0f
        val restorativeMin = (filtered.count { it.hrv >= 60 } * 2.5).toInt()

        val assessment = when {
            earlyAvg >= 60f && lateAvg >= 60f -> "Exzellente Parasympathikus-Aktivierung über die gesamte Nacht. Frühe und stabile autonome Erholung."
            ratio >= 1.25f || (earlyAvg < 55f && lateAvg >= 60f) -> "Verzögerte Erholung: Der Parasympathikus stieg erst in der zweiten Nachthälfte an. Vorbelastung oder späte Mahlzeit verzögerten das Einschlafen."
            earlyAvg < 50f && lateAvg < 50f -> "Durchgehend gedämpfte vegetative Erholung. Hohe sympathische Aktivität in der Nacht."
            else -> "Ausgeglichene nächtliche Erholungskurve im Normbereich."
        }

        return ParasympatheticRecoveryAnalysis(
            totalPoints = filtered.size,
            meanHrv = totalAvg,
            earlySleepHrv = earlyAvg,
            lateSleepHrv = lateAvg,
            recoveryRatio = ratio,
            restorativeMinutes = restorativeMin,
            assessment = assessment
        )
    }
}
