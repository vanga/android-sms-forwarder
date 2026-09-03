package `in`.dattam.smsforwarder.sms

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.data.EventLog
import `in`.dattam.smsforwarder.data.EventLogDao
import `in`.dattam.smsforwarder.data.ForwardingRule
import `in`.dattam.smsforwarder.data.WebhookDelivery
import `in`.dattam.smsforwarder.matcher.SenderMatcher
import `in`.dattam.smsforwarder.webhook.WebhookSendResult
import `in`.dattam.smsforwarder.webhook.WebhookSender
import `in`.dattam.smsforwarder.webhook.WebhookWorker
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Matches and delivers one SMS in a single successful-path WorkManager job. */
class SmsDispatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val sentTs = inputData.getLong(KEY_SENT_TS, 0)
        val recvTs = inputData.getLong(KEY_RECV_TS, 0)
        val slot = inputData.getInt(KEY_SLOT, 0)
        val preview = body.take(120)
        val smsKey = smsKey(sender, body, slot, sentTs)
        val db = AppDatabase.get(applicationContext)
        val events = db.eventLogDao()

        return try {
            events.log(EventLog(stage = "RECEIVED", sender = sender, bodyPreview = preview))
            val rules = db.ruleDao().allEnabled()
            val matchingRules = rules.filter { rule ->
                (slot == 0 || rule.simSlot == 0 || rule.simSlot == slot) &&
                    SenderMatcher.matches(rule, sender, body)
            }

            if (matchingRules.isEmpty()) {
                events.log(
                    EventLog(
                        stage = "SKIPPED",
                        sender = sender,
                        bodyPreview = preview,
                        detail = if (rules.isEmpty()) "no enabled rules"
                        else "no rule matched (${rules.size} enabled rules checked)",
                    )
                )
                return Result.success()
            }

            var waitingForLease = false
            for (rule in matchingRules) {
                val delivery = findOrCreateDelivery(db, smsKey, rule, sender, body, slot, sentTs, recvTs)
                if (delivery.state in TERMINAL_STATES) continue
                // A retry worker owns RETRYING deliveries. A duplicate SMS broadcast must not
                // bypass its backoff by sending the same delivery from this worker.
                if (delivery.state == WebhookDelivery.STATE_RETRYING) continue

                val now = System.currentTimeMillis()
                if (db.webhookDeliveryDao().claimInitial(delivery.id, now, now - LEASE_MS) == 0) {
                    waitingForLease = true
                    continue
                }
                val claimed = db.webhookDeliveryDao().byId(delivery.id) ?: continue
                Log.i(TAG, "MATCHED rule='${rule.name}' id=${rule.id} attempt=${claimed.attemptCount}")
                events.log(
                    EventLog(
                        stage = "MATCHED",
                        sender = sender,
                        bodyPreview = preview,
                        ruleId = rule.id,
                        ruleName = rule.name,
                        attempt = claimed.attemptCount,
                    )
                )
                deliver(db, events, claimed, rule)
            }

            if (waitingForLease) Result.retry() else Result.success()
        } catch (error: Throwable) {
            Log.e(TAG, "dispatch failed", error)
            runCatching {
                events.log(EventLog(stage = "FAILED", sender = sender, bodyPreview = preview, detail = error.message))
            }
            Result.retry()
        }
    }

    private suspend fun findOrCreateDelivery(
        db: AppDatabase,
        smsKey: String,
        rule: ForwardingRule,
        sender: String,
        body: String,
        slot: Int,
        sentTs: Long,
        recvTs: Long,
    ): WebhookDelivery {
        val candidate = WebhookDelivery(
            id = UUID.randomUUID().toString(),
            smsKey = smsKey,
            ruleId = rule.id,
            ruleName = rule.name,
            sender = sender,
            body = body,
            sim = slot,
            sentTs = sentTs,
            recvTs = recvTs,
            maxAttempts = rule.retries.coerceAtLeast(1),
        )
        db.webhookDeliveryDao().insert(candidate)
        return db.webhookDeliveryDao().bySmsAndRule(smsKey, rule.id) ?: candidate
    }

    private suspend fun deliver(
        db: AppDatabase,
        events: EventLogDao,
        delivery: WebhookDelivery,
        rule: ForwardingRule,
    ) {
        val preview = delivery.body.take(120)
        val outcome = runCatching {
            WebhookSender(applicationContext).send(
                rule,
                delivery.sender,
                delivery.body,
                delivery.sim,
                delivery.sentTs,
                delivery.recvTs,
                delivery.id,
            )
        }.getOrElse { WebhookSendResult.Retryable(it.message ?: "send-error", null) }

        when (outcome) {
            is WebhookSendResult.Posted -> {
                db.webhookDeliveryDao().markTerminal(
                    delivery.id,
                    WebhookDelivery.STATE_POSTED,
                    outcome.httpCode,
                    null,
                    System.currentTimeMillis(),
                )
                events.log(
                    EventLog(
                        stage = "POSTED",
                        sender = delivery.sender,
                        bodyPreview = preview,
                        ruleId = delivery.ruleId,
                        ruleName = delivery.ruleName,
                        httpCode = outcome.httpCode,
                        attempt = delivery.attemptCount,
                    )
                )
            }
            is WebhookSendResult.PermanentFailure ->
                markFailed(db, events, delivery, preview, outcome.detail, outcome.httpCode)
            is WebhookSendResult.Retryable -> {
                if (delivery.attemptCount >= delivery.maxAttempts) {
                    markFailed(db, events, delivery, preview, outcome.detail, outcome.httpCode)
                } else {
                    // Enqueue while the row is still leased. If enqueueing fails, this worker
                    // retries and can reclaim the stale lease instead of stranding the delivery.
                    enqueueRetry(delivery.id)
                    db.webhookDeliveryDao().markRetrying(
                        delivery.id,
                        outcome.httpCode,
                        outcome.detail,
                        System.currentTimeMillis(),
                    )
                    events.log(
                        EventLog(
                            stage = "RETRYING",
                            sender = delivery.sender,
                            bodyPreview = preview,
                            ruleId = delivery.ruleId,
                            ruleName = delivery.ruleName,
                            httpCode = outcome.httpCode,
                            attempt = delivery.attemptCount,
                            detail = outcome.detail,
                        )
                    )
                }
            }
        }
    }

    private suspend fun markFailed(
        db: AppDatabase,
        events: EventLogDao,
        delivery: WebhookDelivery,
        preview: String,
        detail: String,
        httpCode: Int?,
    ) {
        db.webhookDeliveryDao().markTerminal(
            delivery.id,
            WebhookDelivery.STATE_FAILED,
            httpCode,
            detail,
            System.currentTimeMillis(),
        )
        events.log(
            EventLog(
                stage = "FAILED",
                sender = delivery.sender,
                bodyPreview = preview,
                ruleId = delivery.ruleId,
                ruleName = delivery.ruleName,
                httpCode = httpCode,
                attempt = delivery.attemptCount,
                detail = detail,
            )
        )
    }

    private fun enqueueRetry(deliveryId: String) {
        val data = Data.Builder().putString(WebhookWorker.KEY_DELIVERY_ID, deliveryId).build()
        val request = OneTimeWorkRequestBuilder<WebhookWorker>()
            .addTag(WebhookWorker.TAG_RETRY)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "webhook-delivery-$deliveryId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun smsKey(sender: String, body: String, slot: Int, sentTs: Long): String {
        val bytes = "$sender\u0000$slot\u0000$sentTs\u0000$body".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "SmsDispatchWorker"
        private const val LEASE_MS = 2 * 60 * 1000L
        private val TERMINAL_STATES = setOf(
            WebhookDelivery.STATE_POSTED,
            WebhookDelivery.STATE_FAILED,
            WebhookDelivery.STATE_SKIPPED,
        )
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_SLOT = "slot"
        const val KEY_SENT_TS = "sentTs"
        const val KEY_RECV_TS = "recvTs"
        const val TAG_DISPATCH = "sms-dispatch"
    }
}
