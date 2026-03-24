package com.example.pawsociety

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.SettingsRepository
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class NotificationsSettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var btnBack: ImageView
    private lateinit var switchLikes: SwitchCompat
    private lateinit var switchComments: SwitchCompat
    private lateinit var switchFollows: SwitchCompat
    private lateinit var switchMessages: SwitchCompat
    private lateinit var switchPostReminders: SwitchCompat
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    private val settingsRepository = SettingsRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications_settings)

        sessionManager = SessionManager(this)

        initViews()
        setupClickListeners()
        loadNotificationSettings()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        switchLikes = findViewById(R.id.switch_likes)
        switchComments = findViewById(R.id.switch_comments)
        switchFollows = findViewById(R.id.switch_follows)
        switchMessages = findViewById(R.id.switch_messages)
        switchPostReminders = findViewById(R.id.switch_post_reminders)
        btnSave = findViewById(R.id.btn_save)
        progressBar = findViewById(R.id.progress_bar)

        // Hide progress bar initially
        progressBar.visibility = View.GONE
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveNotificationSettings()
        }

        // 🔥 REAL-TIME SYNC: Listen to each switch toggle and save immediately
        switchLikes.setOnCheckedChangeListener { _, isChecked ->
            syncSettingToBackend("likes", isChecked)
        }

        switchComments.setOnCheckedChangeListener { _, isChecked ->
            syncSettingToBackend("comments", isChecked)
        }

        switchFollows.setOnCheckedChangeListener { _, isChecked ->
            syncSettingToBackend("follows", isChecked)
        }

        switchMessages.setOnCheckedChangeListener { _, isChecked ->
            syncSettingToBackend("messages", isChecked)
        }

        switchPostReminders.setOnCheckedChangeListener { _, isChecked ->
            syncSettingToBackend("post_reminders", isChecked)
        }
    }

    private fun loadNotificationSettings() {
        val currentUser = sessionManager.getCurrentUser() ?: return

        lifecycleScope.launch {
            try {
                // Load settings from backend
                val result = settingsRepository.getNotificationSettings(currentUser.firebaseUid)

                if (result.isSuccess) {
                    val settings = result.getOrNull() ?: emptyMap()
                    
                    // Update UI without triggering listeners
                    switchLikes.setOnCheckedChangeListener(null)
                    switchComments.setOnCheckedChangeListener(null)
                    switchFollows.setOnCheckedChangeListener(null)
                    switchMessages.setOnCheckedChangeListener(null)
                    switchPostReminders.setOnCheckedChangeListener(null)

                    switchLikes.isChecked = settings["likes"] as? Boolean ?: true
                    switchComments.isChecked = settings["comments"] as? Boolean ?: true
                    switchFollows.isChecked = settings["follows"] as? Boolean ?: true
                    switchMessages.isChecked = settings["messages"] as? Boolean ?: true
                    switchPostReminders.isChecked = settings["post_reminders"] as? Boolean ?: false

                    // Re-attach listeners
                    switchLikes.setOnCheckedChangeListener { _, isChecked ->
                        syncSettingToBackend("likes", isChecked)
                    }
                    switchComments.setOnCheckedChangeListener { _, isChecked ->
                        syncSettingToBackend("comments", isChecked)
                    }
                    switchFollows.setOnCheckedChangeListener { _, isChecked ->
                        syncSettingToBackend("follows", isChecked)
                    }
                    switchMessages.setOnCheckedChangeListener { _, isChecked ->
                        syncSettingToBackend("messages", isChecked)
                    }
                    switchPostReminders.setOnCheckedChangeListener { _, isChecked ->
                        syncSettingToBackend("post_reminders", isChecked)
                    }

                    Log.d("NotificationSettings", "✅ Loaded settings from backend")
                } else {
                    // Fallback to SharedPreferences if API fails
                    loadFromSharedPreferences()
                }
            } catch (e: Exception) {
                Log.e("NotificationSettings", "Error loading settings: ${e.message}")
                loadFromSharedPreferences()
            }
        }
    }

    private fun loadFromSharedPreferences() {
        val prefs = getSharedPreferences("notification_settings", MODE_PRIVATE)
        switchLikes.isChecked = prefs.getBoolean("likes", true)
        switchComments.isChecked = prefs.getBoolean("comments", true)
        switchFollows.isChecked = prefs.getBoolean("follows", true)
        switchMessages.isChecked = prefs.getBoolean("messages", true)
        switchPostReminders.isChecked = prefs.getBoolean("post_reminders", false)
    }

    private fun syncSettingToBackend(settingKey: String, value: Boolean) {
        val currentUser = sessionManager.getCurrentUser() ?: return

        Log.d("NotificationSettings", "🔄 Syncing $settingKey = $value to backend...")

        lifecycleScope.launch {
            try {
                // Update to backend via API
                val result = settingsRepository.updateNotificationSetting(
                    firebaseUid = currentUser.firebaseUid,
                    settingKey = settingKey,
                    value = value
                )

                if (result.isSuccess) {
                    Log.d("NotificationSettings", "✅ $settingKey synced successfully")

                    // Also emit Socket.io event for real-time updates
                    emitSettingChangeEvent(settingKey, value)

                    // Save to SharedPreferences as backup
                    saveToSharedPreferences(settingKey, value)
                } else {
                    Log.e("NotificationSettings", "❌ Failed to sync $settingKey")
                    Toast.makeText(this@NotificationsSettingsActivity, "Failed to sync setting", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("NotificationSettings", "Error syncing: ${e.message}")
                Toast.makeText(this@NotificationsSettingsActivity, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun emitSettingChangeEvent(settingKey: String, value: Boolean) {
        try {
            val currentUser = sessionManager.getCurrentUser() ?: return
            
            val eventData = JSONObject().apply {
                put("firebaseUid", currentUser.firebaseUid)
                put("settingKey", settingKey)
                put("value", value)
                put("timestamp", System.currentTimeMillis())
            }

            SocketManager.emit("notification-settings-updated", eventData)
            Log.d("NotificationSettings", "📤 Emitted Socket.io event: notification-settings-updated")
        } catch (e: Exception) {
            Log.e("NotificationSettings", "Error emitting event: ${e.message}")
        }
    }

    private fun saveToSharedPreferences(settingKey: String, value: Boolean) {
        val prefs = getSharedPreferences("notification_settings", MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(settingKey, value)
            apply()
        }
    }

    private fun saveNotificationSettings() {
        val currentUser = sessionManager.getCurrentUser() ?: return

        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                val settingsMap = mapOf(
                    "likes" to switchLikes.isChecked,
                    "comments" to switchComments.isChecked,
                    "follows" to switchFollows.isChecked,
                    "messages" to switchMessages.isChecked,
                    "post_reminders" to switchPostReminders.isChecked
                )

                // Save all settings together
                val result = settingsRepository.saveAllNotificationSettings(
                    firebaseUid = currentUser.firebaseUid,
                    settings = settingsMap
                )

                if (result.isSuccess) {
                    Toast.makeText(this@NotificationsSettingsActivity, "✅ Settings saved", Toast.LENGTH_SHORT).show()
                    
                    // Emit final event to backend
                    val eventData = JSONObject().apply {
                        put("firebaseUid", currentUser.firebaseUid)
                        put("settings", JSONObject(settingsMap))
                        put("timestamp", System.currentTimeMillis())
                    }
                    SocketManager.emit("notification-settings-saved", eventData)
                    
                    finish()
                } else {
                    Toast.makeText(this@NotificationsSettingsActivity, "❌ Failed to save settings", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NotificationsSettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
            }
        }
    }
}