package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.pawsociety.api.ApiConversation
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.ChatRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import kotlinx.coroutines.launch

class InboxActivity : BaseNavigationActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inboxAdapter: InboxAdapter
    private lateinit var emptyState: LinearLayout
    private lateinit var tvUsername: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var sessionManager: SessionManager

    private var conversations = listOf<ApiConversation>()
    private var usersMap = mutableMapOf<String, ApiUser>()
    private var currentUser: ApiUser? = null
    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inbox)

        sessionManager = SessionManager(this)

        currentUser = sessionManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Please login to view inbox", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        setupOnlineStatusListener()
        loadConversations()

        // Join user room for online status
        SocketManager.connect()
        currentUser?.let { user ->
            SocketManager.joinUserRoom(user.firebaseUid)
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.inbox_recycler_view)
        emptyState = findViewById(R.id.empty_state)
        tvUsername = findViewById(R.id.tv_username)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)

        tvUsername.text = currentUser?.username ?: "username"

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setColorSchemeColors(
            android.graphics.Color.parseColor("#7A4F2B"),
            android.graphics.Color.parseColor("#FF6B35"),
            android.graphics.Color.parseColor("#4CAF50")
        )
        swipeRefreshLayout.setOnRefreshListener {
            loadConversations()
        }
    }

    private fun setupClickListeners() {
        val searchInput = findViewById<EditText>(R.id.search_input)
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.text.toString())
                true
            } else {
                false
            }
        }

        findViewById<ImageButton>(R.id.btn_new_message).setOnClickListener {
            Toast.makeText(this, "Start a new chat from a user's profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupOnlineStatusListener() {
        SocketManager.addOnlineStatusListener { userId, isOnline ->
            runOnUiThread {
                if (::inboxAdapter.isInitialized) {
                    inboxAdapter.updateOnlineStatus(userId, isOnline)
                }
            }
        }
        println("👂 Online status listener setup")
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            if (::inboxAdapter.isInitialized) {
                inboxAdapter.notifyDataSetChanged()
            }
        } else {
            val filteredConversations = conversations.filter { conv ->
                val otherUserId = conv.participants.find { it != currentUser?.firebaseUid }
                val user = otherUserId?.let { usersMap[it] }
                user?.username?.contains(query, ignoreCase = true) == true
            }

            if (filteredConversations.isEmpty()) {
                Toast.makeText(this, "No conversations match '$query'", Toast.LENGTH_SHORT).show()
            } else {
                updateAdapter(filteredConversations)
            }
        }
    }

    private fun loadConversations() {
        println("📬 Loading conversations...")
        val currentUser = sessionManager.getCurrentUser() ?: return

        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            try {
                val result = chatRepository.getConversations(currentUser.firebaseUid)

                if (result.isSuccess) {
                    conversations = result.getOrNull()!!
                    println("✅ Loaded ${conversations.size} conversations")

                    if (conversations.isEmpty()) {
                        showEmptyState()
                    } else {
                        loadUsersForConversations()
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to load conversations"
                    println("❌ Failed to load conversations: $errorMsg")
                    Toast.makeText(this@InboxActivity, "Failed to load conversations", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                }
            } catch (e: Exception) {
                println("❌ Error loading conversations: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@InboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showEmptyState()
            } finally {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private suspend fun loadUsersForConversations() {
        usersMap.clear()

        for (conv in conversations) {
            val otherUserId = conv.participants.find { it != currentUser?.firebaseUid } ?: continue
            if (!usersMap.containsKey(otherUserId)) {
                val userResult = userRepository.getUserByUid(otherUserId)
                if (userResult.isSuccess) {
                    userResult.getOrNull()?.let { usersMap[otherUserId] = it }
                }
            }
        }

        if (usersMap.isEmpty()) {
            showEmptyState()
        } else {
            showConversationList()
            updateAdapter(conversations)
        }
    }

    private fun updateAdapter(conversations: List<ApiConversation>) {
        inboxAdapter = InboxAdapter(
            conversations = conversations,
            usersMap = usersMap,
            currentUserId = currentUser?.firebaseUid ?: "",
            onUserClick = { user ->
                openConversation(user)
            }
        )
        recyclerView.adapter = inboxAdapter
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE

        val emptyText = findViewById<TextView>(R.id.empty_text)
        emptyText.text = "No conversations yet\n\nStart chatting from a user's profile!"
    }

    private fun setupProfileUpdateListener() {
        SocketManager.onUserProfileUpdated { userId ->
            runOnUiThread {
                // Reload user data for this userId
                loadUserData(userId)
            }
        }
    }

    private fun loadUserData(userId: String) {
        lifecycleScope.launch {
            val result = userRepository.getUserByUid(userId)
            if (result.isSuccess) {
                val updatedUser = result.getOrNull()
                updatedUser?.let {
                    usersMap[userId] = it
                    // Update adapter
                    if (::inboxAdapter.isInitialized) {
                        inboxAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun showConversationList() {
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun openConversation(user: ApiUser) {
        println("📬 Opening chat with user: ${user.username}, UID: ${user.firebaseUid}")
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("receiverUid", user.firebaseUid)
        intent.putExtra("receiverUsername", user.username)
        intent.putExtra("receiverProfileImage", user.profileImageUrl ?: "")
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't disconnect completely, other activities might need socket
    }
}