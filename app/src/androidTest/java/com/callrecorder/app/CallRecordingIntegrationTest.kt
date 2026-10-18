package com.callrecorder.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callrecorder.app.core.storage.StorageManager
import com.callrecorder.app.data.local.CallRecordDao
import com.callrecorder.app.data.local.CallRecordDatabase
import com.callrecorder.app.data.local.entity.CallCategory
import com.callrecorder.app.data.repository.CallRecordRepositoryImpl
import com.callrecorder.app.domain.model.CallFilter
import com.callrecorder.app.domain.model.CallRecord
import com.callrecorder.app.domain.model.CallType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-End Android Instrumentation Integration Test.
 * Validates the full repository, Room database persistence, scoped storage lifecycle,
 * and filtering pipelines on an active Android runtime environment (device or emulator).
 */
@RunWith(AndroidJUnit4::class)
class CallRecordingIntegrationTest {

    private lateinit var database: CallRecordDatabase
    private lateinit var dao: CallRecordDao
    private lateinit var storageManager: StorageManager
    private lateinit var repository: CallRecordRepositoryImpl
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CallRecordDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.callRecordDao()
        storageManager = StorageManager(context)
        repository = CallRecordRepositoryImpl(dao, storageManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testEndToEndRecordingSaveRetrieveAndDelete() = runBlocking {
        // 1. Create a simulated recorded audio file in scoped storage
        val testFile = storageManager.createRecordingFile("+15550001234", isIncoming = true)
        testFile.writeBytes(ByteArray(1024) { 1 }) // Simulated 1KB AAC header/audio
        assertTrue(testFile.exists())

        // 2. Persist metadata via repository (as CallRecordingService does)
        val initialRecord = CallRecord(
            filePath = testFile.absolutePath,
            fileName = testFile.name,
            fileSizeBytes = testFile.length(),
            durationSeconds = 45,
            phoneNumber = "+15550001234",
            contactName = "John Doe",
            contactPhotoUri = null,
            isKnownContact = true,
            callType = CallType.INCOMING,
            timestamp = System.currentTimeMillis(),
            category = CallCategory.KNOWN
        )

        val insertedId = repository.saveRecording(initialRecord)
        assertTrue(insertedId > 0)

        // 3. Query all recordings
        val allRecords = repository.getRecordings(CallFilter()).first()
        assertEquals(1, allRecords.size)
        val saved = allRecords[0]
        assertEquals("John Doe", saved.contactName)
        assertEquals("+15550001234", saved.phoneNumber)
        assertEquals(45L, saved.durationSeconds)

        // 4. Test Favorite toggle
        repository.toggleFavorite(saved.id, true)
        val favRecords = repository.getRecordings(CallFilter(onlyFavorites = true)).first()
        assertEquals(1, favRecords.size)
        assertTrue(favRecords[0].isFavorite)

        // 5. Test Live Search
        val searchResults = repository.searchRecordings("John").first()
        assertEquals(1, searchResults.size)

        val emptySearch = repository.searchRecordings("NonExistent").first()
        assertTrue(emptySearch.isEmpty())

        // 6. Test Delete: must purge database row AND delete audio file from disk
        val deleteSuccess = repository.deleteRecording(saved.id)
        assertTrue(deleteSuccess)
        assertFalse("Audio file must be deleted from storage on record deletion", testFile.exists())

        val remaining = repository.getRecordings(CallFilter()).first()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun testUnknownNumberFiltering() = runBlocking {
        // Create a known contact call
        val knownFile = storageManager.createRecordingFile("+15551111111", isIncoming = true)
        knownFile.writeBytes(ByteArray(100))
        repository.saveRecording(
            CallRecord(
                filePath = knownFile.absolutePath,
                fileName = knownFile.name,
                fileSizeBytes = 100,
                durationSeconds = 20,
                phoneNumber = "+15551111111",
                contactName = "Alice Smith",
                contactPhotoUri = null,
                isKnownContact = true,
                callType = CallType.INCOMING,
                timestamp = System.currentTimeMillis(),
                category = CallCategory.KNOWN
            )
        )

        // Create an unknown caller call
        val unknownFile = storageManager.createRecordingFile("+19998887777", isIncoming = true)
        unknownFile.writeBytes(ByteArray(100))
        repository.saveRecording(
            CallRecord(
                filePath = unknownFile.absolutePath,
                fileName = unknownFile.name,
                fileSizeBytes = 100,
                durationSeconds = 15,
                phoneNumber = "+19998887777",
                contactName = null,
                contactPhotoUri = null,
                isKnownContact = false,
                callType = CallType.INCOMING,
                timestamp = System.currentTimeMillis(),
                category = CallCategory.UNKNOWN
            )
        )

        // Test filtering by unknown numbers tab
        val unknownList = repository.getRecordings(CallFilter(onlyUnknown = true)).first()
        assertEquals(1, unknownList.size)
        assertEquals("+19998887777", unknownList[0].phoneNumber)
        assertFalse(unknownList[0].isKnownContact)

        // Cleanup
        storageManager.deleteFile(knownFile.absolutePath)
        storageManager.deleteFile(unknownFile.absolutePath)
    }
}
