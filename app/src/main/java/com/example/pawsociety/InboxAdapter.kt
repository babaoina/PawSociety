package com.example.pawsociety

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pawsociety.api.ApiUser

class InboxAdapter(
    private val users: List<ApiUser>,
    private val onUserClick: (ApiUser) -> Unit
) : RecyclerView.Adapter<InboxAdapter.InboxViewHolder>() {

    private val colors = listOf(
        "#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#E91E63"
    )

    class InboxViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileIcon: TextView = itemView.findViewById(R.id.profile_icon)
        val username: TextView = itemView.findViewById(R.id.username)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InboxViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inbox_simple, parent, false)
        return InboxViewHolder(view)
    }

    override fun onBindViewHolder(holder: InboxViewHolder, position: Int) {
        val user = users[position]

        val firstLetter = if (user.username.isNotEmpty()) {
            user.username.first().toString().uppercase()
        } else {
            "?"
        }

        // Use username for color if firebaseUid is null
        val hashString = user.firebaseUid ?: user.username ?: "unknown"
        val colorIndex = Math.abs(hashString.hashCode()) % colors.size
        holder.profileIcon.text = firstLetter
        holder.profileIcon.setBackgroundColor(Color.parseColor(colors[colorIndex]))
        holder.profileIcon.setTextColor(Color.WHITE)

        holder.username.text = user.username

        holder.itemView.setOnClickListener {
            onUserClick(user)
        }
    }

    override fun getItemCount() = users.size
}