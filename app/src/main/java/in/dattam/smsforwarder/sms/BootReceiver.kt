package `in`.dattam.smsforwarder.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.dattam.smsforwarder.data.SettingsRepo
import `in`.dattam.smsforwarder.service.ForwardingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (SettingsRepo(context).current().enhancedReliability) {
                    ForwardingService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
