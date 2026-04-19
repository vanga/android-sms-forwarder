package `in`.dattam.smsforwarder.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.dattam.smsforwarder.data.AppDefaults
import `in`.dattam.smsforwarder.data.SettingsRepo
import `in`.dattam.smsforwarder.sms.SimHelper
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
    val ctx = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var headers by remember { mutableStateOf(SAMPLE_HEADERS_JSON) }
    var bodyTemplate by remember { mutableStateOf(SAMPLE_BODY_TEMPLATE) }
    var mobile by remember { mutableStateOf("") }
    var simHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val d = vm.load()
        headers = prettyJson(d.headersJson)
        bodyTemplate = d.bodyTemplate.let { if (it.isBlank()) SAMPLE_BODY_TEMPLATE else prettyJson(it) }
        mobile = d.mobileNumber
        // Auto-populate mobile from SIM if user hasn't set one yet.
        if (mobile.isBlank()) {
            val detected = SimHelper.readMobileNumber(ctx)
            if (!detected.isNullOrBlank()) {
                mobile = detected
                simHint = "Auto-detected from SIM. Edit if wrong, then Save."
            } else {
                simHint = "SIM didn't expose the number (common on Indian carriers). Enter manually."
            }
        }
        loaded = true
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Defaults") }) }) { pad ->
        if (!loaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "These apply to every rule that leaves the corresponding field blank. " +
                    "Good for auth tokens, a shared body template, and the device's phone number.",
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    mobile,
                    { mobile = it.filter(Char::isDigit).take(10) },
                    label = { Text("This phone's mobile number") },
                    placeholder = { Text("10 digits, no country code") },
                    supportingText = { simHint?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val n = SimHelper.readMobileNumber(ctx)
                    if (!n.isNullOrBlank()) {
                        mobile = n
                        simHint = "Filled from SIM."
                    } else {
                        simHint = "SIM didn't return a number."
                    }
                }) { Text("Read SIM") }
            }

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
            TextButton(
                onClick = { headers = prettyJson(headers) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Format JSON") }

            OutlinedTextField(
                bodyTemplate, { bodyTemplate = it },
                label = { Text("Default Body Template") },
                supportingText = { Text("Used by every rule that leaves its own template blank.") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                minLines = 5,
            )
            TextButton(
                onClick = { bodyTemplate = prettyJson(bodyTemplate) },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Format JSON") }
            PlaceholderHelper(onInsert = { bodyTemplate += it })

            Button(
                onClick = {
                    vm.save(
                        AppDefaults(
                            headersJson = headers.ifBlank { "{}" },
                            bodyTemplate = bodyTemplate,
                            mobileNumber = mobile,
                        ),
                        onDone,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}
