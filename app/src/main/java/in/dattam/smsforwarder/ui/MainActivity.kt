package `in`.dattam.smsforwarder.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `in`.dattam.smsforwarder.data.SettingsRepo
import `in`.dattam.smsforwarder.service.ForwardingService
import `in`.dattam.smsforwarder.sms.SimHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var promptSettingsForMobile by mutableStateOf(false)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            checkAndPersistMobileNumber()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestCorePermissions()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "rules") {
                        composable("rules") {
                            LaunchedEffect(promptSettingsForMobile) {
                                if (promptSettingsForMobile) {
                                    promptSettingsForMobile = false
                                    nav.navigate("settings")
                                }
                            }
                            RulesScreen(
                                onEdit = { id, clone ->
                                    val route = when {
                                        clone && id != null -> "clone/$id"
                                        else -> "edit/${id ?: -1L}"
                                    }
                                    nav.navigate(route)
                                },
                                onOpenEvents = { nav.navigate("events") },
                                onOpenSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("edit/{id}") { backStack ->
                            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: -1L
                            RuleEditScreen(ruleId = id, cloneFromRuleId = null, onDone = { nav.popBackStack() })
                        }
                        composable("clone/{id}") { backStack ->
                            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: -1L
                            RuleEditScreen(ruleId = -1L, cloneFromRuleId = id, onDone = { nav.popBackStack() })
                        }
                        composable("events") { EventsScreen() }
                        composable("settings") { SettingsScreen(onDone = { nav.popBackStack() }) }
                    }
                }
            }
        }

        checkAndPersistMobileNumber()
        lifecycleScope.launch {
            if (SettingsRepo(this@MainActivity).current().enhancedReliability) {
                ForwardingService.start(this@MainActivity)
            }
        }
    }

    private fun requestCorePermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
        )
        permLauncher.launch(perms.toTypedArray())
    }

    private fun checkAndPersistMobileNumber() {
        lifecycleScope.launch {
            val repo = SettingsRepo(this@MainActivity)
            val current = repo.current()
            if (current.mobileNumber.length == 10) {
                promptSettingsForMobile = false
                return@launch
            }

            val detected = SimHelper.readMobileNumber(this@MainActivity)
            if (!detected.isNullOrBlank() && detected.length == 10) {
                repo.save(current.copy(mobileNumber = detected))
                promptSettingsForMobile = false
            } else {
                promptSettingsForMobile = true
            }
        }
    }
}
