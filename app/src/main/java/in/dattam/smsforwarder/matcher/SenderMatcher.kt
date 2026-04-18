package `in`.dattam.smsforwarder.matcher

import `in`.dattam.smsforwarder.data.ForwardingRule

/**
 * Per-line auto-detected matching:
 *   - Line starting with "re:"  → regex (prefix stripped).
 *   - Any other line            → wildcard (* and ?), which trivially subsumes
 *                                 exact match when no wildcards are present.
 * Case-insensitive throughout. Empty lines are skipped. Any line containing
 * a bare "*" matches everything. Multiple lines = logical OR.
 */
object SenderMatcher {

    private const val REGEX_PREFIX = "re:"

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
        if (lines.isEmpty() || lines.any { it == "*" }) return true
        return lines.any { line -> matchOne(line, sender) }
    }

    private fun matchOne(pattern: String, sender: String): Boolean {
        if (pattern.startsWith(REGEX_PREFIX, ignoreCase = true)) {
            val body = pattern.substring(REGEX_PREFIX.length).trim()
            if (body.isEmpty()) return false
            return runCatching {
                Regex(body, RegexOption.IGNORE_CASE).containsMatchIn(sender)
            }.getOrDefault(false)
        }
        return Regex(wildcardToRegex(pattern), RegexOption.IGNORE_CASE).matches(sender)
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
