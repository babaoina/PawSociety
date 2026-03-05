package com.example.pawsociety

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pawsociety.api.ApiMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatMessageAdapter(
    private val messages: List<ApiMessage>,
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 0
    }

    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView = itemView.findViewById(R.id.text_message)
        val timeText: TextView = itemView.findViewById(R.id.time_text)
        val readStatusIcon: ImageView = itemView.findViewById(R.id.iv_read_status)
    }

    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView = itemView.findViewById(R.id.text_message)
        val timeText: TextView = itemView.findViewById(R.id.time_text)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderUid == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        when (holder) {
            is SentMessageViewHolder -> {
                holder.textMessage.text = message.text ?: ""
                holder.timeText.text = formatTime(message.createdAt)

                if (message.isRead) {
                    holder.readStatusIcon.visibility = View.VISIBLE
                    holder.readStatusIcon.setImageResource(R.drawable.ic_double_check)
                    holder.readStatusIcon.setColorFilter(Color.parseColor("#4CAF50"))
                } else {
                    holder.readStatusIcon.visibility = View.VISIBLE
                    holder.readStatusIcon.setImageResource(R.drawable.ic_check)
                    holder.readStatusIcon.setColorFilter(Color.parseColor("#999999"))
                }
            }
            is ReceivedMessageViewHolder -> {
                holder.textMessage.text = message.text ?: ""
                holder.timeText.text = formatTime(message.createdAt)
            }
        }
    }

    override fun getItemCount(): Int = messages.size

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

    fun updateMessageReadStatus(messageId: String) {
        val index = messages.indexOfFirst { it.messageId == messageId }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }
}