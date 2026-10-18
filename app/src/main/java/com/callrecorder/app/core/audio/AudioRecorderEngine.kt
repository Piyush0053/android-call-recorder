package com.callrecorder.app.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.callrecorder.app.core.telephony.CallCompanionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Thread-safe circular buffer for pre-roll speech retention.
 */
class CircularByteBuffer(private val capacity: Int) {
    init {
        require(capacity > 0) { "Capacity must be greater than 0, got: $capacity" }
    }

    private val buffer = ByteArray(capacity)
    private var writePos = 0
    var size = 0
        private set

    @Synchronized
    fun write(data: ByteArray, offset: Int, length: Int) {
        if (length <= 0 || offset < 0 || offset + length > data.size) return
        for (i in 0 until length) {
            buffer[writePos] = data[offset + i]
            writePos = (writePos + 1) % capacity
            if (size < capacity) size++
        }
    }

    @Synchronized
    fun toByteArray(): ByteArray {
        val result = ByteArray(size)
        val startPos = if (size < capacity) 0 else writePos
        for (i in 0 until size) {
            result[i] = buffer[(startPos + i) % capacity]
        }
        return result
    }

    @Synchronized
    fun clear() {
        writePos = 0
        size = 0
    }
}

/**
 * High-performance background audio capture engine.
 * Captures in-call audio using hardware AudioRecord, applies software gain boost,
 * and encodes to AAC (.m4a).
 *
 * Outgoing Answer Detection:
 * Employs Voice Activity Detection (VAD) and a circular pre-roll buffer.
 * If CallCompanionService is active, answer state is signaled directly by Android Telecom.
 * Otherwise, the voice energy gate detects speech and flushes the pre-roll audio into the encoder.
 */
class AudioRecorderEngine(
    private val gainProcessor: AudioGainProcessor = AudioGainProcessor()
) {

    private var audioRecord: AudioRecord? = null
    private var audioEncoder: AudioEncoder? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioManager: AudioManager? = null
    private var originalSpeakerphoneState: Boolean = false
    private var preRollBuffer: CircularByteBuffer? = null
    private var recordingStartTime = 0L
    private var callAnswerTime = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentDurationSeconds = MutableStateFlow(0L)
    val currentDurationSeconds: StateFlow<Long> = _currentDurationSeconds.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0.0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private val _isAnswered = MutableStateFlow(false)
    val isAnswered: StateFlow<Boolean> = _isAnswered.asStateFlow()

    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    companion object {
        private const val TAG = "AudioRecorderEngine"
        private const val SPEECH_AMPLITUDE_THRESHOLD = 0.015f
        private const val MAX_ANSWER_DETECTION_TIMEOUT_MS = 3000L
    }

    private fun isCallCompanionActive(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && CallCompanionService.isCompanionActive
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    fun startRecording(
        outputFile: File,
        gainMultiplier: Float = 2.2f,
        context: Context? = null,
        enableSpeakerphone: Boolean = true,
        isOutgoingCall: Boolean = false
    ) {
        if (_isRecording.value) return

        gainProcessor.setGainMultiplier(gainMultiplier)

        // Enable speakerphone boost to capture both caller and remote party
        if (context != null) {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null) {
                    audioManager = am
                    @Suppress("DEPRECATION")
                    originalSpeakerphoneState = am.isSpeakerphoneOn

                    if (enableSpeakerphone) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val speakerDevice = am.availableCommunicationDevices.firstOrNull {
                                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                            }
                            if (speakerDevice != null) {
                                am.setCommunicationDevice(speakerDevice)
                                Log.i(TAG, "Speakerphone communication device enabled for clear in-call acoustic capture")
                            } else {
                                @Suppress("DEPRECATION")
                                am.isSpeakerphoneOn = true
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            am.isSpeakerphoneOn = true
                            Log.i(TAG, "Speakerphone boost enabled for clear in-call acoustic capture")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not set AudioManager speakerphone: ${e.message}")
            }
        }

        // Test sample rates: 44100 Hz first (universal AAC standard), fallback to 16000 Hz
        val sampleRatesToTry = intArrayOf(44100, 16000)

        // Prioritize VOICE_COMMUNICATION for bi-directional call audio
        val audioSources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )

        var record: AudioRecord? = null
        var activeSampleRate = 44100
        var activeBufferSize = 8192

        sourceLoop@ for (sr in sampleRatesToTry) {
            val minBuf = AudioRecord.getMinBufferSize(sr, channelConfig, audioFormat)
            if (minBuf <= 0) continue

            val bufSize = (minBuf * 2).coerceAtLeast(8192)

            for (source in audioSources) {
                try {
                    val candidate = AudioRecord(
                        source,
                        sr,
                        channelConfig,
                        audioFormat,
                        bufSize
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        record = candidate
                        activeSampleRate = sr
                        activeBufferSize = bufSize
                        Log.i(TAG, "Selected audio source=$source at sampleRate=$sr, bufferSize=$bufSize")
                        break@sourceLoop
                    } else {
                        candidate.release()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Failed source=$source at $sr: ${e.message}")
                }
            }
        }

        if (record == null) {
            cleanupAudioRouting()
            throw IllegalStateException("Failed to initialize AudioRecord with any available audio source or sample rate")
        }

        var encoder: AudioEncoder? = null
        try {
            outputFile.parentFile?.mkdirs()
            val bitrate = if (activeSampleRate >= 44100) 96000 else 64000
            val createdEncoder = AudioEncoder(outputFile, sampleRate = activeSampleRate, channelCount = 1, bitrate = bitrate)
            createdEncoder.start()
            encoder = createdEncoder

            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("AudioRecord failed to start recording (recordingState=${record.recordingState})")
            }
            audioRecord = record
            audioEncoder = createdEncoder
            _isRecording.value = true
            _currentDurationSeconds.value = 0L
        } catch (e: Exception) {
            try { encoder?.stop() } catch (_: Exception) {}
            try { record.stop() } catch (_: Exception) {}
            try { record.release() } catch (_: Exception) {}
            cleanupAudioRouting()
            throw e
        }

        // Incoming calls are already answered by user when OFFHOOK.
        // If CallCompanionService is active, answer is already verified by Telecom.
        val preRollCapacity = activeSampleRate * 2 * 2 // 2 seconds of 16-bit mono PCM
        val preRoll = CircularByteBuffer(preRollCapacity)
        preRollBuffer = preRoll

        var hasDetectedAnswer = !isOutgoingCall || isCallCompanionActive()
        _isAnswered.value = hasDetectedAnswer

        val startTime = System.currentTimeMillis()
        recordingStartTime = startTime
        callAnswerTime = if (hasDetectedAnswer) startTime else 0L

        recordingJob = scope.launch {
            val myJob = coroutineContext[Job]
            val buffer = ByteArray(activeBufferSize)
            var answerTime = if (hasDetectedAnswer) startTime else startTime
            var lastSecondUpdate = startTime

            try {
                while (isActive && _isRecording.value) {
                    val readBytes = record.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        // Apply software digital gain to amplify caller voice
                        gainProcessor.processPcmBuffer(buffer, readBytes)

                        // Compute instantaneous amplitude for real-time visualizer
                        val amp = gainProcessor.calculateNormalizedAmplitude(buffer, readBytes)
                        _currentAmplitude.value = amp

                        val now = System.currentTimeMillis()

                        if (!hasDetectedAnswer) {
                            preRoll.write(buffer, 0, readBytes)

                            // Check for speech energy, companion signal, or fallback timeout
                            if (amp >= SPEECH_AMPLITUDE_THRESHOLD || 
                                (now - startTime) >= MAX_ANSWER_DETECTION_TIMEOUT_MS ||
                                isCallCompanionActive()) {
                                hasDetectedAnswer = true
                                _isAnswered.value = true
                                answerTime = now
                                callAnswerTime = now
                                lastSecondUpdate = now
                                Log.i(TAG, "Answer detected (amp=$amp, elapsed=${now - startTime}ms). Flushing pre-roll buffer.")

                                val preRollData = preRoll.toByteArray()
                                if (preRollData.isNotEmpty()) {
                                    audioEncoder?.encodePcm(preRollData, preRollData.size)
                                }
                                preRoll.clear()
                            }
                        } else {
                            // Encode amplified PCM to AAC
                            audioEncoder?.encodePcm(buffer, readBytes)

                            // Update elapsed time every second from answer time
                            if (now - lastSecondUpdate >= 1000) {
                                _currentDurationSeconds.value = (now - answerTime) / 1000
                                lastSecondUpdate = now
                            }
                        }
                    } else if (readBytes < 0) {
                        Log.e(TAG, "Fatal AudioRecord read error code: $readBytes; terminating capture loop")
                        break
                    }
                }
            } finally {
                if (recordingJob === myJob) {
                    cleanup()
                }
            }
        }
    }

    @Synchronized
    fun stopRecording(): Long {
        _isRecording.value = false
        val activeJob = recordingJob
        recordingJob = null
        activeJob?.cancel()

        // Flush remaining pre-roll audio before stopping encoder
        try {
            preRollBuffer?.let { pr ->
                if (pr.size > 0 && audioEncoder != null) {
                    val remaining = pr.toByteArray()
                    if (remaining.isNotEmpty()) {
                        audioEncoder?.encodePcm(remaining, remaining.size)
                    }
                    pr.clear()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing preRollBuffer on stop: ${e.message}")
        }
        preRollBuffer = null

        // Calculate accurate duration up to stop time if answered
        if (_isAnswered.value && callAnswerTime > 0L) {
            val elapsed = (System.currentTimeMillis() - callAnswerTime) / 1000
            if (elapsed > _currentDurationSeconds.value) {
                _currentDurationSeconds.value = elapsed
            }
        }

        cleanup()
        val duration = _currentDurationSeconds.value
        return if (duration >= 1) duration else 1L
    }

    @Synchronized
    private fun cleanup() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioEncoder?.stop()
        } catch (_: Exception) {}
        audioEncoder = null

        cleanupAudioRouting()

        _isRecording.value = false
        _currentAmplitude.value = 0.0f
        _isAnswered.value = false
        recordingStartTime = 0L
        callAnswerTime = 0L
    }

    @Synchronized
    private fun cleanupAudioRouting() {
        try {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = originalSpeakerphoneState
            }
        } catch (_: Exception) {}
        audioManager = null
    }

    fun release() {
        stopRecording()
        scope.cancel()
    }
}
