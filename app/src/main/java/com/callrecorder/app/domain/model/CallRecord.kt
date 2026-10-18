package com.callrecorder.app.domain.model

import com.callrecorder.app.data.local.entity.CallCategory
import com.callrecorder.app.data.local.entity.CallRecordEntity

/**
 * Domain representation of a call recording.
 */
data class CallRecord(
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val durationSeconds: Long,
    val phoneNumber: String,
    val contactName: String?,
    val contactPhotoUri: String?,
    val isKnownContact: Boolean,
    val callType: CallType,
    val timestamp: Long,
    val category: CallCategory,
    val isFavorite: Boolean = false,
    val notes: String? = null
) {
    val displayName: String
        get() = contactName?.takeIf { it.isNotBlank() } ?: phoneNumber

    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

enum class CallType {
    INCOMING,
    OUTGOING
}

fun CallRecordEntity.toDomain(): CallRecord {
    return CallRecord(
        id = id,
        filePath = filePath,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        durationSeconds = durationSeconds,
        phoneNumber = phoneNumber,
        contactName = contactName,
        contactPhotoUri = contactPhotoUri,
        isKnownContact = isKnownContact,
        callType = if (callType.equals("OUTGOING", ignoreCase = true)) CallType.OUTGOING else CallType.INCOMING,
        timestamp = timestamp,
        category = runCatching { CallCategory.valueOf(category) }.getOrDefault(CallCategory.ALL),
        isFavorite = isFavorite,
        notes = notes
    )
}

fun CallRecord.toEntity(): CallRecordEntity {
    return CallRecordEntity(
        id = id,
        filePath = filePath,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        durationSeconds = durationSeconds,
        phoneNumber = phoneNumber,
        contactName = contactName,
        contactPhotoUri = contactPhotoUri,
        isKnownContact = isKnownContact,
        callType = callType.name,
        timestamp = timestamp,
        category = category.name,
        isFavorite = isFavorite,
        notes = notes
    )
}
