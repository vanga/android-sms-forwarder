package `in`.dattam.smsforwarder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * Very thin SMS receiver. Its only job is to:
 *   1. Parse PDUs into sender + body + timestamp + SIM slot.
 *   2. Enqueue a SmsDispatchWorker with that data.
 *
 * All DB work, rule matching, and normal webhook delivery happen in the
 * dispatch worker. This keeps the receiver synchronous and well under the
 * ~10 s BroadcastReceiver deadline even on a cold-started process with an
 * uninitialized Room database on slow storage.
 *
 * Expedited WorkManager is requested so the dispatch runs ASAP (OTPs are
 * time-sensitive). If the device is out of expedited quota the job falls back
 * to a normal request, which still runs within seconds on a foregrounded app.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val timestamp = messages[0].timestampMillis
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }

        val slotIdRaw = SimSlotDetector.detect(context, intent)
        val slotId = if (slotIdRaw < 0) 0 else slotIdRaw + 1

        Log.i(TAG, "RECEIVED from=$sender slot=$slotId bodyLen=${body.length}")

        try {
            val data = Data.Builder()
                .putString(SmsDispatchWorker.KEY_SENDER, sender)
                .putString(SmsDispatchWorker.KEY_BODY, body)
                .putInt(SmsDispatchWorker.KEY_SLOT, slotId)
                .putLong(SmsDispatchWorker.KEY_SENT_TS, timestamp)
                .putLong(SmsDispatchWorker.KEY_RECV_TS, System.currentTimeMillis())
                .build()

            val req = OneTimeWorkRequestBuilder<SmsDispatchWorker>()
                .addTag(SmsDispatchWorker.TAG_DISPATCH)
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueue(req)
        } catch (t: Throwable) {
            // Data.Builder can throw if the serialized payload exceeds
            // WorkManager's 10 KB cap (happens on concatenated multi-part
            // SMS > ~5 segments). Fall back to an in-DB store is tracked as
            // a separate review item; for now we don't want a crash.
            Log.e(TAG, "failed to enqueue dispatch for $sender", t)
        }
    }

    companion object { private const val TAG = "SmsReceiver" }
}
