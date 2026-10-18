package com.callrecorder.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.callrecorder.app.data.local.entity.CallRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for querying and manipulating call recordings in Room.
 */
@Dao
interface CallRecordDao {

    @Query("SELECT * FROM call_recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_recordings WHERE isKnownContact = 1 ORDER BY timestamp DESC")
    fun getKnownContactRecordings(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_recordings WHERE isKnownContact = 0 ORDER BY timestamp DESC")
    fun getUnknownNumberRecordings(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_recordings WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteRecordings(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: Long): CallRecordEntity?

    @Query("""
        SELECT * FROM call_recordings 
        WHERE (contactName LIKE '%' || :query || '%' 
           OR phoneNumber LIKE '%' || :query || '%' 
           OR notes LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
    """)
    fun searchRecordings(query: String): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_recordings ORDER BY timestamp DESC")
    suspend fun getAllRecordingsSync(): List<CallRecordEntity>

    @Query("""
        SELECT * FROM call_recordings 
        WHERE (:category = 'ALL' OR category = :category)
          AND (:onlyKnown = 0 OR isKnownContact = 1)
          AND (:onlyUnknown = 0 OR isKnownContact = 0)
          AND (:onlyFavorites = 0 OR isFavorite = 1)
          AND (contactName LIKE '%' || :query || '%' 
               OR phoneNumber LIKE '%' || :query || '%' 
               OR notes LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
    """)
    fun filterRecordings(
        query: String,
        category: String,
        onlyKnown: Int,
        onlyUnknown: Int,
        onlyFavorites: Int
    ): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: CallRecordEntity): Long

    @Update
    suspend fun updateRecording(recording: CallRecordEntity)

    @Delete
    suspend fun deleteRecording(recording: CallRecordEntity)

    @Query("DELETE FROM call_recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)

    @Query("UPDATE call_recordings SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE call_recordings SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("SELECT COUNT(*) FROM call_recordings")
    fun getRecordingCount(): Flow<Int>

    @Query("SELECT SUM(fileSizeBytes) FROM call_recordings")
    fun getTotalStorageUsed(): Flow<Long?>

    @Query("SELECT * FROM call_recordings WHERE timestamp < :cutoffTimestamp AND isFavorite = 0")
    suspend fun getRecordingsOlderThan(cutoffTimestamp: Long): List<CallRecordEntity>

    @Query("SELECT * FROM call_recordings WHERE filePath = :filePath LIMIT 1")
    suspend fun getRecordingByFilePath(filePath: String): CallRecordEntity?

    @Query("DELETE FROM call_recordings WHERE id NOT IN (SELECT MIN(id) FROM call_recordings GROUP BY filePath)")
    suspend fun deleteDuplicateRecordings()
}
