package `in`.dattam.smsforwarder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.dattam.smsforwarder.service.ForwardingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ForwardingService.start(context)
    }
}
