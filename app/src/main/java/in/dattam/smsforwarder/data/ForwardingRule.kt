package `in`.dattam.smsforwarder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A rule deciding whether an incoming SMS should be forwarded.
 *
 * [senderPattern] matching modes (driven by [patternMode]):
 *   EXACT    — case-insensitive exact match of the originating address.
 *   WILDCARD — shell-style wildcards: * matches any run of chars, ? one char.
 *              Example: "*VM-HDFCBK*" matches "VM-HDFCBK", "AX-HDFCBK-S".
 *   REGEX    — raw Java regex.
 * [simSlot] 0 = any, 1/2 = specific SIM.
 */
@Entity(tableName = "forwarding_rules")
data class ForwardingRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val senderPattern: String,
    val patternMode: PatternMode = PatternMode.WILDCARD,
    val bodyPattern: String? = null,
    val url: String,
    val headersJson: String = "{}",
    val simSlot: Int = 0,
    val enabled: Boolean = true,
    val retries: Int = 10,
    val ignoreSsl: Boolean = false,
)

enum class PatternMode { EXACT, WILDCARD, REGEX }
