package com.radfahrzeugs.intervalsdirect

import com.radfahrzeugs.intervalsdirect.engine.IntradayHrvParser
import com.radfahrzeugs.intervalsdirect.engine.IntradayPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IntradayHrvParserTest {

    @Test
    fun testParseJsonArray() {
        val json = "[60, 65, 72, 80, 55, 68]".toByteArray(Charsets.UTF_8)
        val points = IntradayHrvParser.parseBlob(json)
        assertEquals(6, points.size)
        assertEquals(60, points[0].hrv)
        assertEquals(80, points[3].hrv)
    }

    @Test
    fun testParseBinaryUint16() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(50.toShort())
        buffer.putShort(75.toShort())
        buffer.putShort(90.toShort())
        buffer.putShort(62.toShort())

        val points = IntradayHrvParser.parseBlob(buffer.array())
        assertEquals(4, points.size)
        assertEquals(50, points[0].hrv)
        assertEquals(75, points[1].hrv)
        assertEquals(90, points[2].hrv)
        assertEquals(62, points[3].hrv)
    }

    @Test
    fun testAnalyzeRecoveryDynamics() {
        val points = listOf(
            IntradayPoint(0L, 65),
            IntradayPoint(0L, 70),
            IntradayPoint(0L, 72),
            IntradayPoint(0L, 75)
        )
        val analysis = IntradayHrvParser.analyzeRecovery(points)
        assertNotNull(analysis)
        assertTrue(analysis!!.meanHrv > 60.0)
        assertTrue(analysis.recoveryRatio > 0.9)
    }
}
