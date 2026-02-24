package com.example.pawsociety

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pawsociety.api.ApiMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatMessageAdapter(
    private val messages: List<ApiMessage>,
    private val currentUserId: String
) : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView = itemView.findViewById(R.id.text_message)
        val timeText: TextView = itemView.findViewById(R.id.time_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = if (viewType == MESSAGE_SENT) {
            LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
        } else {
            LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
        }
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.textMessage.text = message.text
        holder.timeText.text = formatTime(message.createdAt)
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderUid == currentUserId) {
            MESSAGE_SENT
        } else {
            MESSAGE_RECEIVED
        }
    }

    private fun formatTime(dateString: String): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(dateString)
            val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val MESSAGE_SENT = 1
        private const val MESSAGE_RECEIVED = 0
    }
}
