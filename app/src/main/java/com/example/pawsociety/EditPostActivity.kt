package com.example.pawsociety

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.location.Geocoder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.util.KeyboardAwareScrollHelper
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditPostActivity : AppCompatActivity() {

    private lateinit var etPetName: EditText
    private lateinit var etDescription: EditText
    private lateinit var etContact: EditText
    private lateinit var etEventDate: EditText
    private lateinit var etIdentifyingMarks: EditText
    private lateinit var etTemperament: EditText
    private lateinit var etHealthCondition: EditText
    private lateinit var tvLocation: TextView
    private lateinit var tvLocationLabel: TextView
    private lateinit var tvEventDateLabel: TextView
    private lateinit var btnSelectLocation: Button
    private lateinit var btnCaseOwnerLost: TextView
    private lateinit var btnCaseSeenLostPet: TextView
    private lateinit var btnCaseFoundInCare: TextView
    private lateinit var btnCaseAdoption: TextView
    private lateinit var btnContactCall: TextView
    private lateinit var btnContactText: TextView
    private lateinit var btnContactChat: TextView
    private lateinit var btnCareInMyCare: TextView
    private lateinit var btnCareSightingOnly: TextView
    private lateinit var btnCollarYes: TextView
    private lateinit var btnCollarNo: TextView
    private lateinit var layoutCurrentCare: LinearLayout
    private lateinit var layoutHealthCondition: LinearLayout
    private lateinit var btnSave: TextView
    private lateinit var btnCancel: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var sessionManager: SessionManager
    private val postRepository = PostRepository()
    private var post: ApiPost? = null
    private var selectedCaseType = "owner_lost"
    private var selectedContactPreference = "call"
    private var selectedCurrentCareStatus = "owner"
    private var hasCollar = false
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_post)

        sessionManager = SessionManager(this)

        // Get post from intent
        post = intent.getSerializableExtra("post") as? ApiPost
        if (post == null) {
            Toast.makeText(this, "Post not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupKeyboardScrollHandlers()
        setupClickListeners()
        loadPostData()
    }

    private fun initializeViews() {
        etPetName = findViewById(R.id.et_pet_name)
        etDescription = findViewById(R.id.et_description)
        etContact = findViewById(R.id.et_contact)
        etEventDate = findViewById(R.id.et_event_date)
        etIdentifyingMarks = findViewById(R.id.et_identifying_marks)
        etTemperament = findViewById(R.id.et_temperament)
        etHealthCondition = findViewById(R.id.et_health_condition)
        tvLocation = findViewById(R.id.tv_location)
        tvLocationLabel = findViewById(R.id.tv_location_label)
        tvEventDateLabel = findViewById(R.id.tv_event_date_label)
        btnSelectLocation = findViewById(R.id.btn_select_location)
        btnCaseOwnerLost = findViewById(R.id.btn_case_owner_lost)
        btnCaseSeenLostPet = findViewById(R.id.btn_case_seen_lost_pet)
        btnCaseFoundInCare = findViewById(R.id.btn_case_found_in_care)
        btnCaseAdoption = findViewById(R.id.btn_case_adoption)
        btnContactCall = findViewById(R.id.btn_contact_call)
        btnContactText = findViewById(R.id.btn_contact_text)
        btnContactChat = findViewById(R.id.btn_contact_chat)
        btnCareInMyCare = findViewById(R.id.btn_care_in_my_care)
        btnCareSightingOnly = findViewById(R.id.btn_care_sighting_only)
        btnCollarYes = findViewById(R.id.btn_collar_yes)
        btnCollarNo = findViewById(R.id.btn_collar_no)
        layoutCurrentCare = findViewById(R.id.layout_current_care)
        layoutHealthCondition = findViewById(R.id.layout_health_condition)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
        progressBar = findViewById(R.id.progress_bar)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            saveChanges()
        }

        btnCancel.setOnClickListener {
            finish()
        }

        etEventDate.setOnClickListener {
            showEventDatePicker()
        }

        btnCaseOwnerLost.setOnClickListener { updateCaseTypeSelection(btnCaseOwnerLost, "owner_lost") }
        btnCaseSeenLostPet.setOnClickListener { updateCaseTypeSelection(btnCaseSeenLostPet, "seen_lost_pet") }
        btnCaseFoundInCare.setOnClickListener { updateCaseTypeSelection(btnCaseFoundInCare, "found_in_care") }
        btnCaseAdoption.setOnClickListener { updateCaseTypeSelection(btnCaseAdoption, "adoption") }
        btnContactCall.setOnClickListener { updateContactPreferenceButtons(btnContactCall) }
        btnContactText.setOnClickListener { updateContactPreferenceButtons(btnContactText) }
        btnContactChat.setOnClickListener { updateContactPreferenceButtons(btnContactChat) }
        btnCareInMyCare.setOnClickListener { updateCurrentCareButtons(btnCareInMyCare) }
        btnCareSightingOnly.setOnClickListener { updateCurrentCareButtons(btnCareSightingOnly) }
        btnCollarYes.setOnClickListener { updateCollarButtons(true) }
        btnCollarNo.setOnClickListener { updateCollarButtons(false) }

        // Location picker
        btnSelectLocation.setOnClickListener {
            try {
                val dialog = LocationPickerDialog(this) { fullLocation ->
                    tvLocation.text = fullLocation
                    tvLocation.visibility = View.VISIBLE
                    geocodeSelectedLocation(fullLocation)
                }
                dialog.show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error opening location picker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupKeyboardScrollHandlers() {
        KeyboardAwareScrollHelper.attach(
            etPetName,
            etDescription,
            etContact,
            etEventDate,
            etIdentifyingMarks,
            etTemperament,
            etHealthCondition
        )
    }

    private fun loadPostData() {
        post?.let { post ->
            etPetName.setText(post.petName)
            etDescription.setText(post.description)
            etContact.setText(post.contactInfo)
            etEventDate.setText(post.eventDate ?: "")
            etIdentifyingMarks.setText(post.identifyingMarks ?: "")
            etTemperament.setText(post.temperament ?: "")
            etHealthCondition.setText(post.healthCondition ?: "")
            updateCaseTypeSelection(
                when (post.caseType) {
                    "seen_lost_pet" -> btnCaseSeenLostPet
                    "found_in_care" -> btnCaseFoundInCare
                    "adoption" -> btnCaseAdoption
                    else -> btnCaseOwnerLost
                },
                post.caseType ?: "owner_lost"
            )
            updateContactPreferenceButtons(
                when (post.contactPreference) {
                    "text" -> btnContactText
                    "in_app_chat" -> btnContactChat
                    else -> btnContactCall
                }
            )
            updateCurrentCareButtons(
                when (post.currentCareStatus) {
                    "sighting_only" -> btnCareSightingOnly
                    else -> btnCareInMyCare
                }
            )
            updateCollarButtons(post.hasCollar)
            selectedLatitude = post.latitude
            selectedLongitude = post.longitude

            if (!post.location.isNullOrEmpty()) {
                tvLocation.text = post.location
                tvLocation.visibility = View.VISIBLE
            } else {
                tvLocation.text = "No location selected"
                tvLocation.visibility = View.GONE
            }
        }
    }

    private fun saveChanges() {
        val currentUser = sessionManager.getCurrentUser() ?: run {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val post = post ?: return

        // DEBUG: Print the post ID being sent
        println("========== EDIT POST DEBUG ==========")
        println("Post ID being sent: '${post.postId}'")
        println("Post ID length: ${post.postId.length}")
        println("Current User UID: ${currentUser.firebaseUid}")
        println("======================================")

        val updatedPetName = etPetName.text.toString().trim()
        val updatedDescription = etDescription.text.toString().trim()
        val updatedContact = etContact.text.toString().trim()
        val updatedLocation = if (tvLocation.text.toString().trim() != "No location selected")
            tvLocation.text.toString().trim() else ""

        if (!validateInputs(updatedPetName, updatedDescription, updatedContact, updatedLocation)) {
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                // Create a simple map with string values
                val updateMap = HashMap<String, Any>()
                updateMap["petName"] = updatedPetName
                updateMap["description"] = updatedDescription
                updateMap["contactInfo"] = updatedContact
                updateMap["location"] = updatedLocation
                updateMap["eventLocation"] = updatedLocation
                selectedLatitude?.let { updateMap["latitude"] = it }
                selectedLongitude?.let { updateMap["longitude"] = it }
                updateMap["eventDate"] = etEventDate.text.toString().trim()
                updateMap["caseType"] = selectedCaseType
                updateMap["contactPreference"] = selectedContactPreference
                updateMap["currentCareStatus"] = selectedCurrentCareStatus
                updateMap["identifyingMarks"] = etIdentifyingMarks.text.toString().trim()
                updateMap["temperament"] = etTemperament.text.toString().trim()
                updateMap["healthCondition"] = etHealthCondition.text.toString().trim()
                updateMap["hasCollar"] = hasCollar

                println("📤 Sending update to backend:")
                println("   Post ID: ${post.postId}")
                println("   Update data: $updateMap")

                val result = postRepository.updatePost(post.postId, updateMap, currentUser.firebaseUid)

                if (result.isSuccess) {
                    println("✅ Update successful!")

                    setResult(RESULT_OK)

                    Toast.makeText(this@EditPostActivity, "Post updated successfully!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    println("❌ Update failed: $errorMsg")
                    Toast.makeText(this@EditPostActivity, "Failed: $errorMsg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                println("❌ Exception in update: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@EditPostActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                btnSave.isEnabled = true
            }
        }
    }

    companion object {
        const val EDIT_POST_REQUEST = 1002
    }

    private fun updateCaseTypeSelection(selectedView: TextView, caseType: String) {
        selectedCaseType = caseType
        listOf(btnCaseOwnerLost, btnCaseSeenLostPet, btnCaseFoundInCare, btnCaseAdoption).forEach {
            it.setBackgroundResource(R.drawable.button_oval_brown_outline)
            it.setTextColor(Color.parseColor("#7A4F2B"))
        }
        selectedView.setBackgroundResource(R.drawable.button_oval_brown)
        selectedView.setTextColor(Color.WHITE)

        when (caseType) {
            "owner_lost" -> {
                tvEventDateLabel.text = "Last Seen Date"
                tvLocationLabel.text = "Last Seen Location"
                layoutCurrentCare.visibility = View.GONE
                layoutHealthCondition.visibility = View.GONE
                selectedCurrentCareStatus = "owner"
            }
            "seen_lost_pet" -> {
                tvEventDateLabel.text = "Sighting Date"
                tvLocationLabel.text = "Where You Saw The Pet"
                layoutCurrentCare.visibility = View.GONE
                layoutHealthCondition.visibility = View.GONE
                selectedCurrentCareStatus = "sighting_only"
            }
            "found_in_care" -> {
                tvEventDateLabel.text = "Found Date"
                tvLocationLabel.text = "Found Location"
                layoutCurrentCare.visibility = View.VISIBLE
                layoutHealthCondition.visibility = View.VISIBLE
            }
            else -> {
                tvEventDateLabel.text = "Posting Date"
                tvLocationLabel.text = "City / Pickup Area"
                layoutCurrentCare.visibility = View.GONE
                layoutHealthCondition.visibility = View.VISIBLE
                selectedCurrentCareStatus = "owner"
            }
        }
    }

    private fun updateContactPreferenceButtons(selectedView: TextView) {
        listOf(btnContactCall, btnContactText, btnContactChat).forEach {
            it.setBackgroundResource(R.drawable.button_oval_brown_outline)
            it.setTextColor(Color.parseColor("#7A4F2B"))
        }
        selectedView.setBackgroundResource(R.drawable.button_oval_brown)
        selectedView.setTextColor(Color.WHITE)
        selectedContactPreference = when (selectedView) {
            btnContactText -> "text"
            btnContactChat -> "in_app_chat"
            else -> "call"
        }
    }

    private fun updateCurrentCareButtons(selectedView: TextView) {
        listOf(btnCareInMyCare, btnCareSightingOnly).forEach {
            it.setBackgroundResource(R.drawable.button_oval_brown_outline)
            it.setTextColor(Color.parseColor("#7A4F2B"))
        }
        selectedView.setBackgroundResource(R.drawable.button_oval_brown)
        selectedView.setTextColor(Color.WHITE)
        selectedCurrentCareStatus = if (selectedView == btnCareSightingOnly) "sighting_only" else "in_my_care"
    }

    private fun updateCollarButtons(selected: Boolean) {
        hasCollar = selected
        if (selected) {
            btnCollarYes.setBackgroundResource(R.drawable.button_oval_brown)
            btnCollarYes.setTextColor(Color.WHITE)
            btnCollarNo.setBackgroundResource(R.drawable.button_oval_brown_outline)
            btnCollarNo.setTextColor(Color.parseColor("#7A4F2B"))
        } else {
            btnCollarNo.setBackgroundResource(R.drawable.button_oval_brown)
            btnCollarNo.setTextColor(Color.WHITE)
            btnCollarYes.setBackgroundResource(R.drawable.button_oval_brown_outline)
            btnCollarYes.setTextColor(Color.parseColor("#7A4F2B"))
        }
    }

    private fun showEventDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            R.style.Theme_PawSociety_DatePickerDialog,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                etEventDate.setText(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun validateInputs(
        petName: String,
        description: String,
        contact: String,
        location: String
    ): Boolean {
        etPetName.error = null
        etDescription.error = null
        etContact.error = null
        etEventDate.error = null
        etIdentifyingMarks.error = null
        etHealthCondition.error = null

        if (petName.isEmpty()) {
            etPetName.error = "Pet name is required"
            return false
        }

        if (petName.length < 2) {
            etPetName.error = "Pet name must be at least 2 characters"
            return false
        }

        if (!petName.matches(Regex("^[A-Za-z0-9 .'-]+$"))) {
            etPetName.error = "Use letters and numbers only"
            return false
        }

        if (description.isEmpty()) {
            etDescription.error = "Description is required"
            return false
        }

        if (description.length < 10) {
            etDescription.error = "Description must be at least 10 characters"
            return false
        }

        if (contact.isEmpty()) {
            etContact.error = "Contact number is required"
            return false
        }

        if (!contact.matches(Regex("^09\\d{9}$"))) {
            etContact.error = "Must be 11 digits starting with 09"
            return false
        }

        val eventDate = etEventDate.text.toString().trim()
        if (eventDate.isEmpty()) {
            etEventDate.error = "Date is required"
            return false
        }

        if (isFutureDate(eventDate)) {
            etEventDate.error = "Date cannot be in the future"
            return false
        }

        if (location.isEmpty()) {
            Toast.makeText(this, "Location is required", Toast.LENGTH_SHORT).show()
            return false
        }

        val hasPangasinanText = location.contains("Pangasinan", ignoreCase = true)
        val hasPangasinanCoordinates = selectedLatitude != null && selectedLongitude != null &&
            isWithinPangasinan(selectedLatitude!!, selectedLongitude!!)

        if (!hasPangasinanText && !hasPangasinanCoordinates) {
            Toast.makeText(this, "Location must be within Pangasinan", Toast.LENGTH_SHORT).show()
            return false
        }

        val identifyingMarks = etIdentifyingMarks.text.toString().trim()
        if (selectedCaseType != "adoption" && identifyingMarks.length < 5) {
            etIdentifyingMarks.error = "Add at least 5 characters for identifying marks"
            return false
        }

        val healthCondition = etHealthCondition.text.toString().trim()
        if ((selectedCaseType == "found_in_care" || selectedCaseType == "adoption") && healthCondition.length < 5) {
            etHealthCondition.error = "Add at least 5 characters for health / condition"
            return false
        }

        return true
    }

    private fun isFutureDate(value: String): Boolean {
        return try {
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply {
                isLenient = false
            }
            val selectedDate = formatter.parse(value) ?: return false
            selectedDate.after(Calendar.getInstance().time)
        } catch (_: Exception) {
            false
        }
    }

    private fun geocodeSelectedLocation(locationName: String) {
        lifecycleScope.launch {
            val latLng = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(this@EditPostActivity, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(locationName, 1)
                    val address = addresses?.firstOrNull()
                    val lat = address?.latitude
                    val lon = address?.longitude
                    if (lat != null && lon != null && isWithinPangasinan(lat, lon)) {
                        lat to lon
                    } else {
                        null to null
                    }
                } catch (_: Exception) {
                    null to null
                }
            }

            selectedLatitude = latLng.first
            selectedLongitude = latLng.second
            if (selectedLatitude == null || selectedLongitude == null) {
                Toast.makeText(this@EditPostActivity, "Only Pangasinan locations are allowed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isWithinPangasinan(lat: Double, lon: Double): Boolean {
        return lat in 15.78..16.37 && lon in 119.80..120.89
    }
}
