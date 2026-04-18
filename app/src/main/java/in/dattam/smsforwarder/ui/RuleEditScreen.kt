package `in`.dattam.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import `in`.dattam.smsforwarder.data.PatternMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(ruleId: Long, onDone: () -> Unit, vm: RulesViewModel = viewModel()) {
    val existing = vm.rules.value.firstOrNull { it.id == ruleId }

    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("*") }
    var mode by remember { mutableStateOf(PatternMode.WILDCARD) }
    var bodyPattern by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("{}") }
    var retries by remember { mutableStateOf("10") }
    var sim by remember { mutableStateOf("0") }

    LaunchedEffect(existing?.id) {
        existing?.let {
            name = it.name; pattern = it.senderPattern; mode = it.patternMode
            bodyPattern = it.bodyPattern.orEmpty(); url = it.url; headers = it.headersJson
            retries = it.retries.toString(); sim = it.simSlot.toString()
        }
    }

    var modeExpanded by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(if (ruleId > 0) "Edit Rule" else "New Rule") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pattern, { pattern = it }, label = { Text("Sender pattern") }, modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(expanded = modeExpanded, onExpandedChange = { modeExpanded = it }) {
                OutlinedTextField(
                    value = mode.name, onValueChange = {}, readOnly = true,
                    label = { Text("Pattern mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(modeExpanded, { modeExpanded = false }) {
                    PatternMode.entries.forEach { m ->
                        DropdownMenuItem(text = { Text(m.name) }, onClick = { mode = m; modeExpanded = false })
                    }
                }
            }

            OutlinedTextField(bodyPattern, { bodyPattern = it }, label = { Text("Body regex (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text("Webhook URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(headers, { headers = it }, label = { Text("Headers JSON") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(retries, { retries = it.filter(Char::isDigit) }, label = { Text("Retries") }, modifier = Modifier.weight(1f))
                OutlinedTextField(sim, { sim = it.filter(Char::isDigit) }, label = { Text("SIM (0=any)") }, modifier = Modifier.weight(1f))
            }

            Button(
                onClick = {
                    val rule = (existing ?: ForwardingRule(name = "", senderPattern = "", url = "")).copy(
                        name = name.ifBlank { pattern },
                        senderPattern = pattern,
                        patternMode = mode,
                        bodyPattern = bodyPattern.ifBlank { null },
                        url = url,
                        headersJson = headers.ifBlank { "{}" },
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
