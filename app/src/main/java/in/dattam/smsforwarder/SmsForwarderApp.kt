package `in`.dattam.smsforwarder

import android.app.Application
import `in`.dattam.smsforwarder.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            // Warm Room so the first SMS doesn't hit a cold DB under BroadcastReceiver.goAsync()'s 10s ceiling.
            val db = AppDatabase.get(this@SmsForwarderApp)
            db.openHelper.writableDatabase
            // Keep enough completed delivery IDs to deduplicate recent broadcasts without
            // allowing the ledger to grow forever on phones receiving thousands of SMS/day.
            db.webhookDeliveryDao().trimTerminal()
        }
    }
}
