package com.callrecorder.app.core.audio

import java.io.File
import java.io.FileInputStream
import kotlin.math.abs

/**
 * Extracts normalized amplitude bars (0.0f - 1.0f) from audio data or PCM buffers
 * for rendering visual waveforms.
 */
object WaveformExtractor {

    /**
     * Extracts a fixed number of normalized amplitude points from a raw PCM file.
     */
    fun extractAmplitudesFromPcm(pcmFile: File, barCount: Int = 50): List<Float> {
        if (!pcmFile.exists() || pcmFile.length() < 2) {
            return generateMockWaveform(barCount)
        }

        val amplitudes = ArrayList<Float>()
        val totalBytes = pcmFile.length()
        val bytesPerBucket = (totalBytes / barCount).toInt().coerceAtLeast(2)

        try {
            FileInputStream(pcmFile).use { fis ->
                val buffer = ByteArray(bytesPerBucket)
                var read: Int
                while (fis.read(buffer).also { read = it } > 0) {
                    var maxSample = 0
                    var i = 0
                    while (i < read - 1) {
                        val low = buffer[i].toInt() and 0xFF
                        val high = buffer[i + 1].toInt()
                        val sample = abs((high shl 8) or low)
                        if (sample > maxSample) {
                            maxSample = sample
                        }
                        i += 2
                    }
                    val normalized = (maxSample / 32768.0f).coerceIn(0.05f, 1.0f)
                    amplitudes.add(normalized)
                }
            }
        } catch (_: Exception) {
            return generateMockWaveform(barCount)
        }

        // Fill or trim to barCount
        while (amplitudes.size < barCount) {
            amplitudes.add(0.15f)
        }
        return amplitudes.take(barCount)
    }

    /**
     * Generates a realistic mock waveform for previews or fallback playback.
     */
    fun generateMockWaveform(barCount: Int = 50): List<Float> {
        val pattern = floatArrayOf(
            0.15f, 0.35f, 0.65f, 0.45f, 0.85f, 0.95f, 0.75f, 0.40f, 0.25f, 0.55f,
            0.70f, 0.85f, 0.60f, 0.30f, 0.20f, 0.50f, 0.90f, 0.65f, 0.40f, 0.15f
        )
        return List(barCount) { index ->
            pattern[index % pattern.size]
        }
    }
}
