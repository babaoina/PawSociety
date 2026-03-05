package com.example.pawsociety

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.FollowRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class FollowersFollowingActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var tabFollowers: TextView
    private lateinit var tabFollowing: TextView
    private lateinit var tabIndicator: View

    private lateinit var sessionManager: SessionManager
    private lateinit var followRepository: FollowRepository

    private var currentMode = "followers" // "followers" or "following"
    private var targetUserId: String = ""
    private var targetUserName: String = ""
    private var usersList = listOf<ApiUser>()
    private var currentUser: ApiUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_followers_following)

        sessionManager = SessionManager(this)
        currentUser = sessionManager.getCurrentUser()
        followRepository = FollowRepository()

        targetUserId = intent.getStringExtra("userId") ?: ""
        targetUserName = intent.getStringExtra("userName") ?: "User"
        currentMode = intent.getStringExtra("mode") ?: "followers"

        println("📋 FollowersFollowingActivity - Mode: $currentMode for user: $targetUserName (UID: $targetUserId)")

        initializeViews()
        setupClickListeners()
        loadData()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_view)
        emptyState = findViewById(R.id.empty_state)
        progressBar = findViewById(R.id.progress_bar)
        tvTitle = findViewById(R.id.tv_title)
        btnBack = findViewById(R.id.btn_back)
        tabFollowers = findViewById(R.id.tab_followers)
        tabFollowing = findViewById(R.id.tab_following)
        tabIndicator = findViewById(R.id.tab_indicator)

        tvTitle.text = targetUserName
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Set initial tab selection
        updateTabSelection()
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        tabFollowers.setOnClickListener {
            if (currentMode != "followers") {
                currentMode = "followers"
                updateTabSelection()
                loadData()
            }
        }

        tabFollowing.setOnClickListener {
            if (currentMode != "following") {
                currentMode = "following"
                updateTabSelection()
                loadData()
            }
        }
    }

    private fun updateTabSelection() {
        tabFollowers.setTextColor(Color.parseColor("#999999"))
        tabFollowing.setTextColor(Color.parseColor("#999999"))

        if (currentMode == "followers") {
            tabFollowers.setTextColor(Color.parseColor("#7A4F2B"))
            tabIndicator.animate().x(tabFollowers.x).setDuration(200).start()
        } else {
            tabFollowing.setTextColor(Color.parseColor("#7A4F2B"))
            tabIndicator.animate().x(tabFollowing.x).setDuration(200).start()
        }
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = if (currentMode == "followers") {
                    followRepository.getFollowers(targetUserId)
                } else {
                    followRepository.getFollowing(targetUserId)
                }

                if (result.isSuccess) {
                    usersList = result.getOrNull() ?: emptyList()
                    println("✅ Loaded ${usersList.size} ${currentMode}")

                    if (usersList.isEmpty()) {
                        showEmptyState()
                    } else {
                        showUserList()
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    println("❌ Failed to load $currentMode: $error")
                    showEmptyState()
                }
            } catch (e: Exception) {
                println("❌ Exception loading $currentMode: ${e.message}")
                e.printStackTrace()
                showEmptyState()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun showUserList() {
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        val adapter = FollowersAdapter(usersList, currentMode, currentUser) { user ->
            // Open user profile when clicked
            if (user.firebaseUid == currentUser?.firebaseUid) {
                val intent = Intent(this, ProfileActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
            } else {
                val intent = Intent(this, UserProfileActivity::class.java)
                intent.putExtra("userId", user.firebaseUid)
                intent.putExtra("userName", user.username)
                startActivity(intent)
            }
        }
        recyclerView.adapter = adapter
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE

        val emptyText = findViewById<TextView>(R.id.empty_text)
        emptyText.text = if (currentMode == "followers") {
            "No followers yet"
        } else {
            "Not following anyone yet"
        }
    }
}

// Adapter for followers/following list
class FollowersAdapter(
    private val users: List<ApiUser>,
    private val mode: String,
    private val currentUser: ApiUser?,
    private val onUserClick: (ApiUser) -> Unit
) : RecyclerView.Adapter<FollowersAdapter.FollowerViewHolder>() {

    class FollowerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileIcon: TextView = itemView.findViewById(R.id.profile_icon)
        val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        val username: TextView = itemView.findViewById(R.id.username)
        val btnAction: Button = itemView.findViewById(R.id.btn_action)
        val fullName: TextView = itemView.findViewById(R.id.full_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follower, parent, false)
        return FollowerViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowerViewHolder, position: Int) {
        val user = users[position]

        holder.username.text = user.username
        holder.fullName.text = user.fullName

        // Set profile picture or icon
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

            val colors = listOf(
                "#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#E91E63"
            )
            val colorIndex = Math.abs(user.firebaseUid.hashCode()) % colors.size
            holder.profileIcon.setBackgroundColor(Color.parseColor(colors[colorIndex]))
        }

        // Set action button based on mode and relationship
        if (user.firebaseUid == currentUser?.firebaseUid) {
            holder.btnAction.visibility = View.GONE
        } else {
            holder.btnAction.visibility = View.VISIBLE
            if (mode == "followers") {
                holder.btnAction.text = "Follow Back"
                holder.btnAction.setBackgroundColor(Color.parseColor("#7A4F2B"))
            } else {
                holder.btnAction.text = "Following"
                holder.btnAction.setBackgroundColor(Color.parseColor("#4CAF50"))
            }
        }

        holder.itemView.setOnClickListener {
            onUserClick(user)
        }

        holder.btnAction.setOnClickListener {
            // TODO: Handle follow/unfollow action
            Toast.makeText(holder.itemView.context, "Follow action for ${user.username}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = users.size
}