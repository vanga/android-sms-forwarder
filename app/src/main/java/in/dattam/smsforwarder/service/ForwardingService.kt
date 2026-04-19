package `in`.dattam.smsforwarder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import `in`.dattam.smsforwarder.R
import `in`.dattam.smsforwarder.ui.MainActivity

/**
 * Foreground service whose sole purpose is to keep the app process warm so
 * OEM battery optimizers don't kill the SMS BroadcastReceiver / WorkManager
 * queue on Xiaomi/Oppo/Samsung/etc.
 */
class ForwardingService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle("SMS forwarding active")
            .setContentText("Listening for incoming SMS")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val id = getString(R.string.channel_id)
        if (nm.getNotificationChannel(id) != null) return
        nm.createNotificationChannel(
            NotificationChannel(id, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 1
        private const val TAG = "ForwardingService"

        /**
         * Start the foreground service. Safe to call from activities, boot
         * receivers, and user-initiated flows. On API 31+ this can still
         * throw ForegroundServiceStartNotAllowedException from background
         * contexts (e.g. during a broadcast delivered to a reaped process);
         * we swallow that exception so it never crashes the app — the next
         * activity launch or SMS-triggered WorkManager job will carry on
         * regardless.
         */
        fun start(ctx: Context) {
            val i = Intent(ctx, ForwardingService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (t: Throwable) {
                // ForegroundServiceStartNotAllowedException (API 31+) is the
                // expected case; catch broadly for forward-compat.
                android.util.Log.w(TAG, "FGS start denied: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }
}
