package `in`.dattam.smsforwarder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A rule deciding whether an incoming SMS should be forwarded.
 *
 * [senderPattern] is newline-delimited: each line is tried independently, any
 * match fires the rule (logical OR). A single line with "*" matches everything.
 * Mode (driven by [patternMode]) applies to every line:
 *   EXACT    — case-insensitive exact match of the originating address.
 *   WILDCARD — shell-style wildcards: * matches any run of chars, ? one char.
 *              Example: "*VM-HDFCBK*" matches "VM-HDFCBK", "AX-HDFCBK-S".
 *   REGEX    — raw Java regex.
 * [simSlot] 0 = any, 1/2 = specific SIM.
 *
 * [bodyTemplate] is the request body. If null, a default JSON payload is sent
 * (from/text/sentStamp/receivedStamp/sim). Otherwise placeholders are substituted:
 *   %from%, %text%, %sentStamp%, %receivedStamp%, %sim%
 * [bodyContentType] is the Content-Type header for the request body.
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
    val bodyTemplate: String? = null,
    val bodyContentType: String = "application/json; charset=utf-8",
    val simSlot: Int = 0,
    val enabled: Boolean = true,
    val retries: Int = 10,
    val ignoreSsl: Boolean = false,
)

enum class PatternMode { EXACT, WILDCARD, REGEX }
