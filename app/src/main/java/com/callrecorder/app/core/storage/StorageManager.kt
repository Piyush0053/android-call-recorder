package com.callrecorder.app.core.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages app-private scoped storage for all call recordings.
 * Audio files are stored under context.filesDir/recordings/ ensuring complete
 * isolation from other applications and zero external exposure.
 */
class StorageManager(private val context: Context) {

    private val recordingsDir: File
        get() {
            val dir = File(context.filesDir, "recordings")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Creates a new unique file handle for an upcoming call recording.
     */
    fun createRecordingFile(phoneNumber: String, isIncoming: Boolean): File {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = dateFormat.format(Date())
        val sanitizedNumber = phoneNumber.replace(Regex("[^0-9+]"), "_")
        val direction = if (isIncoming) "IN" else "OUT"
        val fileName = "REC_${timestamp}_${sanitizedNumber}_${direction}.m4a"
        return File(recordingsDir, fileName)
    }

    /**
     * Resolves a File object from an absolute path string.
     */
    fun getFile(path: String): File {
        return File(path)
    }

    /**
     * Deletes a recording file safely.
     */
    fun deleteFile(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    /**
     * Returns a shareable content Uri using FileProvider.
     */
    fun getShareableUri(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Calculates the total storage size used by all recorded audio files.
     */
    fun calculateTotalStorageBytes(): Long {
        return recordingsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
