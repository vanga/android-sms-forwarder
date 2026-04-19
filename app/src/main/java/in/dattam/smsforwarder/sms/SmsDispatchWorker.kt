package `in`.dattam.smsforwarder.sms

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.data.EventLog
import `in`.dattam.smsforwarder.matcher.SenderMatcher
import `in`.dattam.smsforwarder.webhook.WebhookWorker
import java.util.concurrent.TimeUnit

/**
 * Runs rule matching + event logging + per-match WebhookWorker enqueue inside a
 * WorkManager job instead of the BroadcastReceiver. This moves the DB work off
 * the receiver's ~10-second `goAsync()` budget (which is tight on cold DB and
 * on OEM devices with slow eMMC) into a worker that has a 10-minute budget.
 */
class SmsDispatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val sentTs = inputData.getLong(KEY_SENT_TS, 0)
        val recvTs = inputData.getLong(KEY_RECV_TS, 0)
        val slotId = inputData.getInt(KEY_SLOT, 0)
        val preview = body.take(120)

        val db = AppDatabase.get(applicationContext)
        val events = db.eventLogDao()

        return try {
            events.log(EventLog(stage = "RECEIVED", sender = sender, bodyPreview = preview))

            val rules = db.ruleDao().allEnabled()
            if (rules.isEmpty()) {
                events.log(EventLog(stage = "SKIPPED", sender = sender, bodyPreview = preview,
                    detail = "no enabled rules"))
                return Result.success()
            }

            var matched = 0
            val slotUnknown = slotId == 0
            for (rule in rules) {
                // slotId 0 means detection failed (undetected). Don't use it to exclude
                // SIM-scoped rules — a failed detection shouldn't silently drop every
                // OTP on devices with OEM bundle-key variants we don't recognise.
                if (!slotUnknown && rule.simSlot != 0 && rule.simSlot != slotId) {
                    Log.d(TAG, "rule '${rule.name}' skipped: sim ${rule.simSlot} != $slotId")
                    continue
                }
                if (!SenderMatcher.matches(rule, sender, body)) {
                    Log.d(TAG, "rule '${rule.name}' skipped: pattern no match")
                    continue
                }
                matched++
                Log.i(TAG, "MATCHED rule='${rule.name}' id=${rule.id} -> enqueue webhook")
                events.log(EventLog(stage = "MATCHED", sender = sender, bodyPreview = preview,
                    ruleId = rule.id, ruleName = rule.name))
                enqueueWebhook(applicationContext, rule.id, sender, body, slotId, sentTs, recvTs, rule.retries)
            }

            if (matched == 0) {
                events.log(EventLog(stage = "SKIPPED", sender = sender, bodyPreview = preview,
                    detail = "no rule matched (${rules.size} enabled rules checked)"))
            }
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "dispatch failed", t)
            runCatching {
                events.log(EventLog(stage = "FAILED", sender = sender, bodyPreview = preview,
                    detail = t.message))
            }
            Result.failure()
        }
    }

    private fun enqueueWebhook(
        ctx: Context, ruleId: Long, sender: String, body: String,
        slot: Int, sentTs: Long, recvTs: Long, retries: Int,
    ) {
        val data = Data.Builder()
            .putLong(WebhookWorker.KEY_RULE_ID, ruleId)
            .putString(WebhookWorker.KEY_SENDER, sender)
            .putString(WebhookWorker.KEY_BODY, body)
            .putInt(WebhookWorker.KEY_SIM, slot)
            .putLong(WebhookWorker.KEY_SENT_TS, sentTs)
            .putLong(WebhookWorker.KEY_RECV_TS, recvTs)
            .putInt(WebhookWorker.KEY_MAX_RETRIES, retries)
            .build()

        val req = OneTimeWorkRequestBuilder<WebhookWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(ctx).enqueue(req)
    }

    companion object {
        private const val TAG = "SmsDispatchWorker"
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_SLOT = "slot"
        const val KEY_SENT_TS = "sentTs"
        const val KEY_RECV_TS = "recvTs"
    }
}
