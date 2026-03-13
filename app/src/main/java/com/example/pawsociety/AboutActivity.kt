package com.example.pawsociety

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
class AboutActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnWebsite: LinearLayout
    private lateinit var btnPrivacy: LinearLayout
    private lateinit var btnTerms: LinearLayout
    private lateinit var btnLicenses: LinearLayout
    private lateinit var btnRate: LinearLayout
    private lateinit var btnShare: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        btnWebsite = findViewById(R.id.btn_website)
        btnPrivacy = findViewById(R.id.btn_privacy)
        btnTerms = findViewById(R.id.btn_terms)
        btnLicenses = findViewById(R.id.btn_licenses)
        btnRate = findViewById(R.id.btn_rate)
        btnShare = findViewById(R.id.btn_share)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnWebsite.setOnClickListener {
            openUrl("https://pawsociety.com")
        }

        btnPrivacy.setOnClickListener {
            openUrl("https://pawsociety.com/privacy")
        }

        btnTerms.setOnClickListener {
            openUrl("https://pawsociety.com/terms")
        }

        btnLicenses.setOnClickListener {
            showOpenSourceLicenses()
        }

        btnRate.setOnClickListener {
            rateApp()
        }

        btnShare.setOnClickListener {
            shareApp()
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun showOpenSourceLicenses() {
        AlertDialog.Builder(this)
            .setTitle("Open Source Licenses")
            .setMessage("PawSociety uses the following open source libraries:\n\n" +
                    "- Retrofit (Apache 2.0)\n" +
                    "- OkHttp (Apache 2.0)\n" +
                    "- Glide (BSD, part MIT and Apache 2.0)\n" +
                    "- Gson (Apache 2.0)\n" +
                    "- AndroidX Libraries (Apache 2.0)\n" +
                    "- Material Components (Apache 2.0)\n" +
                    "- Kotlin Coroutines (Apache 2.0)")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun rateApp() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        startActivity(intent)
    }

    private fun shareApp() {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Check out PawSociety - the best app for pet lovers! https://play.google.com/store/apps/details?id=$packageName")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }
}