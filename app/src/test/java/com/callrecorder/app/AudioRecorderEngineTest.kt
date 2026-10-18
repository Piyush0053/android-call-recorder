package com.callrecorder.app

import com.callrecorder.app.core.audio.AudioGainProcessor
import com.callrecorder.app.core.audio.AudioRecorderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AudioRecorderEngineTest {

    @Test
    fun testInitialEngineState() {
        val engine = AudioRecorderEngine(AudioGainProcessor())

        assertFalse(engine.isRecording.value)
        assertFalse(engine.isAnswered.value)
        assertEquals(0L, engine.currentDurationSeconds.value)
        assertEquals(0.0f, engine.currentAmplitude.value, 0.001f)
    }

    @Test
    fun testStopRecordingWhenIdle() {
        val engine = AudioRecorderEngine(AudioGainProcessor())

        val duration = engine.stopRecording()
        assertEquals(1L, duration)
        assertFalse(engine.isRecording.value)
        assertFalse(engine.isAnswered.value)
    }

    @Test
    fun testReleaseWhenIdle() {
        val engine = AudioRecorderEngine(AudioGainProcessor())
        engine.release()
        assertFalse(engine.isRecording.value)
        assertFalse(engine.isAnswered.value)
    }
}
