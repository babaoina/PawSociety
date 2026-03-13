package com.example.pawsociety

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pawsociety.api.BatchUser
import com.example.pawsociety.api.Block
import com.example.pawsociety.data.repository.BlockRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class BlockedUsersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: ImageView

    private lateinit var sessionManager: SessionManager
    private val blockRepository = BlockRepository()
    private val userRepository = UserRepository()

    private var blockedList = listOf<Block>()
    private var userMap = mutableMapOf<String, BatchUser>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_users)

        sessionManager = SessionManager(this)

        initializeViews()
        loadBlockedUsers()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recycler_view)
        emptyState = findViewById(R.id.empty_state)
        progressBar = findViewById(R.id.progress_bar)
        btnBack = findViewById(R.id.btn_back)

        recyclerView.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadBlockedUsers() {
        val currentUser = sessionManager.getCurrentUser() ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = blockRepository.getBlockedUsers(currentUser.firebaseUid)

                if (result.isSuccess) {
                    blockedList = result.getOrNull() ?: emptyList()

                    if (blockedList.isEmpty()) {
                        showEmptyState()
                    } else {
                        val blockedUids = blockedList.map { it.blockedUid }

                        // Fetch user info
                        val userResult = userRepository.getUsersBatch(blockedUids)

                        if (userResult.isSuccess) {
                            val users = userResult.getOrNull() ?: emptyMap()
                            userMap.clear()
                            userMap.putAll(users)

                            // Debug log
                            println("📦 Blocked users map:")
                            userMap.forEach { (uid, user) ->
                                println("   - $uid: ${user.username}")
                            }
                        }

                        showUserList()
                    }
                } else {
                    Toast.makeText(this@BlockedUsersActivity, "Failed to load blocked users", Toast.LENGTH_SHORT).show()
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

        val adapter = BlockedUsersAdapter(blockedList, userMap) { block ->
            showUnblockDialog(block)
        }
        recyclerView.adapter = adapter
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE

        val emptyText = emptyState.findViewById<TextView>(R.id.empty_text)
        emptyText.text = "No blocked users"
    }

    private fun showUnblockDialog(block: Block) {
        val userInfo = userMap[block.blockedUid]
        val username = userInfo?.username ?: "this user"

        AlertDialog.Builder(this)
            .setTitle("Unblock User")
            .setMessage("Are you sure you want to unblock $username?")
            .setPositiveButton("Unblock") { _, _ ->
                unblockUser(block)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unblockUser(block: Block) {
        val currentUser = sessionManager.getCurrentUser() ?: return

        lifecycleScope.launch {
            val result = blockRepository.unblockUser(currentUser.firebaseUid, block.blockedUid)

            if (result.isSuccess) {
                Toast.makeText(this@BlockedUsersActivity, "User unblocked", Toast.LENGTH_SHORT).show()
                loadBlockedUsers()
            } else {
                Toast.makeText(this@BlockedUsersActivity, "Failed to unblock user", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class BlockedUsersAdapter(
    private val blocks: List<Block>,
    private val userMap: Map<String, BatchUser>,
    private val onUnblockClick: (Block) -> Unit
) : RecyclerView.Adapter<BlockedUsersAdapter.BlockedViewHolder>() {

    class BlockedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileIcon: TextView = itemView.findViewById(R.id.profile_icon)
        val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        val username: TextView = itemView.findViewById(R.id.username)
        val btnUnblock: Button = itemView.findViewById(R.id.btn_unblock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_user, parent, false)
        return BlockedViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlockedViewHolder, position: Int) {
        val block = blocks[position]
        val userInfo = userMap[block.blockedUid]

        // Get username - this should now work
        val username = if (userInfo != null && userInfo.username.isNotEmpty() && userInfo.username != "Unknown User") {
            userInfo.username
        } else {
            // Try to get from database as fallback
            "User ${block.blockedUid.take(4)}"
        }

        holder.username.text = username

        // Get profile image URL
        val profileImageUrl = userInfo?.profileImageUrl

        // Show profile image if available
        if (!profileImageUrl.isNullOrEmpty()) {
            val fullImageUrl = if (profileImageUrl.startsWith("http")) {
                profileImageUrl
            } else {
                "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$profileImageUrl"
            }

            holder.profileImage.visibility = View.VISIBLE
            holder.profileIcon.visibility = View.GONE

            Glide.with(holder.itemView.context)
                .load(fullImageUrl)
                .circleCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(holder.profileImage)
        } else {
            // Show text icon with first letter
            holder.profileImage.visibility = View.GONE
            holder.profileIcon.visibility = View.VISIBLE

            val firstLetter = if (username.isNotEmpty() && username != "Unknown User") {
                username.first().toString().uppercase()
            } else {
                "?"
            }
            holder.profileIcon.text = firstLetter

            // Generate consistent color based on user ID
            val colors = listOf("#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800")
            val colorIndex = Math.abs(block.blockedUid.hashCode()) % colors.size
            holder.profileIcon.setBackgroundColor(Color.parseColor(colors[colorIndex]))
        }

        holder.btnUnblock.setOnClickListener {
            onUnblockClick(block)
        }
    }

    override fun getItemCount() = blocks.size
}