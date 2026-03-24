package com.example.pawsociety

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.KeyboardAwareScrollHelper
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.launch

class DeleteAccountActivity : AppCompatActivity() {

    private lateinit var etPassword: EditText
    private lateinit var btnDelete: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var cbConfirm: CheckBox
    private lateinit var deletionProgressContainer: LinearLayout
    private lateinit var deletionStatusText: TextView

    private lateinit var sessionManager: SessionManager
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_account)

        sessionManager = SessionManager(this)

        initializeViews()
        KeyboardAwareScrollHelper.attach(etPassword)
        setupClickListeners()
    }

    private fun initializeViews() {
        etPassword = findViewById(R.id.et_password)
        btnDelete = findViewById(R.id.btn_delete)
        btnCancel = findViewById(R.id.btn_cancel)
        progressBar = findViewById(R.id.progress_bar)
        cbConfirm = findViewById(R.id.cb_confirm)
        deletionProgressContainer = findViewById(R.id.deletion_progress_container)
        deletionStatusText = findViewById(R.id.deletion_status_text)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        btnCancel.setOnClickListener {
            finish()
        }

        btnDelete.setOnClickListener {
            if (validateInput()) {
                showConfirmationDialog()
            }
        }
    }

    private fun validateInput(): Boolean {
        var isValid = true

        val password = etPassword.text.toString()

        if (password.isEmpty()) {
            etPassword.error = "Password is required"
            isValid = false
        }

        if (!cbConfirm.isChecked) {
            Toast.makeText(this, "Please confirm that you understand the consequences", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setTitle("Delete Account Forever?")
            .setMessage("This action CANNOT be undone. All your posts, comments, likes, and personal data will be permanently deleted.")
            .setPositiveButton("Yes, Delete Everything") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAccount() {
        val currentUser = sessionManager.getCurrentUser() ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val password = etPassword.text.toString()

        // Hide input fields, show progress
        etPassword.visibility = View.GONE
        cbConfirm.visibility = View.GONE
        btnDelete.visibility = View.GONE
        btnCancel.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        deletionProgressContainer.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Step 1: Sign out from Google (if signed in with Google)
                try {
                    Log.d("DeleteAccount", "🔐 Signing out from Google...")
                    updateProgress("Signing out from Google...")
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(this@DeleteAccountActivity, gso)
                    googleSignInClient.signOut().addOnCompleteListener {
                        Log.d("DeleteAccount", "✅ Google sign out completed")
                    }
                } catch (e: Exception) {
                    Log.w("DeleteAccount", "⚠️ Google sign out failed (may not have been signed in with Google): ${e.message}")
                }

                // Step 2: Call backend to delete account and verify password
                Log.d("DeleteAccount", "📡 Calling backend to delete account...")
                updateProgress("Verifying password and deleting all data...")
                val result = userRepository.deleteAccountWithPassword(
                    firebaseUid = currentUser.firebaseUid,
                    password = password
                )

                if (result.isSuccess) {
                    val response = result.getOrNull()
                    
                    // Step 3: Handle successful deletion
                    if (response?.deleted == true || response?.success == true) {
                        Log.d("DeleteAccount", "✅ Backend deletion successful")
                        updateProgress("Clearing local data...")

                        // Step 4: Clear local session
                        Log.d("DeleteAccount", "🧹 Clearing local session...")
                        sessionManager.clearSession()
                        SocketManager.disconnect()

                        // Step 5: Sign out from Firebase
                        Log.d("DeleteAccount", "🔥 Signing out from Firebase...")
                        updateProgress("Finalizing deletion...")
                        FirebaseAuthHelper.signOut()

                        // Step 6: Show success message
                        Toast.makeText(
                            this@DeleteAccountActivity,
                            "Account permanently deleted. We'll miss you! 👋",
                            Toast.LENGTH_LONG
                        ).show()

                        // Step 7: Redirect to login screen
                        Log.d("DeleteAccount", "➡️ Redirecting to login...")
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val intent = Intent(this@DeleteAccountActivity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }, 1500)
                    } else {
                        // Backend returned success but account wasn't deleted
                        Log.w("DeleteAccount", "⚠️ Backend returned success but account wasn't deleted")
                        Toast.makeText(
                            this@DeleteAccountActivity,
                            response?.message ?: "Failed to delete account",
                            Toast.LENGTH_SHORT
                        ).show()
                        showInputFields()
                    }
                } else {
                    // Handle error response
                    val errorMsg = result.exceptionOrNull()?.message 
                        ?: "Unknown error occurred"
                    
                    Log.e("DeleteAccount", "❌ Error deleting account: $errorMsg")
                    
                    // Check if it's a password verification error
                    val userFriendlyError = when {
                        errorMsg.contains("Invalid password", ignoreCase = true) -> "Incorrect password. Please try again."
                        errorMsg.contains("network", ignoreCase = true) -> "Network error. Check your connection and try again."
                        else -> errorMsg
                    }
                    
                    Toast.makeText(
                        this@DeleteAccountActivity,
                        userFriendlyError,
                        Toast.LENGTH_LONG
                    ).show()
                    
                    showInputFields()
                }
            } catch (e: Exception) {
                Log.e("DeleteAccount", "❌ Exception during account deletion", e)
                Toast.makeText(
                    this@DeleteAccountActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                showInputFields()
            }
        }
    }

    private fun updateProgress(message: String) {
        deletionStatusText.text = message
        Log.d("DeleteAccount", "📊 Progress: $message")
    }

    private fun showInputFields() {
        etPassword.visibility = View.VISIBLE
        cbConfirm.visibility = View.VISIBLE
        btnDelete.visibility = View.VISIBLE
        btnCancel.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        deletionProgressContainer.visibility = View.GONE
        btnDelete.isEnabled = true
        btnCancel.isEnabled = true
    }
}
