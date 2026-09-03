package `in`.dattam.smsforwarder.webhook

import android.content.Context
import `in`.dattam.smsforwarder.data.ForwardingRule
import `in`.dattam.smsforwarder.data.SettingsRepo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

sealed interface WebhookSendResult {
    data class Posted(val httpCode: Int) : WebhookSendResult
    data class Retryable(val detail: String, val httpCode: Int?) : WebhookSendResult
    data class PermanentFailure(val detail: String, val httpCode: Int?) : WebhookSendResult
}

class WebhookSender(private val context: Context) {
    suspend fun send(
        rule: ForwardingRule,
        sender: String,
        body: String,
        sim: Int,
        sentTs: Long,
        recvTs: Long,
        idempotencyKey: String,
    ): WebhookSendResult {
        val defaults = SettingsRepo(context).current()
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

        val request = try {
            Request.Builder()
                .url(rule.url)
                .post(payload.toRequestBody(JSON_MEDIA))
                .apply {
                    mergeHeaders(defaults.headersJson, rule.headersJson)
                        .forEach { (name, value) -> addHeader(name, headerValue(value)) }
                    header("Idempotency-Key", idempotencyKey)
                }
                .build()
        } catch (error: IllegalArgumentException) {
            return WebhookSendResult.PermanentFailure("invalid request: ${error.message}", null)
        }

        return withContext(Dispatchers.IO) {
            try {
                httpClient(rule.ignoreSsl).newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> WebhookSendResult.Posted(response.code)
                        response.code in 500..599 || response.code == 408 || response.code == 429 ->
                            WebhookSendResult.Retryable(
                                httpFailureDetail(response.code, response.body.string()),
                                response.code,
                            )
                        else -> WebhookSendResult.PermanentFailure(
                            "${httpFailureDetail(response.code, response.body.string())} " +
                                "(client error, not retried)",
                            response.code,
                        )
                    }
                }
            } catch (error: IOException) {
                WebhookSendResult.Retryable(error.message ?: "io-error", null)
            }
        }
    }

    private fun headerValue(value: kotlinx.serialization.json.JsonElement): String =
        (value as? JsonPrimitive)?.content ?: value.toString()

    private fun mergeHeaders(defaultsJson: String, ruleJson: String): JsonObject {
        val defaults = runCatching { Json.decodeFromString<JsonObject>(defaultsJson) }
            .getOrDefault(JsonObject(emptyMap()))
        val rule = runCatching { Json.decodeFromString<JsonObject>(ruleJson) }
            .getOrDefault(JsonObject(emptyMap()))
        val ruleKeys = rule.keys.map { it.lowercase() }.toSet()
        return JsonObject(defaults.filterKeys { it.lowercase() !in ruleKeys } + rule)
    }

    private fun substitute(
        template: String,
        from: String,
        text: String,
        sentTs: Long,
        recvTs: Long,
        sim: String,
        mobile: String,
    ): String {
        val values = mapOf(
            "from" to jsonEscape(from),
            "text" to jsonEscape(text),
            "sentStamp" to sentTs.toString(),
            "receivedStamp" to recvTs.toString(),
            "sim" to sim,
            "mobile" to jsonEscape(mobile),
        )
        return PLACEHOLDER.replace(template) { match -> values[match.groupValues[1]] ?: match.value }
    }

    private fun jsonEscape(value: String): String =
        Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value)).removeSurrounding("\"")

    private fun httpFailureDetail(code: Int, body: String): String {
        val responseBody = body.trim()
        return if (responseBody.isEmpty()) "HTTP $code"
        else "HTTP $code: ${responseBody.take(MAX_ERROR_BODY_CHARS)}"
    }

    private fun httpClient(ignoreSsl: Boolean): OkHttpClient =
        if (ignoreSsl) trustingClient else secureClient

    companion object {
        private const val MAX_ERROR_BODY_CHARS = 500
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val PLACEHOLDER = Regex("""%(from|text|sentStamp|receivedStamp|sim|mobile)%""")

        private val secureClient: OkHttpClient by lazy { baseClient().build() }

        private val trustingClient: OkHttpClient by lazy {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), java.security.SecureRandom())
            }
            baseClient()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier(HostnameVerifier { _, _ -> true })
                .build()
        }

        private fun baseClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
    }
}
