package com.example.pawsociety.util

import android.util.Log
import com.example.pawsociety.MyApplication
import com.example.pawsociety.api.ApiClient
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

object SocketManager {
    private const val TAG = "SocketManager"
    private var mSocket: Socket? = null
    private val listeners = mutableMapOf<String, MutableList<(Array<Any>) -> Unit>>()
    private var isConnecting = false
    private var currentUserId: String? = null
    private val onlineStatusListeners = mutableListOf<(String, Boolean) -> Unit>()

    @Synchronized
    fun getSocket(): Socket? {
        if (mSocket == null) {
            try {
                val baseUrl = ApiClient.FULL_BASE_URL
                val socketUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl

                val opts = IO.Options().apply {
                    reconnection = true
                    reconnectionAttempts = 10
                    reconnectionDelay = 1000
                    reconnectionDelayMax = 5000
                    timeout = 20000
                }

                mSocket = IO.socket(socketUrl, opts)
                setupBaseListeners()
                Log.d(TAG, "Socket initialized with URL: $socketUrl")
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
            Log.d(TAG, "Socket connected")
            // Re-emit user presence when reconnected
            currentUserId?.let { userId ->
                emit("user-online", userId)
            }
        }?.on(Socket.EVENT_DISCONNECT) {
            Log.d(TAG, "Socket disconnected")
        }?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            isConnecting = false
            Log.e(TAG, "Socket connect error: ${args.firstOrNull()}")
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
                        // Notify all listeners
                        onlineStatusListeners.forEach { it.invoke(userId, isOnline) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing user status: ${e.message}")
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
            }
            if (mSocket != null && !mSocket!!.connected()) {
                isConnecting = true
                mSocket!!.connect()
                Log.d(TAG, "Socket connecting...")
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
                // Tell server user is going offline
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
            // Tell everyone you're online
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