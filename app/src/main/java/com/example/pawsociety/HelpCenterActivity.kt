package com.example.pawsociety

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView

class HelpCenterActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var searchView: SearchView
    private lateinit var btnFAQ: LinearLayout
    private lateinit var btnContact: LinearLayout
    private lateinit var btnReport: LinearLayout
    private lateinit var btnTutorials: LinearLayout
    private lateinit var btnCommunity: LinearLayout
    private lateinit var faqContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_center)

        initViews()
        setupClickListeners()
        loadFAQs()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        searchView = findViewById(R.id.search_view)
        btnFAQ = findViewById(R.id.btn_faq)
        btnContact = findViewById(R.id.btn_contact)
        btnReport = findViewById(R.id.btn_report)
        btnTutorials = findViewById(R.id.btn_tutorials)
        btnCommunity = findViewById(R.id.btn_community)
        faqContainer = findViewById(R.id.faq_container)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnFAQ.setOnClickListener {
            toggleFAQ()
        }

        btnContact.setOnClickListener {
            contactSupport()
        }

        btnReport.setOnClickListener {
            reportProblem()
        }

        btnTutorials.setOnClickListener {
            openTutorials()
        }

        btnCommunity.setOnClickListener {
            openCommunity()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Use the let block or a null check to ensure query is not null
                query?.let {
                    searchFAQs(it)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    loadFAQs()
                } else {
                    searchFAQs(newText)
                }
                return true
            }
        })
    }

    private fun loadFAQs() {
        val faqs = listOf(
            "How do I create a post?" to "Tap the + button at the bottom of the home screen and fill in the details about your pet.",
            "How do I edit my profile?" to "Go to your profile and tap the 'Edit Profile' button.",
            "How do I report a post?" to "Tap the three dots on any post and select 'Report'.",
            "How do I block a user?" to "Go to their profile, tap the three dots, and select 'Block'.",
            "How do I change my password?" to "Go to Settings > Security > Change Password.",
            "How do I delete my account?" to "Go to Settings > Delete Account at the bottom."
        )

        faqContainer.removeAllViews()

        faqs.forEach { (question, answer) ->
            addFAQItem(question, answer)
        }
    }

    private fun addFAQItem(question: String, answer: String) {
        val itemView = layoutInflater.inflate(R.layout.item_faq, faqContainer, false)

        val tvQuestion = itemView.findViewById<TextView>(R.id.tv_question)
        val tvAnswer = itemView.findViewById<TextView>(R.id.tv_answer)

        tvQuestion.text = question
        tvAnswer.text = answer

        itemView.setOnClickListener {
            tvAnswer.visibility = if (tvAnswer.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        faqContainer.addView(itemView)
    }

    private fun toggleFAQ() {
        for (i in 0 until faqContainer.childCount) {
            val itemView = faqContainer.getChildAt(i)
            val tvAnswer = itemView.findViewById<TextView>(R.id.tv_answer)
            tvAnswer.visibility = View.GONE
        }
    }

    private fun searchFAQs(query: String) {
        // Simple search implementation
        for (i in 0 until faqContainer.childCount) {
            val itemView = faqContainer.getChildAt(i)
            val tvQuestion = itemView.findViewById<TextView>(R.id.tv_question)

            if (tvQuestion.text.toString().contains(query, ignoreCase = true)) {
                itemView.visibility = View.VISIBLE
            } else {
                itemView.visibility = View.GONE
            }
        }
    }

    private fun contactSupport() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:support@pawsociety.com")
            putExtra(Intent.EXTRA_SUBJECT, "Support Request")
        }
        startActivity(intent)
    }

    private fun reportProblem() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:report@pawsociety.com")
            putExtra(Intent.EXTRA_SUBJECT, "Problem Report")
        }
        startActivity(intent)
    }

    private fun openTutorials() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pawsociety.com/tutorials"))
        startActivity(intent)
    }

    private fun openCommunity() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://community.pawsociety.com"))
        startActivity(intent)
    }
}