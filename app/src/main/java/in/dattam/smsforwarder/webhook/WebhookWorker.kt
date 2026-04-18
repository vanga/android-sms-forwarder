package `in`.dattam.smsforwarder.webhook

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.data.EventLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

        val rule = db.ruleDao().allEnabled().firstOrNull { it.id == ruleId } ?: run {
            Log.w(TAG, "rule $ruleId no longer exists; dropping")
            events.log(EventLog(stage = "FAILED", sender = sender, bodyPreview = preview,
                ruleId = ruleId, attempt = attempt, detail = "rule deleted"))
            return Result.failure()
        }

        Log.i(TAG, "POST rule='${rule.name}' url=${rule.url} attempt=$attempt/$maxRetries")
        events.log(EventLog(stage = "ENQUEUED", sender = sender, bodyPreview = preview,
            ruleId = rule.id, ruleName = rule.name, attempt = attempt))

        val payload = buildJsonObject {
            put("from", sender)
            put("text", body)
            put("sentStamp", sentTs)
            put("receivedStamp", recvTs)
            put("sim", if (sim > 0) "sim$sim" else "undetected")
        }

        val headers = runCatching { Json.decodeFromString<JsonObject>(rule.headersJson) }
            .getOrDefault(JsonObject(emptyMap()))

        return try {
            val client = buildClient(rule.ignoreSsl)
            val reqBuilder = Request.Builder()
                .url(rule.url)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v.toString().trim('"')) }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    Log.i(TAG, "POSTED ${resp.code} rule='${rule.name}'")
                    events.log(EventLog(stage = "POSTED", sender = sender, bodyPreview = preview,
                        ruleId = rule.id, ruleName = rule.name, httpCode = resp.code, attempt = attempt))
                    Result.success()
                } else {
                    retryOrFail(events, rule.id, rule.name, sender, preview,
                        maxRetries, attempt, "HTTP ${resp.code}", resp.code)
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
        val stage = if (attempt < max) "ENQUEUED" else "FAILED"
        events.log(EventLog(stage = stage, sender = sender, bodyPreview = preview,
            ruleId = ruleId, ruleName = ruleName, attempt = attempt,
            httpCode = httpCode, detail = reason))
        return if (attempt < max) Result.retry() else Result.failure()
    }

    private fun buildClient(ignoreSsl: Boolean): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        if (ignoreSsl) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }
            val sc = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), java.security.SecureRandom()) }
            b.sslSocketFactory(sc.socketFactory, tm)
            b.hostnameVerifier(HostnameVerifier { _, _ -> true })
        }
        return b.build()
    }

    companion object {
        private const val TAG = "WebhookWorker"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val KEY_RULE_ID = "ruleId"
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_SIM = "sim"
        const val KEY_SENT_TS = "sentTs"
        const val KEY_RECV_TS = "recvTs"
        const val KEY_MAX_RETRIES = "maxRetries"
    }
}
