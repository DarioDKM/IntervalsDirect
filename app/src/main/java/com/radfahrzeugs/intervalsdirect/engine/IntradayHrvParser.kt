package com.radfahrzeugs.intervalsdirect.engine

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * Modell fuer einen hochaufgeloesten 2.5-minuetigen Messpunkt aus RingConn.
 */
data class IntradayPoint(
    val timestampUtc: Long,
    val hrv: Int,
    val pulseRate: Int = 0,
    val respirationRate: Double? = null
)

/**
 * Sportwissenschaftliche Bewertung der naechtlichen Vagus- und Parasympathikus-Aktivierung.
 */
data class AutonomicRecoveryAnalysis(
    val meanHrv: Double,
    val earlyHrv: Double,
    val lateHrv: Double,
    val recoveryRatio: Double,
    val restorativeMinutes: Int,
    val assessment: String
)

/**
 * Schluesselfertiger Dekodierer fuer die tsList36hrhrvs BLOBs aus RingConn SQLite (SleepSyncModel).
 * Unterstuetzt GZIP, ZLIB, JSON-Arrays und binaere Little-Endian uint16-Streams.
 */
object IntradayHrvParser {

    fun parseBlob(blob: ByteArray?): List<IntradayPoint> {
        if (blob == null || blob.isEmpty()) return emptyList()

        // 1. Dekompression pruefen
        val decompressed = decompressIfNeeded(blob)

        // 2. Versuch als UTF-8 Text / JSON zu parsen
        val jsonPoints = parseTextArray(decompressed)
        if (jsonPoints.isNotEmpty()) {
            return jsonPoints
        }

        // 3. Versuch als binaeres uint16 Array (Little-Endian)
        return parseBinaryUint16(decompressed)
    }

    private fun decompressIfNeeded(data: ByteArray): ByteArray {
        if (data.size < 2) return data

        // GZIP Magic Bytes: 0x1f 0x8b
        if (data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            return try {
                GZIPInputStream(ByteArrayInputStream(data)).readBytes()
            } catch (e: Exception) {
                data
            }
        }

        // ZLIB Magic Bytes: 0x78 0x9c, 0x78 0x01, 0x78 0xda
        if (data[0] == 0x78.toByte() && (data[1] == 0x9c.toByte() || data[1] == 0x01.toByte() || data[1] == 0xda.toByte())) {
            return try {
                InflaterInputStream(ByteArrayInputStream(data)).readBytes()
            } catch (e: Exception) {
                data
            }
        }

        return data
    }

    private fun parseTextArray(data: ByteArray): List<IntradayPoint> {
        return try {
            val text = String(data, Charsets.UTF_8).trim()
            if (!text.startsWith("[") && !text.startsWith("{")) return emptyList()

            val list = mutableListOf<IntradayPoint>()

            // Einfaches Array: [60, 65, 72, ...]
            if (text.startsWith("[") && !text.contains("{")) {
                val cleaned = text.removeSurrounding("[", "]")
                val tokens = cleaned.split(",")
                for (token in tokens) {
                    val num = token.trim().toIntOrNull()
                    if (num != null && num in 5..300) {
                        list.add(IntradayPoint(0L, num))
                    }
                }
                return list
            }

            // Fallback fuer Objekt-Array mit regex: {"utc":..., "hrv":...}
            val pattern = Regex("""\"hrv\"\s*:\s*(\d+)""")
            val matches = pattern.findAll(text)
            for (m in matches) {
                val hrv = m.groupValues[1].toIntOrNull()
                if (hrv != null && hrv in 5..300) {
                    list.add(IntradayPoint(0L, hrv))
                }
            }

            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseBinaryUint16(data: ByteArray): List<IntradayPoint> {
        if (data.size < 4 || data.size % 2 != 0) return emptyList()

        val list = mutableListOf<IntradayPoint>()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        while (buffer.remaining() >= 2) {
            val value = buffer.short.toInt() and 0xFFFF
            if (value in 5..300) {
                list.add(IntradayPoint(0L, value))
            }
        }
        return list
    }

    fun analyzeRecovery(points: List<IntradayPoint>): AutonomicRecoveryAnalysis? {
        val valid = points.filter { it.hrv > 0 }
        if (valid.isEmpty()) return null

        val total = valid.size
        val half = total / 2
        val firstHalf = valid.subList(0, half)
        val secondHalf = valid.subList(half, total)

        val meanEarly = if (firstHalf.isNotEmpty()) firstHalf.map { it.hrv }.average() else 0.0
        val meanLate = if (secondHalf.isNotEmpty()) secondHalf.map { it.hrv }.average() else 0.0
        val meanTotal = valid.map { it.hrv }.average()

        val ratio = if (meanEarly > 0.0) meanLate / meanEarly else 1.0
        val restorativeMinutes = valid.count { it.hrv >= 60 } * 2

        val assessment = when {
            meanEarly >= 60.0 && meanLate >= 60.0 ->
                "Exzellente Parasympathikus-Aktivierung ueber die gesamte Nacht. Fruehe und stabile autonome Erholung."
            ratio >= 1.25 || (meanEarly < 55.0 && meanLate >= 60.0) ->
                "Verzoegerte Erholung: Parasympathikus stieg erst in der zweiten Nachthaelfte an."
            meanEarly < 50.0 && meanLate < 50.0 ->
                "Durchgehend gedaempfte vegetative Erholung. Hohe sympathische Aktivitaet."
            else ->
                "Ausgeglichene naechtliche Erholungskurve im Normbereich."
        }

        return AutonomicRecoveryAnalysis(
            meanHrv = meanTotal,
            earlyHrv = meanEarly,
            lateHrv = meanLate,
            recoveryRatio = ratio,
            restorativeMinutes = restorativeMinutes,
            assessment = assessment
        )
    }
}
