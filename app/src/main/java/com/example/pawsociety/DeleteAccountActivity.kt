package com.example.pawsociety

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import kotlinx.coroutines.launch

class DeleteAccountActivity : AppCompatActivity() {

    private lateinit var etPassword: EditText
    private lateinit var btnDelete: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var cbConfirm: CheckBox

    private lateinit var sessionManager: SessionManager
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_account)

        sessionManager = SessionManager(this)

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        etPassword = findViewById(R.id.et_password)
        btnDelete = findViewById(R.id.btn_delete)
        btnCancel = findViewById(R.id.btn_cancel)
        progressBar = findViewById(R.id.progress_bar)
        cbConfirm = findViewById(R.id.cb_confirm)

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
        AlertDialog.Builder(this)
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

        progressBar.visibility = View.VISIBLE
        btnDelete.isEnabled = false

        lifecycleScope.launch {
            try {
                // Step 1: Re-authenticate with Firebase
                val authResult = FirebaseAuthHelper.loginWithEmail(currentUser.email, password)

                if (authResult.isFailure) {
                    Toast.makeText(this@DeleteAccountActivity, "Incorrect password", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnDelete.isEnabled = true
                    return@launch
                }

                // Step 2: Delete from MongoDB
                val result = userRepository.deleteUser(currentUser.firebaseUid)

                if (result.isSuccess) {
                    // Step 3: Delete from Firebase Auth
                    val firebaseUser = FirebaseAuthHelper.currentUser
                    firebaseUser?.delete()?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Step 4: Clear local session
                            sessionManager.clearSession()
                            SocketManager.disconnect()

                            Toast.makeText(this@DeleteAccountActivity, "Account deleted successfully", Toast.LENGTH_LONG).show()

                            // Go to login screen
                            val intent = Intent(this@DeleteAccountActivity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@DeleteAccountActivity, "Failed to delete Firebase account", Toast.LENGTH_SHORT).show()
                            progressBar.visibility = View.GONE
                            btnDelete.isEnabled = true
                        }
                    }
                } else {
                    Toast.makeText(this@DeleteAccountActivity, "Failed to delete account: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnDelete.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@DeleteAccountActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                btnDelete.isEnabled = true
            }
        }
    }
}