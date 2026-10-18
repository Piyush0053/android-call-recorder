package com.callrecorder.app.core.storage

import com.callrecorder.app.data.local.CallRecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles automated retention and cleanup of old non-favorite recordings.
 */
class StorageCleaner(
    private val dao: CallRecordDao,
    private val storageManager: StorageManager
) {

    /**
     * Cleans up unstarred recordings older than retentionDays.
     * If retentionDays is 0 or negative, auto-cleanup is disabled.
     */
    suspend fun cleanOldRecordings(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) return@withContext 0

        val cutoffMillis = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        val oldRecordings = dao.getRecordingsOlderThan(cutoffMillis)
        var deletedCount = 0

        for (record in oldRecordings) {
            storageManager.deleteFile(record.filePath)
            dao.deleteRecording(record)
            deletedCount++
        }
        deletedCount
    }
}
