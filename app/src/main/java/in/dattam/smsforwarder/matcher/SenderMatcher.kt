package `in`.dattam.smsforwarder.matcher

import `in`.dattam.smsforwarder.data.ForwardingRule

/**
 * Per-line regex, anchored (whole-string) match. Plain strings without regex
 * metachars match themselves exactly, so they behave as "exact match" without
 * needing a separate mode. Use `.*` for substring-style matching.
 *
 * Case-insensitive. Empty lines skipped. Lines that fail to compile are
 * silently dropped (do not block other patterns in the rule).
 * Multiple lines = logical OR.
 */
object SenderMatcher {

    fun matches(rule: ForwardingRule, sender: String, body: String): Boolean {
        if (!matchSender(rule, sender)) return false
        val bp = rule.bodyPattern?.takeIf { it.isNotBlank() } ?: return true
        return runCatching { Regex(bp).containsMatchIn(body) }.getOrDefault(false)
    }

    private fun matchSender(rule: ForwardingRule, sender: String): Boolean {
        val lines = rule.senderPattern.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return true
        // Convenience: a bare "*" is an invalid regex, but users intuitively read it
        // as "match everything". Treat it as such.
        if (lines.any { it == "*" }) return true
        return lines.any { line ->
            runCatching {
                Regex(line, RegexOption.IGNORE_CASE).matches(sender)
            }.getOrDefault(false)
        }
    }

}
