package `in`.dattam.smsforwarder.ui

import android.app.Application
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.dattam.smsforwarder.data.AppDefaults
import `in`.dattam.smsforwarder.data.SettingsRepo
import `in`.dattam.smsforwarder.service.ForwardingService
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
    var enhancedReliability by remember { mutableStateOf(false) }
    val headersError = remember(headers) { validateJsonObject(headers) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        ForwardingService.start(ctx)
        requestBatteryExemption(ctx)
        onDone()
    }

    LaunchedEffect(Unit) {
        val d = vm.load()
        headers = prettyJson(d.headersJson)
        bodyTemplate = d.bodyTemplate.let { if (it.isBlank()) SAMPLE_BODY_TEMPLATE else prettyJson(it) }
        mobile = d.mobileNumber
        enhancedReliability = d.enhancedReliability
        // Auto-populate mobile from SIM if user hasn't set one yet. Indian
        // carriers often leave this empty on the SIM — user just types it.
        if (mobile.isBlank()) {
            SimHelper.readMobileNumber(ctx)?.takeIf { it.isNotBlank() }?.let {
                mobile = it
                simHint = "Auto-filled from SIM — edit if wrong."
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

            OutlinedTextField(
                mobile,
                { mobile = it.filter(Char::isDigit).take(10) },
                label = { Text("This phone's mobile number *") },
                placeholder = { Text("10 digits, no country code") },
                supportingText = {
                    val isValid = mobile.length == 10
                    when {
                        !isValid -> Text(
                            "Required. Enter 10 digits.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        simHint != null -> Text(simHint!!)
                        else -> Text("Used by the %mobile% placeholder in body templates.")
                    }
                },
                isError = mobile.isNotEmpty() && mobile.length != 10,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                headers, { headers = it },
                label = { Text("Default Headers JSON") },
                isError = headersError != null,
                supportingText = {
                    Text(
                        headersError ?: (
                            "Shared across all rules. Typical use: a shared-secret token " +
                                "header so your server can authenticate the webhook."
                            )
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

            Text("Delivery reliability", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Enhanced reliability")
                    Text(
                        "Optional. Keeps SMS Forwarder running and asks Android for unrestricted " +
                            "battery use. Enable this only if standard mode misses messages.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = enhancedReliability,
                    onCheckedChange = { enhancedReliability = it },
                )
            }

            Button(
                onClick = {
                    vm.save(
                        AppDefaults(
                            headersJson = headers.ifBlank { "{}" },
                            bodyTemplate = bodyTemplate,
                            mobileNumber = mobile,
                            enhancedReliability = enhancedReliability,
                        ),
                    ) {
                        if (enhancedReliability) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                ForwardingService.start(ctx)
                                requestBatteryExemption(ctx)
                                onDone()
                            }
                        } else {
                            ctx.stopService(Intent(ctx, ForwardingService::class.java))
                            onDone()
                        }
                    }
                },
                enabled = mobile.length == 10 && headersError == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

private fun requestBatteryExemption(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    }
}
