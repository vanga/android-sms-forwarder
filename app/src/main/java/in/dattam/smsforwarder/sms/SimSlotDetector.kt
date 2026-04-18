package `in`.dattam.smsforwarder.sms

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Two-tier SIM-slot detection, 0-based slot. Returns -1 if undetected.
 *
 * 1. Modern path (Android 5+, requires READ_PHONE_STATE): pull subscriptionId
 *    from the intent extras and resolve via SubscriptionManager to a slot index.
 * 2. Legacy path: scan bundle extras for OEM-specific slot keys. Empirically
 *    collected from bogkonstantin/android_income_sms_gateway_webhook — different
 *    OEMs use different keys, this covers Xiaomi/Samsung/Oppo/Vivo/etc.
 */
object SimSlotDetector {

    private val LEGACY_KEYS = listOf(
        "phone", "slot", "simId", "simSlot", "slot_id",
        "simnum", "slotId", "slotIdx",
        "android.telephony.extra.SLOT_INDEX",
    )

    private val SUBSCRIPTION_KEYS = listOf(
        "subscription",
        "android.telephony.extra.SUBSCRIPTION_INDEX",
    )

    fun detect(context: Context, intent: Intent): Int {
        val bundle = intent.extras ?: return -1

        val modern = detectViaSubscriptionManager(context, bundle)
        if (modern >= 0) return modern

        return detectLegacy(bundle)
    }

    @SuppressLint("MissingPermission")
    private fun detectViaSubscriptionManager(context: Context, bundle: Bundle): Int {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return -1

        val subId = SUBSCRIPTION_KEYS.firstNotNullOfOrNull {
            if (bundle.containsKey(it)) bundle.getInt(it, -1).takeIf { v -> v >= 0 } else null
        } ?: return -1

        return try {
            val sm = context.getSystemService(SubscriptionManager::class.java) ?: return -1
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                sm.getActiveSubscriptionInfo(subId)?.simSlotIndex ?: -1
            } else {
                @Suppress("DEPRECATION")
                sm.activeSubscriptionInfoList?.firstOrNull { it.subscriptionId == subId }?.simSlotIndex ?: -1
            }
        } catch (_: SecurityException) {
            -1
        }
    }

    private fun detectLegacy(bundle: Bundle): Int {
        for (key in LEGACY_KEYS) {
            if (bundle.containsKey(key)) {
                val v = bundle.getInt(key, -1)
                if (v != -1) return v
            }
        }
        for (key in bundle.keySet()) {
            val k = key.lowercase()
            if ("slot" in k || "sim" in k) {
                val s = bundle.getString(key, "-1")
                if (s in setOf("0", "1", "2")) return s.toInt()
            }
        }
        return -1
    }
}
