package com.example.pawsociety

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiHighlight
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.UploadRepository
import com.example.pawsociety.util.FileHelper
import com.example.pawsociety.util.PermissionHelper
import com.example.pawsociety.util.SessionManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : BaseNavigationActivity() {

    private lateinit var viewModel: ProfileViewModel
    private lateinit var sessionManager: SessionManager
    private val uploadRepository = UploadRepository()
    private var currentUser: ApiUser? = null
    private var selectedProfileImageUri: Uri? = null
    private var selectedHighlightImageUri: Uri? = null
    private var currentDialogView: View? = null
    private var currentPhotoPath: String? = null
    private var isUploading = false
    private var pendingPermissionAction = ""

    companion object {
        private const val EDIT_PROFILE_REQUEST = 1001
        private const val CREATE_POST_REQUEST = 1002
    }

    // Gallery launcher for picking images
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedProfileImageUri = it
            startCrop(it)
        }
    }

    // Edit Profile launcher with auto-refresh
    private val editProfileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Auto-refresh profile data
            viewModel.loadUserData()

            // Also refresh from session to be safe
            sessionManager.getCurrentUser()?.let { user ->
                currentUser = user
                updateProfileWithUserData(user)
            }

            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Create Post launcher with auto-refresh
    private val createPostLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Auto-refresh posts immediately
            currentUser?.let { user ->
                lifecycleScope.launch {
                    // Small delay to ensure backend has processed the post
                    delay(500)
                    viewModel.loadUserPosts(user.firebaseUid)
                }
            }

            // Switch to posts tab
            if (currentTab != "posts") {
                currentTab = "posts"
                updateTabIcons()
            }

            Toast.makeText(this, "Post created successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val uri = Uri.fromFile(file)
                    selectedProfileImageUri = uri
                    startCrop(uri)
                }
            }
        }
    }

    // UCrop result launcher
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            resultUri?.let { croppedUri ->
                // Upload the cropped image
                lifecycleScope.launch {
                    uploadCroppedImage(croppedUri)
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = UCrop.getError(result.data!!)
            Toast.makeText(this, "Crop error: ${error?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            when (pendingPermissionAction) {
                "camera" -> openCamera()
                "gallery" -> openGallery()
            }
        } else {
            Toast.makeText(this, "Permission denied. Cannot access $pendingPermissionAction", Toast.LENGTH_SHORT).show()
        }
        pendingPermissionAction = ""
    }

    private val highlightImagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedHighlightImageUri = it
            currentDialogView?.findViewById<ImageView>(R.id.highlight_image_preview)?.let { imageView ->
                imageView.setImageURI(it)
                imageView.visibility = View.VISIBLE
            }
        }
    }

    private var currentTab = "posts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            println("📱 ProfileActivity - Starting...")
            setContentView(R.layout.activity_profile)

            sessionManager = SessionManager(this)

            currentUser = sessionManager.getCurrentUser()

            if (currentUser == null) {
                println("📱 ProfileActivity - No user found, redirecting to login")
                Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return
            }

            if (currentUser!!.firebaseUid.isNullOrEmpty()) {
                println("❌ ProfileActivity - User has invalid UID: ${currentUser!!.firebaseUid}")
                Toast.makeText(this, "Invalid user session. Please login again.", Toast.LENGTH_LONG).show()
                sessionManager.clearSession()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return
            }

            println("✅ ProfileActivity - Valid user: ${currentUser!!.username} with UID: ${currentUser!!.firebaseUid}")

            viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
            viewModel.setSessionManager(sessionManager)

            viewModel.user.observe(this) { user ->
                if (user != null) {
                    println("📱 ProfileActivity - User data received: ${user.username}")
                    currentUser = user
                    updateProfileWithUserData(user)
                    setupAllClickListeners()
                }
            }

            viewModel.highlights.observe(this) { highlights ->
                println("📱 ProfileActivity - Highlights received: ${highlights.size}")
                updateHighlightsDisplay(highlights)
            }

            viewModel.userPosts.observe(this) { posts ->
                println("📱 ProfileActivity - Posts received: ${posts.size}")
                if (currentTab == "posts") {
                    updatePostsGrid(posts)
                }
                // Stop refresh indicator when posts load
                val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
                swipeRefreshLayout?.isRefreshing = false
            }

            viewModel.favoritePosts.observe(this) { favorites ->
                println("📱 ProfileActivity - Favorites received: ${favorites.size}")
                if (currentTab == "favorites") {
                    updateFavoritesGrid(favorites)
                }
                // Stop refresh indicator when favorites load
                val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
                swipeRefreshLayout?.isRefreshing = false
            }

            viewModel.error.observe(this) { error ->
                error?.let {
                    println("📱 ProfileActivity - Error: $it")
                    Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                    // Stop refresh indicator on error too
                    val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
                    swipeRefreshLayout?.isRefreshing = false
                }
            }

            viewModel.loadUserData()
            println("📱 ProfileActivity - Loading user data...")

            setupAllClickListeners()

            // Set initial tab indicator based on currentTab
            viewModel.user.observe(this) { user ->
                if (user != null) {
                    // Delay to ensure views are laid out
                    findViewById<View>(R.id.tab_posts).post {
                        updateTabIcons()
                    }
                }
            }

            // Initialize SwipeRefreshLayout colors
            val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
            swipeRefreshLayout?.setColorSchemeColors(
                Color.parseColor("#7A4F2B"),
                Color.parseColor("#B88B4A"),
                Color.parseColor("#FF6B35")
            )

        } catch (e: Exception) {
            println("❌ ProfileActivity CRASH in onCreate: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = externalCacheDir ?: cacheDir
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setCircleDimmedLayer(true)
            setShowCropFrame(true)
            setCropFrameColor(Color.parseColor("#7A4F2B"))
            setCropFrameStrokeWidth(4)
            setShowCropGrid(true)
            setCropGridColor(Color.parseColor("#FFFFFF"))
            setCropGridStrokeWidth(2)
            setToolbarColor(Color.parseColor("#7A4F2B"))
            setStatusBarColor(Color.parseColor("#7A4F2B"))
            setActiveControlsWidgetColor(Color.parseColor("#7A4F2B"))
            setToolbarTitle("Crop Profile Picture")
        }

        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(512, 512)
            .withOptions(options)

        cropLauncher.launch(uCrop.getIntent(this))
    }

    private suspend fun uploadCroppedImage(uri: Uri) {
        if (isUploading) {
            Toast.makeText(this, "Upload already in progress", Toast.LENGTH_SHORT).show()
            return
        }

        isUploading = true
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show()

        try {
            val file = FileHelper.uriToFile(this, uri)
            if (file == null) {
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
                isUploading = false
                return
            }

            val compressedFile = FileHelper.compressImage(file, maxWidth = 512, quality = 85)
            val result = uploadRepository.uploadProfilePicture(compressedFile)

            // Clean up temp files
            if (file.name.startsWith("upload_") || file.name.startsWith("cropped_") || file.name.startsWith("compressed_")) {
                FileHelper.deleteFile(file)
            }
            if (compressedFile != file && (compressedFile.name.startsWith("upload_") || compressedFile.name.startsWith("cropped_") || compressedFile.name.startsWith("compressed_"))) {
                FileHelper.deleteFile(compressedFile)
            }

            if (result.isSuccess) {
                val imageUrl = result.getOrNull()
                Toast.makeText(this, "Image uploaded successfully!", Toast.LENGTH_SHORT).show()

                // Update profile with new image and auto-refresh
                currentUser?.let { user ->
                    viewModel.updateProfile(
                        profileImageUrl = imageUrl
                    )

                    // Small delay then refresh
                    delay(500)
                    viewModel.loadUserData()
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Upload failed"
                Toast.makeText(this, "Upload failed: $error", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error uploading: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isUploading = false
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")

        AlertDialog.Builder(this)
            .setTitle("Change Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> checkStoragePermissionAndOpen()
                    2 -> { /* Cancel */ }
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (PermissionHelper.hasCameraPermission(this)) {
            openCamera()
        } else {
            pendingPermissionAction = "camera"
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkStoragePermissionAndOpen() {
        if (PermissionHelper.hasStoragePermission(this)) {
            openGallery()
        } else {
            pendingPermissionAction = "gallery"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun setupAllClickListeners() {
        try {
            // Setup SwipeRefreshLayout
            val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
            swipeRefreshLayout?.setOnRefreshListener {
                println("🔄 Refreshing profile...")
                viewModel.refreshData()

                // Fallback: Stop refresh after 3 seconds if not stopped by observers
                Handler(Looper.getMainLooper()).postDelayed({
                    if (swipeRefreshLayout.isRefreshing) {
                        swipeRefreshLayout.isRefreshing = false
                        println("⚠️ Refresh stopped by timeout")
                    }
                }, 3000)
            }

            val menuBtn = findViewById<ImageView>(R.id.btn_menu)
            menuBtn?.setOnClickListener {
                println("🔘 Menu button clicked")
                it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            val shareProfileBtn = findViewById<TextView>(R.id.btn_share_profile)
            shareProfileBtn?.setOnClickListener {
                println("🔘 Share Profile button clicked")
                it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
                shareProfile()
            }

            val editProfileBtn = findViewById<TextView>(R.id.btn_edit_profile)
            editProfileBtn?.setOnClickListener {
                println("🔘 Edit Profile button clicked")
                it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()

                val intent = Intent(this, EditProfileActivity::class.java)
                editProfileLauncher.launch(intent)
            }

            val bigPlusButton = findViewById<View>(R.id.big_plus_button)
            bigPlusButton?.setOnClickListener {
                println("🔘 Big Plus button clicked")
                it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()

                val intent = Intent(this, CreatePostActivity::class.java)
                createPostLauncher.launch(intent)
            }

            val layoutPosts = findViewById<LinearLayout>(R.id.layout_posts)
            layoutPosts?.setOnClickListener {
                println("🔘 Posts count clicked")
            }

            val layoutFollowers = findViewById<LinearLayout>(R.id.layout_followers)
            layoutFollowers?.setOnClickListener {
                println("🔘 Followers clicked")
                currentUser?.let { user ->
                    val intent = Intent(this, FollowersFollowingActivity::class.java)
                    intent.putExtra("userId", user.firebaseUid)
                    intent.putExtra("userName", user.username)
                    intent.putExtra("mode", "followers")
                    startActivity(intent)
                }
            }

            val layoutFollowing = findViewById<LinearLayout>(R.id.layout_following)
            layoutFollowing?.setOnClickListener {
                println("🔘 Following clicked")
                currentUser?.let { user ->
                    val intent = Intent(this, FollowersFollowingActivity::class.java)
                    intent.putExtra("userId", user.firebaseUid)
                    intent.putExtra("userName", user.username)
                    intent.putExtra("mode", "following")
                    startActivity(intent)
                }
            }

            val tabPosts = findViewById<View>(R.id.tab_posts)
            tabPosts?.setOnClickListener {
                println("🔘 Posts tab clicked")
                if (currentTab != "posts") {
                    currentTab = "posts"
                    updateTabIcons()
                    currentUser?.let { user ->
                        viewModel.userPosts.value?.let { posts ->
                            updatePostsGrid(posts)
                        } ?: viewModel.loadUserPosts(user.firebaseUid)
                    }
                }
            }

            val tabFavorites = findViewById<View>(R.id.tab_favorites)
            tabFavorites?.setOnClickListener {
                println("🔘 Favorites tab clicked")
                if (currentTab != "favorites") {
                    currentTab = "favorites"
                    updateTabIcons()
                    currentUser?.let { user ->
                        viewModel.favoritePosts.value?.let { favorites ->
                            updateFavoritesGrid(favorites)
                        } ?: viewModel.loadFavoritePosts(user.firebaseUid)
                    }
                }
            }

            println("✅ All click listeners setup complete")

        } catch (e: Exception) {
            println("❌ Error setting up click listeners: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun updateTabIcons() {
        val tabPostsIcon = findViewById<ImageView>(R.id.tab_posts_icon)
        val tabFavoritesIcon = findViewById<ImageView>(R.id.tab_favorites_icon)
        val tabIndicator = findViewById<View>(R.id.tab_indicator)
        val tabPosts = findViewById<View>(R.id.tab_posts)
        val tabFavorites = findViewById<View>(R.id.tab_favorites)

        // Make sure views exist before proceeding
        if (tabPosts == null || tabFavorites == null || tabIndicator == null) {
            return
        }

        tabPosts.post {
            if (currentTab == "posts") {
                // Set colors
                tabPostsIcon?.colorFilter = PorterDuffColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                tabFavoritesIcon?.colorFilter = PorterDuffColorFilter(Color.parseColor("#999999"), PorterDuff.Mode.SRC_ATOP)

                // Move indicator to posts tab
                val targetX = tabPosts.x
                val targetWidth = tabPosts.width

                val layoutParams = tabIndicator.layoutParams
                layoutParams.width = targetWidth
                tabIndicator.layoutParams = layoutParams

                tabIndicator.animate()
                    ?.x(targetX)
                    ?.setDuration(200)
                    ?.start()
            } else {
                // Set colors
                tabFavoritesIcon?.colorFilter = PorterDuffColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                tabPostsIcon?.colorFilter = PorterDuffColorFilter(Color.parseColor("#999999"), PorterDuff.Mode.SRC_ATOP)

                // Move indicator to favorites tab
                val targetX = tabFavorites.x
                val targetWidth = tabFavorites.width

                val layoutParams = tabIndicator.layoutParams
                layoutParams.width = targetWidth
                tabIndicator.layoutParams = layoutParams

                tabIndicator.animate()
                    ?.x(targetX)
                    ?.setDuration(200)
                    ?.start()
            }
        }
    }

    private fun shareProfile() {
        currentUser?.let { user ->
            val shareText = "Check out my profile on PawSociety!\n\nUsername: ${user.username}\n\nDownload the app now!"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, "Share profile via"))
        }
    }

    private fun updateProfileWithUserData(user: ApiUser) {
        try {
            val tvUsername = findViewById<TextView>(R.id.tv_username)
            tvUsername?.text = user.username
            println("✅ Username set to: ${user.username}")

            setProfilePicture(user)

            // Instagram Style Bio
            val tvFullName = findViewById<TextView>(R.id.tv_full_name)
            tvFullName?.text = user.fullName
            tvFullName?.visibility = View.VISIBLE

            val tvUsernameBio = findViewById<TextView>(R.id.tv_username_bio)
            tvUsernameBio?.text = "@${user.username}"
            tvUsernameBio?.visibility = View.VISIBLE

            val tvBioText = findViewById<TextView>(R.id.tv_bio_text)
            tvBioText?.text = user.bio ?: ""

            val tvLocationBio = findViewById<TextView>(R.id.tv_location_bio)
            if (!user.location.isNullOrEmpty()) {
                tvLocationBio?.text = user.location
                tvLocationBio?.visibility = View.VISIBLE
            } else {
                tvLocationBio?.visibility = View.GONE
            }

            println("✅ Bio updated - Full Name: ${user.fullName}, Username: @${user.username}")

            val tvPostCount = findViewById<TextView>(R.id.tv_post_count)
            tvPostCount?.text = viewModel.userPosts.value?.size.toString() ?: "0"

            // Fetch real follower counts
            lifecycleScope.launch {
                try {
                    val followRepository = com.example.pawsociety.data.repository.FollowRepository()

                    val followersResult = followRepository.getFollowersCount(user.firebaseUid)
                    val tvFollowerCount = findViewById<TextView>(R.id.tv_follower_count)
                    if (followersResult.isSuccess) {
                        tvFollowerCount?.text = followersResult.getOrNull().toString()
                    } else {
                        tvFollowerCount?.text = "0"
                    }

                    val followingResult = followRepository.getFollowingCount(user.firebaseUid)
                    val tvFollowingCount = findViewById<TextView>(R.id.tv_following_count)
                    if (followingResult.isSuccess) {
                        tvFollowingCount?.text = followingResult.getOrNull().toString()
                    } else {
                        tvFollowingCount?.text = "0"
                    }
                } catch (e: Exception) {
                    println("❌ Error loading follower counts: ${e.message}")
                }
            }

            if (currentTab == "posts") {
                viewModel.userPosts.value?.let { posts ->
                    updatePostsGrid(posts)
                }
            } else {
                viewModel.favoritePosts.value?.let { favorites ->
                    updateFavoritesGrid(favorites)
                }
            }
        } catch (e: Exception) {
            println("❌ Error in updateProfileWithUserData: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setProfilePicture(user: ApiUser) {
        try {
            val profileInitial = findViewById<TextView>(R.id.profile_initial)
            val profileImage = findViewById<ImageView>(R.id.profile_image)
            val profileBackground = findViewById<View>(R.id.profile_circle_background)

            if (!user.profileImageUrl.isNullOrEmpty()) {
                val fullImageUrl = if (user.profileImageUrl.startsWith("http")) {
                    user.profileImageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${user.profileImageUrl}"
                }

                profileImage?.visibility = View.VISIBLE
                profileInitial?.visibility = View.GONE

                Glide.with(this)
                    .load(fullImageUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(profileImage!!)
            } else {
                profileImage?.visibility = View.GONE
                profileInitial?.visibility = View.VISIBLE

                val color = generateColorFromUsername(user.username)
                val backgroundDrawable = ContextCompat.getDrawable(this, R.drawable.circle_solid_profile)
                backgroundDrawable?.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
                profileBackground?.background = backgroundDrawable

                val firstName = if (user.fullName.contains(",")) {
                    val parts = user.fullName.split(", ")
                    if (parts.size > 1) {
                        parts[1].split(" ").firstOrNull() ?: ""
                    } else {
                        user.fullName
                    }
                } else {
                    user.fullName.split(" ").firstOrNull() ?: ""
                }

                val firstLetter = if (firstName.isNotEmpty()) {
                    firstName.first().toString().uppercase()
                } else {
                    "?"
                }

                profileInitial?.text = firstLetter
                profileInitial?.textSize = 36f
            }
        } catch (e: Exception) {
            println("❌ Error in setProfilePicture: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun generateColorFromUsername(username: String): Int {
        val colors = listOf(
            "#FF6B35", "#4CAF50", "#2196F3", "#9C27B0",
            "#F44336", "#009688", "#FF9800", "#3F51B5",
            "#E91E63", "#7A4F2B"
        )
        val hash = Math.abs(username.hashCode())
        val index = hash % colors.size
        return Color.parseColor(colors[index])
    }

    private fun updateHighlightsDisplay(highlights: List<ApiHighlight>) {
        try {
            val highlightsContainer = findViewById<LinearLayout>(R.id.highlights_container)
            highlightsContainer?.removeAllViews()
            addNewHighlightButton(highlightsContainer)
            for (highlight in highlights) {
                addHighlightView(highlightsContainer, highlight)
            }
        } catch (e: Exception) {
            println("❌ Error in updateHighlightsDisplay: ${e.message}")
        }
    }

    private fun addNewHighlightButton(container: LinearLayout?) {
        if (container == null) return
        try {
            val newHighlightView = layoutInflater.inflate(R.layout.item_highlight_new, container, false)
            newHighlightView.setOnClickListener { view ->
                view.animate().scaleX(0.8f).scaleY(0.8f).setDuration(150).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }.start()
                showCreateHighlightDialog()
            }
            container.addView(newHighlightView)
        } catch (e: Exception) {
            println("❌ Error in addNewHighlightButton: ${e.message}")
        }
    }

    private fun addHighlightView(container: LinearLayout?, highlight: ApiHighlight) {
        if (container == null) return
        try {
            val highlightView = layoutInflater.inflate(R.layout.item_highlight, container, false)

            val circleBackground = highlightView.findViewById<View>(R.id.highlight_circle_background)
            val circleImage = highlightView.findViewById<ImageView>(R.id.highlight_circle_image)
            val circleText = highlightView.findViewById<TextView>(R.id.highlight_circle_text)
            val titleText = highlightView.findViewById<TextView>(R.id.highlight_title)

            if (!highlight.imageUrl.isNullOrEmpty()) {
                circleImage.visibility = View.VISIBLE
                circleText.visibility = View.GONE
                circleBackground.visibility = View.GONE

                val fullImageUrl = if (highlight.imageUrl.startsWith("http")) {
                    highlight.imageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${highlight.imageUrl}"
                }

                Glide.with(this)
                    .load(fullImageUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(circleImage)
            } else {
                circleImage.visibility = View.GONE
                circleText.visibility = View.VISIBLE
                circleBackground.visibility = View.VISIBLE

                val backgroundDrawable = ContextCompat.getDrawable(this, R.drawable.circle_solid_highlight)
                backgroundDrawable?.setColorFilter(Color.parseColor(highlight.color), PorterDuff.Mode.SRC_ATOP)
                circleBackground?.background = backgroundDrawable

                circleText?.text = highlight.emoji
            }

            titleText?.text = highlight.name

            highlightView.setOnClickListener { view ->
                view.animate().scaleX(0.8f).scaleY(0.8f).setDuration(150).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }.start()
                Toast.makeText(this, "Viewing ${highlight.name} highlight", Toast.LENGTH_SHORT).show()
            }
            container.addView(highlightView)
        } catch (e: Exception) {
            println("❌ Error in addHighlightView: ${e.message}")
        }
    }

    private fun showCreateHighlightDialog() {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_highlight, null)
            currentDialogView = dialogView

            val etName = dialogView.findViewById<EditText>(R.id.et_highlight_name)
            val btnSelectImage = dialogView.findViewById<LinearLayout>(R.id.btn_select_image)
            val highlightImagePreview = dialogView.findViewById<ImageView>(R.id.highlight_image_preview)
            val postsContainer = dialogView.findViewById<LinearLayout>(R.id.posts_container)

            if (selectedHighlightImageUri != null) {
                highlightImagePreview?.setImageURI(selectedHighlightImageUri)
                highlightImagePreview?.visibility = View.VISIBLE
            }

            btnSelectImage?.setOnClickListener {
                highlightImagePickerLauncher.launch("image/*")
            }

            loadUserPostsForHighlights(postsContainer)

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create()

            dialog.setOnShowListener {
                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.setOnClickListener {
                    val name = etName?.text.toString().trim()
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Please enter a highlight name", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val selectedPostIds = emptyList<String>()
                    viewModel.createHighlight(
                        name = name,
                        emoji = "📸",
                        color = "#FF6B35",
                        imageUrl = null,
                        postIds = selectedPostIds
                    )
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (e: Exception) {
            println("❌ Error in showCreateHighlightDialog: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Could not create highlight", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserPostsForHighlights(container: LinearLayout?) {
        if (container == null) return
        container.removeAllViews()

        viewModel.userPosts.observe(this) { userPosts ->
            if (userPosts.isNotEmpty()) {
                val posts = userPosts.take(5)
                for (post in posts) {
                    try {
                        val postItem = layoutInflater.inflate(R.layout.item_highlight_post, container, false)
                        val postImage = postItem.findViewById<ImageView>(R.id.post_image)

                        if (!post.imageUrls.isNullOrEmpty() && post.imageUrls.isNotEmpty()) {
                            val imageUrl = post.imageUrls[0]
                            val fullImageUrl = if (imageUrl.startsWith("http")) {
                                imageUrl
                            } else {
                                "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
                            }

                            Glide.with(this@ProfileActivity)
                                .load(fullImageUrl)
                                .centerCrop()
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .into(postImage!!)
                        }

                        postItem.setOnClickListener {
                            selectedHighlightImageUri = null
                            Toast.makeText(this@ProfileActivity, "Selected: ${post.petName}", Toast.LENGTH_SHORT).show()
                            val previewImage = currentDialogView?.findViewById<ImageView>(R.id.highlight_image_preview)
                            previewImage?.setImageDrawable(postImage?.drawable)
                            previewImage?.visibility = View.VISIBLE
                        }
                        container.addView(postItem)
                    } catch (e: Exception) {
                        println("❌ Error loading post for highlight: ${e.message}")
                    }
                }
            }
        }
    }

    private fun parseFullName(fullName: String): Triple<String, String, String> {
        return try {
            val parts = fullName.split(", ")
            val lastName = parts.getOrElse(0) { "" }
            val firstMiddle = parts.getOrElse(1) { "" }.split(" ")
            val firstName = firstMiddle.getOrElse(0) { "" }
            val middleInitial = if (firstMiddle.size > 1)
                firstMiddle[1].replace(".", "") else ""
            Triple(lastName, firstName, middleInitial)
        } catch (e: Exception) {
            val nameParts = fullName.split(" ")
            when (nameParts.size) {
                1 -> Triple(nameParts[0], "", "")
                2 -> Triple(nameParts[0], nameParts[1], "")
                3 -> Triple(nameParts[0], nameParts[1], nameParts[2])
                else -> Triple("", "", "")
            }
        }
    }

    private fun updatePostsGrid(posts: List<ApiPost>) {
        val postsGrid = findViewById<LinearLayout>(R.id.posts_grid)
        val bigPlusButton = findViewById<View>(R.id.big_plus_button)

        findViewById<TextView>(R.id.tv_post_count)?.text = posts.size.toString()

        if (posts.isNotEmpty()) {
            bigPlusButton?.visibility = View.GONE
            postsGrid?.visibility = View.VISIBLE
            createPostsGrid(postsGrid!!, posts)
        } else {
            bigPlusButton?.visibility = View.VISIBLE
            postsGrid?.visibility = View.GONE
        }
    }

    private fun updateFavoritesGrid(favorites: List<ApiPost>) {
        val postsGrid = findViewById<LinearLayout>(R.id.posts_grid)
        val bigPlusButton = findViewById<View>(R.id.big_plus_button)

        bigPlusButton?.visibility = View.GONE
        postsGrid?.visibility = View.VISIBLE
        postsGrid?.removeAllViews()

        if (favorites.isNotEmpty()) {
            createPostsGrid(postsGrid!!, favorites)
        } else {
            val emptyView = TextView(this)
            emptyView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            emptyView.gravity = android.view.Gravity.CENTER
            emptyView.text = "No favorites yet\nSave posts you like from the home feed!"
            emptyView.setTextColor(Color.parseColor("#999999"))
            emptyView.textSize = 16f
            emptyView.setPadding(0, 100, 0, 100)
            postsGrid?.addView(emptyView)
        }
    }

    private fun createPostsGrid(container: LinearLayout, posts: List<ApiPost>) {
        try {
            container.removeAllViews()

            if (posts.isEmpty()) {
                val emptyView = TextView(this)
                emptyView.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                emptyView.gravity = android.view.Gravity.CENTER
                emptyView.text = "No posts yet"
                emptyView.setTextColor(Color.parseColor("#999999"))
                emptyView.textSize = 16f
                emptyView.setPadding(0, 100, 0, 100)
                container.addView(emptyView)
                return
            }

            val colors = listOf(
                "#4CAF50", "#2196F3", "#FF9800",
                "#9C27B0", "#FF5722", "#00BCD4",
                "#E91E63", "#3F51B5", "#009688"
            )

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val spacing = 2
            val columns = 3

            val itemSize = (screenWidth - (spacing * (columns - 1))) / columns

            val rows = (posts.size + columns - 1) / columns
            var postIndex = 0

            for (row in 0 until rows) {
                val rowLayout = LinearLayout(this)
                rowLayout.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                rowLayout.orientation = LinearLayout.HORIZONTAL

                for (col in 0 until columns) {
                    if (postIndex >= posts.size) {
                        val emptyContainer = FrameLayout(this)
                        val emptyParams = LinearLayout.LayoutParams(
                            itemSize,
                            itemSize
                        )
                        if (col > 0) {
                            emptyParams.leftMargin = spacing
                        }
                        emptyContainer.layoutParams = emptyParams
                        rowLayout.addView(emptyContainer)
                        continue
                    }

                    val squareContainer = FrameLayout(this)
                    val squareParams = LinearLayout.LayoutParams(
                        itemSize,
                        itemSize
                    )

                    if (col > 0) {
                        squareParams.leftMargin = spacing
                    }

                    squareContainer.layoutParams = squareParams

                    val post = posts[postIndex]
                    val postView = layoutInflater.inflate(R.layout.item_profile_post, squareContainer, false)
                    val postContent = postView.findViewById<TextView>(R.id.post_content)
                    val postImage = postView.findViewById<ImageView>(R.id.post_image)

                    if (!post.imageUrls.isNullOrEmpty() && post.imageUrls.isNotEmpty()) {
                        val imageUrl = post.imageUrls[0]
                        val fullImageUrl = if (imageUrl.startsWith("http")) {
                            imageUrl
                        } else {
                            "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
                        }

                        postImage.visibility = View.VISIBLE
                        postContent.visibility = View.GONE

                        Glide.with(this)
                            .load(fullImageUrl)
                            .centerCrop()
                            .override(itemSize, itemSize)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .into(postImage)
                    } else {
                        postImage.visibility = View.GONE
                        postContent.visibility = View.VISIBLE

                        val emoji = when {
                            post.petType.contains("dog", ignoreCase = true) -> "🐶"
                            post.petType.contains("cat", ignoreCase = true) -> "🐱"
                            post.petType.contains("bird", ignoreCase = true) -> "🐦"
                            post.petType.contains("rabbit", ignoreCase = true) -> "🐰"
                            post.petType.contains("fish", ignoreCase = true) -> "🐟"
                            else -> "🐾"
                        }

                        postContent.text = "$emoji\n${post.petName}"
                        postContent.setBackgroundColor(Color.parseColor(colors[postIndex % colors.size]))
                        postContent.setTextColor(Color.WHITE)
                        postContent.textSize = 14f
                    }

                    val currentPosition = postIndex
                    postView.setOnClickListener {
                        val intent = Intent(this@ProfileActivity, PostViewActivity::class.java)
                        intent.putExtra("all_posts", ArrayList(posts))
                        intent.putExtra("position", currentPosition)
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }

                    squareContainer.addView(postView)
                    rowLayout.addView(squareContainer)
                    postIndex++
                }

                if (row < rows - 1) {
                    val rowParams = rowLayout.layoutParams as LinearLayout.LayoutParams
                    rowParams.bottomMargin = spacing
                    rowLayout.layoutParams = rowParams
                }

                container.addView(rowLayout)
            }
        } catch (e: Exception) {
            println("❌ Error in createPostsGrid: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun getPetEmoji(petType: String): String {
        return when {
            petType.contains("dog", ignoreCase = true) -> "🐶"
            petType.contains("cat", ignoreCase = true) -> "🐱"
            petType.contains("bird", ignoreCase = true) -> "🐦"
            petType.contains("rabbit", ignoreCase = true) -> "🐰"
            petType.contains("fish", ignoreCase = true) -> "🐟"
            else -> "🐾"
        }
    }

    private fun showPostDetails(post: ApiPost) {
        val details = """
            🐾 ${post.petName}
            👤 Posted by: ${post.userName}
            📍 ${post.location}
            🔍 Status: ${post.status}
            💰 ${if (!post.reward.isNullOrEmpty()) "Reward: ${post.reward}" else "No reward"}
            📞 ${post.contactInfo}

            ${post.description}

            🕒 ${post.createdAt}
        """.trimIndent()

        Toast.makeText(this, details, Toast.LENGTH_LONG).show()
    }

    private fun loadPostsTab(user: ApiUser) {
        try {
            val postsGrid = findViewById<LinearLayout>(R.id.posts_grid)
            val bigPlusButton = findViewById<View>(R.id.big_plus_button)

            viewModel.userPosts.observe(this) { userPosts ->
                postsGrid?.removeAllViews()
                if (userPosts.isNotEmpty()) {
                    bigPlusButton?.visibility = View.GONE
                    postsGrid?.visibility = View.VISIBLE
                    createPostsGrid(postsGrid!!, userPosts)
                    findViewById<TextView>(R.id.tv_post_count)?.text = userPosts.size.toString()
                } else {
                    bigPlusButton?.visibility = View.VISIBLE
                    postsGrid?.visibility = View.GONE
                    findViewById<TextView>(R.id.tv_post_count)?.text = "0"
                }
            }
        } catch (e: Exception) {
            println("❌ Error in loadPostsTab: ${e.message}")
        }
    }

    private fun loadFavoritesTab(user: ApiUser) {
        try {
            val postsGrid = findViewById<LinearLayout>(R.id.posts_grid)
            val bigPlusButton = findViewById<View>(R.id.big_plus_button)

            postsGrid?.removeAllViews()
            bigPlusButton?.visibility = View.GONE
            postsGrid?.visibility = View.VISIBLE

            viewModel.favoritePosts.observe(this) { favoritePosts ->
                postsGrid?.removeAllViews()
                if (favoritePosts.isNotEmpty()) {
                    createPostsGrid(postsGrid!!, favoritePosts)
                } else {
                    val emptyView = TextView(this)
                    emptyView.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    emptyView.gravity = android.view.Gravity.CENTER
                    emptyView.text = "No favorites yet\nSave posts you like from the home feed!"
                    emptyView.setTextColor(Color.parseColor("#999999"))
                    emptyView.textSize = 16f
                    emptyView.setPadding(0, 100, 0, 100)
                    postsGrid?.addView(emptyView)
                }
            }
        } catch (e: Exception) {
            println("❌ Error in loadFavoritesTab: ${e.message}")
        }
    }

    private fun showEditProfileDialog(user: ApiUser) {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null)

            val etLastName = dialogView.findViewById<EditText>(R.id.et_last_name)
            val etFirstName = dialogView.findViewById<EditText>(R.id.et_first_name)
            val etMiddleInitial = dialogView.findViewById<EditText>(R.id.et_middle_initial)
            val etUsername = dialogView.findViewById<EditText>(R.id.et_username)
            val etBio = dialogView.findViewById<EditText>(R.id.et_bio)
            val profileImage = dialogView.findViewById<ImageView>(R.id.edit_profile_image)
            val btnChangePhoto = dialogView.findViewById<TextView>(R.id.btn_change_photo)

            val (lastName, firstName, middleInitial) = parseFullName(user.fullName)
            etLastName?.setText(lastName)
            etFirstName?.setText(firstName)
            etMiddleInitial?.setText(middleInitial)
            etUsername?.setText(user.username)
            etBio?.setText(user.bio)

            if (!user.profileImageUrl.isNullOrEmpty()) {
                val fullImageUrl = if (user.profileImageUrl.startsWith("http")) {
                    user.profileImageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${user.profileImageUrl}"
                }
                Glide.with(this)
                    .load(fullImageUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(profileImage!!)
            }

            btnChangePhoto?.setOnClickListener {
                showImageSourceDialog()
            }

            setupValidation(etLastName!!, etFirstName!!, etUsername!!)

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create()

            dialog.setOnShowListener {
                val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                button.setOnClickListener {
                    lifecycleScope.launch {
                        if (validateFields(etLastName, etFirstName, etUsername)) {
                            val newLastName = etLastName.text.toString().trim()
                            val newFirstName = etFirstName.text.toString().trim()
                            val newMiddleInitial = etMiddleInitial?.text.toString().trim()

                            val newFullName = if (newMiddleInitial.isNotEmpty()) {
                                "$newLastName, $newFirstName $newMiddleInitial."
                            } else {
                                "$newLastName, $newFirstName"
                            }

                            val newUsername = etUsername.text.toString().trim()
                            val newBio = etBio?.text.toString().trim()

                            viewModel.updateProfile(
                                fullName = newFullName,
                                username = newUsername,
                                bio = newBio
                            )

                            Toast.makeText(this@ProfileActivity, "Profile updated!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    }
                }
            }

            dialog.show()
        } catch (e: Exception) {
            println("❌ Error in showEditProfileDialog: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Could not open edit profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupValidation(
        etLastName: EditText,
        etFirstName: EditText,
        etUsername: EditText
    ) {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateFields(etLastName, etFirstName, etUsername)
            }
        }
        etLastName.addTextChangedListener(textWatcher)
        etFirstName.addTextChangedListener(textWatcher)
        etUsername.addTextChangedListener(textWatcher)
    }

    private fun validateFields(
        etLastName: EditText,
        etFirstName: EditText,
        etUsername: EditText
    ): Boolean {
        var isValid = true

        val lastName = etLastName.text.toString().trim()
        if (lastName.isEmpty()) {
            etLastName.error = "Last name is required"
            isValid = false
        } else if (lastName.length < 2) {
            etLastName.error = "Last name must be at least 2 characters"
            isValid = false
        } else if (!lastName.matches(Regex("^[a-zA-Z\\s.-]+$"))) {
            etLastName.error = "Only letters, spaces, dots, and hyphens allowed"
            isValid = false
        } else {
            etLastName.error = null
        }

        val firstName = etFirstName.text.toString().trim()
        if (firstName.isEmpty()) {
            etFirstName.error = "First name is required"
            isValid = false
        } else if (firstName.length < 2) {
            etFirstName.error = "First name must be at least 2 characters"
            isValid = false
        } else if (!firstName.matches(Regex("^[a-zA-Z\\s.-]+$"))) {
            etFirstName.error = "Only letters, spaces, dots, and hyphens allowed"
            isValid = false
        } else {
            etFirstName.error = null
        }

        val username = etUsername.text.toString().trim()
        if (username.isEmpty()) {
            etUsername.error = "Username is required"
            isValid = false
        } else if (username.length < 3) {
            etUsername.error = "Username must be at least 3 characters"
            isValid = false
        } else if (username.length > 20) {
            etUsername.error = "Username must be less than 20 characters"
            isValid = false
        } else if (!username.matches(Regex("^[a-zA-Z0-9._]+$"))) {
            etUsername.error = "Only letters, numbers, dots, and underscores allowed"
            isValid = false
        } else {
            etUsername.error = null
        }

        return isValid
    }

    override fun onResume() {
        super.onResume()
        try {
            val currentUser = sessionManager.getCurrentUser()
            currentUser?.let {
                this.currentUser = it
                updateProfileWithUserData(it)
                viewModel.refreshData()
            }

            // Force stop any stuck refresh indicator
            val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
            swipeRefreshLayout?.isRefreshing = false

        } catch (e: Exception) {
            println("❌ Error in onResume: ${e.message}")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}