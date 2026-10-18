package com.callrecorder.app.core.audio

import kotlin.math.max
import kotlin.math.min

/**
 * Applies digital software amplification and soft-limiting to raw 16-bit PCM audio samples.
 * Amplifies caller speech received via earpiece/speaker while preventing harsh audio clipping.
 */
class AudioGainProcessor(
    private var gainMultiplier: Float = 1.8f
) {

    fun setGainMultiplier(multiplier: Float) {
        this.gainMultiplier = max(1.0f, min(multiplier, 5.0f))
    }

    fun getGainMultiplier(): Float = gainMultiplier

    /**
     * Processes in-place a buffer of 16-bit PCM byte array.
     * Little-endian sample format: byte0 = low byte, byte1 = high byte.
     */
    fun processPcmBuffer(buffer: ByteArray, bytesRead: Int) {
        if (gainMultiplier <= 1.01f) return

        var i = 0
        while (i < bytesRead - 1) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low

            // Amplify sample with soft saturation limiting
            val amplified = sample * gainMultiplier
            val clamped = when {
                amplified > 32767f -> 32767
                amplified < -32768f -> -32768
                else -> amplified.toInt()
            }

            buffer[i] = (clamped and 0xFF).toByte()
            buffer[i + 1] = ((clamped shr 8) and 0xFF).toByte()

            i += 2
        }
    }

    /**
     * Calculates the Root-Mean-Square (RMS) amplitude normalized to 0.0f - 1.0f.
     */
    fun calculateNormalizedAmplitude(buffer: ByteArray, bytesRead: Int): Float {
        if (bytesRead < 2) return 0.0f

        var sumSquare = 0.0
        var sampleCount = 0

        var i = 0
        while (i < bytesRead - 1) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low

            sumSquare += (sample * sample).toDouble()
            sampleCount++
            i += 2
        }

        if (sampleCount == 0) return 0.0f
        val rms = kotlin.math.sqrt(sumSquare / sampleCount)
        return (rms / 32768.0).toFloat().coerceIn(0.0f, 1.0f)
    }
}
