package com.example.pawsociety

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.pawsociety.util.SessionManager

class NotificationsSettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var btnBack: ImageView
    private lateinit var switchLikes: SwitchCompat
    private lateinit var switchComments: SwitchCompat
    private lateinit var switchFollows: SwitchCompat
    private lateinit var switchMessages: SwitchCompat
    private lateinit var switchPostReminders: SwitchCompat
    private lateinit var btnSave: Button

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
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveNotificationSettings()
        }
    }

    private fun loadNotificationSettings() {
        // Load from SharedPreferences or API
        val prefs = getSharedPreferences("notification_settings", MODE_PRIVATE)
        switchLikes.isChecked = prefs.getBoolean("likes", true)
        switchComments.isChecked = prefs.getBoolean("comments", true)
        switchFollows.isChecked = prefs.getBoolean("follows", true)
        switchMessages.isChecked = prefs.getBoolean("messages", true)
        switchPostReminders.isChecked = prefs.getBoolean("post_reminders", false)
    }

    private fun saveNotificationSettings() {
        val prefs = getSharedPreferences("notification_settings", MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("likes", switchLikes.isChecked)
            putBoolean("comments", switchComments.isChecked)
            putBoolean("follows", switchFollows.isChecked)
            putBoolean("messages", switchMessages.isChecked)
            putBoolean("post_reminders", switchPostReminders.isChecked)
            apply()
        }

        Toast.makeText(this, "Notification settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}