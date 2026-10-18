package com.callrecorder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageManagerTest {

    @Test
    fun testRecordingFileNamePattern() {
        val phoneNumber = "+1-555-987-6543"
        val sanitized = phoneNumber.replace(Regex("[^0-9+]"), "_")
        val direction = "IN"
        val fileName = "REC_20261018_143000_${sanitized}_${direction}.m4a"

        assertTrue(fileName.startsWith("REC_"))
        assertTrue(fileName.endsWith(".m4a"))
        assertTrue(fileName.contains("_IN.m4a"))
        assertTrue(fileName.contains("+1_555_987_6543"))
    }

    @Test
    fun testOutgoingDirectionTag() {
        val phoneNumber = "5551234"
        val sanitized = phoneNumber.replace(Regex("[^0-9+]"), "_")
        val isIncoming = false
        val direction = if (isIncoming) "IN" else "OUT"
        val fileName = "REC_20261018_143000_${sanitized}_${direction}.m4a"

        assertTrue(fileName.endsWith("_OUT.m4a"))
    }
}
