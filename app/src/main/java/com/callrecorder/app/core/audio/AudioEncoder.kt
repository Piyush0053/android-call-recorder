package com.callrecorder.app.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

/**
 * Encodes raw 16-bit PCM audio samples into an MPEG-4 container with AAC-LC audio (.m4a).
 * Uses hardware/software MediaCodec and MediaMuxer for compact, high-efficiency speech files.
 */
class AudioEncoder(
    private val outputFile: File,
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 1,
    private val bitrate: Int = 96000
) {

    companion object {
        private const val TAG = "AudioEncoder"
    }

    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var trackIndex = -1
    private var isMuxerStarted = false
    private var presentationTimeUs = 0L

    @Synchronized
    fun start() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        mediaCodec = codec

        mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        presentationTimeUs = 0L
        isMuxerStarted = false
        trackIndex = -1
        Log.i(TAG, "AudioEncoder started: file=${outputFile.name}, sr=$sampleRate, bitrate=$bitrate")
    }

    /**
     * Feeds raw PCM bytes into the encoder and drains available encoded AAC packets into the muxer.
     *
     * Internally chunks [pcmData] into segments that fit the codec's actual input-buffer
     * capacity so that a large pre-roll flush (potentially hundreds of KB) never causes a
     * [java.nio.BufferOverflowException] at [java.nio.DirectByteBuffer.put].
     */
    @Synchronized
    fun encodePcm(pcmData: ByteArray, length: Int, isEndOfStream: Boolean = false) {
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return

        val bytesPerSample = 2 * channelCount

        var offset = 0
        var retries = 0
        while (offset < length) {
            val inputBufferIndex = codec.dequeueInputBuffer(10_000)
            if (inputBufferIndex < 0) {
                // Codec not ready yet — drain output and retry next iteration
                drainEncoder(codec, muxer, false)
                retries++
                if (retries > 50) {
                    Log.w(TAG, "dequeueInputBuffer timed out after 50 attempts; dropping remaining chunk")
                    break
                }
                continue
            }
            retries = 0

            val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: break
            inputBuffer.clear()

            // Determine how many bytes this codec buffer can accept, aligned to sample frames
            val bufCap = inputBuffer.capacity()
            val remaining = length - offset
            var chunkSize = minOf(remaining, bufCap)
            // Align to a whole PCM frame boundary to avoid half-sample writes
            chunkSize = (chunkSize / bytesPerSample) * bytesPerSample
            if (chunkSize <= 0) break

            inputBuffer.put(pcmData, offset, chunkSize)

            val isLastChunk = (offset + chunkSize >= length)
            val flags = if (isLastChunk && isEndOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            val durationUs = (chunkSize.toLong() * 1_000_000L) / (sampleRate * bytesPerSample)

            codec.queueInputBuffer(inputBufferIndex, 0, chunkSize, presentationTimeUs, flags)
            presentationTimeUs += durationUs
            offset += chunkSize

            drainEncoder(codec, muxer, isLastChunk && isEndOfStream)
        }

        // If no PCM data at all (e.g., EOS-only signal), submit EOS with retry
        if (length == 0 && isEndOfStream) {
            for (retry in 0..10) {
                val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    codec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    drainEncoder(codec, muxer, true)
                    break
                } else {
                    drainEncoder(codec, muxer, false)
                }
            }
        }
    }

    private fun drainEncoder(codec: MediaCodec, muxer: MediaMuxer, isEndOfStream: Boolean) {
        val bufferInfo = MediaCodec.BufferInfo()
        val timeout = if (isEndOfStream) 50_000L else 10_000L

        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeout)

            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    break
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (isMuxerStarted) {
                        Log.w(TAG, "Output format changed after muxer started")
                        break
                    }
                    val newFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    isMuxerStarted = true
                    Log.i(TAG, "MediaMuxer started with trackIndex=$trackIndex, format=$newFormat")
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0 && isMuxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    /**
     * Flushes remaining samples, stops codec and closes MPEG-4 muxer cleanly.
     */
    @Synchronized
    fun stop() {
        try {
            encodePcm(ByteArray(0), 0, isEndOfStream = true)
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing EOS to encoder: ${e.message}")
        }

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping mediaCodec: ${e.message}")
        }
        mediaCodec = null

        try {
            if (isMuxerStarted) {
                mediaMuxer?.stop()
            }
            mediaMuxer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping mediaMuxer: ${e.message}")
        }
        mediaMuxer = null
        isMuxerStarted = false
        Log.i(TAG, "AudioEncoder stopped cleanly for ${outputFile.name}, size=${outputFile.length()} bytes")
    }
}
