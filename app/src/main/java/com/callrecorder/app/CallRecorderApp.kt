package com.callrecorder.app

import android.app.Application
import com.callrecorder.app.core.audio.AudioPlayerEngine
import com.callrecorder.app.core.storage.StorageManager
import com.callrecorder.app.data.local.CallRecordDatabase
import com.callrecorder.app.data.repository.CallRecordRepositoryImpl
import com.callrecorder.app.domain.repository.CallRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Application class providing application-level singletons.
 */
class CallRecorderApp : Application() {

    lateinit var database: CallRecordDatabase
        private set

    lateinit var storageManager: StorageManager
        private set

    lateinit var repository: CallRecordRepository
        private set

    val audioPlayerEngine = AudioPlayerEngine()

    override fun onCreate() {
        super.onCreate()

        database = CallRecordDatabase.getDatabase(this)
        storageManager = StorageManager(this)
        repository = CallRecordRepositoryImpl(database.callRecordDao(), storageManager)

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repository.cleanDuplicateRecordings()
            repository.syncContactsAndCallLogs(this@CallRecorderApp)
        }
    }

    override fun onTerminate() {
        audioPlayerEngine.stop()
        super.onTerminate()
    }
}
