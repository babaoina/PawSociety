package com.example.pawsociety.util

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object InboxBadgeManager {

    private val chatRepository = ChatRepository()
    private var badgeViews = mutableListOf<TextView>()
    private var pollingJob: Job? = null
    private var currentUserId: String? = null
    private var lifecycleOwner: LifecycleOwner? = null

    fun initialize(userId: String, owner: LifecycleOwner) {
        currentUserId = userId
        lifecycleOwner = owner
        startPolling()
    }

    fun registerBadge(badgeView: TextView) {
        if (!badgeViews.contains(badgeView)) {
            badgeViews.add(badgeView)
        }
    }

    fun unregisterBadge(badgeView: TextView) {
        badgeViews.remove(badgeView)
    }

    private fun startPolling() {
        stopPolling()

        pollingJob = lifecycleOwner?.lifecycleScope?.launch {
            while (true) {
                try {
                    val userId = currentUserId ?: break
                    val result = chatRepository.getConversations(userId)

                    if (result.isSuccess) {
                        val response = result.getOrNull()!!
                        val totalUnread = (response.messages?.sumOf { it.unreadCount } ?: 0) +
                                (response.requests?.size ?: 0)

                        updateAllBadges(totalUnread)
                    }
                    delay(5000) // Poll every 5 seconds
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(10000)
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun updateBadgeManually(count: Int) {
        updateAllBadges(count)
    }

    private fun updateAllBadges(count: Int) {
        badgeViews.forEach { badge ->
            badge.post {
                if (count > 0) {
                    badge.text = if (count > 99) "99+" else count.toString()
                    badge.visibility = View.VISIBLE
                } else {
                    badge.visibility = View.GONE
                }
            }
        }
    }

    fun cleanup() {
        stopPolling()
        badgeViews.clear()
        currentUserId = null
        lifecycleOwner = null
    }
}