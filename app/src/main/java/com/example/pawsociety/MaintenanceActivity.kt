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
    private val tag = "MaintenanceActivity"

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
            println("📝 $tag - Message from intent: $message")
        } else {
            loadMaintenanceMessage()
        }

        btnRetry.setOnClickListener {
            println("🔄 $tag - Retry button clicked")
            checkMaintenanceStatus()
        }

        btnContact.setOnClickListener {
            println("📧 $tag - Contact button clicked")
            lifecycleScope.launch {
                // 🔥 FIXED: Use getSupportEmail() instead of getSettings()
                val supportEmail = settingsRepository.getSupportEmail()
                println("📧 $tag - Support email: $supportEmail")

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
                println("📥 $tag - Loading maintenance message...")
                val message = settingsRepository.getMaintenanceMessage()
                tvMessage.text = message
                println("📝 $tag - Loaded message: $message")
            } catch (e: Exception) {
                println("❌ $tag - Error loading message: ${e.message}")
                tvMessage.text = "PawSociety is under maintenance. We'll be back soon! 🐾"
            }
        }
    }

    private fun checkMaintenanceStatus() {
        btnRetry.isEnabled = false
        btnRetry.text = "Checking..."

        lifecycleScope.launch {
            try {
                println("🔍 $tag - Checking maintenance status...")

                // Add small delay to show checking state
                delay(1000)

                val isMaintenance = settingsRepository.isMaintenanceMode()
                println("🚦 $tag - Maintenance mode: $isMaintenance")

                if (!isMaintenance) {
                    println("✅ $tag - Maintenance off, going to Login")
                    // Maintenance mode is off, go to login
                    startActivity(Intent(this@MaintenanceActivity, LoginActivity::class.java))
                    finish()
                } else {
                    println("⚠️ $tag - Still in maintenance")
                    // Still in maintenance, update message and re-enable button
                    loadMaintenanceMessage()
                    btnRetry.isEnabled = true
                    btnRetry.text = "Retry"
                }
            } catch (e: Exception) {
                println("❌ $tag - Error checking maintenance: ${e.message}")
                e.printStackTrace()
                btnRetry.isEnabled = true
                btnRetry.text = "Retry"
                tvMessage.text = "Cannot connect to server. Please check your internet connection."
            }
        }
    }
}