package com.example.pawsociety

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
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
    private val userRepository = UserRepository()

    private var currentMode = "followers" // "followers" or "following"
    private var targetUserId: String = ""
    private var targetUserName: String = ""
    private var usersList = listOf<ApiUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_followers_following)

        sessionManager = SessionManager(this)

        targetUserId = intent.getStringExtra("userId") ?: ""
        targetUserName = intent.getStringExtra("userName") ?: "User"
        currentMode = intent.getStringExtra("mode") ?: "followers"

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
                // TODO: Replace with actual API calls to get followers/following
                // For now, simulate with sample data
                val result = userRepository.getUsers(limit = 20)

                if (result.isSuccess) {
                    usersList = result.getOrNull()?.filter { it.firebaseUid != targetUserId } ?: emptyList()

                    if (usersList.isEmpty()) {
                        showEmptyState()
                    } else {
                        showUserList()
                    }
                } else {
                    showEmptyState()
                }
            } catch (e: Exception) {
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

        val adapter = FollowersAdapter(usersList, currentMode) { user ->
            // Open user profile when clicked
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("userId", user.firebaseUid)
            intent.putExtra("userName", user.username)
            startActivity(intent)
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
    private val onUserClick: (ApiUser) -> Unit
) : RecyclerView.Adapter<FollowersAdapter.FollowerViewHolder>() {

    class FollowerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileIcon: TextView = itemView.findViewById(R.id.profile_icon)
        val username: TextView = itemView.findViewById(R.id.username)
        val btnAction: Button = itemView.findViewById(R.id.btn_action)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_follower, parent, false)
        return FollowerViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowerViewHolder, position: Int) {
        val user = users[position]

        // Set profile icon
        val firstLetter = if (user.username.isNotEmpty()) {
            user.username.first().toString().uppercase()
        } else {
            "?"
        }
        holder.profileIcon.text = firstLetter
        holder.profileIcon.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.circle_solid_profile)

        holder.username.text = user.username

        // Set action button based on mode
        if (mode == "followers") {
            holder.btnAction.text = "Follow Back"
            holder.btnAction.setBackgroundColor(Color.parseColor("#7A4F2B"))
        } else {
            holder.btnAction.text = "Following"
            holder.btnAction.setBackgroundColor(Color.parseColor("#4CAF50"))
        }

        holder.itemView.setOnClickListener {
            onUserClick(user)
        }

        holder.btnAction.setOnClickListener {
            // TODO: Handle follow/unfollow action
            Toast.makeText(holder.itemView.context, "Follow action", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = users.size
}