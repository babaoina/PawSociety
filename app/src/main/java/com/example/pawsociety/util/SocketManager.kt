package com.example.pawsociety.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.pawsociety.ChatActivity
import com.example.pawsociety.HomeViewModel
import com.example.pawsociety.LoginActivity
import com.example.pawsociety.MyApplication
import com.example.pawsociety.PostDetailsActivity
import com.example.pawsociety.R
import com.example.pawsociety.UserProfileActivity
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URISyntaxException
import java.net.InetAddress
import java.net.NetworkInterface

object SocketManager {
    private const val TAG = "SocketManager"
    private const val CHANNEL_ID = "pawsociety_notifications"
    private var mSocket: Socket? = null
    private val listeners = mutableMapOf<String, MutableList<(Array<Any>) -> Unit>>()
    private var isConnecting = false
    private var currentUserId: String? = null
    private val onlineStatusListeners = mutableListOf<(String, Boolean) -> Unit>()

    private fun getServerUrl(): String {
        return if (isEmulator()) {
            "http://10.0.2.2:5000"
        } else {
            "http://${getLocalIpAddress()}:5000"
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.HARDWARE == "ranchu"
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement() as NetworkInterface
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement() as InetAddress

                    if (!inetAddress.isLoopbackAddress &&
                        inetAddress.address.size == 4 &&
                        !inetAddress.hostAddress?.startsWith("169.254")!!) {

                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "192.168.254.100"
    }

    // 🔥 ADD THIS - Function to refresh inbox count
    private fun refreshInboxCount() {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                HomeViewModel.refreshInboxCount()
                Log.d(TAG, "🔄 Refreshed inbox count from socket event")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error refreshing inbox count: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PawSociety Notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "Notifications for likes, messages, and follows"
                enableLights(true)
                enableVibration(true)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d(TAG, "✅ Notification Channel Created: $CHANNEL_ID with HIGH importance")
        }
    }

    private fun showDropDownNotification(title: String, message: String, type: String, data: JSONObject) {
        val context = MyApplication.instance

        var fromUserName = data.optString("fromUserName", "")
        if (fromUserName.isEmpty()) {
            fromUserName = data.optString("username", "")
        }
        if (fromUserName.isEmpty()) {
            fromUserName = data.optString("from", "")
        }
        if (fromUserName.isEmpty()) {
            fromUserName = data.optString("senderName", "")
        }
        if (fromUserName.isEmpty()) {
            fromUserName = "Someone"
        }

        val fromUserId = data.optString("fromUserId", "")
        val postId = data.optString("postId", "")
        val messageText = data.optString("text", "")

        val displayMessage = when (type) {
            "like" -> "$fromUserName liked your post"
            "message" -> if (messageText.isNotEmpty()) "$fromUserName: $messageText" else "Message from $fromUserName"
            "message_request" -> "$fromUserName wants to message you"
            "follow" -> "$fromUserName started following you"
            else -> message
        }

        val intent = when (type) {
            "like" -> {
                Intent(context, PostDetailsActivity::class.java).apply {
                    putExtra("postId", postId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            "message", "message_request" -> {
                Intent(context, ChatActivity::class.java).apply {
                    putExtra("receiverUid", fromUserId)
                    putExtra("receiverUsername", fromUserName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            "follow" -> {
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

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(displayMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(defaultSoundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setColor(android.graphics.Color.parseColor("#7A4F2B"))
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val notificationId = System.currentTimeMillis().toInt()
                    val notificationManager = NotificationManagerCompat.from(context)

                    notificationManager.notify(notificationId, builder.build())
                    Log.d(TAG, "✅ Heads-up notification shown: $title - $displayMessage")

                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            notificationManager.cancel(notificationId)
                            Log.d(TAG, "✅ Notification auto-dismissed after 3 seconds")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error auto-dismissing: ${e.message}")
                        }
                    }, 3000)
                } else {
                    Log.w(TAG, "⚠️ Notification permission not granted")
                }
            } else {
                val notificationId = System.currentTimeMillis().toInt()
                val notificationManager = NotificationManagerCompat.from(context)

                notificationManager.notify(notificationId, builder.build())
                Log.d(TAG, "✅ Heads-up notification shown: $title - $displayMessage")

                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        notificationManager.cancel(notificationId)
                        Log.d(TAG, "✅ Notification auto-dismissed after 3 seconds")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error auto-dismissing: ${e.message}")
                    }
                }, 3000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing notification: ${e.message}")
        }
    }

    fun setCurrentUser(userId: String?) {
        this.currentUserId = userId
        if (mSocket?.connected() == true && userId != null) {
            mSocket?.emit("user-online", userId)
        }
    }

    @Synchronized
    fun getSocket(): Socket? {
        if (mSocket == null) {
            createNotificationChannel(MyApplication.instance)

            try {
                val baseUrl = getServerUrl()
                val socketUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

                Log.d(TAG, "🔍 Socket URL being used: $socketUrl")

                val opts = IO.Options().apply {
                    transports = arrayOf("websocket", "polling")
                    reconnection = true
                    reconnectionAttempts = 10
                    reconnectionDelay = 1000
                    reconnectionDelayMax = 5000
                    timeout = 20000
                    forceNew = true
                }

                mSocket = IO.socket(socketUrl, opts)

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
        mSocket?.on(Socket.EVENT_CONNECT) {
            isConnecting = false
            Log.d(TAG, "✅ Socket connected successfully")
            currentUserId?.let { userId ->
                mSocket?.emit("user-online", userId)
            }
        }

        mSocket?.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "Socket disconnected")
        }

        mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            isConnecting = false
            Log.e(TAG, "❌ Socket connection error: ${args.firstOrNull()}")
        }

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

        mSocket?.on("new-like") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "❤️ New like event received: $data")

                    val notificationData = JSONObject().apply {
                        put("fromUserName", data.optString("fromUserName", "Someone"))
                        put("postId", data.optString("postId", ""))
                    }

                    showDropDownNotification("New Like! ❤️", "", "like", notificationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing like notification: ${e.message}")
                }
            }
        }

        mSocket?.on("new-message") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "📨 New message event received: $data")

                    // 🔥 Refresh inbox count
                    refreshInboxCount()

                    val notificationData = JSONObject().apply {
                        put("fromUserName", data.optString("fromUserName", "Someone"))
                        put("fromUserId", data.optString("fromUserId", ""))
                        put("text", data.optString("text", ""))
                    }

                    showDropDownNotification("New Message 📨", "", "message", notificationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message notification: ${e.message}")
                }
            }
        }

        mSocket?.on("new-message-request") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "📨 New message request received: $data")

                    // 🔥 Refresh inbox count
                    refreshInboxCount()

                    val notificationData = JSONObject().apply {
                        put("fromUserName", data.optString("fromUserName", "Someone"))
                        put("fromUserId", data.optString("fromUserId", ""))
                    }

                    showDropDownNotification("Message Request 📨", "", "message_request", notificationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message request: ${e.message}")
                }
            }
        }

        mSocket?.on("new-follow") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "👤 New follow event received: $data")

                    val notificationData = JSONObject().apply {
                        put("fromUserName", data.optString("fromUserName", "Someone"))
                        put("fromUserId", data.optString("fromUserId", ""))
                    }

                    showDropDownNotification("New Follower! 👤", "", "follow", notificationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing follow notification: ${e.message}")
                }
            }
        }

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

        mSocket?.on("force-logout") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    val reason = data?.optString("reason", "Account deleted")
                    Log.d(TAG, "🔴 Force logout received: $reason")

                    // Clear all user data
                    val prefs = MyApplication.instance.getSharedPreferences("PawSocietyPrefs", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        remove("userToken")
                        remove("userData")
                        remove("firebaseToken")
                        remove("userId")
                        apply()
                    }

                    // Clear Firebase Auth
                    try {
                        FirebaseAuthHelper.signOut()
                        Log.d(TAG, "Firebase Auth signed out")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error signing out from Firebase: ${e.message}")
                    }

                    // Disconnect socket
                    disconnect()

                    // Show notification and navigate to login
                    Handler(Looper.getMainLooper()).post {
                        try {
                            val intent = Intent(MyApplication.instance, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                putExtra("logout_reason", "Your account has been deleted by an administrator.")
                                putExtra("force_logout", true)
                            }
                            MyApplication.instance.startActivity(intent)

                            // Show a toast message
                            android.widget.Toast.makeText(
                                MyApplication.instance,
                                "Your account has been deleted by an administrator.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()

                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling force logout: ${e.message}")
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing force logout: ${e.message}")
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