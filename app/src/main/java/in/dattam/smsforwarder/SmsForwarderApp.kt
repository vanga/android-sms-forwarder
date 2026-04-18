package `in`.dattam.smsforwarder

import android.app.Application
import `in`.dattam.smsforwarder.service.ForwardingService

class SmsForwarderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ForwardingService.start(this)
    }
}
