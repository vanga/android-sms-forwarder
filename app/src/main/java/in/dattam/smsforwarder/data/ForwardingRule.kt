@file:Suppress("DEPRECATION")

package `in`.dattam.smsforwarder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A rule deciding whether an incoming SMS should be forwarded.
 *
 * [senderPattern] is newline-delimited: each line is a case-insensitive Java
 * regex, anchored (whole-string). Plain strings without metachars match
 * themselves exactly. Use `.*` for substring, `.*-GOV-.*` for "contains",
 * `VK-\d{4}` for shapes. A single line of `*` is treated as match-everything
 * (convenience shortcut). Empty lines are skipped. Lines that fail to compile
 * are silently dropped. Multiple lines = logical OR.
 *
 * [bodyPattern] optional regex, substring match on the SMS body. Null/blank
 * means "don't gate on body".
 *
 * [bodyTemplate] JSON request body template. Null/blank falls back to the
 * app-wide default from Settings, then to the built-in
 * {from, text, sentStamp, receivedStamp, sim} payload. Placeholders:
 * %from%, %text%, %sentStamp%, %receivedStamp%, %sim%. Body is always POSTed
 * as application/json.
 *
 * [simSlot] 0 = any SIM, 1 / 2 = specific SIM.
 *
 * [patternMode] and [bodyContentType] are legacy schema columns retained for
 * back-compat; they are no longer read by the runtime.
 */
@Entity(tableName = "forwarding_rules")
data class ForwardingRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val senderPattern: String,
    @Deprecated("Matcher is always anchored regex; field retained for schema back-compat.")
    val patternMode: PatternMode = PatternMode.WILDCARD,
    val bodyPattern: String? = null,
    val url: String,
    val headersJson: String = "{}",
    val bodyTemplate: String? = null,
    @Deprecated("Body is always application/json; field retained for schema back-compat.")
    val bodyContentType: String = "application/json; charset=utf-8",
    val simSlot: Int = 0,
    val enabled: Boolean = true,
    val retries: Int = 10,
    val ignoreSsl: Boolean = false,
)

@Deprecated("No longer read by the matcher; every pattern is treated as anchored regex.")
enum class PatternMode { EXACT, WILDCARD, REGEX }
