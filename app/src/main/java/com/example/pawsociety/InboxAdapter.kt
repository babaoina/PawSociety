package com.example.pawsociety

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.example.pawsociety.api.ApiConversation
import com.example.pawsociety.api.ApiUser
import java.text.SimpleDateFormat
import java.util.*
import android.widget.LinearLayout

// Interface for long click listener
interface OnItemLongClickListener {
    fun onItemLongClick(conversation: ApiConversation, user: ApiUser)
}

class InboxAdapter(
    private val conversations: List<ApiConversation>,
    private val usersMap: Map<String, ApiUser>,
    private val currentUserId: String,
    private val isRequestTab: Boolean = false,
    private val onUserClick: (ApiUser) -> Unit,
    private val onAcceptRequest: (ApiConversation) -> Unit,
    private val onRejectRequest: (ApiConversation) -> Unit,
    private val onLongClick: OnItemLongClickListener
) : RecyclerView.Adapter<InboxAdapter.InboxViewHolder>() {

    private val colors = listOf(
        "#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#E91E63"
    )

    // Map to store online status
    private val onlineStatus = mutableMapOf<String, Boolean>()

    class InboxViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Profile image views
        val ivProfile: ImageView = itemView.findViewById(R.id.iv_profile)
        val tvProfileIcon: TextView = itemView.findViewById(R.id.tv_profile_icon)

        // Online indicator (green dot)
        val onlineIndicator: View = itemView.findViewById(R.id.online_indicator)

        // Text views
        val tvUsername: TextView = itemView.findViewById(R.id.tv_username)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tv_last_message)
        val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        val tvUnread: TextView = itemView.findViewById(R.id.tv_unread)

        // Status indicators
        val redPing: View = itemView.findViewById(R.id.red_ping)

        // Request tab buttons
        val buttonContainer: LinearLayout = itemView.findViewById(R.id.button_container)
        val btnAccept: Button = itemView.findViewById(R.id.btn_accept)
        val btnReject: Button = itemView.findViewById(R.id.btn_reject)
        val requestBadge: TextView = itemView.findViewById(R.id.request_badge)
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

        // ===== PROFILE IMAGE - CIRCULAR for ALL =====
        setupCircularProfileImage(holder, user)

        // Show online status if user is online (green dot on profile)
        if (onlineStatus[otherUserId] == true) {
            holder.onlineIndicator.visibility = View.VISIBLE
        } else {
            holder.onlineIndicator.visibility = View.GONE
        }

        // Handle different states based on tab type
        if (isRequestTab) {
            // REQUEST TAB - Show accept/reject buttons
            holder.buttonContainer.visibility = View.VISIBLE  // 🔥 SHOW BUTTON CONTAINER
            holder.btnAccept.visibility = View.VISIBLE
            holder.btnReject.visibility = View.VISIBLE
            holder.redPing.visibility = View.GONE
            holder.tvUnread.visibility = View.GONE
            holder.requestBadge.visibility = View.VISIBLE
            holder.requestBadge.text = "REQUEST"

            // Set last message text
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
                    else -> "Wants to message you"
                }
                holder.tvLastMessage.text = "📨 $messageText"
            } else {
                holder.tvLastMessage.text = "📨 Wants to message you"
            }

            // Show pending count if available
            if (conversation.pendingCount != null && conversation.pendingCount > 0) {
                holder.requestBadge.text = "${conversation.pendingCount} REQUEST${if (conversation.pendingCount > 1) "S" else ""}"
                holder.requestBadge.visibility = View.VISIBLE
            } else {
                holder.requestBadge.visibility = View.GONE
            }

            // Accept button click
            holder.btnAccept.setOnClickListener {
                onAcceptRequest(conversation)
            }

            // Reject button click
            holder.btnReject.setOnClickListener {
                onRejectRequest(conversation)
            }

        } else {
            // MESSAGES TAB - Hide accept/reject buttons, show message details
            holder.buttonContainer.visibility = View.GONE  // 🔥 HIDE BUTTON CONTAINER
            holder.btnAccept.visibility = View.GONE
            holder.btnReject.visibility = View.GONE
            holder.requestBadge.visibility = View.GONE

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
        }

        // Regular click listener - different behavior for requests vs messages
        holder.itemView.setOnClickListener {
            if (isRequestTab) {
                // For requests, clicking opens the user profile
                onUserClick(user)
            } else {
                // For messages, clicking opens the chat
                onUserClick(user)
            }
        }

        // Long click listener for both tabs
        holder.itemView.setOnLongClickListener {
            onLongClick.onItemLongClick(conversation, user)
            true
        }
    }

    // Helper function to setup circular profile image (WITHOUT RequestListener)
    private fun setupCircularProfileImage(holder: InboxViewHolder, user: ApiUser) {
        if (!user.profileImageUrl.isNullOrEmpty()) {
            val fullImageUrl = if (user.profileImageUrl.startsWith("http")) {
                user.profileImageUrl
            } else {
                "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${user.profileImageUrl}"
            }

            // Show image view, hide text icon
            holder.ivProfile.visibility = View.VISIBLE
            holder.tvProfileIcon.visibility = View.GONE

            // Clear any previous image
            holder.ivProfile.setImageDrawable(null)

            // Load image with CircleCrop transform
            try {
                val requestOptions = RequestOptions()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .transform(CircleCrop()) // This makes it circular!

                Glide.with(holder.itemView.context)
                    .load(fullImageUrl)
                    .apply(requestOptions)
                    .into(holder.ivProfile)

            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to text icon if Glide fails
                holder.ivProfile.visibility = View.GONE
                holder.tvProfileIcon.visibility = View.VISIBLE
                showCircularTextIcon(holder, user)
            }
        } else {
            // No profile image - show circular text icon
            holder.ivProfile.visibility = View.GONE
            holder.tvProfileIcon.visibility = View.VISIBLE
            showCircularTextIcon(holder, user)
        }
    }

    // Helper function to show CIRCULAR text icon with first letter
    private fun showCircularTextIcon(holder: InboxViewHolder, user: ApiUser) {
        val firstLetter = if (user.username.isNotEmpty()) {
            user.username.first().toString().uppercase()
        } else {
            "?"
        }
        holder.tvProfileIcon.text = firstLetter

        // Get color based on user ID
        val colorIndex = Math.abs(user.firebaseUid.hashCode()) % colors.size
        val bgColor = Color.parseColor(colors[colorIndex])

        // Set the circular drawable as background
        val drawable = ContextCompat.getDrawable(holder.itemView.context, R.drawable.circle_solid_profile)
        drawable?.mutate()
        drawable?.setTint(bgColor)

        // Apply the circular drawable to TextView
        holder.tvProfileIcon.background = drawable
        holder.tvProfileIcon.setTextColor(Color.WHITE)
    }

    override fun getItemCount() = conversations.size

    // Function to update online status
    fun updateOnlineStatus(userId: String, isOnline: Boolean) {
        onlineStatus[userId] = isOnline
        notifyDataSetChanged()
    }

    // Function to update user data (for real-time profile changes)
    fun updateUser(userId: String, updatedUser: ApiUser) {
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