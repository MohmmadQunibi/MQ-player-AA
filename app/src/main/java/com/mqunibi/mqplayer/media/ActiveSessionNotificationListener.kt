package com.mqunibi.mqplayer.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class ActiveSessionNotificationListener : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        ActiveMediaRepository.initialize(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ActiveMediaRepository.onNotificationListenerAvailabilityChanged()
    }

    override fun onListenerDisconnected() {
        ActiveMediaRepository.onNotificationListenerAvailabilityChanged()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        ActiveMediaRepository.refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        ActiveMediaRepository.refresh()
    }
}

