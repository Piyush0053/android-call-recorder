package com.callrecorder.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Database entity representing a cataloged call recording.
 */
@Entity(
    tableName = "call_recordings",
    indices = [
        Index("phoneNumber"),
        Index("timestamp"),
        Index("category"),
        Index("isKnownContact"),
        Index("isFavorite")
    ]
)
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val durationSeconds: Long,
    val phoneNumber: String,
    val contactName: String?,
    val contactPhotoUri: String?,
    val isKnownContact: Boolean,
    val callType: String, // "INCOMING" or "OUTGOING"
    val timestamp: Long,   // Epoch millis
    val category: String = CallCategory.ALL.name,
    val isFavorite: Boolean = false,
    val notes: String? = null
)
