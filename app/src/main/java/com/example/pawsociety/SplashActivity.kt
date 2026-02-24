package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.SessionManager

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val sessionManager = SessionManager(this)

        // Wait 2 seconds then check auth and redirect
        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = sessionManager.getCurrentUser()
            val isFirebaseSignedIn = FirebaseAuthHelper.isSignedIn
            
            if (currentUser != null && isFirebaseSignedIn) {
                // User is already logged in, go to Home
                println("✅ User already logged in, going to Home")
                val intent = Intent(this, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } else {
                // User not logged in, go to Login
                println("⚠️ User not logged in, going to Login")
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            
            finish()
        }, 2000)
    }
}