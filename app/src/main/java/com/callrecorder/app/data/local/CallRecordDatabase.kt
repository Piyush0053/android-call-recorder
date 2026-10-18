package com.callrecorder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.callrecorder.app.data.local.entity.CallRecordEntity

/**
 * Main Room Database for the Call Recorder application.
 */
@Database(
    entities = [CallRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CallRecordDatabase : RoomDatabase() {

    abstract fun callRecordDao(): CallRecordDao

    companion object {
        @Volatile
        private var INSTANCE: CallRecordDatabase? = null

        fun getDatabase(context: Context): CallRecordDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CallRecordDatabase::class.java,
                    "call_recordings.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
