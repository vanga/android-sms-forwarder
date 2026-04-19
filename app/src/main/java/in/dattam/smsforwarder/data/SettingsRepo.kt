package `in`.dattam.smsforwarder.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** App-wide defaults applied when a rule leaves the corresponding field blank. */
data class AppDefaults(
    val headersJson: String,
    val bodyTemplate: String,
) {
    companion object {
        const val DEFAULT_HEADERS_JSON = "{\n  \"X-SMS-Secret\": \"replace-with-your-shared-secret\"\n}"
    }
}

private val Context.dataStore by preferencesDataStore(name = "app_defaults")

class SettingsRepo(private val ctx: Context) {
    private object Keys {
        val HEADERS = stringPreferencesKey("default_headers_json")
        val BODY = stringPreferencesKey("default_body_template")
    }

    val flow: Flow<AppDefaults> = ctx.dataStore.data.map { p -> p.toDefaults() }

    suspend fun current(): AppDefaults = flow.first()

    suspend fun save(d: AppDefaults) {
        ctx.dataStore.edit { p ->
            p[Keys.HEADERS] = d.headersJson
            p[Keys.BODY] = d.bodyTemplate
        }
    }

    private fun Preferences.toDefaults() = AppDefaults(
        headersJson = this[Keys.HEADERS] ?: AppDefaults.DEFAULT_HEADERS_JSON,
        bodyTemplate = this[Keys.BODY] ?: "",
    )
}
