package com.callrecorder.app.core.telephony

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.R
import com.callrecorder.app.core.audio.AudioRecorderEngine
import com.callrecorder.app.core.storage.StorageManager
import com.callrecorder.app.data.local.entity.CallCategory
import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground Service that captures in-call audio while Google Phone or another dialer
 * has an active conversation connected.
 */
class CallRecordingService : Service() {

    private lateinit var storageManager: StorageManager
    private lateinit var contactLookupManager: ContactLookupManager
    private val audioRecorderEngine = AudioRecorderEngine()

    private var wakeLock: PowerManager.WakeLock? = null
    private var recordingFile: File? = null
    private var currentPhoneNumber: String = ""
    private var isIncoming: Boolean = true
    private var contactInfo: ContactInfo? = null

    private var isRecording = false
    private var isStopping = false

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var notificationJob: Job? = null

    companion object {
        private const val TAG = "CallRecordingService"
        const val CHANNEL_ID = "call_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_RECORDING = "com.callrecorder.app.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.callrecorder.app.action.STOP_RECORDING"

        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"

        fun startRecording(context: Context, phoneNumber: String, isIncoming: Boolean) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        storageManager = StorageManager(this)
        contactLookupManager = ContactLookupManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val number = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
                val incoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, true)
                handleStartRecording(number, incoming)
            }
            ACTION_STOP_RECORDING -> {
                handleStopRecording()
            }
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun handleStartRecording(phoneNumber: String, incoming: Boolean) {
        if (isRecording) {
            android.util.Log.d(TAG, "Recording is already active for $currentPhoneNumber. Ignoring duplicate start request.")
            return
        }

        if (!com.callrecorder.app.core.utils.PermissionHelper.hasAudioPermission(this)) {
            android.util.Log.w(TAG, "Cannot start recording: RECORD_AUDIO permission is not granted.")
            stopSelf()
            return
        }

        currentPhoneNumber = phoneNumber
        isIncoming = incoming
        contactInfo = contactLookupManager.lookupContact(phoneNumber)

        acquireWakeLock()

        val notification = buildRecordingNotification(0)
        val started = startForegroundSafely(notification)
        if (!started) {
            releaseWakeLock()
            stopSelf()
            return
        }

        val prefs = getSharedPreferences("call_recorder_settings", Context.MODE_PRIVATE)
        val gainMultiplier = prefs.getFloat("gain_multiplier", 2.2f)
        val enableSpeakerphone = prefs.getBoolean("speakerphone_boost_enabled", true)

        val file = storageManager.createRecordingFile(phoneNumber, incoming)
        recordingFile = file

        try {
            audioRecorderEngine.startRecording(
                outputFile = file,
                gainMultiplier = gainMultiplier,
                context = this,
                enableSpeakerphone = enableSpeakerphone,
                isOutgoingCall = !incoming
            )
            isRecording = true
            isStopping = false
            startNotificationUpdater()
            android.util.Log.i(TAG, "Call recording started for $phoneNumber (speakerphoneBoost=$enableSpeakerphone)")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start audio recording engine: ${e.message}", e)
            handleStopRecording()
        }
    }

    private fun startForegroundSafely(notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return try {
                startForeground(NOTIFICATION_ID, notification)
                true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed startForeground on legacy OS: ${e.message}", e)
                false
            }
        }

        // Tier 1: Try MICROPHONE | PHONE_CALL (ideal on Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
                android.util.Log.d(TAG, "Started FGS with MICROPHONE|PHONE_CALL")
                return true
            } catch (e: SecurityException) {
                android.util.Log.w(TAG, "Tier 1 MICROPHONE|PHONE_CALL not permitted in current state: ${e.message}")
            }
        }

        // Tier 2: Try MICROPHONE alone (API 29+)
        try {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            android.util.Log.d(TAG, "Started FGS with MICROPHONE")
            return true
        } catch (e: SecurityException) {
            android.util.Log.w(TAG, "Tier 2 MICROPHONE not permitted in current state: ${e.message}")
        }

        // Tier 3: Android 14+ (API 34+) PHONE_CALL fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
                android.util.Log.d(TAG, "Started FGS with PHONE_CALL fallback")
                return true
            } catch (e: SecurityException) {
                android.util.Log.w(TAG, "Tier 3 PHONE_CALL not permitted in current state: ${e.message}")
            }
        }

        // Tier 4: Android 14+ (API 34+) SPECIAL_USE fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                android.util.Log.d(TAG, "Started FGS with SPECIAL_USE fallback")
                return true
            } catch (e: SecurityException) {
                android.util.Log.w(TAG, "Tier 4 SPECIAL_USE not permitted in current state: ${e.message}")
            }
        }

        // Tier 5: Fallback to basic startForeground without explicit FGS type flags
        return try {
            startForeground(NOTIFICATION_ID, notification)
            android.util.Log.d(TAG, "Started FGS without explicit type flags")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Fatal: All startForeground attempts failed: ${e.message}", e)
            false
        }
    }

    @Synchronized
    private fun handleStopRecording() {
        if (!isRecording && !isStopping) {
            return
        }
        if (isStopping) {
            return
        }
        isStopping = true
        isRecording = false

        notificationJob?.cancel()
        notificationJob = null

        val duration = audioRecorderEngine.stopRecording()
        val file = recordingFile
        recordingFile = null // IMMEDIATELY nullify so it can NEVER be saved a second time!

        val finalDuration = if (duration >= 1) duration else if (file != null && file.length() > 2048) 1L else 0L

        if (file != null && file.exists() && file.length() > 0 && finalDuration >= 1) {
            saveRecordToDatabase(file, finalDuration)
        } else {
            // Remove empty or 0-second file
            file?.delete()
        }

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveRecordToDatabase(file: File, durationSeconds: Long) {
        val app = application as? CallRecorderApp ?: return

        serviceScope.launch {
            // Give Android Telephony a brief moment to commit CallLog entry
            kotlinx.coroutines.delay(500)

            var resolvedNumber = currentPhoneNumber
            var resolvedIsIncoming = isIncoming
            var resolvedName: String? = null
            var resolvedPhoto: String? = null
            var resolvedIsKnown = false

            // Query CallLog to resolve real phone number if missing or unknown
            if (resolvedNumber.isBlank() || resolvedNumber.equals("Unknown Number", ignoreCase = true) || resolvedNumber.equals("Unknown", ignoreCase = true)) {
                val callLogEntry = CallLogHelper.getLatestCallDetails(this@CallRecordingService, maxLookbackMs = 180_000L)
                if (callLogEntry != null && callLogEntry.number.isNotBlank() && !callLogEntry.number.equals("Unknown Number", ignoreCase = true)) {
                    resolvedNumber = callLogEntry.number
                    resolvedIsIncoming = callLogEntry.isIncoming
                    android.util.Log.i(TAG, "Resolved real phone number from CallLog: $resolvedNumber (incoming=$resolvedIsIncoming)")
                }
            }

            // Look up contact details in ContactsContract
            val info = contactLookupManager.lookupContact(resolvedNumber)
            if (info.isKnown) {
                resolvedName = info.displayName
                resolvedPhoto = info.photoUri
                resolvedIsKnown = true
            }

            val record = CallRecord(
                filePath = file.absolutePath,
                fileName = file.name,
                fileSizeBytes = file.length(),
                durationSeconds = durationSeconds,
                phoneNumber = resolvedNumber,
                contactName = resolvedName,
                contactPhotoUri = resolvedPhoto,
                isKnownContact = resolvedIsKnown,
                callType = if (resolvedIsIncoming) CallType.INCOMING else CallType.OUTGOING,
                timestamp = System.currentTimeMillis(),
                category = if (resolvedIsKnown) CallCategory.KNOWN else CallCategory.UNKNOWN
            )

            app.repository.saveRecording(record)
        }
    }

    private fun buildRecordingNotification(elapsedSeconds: Long): Notification {
        val displayName = contactInfo?.displayName ?: currentPhoneNumber.ifBlank { "Active Call" }
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val timeString = "%02d:%02d".format(minutes, seconds)

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Call with $displayName")
            .setContentText("Elapsed: $timeString")
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun startNotificationUpdater() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            val notificationManager = getSystemService(NotificationManager::class.java)

            while (isActive && audioRecorderEngine.isRecording.value) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                notificationManager?.notify(NOTIFICATION_ID, buildRecordingNotification(elapsed))
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CallRecorder::AudioCaptureWakeLock"
            ).apply {
                acquire(3 * 60 * 60 * 1000L) // 3-hour maximum safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        if (isRecording) {
            handleStopRecording()
        }
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
