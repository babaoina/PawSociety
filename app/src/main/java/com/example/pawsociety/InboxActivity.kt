package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class InboxActivity : BaseNavigationActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var inboxAdapter: InboxAdapter
    private lateinit var emptyState: LinearLayout
    private lateinit var tvUsername: TextView
    private lateinit var sessionManager: SessionManager

    private var currentTab = "primary"
    private var allUsers = listOf<ApiUser>()
    private var currentUser: ApiUser? = null
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
        loadOtherUsers()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.inbox_recycler_view)
        emptyState = findViewById(R.id.empty_state)
        tvUsername = findViewById(R.id.tv_username)

        tvUsername.text = currentUser?.username ?: "username"

        recyclerView.layoutManager = LinearLayoutManager(this)
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
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            if (::inboxAdapter.isInitialized) {
                inboxAdapter.notifyDataSetChanged()
            }
        } else {
            val filteredUsers = allUsers.filter {
                it.username.contains(query, ignoreCase = true)
            }

            if (filteredUsers.isEmpty()) {
                Toast.makeText(this, "No users found matching '$query'", Toast.LENGTH_SHORT).show()
            } else {
                inboxAdapter = InboxAdapter(filteredUsers) { user ->
                    openConversation(user)
                }
                recyclerView.adapter = inboxAdapter
            }
        }
    }

    private fun loadOtherUsers() {
        println("📬 Loading users from API...")
        val currentUser = sessionManager.getCurrentUser()

        // Fetch users from API
        lifecycleScope.launch {
            try {
                val result = userRepository.getUsers(limit = 50)

                if (result.isSuccess) {
                    allUsers = result.getOrNull()!!
                    println("✅ Loaded ${allUsers.size} users from API")

                    // Filter out current user
                    if (currentUser != null) {
                        allUsers = allUsers.filter { it.firebaseUid != currentUser.firebaseUid }
                        println("👤 After filtering self: ${allUsers.size} users")
                    }

                    if (allUsers.isEmpty()) {
                        println("⚠️ No other users found")
                        showEmptyState()
                    } else {
                        showUserList()
                        setupAdapter(allUsers)
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to load users"
                    println("❌ Failed to load users: $errorMsg")
                    Toast.makeText(this@InboxActivity, "Failed to load users: $errorMsg", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                }
            } catch (e: Exception) {
                println("❌ Error loading users: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@InboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
        }
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE

        val emptyText = findViewById<TextView>(R.id.empty_text)
        emptyText.text = "No other users yet\n\nWhen other people register, they will appear here"
    }

    private fun showUserList() {
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun setupAdapter(users: List<ApiUser>) {
        inboxAdapter = InboxAdapter(users) { user ->
            openConversation(user)
        }
        recyclerView.adapter = inboxAdapter
    }

    private fun openConversation(user: ApiUser) {
        println("📬 Opening chat with user: ${user.username}, UID: ${user.firebaseUid}")
        // Start chat activity with selected user
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("receiverUid", user.firebaseUid)
        intent.putExtra("receiverUsername", user.username)
        intent.putExtra("receiverProfileImage", user.profileImageUrl ?: "")
        println("📬 Intent extras - receiverUid: ${user.firebaseUid}, username: ${user.username}")
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadOtherUsers()
    }
}