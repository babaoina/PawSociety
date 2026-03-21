package com.example.pawsociety

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build

class MyApplication : Application() {

    companion object {
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PawSociety Notifications"
            val descriptionText = "Notifications for likes, comments, and messages"
            val importance = NotificationManager.IMPORTANCE_HIGH

            // 🔥 FIXED: Use the SAME channel ID as in MyFirebaseMessagingService
            val channel = NotificationChannel("pawsociety_notifications", name, importance).apply {
                this.description = descriptionText
                enableLights(true)
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            println("✅ Notification channel created with HIGH importance")
        }
    }
}