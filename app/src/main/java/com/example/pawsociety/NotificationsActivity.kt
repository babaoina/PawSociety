package com.example.pawsociety

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiNotification
import com.example.pawsociety.data.repository.NotificationRepository
import com.example.pawsociety.util.NotificationManager
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NotificationsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val notificationRepository = NotificationRepository()
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private val notificationsList = mutableListOf<ApiNotification>()
    private lateinit var notificationsAdapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        sessionManager = SessionManager(this)

        // Check if user is logged in
        val currentUser = sessionManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Please login to view notifications", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        loadNotifications(currentUser.firebaseUid)

        // Mark all as read when opening notifications
        markAllAsRead(currentUser.firebaseUid)

        // Back Button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Clear All Button
        findViewById<TextView>(R.id.btn_clear_all).setOnClickListener {
            clearAllNotifications(currentUser.firebaseUid)
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.notifications_recycler_view)
        emptyState = findViewById(R.id.empty_state)
    }

    private fun setupRecyclerView() {
        notificationsAdapter = NotificationsAdapter(notificationsList) { notification ->
            // Handle notification click
            when (notification.type) {
                "like", "comment" -> {
                    // Navigate to the post
                    if (!notification.postId.isNullOrEmpty()) {
                        val intent = Intent(this, PostDetailsActivity::class.java)
                        intent.putExtra("postId", notification.postId)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "Post no longer available", Toast.LENGTH_SHORT).show()
                    }
                }
                "message", "message_request" -> {
                    // Navigate to conversation/chat
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("receiverUid", notification.fromUserId)
                    intent.putExtra("receiverUsername", notification.fromUserName)
                    startActivity(intent)
                }
                "follow" -> {
                    // Navigate to user profile
                    val intent = Intent(this, UserProfileActivity::class.java)
                    intent.putExtra("userId", notification.fromUserId)
                    intent.putExtra("userName", notification.fromUserName)
                    startActivity(intent)
                }
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = notificationsAdapter
    }

    private fun loadNotifications(userId: String) {
        lifecycleScope.launch {
            try {
                val result = notificationRepository.getNotifications(userId)

                if (result.isSuccess) {
                    val notifications = result.getOrNull()!!
                    notificationsList.clear()
                    notificationsList.addAll(notifications)
                    notificationsList.sortByDescending { it.createdAt } // Newest first
                    notificationsAdapter.notifyDataSetChanged()

                    if (notifications.isEmpty()) {
                        recyclerView.visibility = View.GONE
                        emptyState.visibility = View.VISIBLE
                    } else {
                        recyclerView.visibility = View.VISIBLE
                        emptyState.visibility = View.GONE
                    }
                } else {
                    Toast.makeText(this@NotificationsActivity, "Failed to load notifications", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NotificationsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markAllAsRead(userId: String) {
        lifecycleScope.launch {
            try {
                notificationRepository.markAllAsRead(userId)
                // Refresh badge count in HomeActivity
                NotificationManager.startPolling(userId, this@NotificationsActivity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun clearAllNotifications(userId: String) {
        lifecycleScope.launch {
            try {
                val result = notificationRepository.clearAllNotifications(userId)
                if (result.isSuccess) {
                    notificationsList.clear()
                    notificationsAdapter.notifyDataSetChanged()
                    recyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    Toast.makeText(this@NotificationsActivity, "All notifications cleared", Toast.LENGTH_SHORT).show()
                    // Refresh badge count
                    NotificationManager.startPolling(userId, this@NotificationsActivity)
                } else {
                    Toast.makeText(this@NotificationsActivity, "Failed to clear notifications", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NotificationsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// Updated Notification Adapter with Instagram style
class NotificationsAdapter(
    private val notifications: List<ApiNotification>,
    private val onItemClick: (ApiNotification) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userIcon: TextView = itemView.findViewById(R.id.notif_user_icon)
        val userImage: ImageView = itemView.findViewById(R.id.notif_user_image)
        val message: TextView = itemView.findViewById(R.id.notif_message)
        val time: TextView = itemView.findViewById(R.id.notif_time)
        val actionImage: ImageView = itemView.findViewById(R.id.notif_action_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_instagram, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notif = notifications[position]

        // 🔥 FIXED: Show username for message notifications
        val messageText = when (notif.type) {
            "like" -> "${notif.fromUserName} liked your post"
            "comment" -> {
                if (notif.message.length > 50) {
                    "${notif.fromUserName} commented: ${notif.message.substring(0, 47)}..."
                } else {
                    "${notif.fromUserName} commented: ${notif.message}"
                }
            }
            "follow" -> "${notif.fromUserName} started following you"
            "message", "message_request" -> "${notif.fromUserName}: ${notif.message}"  // 🔥 ADDED
            else -> notif.message
        }
        holder.message.text = messageText

        // Set time
        holder.time.text = getTimeAgo(notif.createdAt)

        // 🔥 FIXED: Set action image based on type (including messages)
        when (notif.type) {
            "like" -> {
                holder.actionImage.setImageResource(R.drawable.ic_heart_filled)
                holder.actionImage.visibility = View.VISIBLE
            }
            "comment" -> {
                holder.actionImage.setImageResource(R.drawable.comment)
                holder.actionImage.visibility = View.VISIBLE
            }
            "follow" -> {
                holder.actionImage.setImageResource(R.drawable.add)
                holder.actionImage.visibility = View.VISIBLE
            }
            "message", "message_request" -> {  // 🔥 ADDED
                holder.actionImage.setImageResource(R.drawable.message)
                holder.actionImage.visibility = View.VISIBLE
            }
            else -> holder.actionImage.visibility = View.GONE
        }
        holder.actionImage.setColorFilter(Color.parseColor("#7A4F2B"))

        // ===== FIXED: Load user profile picture properly =====
        if (!notif.fromUserImage.isNullOrEmpty() && notif.fromUserImage != "null") {
            val fullImageUrl = if (notif.fromUserImage.startsWith("http")) {
                notif.fromUserImage
            } else {
                // Make sure to use the correct base URL without double slashes
                val baseUrl = com.example.pawsociety.api.ApiClient.FULL_BASE_URL
                val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
                val cleanImageUrl = if (notif.fromUserImage.startsWith("/")) notif.fromUserImage else "/$notif.fromUserImage"
                "$cleanBaseUrl$cleanImageUrl"
            }

            holder.userIcon.visibility = View.GONE
            holder.userImage.visibility = View.VISIBLE

            Glide.with(holder.itemView.context)
                .load(fullImageUrl)
                .circleCrop()
                .placeholder(R.drawable.circle_solid_profile)
                .error(R.drawable.circle_solid_profile)
                .into(holder.userImage)
        } else {
            // Show text icon with first letter
            holder.userImage.visibility = View.GONE
            holder.userIcon.visibility = View.VISIBLE

            val firstLetter = if (notif.fromUserName.isNotEmpty()) {
                notif.fromUserName.first().toString().uppercase()
            } else {
                "?"
            }
            holder.userIcon.text = firstLetter

            // Set icon color based on type
            val color = when (notif.type) {
                "like" -> "#FF6B35"
                "comment" -> "#4CAF50"
                "follow" -> "#2196F3"
                "message", "message_request" -> "#7A4F2B"  // 🔥 ADDED
                else -> "#7A4F2B"
            }
            holder.userIcon.setBackgroundColor(Color.parseColor(color))
        }

        holder.itemView.setOnClickListener {
            onItemClick(notif)
        }
    }

    override fun getItemCount() = notifications.size

    private fun getTimeAgo(dateString: String): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(dateString) ?: return ""

            val now = Date()
            val diff = now.time - date.time
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            when {
                days > 0 -> "${days}d"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes}m"
                else -> "now"
            }
        } catch (e: Exception) {
            ""
        }
    }
}