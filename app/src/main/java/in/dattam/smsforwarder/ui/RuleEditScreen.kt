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
    var contentType by remember { mutableStateOf("application/json; charset=utf-8") }
    var retries by remember { mutableStateOf("10") }
    var sim by remember { mutableStateOf("0") }

    LaunchedEffect(existing?.id) {
        existing?.let {
            name = it.name; pattern = it.senderPattern
            bodyPattern = it.bodyPattern.orEmpty(); url = it.url; headers = it.headersJson
            bodyTemplate = it.bodyTemplate.orEmpty(); contentType = it.bodyContentType
            retries = it.retries.toString(); sim = it.simSlot.toString()
        }
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
                supportingText = {
                    Text(
                        "Each line is a Java regex, anchored (whole-string match). " +
                            "Plain strings match themselves exactly. Use .* for wildcard, " +
                            ".*-GOV-.* for substring."
                    )
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
                label = { Text("Headers JSON") },
                supportingText = { Text("Merged with app-wide default headers; rule keys win.") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                bodyTemplate, { bodyTemplate = it },
                label = { Text("Body template") },
                supportingText = { Text("Clear to inherit the default body template from settings.") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                minLines = 4,
            )
            PlaceholderHelper(
                onInsert = { bodyTemplate += it },
                onInsertSample = { if (bodyTemplate.isBlank()) bodyTemplate = SAMPLE_BODY_TEMPLATE },
            )
            OutlinedTextField(
                contentType, { contentType = it },
                label = { Text("Content-Type") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(retries, { retries = it.filter(Char::isDigit) }, label = { Text("Retries") }, modifier = Modifier.weight(1f))
                OutlinedTextField(sim, { sim = it.filter(Char::isDigit) }, label = { Text("SIM (0=any)") }, modifier = Modifier.weight(1f))
            }

            Button(
                onClick = {
                    val rule = (existing ?: ForwardingRule(name = "", senderPattern = "", url = "")).copy(
                        name = name.ifBlank { pattern.lineSequence().firstOrNull()?.trim().orEmpty() },
                        senderPattern = pattern,
                        bodyPattern = bodyPattern.ifBlank { null },
                        url = url,
                        headersJson = headers.ifBlank { "{}" },
                        bodyTemplate = bodyTemplate.ifBlank { null },
                        bodyContentType = contentType.ifBlank { "application/json; charset=utf-8" },
                        retries = retries.toIntOrNull() ?: 10,
                        simSlot = sim.toIntOrNull() ?: 0,
                    )
                    vm.upsert(rule); onDone()
                },
                enabled = url.isNotBlank() && pattern.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}
