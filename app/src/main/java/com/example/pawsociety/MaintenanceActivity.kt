package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MaintenanceActivity : AppCompatActivity() {

    private lateinit var tvMessage: TextView
    private lateinit var btnRetry: Button
    private lateinit var btnContact: Button
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maintenance)

        settingsRepository = SettingsRepository()

        tvMessage = findViewById(R.id.tv_maintenance_message)
        btnRetry = findViewById(R.id.btn_retry)
        btnContact = findViewById(R.id.btn_contact_support)

        // Get message from intent or load from settings
        val message = intent.getStringExtra("message")
        if (message != null) {
            tvMessage.text = message
        } else {
            loadMaintenanceMessage()
        }

        btnRetry.setOnClickListener {
            checkMaintenanceStatus()
        }

        btnContact.setOnClickListener {
            lifecycleScope.launch {
                val settings = settingsRepository.getSettings().getOrNull()
                val supportEmail = settings?.general?.supportEmail ?: "support@pawsociety.com"

                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:$supportEmail")
                    putExtra(Intent.EXTRA_SUBJECT, "Maintenance Issue")
                }
                startActivity(intent)
            }
        }
    }

    private fun loadMaintenanceMessage() {
        lifecycleScope.launch {
            try {
                val message = settingsRepository.getMaintenanceMessage()
                tvMessage.text = message
            } catch (e: Exception) {
                tvMessage.text = "PawSociety is under maintenance. We'll be back soon! 🐾"
            }
        }
    }

    private fun checkMaintenanceStatus() {
        btnRetry.isEnabled = false
        btnRetry.text = "Checking..."

        lifecycleScope.launch {
            try {
                // Add small delay to show checking state
                delay(1000)

                val isMaintenance = settingsRepository.isMaintenanceMode()

                if (!isMaintenance) {
                    // Maintenance mode is off, go to login
                    startActivity(Intent(this@MaintenanceActivity, LoginActivity::class.java))
                    finish()
                } else {
                    // Still in maintenance, update message and re-enable button
                    loadMaintenanceMessage()
                    btnRetry.isEnabled = true
                    btnRetry.text = "Retry"
                }
            } catch (e: Exception) {
                btnRetry.isEnabled = true
                btnRetry.text = "Retry"
                tvMessage.text = "Cannot connect to server. Please check your internet connection."
            }
        }
    }
}