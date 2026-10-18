package com.callrecorder.app

import com.callrecorder.app.core.audio.AudioGainProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGainProcessorTest {

    @Test
    fun testGainMultiplierClamping() {
        val processor = AudioGainProcessor(1.8f)
        assertEquals(1.8f, processor.getGainMultiplier(), 0.001f)

        // Test lower bound clamping to 1.0f
        processor.setGainMultiplier(0.5f)
        assertEquals(1.0f, processor.getGainMultiplier(), 0.001f)

        // Test upper bound clamping to 5.0f
        processor.setGainMultiplier(10.0f)
        assertEquals(5.0f, processor.getGainMultiplier(), 0.001f)
    }

    @Test
    fun testPcmBufferAmplification() {
        val processor = AudioGainProcessor(2.0f)

        // 16-bit sample = 1000 (0x03E8 in little endian: low=0xE8, high=0x03)
        val sample = 1000
        val buffer = byteArrayOf(
            (sample and 0xFF).toByte(),
            ((sample shr 8) and 0xFF).toByte()
        )

        processor.processPcmBuffer(buffer, 2)

        val amplifiedLow = buffer[0].toInt() and 0xFF
        val amplifiedHigh = buffer[1].toInt()
        val amplifiedSample = (amplifiedHigh shl 8) or amplifiedLow

        // Expect sample * 2.0 = 2000
        assertEquals(2000, amplifiedSample)
    }

    @Test
    fun testSoftClippingOnSaturation() {
        val processor = AudioGainProcessor(3.0f)

        // 16-bit sample = 20000. Multiplied by 3 = 60000, which exceeds max short (32767).
        val sample = 20000
        val buffer = byteArrayOf(
            (sample and 0xFF).toByte(),
            ((sample shr 8) and 0xFF).toByte()
        )

        processor.processPcmBuffer(buffer, 2)

        val amplifiedLow = buffer[0].toInt() and 0xFF
        val amplifiedHigh = buffer[1].toInt()
        val amplifiedSample = (amplifiedHigh shl 8) or amplifiedLow

        // Must clamp to short maximum 32767
        assertEquals(32767, amplifiedSample)
    }

    @Test
    fun testRmsAmplitudeCalculation() {
        val processor = AudioGainProcessor()

        // Silence buffer
        val silenceBuffer = ByteArray(100) { 0 }
        val silenceAmp = processor.calculateNormalizedAmplitude(silenceBuffer, 100)
        assertEquals(0.0f, silenceAmp, 0.001f)

        // Buffer with active samples
        val activeBuffer = ByteArray(20)
        for (i in 0 until 10) {
            val sample = 16384 // Half max amplitude
            activeBuffer[i * 2] = (sample and 0xFF).toByte()
            activeBuffer[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        val activeAmp = processor.calculateNormalizedAmplitude(activeBuffer, 20)
        assertTrue(activeAmp in 0.49f..0.51f)
    }
}
