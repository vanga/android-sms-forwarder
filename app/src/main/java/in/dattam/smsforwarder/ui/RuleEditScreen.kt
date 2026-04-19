package `in`.dattam.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.dattam.smsforwarder.data.ForwardingRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(ruleId: Long, onDone: () -> Unit, vm: RulesViewModel = viewModel()) {
    val existing = vm.rules.value.firstOrNull { it.id == ruleId }

    val isNew = ruleId <= 0
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf(if (isNew) ".*-GOV-.*" else "") }
    var bodyPattern by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("{}") }
    var bodyTemplate by remember { mutableStateOf(if (isNew) SAMPLE_BODY_TEMPLATE else "") }
    var retries by remember { mutableStateOf("10") }
    var sim by remember { mutableStateOf("0") }
    var hydrated by remember { mutableStateOf(isNew) }

    // Only hydrate once from the first non-null emission of the rule list;
    // subsequent emissions (e.g. another rule toggle while the editor is open)
    // would otherwise overwrite in-progress user edits.
    LaunchedEffect(existing?.id) {
        val e = existing
        if (!hydrated && e != null) {
            name = e.name; pattern = e.senderPattern
            bodyPattern = e.bodyPattern.orEmpty(); url = e.url; headers = e.headersJson
            bodyTemplate = e.bodyTemplate.orEmpty()
            retries = e.retries.toString(); sim = e.simSlot.toString()
            hydrated = true
        }
    }

    // Live regex validation — show which lines won't compile.
    val badLines = remember(pattern) {
        pattern.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "*" }
            .filter { line -> runCatching { Regex(line) }.isFailure }
            .toList()
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (ruleId > 0) "Edit Rule" else "New Rule") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())

            SectionLabel("Senders")
            OutlinedTextField(
                pattern, { pattern = it },
                label = { Text("Sender patterns (one per line, OR)") },
                isError = badLines.isNotEmpty(),
                supportingText = {
                    if (badLines.isEmpty()) {
                        Text(
                            "Each line is a Java regex, anchored (whole-string match). " +
                                "Plain strings match themselves exactly. Use .* for wildcard, " +
                                ".*-GOV-.* for substring."
                        )
                    } else {
                        Text(
                            "Invalid regex on ${badLines.size} line(s): " +
                                badLines.joinToString(", ") { "'$it'" }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                minLines = 3,
            )
            OutlinedTextField(
                bodyPattern, { bodyPattern = it },
                label = { Text("Body regex filter (optional)") },
                supportingText = { Text("Substring regex match; leave blank to skip.") },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Webhook")
            OutlinedTextField(url, { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                headers, { headers = it },
                label = { Text("Headers JSON (rule-specific)") },
                supportingText = {
                    Text(
                        "Leave as {} to use only app-wide default headers. " +
                            "Rule keys override defaults on collision."
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                bodyTemplate, { bodyTemplate = it },
                label = { Text("Body template") },
                supportingText = { Text("Clear to inherit the default body template from settings.") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                minLines = 4,
            )
            PlaceholderHelper(onInsert = { bodyTemplate += it })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    retries,
                    { retries = it.filter(Char::isDigit).take(2) },
                    label = { Text("Max attempts (1-20)") },
                    supportingText = { Text("1 means no retry.") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    sim,
                    { sim = it.filter(Char::isDigit).take(1) },
                    label = { Text("SIM 0=any, 1, 2") },
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = {
                    val clampedRetries = (retries.toIntOrNull() ?: 10).coerceIn(1, 20)
                    val clampedSim = (sim.toIntOrNull() ?: 0).coerceIn(0, 2)
                    val rule = (existing ?: ForwardingRule(name = "", senderPattern = "", url = "")).copy(
                        name = name.ifBlank { pattern.lineSequence().firstOrNull()?.trim().orEmpty() },
                        senderPattern = pattern,
                        bodyPattern = bodyPattern.ifBlank { null },
                        url = url,
                        headersJson = headers.ifBlank { "{}" },
                        bodyTemplate = bodyTemplate.ifBlank { null },
                        retries = clampedRetries,
                        simSlot = clampedSim,
                    )
                    vm.upsert(rule); onDone()
                },
                enabled = url.isNotBlank() && pattern.isNotBlank() && badLines.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}
