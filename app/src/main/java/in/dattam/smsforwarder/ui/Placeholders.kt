package `in`.dattam.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

val SAMPLE_HEADERS_JSON: String = """
{
  "X-Api-Key": "replace-with-your-shared-secret"
}
""".trimIndent()

/**
 * Body shape expected by the OTP-receiver API that this app is paired with.
 * `mobile` is the receiving SIM's 10-digit number — hardcode it per rule so
 * the server can route the OTP to the right scraper-phone pool entry.
 */
val SAMPLE_BODY_TEMPLATE: String = """
{
  "message": "%text%",
  "from": "%from%",
  "time": "%receivedStamp%",
  "receivedStamp": %receivedStamp%,
  "sim": "%sim%",
  "mobile": "replace-with-10-digit-number"
}
""".trimIndent()

private val PLACEHOLDERS = listOf(
    "%from%" to "sender address / ID",
    "%text%" to "full message body",
    "%sentStamp%" to "epoch ms, from network",
    "%receivedStamp%" to "epoch ms, when phone got it",
    "%sim%" to "sim1 / sim2 / undetected",
)

/** Chips that append a placeholder to the target field on tap, plus a doc list. */
@Composable
fun PlaceholderHelper(onInsert: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PLACEHOLDERS.forEach { (token, _) ->
            AssistChip(onClick = { onInsert(token) }, label = { Text(token) })
        }
    }
    PLACEHOLDERS.forEach { (token, doc) ->
        Text(
            "$token — $doc",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
