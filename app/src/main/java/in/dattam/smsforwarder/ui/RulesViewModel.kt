package `in`.dattam.smsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.data.ForwardingRule
import `in`.dattam.smsforwarder.webhook.WebhookWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class RulesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).ruleDao()
    val rules = dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun upsert(rule: ForwardingRule) = viewModelScope.launch { dao.upsert(rule) }
    fun delete(rule: ForwardingRule) = viewModelScope.launch { dao.delete(rule) }
    fun toggle(rule: ForwardingRule) = viewModelScope.launch {
        dao.update(rule.copy(enabled = !rule.enabled))
    }

    /**
     * Fire a synthetic webhook for this rule — bypasses sender matching by
     * enqueueing WebhookWorker directly with test data. Useful for confirming
     * URL + headers + body template reach the user's server before waiting on
     * a real SMS.
     */
    fun sendTest(rule: ForwardingRule) {
        val now = System.currentTimeMillis()
        val data = Data.Builder()
            .putLong(WebhookWorker.KEY_RULE_ID, rule.id)
            .putString(WebhookWorker.KEY_SENDER, "[TEST]")
            .putString(WebhookWorker.KEY_BODY, "Test SMS from sms-forwarder-kt. OTP 123456.")
            .putInt(WebhookWorker.KEY_SIM, 1)
            .putLong(WebhookWorker.KEY_SENT_TS, now)
            .putLong(WebhookWorker.KEY_RECV_TS, now)
            .putInt(WebhookWorker.KEY_MAX_RETRIES, rule.retries)
            .build()

        val req = OneTimeWorkRequestBuilder<WebhookWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(getApplication()).enqueue(req)
    }
}
