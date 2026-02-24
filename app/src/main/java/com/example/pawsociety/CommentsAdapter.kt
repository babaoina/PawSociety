package com.example.pawsociety

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class CommentsAdapter(
    private val comments: List<Comment>,
    private val currentUserId: String,
    private val onLikeClick: (String) -> Unit
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userIcon: TextView = itemView.findViewById(R.id.comment_user_icon)
        val userName: TextView = itemView.findViewById(R.id.comment_user_name)
        val commentText: TextView = itemView.findViewById(R.id.comment_text)
        val commentTime: TextView = itemView.findViewById(R.id.comment_time)
        val likeButton: ImageView = itemView.findViewById(R.id.comment_like_button)
        val likeCount: TextView = itemView.findViewById(R.id.comment_like_count)
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
        holder.userIcon.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.circle_solid_profile)

        holder.userName.text = comment.userName
        holder.commentText.text = comment.text
        holder.commentTime.text = getTimeAgo(comment.createdAt)

        // Check if likes list contains currentUserId
        val isLiked = comment.likes.contains(currentUserId)

        holder.likeButton.setImageResource(R.drawable.heart)
        if (isLiked) {
            holder.likeButton.setColorFilter(Color.parseColor("#7A4F2B"))  // Brown
        } else {
            holder.likeButton.setColorFilter(Color.parseColor("#999999"))  // Gray
        }

        holder.likeCount.text = comment.likesCount.toString()

        holder.likeButton.setOnClickListener {
            onLikeClick(comment.commentId)
        }
    }

    override fun getItemCount() = comments.size

    private fun getTimeAgo(dateString: String): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
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
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}