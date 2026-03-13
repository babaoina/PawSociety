package com.example.pawsociety.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.pawsociety.ChatActivity
import com.example.pawsociety.MyApplication
import com.example.pawsociety.PostDetailsActivity
import com.example.pawsociety.R
import com.example.pawsociety.UserProfileActivity
import com.example.pawsociety.api.ApiClient
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

object SocketManager {
    private const val TAG = "SocketManager"
    private const val CHANNEL_ID = "pawsociety_alerts_v2" // Changed ID to force new settings
    private var mSocket: Socket? = null
    private val listeners = mutableMapOf<String, MutableList<(Array<Any>) -> Unit>>()
    private var isConnecting = false
    private var currentUserId: String? = null
    private val onlineStatusListeners = mutableListOf<(String, Boolean) -> Unit>()

    // Create notification channel with HIGH importance (REQUIRED for drop-down)
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PawSociety Alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH // Drops down from top
            val channel = android.app.NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "Notifications for likes, comments, and messages"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification Channel Created: $CHANNEL_ID with HIGH importance")
        }
    }

    // Function to show drop-down notifications
    private fun showDropDownNotification(title: String, message: String, type: String, data: JSONObject) {
        val context = MyApplication.instance

        // Create intent based on notification type
        val intent = when (type) {
            "like", "comment" -> {
                val postId = data.optString("postId", "")
                Intent(context, PostDetailsActivity::class.java).apply {
                    putExtra("postId", postId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            "message" -> {
                val fromUserId = data.optString("fromUserId", "")
                val fromUserName = data.optString("fromUserName", "")
                Intent(context, ChatActivity::class.java).apply {
                    putExtra("receiverUid", fromUserId)
                    putExtra("receiverUsername", fromUserName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            "follow" -> {
                val fromUserId = data.optString("fromUserId", "")
                val fromUserName = data.optString("fromUserName", "")
                Intent(context, UserProfileActivity::class.java).apply {
                    putExtra("userId", fromUserId)
                    putExtra("userName", fromUserName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            else -> {
                Intent(context, com.example.pawsociety.NotificationsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Get notification sound
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID) // Use the same CHANNEL_ID
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Change to your own icon later
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // For older Android versions
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Triggers sound/vibe/heads-up
            .setSound(defaultSoundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // For Android 13+ - add color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setColor(android.graphics.Color.parseColor("#7A4F2B"))
        }

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
            Log.d(TAG, "✅ Drop-down notification shown: $title - $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing notification: ${e.message}")
        }
    }

    @Synchronized
    fun getSocket(): Socket? {
        if (mSocket == null) {
            // Create notification channel BEFORE socket initialization
            createNotificationChannel(MyApplication.instance)

            try {
                val baseUrl = ApiClient.FULL_BASE_URL
                val socketUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

                Log.d(TAG, "Connecting to socket at: $socketUrl")

                val opts = IO.Options().apply {
                    transports = arrayOf("websocket", "polling") // Try websocket first
                    reconnection = true
                    reconnectionAttempts = 10
                    reconnectionDelay = 1000
                    reconnectionDelayMax = 5000
                    timeout = 20000
                    forceNew = true
                }

                mSocket = IO.socket(socketUrl, opts)

                // CRITICAL: Setup listeners BEFORE calling connect()
                setupBaseListeners()
                mSocket?.connect()

                Log.d(TAG, "📡 Socket attempting connection to: $socketUrl")
            } catch (e: URISyntaxException) {
                Log.e(TAG, "Socket initialization error: ${e.message}")
                e.printStackTrace()
            }
        }
        return mSocket
    }

    private fun setupBaseListeners() {
        // Clean single listener for connect
        mSocket?.on(Socket.EVENT_CONNECT) {
            isConnecting = false
            Log.d(TAG, "✅ Socket connected successfully")
            currentUserId?.let { userId ->
                // Make sure the event name matches your backend (user-online vs join)
                mSocket?.emit("user-online", userId)
            }
        }

        // Clean single listener for disconnect
        mSocket?.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "Socket disconnected")
        }

        // Clean single listener for connect error
        mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            isConnecting = false
            Log.e(TAG, "❌ Socket connection error: ${args.firstOrNull()}")
            if (args.isNotEmpty()) {
                Log.e(TAG, "Error details: ${args[0]}")
            }
        }

        // Listen for user status changes
        mSocket?.on("user-status") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    val userId = data?.optString("userId", "")
                    val isOnline = data?.optBoolean("online", false)
                    if (userId != null && isOnline != null) {
                        Log.d(TAG, "User $userId is ${if (isOnline) "online" else "offline"}")
                        onlineStatusListeners.forEach { it.invoke(userId, isOnline) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing user status: ${e.message}")
                }
            }
        }

        // Listen for new likes
        mSocket?.on("new-like") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "❤️ New like event received: $data")

                    val fromUserName = data.optString("fromUserName", "Someone")
                    val postId = data.optString("postId", "")

                    val message = "$fromUserName liked your post"
                    val notificationData = JSONObject().apply {
                        put("fromUserName", fromUserName)
                        put("postId", postId)
                    }

                    showDropDownNotification("New Like! ❤️", message, "like", notificationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing like notification: ${e.message}")
                }
            }
        }

        // Listen for new comments
        mSocket?.on("new-comment") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "💬 New comment event received: $data")

                    val fromUserName = data.optString("fromUserName", "Someone")
                    val postId = data.optString("postId", "")

                    val message = "$fromUserName commented on your post"
                    val notificationData = JSONObject().apply {
                        put("fromUserName", fromUserName)
                        put("postId", postId)
                    }

                    showDropDownNotification("New Comment! 💬", message, "comment", notificationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing comment notification: ${e.message}")
                }
            }
        }

        // Listen for new messages
        mSocket?.on("new-message") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "📨 New message event received: $data")

                    val fromUserName = data.optString("fromUserName", "Someone")
                    val fromUserId = data.optString("fromUserId", "")
                    val messageText = data.optString("text", "Sent you a message")

                    val notificationData = JSONObject().apply {
                        put("fromUserName", fromUserName)
                        put("fromUserId", fromUserId)
                        put("text", messageText)
                    }

                    showDropDownNotification("Message from $fromUserName 📨", messageText, "message", notificationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message notification: ${e.message}")
                }
            }
        }

        // Listen for new followers
        mSocket?.on("new-follow") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "👥 New follow event received: $data")

                    val fromUserName = data.optString("fromUserName", "Someone")
                    val fromUserId = data.optString("fromUserId", "")

                    val message = "$fromUserName started following you"
                    val notificationData = JSONObject().apply {
                        put("fromUserName", fromUserName)
                        put("fromUserId", fromUserId)
                    }

                    showDropDownNotification("New Follower! 👥", message, "follow", notificationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing follow notification: ${e.message}")
                }
            }
        }

        // Listen for profile updates
        mSocket?.on("profile-updated") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    val userId = data?.optString("userId", "")
                    Log.d(TAG, "Profile updated for user: $userId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing profile update: ${e.message}")
                }
            }
        }
    }

    @Synchronized
    fun connect() {
        if (isConnecting) return

        try {
            if (mSocket == null) {
                getSocket()
            } else if (mSocket != null && !mSocket!!.connected()) {
                isConnecting = true
                Log.d(TAG, "Attempting to connect socket...")
                mSocket!!.connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket connect error: ${e.message}")
            isConnecting = false
        }
    }

    @Synchronized
    fun disconnect() {
        try {
            if (mSocket != null && mSocket!!.connected()) {
                currentUserId?.let { userId ->
                    emit("user-offline", userId)
                }
                mSocket!!.disconnect()
                mSocket?.off()
                mSocket = null
                listeners.clear()
                onlineStatusListeners.clear()
                isConnecting = false
                Log.d(TAG, "Socket disconnected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket disconnect error: ${e.message}")
        }
    }

    @Synchronized
    fun emit(event: String, vararg args: Any) {
        try {
            if (mSocket != null && mSocket!!.connected()) {
                mSocket!!.emit(event, *args)
                Log.d(TAG, "Emitted event: $event")
            } else {
                Log.w(TAG, "Socket not connected, cannot emit: $event")
                connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Socket emit error: ${e.message}")
        }
    }

    @Synchronized
    fun on(event: String, callback: (Array<Any>) -> Unit) {
        try {
            if (mSocket == null) {
                getSocket()
            }

            mSocket?.off(event)
            mSocket?.on(event) { args ->
                callback(args)
            }

            listeners.getOrPut(event) { mutableListOf() }.add(callback)
            Log.d(TAG, "Registered listener for: $event")
        } catch (e: Exception) {
            Log.e(TAG, "Socket on error: ${e.message}")
        }
    }

    @Synchronized
    fun off(event: String) {
        try {
            mSocket?.off(event)
            listeners.remove(event)
            Log.d(TAG, "Removed listener for: $event")
        } catch (e: Exception) {
            Log.e(TAG, "Socket off error: ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return mSocket != null && mSocket!!.connected()
    }

    fun joinUserRoom(userId: String) {
        if (userId.isNotEmpty()) {
            currentUserId = userId
            emit("join", userId)
            emit("user-online", userId)
            Log.d(TAG, "User $userId joined their room and is now online")
        }
    }

    fun joinChatRoom(chatId: String) {
        if (chatId.isNotEmpty()) {
            emit("join-chat", chatId)
            Log.d(TAG, "Joined chat room: $chatId")
        }
    }

    fun leaveChatRoom(chatId: String) {
        if (chatId.isNotEmpty()) {
            emit("leave-chat", chatId)
            Log.d(TAG, "Left chat room: $chatId")
        }
    }

    fun onUserProfileUpdated(callback: (String) -> Unit) {
        on("user-profile-updated") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    val userId = data?.optString("userId", "")
                    if (userId != null) {
                        callback(userId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun sendTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        val json = JSONObject().apply {
            put("chatId", chatId)
            put("userId", userId)
            put("isTyping", isTyping)
        }
        emit("typing", json)
    }

    fun addOnlineStatusListener(listener: (String, Boolean) -> Unit) {
        onlineStatusListeners.add(listener)
    }

    fun removeOnlineStatusListener(listener: (String, Boolean) -> Unit) {
        onlineStatusListeners.remove(listener)
    }
}