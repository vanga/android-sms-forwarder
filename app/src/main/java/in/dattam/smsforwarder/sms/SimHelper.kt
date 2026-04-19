package `in`.dattam.smsforwarder.sms

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Best-effort SIM MSISDN reader. Returns the first non-blank number found on
 * any active subscription, normalized to digits only and truncated to the
 * trailing 10 digits (India format). Returns null when:
 *   - READ_PHONE_STATE not granted,
 *   - No SIMs active, or
 *   - Carrier doesn't write MSISDN to the SIM (common on Jio/Airtel).
 *
 * Indian carriers frequently leave this field empty, so the UI must always
 * allow manual entry as the primary path.
 */
object SimHelper {

    @SuppressLint("MissingPermission", "HardwareIds")
    fun readMobileNumber(ctx: Context): String? {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val sm = ctx.getSystemService(SubscriptionManager::class.java) ?: return null
        return try {
            val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                sm.activeSubscriptionInfoList.orEmpty()
            } else emptyList()
            @Suppress("DEPRECATION") // SubscriptionInfo.number is deprecated on some APIs but
            // remains the simplest cross-version way to read MSISDN. Google's replacement
            // (getPhoneNumber) requires API 33+ AND extra runtime permission.
            list.asSequence()
                .mapNotNull { it.number?.takeIf(String::isNotBlank) }
                .map { it.filter(Char::isDigit) }
                .map { if (it.length > 10) it.takeLast(10) else it }
                .firstOrNull { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        }
    }
}
