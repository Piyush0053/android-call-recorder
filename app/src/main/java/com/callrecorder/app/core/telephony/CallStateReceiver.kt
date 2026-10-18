package com.callrecorder.app.core.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * BroadcastReceiver monitoring Android telephony state changes.
 * Detects when incoming or outgoing calls connect (OFFHOOK) and terminate (IDLE).
 */
class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
        private const val PERMISSION_NOTIFICATION_ID = 2001
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var savedNumber: String? = null
        private var isIncoming: Boolean = false
        private var isCallActive: Boolean = false
    }

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Device rebooted; ready to capture calls
            return
        }

        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            val currentState = when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                else -> return
            }

            onCallStateChanged(context, currentState, number)
        }
    }

    private fun onCallStateChanged(context: Context, state: Int, number: String?) {
        if (state == lastState) return

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                isIncoming = true
                savedNumber = number ?: savedNumber
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && 
                    (CallCompanionService.isCompanionActive || com.callrecorder.app.core.utils.RoleHelper.isCallCompanionHeld(context))) {
                    android.util.Log.d(TAG, "CallCompanionService is handling call; CallStateReceiver skipping to wait for true STATE_ACTIVE.")
                    lastState = state
                    return
                }

                if (isCallActive) {
                    android.util.Log.d(TAG, "Call is already active; ignoring duplicate OFFHOOK.")
                    lastState = state
                    return
                }
                isCallActive = true

                // Call answered / connected!
                val activeNumber = number ?: savedNumber ?: "Unknown Number"

                val prefs = context.getSharedPreferences("call_recorder_settings", Context.MODE_PRIVATE)
                val isAutoRecordEnabled = prefs.getBoolean("auto_record_enabled", true)
                if (!isAutoRecordEnabled) {
                    android.util.Log.d(TAG, "Auto call recording is disabled in settings. Skipping.")
                    lastState = state
                    return
                }

                val isRecordUnknownEnabled = prefs.getBoolean("record_unknown_enabled", true)
                val isKnown = ContactLookupManager(context).lookupContact(activeNumber).isKnown
                if (!isKnown && !isRecordUnknownEnabled) {
                    android.util.Log.d(TAG, "Recording unknown callers is disabled in settings. Skipping.")
                    lastState = state
                    return
                }

                if (!com.callrecorder.app.core.utils.PermissionHelper.hasAudioPermission(context)) {
                    android.util.Log.w(TAG, "RECORD_AUDIO permission missing; cannot start recording.")
                    notifyMissingAudioPermission(context)
                    lastState = state
                    return
                }

                CallRecordingService.startRecording(context, activeNumber, isIncoming)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended / hung up
                if (isCallActive || lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    CallRecordingService.stopRecording(context)
                }
                isCallActive = false
                savedNumber = null
                isIncoming = false
            }
        }

        lastState = state
    }

    private fun notifyMissingAudioPermission(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CallRecordingService.CHANNEL_ID,
                context.getString(com.callrecorder.app.R.string.notification_channel_name),
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(com.callrecorder.app.R.string.notification_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, com.callrecorder.app.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            PERMISSION_NOTIFICATION_ID,
            launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, CallRecordingService.CHANNEL_ID)
            .setContentTitle("Call Recording Paused")
            .setContentText("Microphone permission is required to record calls. Tap to configure.")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(PERMISSION_NOTIFICATION_ID, notification)
    }
}
