package com.example.pawsociety

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
import java.util.concurrent.TimeUnit

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
    private lateinit var typingIndicator: LinearLayout
    private lateinit var onlineIndicator: View
    private lateinit var headerProfileImage: ImageView
    private lateinit var headerProfileIcon: TextView
    private lateinit var btnChatSettings: ImageView
    private lateinit var messageContainer: LinearLayout
    private lateinit var typingDot1: View
    private lateinit var typingDot2: View
    private lateinit var typingDot3: View
    private val typingAnimators = mutableListOf<AnimatorSet>()

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

    private var isReceiverOnline = false
    private var lastSeenTime: Date? = null
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null

    private var isMuted = false
    private var muteCheckJob: Job? = null
    private var isMuteToggleInFlight = false

    private var isInForeground = false

    // Auto-refresh variables
    private var refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

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
        adjustKeyboardBehavior() // 🔥 ADD THIS - Only pushes message input, not header
        loadReceiverUser()
        setupRecyclerView()
        setupClickListeners()
        checkIfBlocked()
        setupOnlineStatusListener()

        // Connect to socket and load messages
        setupSocket()
        loadMessages()

        // Load saved mute status
        loadMuteStatus()
    }

    private fun adjustKeyboardBehavior() {
        // Set window to resize when keyboard opens
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Find the message input container
        messageContainer = findViewById(R.id.message_input_container)

        // Add insets listener to only adjust the message input container
        ViewCompat.setOnApplyWindowInsetsListener(messageContainer) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Only add padding for keyboard (IME) and navigation bar
            val bottomPadding = if (imeInsets.bottom > 0) {
                // Keyboard is open - add padding above keyboard
                imeInsets.bottom + 8
            } else {
                // Keyboard is closed - just add navigation bar padding
                navBars.bottom + 8
            }

            view.updatePadding(bottom = bottomPadding)

            // Also adjust recycler view padding when keyboard opens
            val recyclerViewPadding = if (imeInsets.bottom > 0) {
                imeInsets.bottom + 12
            } else {
                navBars.bottom + 12
            }
            recyclerView.updatePadding(bottom = recyclerViewPadding)

            insets
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.btn_send)
        tvUsername = findViewById(R.id.tv_chat_username)
        tvUserStatus = findViewById(R.id.tv_user_status)
        typingIndicator = findViewById(R.id.typing_indicator)
        onlineIndicator = findViewById(R.id.online_indicator)
        headerProfileImage = findViewById(R.id.header_profile_image)
        headerProfileIcon = findViewById(R.id.header_profile_icon)
        btnChatSettings = findViewById(R.id.btn_chat_settings)
        typingDot1 = findViewById(R.id.typing_dot_1)
        typingDot2 = findViewById(R.id.typing_dot_2)
        typingDot3 = findViewById(R.id.typing_dot_3)

        tvUsername.text = receiverUsername
        tvUserStatus.text = "Active now"
        onlineIndicator.visibility = View.GONE
        typingIndicator.visibility = View.GONE

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

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

    private fun setupOnlineStatusListener() {
        SocketManager.addOnlineStatusListener { userId, isOnline ->
            if (userId == receiverUid) {
                runOnUiThread {
                    isReceiverOnline = isOnline
                    updateOnlineStatus()
                }
            }
        }
    }

    private fun updateOnlineStatus() {
        if (isReceiverOnline) {
            onlineIndicator.visibility = View.VISIBLE
            if (typingIndicator.visibility != View.VISIBLE) {
                tvUserStatus.text = "Active now"
                tvUserStatus.setTextColor(Color.parseColor("#D7F5D2"))
            }
            statusRunnable?.let { statusHandler.removeCallbacks(it) }
        } else {
            onlineIndicator.visibility = View.GONE
            if (lastSeenTime != null) {
                updateLastSeenText()
                statusRunnable = object : Runnable {
                    override fun run() {
                        updateLastSeenText()
                        statusHandler.postDelayed(this, 60000)
                    }
                }
                statusHandler.post(statusRunnable!!)
            } else {
                tvUserStatus.text = "Offline"
                tvUserStatus.setTextColor(Color.parseColor("#999999"))
            }
        }
    }

    private fun updateLastSeenText() {
        val now = Date()
        val diff = now.time - (lastSeenTime?.time ?: now.time)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        tvUserStatus.text = when {
            minutes < 1 -> "Active just now"
            minutes < 60 -> "Active $minutes min ago"
            hours < 24 -> "Active $hours hr ago"
            days == 1L -> "Active yesterday"
            else -> "Active ${days} days ago"
        }
        tvUserStatus.setTextColor(Color.parseColor("#F3E3D3"))
    }

    private fun checkIfBlocked() {
        lifecycleScope.launch {
            val currentUser = sessionManager.getCurrentUser() ?: return@launch

            val blockedByMeResult = blockRepository.checkBlockStatus(currentUser.firebaseUid, receiverUid)
            val blockedByMe = blockedByMeResult.isSuccess && blockedByMeResult.getOrNull() == true

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

        tvUsername.setOnClickListener {
            openUserProfile()
        }

        headerProfileImage.setOnClickListener {
            openUserProfile()
        }

        headerProfileIcon.setOnClickListener {
            openUserProfile()
        }

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

            val switchMute = dialogView.findViewById<Switch>(R.id.switch_mute)
            val profileIcon = dialogView.findViewById<TextView>(R.id.dialog_profile_icon)
            val username = dialogView.findViewById<TextView>(R.id.dialog_username)
            val btnClose = dialogView.findViewById<TextView>(R.id.btn_close_dialog)
            val optionViewProfile = dialogView.findViewById<LinearLayout>(R.id.option_view_profile)
            val optionMute = dialogView.findViewById<LinearLayout>(R.id.option_mute)
            val optionBlock = dialogView.findViewById<LinearLayout>(R.id.option_block)
            val optionReport = dialogView.findViewById<LinearLayout>(R.id.option_report)
            val optionClearChat = dialogView.findViewById<LinearLayout>(R.id.option_clear_chat)

            username.text = receiverUsername
            profileIcon.text = receiverUsername.firstOrNull()?.uppercase() ?: "?"

            lifecycleScope.launch {
                val muteKey = booleanPreferencesKey("mute_$receiverUid")
                val prefs = dataStore.data.first()
                isMuted = prefs[muteKey] ?: false

                runOnUiThread {
                    switchMute.isChecked = isMuted
                }
            }

            val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnClose.setOnClickListener { dialog.dismiss() }

            optionViewProfile.setOnClickListener {
                dialog.dismiss()
                openUserProfile()
            }

            optionMute.setOnClickListener {
                if (!isMuteToggleInFlight) {
                    switchMute.isChecked = !switchMute.isChecked
                }
            }

            switchMute.setOnCheckedChangeListener { _, isChecked ->
                if (!isMuteToggleInFlight && isChecked != isMuted) {
                    toggleMuteWithSwitch(isChecked, switchMute)
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
        AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
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

        AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
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
        AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
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

        // Listen for online status
        SocketManager.addOnlineStatusListener { userId, isOnline ->
            if (userId == receiverUid) {
                runOnUiThread {
                    isReceiverOnline = isOnline
                    updateOnlineStatus()
                }
            }
        }

        // Real-time message receiving with immediate UI update
        SocketManager.on("new-message") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val message = parseMessageFromJson(data)

                        // Only show if this message belongs to this chat
                        if (message.chatId == currentChatId ||
                            (message.senderUid == receiverUid && message.receiverUid == currentUserUid)) {

                            runOnUiThread {
                                // Check if message already exists
                                val exists = messageList.any { it.messageId == message.messageId }
                                if (!exists) {
                                    messageList.add(message)
                                    messageList.sortBy { it.createdAt }
                                    messageAdapter.notifyDataSetChanged()
                                    recyclerView.scrollToPosition(messageList.size - 1)

                                    // Mark as read if it's for current user
                                    if (message.receiverUid == currentUserUid && !message.isRead) {
                                        markMessageAsRead(message.messageId)
                                    }

                                    // Also refresh from server to be safe
                                    refreshMessagesFromServer()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing new message: ${e.message}")
                }
            }
        }

        // Handle chat-message event (direct to chat room)
        SocketManager.on("chat-message") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val message = parseMessageFromJson(data)

                        if (message.chatId == currentChatId) {
                            runOnUiThread {
                                val exists = messageList.any { it.messageId == message.messageId }
                                if (!exists) {
                                    messageList.add(message)
                                    messageList.sortBy { it.createdAt }
                                    messageAdapter.notifyDataSetChanged()
                                    recyclerView.scrollToPosition(messageList.size - 1)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing chat message: ${e.message}")
                }
            }
        }

        // Handle message sent confirmation
        SocketManager.on("message-sent") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val message = parseMessageFromJson(data)
                        runOnUiThread {
                            val index = messageList.indexOfFirst { it.messageId == message.messageId }
                            if (index >= 0) {
                                messageList[index] = message
                                messageAdapter.notifyItemChanged(index)
                            } else if (message.chatId == currentChatId) {
                                messageList.add(message)
                                messageList.sortBy { it.createdAt }
                                messageAdapter.notifyDataSetChanged()
                                recyclerView.scrollToPosition(messageList.size - 1)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing message sent: ${e.message}")
                }
            }
        }

        // Handle message read status
        SocketManager.on("message-read") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val messageId = data.optString("messageId", "")
                        runOnUiThread {
                            val index = messageList.indexOfFirst { it.messageId == messageId }
                            if (index >= 0) {
                                val updatedMessage = messageList[index].copy(isRead = true)
                                messageList[index] = updatedMessage
                                messageAdapter.notifyItemChanged(index)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing message read: ${e.message}")
                }
            }
        }

        // Handle typing indicator
        SocketManager.on("user-typing") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val userId = data.optString("userId", "")
                        val typing = data.optBoolean("isTyping", false)

                        runOnUiThread {
                            if (userId == receiverUid) {
                                showTypingIndicator(typing)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing typing indicator: ${e.message}")
                }
            }
        }

        // Handle chat cleared
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
            status = json.optString("status", "delivered"),
            createdAt = json.optString("createdAt", "")
        )
    }

    // Refresh messages from server every 3 seconds when in foreground
    private fun startAutoRefresh() {
        stopAutoRefresh()

        refreshRunnable = Runnable {
            if (isInForeground && currentChatId.isNotEmpty()) {
                refreshMessagesFromServer()
                refreshHandler.postDelayed(refreshRunnable!!, 3000)
            }
        }
        refreshHandler.postDelayed(refreshRunnable!!, 3000)
        Log.d(tag, "🔄 Auto-refresh started")
    }

    private fun stopAutoRefresh() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
        Log.d(tag, "🔄 Auto-refresh stopped")
    }

    // Force refresh messages from server
    private fun refreshMessagesFromServer() {
        if (currentChatId.isEmpty() || !isInForeground) return

        lifecycleScope.launch {
            try {
                val result = chatRepository.getMessages(currentChatId, currentUserUid, 50, 0)

                if (result.isSuccess) {
                    val serverMessages = result.getOrNull() ?: emptyList()

                    // Check if we have new messages
                    val currentIds = messageList.map { it.messageId }.toSet()
                    val newMessages = serverMessages.filter { !currentIds.contains(it.messageId) }

                    if (newMessages.isNotEmpty()) {
                        runOnUiThread {
                            messageList.clear()
                            messageList.addAll(serverMessages)
                            messageList.sortBy { it.createdAt }
                            messageAdapter.notifyDataSetChanged()
                            recyclerView.scrollToPosition(messageList.size - 1)
                            Log.d(tag, "✅ Added ${newMessages.size} new messages from server refresh")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error refreshing messages: ${e.message}")
            }
        }
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

        sendButton.isEnabled = false

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

                        // Add message to list immediately
                        messageList.add(message)
                        messageList.sortBy { it.createdAt }
                        messageAdapter.notifyDataSetChanged()
                        recyclerView.scrollToPosition(messageList.size - 1)

                        if (currentChatId.isEmpty()) {
                            currentChatId = message.chatId
                            SocketManager.joinChatRoom(currentChatId)
                        }

                        setResult(RESULT_OK)

                        if (message.status == "pending") {
                            Toast.makeText(this@ChatActivity,
                                "Message request sent", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this@ChatActivity, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                sendButton.isEnabled = true
            }
        }
    }

    private fun showTypingIndicator(show: Boolean) {
        if (show) {
            typingIndicator.visibility = View.VISIBLE
            tvUserStatus.text = "Typing..."
            tvUserStatus.setTextColor(Color.parseColor("#FFF1C9"))
            startTypingDotsAnimation()
        } else {
            typingIndicator.visibility = View.GONE
            stopTypingDotsAnimation()
            updateOnlineStatus()
        }
    }

    private fun startTypingDotsAnimation() {
        if (typingAnimators.isNotEmpty()) return

        listOf(typingDot1, typingDot2, typingDot3).forEachIndexed { index, dot ->
            dot.alpha = 0.35f
            dot.translationY = 0f

            val moveUp = ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -10f, 0f).apply {
                duration = 620
                repeatCount = ObjectAnimator.INFINITE
                startDelay = (index * 140).toLong()
                interpolator = AccelerateDecelerateInterpolator()
            }

            val fade = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.35f, 1f, 0.35f).apply {
                duration = 620
                repeatCount = ObjectAnimator.INFINITE
                startDelay = (index * 140).toLong()
                interpolator = AccelerateDecelerateInterpolator()
            }

            AnimatorSet().apply {
                playTogether(moveUp, fade)
                start()
                typingAnimators.add(this)
            }
        }
    }

    private fun stopTypingDotsAnimation() {
        typingAnimators.forEach { it.cancel() }
        typingAnimators.clear()

        listOf(typingDot1, typingDot2, typingDot3).forEach { dot ->
            dot.alpha = 0.35f
            dot.translationY = 0f
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

    // LIFECYCLE METHODS
    override fun onResume() {
        super.onResume()
        isInForeground = true
        SocketManager.connect()
        if (currentUserUid.isNotEmpty()) {
            SocketManager.joinUserRoom(currentUserUid)
        }
        if (currentChatId.isNotEmpty()) {
            SocketManager.joinChatRoom(currentChatId)
            loadMessagesForChat(currentChatId)
            markMessagesAsRead()
        }
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
        stopAutoRefresh()
        if (currentChatId.isNotEmpty()) {
            SocketManager.leaveChatRoom(currentChatId)
        }
        if (isTyping) {
            isTyping = false
            SocketManager.sendTypingStatus(currentChatId, currentUserUid, false)
        }
        statusRunnable?.let { statusHandler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
        SocketManager.off("new-message")
        SocketManager.off("chat-message")
        SocketManager.off("message-sent")
        SocketManager.off("user-typing")
        SocketManager.off("message-read")
        SocketManager.off("chat-cleared")
        SocketManager.removeOnlineStatusListener { _, _ -> }
        typingHandler.removeCallbacksAndMessages(null)
        statusRunnable?.let { statusHandler.removeCallbacks(it) }

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
                } else {
                    Log.w(tag, "⚠️ Skipping mute sync overwrite because API did not return a valid mute list")
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Failed to sync mute status: ${e.message}")
            }
        }
    }

    private fun toggleMuteWithSwitch(isChecked: Boolean, switchMute: Switch? = null) {
        if (isMuteToggleInFlight) return

        val previousMuteState = isMuted
        isMuted = isChecked
        isMuteToggleInFlight = true
        switchMute?.isEnabled = false

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
                } else {
                    throw result.exceptionOrNull() ?: Exception("Failed to update mute status")
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
                isMuted = previousMuteState

                val muteKey = booleanPreferencesKey("mute_$receiverUid")
                dataStore.edit { settings ->
                    settings[muteKey] = previousMuteState
                }

                runOnUiThread {
                    switchMute?.let {
                        it.setOnCheckedChangeListener(null)
                        it.isChecked = previousMuteState
                        it.setOnCheckedChangeListener { _, checked ->
                            if (!isMuteToggleInFlight && checked != isMuted) {
                                toggleMuteWithSwitch(checked, it)
                            }
                        }
                    }

                    Toast.makeText(
                        this@ChatActivity,
                        "Couldn't update mute right now",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                isMuteToggleInFlight = false
                runOnUiThread {
                    switchMute?.isEnabled = true
                }
            }
        }
    }
}
