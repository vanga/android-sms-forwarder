package `in`.dattam.smsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.data.ForwardingRule
import `in`.dattam.smsforwarder.webhook.WebhookWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class RulesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.ruleDao()
    val rules = dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** ruleId → latest event, for at-a-glance status chips on rule rows. */
    val latestByRule = db.eventLogDao().observeLatestPerRule()
        .map { list -> list.associateBy { it.ruleId ?: 0L } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** ruleId currently being tested; null when idle. Drives the button spinner. */
    private val _testing = MutableStateFlow<Long?>(null)
    val testing: StateFlow<Long?> = _testing.asStateFlow()

    /** Transient one-shot user messages (result of test, save confirmation, etc). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun upsert(rule: ForwardingRule) = viewModelScope.launch {
        dao.upsert(rule)
        _messages.emit(if (rule.id > 0) "Rule updated" else "Rule created")
    }

    fun delete(rule: ForwardingRule) = viewModelScope.launch {
        dao.delete(rule)
        _messages.emit("Deleted '${rule.name}'")
    }

    fun toggle(rule: ForwardingRule) = viewModelScope.launch {
        dao.update(rule.copy(enabled = !rule.enabled))
    }

    /**
     * Fire a synthetic webhook for this rule. Observes the resulting WorkInfo
     * so the caller can show a spinner until the worker finishes, then emits
     * a user-facing result message to [messages].
     */
    fun sendTest(rule: ForwardingRule) {
        if (_testing.value != null) return
        val wm = WorkManager.getInstance(getApplication())
        val now = System.currentTimeMillis()
        val data = Data.Builder()
            .putLong(WebhookWorker.KEY_RULE_ID, rule.id)
            .putString(WebhookWorker.KEY_SENDER, "[TEST]")
            .putString(WebhookWorker.KEY_BODY, "Test SMS from sms-forwarder-kt. OTP 123456.")
            .putInt(WebhookWorker.KEY_SIM, 1)
            .putLong(WebhookWorker.KEY_SENT_TS, now)
            .putLong(WebhookWorker.KEY_RECV_TS, now)
            .putInt(WebhookWorker.KEY_MAX_RETRIES, 1)
            .build()

        val req = OneTimeWorkRequestBuilder<WebhookWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(data)
            .addTag(TEST_TAG)
            .build()

        _testing.value = rule.id
        wm.enqueue(req)

        viewModelScope.launch {
            wm.getWorkInfoByIdFlow(req.id).collect { info ->
                if (info == null) return@collect
                if (info.state.isFinished) {
                    val msg = when (info.state) {
                        WorkInfo.State.SUCCEEDED -> "Test POST succeeded"
                        WorkInfo.State.FAILED -> "Test POST failed — check Events for details"
                        WorkInfo.State.CANCELLED -> "Test cancelled"
                        else -> "Test finished: ${info.state}"
                    }
                    _messages.emit(msg)
                    _testing.value = null
                    return@collect
                }
            }
        }
    }

    companion object {
        private const val TEST_TAG = "webhook-test"
    }
}
