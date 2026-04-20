package `in`.dattam.smsforwarder.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.data.EventLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.eventLogDao()
    val events = dao.observeRecent(500).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val rules = db.ruleDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun clear() = viewModelScope.launch { dao.clear() }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(vm: EventsViewModel = viewModel()) {
    val events by vm.events.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val countsByRule = remember(events) { events.groupingBy { it.ruleId }.eachCount() }
    var selectedRuleId by remember { mutableStateOf<Long?>(null) }
    val filteredEvents = remember(events, selectedRuleId) {
        events.filter { selectedRuleId == null || it.ruleId == selectedRuleId }
    }
    val filterableRules = remember(rules, countsByRule) {
        rules.filter { countsByRule.containsKey(it.id) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Events (${filteredEvents.size})") },
            actions = {
                IconButton(onClick = { vm.clear() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                }
            },
        )
    }) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filterableRules.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedRuleId == null,
                        onClick = { selectedRuleId = null },
                        label = { Text("All (${events.size})") },
                    )
                    filterableRules.forEach { rule ->
                        FilterChip(
                            selected = selectedRuleId == rule.id,
                            onClick = { selectedRuleId = rule.id },
                            label = { Text("${rule.name} (${countsByRule[rule.id] ?: 0})") },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filteredEvents, key = { it.id }) { EventRow(it, fmt) }
            }
        }
    }
}

@Composable
private fun EventRow(e: EventLog, fmt: SimpleDateFormat) {
    Card(Modifier.fillMaxSize()) {
        Column(Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fmt.format(Date(e.ts)), style = MaterialTheme.typography.labelSmall)
                Text(
                    e.stage,
                    color = colorForStage(e.stage),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
                e.httpCode?.let { Text("[$it]", style = MaterialTheme.typography.labelMedium) }
                e.attempt?.let { Text("try=$it", style = MaterialTheme.typography.labelSmall) }
            }
            Text("from ${e.sender}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            e.ruleName?.let { Text("rule: $it", style = MaterialTheme.typography.bodySmall) }
            if (e.bodyPreview.isNotBlank()) {
                Text(e.bodyPreview, style = MaterialTheme.typography.bodySmall)
            }
            e.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB00020))
            }
        }
    }
}

private fun colorForStage(stage: String): Color = when (stage) {
    "POSTED" -> Color(0xFF2E7D32)     // green
    "FAILED" -> Color(0xFFB00020)     // red
    "RETRYING" -> Color(0xFFE65100)   // orange — retry in flight
    "SKIPPED" -> Color(0xFF757575)    // grey
    "MATCHED", "ENQUEUED" -> Color(0xFF1565C0)  // blue
    "RECEIVED" -> Color(0xFF5E35B1)   // purple
    else -> Color.Unspecified
}
