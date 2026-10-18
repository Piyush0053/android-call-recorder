package com.callrecorder.app.core.telephony

import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Companion InCallService operating alongside the pre-installed dialer (Google Phone).
 * Receives official Call lifecycle state changes from Android Telecom:
 * - Call.STATE_DIALING / Call.STATE_CONNECTING: Outgoing call initiated; wait for answer.
 * - Call.STATE_RINGING: Incoming call ringing; wait for answer.
 * - Call.STATE_ACTIVE: Call ANSWERED by remote party! We start recording immediately.
 * - Call.STATE_DISCONNECTED: Call terminated. We stop recording and persist audio.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class CallCompanionService : InCallService() {

    companion object {
        private const val TAG = "CallCompanionService"
        
        @Volatile
        var isCompanionActive = false
            private set
    }

    private val activeCallbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        isCompanionActive = true
        Log.d(TAG, "onCallAdded: call=${call.details?.handle}, initial state=${call.state}")

        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                super.onStateChanged(c, state)
                handleStateChanged(c, state)
            }
        }

        activeCallbacks[call] = callback
        call.registerCallback(callback)

        // Process initial call state if already ACTIVE
        handleStateChanged(call, call.state)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: call=${call.details?.handle}")
        val cb = activeCallbacks.remove(call)
        if (cb != null) {
            try {
                call.unregisterCallback(cb)
            } catch (_: Exception) {}
        }

        CallRecordingService.stopRecording(this)

        if (activeCallbacks.isEmpty()) {
            isCompanionActive = false
        }
    }

    private fun handleStateChanged(call: Call, state: Int) {
        val handle = call.details?.handle
        val phoneNumber = handle?.schemeSpecificPart ?: "Unknown Number"
        val isIncoming = call.details?.callDirection == Call.Details.DIRECTION_INCOMING

        Log.d(TAG, "handleStateChanged: state=$state (ACTIVE=${Call.STATE_ACTIVE}, DIALING=${Call.STATE_DIALING}), number=$phoneNumber, incoming=$isIncoming")

        when (state) {
            Call.STATE_ACTIVE -> {
                // TRUE CONNECTED STATE: The remote party has picked up the call!
                Log.i(TAG, "Remote party answered call with $phoneNumber. Starting recording.")
                CallRecordingService.startRecording(this, phoneNumber, isIncoming)
            }
            Call.STATE_DISCONNECTED -> {
                Log.i(TAG, "Call disconnected with $phoneNumber. Stopping recording.")
                CallRecordingService.stopRecording(this)
            }
        }
    }

    override fun onDestroy() {
        activeCallbacks.forEach { (call, cb) ->
            try {
                call.unregisterCallback(cb)
            } catch (_: Exception) {}
        }
        activeCallbacks.clear()
        isCompanionActive = false
        super.onDestroy()
    }
}
