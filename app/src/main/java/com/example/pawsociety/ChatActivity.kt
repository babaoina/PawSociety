package com.example.pawsociety

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pawsociety.api.ApiMessage
import com.example.pawsociety.data.repository.ChatRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var tvUsername: TextView
    
    private val messageList = mutableListOf<ApiMessage>()
    private lateinit var messageAdapter: ChatMessageAdapter
    
    private lateinit var sessionManager: SessionManager
    private val chatRepository = ChatRepository()
    
    private var receiverUid: String = ""
    private var receiverUsername: String = ""
    private var currentChatId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sessionManager = SessionManager(this)

        // Get receiver info from intent
        receiverUid = intent.getStringExtra("receiverUid") ?: ""
        receiverUsername = intent.getStringExtra("receiverUsername") ?: ""
        
        println("💬 ChatActivity received - receiverUid: '$receiverUid', username: '$receiverUsername'")
        
        if (receiverUid.isEmpty()) {
            println("❌ ChatActivity: receiverUid is empty!")
            Toast.makeText(this, "Invalid user - UID is empty", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Check if trying to chat with self
        val currentUser = sessionManager.getCurrentUser()
        if (currentUser?.firebaseUid == receiverUid) {
            Toast.makeText(this, "Cannot chat with yourself", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        loadMessages()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.chat_recycler_view)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.btn_send)
        tvUsername = findViewById(R.id.tv_chat_username)

        tvUsername.text = receiverUsername
        
        // Set up back button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = ChatMessageAdapter(messageList, sessionManager.getCurrentUser()?.firebaseUid ?: "")
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = messageAdapter
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            sendMessage()
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
                        // Add message to local list
                        messageList.add(message)
                        messageAdapter.notifyDataSetChanged()
                        
                        // Scroll to bottom
                        recyclerView.scrollToPosition(messageList.size - 1)
                        
                        // Clear input
                        messageInput.text.clear()
                        
                        // Update chat ID for future messages
                        if (currentChatId.isEmpty()) {
                            currentChatId = message.chatId
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
                // First, get or create chat
                val conversationsResult = chatRepository.getConversations(currentUser.firebaseUid)
                
                if (conversationsResult.isSuccess) {
                    val conversations = conversationsResult.getOrNull()
                    val existingChat = conversations?.find { conv ->
                        conv.participants.contains(receiverUid)
                    }

                    if (existingChat != null) {
                        currentChatId = existingChat.chatId
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
                    messageAdapter.notifyDataSetChanged()
                    
                    // Scroll to bottom
                    if (messageList.isNotEmpty()) {
                        recyclerView.scrollToPosition(messageList.size - 1)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentChatId.isNotEmpty()) {
            loadMessagesForChat(currentChatId)
        }
    }
}
