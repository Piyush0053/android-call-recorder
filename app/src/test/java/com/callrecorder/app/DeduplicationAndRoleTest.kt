package com.callrecorder.app

import com.callrecorder.app.data.local.entity.CallCategory
import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.domain.model.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduplicationAndRoleTest {

    @Test
    fun testDistinctFilePathDeduplicationLogic() {
        val records = listOf(
            CallRecord(
                id = 1,
                filePath = "/storage/rec_1.m4a",
                fileName = "rec_1.m4a",
                fileSizeBytes = 1000,
                durationSeconds = 10,
                phoneNumber = "123456",
                contactName = "Alice",
                contactPhotoUri = null,
                isKnownContact = true,
                callType = CallType.INCOMING,
                timestamp = 1000L,
                category = CallCategory.KNOWN
            ),
            CallRecord(
                id = 2,
                filePath = "/storage/rec_1.m4a", // duplicate filePath
                fileName = "rec_1.m4a",
                fileSizeBytes = 1000,
                durationSeconds = 10,
                phoneNumber = "123456",
                contactName = "Alice",
                contactPhotoUri = null,
                isKnownContact = true,
                callType = CallType.INCOMING,
                timestamp = 1001L,
                category = CallCategory.KNOWN
            ),
            CallRecord(
                id = 3,
                filePath = "/storage/rec_2.m4a",
                fileName = "rec_2.m4a",
                fileSizeBytes = 2000,
                durationSeconds = 20,
                phoneNumber = "789012",
                contactName = null,
                contactPhotoUri = null,
                isKnownContact = false,
                callType = CallType.OUTGOING,
                timestamp = 2000L,
                category = CallCategory.UNKNOWN
            )
        )

        // Verify deduplication by distinct filePath preserves first record
        val deduplicated = records.distinctBy { it.filePath }
        assertEquals(2, deduplicated.size)
        assertEquals(1L, deduplicated[0].id)
        assertEquals(3L, deduplicated[1].id)
    }

    @Test
    fun testCallRecordAttributes() {
        val record = CallRecord(
            id = 5,
            filePath = "/storage/rec_test.m4a",
            fileName = "rec_test.m4a",
            fileSizeBytes = 4096,
            durationSeconds = 35,
            phoneNumber = "+18005551212",
            contactName = "Support",
            contactPhotoUri = null,
            isKnownContact = true,
            isFavorite = true,
            callType = CallType.OUTGOING,
            timestamp = 5000L,
            category = CallCategory.KNOWN
        )

        assertTrue(record.isFavorite)
        assertTrue(record.isKnownContact)
        assertEquals(CallType.OUTGOING, record.callType)
        assertEquals(CallCategory.KNOWN, record.category)
    }
}
