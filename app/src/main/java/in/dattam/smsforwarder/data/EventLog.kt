package `in`.dattam.smsforwarder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_log")
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long = System.currentTimeMillis(),
    val stage: String,            // RECEIVED | MATCHED | SKIPPED | ENQUEUED | POSTED | FAILED
    val sender: String,
    val bodyPreview: String,      // first ~120 chars, for privacy + size
    val ruleId: Long? = null,
    val ruleName: String? = null,
    val httpCode: Int? = null,
    val attempt: Int? = null,
    val detail: String? = null,   // skip reason, error message
)
