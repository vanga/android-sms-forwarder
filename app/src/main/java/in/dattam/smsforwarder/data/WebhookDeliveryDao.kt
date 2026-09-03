package `in`.dattam.smsforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WebhookDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(delivery: WebhookDelivery): Long

    @Query("SELECT * FROM webhook_deliveries WHERE smsKey = :smsKey AND ruleId = :ruleId LIMIT 1")
    suspend fun bySmsAndRule(smsKey: String, ruleId: Long): WebhookDelivery?

    @Query("SELECT * FROM webhook_deliveries WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): WebhookDelivery?

    @Query(
        """
        UPDATE webhook_deliveries
        SET state = 'IN_FLIGHT', attemptCount = attemptCount + 1, updatedAt = :now
        WHERE id = :id
          AND (state = 'PENDING' OR (state = 'IN_FLIGHT' AND updatedAt < :staleBefore))
        """
    )
    suspend fun claimInitial(id: String, now: Long, staleBefore: Long): Int

    @Query(
        """
        UPDATE webhook_deliveries
        SET state = 'IN_FLIGHT', attemptCount = attemptCount + 1, updatedAt = :now
        WHERE id = :id
          AND (state = 'RETRYING' OR (state = 'IN_FLIGHT' AND updatedAt < :staleBefore))
        """
    )
    suspend fun claimRetry(id: String, now: Long, staleBefore: Long): Int

    @Query(
        """
        UPDATE webhook_deliveries
        SET state = 'RETRYING', httpCode = :httpCode, detail = :detail, updatedAt = :now
        WHERE id = :id AND state = 'IN_FLIGHT'
        """
    )
    suspend fun markRetrying(id: String, httpCode: Int?, detail: String, now: Long)

    @Query(
        """
        UPDATE webhook_deliveries
        SET state = :state, sender = '', body = '', httpCode = :httpCode, detail = :detail, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun markTerminal(id: String, state: String, httpCode: Int?, detail: String?, now: Long)

    @Query(
        """
        DELETE FROM webhook_deliveries
        WHERE id IN (
            SELECT id FROM webhook_deliveries
            WHERE state IN ('POSTED', 'FAILED', 'SKIPPED')
            ORDER BY updatedAt DESC
            LIMIT -1 OFFSET :keep
        )
        """
    )
    suspend fun trimTerminal(keep: Int = 10_000)
}
