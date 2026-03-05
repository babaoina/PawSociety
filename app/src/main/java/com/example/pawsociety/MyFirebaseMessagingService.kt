package com.example.pawsociety

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.FCMTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "FCMService"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val CHANNEL_ID = "pawsociety_notifications"
        private const val CHANNEL_NAME = "PawSociety Notifications"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(tag, "From: ${remoteMessage.from}")

        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(tag, "Message data payload: ${remoteMessage.data}")

            // Handle data message
            val title = remoteMessage.data["title"] ?: "PawSociety"
            val body = remoteMessage.data["body"] ?: "You have a new notification"
            val type = remoteMessage.data["type"] ?: "general"
            val postId = remoteMessage.data["postId"] ?: ""
            val userId = remoteMessage.data["userId"] ?: ""

            sendNotification(title, body, type, postId, userId)
        }

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(tag, "Message Notification Body: ${it.body}")

            // Handle notification message
            val title = it.title ?: "PawSociety"
            val body = it.body ?: "You have a new notification"

            sendNotification(title, body, "general", "", "")
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "Refreshed token: $token")

        // Send token to your server
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        // Get current user from SessionManager and send token
        val sessionManager = SessionManager(applicationContext)
        val currentUser = sessionManager.getCurrentUser()

        if (currentUser != null) {
            // Use coroutineScope to call suspend function
            coroutineScope.launch {
                FCMTokenManager.saveTokenToServer(currentUser.firebaseUid, token)
            }
        }
    }

    private fun sendNotification(
        title: String,
        message: String,
        type: String,
        postId: String,
        userId: String
    ) {
        val intent = when (type) {
            "like", "comment" -> {
                Intent(this, PostDetailsActivity::class.java).apply {
                    putExtra("postId", postId)
                }
            }
            "follow" -> {
                Intent(this, UserProfileActivity::class.java).apply {
                    putExtra("userId", userId)
                    putExtra("userName", "")
                }
            }
            else -> {
                Intent(this, HomeActivity::class.java)
            }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "PawSociety notifications"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}