package com.example.pawsociety

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.pawsociety.util.SessionManager
import android.content.Intent
import androidx.appcompat.app.AlertDialog
class PrivacyActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var btnBack: ImageView
    private lateinit var switchPrivateAccount: SwitchCompat
    private lateinit var switchShowActivity: SwitchCompat
    private lateinit var switchTagApproval: SwitchCompat
    private lateinit var spinnerDataSharing: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnPrivacyPolicy: LinearLayout
    private lateinit var btnTermsOfService: LinearLayout
    private lateinit var btnDataDownload: LinearLayout

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
        switchShowActivity = findViewById(R.id.switch_show_activity)
        switchTagApproval = findViewById(R.id.switch_tag_approval)
        spinnerDataSharing = findViewById(R.id.spinner_data_sharing)
        btnSave = findViewById(R.id.btn_save)
        btnPrivacyPolicy = findViewById(R.id.btn_privacy_policy)
        btnTermsOfService = findViewById(R.id.btn_terms_of_service)
        btnDataDownload = findViewById(R.id.btn_data_download)

        // Setup spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.data_sharing_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerDataSharing.adapter = adapter
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            savePrivacySettings()
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
    }

    private fun loadPrivacySettings() {
        val prefs = getSharedPreferences("privacy_settings", MODE_PRIVATE)
        switchPrivateAccount.isChecked = prefs.getBoolean("private_account", false)
        switchShowActivity.isChecked = prefs.getBoolean("show_activity", true)
        switchTagApproval.isChecked = prefs.getBoolean("tag_approval", true)
        spinnerDataSharing.setSelection(prefs.getInt("data_sharing", 0))
    }

    private fun savePrivacySettings() {
        val prefs = getSharedPreferences("privacy_settings", MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("private_account", switchPrivateAccount.isChecked)
            putBoolean("show_activity", switchShowActivity.isChecked)
            putBoolean("tag_approval", switchTagApproval.isChecked)
            putInt("data_sharing", spinnerDataSharing.selectedItemPosition)
            apply()
        }

        Toast.makeText(this, "Privacy settings saved", Toast.LENGTH_SHORT).show()
        finish()
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
            .setTitle("Title")
            .setTitle("Download Your Data")
            .setMessage("We'll prepare a file with all your data and send it to your email when ready.")
            .setPositiveButton("Request") { _, _ ->
                Toast.makeText(this, "Data download requested. Check your email.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}