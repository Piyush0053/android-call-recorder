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
     */
    @Synchronized
    fun encodePcm(pcmData: ByteArray, length: Int, isEndOfStream: Boolean = false) {
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return

        // Submit input buffer
        val inputBufferIndex = codec.dequeueInputBuffer(10_000)
        if (inputBufferIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()

            if (length > 0) {
                inputBuffer?.put(pcmData, 0, length)
            }

            val flags = if (isEndOfStream) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            val bytesPerSample = 2 * channelCount
            val durationUs = (length.toLong() * 1_000_000L) / (sampleRate * bytesPerSample)

            codec.queueInputBuffer(inputBufferIndex, 0, length, presentationTimeUs, flags)
            presentationTimeUs += durationUs
        }

        // Drain output buffers
        drainEncoder(codec, muxer, isEndOfStream)
    }

    private fun drainEncoder(codec: MediaCodec, muxer: MediaMuxer, isEndOfStream: Boolean) {
        val bufferInfo = MediaCodec.BufferInfo()
        val timeout = if (isEndOfStream) 50_000L else 0L

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
