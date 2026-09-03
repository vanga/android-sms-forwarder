package `in`.dattam.smsforwarder.webhook

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.data.EventLog
import `in`.dattam.smsforwarder.data.WebhookDelivery

/** Retries one failed delivery. Successful SMS deliveries never schedule this worker. */
class WebhookWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val deliveryId = inputData.getString(KEY_DELIVERY_ID)
        return if (deliveryId == null) deliverLegacyTest() else retryDelivery(deliveryId)
    }

    private suspend fun retryDelivery(deliveryId: String): Result {
        val db = AppDatabase.get(applicationContext)
        val deliveries = db.webhookDeliveryDao()
        val delivery = deliveries.byId(deliveryId) ?: return Result.success()
        if (delivery.state in TERMINAL_STATES) return Result.success()

        val now = System.currentTimeMillis()
        if (deliveries.claimRetry(deliveryId, now, now - LEASE_MS) == 0) return Result.retry()
        val claimed = deliveries.byId(deliveryId) ?: return Result.success()
        val rule = db.ruleDao().byId(claimed.ruleId)
        if (rule == null || !rule.enabled) {
            val detail = if (rule == null) "rule deleted" else "rule disabled"
            deliveries.markTerminal(
                deliveryId,
                WebhookDelivery.STATE_SKIPPED,
                null,
                detail,
                System.currentTimeMillis(),
            )
            db.eventLogDao().log(
                EventLog(
                    stage = "SKIPPED",
                    sender = claimed.sender,
                    bodyPreview = claimed.body.take(120),
                    ruleId = claimed.ruleId,
                    ruleName = claimed.ruleName,
                    attempt = claimed.attemptCount,
                    detail = detail,
                )
            )
            return Result.success()
        }

        val outcome = runCatching {
            WebhookSender(applicationContext).send(
                rule,
                claimed.sender,
                claimed.body,
                claimed.sim,
                claimed.sentTs,
                claimed.recvTs,
                claimed.id,
            )
        }.getOrElse { WebhookSendResult.Retryable(it.message ?: "send-error", null) }
        return persistOutcome(db, claimed, outcome)
    }

    private suspend fun persistOutcome(
        db: AppDatabase,
        delivery: WebhookDelivery,
        outcome: WebhookSendResult,
    ): Result {
        val preview = delivery.body.take(120)
        return when (outcome) {
            is WebhookSendResult.Posted -> {
                db.webhookDeliveryDao().markTerminal(
                    delivery.id,
                    WebhookDelivery.STATE_POSTED,
                    outcome.httpCode,
                    null,
                    System.currentTimeMillis(),
                )
                db.eventLogDao().log(
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
                Result.success()
            }
            is WebhookSendResult.PermanentFailure -> {
                terminalFailure(db, delivery, preview, outcome.detail, outcome.httpCode)
                Result.failure()
            }
            is WebhookSendResult.Retryable -> {
                if (delivery.attemptCount >= delivery.maxAttempts) {
                    terminalFailure(db, delivery, preview, outcome.detail, outcome.httpCode)
                    Result.failure()
                } else {
                    db.webhookDeliveryDao().markRetrying(
                        delivery.id,
                        outcome.httpCode,
                        outcome.detail,
                        System.currentTimeMillis(),
                    )
                    db.eventLogDao().log(
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
                    Result.retry()
                }
            }
        }
    }

    private suspend fun terminalFailure(
        db: AppDatabase,
        delivery: WebhookDelivery,
        preview: String,
        detail: String,
        httpCode: Int?,
    ) {
        Log.w(TAG, "delivery ${delivery.id} failed: $detail")
        db.webhookDeliveryDao().markTerminal(
            delivery.id,
            WebhookDelivery.STATE_FAILED,
            httpCode,
            detail,
            System.currentTimeMillis(),
        )
        db.eventLogDao().log(
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

    /** Keeps the existing rule-test button independent of durable SMS delivery state. */
    private suspend fun deliverLegacyTest(): Result {
        val ruleId = inputData.getLong(KEY_RULE_ID, -1)
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val sim = inputData.getInt(KEY_SIM, 0)
        val sentTs = inputData.getLong(KEY_SENT_TS, 0)
        val recvTs = inputData.getLong(KEY_RECV_TS, 0)
        val maxRetries = inputData.getInt(KEY_MAX_RETRIES, 1).coerceAtLeast(1)
        val attempt = runAttemptCount + 1
        val db = AppDatabase.get(applicationContext)
        val rule = db.ruleDao().byId(ruleId) ?: return Result.failure()
        val outcome = WebhookSender(applicationContext).send(
            rule,
            sender,
            body,
            sim,
            sentTs,
            recvTs,
            "test-$id",
        )
        return when (outcome) {
            is WebhookSendResult.Posted -> {
                db.eventLogDao().log(
                    EventLog(
                        stage = "POSTED",
                        sender = sender,
                        bodyPreview = body.take(120),
                        ruleId = rule.id,
                        ruleName = rule.name,
                        httpCode = outcome.httpCode,
                        attempt = attempt,
                    )
                )
                Result.success()
            }
            is WebhookSendResult.PermanentFailure -> {
                logLegacyFailure(db, rule.id, rule.name, sender, body, attempt, outcome.detail, outcome.httpCode)
                Result.failure()
            }
            is WebhookSendResult.Retryable -> {
                val willRetry = attempt < maxRetries
                db.eventLogDao().log(
                    EventLog(
                        stage = if (willRetry) "RETRYING" else "FAILED",
                        sender = sender,
                        bodyPreview = body.take(120),
                        ruleId = rule.id,
                        ruleName = rule.name,
                        httpCode = outcome.httpCode,
                        attempt = attempt,
                        detail = outcome.detail,
                    )
                )
                if (willRetry) Result.retry() else Result.failure()
            }
        }
    }

    private suspend fun logLegacyFailure(
        db: AppDatabase,
        ruleId: Long,
        ruleName: String,
        sender: String,
        body: String,
        attempt: Int,
        detail: String,
        httpCode: Int?,
    ) {
        db.eventLogDao().log(
            EventLog(
                stage = "FAILED",
                sender = sender,
                bodyPreview = body.take(120),
                ruleId = ruleId,
                ruleName = ruleName,
                httpCode = httpCode,
                attempt = attempt,
                detail = detail,
            )
        )
    }

    companion object {
        private const val TAG = "WebhookWorker"
        private const val LEASE_MS = 2 * 60 * 1000L
        private val TERMINAL_STATES = setOf(
            WebhookDelivery.STATE_POSTED,
            WebhookDelivery.STATE_FAILED,
            WebhookDelivery.STATE_SKIPPED,
        )
        const val KEY_DELIVERY_ID = "deliveryId"
        const val KEY_RULE_ID = "ruleId"
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_SIM = "sim"
        const val KEY_SENT_TS = "sentTs"
        const val KEY_RECV_TS = "recvTs"
        const val KEY_MAX_RETRIES = "maxRetries"
        const val TAG_RETRY = "webhook-retry"
    }
}
