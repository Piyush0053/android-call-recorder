package com.callrecorder.app.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Audio playback engine for reviewing recorded conversations.
 * Supports scrubbing, rewind/fast-forward, variable playback speeds,
 * and routes directly to the main media loudspeaker.
 */
class AudioPlayerEngine {

    companion object {
        private const val TAG = "AudioPlayerEngine"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _currentSpeed = MutableStateFlow(1.0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private var currentFilePath: String? = null

    fun playFile(file: File, context: Context? = null) {
        if (!file.exists()) {
            Log.w(TAG, "Cannot play non-existent file: ${file.absolutePath}")
            return
        }

        if (file.length() == 0L) {
            Log.w(TAG, "Cannot play empty file (0 bytes): ${file.name}")
            return
        }

        if (currentFilePath == file.absolutePath && mediaPlayer != null) {
            resume()
            return
        }

        stop()
        currentFilePath = file.absolutePath

        // Reset AudioManager mode to MODE_NORMAL so playback routes to loudspeaker
        if (context != null) {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.mode = AudioManager.MODE_NORMAL
            } catch (e: Exception) {
                Log.w(TAG, "Could not reset AudioManager mode: ${e.message}")
            }
        }

        val player = MediaPlayer()
        try {
            // Configure explicitly for media speech playback via loudspeaker
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            player.setAudioAttributes(audioAttributes)
            player.setVolume(1.0f, 1.0f)

            player.setDataSource(file.absolutePath)
            player.prepare()
            _durationMs.value = player.duration
            _currentPositionMs.value = 0

            applyPlaybackSpeed(player, _currentSpeed.value)

            player.setOnCompletionListener {
                Log.d(TAG, "Playback completed for ${file.name}")
                _isPlaying.value = false
                _currentPositionMs.value = _durationMs.value
                stopProgressTracking()
            }

            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra for ${file.name}")
                stop()
                true
            }

            player.start()
            mediaPlayer = player
            _isPlaying.value = true
            startProgressTracking()
            Log.i(TAG, "Playback started: ${file.name}, duration=${player.duration}ms, size=${file.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare or start MediaPlayer for ${file.name}: ${e.message}", e)
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopProgressTracking()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _isPlaying.value = true
                startProgressTracking()
            }
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            val clamped = positionMs.coerceIn(0, it.duration)
            it.seekTo(clamped)
            _currentPositionMs.value = clamped
        }
    }

    fun seekForward(deltaMs: Int = 10_000) {
        val newPos = _currentPositionMs.value + deltaMs
        seekTo(newPos)
    }

    fun seekBackward(deltaMs: Int = 10_000) {
        val newPos = _currentPositionMs.value - deltaMs
        seekTo(newPos)
    }

    fun setSpeed(speed: Float) {
        _currentSpeed.value = speed
        mediaPlayer?.let {
            applyPlaybackSpeed(it, speed)
        }
    }

    private fun applyPlaybackSpeed(player: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = PlaybackParams()
                params.speed = speed
                player.playbackParams = params
            } catch (_: Exception) {}
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPositionMs.value = it.currentPosition
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun stop() {
        stopProgressTracking()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPositionMs.value = 0
        currentFilePath = null
    }
}
