package `in`.dattam.smsforwarder.matcher

import `in`.dattam.smsforwarder.data.ForwardingRule
import `in`.dattam.smsforwarder.data.PatternMode

object SenderMatcher {

    fun matches(rule: ForwardingRule, sender: String, body: String): Boolean {
        if (!matchSender(rule, sender)) return false
        val bp = rule.bodyPattern?.takeIf { it.isNotBlank() } ?: return true
        return runCatching { Regex(bp).containsMatchIn(body) }.getOrDefault(false)
    }

    private fun matchSender(rule: ForwardingRule, sender: String): Boolean {
        val p = rule.senderPattern
        if (p == "*" || p.isBlank()) return true
        return when (rule.patternMode) {
            PatternMode.EXACT -> sender.equals(p, ignoreCase = true)
            PatternMode.WILDCARD -> Regex(wildcardToRegex(p), RegexOption.IGNORE_CASE).matches(sender)
            PatternMode.REGEX -> runCatching {
                Regex(p, RegexOption.IGNORE_CASE).containsMatchIn(sender)
            }.getOrDefault(false)
        }
    }

    /** Shell glob → regex. Supports * and ? only. */
    private fun wildcardToRegex(glob: String): String = buildString {
        append('^')
        for (c in glob) when (c) {
            '*' -> append(".*")
            '?' -> append('.')
            '.', '(', ')', '[', ']', '{', '}', '+', '|', '^', '$', '\\' -> {
                append('\\'); append(c)
            }
            else -> append(c)
        }
        append('$')
    }
}
