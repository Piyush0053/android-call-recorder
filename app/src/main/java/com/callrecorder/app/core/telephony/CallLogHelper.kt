package com.callrecorder.app.core.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs

data class CallLogEntry(
    val number: String,
    val cachedName: String?,
    val isIncoming: Boolean,
    val date: Long,
    val durationSeconds: Long
)

/**
 * Utility to extract verified call details and phone numbers from the Android CallLog provider.
 * Solves the Android 9+ platform restriction where ACTION_PHONE_STATE_CHANGED emits null numbers.
 */
object CallLogHelper {
    private const val TAG = "CallLogHelper"

    /**
     * Retrieves the most recent call log entry from CallLog.Calls within maxLookbackMs.
     */
    fun getLatestCallDetails(context: Context, maxLookbackMs: Long = 180_000L): CallLogEntry? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG permission not granted; cannot query CallLog.")
            return null
        }

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val cutoff = System.currentTimeMillis() - maxLookbackMs

        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(cutoff.toString()),
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    parseCurrentRow(cursor)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query latest call log: ${e.message}")
            null
        }
    }

    /**
     * Finds the closest call log entry matching a specific timestamp within toleranceMs.
     * Used for retroactively recovering phone numbers for existing recordings.
     */
    fun findCallLogForTimestamp(context: Context, timestamp: Long, toleranceMs: Long = 300_000L): CallLogEntry? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val minDate = timestamp - toleranceMs
        val maxDate = timestamp + toleranceMs

        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} BETWEEN ? AND ?",
                arrayOf(minDate.toString(), maxDate.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                var bestEntry: CallLogEntry? = null
                var bestDiff = Long.MAX_VALUE

                while (cursor.moveToNext()) {
                    val entry = parseCurrentRow(cursor)
                    if (entry != null && entry.number.isNotBlank() && !entry.number.equals("Unknown Number", ignoreCase = true)) {
                        val diff = abs(entry.date - timestamp)
                        if (diff < bestDiff) {
                            bestDiff = diff
                            bestEntry = entry
                        }
                    }
                }
                bestEntry
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to match call log for timestamp $timestamp: ${e.message}")
            null
        }
    }

    private fun parseCurrentRow(cursor: Cursor): CallLogEntry? {
        val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
        val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

        val number = if (numberIdx >= 0) cursor.getString(numberIdx) else null
        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
        val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
        val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
        val duration = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0L

        return if (!number.isNullOrBlank()) {
            CallLogEntry(
                number = number,
                cachedName = name?.takeIf { it.isNotBlank() },
                isIncoming = type == CallLog.Calls.INCOMING_TYPE,
                date = date,
                durationSeconds = duration
            )
        } else null
    }
}
