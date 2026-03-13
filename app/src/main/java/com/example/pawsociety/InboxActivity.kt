package com.example.pawsociety

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiConversation
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.api.ConversationsResponse
import com.example.pawsociety.data.repository.BlockRepository
import com.example.pawsociety.data.repository.ChatRepository
import com.example.pawsociety.data.repository.FollowRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import kotlinx.coroutines.launch

class InboxActivity : BaseNavigationActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var requestsRecyclerView: RecyclerView
    private lateinit var inboxAdapter: InboxAdapter
    private lateinit var requestsAdapter: InboxAdapter
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyText: TextView
    private lateinit var tvUsername: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var tabMessages: RelativeLayout
    private lateinit var tabRequests: RelativeLayout
    private lateinit var tabIndicator: View
    private lateinit var tvRequestsBadge: TextView

    private var messagesList = listOf<ApiConversation>()
    private var requestsList = listOf<ApiConversation>()
    private var usersMap = mutableMapOf<String, ApiUser>()
    private var currentUser: ApiUser? = null
    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()
    private val blockRepository = BlockRepository()
    private val followRepository = FollowRepository()

    private lateinit var friendsContainer: LinearLayout
    private lateinit var friendsCarousel: HorizontalScrollView
    private lateinit var tvMutualFriends: TextView
    private val friendsList = mutableListOf<ApiUser>()

    private var currentTab = "messages" // "messages" or "requests"

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
        requestsRecyclerView = findViewById(R.id.requests_recycler_view)
        emptyState = findViewById(R.id.empty_state)
        tvEmptyText = findViewById(R.id.empty_text)
        tvUsername = findViewById(R.id.tv_username)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)

        // FIX: These are RelativeLayout, not TextView
        tabMessages = findViewById(R.id.tab_messages)  // Remove "as TextView"
        tabRequests = findViewById(R.id.tab_requests)  // Remove "as TextView"

        tabIndicator = findViewById(R.id.tab_indicator)
        tvRequestsBadge = findViewById(R.id.tv_requests_badge)
        friendsContainer = findViewById(R.id.friends_container)
        friendsCarousel = findViewById(R.id.friends_carousel)
        tvMutualFriends = findViewById(R.id.tv_mutual_friends)

        tvUsername.text = currentUser?.username ?: "username"

        recyclerView.layoutManager = LinearLayoutManager(this)
        requestsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setColorSchemeColors(
            android.graphics.Color.parseColor("#7A4F2B"),
            android.graphics.Color.parseColor("#FF6B35"),
            android.graphics.Color.parseColor("#4CAF50")
        )
        swipeRefreshLayout.setOnRefreshListener {
            loadConversations()
        }

        // Set initial tab
        updateTabSelection()
    }

    private fun setupClickListeners() {
        // Search bar click - opens search activity
        val searchContainer = findViewById<LinearLayout>(R.id.search_container)
        searchContainer.setOnClickListener {
            val intent = Intent(this, SearchUsersActivity::class.java)
            startActivity(intent)
        }

        // New Message Button
        findViewById<ImageButton>(R.id.btn_new_message).setOnClickListener {
            val intent = Intent(this, SearchUsersActivity::class.java)
            startActivity(intent)
        }

        // Tab clicks
        tabMessages.setOnClickListener {
            if (currentTab != "messages") {
                currentTab = "messages"
                animateTabIndicator(0)
                updateTabColors()
                showCurrentTab()
            }
        }

        tabRequests.setOnClickListener {
            if (currentTab != "requests") {
                currentTab = "requests"
                animateTabIndicator(1)
                updateTabColors()
                showCurrentTab()
            }
        }
    }

    private fun loadMutualFriends() {
        lifecycleScope.launch {
            try {
                val currentUserUid = currentUser?.firebaseUid ?: return@launch

                // Get people YOU are following
                val followingResult = followRepository.getFollowing(currentUserUid)

                if (followingResult.isSuccess) {
                    val following = followingResult.getOrNull() ?: emptyList()

                    // For each person you follow, check if they follow you back
                    val mutualFriends = mutableListOf<ApiUser>()

                    for (user in following) {
                        val checkResult = followRepository.checkFollowStatus(user.firebaseUid, currentUserUid)
                        if (checkResult.isSuccess && checkResult.getOrNull() == true) {
                            // They follow you back - add to mutual friends
                            mutualFriends.add(user)
                        }
                    }

                    friendsList.clear()
                    friendsList.addAll(mutualFriends.take(10)) // Show first 10 mutual friends

                    if (friendsList.isNotEmpty()) {
                        tvMutualFriends.visibility = View.VISIBLE
                        tvMutualFriends.text = "Your friends" // Or "Mutual friends"
                        friendsCarousel.visibility = View.VISIBLE
                        populateFriendsCarousel()
                    } else {
                        tvMutualFriends.visibility = View.GONE
                        friendsCarousel.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun populateFriendsCarousel() {
        friendsContainer.removeAllViews()

        for (friend in friendsList) {
            val friendView = layoutInflater.inflate(R.layout.item_friend_carousel, friendsContainer, false)

            val friendIcon = friendView.findViewById<TextView>(R.id.friend_icon)
            val friendImage = friendView.findViewById<ImageView>(R.id.friend_image)
            val friendName = friendView.findViewById<TextView>(R.id.friend_name)

            friendName.text = friend.username

            // Load profile picture
            if (!friend.profileImageUrl.isNullOrEmpty()) {
                val fullImageUrl = if (friend.profileImageUrl.startsWith("http")) {
                    friend.profileImageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${friend.profileImageUrl}"
                }

                friendIcon.visibility = View.GONE
                friendImage.visibility = View.VISIBLE

                Glide.with(this)
                    .load(fullImageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.circle_solid_profile)
                    .error(R.drawable.circle_solid_profile)
                    .into(friendImage)
            } else {
                friendImage.visibility = View.GONE
                friendIcon.visibility = View.VISIBLE

                val firstLetter = if (friend.username.isNotEmpty()) {
                    friend.username.first().toString().uppercase()
                } else {
                    "?"
                }
                friendIcon.text = firstLetter

                val colors = listOf("#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800")
                val colorIndex = Math.abs(friend.firebaseUid.hashCode()) % colors.size
                friendIcon.setBackgroundColor(Color.parseColor(colors[colorIndex]))
            }

            friendView.setOnClickListener {
                openConversation(friend)
            }

            friendsContainer.addView(friendView)
        }
    }

    private fun animateTabIndicator(tabIndex: Int) {
        tabIndicator.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val targetX = (screenWidth / 2) * tabIndex

            ValueAnimator.ofFloat(tabIndicator.x, targetX.toFloat()).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    tabIndicator.x = animation.animatedValue as Float
                }
                start()
            }

            val targetWidth = screenWidth / 2
            val widthAnimator = ValueAnimator.ofInt(tabIndicator.width, targetWidth)
            widthAnimator.duration = 200
            widthAnimator.addUpdateListener { animation ->
                val params = tabIndicator.layoutParams
                params.width = animation.animatedValue as Int
                tabIndicator.layoutParams = params
            }
            widthAnimator.start()
        }
    }

    private fun updateTabColors() {
        val tvMessages = findViewById<TextView>(R.id.tv_messages)
        val tvRequests = findViewById<TextView>(R.id.tv_requests)

        if (currentTab == "messages") {
            tvMessages.setTextColor(Color.parseColor("#262626"))
            tvRequests.setTextColor(Color.parseColor("#8E8E8E"))
        } else {
            tvRequests.setTextColor(Color.parseColor("#262626"))
            tvMessages.setTextColor(Color.parseColor("#8E8E8E"))
        }
    }

    private fun showCurrentTab() {
        if (currentTab == "messages") {
            recyclerView.visibility = View.VISIBLE
            requestsRecyclerView.visibility = View.GONE
            updateEmptyState(messagesList.isEmpty())
        } else {
            recyclerView.visibility = View.GONE
            requestsRecyclerView.visibility = View.VISIBLE
            updateEmptyState(requestsList.isEmpty())
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            emptyState.visibility = View.VISIBLE
            tvEmptyText.text = if (currentTab == "messages") {
                "No messages yet"
            } else {
                "No requests yet"
            }
        } else {
            emptyState.visibility = View.GONE
        }
    }

    private fun updateRequestsBadge(count: Int) {
        if (count > 0) {
            tvRequestsBadge.visibility = View.VISIBLE
            tvRequestsBadge.text = count.toString()
        } else {
            tvRequestsBadge.visibility = View.GONE
        }
    }

    private fun setupOnlineStatusListener() {
        SocketManager.addOnlineStatusListener { userId, isOnline ->
            runOnUiThread {
                if (::inboxAdapter.isInitialized) {
                    inboxAdapter.updateOnlineStatus(userId, isOnline)
                }
                if (::requestsAdapter.isInitialized) {
                    requestsAdapter.updateOnlineStatus(userId, isOnline)
                }
            }
        }
        println("👂 Online status listener setup")
    }

    private fun loadConversations() {
        println("📬 Loading conversations...")
        val currentUser = sessionManager.getCurrentUser() ?: return

        swipeRefreshLayout.isRefreshing = true

        lifecycleScope.launch {
            try {
                val result = chatRepository.getConversations(currentUser.firebaseUid)

                if (result.isSuccess) {
                    val response = result.getOrNull()!!

                    // Handle the new response structure
                    messagesList = response.messages ?: emptyList()
                    requestsList = response.requests ?: emptyList()

                    println("✅ Loaded ${messagesList.size} messages and ${requestsList.size} requests")

                    // Update requests badge
                    updateRequestsBadge(requestsList.size)

                    if (messagesList.isEmpty() && requestsList.isEmpty()) {
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
                loadMutualFriends()

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

        // Load users for messages
        for (conv in messagesList) {
            val otherUserId = conv.participants.find { it != currentUser?.firebaseUid } ?: continue
            if (!usersMap.containsKey(otherUserId)) {
                val userResult = userRepository.getUserByUid(otherUserId)
                if (userResult.isSuccess) {
                    userResult.getOrNull()?.let { usersMap[otherUserId] = it }
                }
            }
        }

        // Load users for requests
        for (conv in requestsList) {
            val otherUserId = conv.participants.find { it != currentUser?.firebaseUid } ?: continue
            if (!usersMap.containsKey(otherUserId)) {
                val userResult = userRepository.getUserByUid(otherUserId)
                if (userResult.isSuccess) {
                    userResult.getOrNull()?.let { usersMap[otherUserId] = it }
                }
            }
        }

        if (usersMap.isEmpty() && messagesList.isEmpty() && requestsList.isEmpty()) {
            showEmptyState()
        } else {
            showConversationList()
            updateAdapters()
        }
    }

    private fun updateAdapters() {
        // Messages adapter
        inboxAdapter = InboxAdapter(
            conversations = messagesList,
            usersMap = usersMap,
            currentUserId = currentUser?.firebaseUid ?: "",
            isRequestTab = false,
            onUserClick = { user ->
                openConversation(user)
            },
            onAcceptRequest = { conversation ->
                acceptMessageRequest(conversation)
            },
            onRejectRequest = { conversation ->
                rejectMessageRequest(conversation)
            },
            onLongClick = object : OnItemLongClickListener {
                override fun onItemLongClick(conversation: ApiConversation, user: ApiUser) {
                    showConversationOptions(conversation, user)
                }
            }
        )
        recyclerView.adapter = inboxAdapter

        // Requests adapter
        requestsAdapter = InboxAdapter(
            conversations = requestsList,
            usersMap = usersMap,
            currentUserId = currentUser?.firebaseUid ?: "",
            isRequestTab = true,
            onUserClick = { user ->
                // For requests, clicking opens the user profile instead of chat
                openUserProfile(user)
            },
            onAcceptRequest = { conversation ->
                acceptMessageRequest(conversation)
            },
            onRejectRequest = { conversation ->
                rejectMessageRequest(conversation)
            },
            onLongClick = object : OnItemLongClickListener {
                override fun onItemLongClick(conversation: ApiConversation, user: ApiUser) {
                    showRequestOptions(conversation, user)
                }
            }
        )
        requestsRecyclerView.adapter = requestsAdapter

        showCurrentTab()
    }

    private fun acceptMessageRequest(conversation: ApiConversation) {
        lifecycleScope.launch {
            try {
                val result = chatRepository.acceptMessageRequest(
                    conversation.chatId,
                    currentUser?.firebaseUid ?: return@launch
                )

                if (result.isSuccess) {
                    Toast.makeText(this@InboxActivity, "Message request accepted", Toast.LENGTH_SHORT).show()
                    loadConversations() // Refresh the list
                } else {
                    Toast.makeText(this@InboxActivity, "Failed to accept request", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@InboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun rejectMessageRequest(conversation: ApiConversation) {
        AlertDialog.Builder(this)
            .setTitle("Reject Message Request")
            .setMessage("Are you sure you want to reject this message request? This will delete all messages from this user.")
            .setPositiveButton("Reject") { _, _ ->
                performRejectRequest(conversation)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRejectRequest(conversation: ApiConversation) {
        lifecycleScope.launch {
            try {
                val result = chatRepository.rejectMessageRequest(
                    conversation.chatId,
                    currentUser?.firebaseUid ?: return@launch
                )

                if (result.isSuccess) {
                    Toast.makeText(this@InboxActivity, "Message request rejected", Toast.LENGTH_SHORT).show()
                    loadConversations() // Refresh the list
                } else {
                    Toast.makeText(this@InboxActivity, "Failed to reject request", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@InboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRequestOptions(conversation: ApiConversation, user: ApiUser) {
        val options = arrayOf("Accept Request", "Reject Request", "View Profile", "Cancel")

        AlertDialog.Builder(this)
            .setTitle("Message Request from ${user.username}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> acceptMessageRequest(conversation)
                    1 -> rejectMessageRequest(conversation)
                    2 -> openUserProfile(user)
                    3 -> { /* Cancel */ }
                }
            }
            .show()
    }

    private fun showConversationOptions(conversation: ApiConversation, user: ApiUser) {
        val options = arrayOf("Delete Conversation", "View Profile", "Cancel")

        AlertDialog.Builder(this)
            .setTitle(user.username)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmDeleteConversation(conversation, user)
                    1 -> openUserProfile(user)
                    2 -> { /* Cancel */ }
                }
            }
            .show()
    }

    private fun confirmDeleteConversation(conversation: ApiConversation, user: ApiUser) {
        AlertDialog.Builder(this)
            .setTitle("Delete Conversation")
            .setMessage("Are you sure you want to delete this conversation? This will only delete it for you, not for ${user.username}.")
            .setPositiveButton("Delete") { _, _ ->
                deleteConversation(conversation.chatId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteConversation(chatId: String) {
        lifecycleScope.launch {
            try {
                val result = chatRepository.deleteConversation(chatId, currentUser?.firebaseUid ?: return@launch)

                if (result.isSuccess) {
                    Toast.makeText(this@InboxActivity, "Conversation deleted", Toast.LENGTH_SHORT).show()
                    loadConversations()
                } else {
                    Toast.makeText(this@InboxActivity, "Failed to delete conversation", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@InboxActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openUserProfile(user: ApiUser) {
        if (user.firebaseUid == currentUser?.firebaseUid) {
            startActivity(Intent(this, ProfileActivity::class.java))
        } else {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("userId", user.firebaseUid)
            intent.putExtra("userName", user.username)
            startActivity(intent)
        }
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        requestsRecyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        tvEmptyText.text = "No messages yet"
    }

    private fun showConversationList() {
        emptyState.visibility = View.GONE
    }

    private fun openConversation(user: ApiUser) {
        println("📬 Opening chat with user: ${user.username}, UID: ${user.firebaseUid}")

        lifecycleScope.launch {
            try {
                val result = chatRepository.getConversations(currentUser?.firebaseUid ?: return@launch)

                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    val allMessages = response.messages ?: emptyList()

                    // Find existing conversation with this user
                    val existingConversation = allMessages.find { conv ->
                        conv.participants.contains(user.firebaseUid)
                    }

                    val intent = Intent(this@InboxActivity, ChatActivity::class.java)
                    intent.putExtra("receiverUid", user.firebaseUid)
                    intent.putExtra("receiverUsername", user.username)
                    intent.putExtra("receiverProfileImage", user.profileImageUrl ?: "")

                    if (existingConversation != null) {
                        intent.putExtra("chatId", existingConversation.chatId)
                        println("✅ Existing conversation found: ${existingConversation.chatId}")
                    } else {
                        println("🆕 No existing conversation, will create on first message")
                    }

                    startActivityForResult(intent, 1001)
                } else {
                    val intent = Intent(this@InboxActivity, ChatActivity::class.java)
                    intent.putExtra("receiverUid", user.firebaseUid)
                    intent.putExtra("receiverUsername", user.username)
                    intent.putExtra("receiverProfileImage", user.profileImageUrl ?: "")
                    startActivityForResult(intent, 1001)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val intent = Intent(this@InboxActivity, ChatActivity::class.java)
                intent.putExtra("receiverUid", user.firebaseUid)
                intent.putExtra("receiverUsername", user.username)
                intent.putExtra("receiverProfileImage", user.profileImageUrl ?: "")
                startActivityForResult(intent, 1001)
            }
        }
    }

    private fun updateTabSelection() {
        tabIndicator.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val params = tabIndicator.layoutParams
            params.width = screenWidth / 2
            tabIndicator.layoutParams = params
            tabIndicator.x = 0f
        }
        updateTabColors()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            // Refresh conversations when returning from chat
            Handler(Looper.getMainLooper()).postDelayed({
                loadConversations()
            }, 500)
        }
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