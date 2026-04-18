package `in`.dattam.smsforwarder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.matcher.SenderMatcher
import `in`.dattam.smsforwarder.webhook.WebhookWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val timestamp = messages[0].timestampMillis
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }

        val slotIdRaw = intent.extras?.let { SimSlotDetector.detect(it) } ?: -1
        val slotId = if (slotIdRaw < 0) 0 else slotIdRaw + 1

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rules = AppDatabase.get(context).ruleDao().allEnabled()
                for (rule in rules) {
                    if (rule.simSlot != 0 && rule.simSlot != slotId) continue
                    if (!SenderMatcher.matches(rule, sender, body)) continue
                    enqueue(context, rule.id, sender, body, slotId, timestamp, rule.retries)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "onReceive failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun enqueue(
        ctx: Context, ruleId: Long, sender: String, body: String,
        slot: Int, ts: Long, retries: Int,
    ) {
        val data = Data.Builder()
            .putLong(WebhookWorker.KEY_RULE_ID, ruleId)
            .putString(WebhookWorker.KEY_SENDER, sender)
            .putString(WebhookWorker.KEY_BODY, body)
            .putInt(WebhookWorker.KEY_SIM, slot)
            .putLong(WebhookWorker.KEY_SENT_TS, ts)
            .putLong(WebhookWorker.KEY_RECV_TS, System.currentTimeMillis())
            .putInt(WebhookWorker.KEY_MAX_RETRIES, retries)
            .build()

        val req = OneTimeWorkRequestBuilder<WebhookWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(ctx).enqueue(req)
    }

    companion object { private const val TAG = "SmsReceiver" }
}
