package com.example.pawsociety

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.pawsociety.api.ApiClient
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.HidePostRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class HiddenPostsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var hidePostRepository: HidePostRepository
    private lateinit var container: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var btnBack: ImageView
    private lateinit var tvCount: TextView
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val TAG = "HiddenPostsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_posts)

        sessionManager = SessionManager(this)
        hidePostRepository = HidePostRepository()

        initViews()
        loadHiddenPosts()
    }

    private fun initViews() {
        container = findViewById(R.id.hidden_posts_container)
        emptyState = findViewById(R.id.empty_state)
        btnBack = findViewById(R.id.btn_back)
        tvCount = findViewById(R.id.tv_count)
        progressBar = findViewById(R.id.progress_bar)

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadHiddenPosts() {
        val currentUser = sessionManager.getCurrentUser() ?: run {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Show progress
        progressBar.visibility = View.VISIBLE
        container.visibility = View.GONE
        emptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = hidePostRepository.getHiddenPosts(currentUser.firebaseUid)

                progressBar.visibility = View.GONE

                if (result.isSuccess) {
                    val hiddenPosts = result.getOrNull() ?: emptyList()
                    tvCount.text = hiddenPosts.size.toString()
                    displayHiddenPosts(hiddenPosts)
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "Failed to load hidden posts: ${error?.message}")
                    Toast.makeText(this@HiddenPostsActivity,
                        "Failed to load hidden posts", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Log.e(TAG, "Error loading hidden posts: ${e.message}")
                Toast.makeText(this@HiddenPostsActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
        }
    }

    private fun displayHiddenPosts(posts: List<ApiPost>) {
        container.removeAllViews()

        if (posts.isEmpty()) {
            showEmptyState()
            return
        }

        emptyState.visibility = View.GONE
        container.visibility = View.VISIBLE

        posts.forEach { post ->
            val postView = LayoutInflater.from(this).inflate(R.layout.item_hidden_post, container, false)

            val petName = postView.findViewById<TextView>(R.id.tv_pet_name)
            val userName = postView.findViewById<TextView>(R.id.tv_user_name)
            val postImage = postView.findViewById<ImageView>(R.id.post_image)
            val btnUnhide = postView.findViewById<TextView>(R.id.btn_unhide)
            val placeholderIcon = postView.findViewById<TextView>(R.id.placeholder_icon)

            petName.text = post.petName
            userName.text = "Posted by: ${post.userName}"

            // Load post image with proper URL handling
            loadPostImage(post, postImage, placeholderIcon)

            btnUnhide.setOnClickListener {
                showUnhideConfirmation(post)
            }

            // Make the whole item clickable to view post details
            postView.setOnClickListener {
                viewPostDetails(post)
            }

            container.addView(postView)
        }
    }

    private fun loadPostImage(post: ApiPost, imageView: ImageView, placeholderView: TextView?) {
        if (!post.imageUrls.isNullOrEmpty()) {
            val imageUrl = post.imageUrls[0]

            // Construct the correct URL without double slashes
            val fullImageUrl = buildImageUrl(imageUrl)

            Log.d(TAG, "Loading image: $fullImageUrl for post: ${post.postId}")

            // Hide placeholder initially
            placeholderView?.visibility = View.GONE
            imageView.visibility = View.VISIBLE

            // Load image with Glide - WITH PROPER LISTENER
            Glide.with(this)
                .load(fullImageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e(TAG, "Failed to load image: $fullImageUrl - ${e?.message}")
                        imageView.visibility = View.GONE
                        if (placeholderView != null) {
                            placeholderView.visibility = View.VISIBLE
                            placeholderView.text = post.petName.first().toString().uppercase()
                        }
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,          // Remove the ?
                        target: Target<Drawable>?, // Add the ?
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.d(TAG, "Successfully loaded image: $fullImageUrl")
                        return false
                    }
                })
                .centerCrop()
                .into(imageView)

        } else {
            // No images - show placeholder
            imageView.visibility = View.GONE
            if (placeholderView != null) {
                placeholderView.visibility = View.VISIBLE
                placeholderView.text = post.petName.first().toString().uppercase()
            } else {
                // Fallback if no placeholder
                imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                imageView.visibility = View.VISIBLE
            }
        }
    }

    private fun buildImageUrl(imageUrl: String): String {
        return when {
            // Already a full URL
            imageUrl.startsWith("http") -> imageUrl

            // Starts with /api/uploads (e.g., /api/uploads/posts/filename.jpg)
            imageUrl.startsWith("/api/uploads") -> {
                val baseUrl = ApiClient.BASE_URL_NO_API
                // Remove trailing slash from base if present
                val cleanBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
                "$cleanBase$imageUrl"
            }

            // Starts with /uploads (e.g., /uploads/posts/filename.jpg)
            imageUrl.startsWith("/uploads") -> {
                val baseUrl = ApiClient.BASE_URL
                // Remove trailing slash from base if present
                val cleanBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
                "$cleanBase$imageUrl"
            }

            // Just a filename
            else -> {
                val baseUrl = ApiClient.BASE_URL
                val cleanBase = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
                "$cleanBase/uploads/posts/$imageUrl"
            }
        }.replace("//", "/").replace(":/", "://") // Fix any double slashes
    }

    private fun viewPostDetails(post: ApiPost) {
        val intent = Intent(this, PostViewActivity::class.java)
        intent.putExtra("post", post)
        startActivity(intent)
    }

    private fun showUnhideConfirmation(post: ApiPost) {
        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setTitle("Unhide Post")
            .setMessage("Do you want to unhide this post? It will appear in your feed again.")
            .setPositiveButton("Unhide") { _, _ ->
                unhidePost(post)
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        // Make dialog content visible
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
    }

    private fun unhidePost(post: ApiPost) {
        val currentUser = sessionManager.getCurrentUser() ?: return

        lifecycleScope.launch {
            try {
                val result = hidePostRepository.unhidePost(currentUser.firebaseUid, post.postId)

                if (result.isSuccess) {
                    Toast.makeText(this@HiddenPostsActivity,
                        "Post unhidden successfully", Toast.LENGTH_SHORT).show()
                    loadHiddenPosts() // Refresh the list
                } else {
                    val error = result.exceptionOrNull()
                    Toast.makeText(this@HiddenPostsActivity,
                        "Failed to unhide post: ${error?.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unhiding post: ${e.message}")
                Toast.makeText(this@HiddenPostsActivity,
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEmptyState() {
        emptyState.visibility = View.VISIBLE
        container.visibility = View.GONE
        tvCount.text = "0"
        progressBar.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadHiddenPosts()
    }
}
