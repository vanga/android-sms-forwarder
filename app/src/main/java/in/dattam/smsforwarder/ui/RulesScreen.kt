package `in`.dattam.smsforwarder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.dattam.smsforwarder.data.EventLog
import `in`.dattam.smsforwarder.data.ForwardingRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onEdit: (Long?) -> Unit,
    onOpenEvents: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: RulesViewModel = viewModel(),
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val latestByRule by vm.latestByRule.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var toDelete by remember { mutableStateOf<ForwardingRule?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Forwarder") },
                actions = {
                    IconButton(onClick = onOpenEvents) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Events")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Defaults")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New rule") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (rules.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(pad),
                onAdd = { onEdit(null) },
                onSettings = onOpenSettings,
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(rules, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    lastEvent = latestByRule[rule.id],
                    onToggle = { vm.toggle(rule) },
                    onEdit = { onEdit(rule.id) },
                    onDelete = { toDelete = rule },
                )
            }
        }
    }

    toDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete rule?") },
            text = { Text("This removes '${rule.name}'. Existing event log entries stay.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(rule); toDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: ForwardingRule,
    lastEvent: EventLog?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            rule.name,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.size(8.dp))
                        if (lastEvent != null) StatusChip(lastEvent)
                    }
                    Spacer(Modifier.size(2.dp))
                    val senders = rule.senderPattern.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                    Text(
                        if (senders.isEmpty()) "matches everything"
                        else senders.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        rule.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(e: EventLog) {
    val (bg, fg, label) = when (e.stage) {
        "POSTED" -> Triple(Color(0xFFDCEDC8), Color(0xFF2E7D32), "last ${e.httpCode ?: "?"}")
        "FAILED" -> Triple(Color(0xFFFFCDD2), Color(0xFFB00020), "fail ${e.httpCode ?: ""}".trim())
        "RETRYING" -> Triple(Color(0xFFFFE0B2), Color(0xFFE65100), "retry")
        "SKIPPED" -> Triple(Color(0xFFE0E0E0), Color(0xFF616161), "skipped")
        "ENQUEUED" -> Triple(Color(0xFFBBDEFB), Color(0xFF1565C0), "queued")
        else -> Triple(Color(0xFFEDE7F6), Color(0xFF5E35B1), e.stage.lowercase())
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No rules yet.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(8.dp))
        Text(
            "Two steps to get forwarding:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("1. Set your API key and phone number in Defaults.")
            Text("2. Add a rule with sender patterns and the webhook URL.")
        }
        Spacer(Modifier.size(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSettings) { Text("Open defaults") }
            androidx.compose.material3.Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.size(4.dp))
                Text("Add rule")
            }
        }
    }
}
