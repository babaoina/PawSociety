package com.example.pawsociety

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.pawsociety.data.repository.UploadRepository
import com.example.pawsociety.util.FileHelper
import com.example.pawsociety.util.KeyboardAwareScrollHelper
import com.example.pawsociety.util.PermissionHelper
import com.example.pawsociety.util.SessionManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: ProfileViewModel
    private val uploadRepository = UploadRepository()
    private var currentUser: com.example.pawsociety.api.ApiUser? = null
    private var selectedProfileImageUri: Uri? = null
    private var currentPhotoPath: String? = null
    private var isUploading = false
    private var pendingPermissionAction = ""

    // Views
    private lateinit var etLastName: EditText
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleInitial: EditText
    private lateinit var etUsername: EditText
    private lateinit var etBio: EditText
    private lateinit var profileImage: ImageView
    private lateinit var btnChangePhoto: TextView
    private lateinit var btnSave: TextView
    private lateinit var btnBack: ImageView
    private lateinit var tvUsernameTimer: TextView
    private var originalUsername: String = ""

    companion object {
        private const val PREFS_EDIT_PROFILE = "edit_profile_prefs"
        private const val KEY_LAST_USERNAME_CHANGE_AT = "last_username_change_at"
        private const val USERNAME_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000
        private const val STATE_CURRENT_IMAGE_URI = "state_current_image_uri"
        private const val STATE_CURRENT_PHOTO_PATH = "state_current_photo_path"
    }

    // Gallery launcher
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedProfileImageUri = it
            startCrop(it)
        }
    }

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val capturedUri = resolveCapturedImageUri()
            if (capturedUri != null) {
                selectedProfileImageUri = capturedUri
                startCrop(capturedUri)
            } else {
                Toast.makeText(this, "Captured image could not be found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // UCrop result launcher
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            resultUri?.let { croppedUri ->
                selectedProfileImageUri = croppedUri
                profileImage.setImageURI(null)
                profileImage.setImageURI(croppedUri)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        sessionManager = SessionManager(this)
        currentUser = sessionManager.getCurrentUser()

        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        viewModel.setSessionManager(sessionManager)

        initViews()
        loadUserData()
        setupClickListeners()
        setupValidation()
        setupKeyboardScrollHandlers()

        selectedProfileImageUri = savedInstanceState?.getParcelable(STATE_CURRENT_IMAGE_URI)
        currentPhotoPath = savedInstanceState?.getString(STATE_CURRENT_PHOTO_PATH)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_CURRENT_IMAGE_URI, selectedProfileImageUri)
        outState.putString(STATE_CURRENT_PHOTO_PATH, currentPhotoPath)
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        btnSave = findViewById(R.id.btn_save)
        etLastName = findViewById(R.id.et_last_name)
        etFirstName = findViewById(R.id.et_first_name)
        etMiddleInitial = findViewById(R.id.et_middle_initial)
        etUsername = findViewById(R.id.et_username)
        etBio = findViewById(R.id.et_bio)
        profileImage = findViewById(R.id.edit_profile_image)
        btnChangePhoto = findViewById(R.id.btn_change_photo)
        tvUsernameTimer = findViewById(R.id.tv_username_timer)
    }

    private fun resolveCapturedImageUri(): Uri? {
        selectedProfileImageUri?.let { return it }

        val photoPath = currentPhotoPath ?: return null
        val file = File(photoPath)
        if (!file.exists() || file.length() == 0L) {
            return null
        }

        return Uri.fromFile(file)
    }

    private fun loadUserData() {
        currentUser?.let { user ->
            val (lastName, firstName, middleInitial) = parseFullName(user.fullName)
            etLastName.setText(lastName)
            etFirstName.setText(firstName)
            etMiddleInitial.setText(middleInitial)
            etUsername.setText(user.username)
            originalUsername = user.username
            etBio.setText(user.bio)
            updateUsernameCooldownUI()

            if (!user.profileImageUrl.isNullOrEmpty()) {
                val fullImageUrl = if (user.profileImageUrl.startsWith("http")) {
                    user.profileImageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${user.profileImageUrl}"
                }
                Glide.with(this)
                    .load(fullImageUrl)
                    .circleCrop()
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(profileImage)
            }
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            if (validateFields()) {
                saveProfile()
            }
        }

        btnChangePhoto.setOnClickListener {
            showImageSourceDialog()
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")

        AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
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

    private fun setupKeyboardScrollHandlers() {
        KeyboardAwareScrollHelper.attach(
            etLastName,
            etFirstName,
            etMiddleInitial,
            etUsername,
            etBio
        )
    }

    private fun checkCameraPermissionAndOpen() {
        if (PermissionHelper.hasCameraPermission(this)) {
            openCamera()
        } else {
            pendingPermissionAction = "camera"
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun checkStoragePermissionAndOpen() {
        if (PermissionHelper.hasStoragePermission(this)) {
            openGallery()
        } else {
            pendingPermissionAction = "gallery"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
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
            FileHelper.deleteFile(file)
            if (compressedFile != file) {
                FileHelper.deleteFile(compressedFile)
            }

            if (result.isSuccess) {
                val imageUrl = result.getOrNull()
                Toast.makeText(this, "Image uploaded successfully!", Toast.LENGTH_SHORT).show()

                // Update profile with new image
                currentUser?.let { user ->
                    viewModel.updateProfile(
                        profileImageUrl = imageUrl
                    )
                    currentUser = user.copy(profileImageUrl = imageUrl)
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

    private fun saveProfile() {
        val newLastName = etLastName.text.toString().trim()
        val newFirstName = etFirstName.text.toString().trim()
        val newMiddleInitial = etMiddleInitial.text.toString().trim()

        val newFullName = if (newMiddleInitial.isNotEmpty()) {
            "$newLastName, $newFirstName $newMiddleInitial."
        } else {
            "$newLastName, $newFirstName"
        }

        val newUsername = etUsername.text.toString().trim()
        val newBio = etBio.text.toString().trim()

        if (newUsername != originalUsername) {
            val remaining = getUsernameCooldownRemaining()
            if (remaining > 0) {
                Toast.makeText(
                    this,
                    "You can change your username again in ${formatCooldown(remaining)}",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        viewModel.updateProfile(
            fullName = newFullName,
            username = newUsername,
            bio = newBio
        )

        if (newUsername != originalUsername) {
            getSharedPreferences(PREFS_EDIT_PROFILE, MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_USERNAME_CHANGE_AT, System.currentTimeMillis())
                .apply()
            originalUsername = newUsername
        }

        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
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

    private fun setupValidation() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateFields()
            }
        }
        etLastName.addTextChangedListener(textWatcher)
        etFirstName.addTextChangedListener(textWatcher)
        etUsername.addTextChangedListener(textWatcher)
    }

    private fun validateFields(): Boolean {
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

    private fun updateUsernameCooldownUI() {
        val remaining = getUsernameCooldownRemaining()
        val isLocked = remaining > 0

        etUsername.isEnabled = !isLocked
        etUsername.isFocusable = !isLocked
        etUsername.isFocusableInTouchMode = !isLocked
        etUsername.isClickable = !isLocked
        etUsername.alpha = if (isLocked) 0.65f else 1.0f

        tvUsernameTimer.text = if (isLocked) {
            "Username is locked. You can change it again in ${formatCooldown(remaining)}."
        } else {
            "You can change your username now. After that, the next change is available in 30 days."
        }
    }

    private fun getUsernameCooldownRemaining(): Long {
        val prefs = getSharedPreferences(PREFS_EDIT_PROFILE, MODE_PRIVATE)
        val lastChangedAt = prefs.getLong(KEY_LAST_USERNAME_CHANGE_AT, 0L)
        if (lastChangedAt <= 0L) return 0L

        val nextAllowedAt = lastChangedAt + USERNAME_COOLDOWN_MS
        return (nextAllowedAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun formatCooldown(remainingMs: Long): String {
        val totalHours = remainingMs / (60 * 60 * 1000)
        val days = totalHours / 24
        val hours = totalHours % 24

        return when {
            days > 0 -> "$days day${if (days == 1L) "" else "s"}${if (hours > 0) " and $hours hour${if (hours == 1L) "" else "s"}" else ""}"
            hours > 0 -> "$hours hour${if (hours == 1L) "" else "s"}"
            else -> "less than 1 hour"
        }
    }
}
