package `in`.dattam.smsforwarder

import android.app.Application
import `in`.dattam.smsforwarder.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ForwardingService is intentionally NOT started from here. On Android 12+,
 * `startForegroundService()` from Application.onCreate() throws
 * ForegroundServiceStartNotAllowedException when the process is cold-started
 * by a background broadcast (e.g. SMS_RECEIVED arriving to a reaped process).
 * The FGS is started from:
 *   - MainActivity.onCreate — always allowed (activity is foregrounded).
 *   - BootReceiver — gets the boot-time FGS-start exemption on 12+.
 */
class SmsForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            // Warm Room so the first SMS doesn't hit a cold DB under BroadcastReceiver.goAsync()'s 10s ceiling.
            AppDatabase.get(this@SmsForwarderApp).openHelper.writableDatabase
        }
    }
}
