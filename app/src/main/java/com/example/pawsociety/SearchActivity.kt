package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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


class SearchActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var btnBack: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var sessionManager: SessionManager
    private val userRepository = UserRepository()
    private var currentUser: ApiUser? = null
    private var searchResults = listOf<ApiUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_users)

        sessionManager = SessionManager(this)
        currentUser = sessionManager.getCurrentUser()

        initializeViews()
        setupSearch()
        setupClickListeners()
    }

    private fun initializeViews() {
        searchInput = findViewById(R.id.search_input)
        btnBack = findViewById(R.id.btn_back)
        recyclerView = findViewById(R.id.recycler_view)
        emptyState = findViewById(R.id.empty_state)
        progressBar = findViewById(R.id.progress_bar)

        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    performSearch(query)
                } else if (query.isEmpty()) {
                    showEmptyState()
                }
            }
        })
        
        // Keyboard scroll handling for search input
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                searchInput.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        searchInput.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        // Ensure search input is visible at top
                        recyclerView.post {
                            recyclerView.scrollToPosition(0)
                        }
                    }
                })
            }
        }
    }

    private fun performSearch(query: String) {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = userRepository.searchUsers(query)

                if (result.isSuccess) {
                    searchResults = result.getOrNull() ?: emptyList()

                    // Filter out current user
                    searchResults = searchResults.filter { it.firebaseUid != currentUser?.firebaseUid }

                    if (searchResults.isEmpty()) {
                        showEmptyState("No users found")
                    } else {
                        showResults()
                    }
                } else {
                    showEmptyState("Search failed")
                }
            } catch (e: Exception) {
                showEmptyState("Error: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun showResults() {
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        val adapter = SearchUserAdapter(searchResults, currentUser) { user ->
            openUserProfile(user)
        }
        recyclerView.adapter = adapter
    }

    private fun showEmptyState(message: String = "Search for users") {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE

        val emptyText = emptyState.findViewById<TextView>(R.id.empty_text)
        emptyText.text = message
    }

    private fun openUserProfile(user: ApiUser) {
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("userId", user.firebaseUid)
        intent.putExtra("userName", user.username)
        startActivity(intent)
    }
}

/**
 * Adapter for search results with Message button
 */
class SearchUserAdapter(
    private val users: List<ApiUser>,
    private val currentUser: ApiUser?,
    private val onItemClick: (ApiUser) -> Unit
) : RecyclerView.Adapter<SearchUserAdapter.SearchViewHolder>() {

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

            val colors = listOf("#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800")
            val colorIndex = Math.abs(user.firebaseUid.hashCode()) % colors.size
            holder.profileIcon.setBackgroundColor(android.graphics.Color.parseColor(colors[colorIndex]))
        }

        // Item click - open profile
        holder.itemView.setOnClickListener {
            onItemClick(user)
        }

        // Message button click - start chat
        holder.btnMessage.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("receiverUid", user.firebaseUid)
            intent.putExtra("receiverUsername", user.username)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = users.size
}