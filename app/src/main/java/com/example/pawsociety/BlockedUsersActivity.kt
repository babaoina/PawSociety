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
    private var userMap = mutableMapOf<String, String>() // uid -> username

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
                        for (block in blockedList) {
                            val userResult = userRepository.getUserByUid(block.blockedUid)
                            if (userResult.isSuccess) {
                                val user = userResult.getOrNull()
                                userMap[block.blockedUid] = user?.username ?: "Unknown User"
                            } else {
                                userMap[block.blockedUid] = "Unknown User"
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
        val username = userMap[block.blockedUid] ?: "this user"

        AlertDialog.Builder(this)
            .setTitle("Unblock User")
            .setMessage("Are you sure you want to unblock $username? They will be able to see your posts and interact with you again.")
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
    private val userMap: Map<String, String>,
    private val onUnblockClick: (Block) -> Unit
) : RecyclerView.Adapter<BlockedUsersAdapter.BlockedViewHolder>() {

    class BlockedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileIcon: TextView = itemView.findViewById(R.id.profile_icon)
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
        val username = userMap[block.blockedUid] ?: "Unknown User"

        holder.username.text = username

        val firstLetter = if (username.isNotEmpty() && username != "Unknown User") {
            username.first().toString().uppercase()
        } else {
            "?"
        }
        holder.profileIcon.text = firstLetter

        val colors = listOf("#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800")
        val colorIndex = position % colors.size
        holder.profileIcon.setBackgroundColor(Color.parseColor(colors[colorIndex]))

        holder.btnUnblock.setOnClickListener {
            onUnblockClick(block)
        }
    }

    override fun getItemCount() = blocks.size
}