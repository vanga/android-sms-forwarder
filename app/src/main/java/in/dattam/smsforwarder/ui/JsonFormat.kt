package `in`.dattam.smsforwarder.ui

/**
 * Minimal pretty-printer for JSON-shaped strings. Does not parse or validate —
 * walks characters and inserts newlines + indentation based on structural
 * punctuation, respecting string literals. Handles our body templates which
 * contain bare placeholder tokens like `%receivedStamp%` that become numeric
 * literals at runtime and are therefore not valid JSON before substitution.
 *
 * Not a substitute for a real JSON formatter on arbitrary untrusted input,
 * but sufficient for structured text users type into the rule editor.
 */
fun prettyJson(src: String, indent: String = "  "): String {
    if (src.isBlank()) return src
    val out = StringBuilder(src.length + 64)
    var depth = 0
    var inString = false
    var escape = false

    fun newline() {
        out.append('\n')
        repeat(depth) { out.append(indent) }
    }

    for (c in src) {
        if (escape) {
            out.append(c); escape = false; continue
        }
        if (inString) {
            out.append(c)
            when (c) {
                '\\' -> escape = true
                '"' -> inString = false
            }
            continue
        }
        when (c) {
            '"' -> { out.append(c); inString = true }
            '{', '[' -> { out.append(c); depth++; newline() }
            '}', ']' -> { depth--; newline(); out.append(c) }
            ',' -> { out.append(c); newline() }
            ':' -> { out.append(c); out.append(' ') }
            ' ', '\t', '\n', '\r' -> { /* collapse existing whitespace */ }
            else -> out.append(c)
        }
    }
    return out.toString()
}
