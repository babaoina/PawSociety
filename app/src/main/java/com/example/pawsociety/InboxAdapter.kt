package com.example.pawsociety

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.example.pawsociety.api.ApiConversation
import com.example.pawsociety.api.ApiUser
import java.text.SimpleDateFormat
import java.util.*

class InboxAdapter(
    private val conversations: List<ApiConversation>,
    private val usersMap: Map<String, ApiUser>,
    private val currentUserId: String,
    private val onUserClick: (ApiUser) -> Unit
) : RecyclerView.Adapter<InboxAdapter.InboxViewHolder>() {

    private val colors = listOf(
        "#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#E91E63"
    )

    // Map to store online status
    private val onlineStatus = mutableMapOf<String, Boolean>()

    class InboxViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfile: ImageView = itemView.findViewById(R.id.iv_profile)
        val tvProfileIcon: TextView = itemView.findViewById(R.id.tv_profile_icon)
        val tvUsername: TextView = itemView.findViewById(R.id.tv_username)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tv_last_message)
        val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        val tvUnread: TextView = itemView.findViewById(R.id.tv_unread)
        val redPing: View = itemView.findViewById(R.id.red_ping)
        val onlineIndicator: View = itemView.findViewById(R.id.online_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InboxViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inbox_story, parent, false)
        return InboxViewHolder(view)
    }

    override fun onBindViewHolder(holder: InboxViewHolder, position: Int) {
        val conversation = conversations[position]

        // Get the other user (not current user)
        val otherUserId = conversation.participants.find { it != currentUserId }
        if (otherUserId == null) {
            holder.itemView.visibility = View.GONE
            return
        }

        val user = usersMap[otherUserId]
        if (user == null) {
            holder.itemView.visibility = View.GONE
            return
        }

        holder.itemView.visibility = View.VISIBLE

        // Set username
        holder.tvUsername.text = user.username

        // Set last message
        if (conversation.lastMessage != null) {
            val lastMsg = conversation.lastMessage!!
            val messageText = when {
                !lastMsg.text.isNullOrEmpty() -> {
                    if (lastMsg.text.length > 30) {
                        lastMsg.text.substring(0, 27) + "..."
                    } else {
                        lastMsg.text
                    }
                }
                !lastMsg.imageUrl.isNullOrEmpty() -> "📷 Photo"
                else -> "No messages yet"
            }

            val displayText = if (lastMsg.senderUid == currentUserId) {
                "You: $messageText"
            } else {
                messageText
            }
            holder.tvLastMessage.text = displayText
        } else {
            holder.tvLastMessage.text = "No messages yet"
        }

        // Set time
        holder.tvTime.text = formatTime(conversation.lastMessageAt)

        // Show unread count
        if (conversation.unreadCount > 0) {
            holder.redPing.visibility = View.VISIBLE
            holder.tvUnread.visibility = View.VISIBLE
            holder.tvUnread.text = conversation.unreadCount.toString()
            holder.tvLastMessage.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.tvLastMessage.setTextColor(Color.BLACK)
        } else {
            holder.redPing.visibility = View.GONE
            holder.tvUnread.visibility = View.GONE
            holder.tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.tvLastMessage.setTextColor(Color.parseColor("#666666"))
        }

        // Show online status if user is online
        if (onlineStatus[otherUserId] == true) {
            holder.onlineIndicator.visibility = View.VISIBLE
        } else {
            holder.onlineIndicator.visibility = View.GONE
        }

        // ========== FIXED: PROFILE IMAGE WITH CIRCLE CROP ==========
        if (!user.profileImageUrl.isNullOrEmpty()) {
            val fullImageUrl = if (user.profileImageUrl.startsWith("http")) {
                user.profileImageUrl
            } else {
                "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${user.profileImageUrl}"
            }

            // Show image view, hide text icon
            holder.ivProfile.visibility = View.VISIBLE
            holder.tvProfileIcon.visibility = View.GONE

            // IMPORTANT: Clear any previous image to avoid flickering
            holder.ivProfile.setImageDrawable(null)

            // Load image with CircleCrop - MULTIPLE METHODS TO ENSURE IT WORKS
            try {
                // Method 1: Using RequestOptions
                val requestOptions = RequestOptions()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .transform(CircleCrop())

                Glide.with(holder.itemView.context)
                    .load(fullImageUrl)
                    .apply(requestOptions)
                    .into(holder.ivProfile)

                // Alternative Method 2: If above doesn't work, uncomment this:
                /*
                Glide.with(holder.itemView.context)
                    .load(fullImageUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(holder.ivProfile)
                */

            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to text icon if image loading fails
                holder.ivProfile.visibility = View.GONE
                holder.tvProfileIcon.visibility = View.VISIBLE
                showTextIcon(holder, user)
            }
        } else {
            // No profile image - show text icon
            holder.ivProfile.visibility = View.GONE
            holder.tvProfileIcon.visibility = View.VISIBLE
            showTextIcon(holder, user)
        }

        holder.itemView.setOnClickListener {
            onUserClick(user)
        }
    }

    // Helper function to show text icon with first letter
    private fun showTextIcon(holder: InboxViewHolder, user: ApiUser) {
        val firstLetter = if (user.username.isNotEmpty()) {
            user.username.first().toString().uppercase()
        } else {
            "?"
        }
        holder.tvProfileIcon.text = firstLetter

        val colorIndex = Math.abs(user.firebaseUid.hashCode()) % colors.size
        holder.tvProfileIcon.setBackgroundColor(Color.parseColor(colors[colorIndex]))
    }

    override fun getItemCount() = conversations.size

    // Function to update online status
    fun updateOnlineStatus(userId: String, isOnline: Boolean) {
        onlineStatus[userId] = isOnline
        notifyDataSetChanged()
    }

    // Function to update user data (for real-time profile changes)
    fun updateUser(userId: String, updatedUser: ApiUser) {
        // This would require usersMap to be mutable - you'd need to change that in InboxActivity
        notifyDataSetChanged()
    }

    private fun formatTime(dateString: String): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(dateString) ?: return ""

            val now = Date()
            val diff = now.time - date.time
            val minutes = diff / (1000 * 60)
            val hours = minutes / 60
            val days = hours / 24

            when {
                minutes < 1 -> "now"
                minutes < 60 -> "${minutes}m"
                hours < 24 -> "${hours}h"
                days < 7 -> "${days}d"
                else -> {
                    val outputFormat = SimpleDateFormat("MMM d", Locale.getDefault())
                    outputFormat.format(date)
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}