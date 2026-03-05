package com.example.pawsociety

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiMessage
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.BlockRepository
import com.example.pawsociety.data.repository.ChatRepository
import com.example.pawsociety.data.repository.ReportRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var tvUsername: TextView
    private lateinit var tvUserStatus: TextView
    private lateinit var typingIndicator: TextView
    private lateinit var headerProfileImage: ImageView
    private lateinit var headerProfileIcon: TextView
    private lateinit var btnChatSettings: ImageView

    private val messageList = mutableListOf<ApiMessage>()
    private lateinit var messageAdapter: ChatMessageAdapter

    private lateinit var sessionManager: SessionManager
    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()
    private val blockRepository = BlockRepository()
    private val reportRepository = ReportRepository()

    private var receiverUid: String = ""
    private var receiverUsername: String = ""
    private var receiverUser: ApiUser? = null
    private var currentChatId: String = ""
    private var currentUserUid: String = ""

    private val tag = "ChatActivity"
    private val typingHandler = Handler(Looper.getMainLooper())
    private var typingTimeout: Runnable? = null
    private var isTyping = false
    private var isMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sessionManager = SessionManager(this)

        // Get current user
        val currentUser = sessionManager.getCurrentUser()
        currentUserUid = currentUser?.firebaseUid ?: ""

        // Get receiver info from intent
        receiverUid = intent.getStringExtra("receiverUid") ?: ""
        receiverUsername = intent.getStringExtra("receiverUsername") ?: ""

        Log.d(tag, "💬 ChatActivity received - receiverUid: '$receiverUid', username: '$receiverUsername'")

        if (receiverUid.isEmpty()) {
            Log.e(tag, "❌ ChatActivity: receiverUid is empty!")
            Toast.makeText(this, "Invalid user - UID is empty", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Check if trying to chat with self
        if (currentUserUid == receiverUid) {
            Toast.makeText(this, "Cannot chat with yourself", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        loadReceiverUser()
        setupRecyclerView()
        setupClickListeners()

        // Connect to socket and load messages
        setupSocket()
        loadMessages()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.btn_send)
        tvUsername = findViewById(R.id.tv_chat_username)
        tvUserStatus = findViewById(R.id.tv_user_status)
        typingIndicator = findViewById(R.id.typing_indicator)
        headerProfileImage = findViewById(R.id.header_profile_image)
        headerProfileIcon = findViewById(R.id.header_profile_icon)
        btnChatSettings = findViewById(R.id.btn_chat_settings)

        tvUsername.text = receiverUsername
        tvUserStatus.text = "Active now"
        typingIndicator.visibility = View.GONE

        // Set up back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Set up typing detection
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (currentChatId.isNotEmpty()) {
                    val typing = s?.isNotEmpty() == true
                    if (typing != isTyping) {
                        isTyping = typing
                        SocketManager.sendTypingStatus(currentChatId, currentUserUid, isTyping)
                    }

                    // Reset timeout
                    typingTimeout?.let { typingHandler.removeCallbacks(it) }
                    if (isTyping) {
                        typingTimeout = Runnable {
                            isTyping = false
                            SocketManager.sendTypingStatus(currentChatId, currentUserUid, false)
                        }
                        typingHandler.postDelayed(typingTimeout!!, 3000)
                    }
                }
            }
        })
    }

    private fun loadReceiverUser() {
        lifecycleScope.launch {
            val result = userRepository.getUserByUid(receiverUid)
            if (result.isSuccess) {
                receiverUser = result.getOrNull()
                updateProfileHeader()
            }
        }
    }

    private fun updateProfileHeader() {
        receiverUser?.let { user ->
            tvUsername.text = user.username

            if (!user.profileImageUrl.isNullOrEmpty()) {
                val fullImageUrl = if (user.profileImageUrl.startsWith("http")) {
                    user.profileImageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${user.profileImageUrl}"
                }

                headerProfileImage.visibility = View.VISIBLE
                headerProfileIcon.visibility = View.GONE

                Glide.with(this)
                    .load(fullImageUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(headerProfileImage)
            } else {
                headerProfileImage.visibility = View.GONE
                headerProfileIcon.visibility = View.VISIBLE

                val firstLetter = if (user.username.isNotEmpty()) {
                    user.username.first().toString().uppercase()
                } else {
                    "?"
                }
                headerProfileIcon.text = firstLetter
            }
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = ChatMessageAdapter(messageList, currentUserUid)
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = messageAdapter
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            sendMessage()
        }

        // Username click - go to profile
        tvUsername.setOnClickListener {
            openUserProfile()
        }

        // Profile image click - go to profile
        headerProfileImage.setOnClickListener {
            openUserProfile()
        }

        headerProfileIcon.setOnClickListener {
            openUserProfile()
        }

        // Chat settings button
        btnChatSettings.setOnClickListener {
            showChatSettingsDialog()
        }
    }

    private fun openUserProfile() {
        if (receiverUid == currentUserUid) {
            startActivity(Intent(this, ProfileActivity::class.java))
        } else {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("userId", receiverUid)
            intent.putExtra("userName", receiverUsername)
            startActivity(intent)
        }
    }

    private fun showChatSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_chat_settings, null)

        val profileIcon = dialogView.findViewById<TextView>(R.id.dialog_profile_icon)
        val username = dialogView.findViewById<TextView>(R.id.dialog_username)
        val btnClose = dialogView.findViewById<TextView>(R.id.btn_close_dialog)
        val optionViewProfile = dialogView.findViewById<LinearLayout>(R.id.option_view_profile)
        val optionMute = dialogView.findViewById<LinearLayout>(R.id.option_mute)
        val switchMute = dialogView.findViewById<Switch>(R.id.switch_mute)
        val optionBlock = dialogView.findViewById<LinearLayout>(R.id.option_block)
        val optionReport = dialogView.findViewById<LinearLayout>(R.id.option_report)
        val optionClearChat = dialogView.findViewById<LinearLayout>(R.id.option_clear_chat)

        // Set user info
        username.text = receiverUsername
        val firstLetter = if (receiverUsername.isNotEmpty()) {
            receiverUsername.first().toString().uppercase()
        } else {
            "?"
        }
        profileIcon.text = firstLetter

        // Set mute state
        switchMute.isChecked = isMuted

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        optionViewProfile.setOnClickListener {
            dialog.dismiss()
            openUserProfile()
        }

        switchMute.setOnCheckedChangeListener { _, isChecked ->
            isMuted = isChecked
            if (isChecked) {
                Toast.makeText(this, "Notifications muted for $receiverUsername", Toast.LENGTH_SHORT).show()
                // TODO: Save mute preference to backend
            } else {
                Toast.makeText(this, "Notifications unmuted", Toast.LENGTH_SHORT).show()
                // TODO: Save mute preference to backend
            }
        }

        optionBlock.setOnClickListener {
            dialog.dismiss()
            showBlockConfirmation()
        }

        optionReport.setOnClickListener {
            dialog.dismiss()
            showReportDialog()
        }

        optionClearChat.setOnClickListener {
            dialog.dismiss()
            showClearChatConfirmation()
        }

        dialog.show()
    }

    private fun showBlockConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Block User")
            .setMessage("Are you sure you want to block $receiverUsername? You will no longer see their messages and they cannot contact you.")
            .setPositiveButton("Block") { _, _ ->
                blockUser()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun blockUser() {
        lifecycleScope.launch {
            val result = blockRepository.blockUser(currentUserUid, receiverUid)
            if (result.isSuccess) {
                Toast.makeText(this@ChatActivity, "Blocked $receiverUsername", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@ChatActivity, "Failed to block user", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showReportDialog() {
        val options = arrayOf("Spam", "Harassment", "Inappropriate", "Fake account", "Other")

        AlertDialog.Builder(this)
            .setTitle("Report $receiverUsername")
            .setItems(options) { _, which ->
                val reason = options[which]
                submitReport(reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitReport(reason: String) {
        lifecycleScope.launch {
            val result = reportRepository.createReport(
                reporterUid = currentUserUid,
                reason = reason.lowercase().replace(" ", "_"),
                reportedUid = receiverUid,
                description = "Reported from chat: $reason"
            )
            if (result.isSuccess) {
                Toast.makeText(this@ChatActivity, "Report submitted. Thank you!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@ChatActivity, "Failed to submit report", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showClearChatConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear Chat")
            .setMessage("Are you sure you want to clear this conversation? This cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                clearChat()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearChat() {
        // TODO: Implement clear chat functionality
        Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show()
        messageList.clear()
        messageAdapter.notifyDataSetChanged()
    }

    // ==================== SOCKET SETUP ====================
    private fun setupSocket() {
        // Connect to socket
        SocketManager.connect()

        // Join user's room
        if (currentUserUid.isNotEmpty()) {
            SocketManager.joinUserRoom(currentUserUid)
        }

        // Listen for new messages
        SocketManager.on("new-message") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val message = parseMessageFromJson(data)
                        runOnUiThread {
                            addNewMessage(message)
                            // If this message is for current user, mark as read
                            if (message.receiverUid == currentUserUid) {
                                markMessagesAsRead()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing new message: ${e.message}")
                }
            }
        }

        // Listen for message sent confirmation
        SocketManager.on("message-sent") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val message = parseMessageFromJson(data)
                        runOnUiThread {
                            updateOrAddMessage(message)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing message sent: ${e.message}")
                }
            }
        }

        // Listen for typing indicators
        SocketManager.on("user-typing") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val userId = data.optString("userId", "")
                        val typing = data.optBoolean("isTyping", false)

                        runOnUiThread {
                            if (userId == receiverUid) {
                                typingIndicator.visibility = if (typing) View.VISIBLE else View.GONE
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing typing indicator: ${e.message}")
                }
            }
        }

        // Listen for message read receipts
        SocketManager.on("message-read") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val messageId = data.optString("messageId", "")
                        runOnUiThread {
                            updateMessageReadStatus(messageId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing message read: ${e.message}")
                }
            }
        }
    }

    private fun parseMessageFromJson(json: JSONObject): ApiMessage {
        return ApiMessage(
            messageId = json.optString("messageId", ""),
            chatId = json.optString("chatId", ""),
            senderUid = json.optString("senderUid", ""),
            receiverUid = json.optString("receiverUid", ""),
            text = json.optString("text", ""),
            imageUrl = json.optString("imageUrl", ""),
            isRead = json.optBoolean("isRead", false),
            createdAt = json.optString("createdAt", "")
        )
    }

    private fun addNewMessage(message: ApiMessage) {
        // Only add if it belongs to this chat
        if (message.chatId == currentChatId ||
            (message.senderUid == receiverUid && message.receiverUid == currentUserUid) ||
            (message.senderUid == currentUserUid && message.receiverUid == receiverUid)) {

            // Check if message already exists
            val exists = messageList.any { it.messageId == message.messageId }
            if (!exists) {
                messageList.add(message)
                messageList.sortBy { it.createdAt }
                messageAdapter.notifyDataSetChanged()
                recyclerView.scrollToPosition(messageList.size - 1)

                // Mark as read if it's received
                if (message.receiverUid == currentUserUid && !message.isRead) {
                    markMessageAsRead(message.messageId)
                }
            }
        }
    }

    private fun updateOrAddMessage(message: ApiMessage) {
        val index = messageList.indexOfFirst { it.messageId == message.messageId }
        if (index >= 0) {
            messageList[index] = message
            messageAdapter.notifyItemChanged(index)
        } else {
            addNewMessage(message)
        }
    }

    private fun updateMessageReadStatus(messageId: String) {
        val index = messageList.indexOfFirst { it.messageId == messageId }
        if (index >= 0) {
            val message = messageList[index]
            val updatedMessage = message.copy(isRead = true)
            messageList[index] = updatedMessage
            messageAdapter.notifyItemChanged(index)
        }
    }

    // ==================== MARK MESSAGES AS READ ====================
    private fun markMessagesAsRead() {
        lifecycleScope.launch {
            try {
                // Get unread messages where current user is the receiver
                val unreadMessages = messageList.filter {
                    it.receiverUid == currentUserUid && !it.isRead
                }

                if (unreadMessages.isEmpty()) return@launch

                Log.d(tag, "Marking ${unreadMessages.size} messages as read")

                for (message in unreadMessages) {
                    // Mark as read on server
                    chatRepository.markMessageAsRead(message.messageId)

                    // Update local message
                    val index = messageList.indexOfFirst { it.messageId == message.messageId }
                    if (index >= 0) {
                        val updatedMessage = message.copy(isRead = true)
                        messageList[index] = updatedMessage
                    }
                }

                // Notify adapter
                messageAdapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Log.e(tag, "Error marking messages as read: ${e.message}")
            }
        }
    }

    private fun markMessageAsRead(messageId: String) {
        lifecycleScope.launch {
            try {
                chatRepository.markMessageAsRead(messageId)
            } catch (e: Exception) {
                Log.e(tag, "Error marking message as read: ${e.message}")
            }
        }
    }

    // ==================== SEND MESSAGE ====================
    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) {
            return
        }

        val currentUser = sessionManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Please login to send messages", Toast.LENGTH_SHORT).show()
            return
        }

        // Stop typing indicator
        if (isTyping) {
            isTyping = false
            SocketManager.sendTypingStatus(currentChatId, currentUserUid, false)
        }

        lifecycleScope.launch {
            try {
                val result = chatRepository.sendMessage(
                    senderUid = currentUser.firebaseUid,
                    receiverUid = receiverUid,
                    text = text
                )

                if (result.isSuccess) {
                    val message = result.getOrNull()
                    if (message != null) {
                        // Message will be added via socket
                        messageInput.text.clear()

                        // Update chat ID if needed
                        if (currentChatId.isEmpty()) {
                            currentChatId = message.chatId
                            // Join the chat room
                            SocketManager.joinChatRoom(currentChatId)
                        }
                    }
                } else {
                    Toast.makeText(this@ChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== LOAD MESSAGES ====================
    private fun loadMessages() {
        val currentUser = sessionManager.getCurrentUser()
        if (currentUser == null) {
            return
        }

        lifecycleScope.launch {
            try {
                // First, get or create chat
                val conversationsResult = chatRepository.getConversations(currentUser.firebaseUid)

                if (conversationsResult.isSuccess) {
                    val conversations = conversationsResult.getOrNull()
                    val existingChat = conversations?.find { conv ->
                        conv.participants.contains(receiverUid)
                    }

                    if (existingChat != null) {
                        currentChatId = existingChat.chatId
                        // Join the chat room
                        SocketManager.joinChatRoom(currentChatId)
                        loadMessagesForChat(currentChatId)
                    } else {
                        // No existing chat, will be created when first message is sent
                        messageList.clear()
                        messageAdapter.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMessagesForChat(chatId: String) {
        lifecycleScope.launch {
            try {
                val result = chatRepository.getMessages(chatId, limit = 100)

                if (result.isSuccess) {
                    val messages = result.getOrNull() ?: emptyList()
                    messageList.clear()
                    messageList.addAll(messages)
                    messageList.sortBy { it.createdAt }
                    messageAdapter.notifyDataSetChanged()

                    // Scroll to bottom
                    if (messageList.isNotEmpty()) {
                        recyclerView.scrollToPosition(messageList.size - 1)
                    }

                    // MARK ALL UNREAD MESSAGES AS READ
                    markMessagesAsRead()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== ACTIVITY LIFECYCLE ====================
    override fun onResume() {
        super.onResume()
        SocketManager.connect()
        if (currentUserUid.isNotEmpty()) {
            SocketManager.joinUserRoom(currentUserUid)
        }
        if (currentChatId.isNotEmpty()) {
            SocketManager.joinChatRoom(currentChatId)
            loadMessagesForChat(currentChatId)
            // Mark messages as read when returning to chat
            markMessagesAsRead()
        }
    }

    override fun onPause() {
        super.onPause()
        // Leave chat room when activity is paused
        if (currentChatId.isNotEmpty()) {
            SocketManager.leaveChatRoom(currentChatId)
        }
        // Stop typing indicator
        if (isTyping) {
            isTyping = false
            SocketManager.sendTypingStatus(currentChatId, currentUserUid, false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up socket listeners
        SocketManager.off("new-message")
        SocketManager.off("message-sent")
        SocketManager.off("user-typing")
        SocketManager.off("message-read")
        typingHandler.removeCallbacksAndMessages(null)
    }
}