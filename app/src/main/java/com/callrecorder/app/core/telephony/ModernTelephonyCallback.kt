package com.callrecorder.app.core.telephony

import android.os.Build
import android.telephony.TelephonyCallback
import androidx.annotation.RequiresApi

/**
 * Modern TelephonyCallback for Android 12+ (API 31+).
 * Listens for telephony call state transitions:
 * - CALL_STATE_RINGING
 * - CALL_STATE_OFFHOOK (Active connected call)
 * - CALL_STATE_IDLE (Call finished/disconnected)
 */
@RequiresApi(Build.VERSION_CODES.S)
class ModernTelephonyCallback(
    private val onStateChanged: (state: Int) -> Unit
) : TelephonyCallback(), TelephonyCallback.CallStateListener {

    override fun onCallStateChanged(state: Int) {
        onStateChanged(state)
    }
}
