package `in`.dattam.smsforwarder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM forwarding_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<ForwardingRule>>

    @Query("SELECT * FROM forwarding_rules WHERE enabled = 1")
    suspend fun allEnabled(): List<ForwardingRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ForwardingRule): Long

    @Update
    suspend fun update(rule: ForwardingRule)

    @Delete
    suspend fun delete(rule: ForwardingRule)
}
