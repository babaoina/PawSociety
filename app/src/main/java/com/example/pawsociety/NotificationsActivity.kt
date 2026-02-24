package com.example.pawsociety

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        
        // Back Button
        findViewById<TextView>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }
    
    private fun initializeViews() {
        recyclerView = findViewById(R.id.notifications_recycler_view)
        emptyState = findViewById(R.id.empty_state)
    }
    
    private fun setupRecyclerView() {
        notificationsAdapter = NotificationsAdapter(notificationsList) { notification ->
            // Handle notification click
            if (notification.type == "like" || notification.type == "comment") {
                Toast.makeText(this, "Opening post...", Toast.LENGTH_SHORT).show()
                // TODO: Navigate to post
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
}

// Notification Adapter
class NotificationsAdapter(
    private val notifications: List<ApiNotification>,
    private val onItemClick: (ApiNotification) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userIcon: TextView = itemView.findViewById(R.id.notif_user_icon)
        val message: TextView = itemView.findViewById(R.id.notif_message)
        val time: TextView = itemView.findViewById(R.id.notif_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notif = notifications[position]
        
        // Set message
        holder.message.text = notif.message
        
        // Set time
        holder.time.text = getTimeAgo(notif.createdAt)
        
        // Set user icon (first letter)
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
            else -> "#7A4F2B"
        }
        holder.userIcon.setBackgroundColor(Color.parseColor(color))
        
        holder.itemView.setOnClickListener {
            onItemClick(notif)
        }
    }

    override fun getItemCount() = notifications.size
    
    private fun getTimeAgo(dateString: String): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(dateString)
            val now = Date()
            
            if (date != null) {
                val diff = now.time - date.time
                val seconds = diff / 1000
                val minutes = seconds / 60
                val hours = minutes / 60
                val days = hours / 24

                when {
                    days > 0 -> "${days}d ago"
                    hours > 0 -> "${hours}h ago"
                    minutes > 0 -> "${minutes}m ago"
                    else -> "Just now"
                }
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
