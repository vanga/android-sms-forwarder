package `in`.dattam.smsforwarder.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.dattam.smsforwarder.data.AppDefaults
import `in`.dattam.smsforwarder.data.SettingsRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepo(app)
    suspend fun load(): AppDefaults = repo.flow.first()
    fun save(d: AppDefaults, onDone: () -> Unit) = viewModelScope.launch {
        repo.save(d); onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onDone: () -> Unit, vm: SettingsViewModel = viewModel()) {
    var loaded by remember { mutableStateOf(false) }
    var headers by remember { mutableStateOf(SAMPLE_HEADERS_JSON) }
    var bodyTemplate by remember { mutableStateOf("") }
    var contentType by remember { mutableStateOf(AppDefaults.FALLBACK_CONTENT_TYPE) }

    LaunchedEffect(Unit) {
        val d = vm.load()
        headers = d.headersJson
        bodyTemplate = d.bodyTemplate
        contentType = d.contentType
        loaded = true
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Defaults") }) }) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "These apply to every rule that leaves the corresponding field blank. " +
                    "Good for auth tokens and a shared body template.",
            )
            OutlinedTextField(
                headers, { headers = it },
                label = { Text("Default Headers JSON") },
                supportingText = {
                    Text(
                        "Shared across all rules. Typical use: a shared-secret token " +
                            "header so your server can authenticate the webhook."
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                minLines = 3,
            )
            OutlinedTextField(
                bodyTemplate, { bodyTemplate = it },
                label = { Text("Default Body Template") },
                placeholder = { Text("Leave blank to send the built-in JSON payload.") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                minLines = 5,
            )
            PlaceholderHelper(onInsert = { bodyTemplate += it })
            OutlinedTextField(
                contentType, { contentType = it },
                label = { Text("Default Content-Type") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.save(
                        AppDefaults(
                            headersJson = headers.ifBlank { "{}" },
                            bodyTemplate = bodyTemplate,
                            contentType = contentType.ifBlank { AppDefaults.FALLBACK_CONTENT_TYPE },
                        ),
                        onDone,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}
