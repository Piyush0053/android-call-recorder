package com.callrecorder.app.data.repository

import com.callrecorder.app.core.storage.StorageManager
import com.callrecorder.app.data.local.CallRecordDao
import com.callrecorder.app.data.local.entity.CallCategory
import com.callrecorder.app.domain.model.CallFilter
import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.domain.model.toDomain
import com.callrecorder.app.domain.model.toEntity
import com.callrecorder.app.domain.repository.CallRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementation of CallRecordRepository coordinating Room database
 * and scoped file storage.
 */
class CallRecordRepositoryImpl(
    private val dao: CallRecordDao,
    private val storageManager: StorageManager
) : CallRecordRepository {

    override fun getRecordings(filter: CallFilter): Flow<List<CallRecord>> {
        val flow = if (filter.query.isNotBlank() || filter.category != CallCategory.ALL || filter.onlyKnown || filter.onlyUnknown || filter.onlyFavorites) {
            dao.filterRecordings(
                query = filter.query.trim(),
                category = filter.category.name,
                onlyKnown = if (filter.onlyKnown) 1 else 0,
                onlyUnknown = if (filter.onlyUnknown) 1 else 0,
                onlyFavorites = if (filter.onlyFavorites) 1 else 0
            )
        } else {
            dao.getAllRecordings()
        }

        return flow.map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchRecordings(query: String): Flow<List<CallRecord>> {
        return dao.searchRecordings(query.trim()).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getRecordingById(id: Long): CallRecord? = withContext(Dispatchers.IO) {
        dao.getRecordingById(id)?.toDomain()
    }

    override suspend fun saveRecording(record: CallRecord): Long = withContext(Dispatchers.IO) {
        val existing = dao.getRecordingByFilePath(record.filePath)
        if (existing != null) {
            dao.updateRecording(record.toEntity().copy(id = existing.id))
            existing.id
        } else {
            dao.insertRecording(record.toEntity())
        }
    }

    override suspend fun deleteRecording(id: Long): Boolean = withContext(Dispatchers.IO) {
        val record = dao.getRecordingById(id) ?: return@withContext false
        storageManager.deleteFile(record.filePath)
        dao.deleteRecordingById(id)
        true
    }

    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(id, isFavorite)
    }

    override suspend fun updateCategory(id: Long, category: CallCategory) = withContext(Dispatchers.IO) {
        dao.updateCategory(id, category.name)
    }

    override fun getRecordingCount(): Flow<Int> = dao.getRecordingCount().flowOn(Dispatchers.IO)

    override fun getTotalStorageUsed(): Flow<Long> {
        return dao.getTotalStorageUsed().map { it ?: 0L }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAudioFile(filePath: String): File? = withContext(Dispatchers.IO) {
        val file = storageManager.getFile(filePath)
        if (file.exists()) file else null
    }

    override suspend fun cleanDuplicateRecordings() = withContext(Dispatchers.IO) {
        dao.deleteDuplicateRecordings()
    }

    override suspend fun syncContactsAndCallLogs(context: android.content.Context) = withContext(Dispatchers.IO) {
        val allEntities = dao.getAllRecordingsSync()
        val lookupManager = com.callrecorder.app.core.telephony.ContactLookupManager(context)

        for (entity in allEntities) {
            var updated = false
            var currentEntity = entity
            var currentNumber = currentEntity.phoneNumber

            // 1. If the entity has an unknown or placeholder number, resolve from device CallLog
            if (currentNumber.isBlank() || currentNumber.equals("Unknown Number", ignoreCase = true) || currentNumber.equals("Unknown", ignoreCase = true)) {
                val logEntry = com.callrecorder.app.core.telephony.CallLogHelper.findCallLogForTimestamp(context, currentEntity.timestamp, toleranceMs = 300_000L)
                if (logEntry != null && logEntry.number.isNotBlank() && !logEntry.number.equals("Unknown Number", ignoreCase = true)) {
                    currentNumber = logEntry.number
                    currentEntity = currentEntity.copy(
                        phoneNumber = currentNumber,
                        callType = if (logEntry.isIncoming) "INCOMING" else "OUTGOING"
                    )
                    updated = true
                }
            }

            // 2. Perform contact lookup against ContactsContract with currentNumber
            if (currentNumber.isNotBlank() && !currentNumber.equals("Unknown Number", ignoreCase = true) && !currentNumber.equals("Private Number", ignoreCase = true)) {
                val info = lookupManager.lookupContact(currentNumber)
                if (info.isKnown) {
                    if (currentEntity.contactName != info.displayName || !currentEntity.isKnownContact || currentEntity.contactPhotoUri != info.photoUri) {
                        currentEntity = currentEntity.copy(
                            contactName = info.displayName,
                            contactPhotoUri = info.photoUri,
                            isKnownContact = true,
                            category = CallCategory.KNOWN.name
                        )
                        updated = true
                    }
                } else {
                    // Contact not saved (or deleted from phonebook)
                    if (currentEntity.isKnownContact && currentEntity.contactName != null) {
                        currentEntity = currentEntity.copy(
                            contactName = null,
                            contactPhotoUri = null,
                            isKnownContact = false,
                            category = CallCategory.UNKNOWN.name
                        )
                        updated = true
                    }
                }
            }

            if (updated) {
                dao.updateRecording(currentEntity)
            }
        }
    }
}
