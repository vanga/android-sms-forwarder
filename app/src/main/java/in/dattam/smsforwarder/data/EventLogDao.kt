package `in`.dattam.smsforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_log ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<EventLog>>

    @Insert
    suspend fun insert(event: EventLog): Long

    @Query("DELETE FROM event_log WHERE id NOT IN (SELECT id FROM event_log ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int = 500)

    @Transaction
    suspend fun log(event: EventLog, keep: Int = 500) {
        insert(event)
        trim(keep)
    }

    @Query("DELETE FROM event_log")
    suspend fun clear()
}
