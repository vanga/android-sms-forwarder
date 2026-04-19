package `in`.dattam.smsforwarder.webhook

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.data.EventLog
import `in`.dattam.smsforwarder.data.SettingsRepo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class WebhookWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ruleId = inputData.getLong(KEY_RULE_ID, -1)
        val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val preview = body.take(120)
        val sim = inputData.getInt(KEY_SIM, 0)
        val sentTs = inputData.getLong(KEY_SENT_TS, 0)
        val recvTs = inputData.getLong(KEY_RECV_TS, 0)
        val maxRetries = inputData.getInt(KEY_MAX_RETRIES, 10)
        val attempt = runAttemptCount + 1

        val db = AppDatabase.get(applicationContext)
        val events = db.eventLogDao()

        val rule = db.ruleDao().byId(ruleId) ?: run {
            Log.w(TAG, "rule $ruleId no longer exists; dropping")
            events.log(EventLog(stage = "FAILED", sender = sender, bodyPreview = preview,
                ruleId = ruleId, attempt = attempt, detail = "rule deleted"))
            return Result.failure()
        }
        if (!rule.enabled) {
            Log.i(TAG, "rule ${rule.id} disabled; skipping in-flight delivery")
            events.log(EventLog(stage = "SKIPPED", sender = sender, bodyPreview = preview,
                ruleId = rule.id, ruleName = rule.name, detail = "rule disabled"))
            return Result.success()
        }

        Log.i(TAG, "POST rule='${rule.name}' url=${rule.url} attempt=$attempt/$maxRetries")
        events.log(EventLog(stage = "ENQUEUED", sender = sender, bodyPreview = preview,
            ruleId = rule.id, ruleName = rule.name, attempt = attempt))

        val defaults = SettingsRepo(applicationContext).current()
        val simLabel = if (sim > 0) "sim$sim" else "undetected"

        val effectiveTemplate = rule.bodyTemplate?.takeIf { it.isNotBlank() }
            ?: defaults.bodyTemplate.takeIf { it.isNotBlank() }
        val payload = effectiveTemplate
            ?.let { substitute(it, sender, body, sentTs, recvTs, simLabel, defaults.mobileNumber) }
            ?: buildJsonObject {
                put("from", sender)
                put("text", body)
                put("sentStamp", sentTs)
                put("receivedStamp", recvTs)
                put("sim", simLabel)
            }.toString()

        val mergedHeaders = mergeHeaders(defaults.headersJson, rule.headersJson)

        val request = try {
            val reqBuilder = Request.Builder()
                .url(rule.url)
                .post(payload.toRequestBody(JSON_MEDIA))
            mergedHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, headerValue(v)) }
            reqBuilder.build()
        } catch (t: IllegalArgumentException) {
            // Malformed URL, invalid header char, etc. Deterministic — no point retrying.
            Log.e(TAG, "invalid request for rule ${rule.id}: ${t.message}")
            events.log(EventLog(stage = "FAILED", sender = sender, bodyPreview = preview,
                ruleId = rule.id, ruleName = rule.name, attempt = attempt,
                detail = "invalid request: ${t.message}"))
            return Result.failure()
        }

        return try {
            httpClient(rule.ignoreSsl).newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> {
                        Log.i(TAG, "POSTED ${resp.code} rule='${rule.name}'")
                        events.log(EventLog(stage = "POSTED", sender = sender, bodyPreview = preview,
                            ruleId = rule.id, ruleName = rule.name, httpCode = resp.code, attempt = attempt))
                        Result.success()
                    }
                    // Retry only on 5xx + 408 (request timeout) + 429 (rate limited).
                    // 4xx otherwise is a deterministic client/config error; retrying wastes
                    // attempts and drowns the event log during OTP's short validity window.
                    resp.code in 500..599 || resp.code == 408 || resp.code == 429 ->
                        retryOrFail(events, rule.id, rule.name, sender, preview,
                            maxRetries, attempt, "HTTP ${resp.code}", resp.code)
                    else -> {
                        Log.w(TAG, "FAIL HTTP ${resp.code} (no-retry) rule=${rule.name}")
                        events.log(EventLog(stage = "FAILED", sender = sender, bodyPreview = preview,
                            ruleId = rule.id, ruleName = rule.name, httpCode = resp.code, attempt = attempt,
                            detail = "HTTP ${resp.code} (client error, not retried)"))
                        Result.failure()
                    }
                }
            }
        } catch (e: IOException) {
            retryOrFail(events, rule.id, rule.name, sender, preview,
                maxRetries, attempt, e.message ?: "io-error", null)
        }
    }

    private suspend fun retryOrFail(
        events: `in`.dattam.smsforwarder.data.EventLogDao,
        ruleId: Long, ruleName: String, sender: String, preview: String,
        max: Int, attempt: Int, reason: String, httpCode: Int?,
    ): Result {
        Log.w(TAG, "FAIL $reason attempt=$attempt/$max rule=$ruleName")
        val willRetry = attempt < max
        val stage = if (willRetry) "RETRYING" else "FAILED"
        events.log(EventLog(stage = stage, sender = sender, bodyPreview = preview,
            ruleId = ruleId, ruleName = ruleName, attempt = attempt,
            httpCode = httpCode, detail = reason))
        return if (willRetry) Result.retry() else Result.failure()
    }

    /** Coerce a JsonElement header value to a plain string suitable for HTTP headers. */
    private fun headerValue(v: kotlinx.serialization.json.JsonElement): String =
        (v as? JsonPrimitive)?.content ?: v.toString()

    private fun httpClient(ignoreSsl: Boolean): OkHttpClient =
        if (ignoreSsl) trustingClient else secureClient

    /**
     * Rule headers override app defaults on key collision — CASE-INSENSITIVELY,
     * since HTTP header names are case-insensitive (RFC 7230). Without this,
     * a default 'X-Api-Key' and a rule 'X-API-Key' would both be sent on the
     * wire, and the server reads whichever it finds first — typically the
     * default, which often still holds the placeholder 'replace-me' value.
     */
    private fun mergeHeaders(defaultsJson: String, ruleJson: String): JsonObject {
        val defaults = runCatching { Json.decodeFromString<JsonObject>(defaultsJson) }
            .getOrDefault(JsonObject(emptyMap()))
        val rule = runCatching { Json.decodeFromString<JsonObject>(ruleJson) }
            .getOrDefault(JsonObject(emptyMap()))
        val ruleKeysLower = rule.keys.map { it.lowercase() }.toSet()
        val survivingDefaults = defaults.filterKeys { it.lowercase() !in ruleKeysLower }
        return JsonObject(survivingDefaults + rule)
    }

    /**
     * Single-pass placeholder substitution. Scans the template once with a
     * regex and resolves each match via lookup. Substituted content is never
     * re-scanned, so an SMS body or sender that happens to contain a literal
     * `%from%` / `%text%` / `%sim%` cannot be mistaken for a placeholder by
     * a later substitution step.
     *
     * Values for %from% and %text% are JSON-escaped so embedded quotes,
     * backslashes, and control chars don't break the JSON envelope when the
     * placeholder sits inside a quoted JSON string. Timestamps are emitted
     * as raw numeric literals (suitable for `"ts": %sentStamp%`).
     */
    private fun substitute(
        template: String, from: String, text: String, sentTs: Long, recvTs: Long,
        sim: String, mobile: String,
    ): String {
        val values = mapOf(
            "from" to jsonEscape(from),
            "text" to jsonEscape(text),
            "sentStamp" to sentTs.toString(),
            "receivedStamp" to recvTs.toString(),
            "sim" to sim,
            "mobile" to jsonEscape(mobile),
        )
        return PLACEHOLDER.replace(template) { match ->
            values[match.groupValues[1]] ?: match.value
        }
    }

    private fun jsonEscape(value: String): String =
        Json.encodeToString(
            kotlinx.serialization.json.JsonPrimitive.serializer(),
            kotlinx.serialization.json.JsonPrimitive(value),
        ).removeSurrounding("\"")

    companion object {
        private const val TAG = "WebhookWorker"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val PLACEHOLDER = Regex("""%(from|text|sentStamp|receivedStamp|sim|mobile)%""")
        const val KEY_RULE_ID = "ruleId"
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_SIM = "sim"
        const val KEY_SENT_TS = "sentTs"
        const val KEY_RECV_TS = "recvTs"
        const val KEY_MAX_RETRIES = "maxRetries"

        /**
         * Shared OkHttpClient for standard TLS. Singleton so retries and
         * concurrent workers reuse one connection pool + dispatcher instead
         * of allocating a fresh thread pool per invocation.
         *
         * callTimeout is a hard ceiling covering connect + write + read so a
         * slow server can never hang the worker past this bound.
         */
        private val secureClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Singleton client used ONLY when a rule opts into ignoreSsl. Trust
         * is fully nullified — per-rule opt-in acknowledged as a deliberate
         * bypass for internal/self-signed endpoints.
         */
        private val trustingClient: OkHttpClient by lazy {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }
            val sc = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), java.security.SecureRandom()) }
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .sslSocketFactory(sc.socketFactory, tm)
                .hostnameVerifier(HostnameVerifier { _, _ -> true })
                .build()
        }
    }
}
