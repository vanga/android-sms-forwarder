package `in`.dattam.smsforwarder.sms

import android.os.Bundle

/**
 * Port of bogkonstantin/android_income_sms_gateway_webhook detectSim().
 * Real-device empirical: different OEMs use different bundle keys.
 * Returns 0-based slot, or -1 if undetected.
 */
object SimSlotDetector {
    private val KNOWN_KEYS = listOf(
        "phone", "slot", "simId", "simSlot", "slot_id",
        "simnum", "slotId", "slotIdx",
        "android.telephony.extra.SLOT_INDEX",
    )

    fun detect(bundle: Bundle): Int {
        for (key in KNOWN_KEYS) {
            if (bundle.containsKey(key)) {
                val v = bundle.getInt(key, -1)
                if (v != -1) return v
            }
        }
        // Fallback: fuzzy scan for *slot*/*sim* keys with 0/1/2 values.
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
