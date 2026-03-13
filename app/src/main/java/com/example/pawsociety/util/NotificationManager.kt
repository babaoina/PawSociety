package com.example.pawsociety.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.MyApplication
import com.example.pawsociety.NotificationsActivity
import com.example.pawsociety.data.repository.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

object NotificationManager {

    private val notificationRepository = NotificationRepository()
    private var pollJob: Job? = null
    private val listeners = mutableListOf<NotificationCountListener>()
    private val listenerMap = mutableMapOf<TextView, NotificationCountListener>()

    interface NotificationCountListener {
        fun onCountUpdated(count: Int)
    }

    fun addListener(listener: NotificationCountListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: NotificationCountListener) {
        listeners.remove(listener)
    }

    fun startPolling(userId: String, lifecycleOwner: LifecycleOwner) {
        stopPolling()

        pollJob = lifecycleOwner.lifecycleScope.launch {
            while (true) {
                try {
                    val result = notificationRepository.getUnreadCount(userId)
                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        listeners.forEach { it.onCountUpdated(count) }
                    }
                    delay(30000) // Poll every 30 seconds
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(60000)
                }
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun setupNotificationBadge(
        context: Context,
        badgeView: TextView,
        userId: String,
        lifecycleOwner: LifecycleOwner,
        onClick: (() -> Unit)? = null
    ) {
        badgeView.setOnClickListener {
            onClick?.invoke() ?: run {
                val intent = Intent(context, NotificationsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }

        val listener = object : NotificationCountListener {
            override fun onCountUpdated(count: Int) {
                Handler(Looper.getMainLooper()).post {
                    if (count > 0) {
                        badgeView.text = count.toString()
                        badgeView.visibility = android.view.View.VISIBLE
                    } else {
                        badgeView.visibility = android.view.View.GONE
                    }
                }
            }
        }

        listenerMap[badgeView] = listener
        addListener(listener)
        startPolling(userId, lifecycleOwner)
    }

    fun cleanup(badgeView: TextView) {
        val listener = listenerMap.remove(badgeView)
        listener?.let { removeListener(it) }
        stopPolling()
    }

    fun setupSocketNotifications(userId: String, lifecycleOwner: LifecycleOwner) {
        SocketManager.on("new-notification") { args ->
            if (args.isNotEmpty()) {
                try {
                    val notification = args[0] as? JSONObject
                    if (notification != null) {
                        lifecycleOwner.lifecycleScope.launch {
                            val result = notificationRepository.getUnreadCount(userId)
                            if (result.isSuccess) {
                                val count = result.getOrNull() ?: 0
                                listeners.forEach { it.onCountUpdated(count) }
                            }
                        }

                        Handler(Looper.getMainLooper()).post {
                            val message = notification.optString("message", "New notification")
                            Toast.makeText(MyApplication.instance, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}