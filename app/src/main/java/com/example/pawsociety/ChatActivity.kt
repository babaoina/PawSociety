package com.example.pawsociety

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import com.example.pawsociety.api.ConversationsResponse
import com.example.pawsociety.data.repository.BlockRepository
import com.example.pawsociety.data.repository.ChatRepository
import com.example.pawsociety.data.repository.ReportRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// DataStore imports
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "chat_preferences")

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

    // Mute feature
    private var isMuted = false
    private var muteCheckJob: Job? = null

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
        checkIfBlocked()

        // Connect to socket and load messages
        setupSocket()
        loadMessages()

        // Load saved mute status
        loadMuteStatus()
    }

    private fun checkIfBlocked() {
        lifecycleScope.launch {
            val currentUser = sessionManager.getCurrentUser() ?: return@launch

            // Check if current user blocked the receiver
            val blockedByMeResult = blockRepository.checkBlockStatus(currentUser.firebaseUid, receiverUid)
            val blockedByMe = blockedByMeResult.isSuccess && blockedByMeResult.getOrNull() == true

            // Check if receiver blocked current user
            val blockedMeResult = blockRepository.checkBlockStatus(receiverUid, currentUser.firebaseUid)
            val blockedMe = blockedMeResult.isSuccess && blockedMeResult.getOrNull() == true

            if (blockedByMe || blockedMe) {
                runOnUiThread {
                    Toast.makeText(
                        this@ChatActivity,
                        "This conversation is no longer available",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
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
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_chat_settings, null)

            // Initialize views - FIX: Use findViewById correctly
            val switchMute = dialogView.findViewById<Switch>(R.id.switch_mute)
            val profileIcon = dialogView.findViewById<TextView>(R.id.dialog_profile_icon)
            val username = dialogView.findViewById<TextView>(R.id.dialog_username)
            val btnClose = dialogView.findViewById<TextView>(R.id.btn_close_dialog)
            val optionViewProfile = dialogView.findViewById<LinearLayout>(R.id.option_view_profile)
            val optionMute = dialogView.findViewById<LinearLayout>(R.id.option_mute)
            val optionBlock = dialogView.findViewById<LinearLayout>(R.id.option_block)
            val optionReport = dialogView.findViewById<LinearLayout>(R.id.option_report)
            val optionClearChat = dialogView.findViewById<LinearLayout>(R.id.option_clear_chat)

            // Set user info
            username.text = receiverUsername
            profileIcon.text = receiverUsername.firstOrNull()?.uppercase() ?: "?"

            // Load the mute state
            lifecycleScope.launch {
                val muteKey = booleanPreferencesKey("mute_$receiverUid")
                val prefs = dataStore.data.first()
                isMuted = prefs[muteKey] ?: false

                runOnUiThread {
                    switchMute.isChecked = isMuted
                }
            }

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnClose.setOnClickListener { dialog.dismiss() }

            optionViewProfile.setOnClickListener {
                dialog.dismiss()
                openUserProfile()
            }

            // Entire row click toggles the switch
            optionMute.setOnClickListener {
                switchMute.isChecked = !switchMute.isChecked
            }

            // Mute switch listener
            switchMute.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != isMuted) {
                    toggleMuteWithSwitch(isChecked)
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

        } catch (e: Exception) {
            println("❌ Error in showChatSettingsDialog: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Settings not available", Toast.LENGTH_SHORT).show()
        }
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
            .setMessage("Are you sure you want to clear this conversation? This will only clear messages for you.")
            .setPositiveButton("Clear") { _, _ ->
                clearChat()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearChat() {
        if (currentChatId.isEmpty()) {
            Toast.makeText(this, "No chat to clear", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@ChatActivity, "Clearing chat...", Toast.LENGTH_SHORT).show()

                val result = chatRepository.clearChat(currentChatId, currentUserUid)

                if (result.isSuccess) {
                    messageList.clear()
                    messageAdapter.notifyDataSetChanged()
                    Toast.makeText(this@ChatActivity, "Chat cleared (for you only)", Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to clear chat"
                    Toast.makeText(this@ChatActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== SOCKET SETUP ====================
    private fun setupSocket() {
        SocketManager.connect()

        if (currentUserUid.isNotEmpty()) {
            SocketManager.joinUserRoom(currentUserUid)
        }

        SocketManager.on("new-message") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val message = parseMessageFromJson(data)

                        // Check mute status before showing notification
                        if (isMuted && message.receiverUid == currentUserUid) {
                            Log.d(tag, "📵 Message received but user is muted - no notification")
                            // Still add to chat list but don't show notification
                        }

                        runOnUiThread {
                            addNewMessage(message)
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

        SocketManager.on("chat-cleared") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val chatId = data.optString("chatId", "")
                        if (chatId == currentChatId) {
                            runOnUiThread {
                                messageList.clear()
                                messageAdapter.notifyDataSetChanged()
                                Toast.makeText(this@ChatActivity, "Chat was cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing chat cleared: ${e.message}")
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
        if (message.chatId == currentChatId ||
            (message.senderUid == receiverUid && message.receiverUid == currentUserUid) ||
            (message.senderUid == currentUserUid && message.receiverUid == receiverUid)
        ) {

            val exists = messageList.any { it.messageId == message.messageId }
            if (!exists) {
                messageList.add(message)
                messageList.sortBy { it.createdAt }
                messageAdapter.notifyDataSetChanged()
                recyclerView.scrollToPosition(messageList.size - 1)

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

    private fun markMessagesAsRead() {
        lifecycleScope.launch {
            try {
                val unreadMessages = messageList.filter {
                    it.receiverUid == currentUserUid && !it.isRead
                }

                if (unreadMessages.isEmpty()) return@launch

                Log.d(tag, "Marking ${unreadMessages.size} messages as read")

                for (message in unreadMessages) {
                    chatRepository.markMessageAsRead(message.messageId)

                    val index = messageList.indexOfFirst { it.messageId == message.messageId }
                    if (index >= 0) {
                        val updatedMessage = message.copy(isRead = true)
                        messageList[index] = updatedMessage
                    }
                }

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

        if (isTyping) {
            isTyping = false
            SocketManager.sendTypingStatus(currentChatId, currentUserUid, false)
        }

        lifecycleScope.launch {
            try {
                val blockCheckResult = blockRepository.checkBlockStatus(receiverUid, currentUser.firebaseUid)
                val isBlocked = blockCheckResult.isSuccess && blockCheckResult.getOrNull() == true

                if (isBlocked) {
                    Toast.makeText(
                        this@ChatActivity,
                        "You cannot message this user",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }

                val result = chatRepository.sendMessage(
                    senderUid = currentUser.firebaseUid,
                    receiverUid = receiverUid,
                    text = text
                )

                if (result.isSuccess) {
                    val message = result.getOrNull()
                    if (message != null) {
                        messageInput.text.clear()

                        if (currentChatId.isEmpty()) {
                            currentChatId = message.chatId
                            SocketManager.joinChatRoom(currentChatId)
                            setResult(RESULT_OK) // Notify InboxActivity
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

    private fun loadMessages() {
        val currentUser = sessionManager.getCurrentUser()
        if (currentUser == null) {
            return
        }

        lifecycleScope.launch {
            try {
                val conversationsResult = chatRepository.getConversations(currentUser.firebaseUid)

                if (conversationsResult.isSuccess) {
                    val response = conversationsResult.getOrNull()

                    // Search in both messages and requests for the conversation
                    val existingChat = response?.messages?.find { conv ->
                        conv.participants.contains(receiverUid)
                    } ?: response?.requests?.find { conv ->
                        conv.participants.contains(receiverUid)
                    }

                    if (existingChat != null) {
                        currentChatId = existingChat.chatId
                        SocketManager.joinChatRoom(currentChatId)
                        loadMessagesForChat(currentChatId)
                    } else {
                        messageList.clear()
                        messageAdapter.notifyDataSetChanged()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadMessagesForChat(chatId: String) {
        lifecycleScope.launch {
            try {
                val result = chatRepository.getMessages(chatId, currentUserUid, 100, 0)

                if (result.isSuccess) {
                    val messages = result.getOrNull() ?: emptyList()
                    messageList.clear()
                    messageList.addAll(messages)
                    messageList.sortBy { it.createdAt }
                    messageAdapter.notifyDataSetChanged()

                    if (messageList.isNotEmpty()) {
                        recyclerView.scrollToPosition(messageList.size - 1)
                    }

                    markMessagesAsRead()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SocketManager.connect()
        if (currentUserUid.isNotEmpty()) {
            SocketManager.joinUserRoom(currentUserUid)
        }
        if (currentChatId.isNotEmpty()) {
            SocketManager.joinChatRoom(currentChatId)
            loadMessagesForChat(currentChatId)
            markMessagesAsRead()
        }
    }

    override fun onPause() {
        super.onPause()
        if (currentChatId.isNotEmpty()) {
            SocketManager.leaveChatRoom(currentChatId)
        }
        if (isTyping) {
            isTyping = false
            SocketManager.sendTypingStatus(currentChatId, currentUserUid, false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.off("new-message")
        SocketManager.off("message-sent")
        SocketManager.off("user-typing")
        SocketManager.off("message-read")
        SocketManager.off("chat-cleared")
        typingHandler.removeCallbacksAndMessages(null)

        muteCheckJob?.cancel()
    }

    // ===== MUTE FEATURE IMPLEMENTATION WITH DATASTORE =====

    private fun loadMuteStatus() {
        if (receiverUid.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val muteKey = booleanPreferencesKey("mute_${receiverUid}")
                    val prefs = dataStore.data.first()
                    isMuted = prefs[muteKey] ?: false
                    Log.d(tag, "📖 Loaded local mute status for $receiverUsername: $isMuted")

                    // Sync with API to ensure local matches server
                    syncMuteStatusWithApi()
                } catch (e: Exception) {
                    Log.e(tag, "❌ Failed to load mute status: ${e.message}")
                }
            }
        }
    }

    private fun syncMuteStatusWithApi() {
        lifecycleScope.launch {
            try {
                val result = chatRepository.getMutedUsers(currentUserUid)
                if (result.isSuccess) {
                    val mutedUsers = result.getOrNull() ?: emptyList()
                    val apiMuted = mutedUsers.any { it.userId == receiverUid }

                    Log.d(tag, "🌐 API mute status for $receiverUsername: $apiMuted")

                    isMuted = apiMuted
                    val muteKey = booleanPreferencesKey("mute_${receiverUid}")
                    dataStore.edit { settings ->
                        settings[muteKey] = apiMuted
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Failed to sync mute status: ${e.message}")
            }
        }
    }

    private fun toggleMuteWithSwitch(isChecked: Boolean) {
        isMuted = isChecked
        lifecycleScope.launch {
            try {
                val muteKey = booleanPreferencesKey("mute_$receiverUid")
                dataStore.edit { settings ->
                    settings[muteKey] = isChecked
                }

                val result = if (isChecked) {
                    chatRepository.muteUser(currentUserUid, receiverUid)
                } else {
                    chatRepository.unmuteUser(currentUserUid, receiverUid)
                }

                if (result.isSuccess) {
                    Log.d(tag, "✅ Mute status synced with server")
                }

                runOnUiThread {
                    Toast.makeText(
                        this@ChatActivity,
                        if (isChecked) "Notifications muted" else "Notifications unmuted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error saving mute status", e)
            }
        }
    }
}