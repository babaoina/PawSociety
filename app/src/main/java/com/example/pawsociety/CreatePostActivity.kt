package com.example.pawsociety

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.data.repository.UploadRepository
import com.example.pawsociety.util.FileHelper
import com.example.pawsociety.util.KeyboardAwareScrollHelper
import com.example.pawsociety.util.PermissionHelper
import com.example.pawsociety.util.SessionManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

class CreatePostActivity : AppCompatActivity() {

    // Views
    private lateinit var etPetName: EditText
    private lateinit var actPetType: AutoCompleteTextView
    private lateinit var etAge: EditText
    private lateinit var etWeight: EditText
    private lateinit var etReward: EditText
    private lateinit var etEventDate: EditText
    private lateinit var tvLocation: TextView
    private lateinit var tvEventDateLabel: TextView
    private lateinit var tvEventLocationLabel: TextView
    private lateinit var tvCaseTypeHint: TextView
    private lateinit var btnSelectLocation: Button
    private lateinit var etContact: EditText
    private lateinit var etDescription: EditText
    private lateinit var etIdentifyingMarks: EditText
    private lateinit var etTemperament: EditText
    private lateinit var etHealthCondition: EditText
    private lateinit var btnStatusLost: TextView
    private lateinit var btnStatusFound: TextView
    private lateinit var btnStatusAdoption: TextView
    private lateinit var btnCaseOwnerLost: TextView
    private lateinit var btnCaseSeenLostPet: TextView
    private lateinit var btnCaseFoundInCare: TextView
    private lateinit var btnCaseAdoption: TextView
    private lateinit var btnGenderMale: TextView
    private lateinit var btnGenderFemale: TextView
    private lateinit var btnGenderUnknown: TextView
    private lateinit var layoutReward: LinearLayout
    private lateinit var layoutCurrentCare: LinearLayout
    private lateinit var layoutHealthCondition: LinearLayout
    private lateinit var btnPost: TextView
    private lateinit var btnCancel: TextView
    private lateinit var btnAddPhoto: TextView
    private lateinit var photoCountBadge: TextView
    private lateinit var carouselContainer: FrameLayout
    private lateinit var photoEmptyState: LinearLayout
    private lateinit var photoViewPager: ViewPager2
    private lateinit var btnRemoveCurrentPhoto: ImageView
    private lateinit var photoIndicatorContainer: LinearLayout
    private lateinit var imagePreviewContainer: LinearLayout
    private lateinit var thumbnailScroll: HorizontalScrollView
    private lateinit var thumbnailContainer: LinearLayout
    private lateinit var photoUploadProgress: ProgressBar

    // 🔥 NEW: Age and Weight Unit Views
    private lateinit var layoutAgeUnits: LinearLayout
    private lateinit var btnAgeYears: TextView
    private lateinit var btnAgeMonths: TextView
    private lateinit var layoutWeightUnits: LinearLayout
    private lateinit var btnWeightKg: TextView
    private lateinit var btnWeightLbs: TextView
    private lateinit var btnCareInMyCare: TextView
    private lateinit var btnCareSightingOnly: TextView
    private lateinit var btnContactCall: TextView
    private lateinit var btnContactText: TextView
    private lateinit var btnContactChat: TextView
    private lateinit var btnCollarYes: TextView
    private lateinit var btnCollarNo: TextView

    // Track selected units
    private var selectedAgeUnit = "years" // default
    private var selectedWeightUnit = "kg" // default

    // Error Views
    private lateinit var errorPetName: TextView
    private lateinit var errorPetType: TextView
    private lateinit var errorAge: TextView
    private lateinit var errorWeight: TextView
    private lateinit var errorStatus: TextView
    private lateinit var errorEventDate: TextView
    private lateinit var errorLocation: TextView
    private lateinit var errorContact: TextView
    private lateinit var errorDescription: TextView
    private lateinit var errorReward: TextView
    private lateinit var errorIdentifyingMarks: TextView
    private lateinit var errorHealthCondition: TextView

    private lateinit var layoutCategorySelector: LinearLayout
    private lateinit var layoutBreedSelector: LinearLayout
    private lateinit var btnBackToCategories: ImageView
    private lateinit var layoutCategoryDogs: LinearLayout
    private lateinit var layoutCategoryCats: LinearLayout
    private lateinit var layoutCategoryFish: LinearLayout
    private lateinit var layoutCategoryBirds: LinearLayout
    private lateinit var ivCategoryDogs: ImageView
    private lateinit var ivCategoryCats: ImageView
    private lateinit var ivCategoryFish: ImageView
    private lateinit var ivCategoryBirds: ImageView
    private lateinit var tvCategoryDogs: TextView
    private lateinit var tvCategoryCats: TextView
    private lateinit var tvCategoryFish: TextView
    private lateinit var tvCategoryBirds: TextView
    private var selectedCategory = ""
    private var selectedCaseType = "owner_lost"
    private var selectedCurrentCareStatus = "owner"
    private var selectedContactPreference = "call"
    private var hasCollar = false

    // Data
    private var selectedStatus = "Lost"
    private var selectedGender = "Unknown"
    private val selectedImages = mutableListOf<Uri>()
    private val tempUris = mutableListOf<Uri>()
    private var currentImageUri: Uri? = null
    private var currentPhotoPath: String? = null
    private lateinit var sessionManager: SessionManager
    private lateinit var photoPagerAdapter: PhotoPagerAdapter
    private var currentPhotoPosition = 0
    private lateinit var breedAdapter: SuggestionsAdapter
    private var latitude: Double? = null
    private var longitude: Double? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Repositories
    private val uploadRepository = UploadRepository()
    private val postRepository = PostRepository()

    companion object {
        private const val REQUEST_PICK_IMAGE = 1001
        private const val REQUEST_CROP_IMAGE = 1002
        private const val REQUEST_CODE_CAMERA = 1004
        private const val REQUEST_CODE_STORAGE = 1005
        private const val REQUEST_CODE_LOCATION = 1006
        private const val TAG = "CreatePostActivity"
        private const val STATE_CURRENT_IMAGE_URI = "state_current_image_uri"
        private const val STATE_CURRENT_PHOTO_PATH = "state_current_photo_path"
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) {
            Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        val capturedUri = resolveCapturedImageUri()
        if (capturedUri == null) {
            Toast.makeText(this, "Captured image could not be found", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        currentImageUri = capturedUri
        startCrop(capturedUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val currentUser = sessionManager.getCurrentUser()

        if (currentUser == null || currentUser.firebaseUid.isNullOrEmpty()) {
            Toast.makeText(this, "Session error. Please login again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupAdapters()
        setupValidationListeners()
        setupClickListeners()
        setupKeyboardScrollHandlers()

        currentImageUri = savedInstanceState?.getParcelable(STATE_CURRENT_IMAGE_URI)
        currentPhotoPath = savedInstanceState?.getString(STATE_CURRENT_PHOTO_PATH)

        // Set initial gender selection
        updateGenderSelection("Unknown", btnGenderUnknown)

        currentUser.phone
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { savedPhone ->
                etContact.setText(savedPhone)
                etContact.setSelection(savedPhone.length)
            }

        // Set initial unit selections
        updateAgeUnitButtons(btnAgeYears)
        updateWeightUnitButtons(btnWeightKg)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_CURRENT_IMAGE_URI, currentImageUri)
        outState.putString(STATE_CURRENT_PHOTO_PATH, currentPhotoPath)
    }

    private fun initializeViews() {
        // ===== INITIALIZE ALL VIEWS FIRST =====

        // Category selector views - INITIALIZE THESE FIRST!
        layoutCategorySelector = findViewById(R.id.layout_category_selector)
        layoutBreedSelector = findViewById(R.id.layout_breed_selector)
        btnBackToCategories = findViewById(R.id.btn_back_to_categories)

        // Category layouts
        layoutCategoryDogs = findViewById(R.id.layout_category_dogs)
        layoutCategoryCats = findViewById(R.id.layout_category_cats)
        layoutCategoryFish = findViewById(R.id.layout_category_fish)
        layoutCategoryBirds = findViewById(R.id.layout_category_birds)

        // Category images
        ivCategoryDogs = findViewById(R.id.iv_category_dogs)
        ivCategoryCats = findViewById(R.id.iv_category_cats)
        ivCategoryFish = findViewById(R.id.iv_category_fish)
        ivCategoryBirds = findViewById(R.id.iv_category_birds)

        // Category text views
        tvCategoryDogs = findViewById(R.id.tv_category_dogs)
        tvCategoryCats = findViewById(R.id.tv_category_cats)
        tvCategoryFish = findViewById(R.id.tv_category_fish)
        tvCategoryBirds = findViewById(R.id.tv_category_birds)

        // ===== MAIN INPUT FIELDS =====
        etPetName = findViewById(R.id.et_pet_name)
        actPetType = findViewById(R.id.act_pet_type)
        etAge = findViewById(R.id.et_age)
        etWeight = findViewById(R.id.et_weight)
        etReward = findViewById(R.id.et_reward)
        etEventDate = findViewById(R.id.et_event_date)
        tvLocation = findViewById(R.id.tv_location)
        tvEventDateLabel = findViewById(R.id.tv_event_date_label)
        tvEventLocationLabel = findViewById(R.id.tv_event_location_label)
        tvCaseTypeHint = findViewById(R.id.tv_case_type_hint)
        btnSelectLocation = findViewById(R.id.btn_select_location)
        etContact = findViewById(R.id.et_contact)
        etDescription = findViewById(R.id.et_description)
        etIdentifyingMarks = findViewById(R.id.et_identifying_marks)
        etTemperament = findViewById(R.id.et_temperament)
        etHealthCondition = findViewById(R.id.et_health_condition)

        // ===== STATUS BUTTONS =====
        btnStatusLost = findViewById(R.id.btn_status_lost)
        btnStatusFound = findViewById(R.id.btn_status_found)
        btnStatusAdoption = findViewById(R.id.btn_status_adoption)
        btnCaseOwnerLost = findViewById(R.id.btn_case_owner_lost)
        btnCaseSeenLostPet = findViewById(R.id.btn_case_seen_lost_pet)
        btnCaseFoundInCare = findViewById(R.id.btn_case_found_in_care)
        btnCaseAdoption = findViewById(R.id.btn_case_adoption)

        // ===== GENDER BUTTONS =====
        btnGenderMale = findViewById(R.id.btn_gender_male)
        btnGenderFemale = findViewById(R.id.btn_gender_female)
        btnGenderUnknown = findViewById(R.id.btn_gender_unknown)

        // ===== ACTION BUTTONS =====
        layoutReward = findViewById(R.id.layout_reward)
        layoutCurrentCare = findViewById(R.id.layout_current_care)
        layoutHealthCondition = findViewById(R.id.layout_health_condition)
        btnPost = findViewById(R.id.btn_post)
        btnCancel = findViewById(R.id.btn_cancel)
        btnAddPhoto = findViewById(R.id.btn_add_photo)
        photoCountBadge = findViewById(R.id.tv_photo_count)

        // ===== PHOTO CAROUSEL VIEWS =====
        carouselContainer = findViewById(R.id.carousel_container)
        photoEmptyState = findViewById(R.id.photo_empty_state)
        photoViewPager = findViewById(R.id.photo_view_pager)
        btnRemoveCurrentPhoto = findViewById(R.id.btn_remove_current_photo)
        photoIndicatorContainer = findViewById(R.id.photo_indicator_container)
        thumbnailScroll = findViewById(R.id.thumbnail_scroll)
        thumbnailContainer = findViewById(R.id.thumbnail_container)
        photoUploadProgress = findViewById(R.id.photo_upload_progress)
        imagePreviewContainer = findViewById(R.id.image_preview_container)

        // ===== 🔥 NEW: Age and Weight Unit Views =====
        layoutAgeUnits = findViewById(R.id.layout_age_units)
        btnAgeYears = findViewById(R.id.btn_age_years)
        btnAgeMonths = findViewById(R.id.btn_age_months)
        layoutWeightUnits = findViewById(R.id.layout_weight_units)
        btnWeightKg = findViewById(R.id.btn_weight_kg)
        btnWeightLbs = findViewById(R.id.btn_weight_lbs)
        btnCareInMyCare = findViewById(R.id.btn_care_in_my_care)
        btnCareSightingOnly = findViewById(R.id.btn_care_sighting_only)
        btnContactCall = findViewById(R.id.btn_contact_call)
        btnContactText = findViewById(R.id.btn_contact_text)
        btnContactChat = findViewById(R.id.btn_contact_chat)
        btnCollarYes = findViewById(R.id.btn_collar_yes)
        btnCollarNo = findViewById(R.id.btn_collar_no)

        // ===== INITIALIZE ADAPTERS =====
        photoPagerAdapter = PhotoPagerAdapter(selectedImages) { uri ->
            // Handle click if needed
        }
        photoViewPager.adapter = photoPagerAdapter

        // ===== SETUP VIEWPAGER CALLBACK =====
        photoViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPhotoPosition = position
                updatePhotoIndicators(position)
            }
        })

        // ===== CREATE ERROR TEXTVIEWS =====
        errorPetName = createErrorTextView()
        errorPetType = createErrorTextView()
        errorAge = createErrorTextView()
        errorWeight = createErrorTextView()
        errorStatus = createErrorTextView()
        errorEventDate = createErrorTextView()
        errorLocation = createErrorTextView()
        errorContact = createErrorTextView()
        errorDescription = createErrorTextView()
        errorReward = createErrorTextView()
        errorIdentifyingMarks = createErrorTextView()
        errorHealthCondition = createErrorTextView()

        // ===== ADD ERROR VIEWS AFTER EACH INPUT =====
        addErrorViewAfter(etPetName, errorPetName)
        addErrorViewAfter(actPetType, errorPetType)
        addErrorViewAfter(etAge, errorAge)
        addErrorViewAfter(etWeight, errorWeight)
        addErrorViewAfter(etEventDate, errorEventDate)
        addErrorViewAfter(btnSelectLocation, errorLocation)
        addErrorViewAfter(etContact, errorContact)
        addErrorViewAfter(etReward, errorReward)
        addErrorViewAfter(etIdentifyingMarks, errorIdentifyingMarks)
        addErrorViewAfter(etHealthCondition, errorHealthCondition)
        addErrorViewAfter(etDescription, errorDescription)

        // Status error goes after status buttons
        val statusLayout = findViewById<LinearLayout>(R.id.status_buttons_layout)
        addErrorViewAfter(statusLayout, errorStatus)

        // ===== SET INITIAL STATES =====
        // Set initial status
        updateStatusButtons(btnStatusLost)
        updateCaseTypeSelection(btnCaseOwnerLost, "owner_lost")
        updateCurrentCareButtons(btnCareInMyCare)
        updateContactPreferenceButtons(btnContactCall)
        updateCollarButtons(false)
        updateCaseSpecificUi()

        // Initialize location TextView
        tvLocation.text = ""
        tvLocation.visibility = View.GONE

        updatePhotoCountBadge()
    }

    private fun createErrorTextView(): TextView {
        val textView = TextView(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.topMargin = 4
        layoutParams.bottomMargin = 8
        textView.layoutParams = layoutParams
        textView.textSize = 12f
        textView.setTextColor(Color.parseColor("#F44336"))
        textView.visibility = View.GONE
        return textView
    }

    private fun addErrorViewAfter(view: View, errorView: TextView) {
        val parent = view.parent as? ViewGroup
        if (parent != null) {
            val index = parent.indexOfChild(view)
            parent.addView(errorView, index + 1)
        }
    }

    private fun setupAdapters() {
        // Initially show empty adapter until category is selected
        breedAdapter = SuggestionsAdapter()
        breedAdapter.setData(emptyList())
        actPetType.setAdapter(breedAdapter)
        actPetType.threshold = 1

        // Show category selector first, hide breed selector
        layoutCategorySelector.visibility = View.VISIBLE
        layoutBreedSelector.visibility = View.GONE

        setupCategoryClickListeners()
    }

    private fun setupKeyboardScrollHandlers() {
        KeyboardAwareScrollHelper.attach(
            etPetName,
            actPetType,
            etAge,
            etWeight,
            etReward,
            etEventDate,
            etContact,
            etIdentifyingMarks,
            etTemperament,
            etHealthCondition
        )

        // Multiline description needs more lift so the active typing area stays above the keyboard.
        KeyboardAwareScrollHelper.attach(
            etDescription,
            topPaddingDp = 140
        )
    }

    private fun setupCategoryClickListeners() {
        layoutCategoryDogs.setOnClickListener {
            selectedCategory = "Dogs"
            highlightCategory(layoutCategoryDogs, ivCategoryDogs, tvCategoryDogs)

            // Get dog breeds and add "Unknown Dog" sa list
            val breeds = PetData.dogBreeds.toMutableList()
            breeds.add(0, "Unknown Dog")  // ← Specific for dogs
            breeds.add(1, "Other Dog")

            showBreedSelector(breeds)
        }

        layoutCategoryCats.setOnClickListener {
            selectedCategory = "Cats"
            highlightCategory(layoutCategoryCats, ivCategoryCats, tvCategoryCats)

            // Get cat breeds and add "Unknown Cat" sa list
            val breeds = PetData.catBreeds.toMutableList()
            breeds.add(0, "Unknown Cat")  // ← Specific for cats
            breeds.add(1, "Other Cat")

            showBreedSelector(breeds)
        }

        layoutCategoryFish.setOnClickListener {
            selectedCategory = "Fish"
            highlightCategory(layoutCategoryFish, ivCategoryFish, tvCategoryFish)

            // Get fish breeds and add "Unknown Fish" sa list
            val breeds = PetData.fishBreeds.toMutableList()
            breeds.add(0, "Unknown Fish")  // ← Specific for fish
            breeds.add(1, "Other Fish")

            showBreedSelector(breeds)
        }

        layoutCategoryBirds.setOnClickListener {
            selectedCategory = "Birds"
            highlightCategory(layoutCategoryBirds, ivCategoryBirds, tvCategoryBirds)

            // Get bird breeds and add "Unknown Bird" sa list
            val breeds = PetData.birdBreeds.toMutableList()
            breeds.add(0, "Unknown Bird")  // ← Specific for birds
            breeds.add(1, "Other Bird")

            showBreedSelector(breeds)
        }

        btnBackToCategories.setOnClickListener {
            showCategorySelector()
        }
    }

    private fun highlightCategory(selectedLayout: LinearLayout, selectedImage: ImageView, selectedText: TextView) {
        // Reset all categories to default
        val categories = listOf(
            layoutCategoryDogs to Pair(ivCategoryDogs, tvCategoryDogs),
            layoutCategoryCats to Pair(ivCategoryCats, tvCategoryCats),
            layoutCategoryFish to Pair(ivCategoryFish, tvCategoryFish),
            layoutCategoryBirds to Pair(ivCategoryBirds, tvCategoryBirds)
        )

        categories.forEach { (layout, views) ->
            val (imageView, textView) = views
            // Reset to default brown color
            imageView.setColorFilter(Color.parseColor("#7A4F2B"))
            textView.setTextColor(Color.parseColor("#7A4F2B"))
            layout.alpha = 1.0f
        }

        // Highlight selected category
        selectedImage.setColorFilter(Color.parseColor("#B88B4A")) // Lighter brown for selected
        selectedText.setTextColor(Color.parseColor("#B88B4A"))
        selectedLayout.alpha = 1.0f

        // Optional: Add a scale animation
        selectedLayout.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(100)
            .withEndAction {
                selectedLayout.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun showBreedSelector(breeds: List<String>) {
        layoutCategorySelector.visibility = View.GONE
        layoutBreedSelector.visibility = View.VISIBLE

        breedAdapter.setData(breeds)

        // AUTO-FILL with the first option (which is "Unknown Dog/Cat/etc")
        if (breeds.isNotEmpty()) {
            actPetType.setText(breeds[0])  // Sets "Unknown Dog" or "Unknown Cat"
        }

        // Show keyboard
        actPetType.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(actPetType, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showCategorySelector() {
        layoutCategorySelector.visibility = View.VISIBLE
        layoutBreedSelector.visibility = View.GONE
        actPetType.setText("") // Clear breed input
        selectedCategory = ""

        // Reset all category highlighting
        val categories = listOf(
            layoutCategoryDogs to Pair(ivCategoryDogs, tvCategoryDogs),
            layoutCategoryCats to Pair(ivCategoryCats, tvCategoryCats),
            layoutCategoryFish to Pair(ivCategoryFish, tvCategoryFish),
            layoutCategoryBirds to Pair(ivCategoryBirds, tvCategoryBirds)
        )

        categories.forEach { (layout, views) ->
            val (imageView, textView) = views
            imageView.setColorFilter(Color.parseColor("#7A4F2B"))
            textView.setTextColor(Color.parseColor("#7A4F2B"))
            layout.scaleX = 1.0f
            layout.scaleY = 1.0f
        }
    }

    private fun setupValidationListeners() {
        // Pet Name validation
        etPetName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePetName()
            }
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty() && s.length == 1) {
                    etPetName.removeTextChangedListener(this)
                    etPetName.setText(s.toString().uppercase())
                    etPetName.setSelection(1)
                    etPetName.addTextChangedListener(this)
                }
            }
        })

        etPetName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                actPetType.requestFocus()
                true
            } else {
                false
            }
        }

        // Pet Type validation
        actPetType.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePetType()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        actPetType.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                etAge.requestFocus()
                true
            } else {
                false
            }
        }

        // ===== AGE (OPTIONAL) - ONLY NUMBER VALIDATION =====
        etAge.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateAge()
            }
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.toString().trim().isEmpty()
                layoutAgeUnits.visibility = if (hasText) View.VISIBLE else View.GONE

                if (hasText) {
                    // Validate only numbers
                    val input = s.toString()
                    if (!input.matches(Regex("^\\d*\\.?\\d*$"))) {
                        etAge.error = "Only numbers allowed"
                    } else {
                        etAge.error = null
                    }
                }
            }
        })

        etAge.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                etWeight.requestFocus()
                true
            } else {
                false
            }
        }

        // ===== WEIGHT (OPTIONAL) - ONLY NUMBER VALIDATION =====
        etWeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateWeight()
            }
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.toString().trim().isEmpty()
                layoutWeightUnits.visibility = if (hasText) View.VISIBLE else View.GONE

                if (hasText) {
                    // Validate only numbers (allow decimals)
                    val input = s.toString()
                    if (!input.matches(Regex("^\\d*\\.?\\d*$"))) {
                        etWeight.error = "Only numbers allowed"
                    } else {
                        etWeight.error = null
                    }
                }
            }
        })

        etWeight.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                if (selectedStatus == "Lost") {
                    etReward.requestFocus()
                } else {
                    btnSelectLocation.requestFocus()
                }
                true
            } else {
                false
            }
        }

        // ===== AGE UNIT BUTTON CLICKS =====
        btnAgeYears.setOnClickListener {
            if (btnAgeYears.isClickable) {
                selectedAgeUnit = "years"
                updateAgeUnitButtons(btnAgeYears)
            }
        }

        btnAgeMonths.setOnClickListener {
            if (btnAgeMonths.isClickable) {
                selectedAgeUnit = "months"
                updateAgeUnitButtons(btnAgeMonths)
            }
        }

        // ===== WEIGHT UNIT BUTTON CLICKS =====
        btnWeightKg.setOnClickListener {
            if (btnWeightKg.isClickable) {
                selectedWeightUnit = "kg"
                updateWeightUnitButtons(btnWeightKg)
            }
        }

        btnWeightLbs.setOnClickListener {
            if (btnWeightLbs.isClickable) {
                selectedWeightUnit = "lbs"
                updateWeightUnitButtons(btnWeightLbs)
            }
        }

        // REWARD FIELD - With formatting and 1 million limit
        etReward.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateReward()
            }
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                val input = s.toString()
                if (input.isEmpty()) return
                val rawInput = input.replace(",", "")
                try {
                    val number = rawInput.toLongOrNull()
                    if (number != null) {
                        isUpdating = true
                        val limitedNumber = if (number > 1000000) {
                            Toast.makeText(this@CreatePostActivity, "Maximum reward is ₱1,000,000", Toast.LENGTH_SHORT).show()
                            1000000
                        } else {
                            number
                        }
                        val formatted = String.format("%,d", limitedNumber)
                        if (formatted != input) {
                            etReward.setText(formatted)
                            etReward.setSelection(formatted.length)
                        }
                        isUpdating = false
                    }
                } catch (e: Exception) {
                    isUpdating = false
                }
            }
        })

        etIdentifyingMarks.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateIdentifyingMarks()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etHealthCondition.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateHealthCondition()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etReward.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                btnSelectLocation.requestFocus()
                true
            } else {
                false
            }
        }

        // Contact validation
        etContact.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateContact()
            }
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                val digits = input.filter { it.isDigit() }
                if (digits != input && digits.length <= 11) {
                    etContact.removeTextChangedListener(this)
                    etContact.setText(digits)
                    etContact.setSelection(digits.length)
                    etContact.addTextChangedListener(this)
                }
            }
        })

        etContact.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                etDescription.requestFocus()
                true
            } else {
                false
            }
        }

        // Description validation
        etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateDescription()
            }
            override fun afterTextChanged(s: Editable?) {
                updateCharCounter()
            }
        })

        etDescription.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    // ===== UNIT BUTTON UPDATE FUNCTIONS =====
    private fun updateAgeUnitButtons(selected: TextView) {
        // Enable both buttons
        btnAgeYears.isClickable = true
        btnAgeYears.isFocusable = true
        btnAgeMonths.isClickable = true
        btnAgeMonths.isFocusable = true

        // Highlight selected
        if (selected == btnAgeYears) {
            btnAgeYears.setBackgroundResource(R.drawable.button_oval_brown)
            btnAgeYears.setTextColor(Color.WHITE)
            btnAgeMonths.setBackgroundResource(R.drawable.button_oval_brown_outline)
            btnAgeMonths.setTextColor(Color.parseColor("#7A4F2B"))
        } else {
            btnAgeMonths.setBackgroundResource(R.drawable.button_oval_brown)
            btnAgeMonths.setTextColor(Color.WHITE)
            btnAgeYears.setBackgroundResource(R.drawable.button_oval_brown_outline)
            btnAgeYears.setTextColor(Color.parseColor("#7A4F2B"))
        }
    }

    private fun updateWeightUnitButtons(selected: TextView) {
        // Enable both buttons
        btnWeightKg.isClickable = true
        btnWeightKg.isFocusable = true
        btnWeightLbs.isClickable = true
        btnWeightLbs.isFocusable = true

        // Highlight selected
        if (selected == btnWeightKg) {
            btnWeightKg.setBackgroundResource(R.drawable.button_oval_brown)
            btnWeightKg.setTextColor(Color.WHITE)
            btnWeightLbs.setBackgroundResource(R.drawable.button_oval_brown_outline)
            btnWeightLbs.setTextColor(Color.parseColor("#7A4F2B"))
        } else {
            btnWeightLbs.setBackgroundResource(R.drawable.button_oval_brown)
            btnWeightLbs.setTextColor(Color.WHITE)
            btnWeightKg.setBackgroundResource(R.drawable.button_oval_brown_outline)
            btnWeightKg.setTextColor(Color.parseColor("#7A4F2B"))
        }
    }

    // ===== VALIDATION FUNCTIONS (Age and Weight now optional) =====
    private fun validateAge(): Boolean {
        val age = etAge.text.toString().trim()
        return if (age.isEmpty()) {
            // Age is optional - no error
            etAge.setBackgroundResource(R.drawable.edittext_bg)
            errorAge.visibility = View.GONE
            true
        } else {
            // Only validate that it's a number
            if (age.matches(Regex("^\\d*\\.?\\d*$"))) {
                etAge.setBackgroundResource(R.drawable.edittext_bg)
                errorAge.visibility = View.GONE
                true
            } else {
                etAge.setBackgroundResource(R.drawable.edittext_error_bg)
                errorAge.text = "Only numbers allowed"
                errorAge.visibility = View.VISIBLE
                false
            }
        }
    }

    private fun validateWeight(): Boolean {
        val weight = etWeight.text.toString().trim()
        return if (weight.isEmpty()) {
            // Weight is optional - no error
            etWeight.setBackgroundResource(R.drawable.edittext_bg)
            errorWeight.visibility = View.GONE
            true
        } else {
            // Only validate that it's a number (allow decimals)
            if (weight.matches(Regex("^\\d*\\.?\\d*$"))) {
                etWeight.setBackgroundResource(R.drawable.edittext_bg)
                errorWeight.visibility = View.GONE
                true
            } else {
                etWeight.setBackgroundResource(R.drawable.edittext_error_bg)
                errorWeight.text = "Only numbers allowed"
                errorWeight.visibility = View.VISIBLE
                false
            }
        }
    }

    private fun validatePetName(): Boolean {
        val name = etPetName.text.toString().trim()
        return when {
            name.isEmpty() -> {
                etPetName.setBackgroundResource(R.drawable.edittext_error_bg)
                errorPetName.text = "Pet name is required"
                errorPetName.visibility = View.VISIBLE
                false
            }
            name.length < 2 -> {
                etPetName.setBackgroundResource(R.drawable.edittext_error_bg)
                errorPetName.text = "Pet name must be at least 2 characters"
                errorPetName.visibility = View.VISIBLE
                false
            }
            !name.matches(Regex("^[A-Za-z0-9 .'-]+$")) -> {
                etPetName.setBackgroundResource(R.drawable.edittext_error_bg)
                errorPetName.text = "Use letters and numbers only"
                errorPetName.visibility = View.VISIBLE
                false
            }
            name.length > 10 -> {
                etPetName.setBackgroundResource(R.drawable.edittext_error_bg)
                errorPetName.text = "Pet name must be max 10 characters"
                errorPetName.visibility = View.VISIBLE
                false
            }
            else -> {
                etPetName.setBackgroundResource(R.drawable.edittext_bg)
                errorPetName.visibility = View.GONE
                true
            }
        }
    }

    private fun validatePetType(): Boolean {
        val type = actPetType.text.toString().trim()
        return when {
            type.isEmpty() -> {
                actPetType.setBackgroundResource(R.drawable.edittext_error_bg)
                errorPetType.text = "Pet type/breed is required"
                errorPetType.visibility = View.VISIBLE
                false
            }
            type.length < 2 -> {
                actPetType.setBackgroundResource(R.drawable.edittext_error_bg)
                errorPetType.text = "Pet type/breed must be at least 2 characters"
                errorPetType.visibility = View.VISIBLE
                false
            }
            else -> {
                actPetType.setBackgroundResource(R.drawable.edittext_bg)
                errorPetType.visibility = View.GONE
                true
            }
        }
    }

    private fun validateStatus(): Boolean {
        return if (selectedStatus.isEmpty()) {
            errorStatus.text = "Please select a status"
            errorStatus.visibility = View.VISIBLE
            false
        } else {
            errorStatus.visibility = View.GONE
            true
        }
    }

    private fun validateEventDate(): Boolean {
        val eventDate = etEventDate.text.toString().trim()
        return if (eventDate.isEmpty()) {
            etEventDate.setBackgroundResource(R.drawable.edittext_error_bg)
            errorEventDate.text = "Date is required"
            errorEventDate.visibility = View.VISIBLE
            false
        } else if (isFutureDate(eventDate)) {
            etEventDate.setBackgroundResource(R.drawable.edittext_error_bg)
            errorEventDate.text = "Date cannot be in the future"
            errorEventDate.visibility = View.VISIBLE
            false
        } else {
            etEventDate.setBackgroundResource(R.drawable.edittext_bg)
            errorEventDate.visibility = View.GONE
            true
        }
    }

    private fun validateLocation(): Boolean {
        val location = tvLocation.text.toString().trim()
        return when {
            location.isEmpty() || location.equals("No location selected", ignoreCase = true) -> {
                btnSelectLocation.setBackgroundResource(R.drawable.edittext_error_bg)
                errorLocation.text = "Location is required"
                errorLocation.visibility = View.VISIBLE
                false
            }
            !isAllowedPangasinanLocation(location) -> {
                btnSelectLocation.setBackgroundResource(R.drawable.edittext_error_bg)
                errorLocation.text = "Location must be within Pangasinan"
                errorLocation.visibility = View.VISIBLE
                false
            }
            else -> {
                btnSelectLocation.setBackgroundResource(R.drawable.edittext_bg)
                errorLocation.visibility = View.GONE
                true
            }
        }
    }

    private fun validateContact(): Boolean {
        val contact = etContact.text.toString().trim()
        return when {
            contact.isEmpty() -> {
                etContact.setBackgroundResource(R.drawable.edittext_error_bg)
                errorContact.text = "Contact number is required"
                errorContact.visibility = View.VISIBLE
                false
            }
            !contact.matches(Regex("^09\\d{9}$")) -> {
                etContact.setBackgroundResource(R.drawable.edittext_error_bg)
                errorContact.text = "Must be 11 digits starting with 09"
                errorContact.visibility = View.VISIBLE
                false
            }
            else -> {
                etContact.setBackgroundResource(R.drawable.edittext_bg)
                errorContact.visibility = View.GONE
                true
            }
        }
    }

    private fun validateDescription(): Boolean {
        val description = etDescription.text.toString().trim()
        return when {
            description.isEmpty() -> {
                etDescription.setBackgroundResource(R.drawable.edittext_error_bg)
                errorDescription.text = "Description is required"
                errorDescription.visibility = View.VISIBLE
                false
            }
            description.length < 10 -> {
                etDescription.setBackgroundResource(R.drawable.edittext_error_bg)
                errorDescription.text = "Description must be at least 10 characters"
                errorDescription.visibility = View.VISIBLE
                false
            }
            description.length > 500 -> {
                etDescription.setBackgroundResource(R.drawable.edittext_error_bg)
                errorDescription.text = "Description must not exceed 500 characters"
                errorDescription.visibility = View.VISIBLE
                false
            }
            else -> {
                etDescription.setBackgroundResource(R.drawable.edittext_bg)
                errorDescription.visibility = View.GONE
                true
            }
        }
    }

    private fun validateReward(): Boolean {
        if (layoutReward.visibility != View.VISIBLE) {
            etReward.setBackgroundResource(R.drawable.edittext_bg)
            errorReward.visibility = View.GONE
            return true
        }

        val reward = etReward.text.toString().trim().replace(",", "")
        if (reward.isEmpty()) {
            etReward.setBackgroundResource(R.drawable.edittext_bg)
            errorReward.visibility = View.GONE
            return true
        }

        val amount = reward.toBigDecimalOrNull()
        return if (amount == null || amount <= BigDecimal.ZERO) {
            etReward.setBackgroundResource(R.drawable.edittext_error_bg)
            errorReward.text = "Reward must be a valid amount"
            errorReward.visibility = View.VISIBLE
            false
        } else {
            etReward.setBackgroundResource(R.drawable.edittext_bg)
            errorReward.visibility = View.GONE
            true
        }
    }

    private fun validateIdentifyingMarks(): Boolean {
        val marks = etIdentifyingMarks.text.toString().trim()
        val isRequired = selectedCaseType != "adoption"
        return when {
            !isRequired && marks.isEmpty() -> {
                etIdentifyingMarks.setBackgroundResource(R.drawable.edittext_bg)
                errorIdentifyingMarks.visibility = View.GONE
                true
            }
            marks.isEmpty() -> {
                etIdentifyingMarks.setBackgroundResource(R.drawable.edittext_error_bg)
                errorIdentifyingMarks.text = "Identifying marks are required"
                errorIdentifyingMarks.visibility = View.VISIBLE
                false
            }
            marks.length < 5 -> {
                etIdentifyingMarks.setBackgroundResource(R.drawable.edittext_error_bg)
                errorIdentifyingMarks.text = "Add at least 5 characters"
                errorIdentifyingMarks.visibility = View.VISIBLE
                false
            }
            else -> {
                etIdentifyingMarks.setBackgroundResource(R.drawable.edittext_bg)
                errorIdentifyingMarks.visibility = View.GONE
                true
            }
        }
    }

    private fun validateHealthCondition(): Boolean {
        val health = etHealthCondition.text.toString().trim()
        val isRequired = selectedCaseType == "found_in_care" || selectedCaseType == "adoption"
        return when {
            !isRequired && health.isEmpty() -> {
                etHealthCondition.setBackgroundResource(R.drawable.edittext_bg)
                errorHealthCondition.visibility = View.GONE
                true
            }
            health.isEmpty() -> {
                etHealthCondition.setBackgroundResource(R.drawable.edittext_error_bg)
                errorHealthCondition.text = "Health / condition is required"
                errorHealthCondition.visibility = View.VISIBLE
                false
            }
            health.length < 5 -> {
                etHealthCondition.setBackgroundResource(R.drawable.edittext_error_bg)
                errorHealthCondition.text = "Add at least 5 characters"
                errorHealthCondition.visibility = View.VISIBLE
                false
            }
            else -> {
                etHealthCondition.setBackgroundResource(R.drawable.edittext_bg)
                errorHealthCondition.visibility = View.GONE
                true
            }
        }
    }

    private fun validateImages(): Boolean {
        return if (selectedImages.isEmpty()) {
            Toast.makeText(this, "Please add at least 1 photo", Toast.LENGTH_SHORT).show()
            carouselContainer.setBackgroundResource(R.drawable.edittext_error_bg)
            photoEmptyState.setBackgroundResource(R.drawable.edittext_error_bg)
            false
        } else {
            carouselContainer.setBackgroundResource(0)
            photoEmptyState.setBackgroundResource(0)
            true
        }
    }

    private fun validateAllFields(): Boolean {
        val isPetNameValid = validatePetName()
        val isPetTypeValid = validatePetType()
        val isAgeValid = validateAge()  // Now optional
        val isWeightValid = validateWeight()  // Now optional
        val isStatusValid = validateStatus()
        val isEventDateValid = validateEventDate()
        val isLocationValid = validateLocation()
        val isContactValid = validateContact()
        val isRewardValid = validateReward()
        val isIdentifyingMarksValid = validateIdentifyingMarks()
        val isHealthConditionValid = validateHealthCondition()
        val isDescriptionValid = validateDescription()
        val isImagesValid = validateImages()

        return isPetNameValid && isPetTypeValid && isAgeValid && isWeightValid &&
                isStatusValid && isEventDateValid && isLocationValid && isContactValid &&
                isRewardValid && isIdentifyingMarksValid && isHealthConditionValid &&
                isDescriptionValid && isImagesValid
    }

    private fun updateCharCounter() {
        val charCount = etDescription.text.toString().length
        val counterView = findViewById<TextView>(R.id.tv_char_counter)
        counterView.text = "$charCount/500 characters"

        when {
            charCount > 450 -> counterView.setTextColor(Color.parseColor("#FF9800"))
            charCount >= 500 -> counterView.setTextColor(Color.parseColor("#F44336"))
            else -> counterView.setTextColor(Color.parseColor("#999999"))
        }
    }

    private fun setupClickListeners() {
        // Cancel button
        btnCancel.setOnClickListener {
            finish()
        }

        // Add photo button
        btnAddPhoto.setOnClickListener {
            showImagePicker()
        }

        // Location selector button - GPS capture (automatic)
        btnSelectLocation.setOnClickListener {
            captureUserLocation()
        }

        etEventDate.setOnClickListener {
            showEventDatePicker()
        }

        // Status buttons
        btnStatusLost.setOnClickListener {
            updateStatusButtons(btnStatusLost)
            selectedStatus = "Lost"
            layoutReward.visibility = View.VISIBLE
            errorStatus.visibility = View.GONE
            if (selectedCaseType !in listOf("owner_lost", "seen_lost_pet")) {
                updateCaseTypeSelection(btnCaseOwnerLost, "owner_lost")
            } else {
                updateCaseSpecificUi()
            }
            Log.d(TAG, "Status selected: Lost")
        }

        btnStatusFound.setOnClickListener {
            updateStatusButtons(btnStatusFound)
            selectedStatus = "Found"
            layoutReward.visibility = View.GONE
            etReward.text.clear()
            errorStatus.visibility = View.GONE
            updateCaseTypeSelection(btnCaseFoundInCare, "found_in_care")
            Log.d(TAG, "Status selected: Found")
        }

        btnStatusAdoption.setOnClickListener {
            updateStatusButtons(btnStatusAdoption)
            selectedStatus = "Adoption"
            layoutReward.visibility = View.GONE
            etReward.text.clear()
            errorStatus.visibility = View.GONE
            updateCaseTypeSelection(btnCaseAdoption, "adoption")
            Log.d(TAG, "Status selected: Adoption")
        }

        btnCaseOwnerLost.setOnClickListener { updateCaseTypeSelection(btnCaseOwnerLost, "owner_lost") }
        btnCaseSeenLostPet.setOnClickListener { updateCaseTypeSelection(btnCaseSeenLostPet, "seen_lost_pet") }
        btnCaseFoundInCare.setOnClickListener { updateCaseTypeSelection(btnCaseFoundInCare, "found_in_care") }
        btnCaseAdoption.setOnClickListener { updateCaseTypeSelection(btnCaseAdoption, "adoption") }

        // Gender Listeners
        btnGenderMale.setOnClickListener { updateGenderSelection("Male", btnGenderMale) }
        btnGenderFemale.setOnClickListener { updateGenderSelection("Female", btnGenderFemale) }
        btnGenderUnknown.setOnClickListener { updateGenderSelection("Unknown", btnGenderUnknown) }

        btnCareInMyCare.setOnClickListener { updateCurrentCareButtons(btnCareInMyCare) }
        btnCareSightingOnly.setOnClickListener { updateCurrentCareButtons(btnCareSightingOnly) }
        btnContactCall.setOnClickListener { updateContactPreferenceButtons(btnContactCall) }
        btnContactText.setOnClickListener { updateContactPreferenceButtons(btnContactText) }
        btnContactChat.setOnClickListener { updateContactPreferenceButtons(btnContactChat) }
        btnCollarYes.setOnClickListener { updateCollarButtons(true) }
        btnCollarNo.setOnClickListener { updateCollarButtons(false) }

        // Remove current photo button
        btnRemoveCurrentPhoto.setOnClickListener {
            if (selectedImages.isNotEmpty() && currentPhotoPosition < selectedImages.size) {
                removePhotoAt(currentPhotoPosition)
            }
        }

        // Post button
        btnPost.setOnClickListener {
            val currentUser = sessionManager.getCurrentUser()
            if (currentUser != null && validateAllFields()) {
                Log.d(TAG, "📝 Creating post with age: ${etAge.text}, weight: ${etWeight.text}")
                Toast.makeText(this, "Creating post...", Toast.LENGTH_SHORT).show()
                createNewPost(currentUser)
            }
        }
    }

    private fun updateCaseTypeSelection(selectedView: TextView, caseType: String) {
        selectedCaseType = caseType

        val buttons = listOf(btnCaseOwnerLost, btnCaseSeenLostPet, btnCaseFoundInCare, btnCaseAdoption)
        buttons.forEach {
            it.setBackgroundResource(R.drawable.button_oval_brown_outline)
            it.setTextColor(Color.parseColor("#7A4F2B"))
        }

        selectedView.setBackgroundResource(R.drawable.button_oval_brown)
        selectedView.setTextColor(Color.WHITE)

        selectedStatus = when (caseType) {
            "found_in_care" -> "Found"
            "adoption" -> "Adoption"
            else -> "Lost"
        }

        when (selectedStatus) {
            "Lost" -> updateStatusButtons(btnStatusLost)
            "Found" -> updateStatusButtons(btnStatusFound)
            else -> updateStatusButtons(btnStatusAdoption)
        }

        updateCaseSpecificUi()
        validateIdentifyingMarks()
        validateHealthCondition()
        validateReward()
    }

    private fun updateCurrentCareButtons(selectedView: TextView) {
        val buttons = listOf(btnCareInMyCare, btnCareSightingOnly)
        buttons.forEach {
            it.setBackgroundResource(R.drawable.button_oval_brown_outline)
            it.setTextColor(Color.parseColor("#7A4F2B"))
        }

        selectedView.setBackgroundResource(R.drawable.button_oval_brown)
        selectedView.setTextColor(Color.WHITE)
        selectedCurrentCareStatus = if (selectedView == btnCareInMyCare) "in_my_care" else "sighting_only"
    }

    private fun updateContactPreferenceButtons(selectedView: TextView) {
        val buttons = listOf(btnContactCall, btnContactText, btnContactChat)
        buttons.forEach {
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

    private fun updateCollarButtons(hasCollarSelected: Boolean) {
        hasCollar = hasCollarSelected

        if (hasCollarSelected) {
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

    private fun updateCaseSpecificUi() {
        when (selectedCaseType) {
            "owner_lost" -> {
                layoutReward.visibility = View.VISIBLE
                layoutCurrentCare.visibility = View.GONE
                layoutHealthCondition.visibility = View.GONE
                selectedCurrentCareStatus = "owner"
                tvEventDateLabel.text = "Last Seen Date"
                tvEventLocationLabel.text = "Last Seen Location"
                tvCaseTypeHint.text = "Use this when your own pet is missing."
                etEventDate.hint = "When was your pet last seen?"
                etIdentifyingMarks.hint = "Color patterns, scars, clothes, accessories, unique features..."
                etTemperament.hint = "Friendly, shy, scared, calm, energetic..."
            }
            "seen_lost_pet" -> {
                layoutReward.visibility = View.GONE
                etReward.text.clear()
                layoutCurrentCare.visibility = View.GONE
                layoutHealthCondition.visibility = View.GONE
                selectedCurrentCareStatus = "sighting_only"
                tvEventDateLabel.text = "Sighting Date"
                tvEventLocationLabel.text = "Where You Saw The Pet"
                tvCaseTypeHint.text = "Use this when you saw a pet that may be lost but you do not have it."
                etEventDate.hint = "When did you see the pet?"
                etIdentifyingMarks.hint = "Collar, accessories, color patterns, where it was roaming..."
                etTemperament.hint = "Scared, roaming, friendly, hiding..."
            }
            "found_in_care" -> {
                layoutReward.visibility = View.GONE
                etReward.text.clear()
                layoutCurrentCare.visibility = View.VISIBLE
                layoutHealthCondition.visibility = View.VISIBLE
                if (selectedCurrentCareStatus == "owner") {
                    updateCurrentCareButtons(btnCareInMyCare)
                }
                tvEventDateLabel.text = "Found Date"
                tvEventLocationLabel.text = "Found Location"
                tvCaseTypeHint.text = "Use this when you found the pet and want to help return it."
                etEventDate.hint = "When did you find the pet?"
                etIdentifyingMarks.hint = "Collar, markings, visible features, where it was found..."
                etTemperament.hint = "Calm, scared, aggressive, gentle..."
            }
            else -> {
                layoutReward.visibility = View.GONE
                etReward.text.clear()
                layoutCurrentCare.visibility = View.GONE
                layoutHealthCondition.visibility = View.VISIBLE
                selectedCurrentCareStatus = "owner"
                tvEventDateLabel.text = "Posting Date"
                tvEventLocationLabel.text = "City / Pickup Area"
                tvCaseTypeHint.text = "Use this when the pet is ready to be adopted."
                etEventDate.hint = "When is this adoption post active from?"
                etIdentifyingMarks.hint = "Special traits, behavior notes, anything adopters should know..."
                etTemperament.hint = "Sweet, playful, shy, trained..."
            }
        }
        validateEventDate()
        validateLocation()
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

    private fun showEventDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            R.style.Theme_PawSociety_DatePickerDialog,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                etEventDate.setText(formatter.format(selectedCalendar.time))
                validateEventDate()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateGenderSelection(gender: String, selectedView: TextView) {
        selectedGender = gender
        Log.d(TAG, "Selected Gender: $selectedGender")

        val buttons = listOf(btnGenderMale, btnGenderFemale, btnGenderUnknown)
        buttons.forEach {
            it.setBackgroundResource(R.drawable.button_oval_white)
            it.setTextColor(Color.parseColor("#666666"))
        }

        val selectedBackground = when (gender) {
            "Male" -> R.drawable.button_oval_blue
            "Female" -> R.drawable.button_oval_pink
            else -> R.drawable.button_oval_gray
        }

        selectedView.setBackgroundResource(selectedBackground)
        selectedView.setTextColor(Color.WHITE)
    }

    private fun updateStatusButtons(selected: TextView) {
        btnStatusLost.setBackgroundResource(R.drawable.button_oval_status_lost)
        btnStatusLost.setTextColor(Color.WHITE)
        btnStatusFound.setBackgroundResource(R.drawable.button_oval_status_found)
        btnStatusFound.setTextColor(Color.WHITE)
        btnStatusAdoption.setBackgroundResource(R.drawable.button_oval_status_adoption)
        btnStatusAdoption.setTextColor(Color.WHITE)

        if (selected != btnStatusLost) btnStatusLost.alpha = 0.5f
        if (selected != btnStatusFound) btnStatusFound.alpha = 0.5f
        if (selected != btnStatusAdoption) btnStatusAdoption.alpha = 0.5f

        selected.alpha = 1.0f
    }

    // ==================== IMAGE PICKER WITH CROP ====================

    private fun showImagePicker() {
        if (selectedImages.size >= 5) {
            Toast.makeText(this, "Maximum 5 images allowed", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setTitle("Add Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> checkStoragePermission()
                    2 -> { /* Cancel */ }
                }
            }
            .show()
    }

    private fun checkCameraPermission() {
        if (PermissionHelper.hasCameraPermission(this)) {
            openCamera()
        } else {
            PermissionHelper.requestCameraPermission(this)
        }
    }

    private fun checkStoragePermission() {
        if (PermissionHelper.hasStoragePermission(this)) {
            openGallery()
        } else {
            PermissionHelper.requestStoragePermission(this)
        }
    }

    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            currentImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = externalCacheDir ?: cacheDir
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun resolveCapturedImageUri(): Uri? {
        currentImageUri?.let { return it }

        val photoPath = currentPhotoPath ?: return null
        val file = File(photoPath)
        if (!file.exists() || file.length() == 0L) {
            return null
        }

        return Uri.fromFile(file)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_PICK_IMAGE -> {
                    data?.let {
                        val clipData = it.clipData
                        if (clipData != null) {
                            tempUris.clear()
                            for (i in 0 until clipData.itemCount) {
                                if (selectedImages.size + tempUris.size < 5) {
                                    val uri = clipData.getItemAt(i).uri
                                    tempUris.add(uri)
                                }
                            }
                            if (tempUris.isNotEmpty()) {
                                startCrop(tempUris[0])
                            }
                        } else {
                            it.data?.let { uri ->
                                startCrop(uri)
                            }
                        }
                    }
                }
                REQUEST_CROP_IMAGE -> {
                    data?.let {
                        val resultUri = UCrop.getOutput(it)
                        resultUri?.let { croppedUri ->
                            addImageToList(croppedUri)

                            if (tempUris.isNotEmpty()) {
                                tempUris.removeAt(0)
                                if (tempUris.isNotEmpty()) {
                                    startCrop(tempUris[0])
                                }
                            }
                        }
                    }
                }
                UCrop.RESULT_ERROR -> {
                    val error = UCrop.getError(data!!)
                    Toast.makeText(this, "Crop error: ${error?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startCrop(uri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setCircleDimmedLayer(false)
            setShowCropFrame(true)
            setCropFrameColor(Color.parseColor("#7A4F2B"))
            setCropFrameStrokeWidth(4)
            setShowCropGrid(true)
            setCropGridColor(Color.parseColor("#FFFFFF"))
            setCropGridStrokeWidth(2)
            setStatusBarColor(Color.TRANSPARENT)
            setToolbarColor(Color.parseColor("#7A4F2B"))
            setToolbarTitle("Crop Image")
        }

        UCrop.of(uri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1024, 1024)
            .withOptions(options)
            .start(this, REQUEST_CROP_IMAGE)
    }

    private fun addImageToList(uri: Uri) {
        if (selectedImages.size < 5) {
            selectedImages.add(uri)
            updatePhotoDisplay()
            addImageToHorizontalPreview(uri)
            updatePhotoCountBadge()
            photoViewPager.post {
                photoViewPager.currentItem = selectedImages.size - 1
            }
            Toast.makeText(this, "Image added (${selectedImages.size}/5)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Maximum 5 photos allowed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addImageToHorizontalPreview(uri: Uri) {
        val imageView = LayoutInflater.from(this).inflate(R.layout.item_image_preview, imagePreviewContainer, false) as ImageView
        val params = LinearLayout.LayoutParams(200.dp, 200.dp)
        params.setMargins(0, 0, 8.dp, 0)
        imageView.layoutParams = params
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(imageView)

        imageView.setOnClickListener {
            val index = imagePreviewContainer.indexOfChild(imageView)
            if (index >= 0 && index < selectedImages.size) {
                AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
                    .setTitle("Remove Photo")
                    .setMessage("Do you want to remove this photo?")
                    .setPositiveButton("Remove") { _, _ ->
                        removePhotoAt(index)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        imagePreviewContainer.addView(imageView)
    }

    private fun updatePhotoDisplay() {
        if (selectedImages.isNotEmpty()) {
            carouselContainer.visibility = View.VISIBLE
            photoEmptyState.visibility = View.GONE
            thumbnailScroll.visibility = View.GONE

            photoPagerAdapter.updatePhotos(selectedImages)
            updatePhotoIndicators(photoViewPager.currentItem)

            if (selectedImages.size > 1) {
                photoIndicatorContainer.visibility = View.VISIBLE
            } else {
                photoIndicatorContainer.visibility = View.GONE
            }

            btnRemoveCurrentPhoto.visibility = View.VISIBLE
        } else {
            carouselContainer.visibility = View.GONE
            photoEmptyState.visibility = View.VISIBLE
            btnRemoveCurrentPhoto.visibility = View.GONE
        }
    }

    private fun updatePhotoIndicators(selectedPosition: Int) {
        photoIndicatorContainer.removeAllViews()

        for (i in 0 until selectedImages.size) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(8.dp, 8.dp)
            params.setMargins(4.dp, 0, 4.dp, 0)
            dot.layoutParams = params

            if (i == selectedPosition) {
                dot.setBackgroundResource(R.drawable.dot_indicator_selected)
            } else {
                dot.setBackgroundResource(R.drawable.dot_indicator)
            }

            photoIndicatorContainer.addView(dot)
        }
    }

    private fun removePhotoAt(position: Int) {
        selectedImages.removeAt(position)

        if (position < imagePreviewContainer.childCount) {
            imagePreviewContainer.removeViewAt(position)
        }

        updatePhotoDisplay()

        if (selectedImages.isEmpty()) {
            carouselContainer.visibility = View.GONE
            photoEmptyState.visibility = View.VISIBLE
            btnRemoveCurrentPhoto.visibility = View.GONE
        } else {
            val newPosition = if (position >= selectedImages.size) selectedImages.size - 1 else position
            photoViewPager.currentItem = newPosition
            photoPagerAdapter.updatePhotos(selectedImages)
            updatePhotoIndicators(newPosition)
        }

        updatePhotoCountBadge()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun updatePhotoCountBadge() {
        if (selectedImages.isNotEmpty()) {
            photoCountBadge.text = "${selectedImages.size}/5 photos"
            photoCountBadge.visibility = View.VISIBLE
            btnAddPhoto.text = "Add Photos"
        } else {
            photoCountBadge.text = "0/5 photos"
            photoCountBadge.visibility = View.VISIBLE
            btnAddPhoto.text = "+ Add Photo"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CODE_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CODE_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CODE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getDeviceLocation()
                } else {
                    Toast.makeText(this, "Location permission required to capture location", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== GPS LOCATION CAPTURE ====================

    private fun captureUserLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission already granted, get location
            getDeviceLocation()
        } else {
            // Request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_LOCATION
            )
        }
    }

    private fun getDeviceLocation() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            // Show loading toast
            Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()

            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                numUpdates = 1
            }

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        latitude = location.latitude
                        longitude = location.longitude

                        // Convert GPS coordinates to place name using Geocoder
                        convertCoordinatesToPlaceName(latitude!!, longitude!!)

                        Log.d(TAG, "📍 Location captured - Lat: $latitude, Lon: $longitude")

                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Security exception: ${e.message}")
        }
    }

    private fun convertCoordinatesToPlaceName(lat: Double, lon: Double) {
        try {
            val geocoder = Geocoder(this)
            
            // Get address from coordinates
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                
                // Build location string: City, Province/Municipality
                val city = address.locality ?: address.subAdminArea ?: "Unknown"
                val province = address.adminArea ?: "Pangasinan"
                val locationName = "$city, Pangasinan"

                if (!isWithinPangasinan(lat, lon)) {
                    tvLocation.text = ""
                    tvLocation.visibility = View.GONE
                    latitude = null
                    longitude = null
                    validateLocation()
                    Toast.makeText(this@CreatePostActivity, "PawSociety only supports Pangasinan locations", Toast.LENGTH_LONG).show()
                    return
                }
                
                tvLocation.text = locationName
                tvLocation.visibility = View.VISIBLE
                validateLocation()
                
                Toast.makeText(
                    this@CreatePostActivity,
                    "📍 Location: $locationName",
                    Toast.LENGTH_SHORT
                ).show()
                
                Log.d(TAG, "✅ Place name normalized to: $locationName (raw province: $province, Lat: $lat, Lon: $lon)")
            } else {
                // Fallback if geocoding fails
                if (isWithinPangasinan(lat, lon)) {
                    tvLocation.text = "Pangasinan"
                    tvLocation.visibility = View.VISIBLE
                    validateLocation()
                    Toast.makeText(
                        this@CreatePostActivity,
                        "Location saved in Pangasinan",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    tvLocation.text = ""
                    tvLocation.visibility = View.GONE
                    latitude = null
                    longitude = null
                    validateLocation()
                    Toast.makeText(this, "PawSociety only supports Pangasinan locations", Toast.LENGTH_LONG).show()
                }
                Log.d(TAG, "⚠️ No address found for coordinates")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding error: ${e.message}")
            if (isWithinPangasinan(lat, lon)) {
                tvLocation.text = "Pangasinan"
                tvLocation.visibility = View.VISIBLE
                validateLocation()
                Toast.makeText(this, "Using Pangasinan fallback location", Toast.LENGTH_SHORT).show()
            } else {
                tvLocation.text = ""
                tvLocation.visibility = View.GONE
                latitude = null
                longitude = null
                validateLocation()
                Toast.makeText(this, "PawSociety only supports Pangasinan locations", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== POST CREATION ====================

    private fun createNewPost(currentUser: com.example.pawsociety.api.ApiUser) {
        if (currentUser.firebaseUid.isNullOrEmpty()) {
            Toast.makeText(this, "Error: User ID is missing. Please logout and login again.", Toast.LENGTH_LONG).show()
            btnPost.text = "Post"
            btnPost.isEnabled = true
            return
        }

        val petName = etPetName.text.toString().trim()
        val petType = actPetType.text.toString().trim()

        // 🔥 FIXED: Handle empty age and weight properly - send empty strings, not null
        val ageNumber = etAge.text.toString().trim()
        val age = if (ageNumber.isNotEmpty()) {
            "$ageNumber $selectedAgeUnit"
        } else {
            "" // Send empty string, not null
        }

        val weightNumber = etWeight.text.toString().trim()
        val weight = if (weightNumber.isNotEmpty()) {
            "$weightNumber $selectedWeightUnit"
        } else {
            "" // Send empty string, not null
        }

        // Remove commas from reward before saving
        val rewardRaw = etReward.text.toString().trim().replace(",", "")
        val reward = if (selectedStatus == "Lost" && rewardRaw.isNotEmpty()) rewardRaw else ""

        val eventDate = etEventDate.text.toString().trim()
        val location = tvLocation.text.toString()
        val contact = etContact.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val identifyingMarks = etIdentifyingMarks.text.toString().trim()
        val temperament = etTemperament.text.toString().trim()
        val healthCondition = etHealthCondition.text.toString().trim()

        // Log all values
        Log.d(TAG, "=================================")
        Log.d(TAG, "📝 CREATING POST WITH VALUES:")
        Log.d(TAG, "   - User: ${currentUser.username} (UID: ${currentUser.firebaseUid})")
        Log.d(TAG, "   - Pet Name: $petName")
        Log.d(TAG, "   - Pet Type: $petType")
        Log.d(TAG, "   - Age: $age")
        Log.d(TAG, "   - Weight: $weight")
        Log.d(TAG, "   - Status: $selectedStatus")
        Log.d(TAG, "   - Gender: $selectedGender")
        Log.d(TAG, "   - Location: $location")
        Log.d(TAG, "   - Contact: $contact")
        Log.d(TAG, "   - Description: $description")
        Log.d(TAG, "   - Reward: $reward")
        Log.d(TAG, "   - Case Type: $selectedCaseType")
        Log.d(TAG, "   - Event Date: $eventDate")
        Log.d(TAG, "   - Care Status: $selectedCurrentCareStatus")
        Log.d(TAG, "   - Contact Preference: $selectedContactPreference")
        Log.d(TAG, "   - Images: ${selectedImages.size}")
        Log.d(TAG, "=================================")

        // Show loading state
        btnPost.text = "Posting..."
        btnPost.isEnabled = false

        lifecycleScope.launch {
            try {
                // Step 1: Upload images if any
                val imageUrls = if (selectedImages.isNotEmpty()) {
                    Toast.makeText(this@CreatePostActivity, "Uploading images...", Toast.LENGTH_SHORT).show()
                    uploadImages()
                } else {
                    emptyList()
                }

                // Right before calling postRepository.createPost
                Log.d("CreatePost", "📝 Creating post with category: $selectedCategory")
                Log.d("CreatePost", "📍 GPS Coords - Lat: $latitude, Lon: $longitude, Location: $location")

                // Step 2: Create post with all fields including age and weight (empty strings allowed)
                // In CreatePostActivity.kt, inside createNewPost function:

                val result = postRepository.createPost(
                    firebaseUid = currentUser.firebaseUid,
                    petName = petName,
                    petType = petType,
                    category = selectedCategory,
                    age = age,
                    weight = weight,
                    gender = selectedGender,
                    status = selectedStatus,
                    description = description,
                    contactInfo = contact,
                    location = if (location.isNotEmpty()) location else null,
                    reward = if (reward.isNotEmpty()) reward else null,
                    caseType = selectedCaseType,
                    eventDate = eventDate,
                    eventLocation = if (location.isNotEmpty()) location else null,
                    currentCareStatus = selectedCurrentCareStatus,
                    identifyingMarks = if (identifyingMarks.isNotEmpty()) identifyingMarks else null,
                    temperament = if (temperament.isNotEmpty()) temperament else null,
                    healthCondition = if (healthCondition.isNotEmpty()) healthCondition else null,
                    hasCollar = hasCollar,
                    contactPreference = selectedContactPreference,
                    imageUrls = if (imageUrls.isNotEmpty()) imageUrls else null,
                    latitude = latitude,
                    longitude = longitude
                )



                if (result.isSuccess) {
                    Log.d(TAG, "✅ Post created successfully with age: $age, weight: $weight")
                    Toast.makeText(this@CreatePostActivity, "✅ Post created successfully!", Toast.LENGTH_SHORT).show()

                    // IMPORTANT: Return the category of the post that was created
                    val intent = Intent()
                    intent.putExtra("post_created", true)
                    intent.putExtra("post_category", selectedCategory)  // Send the category (Dogs, Cats, etc.)
                    setResult(RESULT_OK, intent)

                    clearForm()
                    finish()

                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    Log.e(TAG, "❌ Failed to create post: $errorMsg")
                    Toast.makeText(this@CreatePostActivity, "❌ Failed: $errorMsg", Toast.LENGTH_SHORT).show()
                    btnPost.text = "Post"
                    btnPost.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@CreatePostActivity, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnPost.text = "Post"
                btnPost.isEnabled = true
            }
        }
    }

    private suspend fun uploadImages(): List<String> {
        if (selectedImages.isEmpty()) {
            return emptyList()
        }

        val imageFiles = selectedImages.mapNotNull { uri ->
            FileHelper.uriToFile(this, uri)?.let { file ->
                FileHelper.compressImage(file)
            }
        }

        if (imageFiles.isEmpty()) {
            throw Exception("Failed to process selected images")
        }

        val result = uploadRepository.uploadPostImages(imageFiles)

        // Clean up temp files
        imageFiles.forEach { file ->
            if (file.name.startsWith("compressed_") || file.name.startsWith("upload_") || file.name.startsWith("cropped_")) {
                FileHelper.deleteFile(file)
            }
        }

        return result.getOrNull() ?: emptyList()
    }

    private fun isAllowedPangasinanLocation(location: String): Boolean {
        val hasPangasinanText = location.contains("Pangasinan", ignoreCase = true)
        val hasPangasinanCoordinates = latitude != null && longitude != null && isWithinPangasinan(latitude!!, longitude!!)
        return hasPangasinanText || hasPangasinanCoordinates
    }

    private fun isWithinPangasinan(lat: Double, lon: Double): Boolean {
        return lat in 15.78..16.37 && lon in 119.80..120.89
    }

    private fun clearForm() {
        etPetName.text.clear()
        actPetType.text.clear()
        etAge.text.clear()
        etWeight.text.clear()
        etReward.text.clear()
        etEventDate.text.clear()
        tvLocation.text = ""
        tvLocation.visibility = View.GONE
        etContact.text.clear()
        etIdentifyingMarks.text.clear()
        etTemperament.text.clear()
        etHealthCondition.text.clear()
        etDescription.text.clear()

        // Clear photos
        selectedImages.clear()
        imagePreviewContainer.removeAllViews()
        updatePhotoDisplay()
        updatePhotoCountBadge()

        // Reset to Lost as default
        updateStatusButtons(btnStatusLost)
        selectedStatus = "Lost"
        updateCaseTypeSelection(btnCaseOwnerLost, "owner_lost")
        updateContactPreferenceButtons(btnContactCall)
        updateCollarButtons(false)
        updateCurrentCareButtons(btnCareInMyCare)

        // Reset gender to Unknown
        updateGenderSelection("Unknown", btnGenderUnknown)

        // Clear all errors
        etPetName.setBackgroundResource(R.drawable.edittext_bg)
        actPetType.setBackgroundResource(R.drawable.edittext_bg)
        etAge.setBackgroundResource(R.drawable.edittext_bg)
        etWeight.setBackgroundResource(R.drawable.edittext_bg)
        etEventDate.setBackgroundResource(R.drawable.edittext_bg)
        btnSelectLocation.setBackgroundResource(R.drawable.edittext_bg)
        etContact.setBackgroundResource(R.drawable.edittext_bg)
        etDescription.setBackgroundResource(R.drawable.edittext_bg)

        errorPetName.visibility = View.GONE
        errorPetType.visibility = View.GONE
        errorAge.visibility = View.GONE
        errorWeight.visibility = View.GONE
        errorStatus.visibility = View.GONE
        errorEventDate.visibility = View.GONE
        errorLocation.visibility = View.GONE
        errorContact.visibility = View.GONE
        errorReward.visibility = View.GONE
        errorIdentifyingMarks.visibility = View.GONE
        errorHealthCondition.visibility = View.GONE
        errorDescription.visibility = View.GONE

        updateCharCounter()
    }
}
