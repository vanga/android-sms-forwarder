package `in`.dattam.smsforwarder

import android.app.Application
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.service.ForwardingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            // Warm Room so the first SMS doesn't hit a cold DB under BroadcastReceiver.goAsync()'s 10s ceiling.
            AppDatabase.get(this@SmsForwarderApp).openHelper.writableDatabase
        }
        ForwardingService.start(this)
    }
}
