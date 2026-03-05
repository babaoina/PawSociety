package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class EditPostActivity : AppCompatActivity() {

    private lateinit var etPetName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etContact: EditText
    private lateinit var tvLocation: TextView
    private lateinit var btnSelectLocation: Button
    private lateinit var btnSave: TextView
    private lateinit var btnCancel: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var sessionManager: SessionManager
    private val postRepository = PostRepository()
    private var post: ApiPost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_post)

        sessionManager = SessionManager(this)

        // Get post from intent
        post = intent.getSerializableExtra("post") as? ApiPost
        if (post == null) {
            Toast.makeText(this, "Post not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        loadPostData()
    }

    private fun initializeViews() {
        etPetName = findViewById(R.id.et_pet_name)
        etDescription = findViewById(R.id.et_description)
        etContact = findViewById(R.id.et_contact)
        tvLocation = findViewById(R.id.tv_location)
        btnSelectLocation = findViewById(R.id.btn_select_location)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        progressBar = findViewById(R.id.progress_bar)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            saveChanges()
        }

        btnCancel.setOnClickListener {
            finish()
        }

        // Location picker
        btnSelectLocation.setOnClickListener {
            try {
                val dialog = LocationPickerDialog(this) { fullLocation ->
                    tvLocation.text = fullLocation
                    tvLocation.visibility = View.VISIBLE
                }
                dialog.show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error opening location picker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPostData() {
        post?.let { post ->
            etPetName.setText(post.petName)
            etDescription.setText(post.description)
            etContact.setText(post.contactInfo)

            if (!post.location.isNullOrEmpty()) {
                tvLocation.text = post.location
                tvLocation.visibility = View.VISIBLE
            } else {
                tvLocation.text = "No location selected"
                tvLocation.visibility = View.GONE
            }
        }
    }

    private fun saveChanges() {
        val currentUser = sessionManager.getCurrentUser() ?: run {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val post = post ?: return

        // DEBUG: Print the post ID being sent
        println("========== EDIT POST DEBUG ==========")
        println("Post ID being sent: '${post.postId}'")
        println("Post ID length: ${post.postId.length}")
        println("Current User UID: ${currentUser.firebaseUid}")
        println("======================================")

        val updatedPetName = etPetName.text.toString().trim()
        val updatedDescription = etDescription.text.toString().trim()
        val updatedContact = etContact.text.toString().trim()
        val updatedLocation = if (tvLocation.text.toString() != "No location selected")
            tvLocation.text.toString() else ""

        if (updatedPetName.isEmpty()) {
            etPetName.error = "Pet name is required"
            return
        }

        if (updatedDescription.isEmpty()) {
            etDescription.error = "Description is required"
            return
        }

        if (updatedDescription.length < 10) {
            etDescription.error = "Description must be at least 10 characters"
            return
        }

        if (updatedContact.isEmpty()) {
            etContact.error = "Contact number is required"
            return
        }

        if (!updatedContact.matches(Regex("^09\\d{9}$"))) {
            etContact.error = "Must be 11 digits starting with 09"
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                // Create a simple map with string values
                val updateMap = HashMap<String, String>()
                updateMap["petName"] = updatedPetName
                updateMap["description"] = updatedDescription
                updateMap["contactInfo"] = updatedContact
                updateMap["location"] = updatedLocation

                println("📤 Sending update to backend:")
                println("   Post ID: ${post.postId}")
                println("   Update data: $updateMap")

                val result = postRepository.updatePost(post.postId, updateMap, currentUser.firebaseUid)

                if (result.isSuccess) {
                    println("✅ Update successful!")
                    Toast.makeText(this@EditPostActivity, "Post updated successfully!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    println("❌ Update failed: $errorMsg")
                    Toast.makeText(this@EditPostActivity, "Failed: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                println("❌ Exception in update: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@EditPostActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
            }
        }
    }

    companion object {
        const val EDIT_POST_REQUEST = 1002
    }
}