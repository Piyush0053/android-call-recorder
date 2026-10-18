package com.callrecorder.app.domain.repository

import com.callrecorder.app.data.local.entity.CallCategory
import com.callrecorder.app.domain.model.CallFilter
import com.callrecorder.app.domain.model.CallRecord
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Domain repository interface for managing recorded calls.
 */
interface CallRecordRepository {
    fun getRecordings(filter: CallFilter): Flow<List<CallRecord>>
    fun searchRecordings(query: String): Flow<List<CallRecord>>
    suspend fun getRecordingById(id: Long): CallRecord?
    suspend fun saveRecording(record: CallRecord): Long
    suspend fun deleteRecording(id: Long): Boolean
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
    suspend fun updateCategory(id: Long, category: CallCategory)
    fun getRecordingCount(): Flow<Int>
    fun getTotalStorageUsed(): Flow<Long>
    suspend fun getAudioFile(filePath: String): File?
    suspend fun cleanDuplicateRecordings()
    suspend fun syncContactsAndCallLogs(context: android.content.Context)
}
