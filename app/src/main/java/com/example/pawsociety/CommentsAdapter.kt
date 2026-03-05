package com.example.pawsociety

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pawsociety.api.ApiComment
import java.text.SimpleDateFormat
import java.util.*

class CommentsAdapter(
    private val comments: List<ApiComment>,  // This should be ALL comments
    private val currentUserId: String,
    private val onLikeClick: (String) -> Unit,
    private val onOptionsClick: (ApiComment) -> Unit,
    private val onProfileClick: (String, String) -> Unit
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userIcon: TextView = itemView.findViewById(R.id.comment_user_icon)
        val userName: TextView = itemView.findViewById(R.id.comment_user_name)
        val commentText: TextView = itemView.findViewById(R.id.comment_text)
        val commentTime: TextView = itemView.findViewById(R.id.comment_time)
        val likeButton: ImageView = itemView.findViewById(R.id.comment_like_button)
        val likeCount: TextView = itemView.findViewById(R.id.comment_like_count)
        val btnMore: TextView = itemView.findViewById(R.id.btn_more_comment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]

        // Set user icon with first letter
        val firstLetter = if (comment.userName.isNotEmpty()) {
            comment.userName.first().toString().uppercase()
        } else {
            "?"
        }
        holder.userIcon.text = firstLetter

        // Generate color based on user ID
        val colors = listOf(
            "#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#E91E63"
        )
        val colorIndex = Math.abs(comment.firebaseUid.hashCode()) % colors.size
        holder.userIcon.setBackgroundColor(Color.parseColor(colors[colorIndex]))

        holder.userName.text = comment.userName
        holder.commentText.text = comment.text
        holder.commentTime.text = getTimeAgo(comment.createdAt)

        // Set like button state
        holder.likeButton.setImageResource(R.drawable.heart)
        if (comment.likesCount > 0) {
            holder.likeButton.setColorFilter(Color.parseColor("#7A4F2B"))
        } else {
            holder.likeButton.setColorFilter(Color.parseColor("#999999"))
        }
        holder.likeCount.text = comment.likesCount.toString()

        // Show more button only for user's own comments or always show for testing
        // For now, show it for all comments to test
        holder.btnMore.visibility = View.VISIBLE

        // Click listeners
        holder.likeButton.setOnClickListener {
            onLikeClick(comment.commentId)
        }

        holder.btnMore.setOnClickListener {
            onOptionsClick(comment)
        }

        // Profile click listeners
        holder.userIcon.setOnClickListener {
            onProfileClick(comment.firebaseUid, comment.userName)
        }

        holder.userName.setOnClickListener {
            onProfileClick(comment.firebaseUid, comment.userName)
        }
    }

    override fun getItemCount() = comments.size

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
                days > 0 -> "${days}d ago"
                hours > 0 -> "${hours}h ago"
                minutes > 0 -> "${minutes}m ago"
                else -> "Just now"
            }
        } catch (e: Exception) {
            ""
        }
    }
}