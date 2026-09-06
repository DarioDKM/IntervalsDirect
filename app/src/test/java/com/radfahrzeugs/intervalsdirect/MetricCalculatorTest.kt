package com.radfahrzeugs.intervalsdirect

import com.radfahrzeugs.intervalsdirect.data.*
import com.radfahrzeugs.intervalsdirect.engine.MetricCalculator
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate

class MetricCalculatorTest {

    @Test
    fun testSleepScoreCalculation() {
        val score = MetricCalculator.calculateSleepScore(
            sleepSecs = 7 * 3600 + 45 * 60, // 7h 45m
            deepMin = 105,
            remMin = 95,
            lightMin = 265,
            awakeMin = 25,
            yesterdayLoad = 85.0f
        )
        assertTrue("Sleep score should be >= 80", score.score >= 80)
        assertTrue("Dynamic need should include strain addition", score.dynamicNeedMin > 480)
        assertEquals("Yesterday load should match input", 85.0f, score.yesterdayLoad ?: 0f, 0.01f)
    }

    @Test
    fun testIndependentPhaseRounding() {
        val deepSecs = 6900L
        val remSecs = 5850L
        val lightSecs = 20250L

        val deepMin = Math.round(deepSecs / 60.0).toInt()
        val remMin = Math.round(remSecs / 60.0).toInt()
        val lightMin = Math.round(lightSecs / 60.0).toInt()

        assertEquals(115, deepMin)
        assertEquals(98, remMin)
        assertEquals(338, lightMin)
        assertEquals(551, deepMin + remMin + lightMin)
    }

    @Test
    fun testBuildPayloadClearsSteps() {
        val payload = MetricCalculator.buildPayload(
            date = LocalDate.of(2026, 9, 6),
            sleepMetrics = null,
            restingHR = 50,
            avgSleepingHR = 52,
            spO2 = 98f,
            respiration = 15f,
            skinTemp = 0.1f,
            hrv = 65f
        )
        val json = org.json.JSONObject(payload.toJsonString())
        assertEquals(-1, json.optInt("steps"))
        assertEquals(50, json.optInt("restingHR"))
        assertEquals(65.0, json.optDouble("hrv"), 0.01)
    }

    @Test
    fun testExportToCsv() {
        val sample = DailyInspectionData(
            date = LocalDate.of(2026, 9, 6),
            sleepMetrics = ProcessedSleepMetrics(
                sleepSecs = 8 * 3600,
                deepSleepMinutes = 90,
                remSleepMinutes = 110,
                lightSleepMinutes = 280,
                awakeMinutes = 20,
                sleepScore = 85,
                onsetIso = null,
                wakeIso = null,
                isBlanketRemoved = false
            ),
            restingHR = 49,
            avgSleepingHR = 52,
            spO2 = 98.5f,
            hrv = 68f
        )
        val csv = MetricCalculator.exportToCsv(listOf(sample))
        assertTrue(csv.contains("2026-09-06"))
        assertTrue(csv.contains("8.00"))
        assertTrue(csv.contains("85"))
        assertTrue(csv.contains("49"))
    }
    fun testDecodeTsList36hrHrvsJson() {
        val jsonString = """
            [
                {"utc": 1788460000, "hrv": 62, "pr": 55},
                {"utc": 1788460150, "hrv": 65, "pr": 54},
                {"utc": 1788460300, "hrv": 58, "pr": 56}
            ]
        """.trimIndent()
        val blob = jsonString.toByteArray(Charsets.UTF_8)

        val points = MetricCalculator.decodeTsList36hrHrvs(blob)
        assertEquals(3, points.size)
        assertEquals(62, points[0].hrv)
        assertEquals(65, points[1].hrv)
        assertEquals(58, points[2].hrv)
    }

    @Test
    fun testDecodeTsList36hrHrvsBinaryUint16() {
        val buffer = ByteBuffer.allocate(60).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 30) {
            buffer.putShort((60 + (i % 10)).toShort())
        }
        val blob = buffer.array()

        val points = MetricCalculator.decodeTsList36hrHrvs(blob, baseUtc = 1788460000L)
        assertEquals(30, points.size)
        assertEquals(60, points[0].hrv)
        assertEquals(69, points[9].hrv)
    }

    @Test
    fun testParasympatheticRecoveryAnalysis() {
        val points = listOf(
            IntradayHrvPoint(1788460000L, "00:00", 50, 58),
            IntradayHrvPoint(1788460150L, "00:02", 52, 57),
            IntradayHrvPoint(1788460300L, "00:05", 51, 57),
            IntradayHrvPoint(1788460450L, "00:07", 70, 52),
            IntradayHrvPoint(1788460600L, "00:10", 72, 51),
            IntradayHrvPoint(1788460750L, "00:12", 75, 50)
        )

        val analysis = MetricCalculator.analyzeParasympatheticRecovery(points)
        assertEquals(6, analysis.totalPoints)
        assertEquals(51.0f, analysis.earlySleepHrv, 0.1f)
        assertEquals(72.33f, analysis.lateSleepHrv, 0.1f)
        assertTrue("Recovery ratio should be > 1.3", analysis.recoveryRatio > 1.3f)
        assertTrue("Should detect delayed or increasing recovery", analysis.assessment.contains("Verzögerte Erholung") || analysis.assessment.contains("Parasympathikus"))
    }
}
