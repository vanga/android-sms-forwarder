package `in`.dattam.smsforwarder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "webhook_deliveries",
    indices = [Index(value = ["smsKey", "ruleId"], unique = true)],
)
data class WebhookDelivery(
    @PrimaryKey val id: String,
    val smsKey: String,
    val ruleId: Long,
    val ruleName: String,
    val sender: String,
    val body: String,
    val sim: Int,
    val sentTs: Long,
    val recvTs: Long,
    val maxAttempts: Int,
    val attemptCount: Int = 0,
    val state: String = STATE_PENDING,
    val httpCode: Int? = null,
    val detail: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_IN_FLIGHT = "IN_FLIGHT"
        const val STATE_RETRYING = "RETRYING"
        const val STATE_POSTED = "POSTED"
        const val STATE_FAILED = "FAILED"
        const val STATE_SKIPPED = "SKIPPED"
    }
}
