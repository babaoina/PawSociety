package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class SearchUsersActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var btnBack: ImageView
    private lateinit var btnClear: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResultsCount: TextView

    private lateinit var sessionManager: SessionManager
    private val userRepository = UserRepository()
    private var currentUser: ApiUser? = null
    private var searchResults = listOf<ApiUser>()
    private lateinit var adapter: SearchUsersAdapter

    private val tag = "SearchUsersActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_users)

        sessionManager = SessionManager(this)
        currentUser = sessionManager.getCurrentUser()

        // Get search query from intent
        val searchQuery = intent.getStringExtra("search_query") ?: ""

        initializeViews()
        setupSearch()
        setupClickListeners()
        setupAdapter()

        // Auto-search if query was passed
        if (searchQuery.isNotEmpty()) {
            searchInput.setText(searchQuery)
            performSearch(searchQuery)
        }
    }

    private fun initializeViews() {
        searchInput = findViewById(R.id.search_input)
        btnBack = findViewById(R.id.btn_back)
        btnClear = findViewById(R.id.btn_clear)
        recyclerView = findViewById(R.id.recycler_view)
        emptyState = findViewById(R.id.empty_state)
        progressBar = findViewById(R.id.progress_bar)
        tvResultsCount = findViewById(R.id.tv_results_count)

        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupAdapter() {
        adapter = SearchUsersAdapter(searchResults, currentUser) { user ->
            // Open user profile when item clicked
            openUserProfile(user)
        }
        recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnClear.setOnClickListener {
            searchInput.text.clear()
            clearResults()
        }
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""

                // Show/hide clear button
                btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                // Search when user types (debounced)
                if (query.length >= 2) {
                    performSearch(query)
                } else if (query.isEmpty()) {
                    clearResults()
                } else {
                    // Less than 2 characters - show empty state with message
                    showEmptyState("Enter at least 2 characters", false)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE
        tvResultsCount.visibility = View.GONE

        lifecycleScope.launch {
            try {
                println("🔍 Searching users for: '$query'")
                val result = userRepository.searchUsers(query, limit = 50)

                if (result.isSuccess) {
                    searchResults = result.getOrNull() ?: emptyList()

                    // Filter out current user
                    searchResults = searchResults.filter { it.firebaseUid != currentUser?.firebaseUid }

                    println("✅ Found ${searchResults.size} users")

                    if (searchResults.isEmpty()) {
                        showEmptyState("No users found for \"$query\"")
                    } else {
                        showResults()
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Search failed"
                    println("❌ Search failed: $error")
                    showEmptyState("Search failed. Please try again.")
                }
            } catch (e: Exception) {
                println("❌ Search exception: ${e.message}")
                showEmptyState("Error: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun showResults() {
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        tvResultsCount.visibility = View.VISIBLE

        tvResultsCount.text = "Found ${searchResults.size} user${if (searchResults.size > 1) "s" else ""}"
        adapter.updateData(searchResults)
    }

    private fun clearResults() {
        searchResults = emptyList()
        adapter.updateData(emptyList())
        recyclerView.visibility = View.GONE
        tvResultsCount.visibility = View.GONE
        showEmptyState("Search for users", false)
    }

    private fun showEmptyState(message: String, showIcon: Boolean = true) {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        tvResultsCount.visibility = View.GONE

        val emptyIcon = emptyState.findViewById<ImageView>(R.id.empty_icon)
        val emptyText = emptyState.findViewById<TextView>(R.id.empty_text)
        val emptySubtext = emptyState.findViewById<TextView>(R.id.empty_subtext)

        if (showIcon) {
            emptyIcon.visibility = View.VISIBLE
        } else {
            emptyIcon.visibility = View.GONE
        }

        emptyText.text = message
        emptySubtext.text = if (message.contains("2 characters")) {
            "Type more to search"
        } else {
            "Try a different search term"
        }
    }

    private fun openUserProfile(user: ApiUser) {
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("userId", user.firebaseUid)
        intent.putExtra("userName", user.username)
        startActivity(intent)
    }
}

/**
 * Adapter for search results
 */
class SearchUsersAdapter(
    private var users: List<ApiUser>,
    private val currentUser: ApiUser?,
    private val onItemClick: (ApiUser) -> Unit
) : RecyclerView.Adapter<SearchUsersAdapter.SearchViewHolder>() {

    private val colors = listOf(
        "#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#E91E63"
    )

    class SearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        val profileIcon: TextView = itemView.findViewById(R.id.profile_icon)
        val username: TextView = itemView.findViewById(R.id.username)
        val fullName: TextView = itemView.findViewById(R.id.full_name)
        val btnMessage: Button = itemView.findViewById(R.id.btn_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_user, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val user = users[position]

        holder.username.text = user.username
        holder.fullName.text = user.fullName

        // Set profile image or icon
        if (!user.profileImageUrl.isNullOrEmpty()) {
            val fullImageUrl = if (user.profileImageUrl.startsWith("http")) {
                user.profileImageUrl
            } else {
                "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${user.profileImageUrl}"
            }

            holder.profileIcon.visibility = View.GONE
            holder.profileImage.visibility = View.VISIBLE

            Glide.with(holder.itemView.context)
                .load(fullImageUrl)
                .circleCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(holder.profileImage)
        } else {
            holder.profileImage.visibility = View.GONE
            holder.profileIcon.visibility = View.VISIBLE

            val firstLetter = if (user.username.isNotEmpty()) {
                user.username.first().toString().uppercase()
            } else {
                "?"
            }
            holder.profileIcon.text = firstLetter

            val colorIndex = Math.abs(user.firebaseUid.hashCode()) % colors.size
            holder.profileIcon.setBackgroundColor(android.graphics.Color.parseColor(colors[colorIndex]))
        }

        // Item click - open profile
        holder.itemView.setOnClickListener {
            onItemClick(user)
        }

        // Message button click - start chat directly
        holder.btnMessage.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("receiverUid", user.firebaseUid)
            intent.putExtra("receiverUsername", user.username)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = users.size

    fun updateData(newUsers: List<ApiUser>) {
        users = newUsers
        notifyDataSetChanged()
    }
}