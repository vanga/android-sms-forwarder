package `in`.dattam.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

val SAMPLE_BODY_TEMPLATE: String = """
{
  "from": "%from%",
  "text": "%text%",
  "sentStamp": %sentStamp%,
  "receivedStamp": %receivedStamp%,
  "sim": "%sim%"
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
fun PlaceholderHelper(
    onInsert: (String) -> Unit,
    onInsertSample: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PLACEHOLDERS.forEach { (token, _) ->
            AssistChip(onClick = { onInsert(token) }, label = { Text(token) })
        }
        onInsertSample?.let {
            TextButton(onClick = it) { Text("Insert sample") }
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
