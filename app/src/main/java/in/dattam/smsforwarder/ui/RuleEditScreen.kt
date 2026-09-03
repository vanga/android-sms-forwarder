package `in`.dattam.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.dattam.smsforwarder.data.ForwardingRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    ruleId: Long,
    cloneFromRuleId: Long? = null,
    onDone: () -> Unit,
    vm: RulesViewModel = viewModel(),
) {
    var currentRuleId by remember(ruleId) { mutableStateOf(ruleId) }
    var currentCloneSourceId by remember(cloneFromRuleId) { mutableStateOf(cloneFromRuleId) }
    val rulesState by vm.rules.collectAsStateWithLifecycle()
    val existing = rulesState.firstOrNull { it.id == currentRuleId }
    val cloneSource = rulesState.firstOrNull { it.id == currentCloneSourceId }
    val testingId by vm.testing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val isClone = cloneSource != null
    val isNew = currentRuleId <= 0 || isClone
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf(if (isNew) ".*-GOV-.*" else "") }
    var bodyPattern by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("{}") }
    var bodyTemplate by remember { mutableStateOf(if (isNew) SAMPLE_BODY_TEMPLATE else "") }
    var retries by remember { mutableStateOf("10") }
    var sim by remember { mutableStateOf("0") }
    var hydrated by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(isNew) }

    LaunchedEffect(existing?.id, cloneSource?.id) {
        val e = cloneSource ?: existing
        if (!hydrated && e != null) {
            name = if (isClone) "${e.name} (copy)" else e.name
            pattern = e.senderPattern
            bodyPattern = e.bodyPattern.orEmpty(); url = e.url
            headers = prettyJson(e.headersJson)
            bodyTemplate = e.bodyTemplate?.let(::prettyJson).orEmpty()
            retries = e.retries.toString(); sim = e.simSlot.toString()
            hydrated = true
            dirty = isClone
        } else if (!hydrated && currentCloneSourceId == null && isNew && e == null) {
            hydrated = true
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val badLines = remember(pattern) {
        pattern.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "*" }
            .filter { line -> runCatching { Regex(line) }.isFailure }
            .toList()
    }
    val headersError = remember(headers) { validateJsonObject(headers) }
    val isTesting = testingId != null && testingId == currentRuleId
    val canSave = url.isNotBlank() && pattern.isNotBlank() && badLines.isEmpty() && headersError == null

    fun buildRule(): ForwardingRule {
        val clampedRetries = (retries.toIntOrNull() ?: 10).coerceIn(1, 20)
        val clampedSim = (sim.toIntOrNull() ?: 0).coerceIn(0, 2)
        val baseRule = if (isClone) {
            cloneSource.copy(id = 0)
        } else {
            existing
        } ?: ForwardingRule(name = "", senderPattern = "", url = "")
        return baseRule.copy(
            name = name.ifBlank { pattern.lineSequence().firstOrNull()?.trim().orEmpty() },
            senderPattern = pattern,
            bodyPattern = bodyPattern.ifBlank { null },
            url = url,
            headersJson = headers.ifBlank { "{}" },
            bodyTemplate = bodyTemplate.ifBlank { null },
            retries = clampedRetries,
            simSlot = clampedSim,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isClone -> "Clone Rule"
                            currentRuleId > 0 -> "Edit Rule"
                            else -> "New Rule"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                name,
                { name = it; dirty = true },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Senders")
            OutlinedTextField(
                pattern, { pattern = it; dirty = true },
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
                bodyPattern, { bodyPattern = it; dirty = true },
                label = { Text("Body regex filter (optional)") },
                supportingText = { Text("Substring regex match on the SMS text; leave blank to skip.") },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Webhook")
            OutlinedTextField(
                url, { url = it; dirty = true },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                headers, { headers = it; dirty = true },
                label = { Text("Headers JSON (rule-specific)") },
                isError = headersError != null,
                supportingText = {
                    Text(
                        headersError ?: (
                            "Leave as {} to use only app-wide default headers. " +
                                "Rule keys override defaults on collision."
                            )
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                minLines = 2,
            )
            TextButton(
                onClick = { headers = prettyJson(headers) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Format JSON") }

            OutlinedTextField(
                bodyTemplate, { bodyTemplate = it; dirty = true },
                label = { Text("Body template") },
                supportingText = { Text("Clear to inherit the default body template from settings.") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                minLines = 4,
            )
            TextButton(
                onClick = { bodyTemplate = prettyJson(bodyTemplate) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Format JSON") }
            PlaceholderHelper(onInsert = { bodyTemplate += it; dirty = true })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    retries,
                    { retries = it.filter(Char::isDigit).take(2); dirty = true },
                    label = { Text("Max attempts (1-20)") },
                    supportingText = { Text("1 means no retry.") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    sim,
                    { sim = it.filter(Char::isDigit).take(1); dirty = true },
                    label = { Text("SIM 0=any, 1, 2") },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.size(8.dp))

            // Manual test still targets the currently saved rule. The primary
            // action below handles the common "save current draft, then test it"
            // flow in one step.
            val canTest = existing != null && !dirty && !isTesting && canSave
            val testHint = when {
                isClone -> "Save the cloned rule once to enable Test."
                existing == null -> "Save the rule once to enable Test."
                dirty -> "Save to test current edits."
                else -> null
            }
            OutlinedButton(
                onClick = { existing?.let(vm::sendTest) },
                enabled = canTest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Sending test…")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Send test POST")
                }
            }
            testHint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Button(
                onClick = {
                    if (existing != null && !dirty) {
                        vm.sendTest(existing)
                    } else {
                        val draft = buildRule()
                        vm.upsert(draft) { savedId ->
                            currentRuleId = savedId
                            currentCloneSourceId = null
                            dirty = false
                            vm.sendTest(draft.copy(id = savedId))
                        }
                    }
                },
                enabled = canSave && !isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Saving and testing…")
                } else {
                    Text("Save and test")
                }
            }

            OutlinedButton(
                onClick = {
                    vm.upsert(buildRule()) { savedId ->
                        currentRuleId = savedId
                        currentCloneSourceId = null
                        dirty = false
                    }
                },
                enabled = canSave && !isTesting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (existing == null || isClone) "Save" else "Save changes") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}
