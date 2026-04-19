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

    /**
     * Most recent event per ruleId. Used to show a status chip on each rule row
     * at-a-glance. Returns at most one row per rule, null-ruleId events excluded.
     */
    @Query("""
        SELECT e.* FROM event_log e
        INNER JOIN (SELECT ruleId, MAX(id) mid FROM event_log WHERE ruleId IS NOT NULL GROUP BY ruleId) m
          ON e.id = m.mid
    """)
    fun observeLatestPerRule(): Flow<List<EventLog>>

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
