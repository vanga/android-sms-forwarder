package `in`.dattam.smsforwarder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.dattam.smsforwarder.service.ForwardingService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Guard against rogue explicit intents from other apps — only act on the
        // three broadcasts we registered for in the manifest.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> ForwardingService.start(context)
        }
    }
}
