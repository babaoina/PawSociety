package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.PrivacyRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class PrivacyActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var btnBack: ImageView
    private lateinit var switchPrivateAccount: SwitchCompat
    private lateinit var btnPrivacyPolicy: LinearLayout
    private lateinit var btnTermsOfService: LinearLayout
    private lateinit var btnDataDownload: LinearLayout
    private lateinit var btnSave: Button

    private val privacyRepository = PrivacyRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        sessionManager = SessionManager(this)

        initViews()
        setupClickListeners()
        loadPrivacySettings()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        switchPrivateAccount = findViewById(R.id.switch_private_account)
        btnPrivacyPolicy = findViewById(R.id.btn_privacy_policy)
        btnTermsOfService = findViewById(R.id.btn_terms_of_service)
        btnDataDownload = findViewById(R.id.btn_data_download)
        btnSave = findViewById(R.id.btn_save)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnPrivacyPolicy.setOnClickListener {
            showPrivacyPolicy()
        }

        btnTermsOfService.setOnClickListener {
            showTermsOfService()
        }

        btnDataDownload.setOnClickListener {
            requestDataDownload()
        }

        btnSave.setOnClickListener {
            savePrivacySettings()
        }
    }

    private fun loadPrivacySettings() {
        val currentUser = sessionManager.getCurrentUser()
        if (currentUser == null) {
            loadLocalPrivacyFallback()
            return
        }

        lifecycleScope.launch {
            val result = privacyRepository.getPrivateAccountSetting(currentUser.firebaseUid)
            if (result.isSuccess) {
                val isPrivate = result.getOrNull() ?: false
                switchPrivateAccount.isChecked = isPrivate
                savePrivacySettingLocally(isPrivate)
            } else {
                loadLocalPrivacyFallback()
            }
        }
    }

    private fun loadLocalPrivacyFallback() {
        val prefs = getSharedPreferences("privacy_settings", MODE_PRIVATE)
        switchPrivateAccount.isChecked = prefs.getBoolean("private_account", false)
    }

    private fun savePrivacySettingLocally(isPrivate: Boolean) {
        val prefs = getSharedPreferences("privacy_settings", MODE_PRIVATE)
        prefs.edit().putBoolean("private_account", isPrivate).apply()
    }

    private fun savePrivacySettings() {
        val currentUser = sessionManager.getCurrentUser()
        val isPrivate = switchPrivateAccount.isChecked

        savePrivacySettingLocally(isPrivate)

        if (currentUser == null) {
            Toast.makeText(this, "Privacy saved on this device", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnSave.isEnabled = false

        lifecycleScope.launch {
            val result = privacyRepository.updatePrivateAccountSetting(currentUser.firebaseUid, isPrivate)
            btnSave.isEnabled = true

            if (result.isSuccess) {
                Toast.makeText(
                    this@PrivacyActivity,
                    if (isPrivate) "Account is now private" else "Account is now public",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            } else {
                Toast.makeText(
                    this@PrivacyActivity,
                    "Saved locally, but failed to sync to server",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showPrivacyPolicy() {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("title", "Privacy Policy")
        intent.putExtra("url", "https://pawsociety.com/privacy")
        startActivity(intent)
    }

    private fun showTermsOfService() {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("title", "Terms of Service")
        intent.putExtra("url", "https://pawsociety.com/terms")
        startActivity(intent)
    }

    private fun requestDataDownload() {
        AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setTitle("Download Your Data")
            .setMessage("We'll prepare a file with all your data and send it to your email when ready.")
            .setPositiveButton("Request") { _, _ ->
                Toast.makeText(this, "Data download requested. Check your email.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
