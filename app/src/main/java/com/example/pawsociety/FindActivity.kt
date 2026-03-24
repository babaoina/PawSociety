package com.example.pawsociety

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.data.repository.SearchRepository
import com.example.pawsociety.util.PermissionHelper
import com.example.pawsociety.util.SessionManager
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.example.pawsociety.util.InboxBadgeManager

// 🔥 ADD THIS IMPORT - To access inbox badge
import android.widget.TextView

class FindActivity : BaseNavigationActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var btnFilter: LinearLayout
    private lateinit var btnClearSearch: ImageView
    private lateinit var chipAll: Button
    private lateinit var chipLost: Button
    private lateinit var chipFound: Button
    private lateinit var chipAdoption: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSearchResults: TextView
    private lateinit var recentSearchesLayout: LinearLayout
    private lateinit var recentSearchesContainer: LinearLayout

    // 🔥 REMOVE THIS LINE - SessionManager is already in BaseNavigationActivity
    // private lateinit var sessionManager: SessionManager

    private val postRepository = PostRepository()
    private val searchRepository = SearchRepository()

    private var currentFilter = "All"
    private var allPosts = listOf<ApiPost>()
    private var filteredPosts = listOf<ApiPost>()
    private var searchQuery = ""
    private var isSearching = false



    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    // For recent searches
    private lateinit var sharedPrefs: android.content.SharedPreferences
    private val recentSearches = mutableListOf<String>()

    // For location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val locationRequestCode = 1001
    private var currentLocationField: EditText? = null  // Store reference to location EditText

    // Sort options
    private var currentSort = "newest" // newest, oldest, most_liked
    private var currentTimeRange = "all" // today, week, month, all
    private var currentLocationFilter = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find)

        try {
            inboxBadge = findViewById(R.id.inbox_badge)
            InboxBadgeManager.registerBadge(inboxBadge)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 🔥 REMOVE THIS LINE - SessionManager is already initialized in BaseNavigationActivity
        // sessionManager = SessionManager(this)

        // Check if user is logged in
        val currentUser = sessionManager.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Please login to find pets", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize shared preferences for recent searches
        sharedPrefs = getSharedPreferences("find_prefs", MODE_PRIVATE)
        loadRecentSearches()

        try {
            initViews()
            setupSearch()
            setupClickListeners()
            loadPosts()
            highlightChip(chipAll)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.find_recycler_view)
        emptyState = findViewById(R.id.empty_state)
        searchInput = findViewById(R.id.search_input)
        btnFilter = findViewById(R.id.btn_filter)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        chipAll = findViewById(R.id.chip_all)
        chipLost = findViewById(R.id.chip_lost)
        chipFound = findViewById(R.id.chip_found)
        chipAdoption = findViewById(R.id.chip_adoption)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        progressBar = findViewById(R.id.progress_bar)
        tvSearchResults = findViewById(R.id.tv_search_results)
        recentSearchesLayout = findViewById(R.id.recent_searches_layout)
        recentSearchesContainer = findViewById(R.id.recent_searches_container)

        // Setup RecyclerView with 3 columns and spacing
        val gridLayoutManager = GridLayoutManager(this, 3)
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.addItemDecoration(GridSpacingItemDecoration(3, 4, true))

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setColorSchemeColors(
            android.graphics.Color.parseColor("#7A4F2B"),
            android.graphics.Color.parseColor("#FF6B35"),
            android.graphics.Color.parseColor("#4CAF50")
        )
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

        // Hide elements initially
        btnClearSearch.visibility = View.GONE
        tvSearchResults.visibility = View.GONE
        recentSearchesLayout.visibility = View.GONE
    }

    private fun setupSearch() {
        // Search with debounce and real-time results
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""

                // Show/hide clear button
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                // Hide recent searches when typing
                if (query.isNotEmpty()) {
                    recentSearchesLayout.visibility = View.GONE
                }

                // Debounce search (wait 300ms after user stops typing)
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    if (query.isNotEmpty()) {
                        if (query.length >= 2) {
                            performSearch(query)
                        } else {
                            tvSearchResults.visibility = View.GONE
                        }
                    } else {
                        clearSearch()
                        // Show recent searches when search is empty
                        showRecentSearches()
                    }
                }
                searchHandler.postDelayed(searchRunnable!!, 300)
            }
        })
        
        // Keyboard scroll handling for search input
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                searchInput.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        searchInput.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        // Scroll search input to top when focused
                        recyclerView.post {
                            recyclerView.scrollToPosition(0)
                        }
                    }
                })
            }
        }

        // Handle search action from keyboard
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString().trim()
                if (query.length >= 2) {
                    performSearch(query)
                } else {
                    Toast.makeText(this, "Enter at least 2 characters", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }

        // Show recent searches when search input is focused and empty
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && searchInput.text.toString().trim().isEmpty()) {
                showRecentSearches()
            } else {
                recentSearchesLayout.visibility = View.GONE
            }
        }

        // Clear search button
        btnClearSearch.setOnClickListener {
            searchInput.text.clear()
            clearSearch()
            showRecentSearches()
        }
    }

    private fun showRecentSearches() {
        if (recentSearches.isNotEmpty()) {
            recentSearchesContainer.removeAllViews()

            for (query in recentSearches) {
                val itemView = layoutInflater.inflate(R.layout.item_recent_search, recentSearchesContainer, false)
                val tvQuery = itemView.findViewById<TextView>(R.id.tv_query)
                val btnRemove = itemView.findViewById<ImageView>(R.id.btn_remove)

                tvQuery.text = query

                tvQuery.setOnClickListener {
                    searchInput.setText(query)
                    searchInput.setSelection(query.length)
                    performSearch(query)
                    recentSearchesLayout.visibility = View.GONE
                }

                btnRemove.setOnClickListener {
                    recentSearches.remove(query)
                    saveRecentSearches()
                    showRecentSearches()
                }

                recentSearchesContainer.addView(itemView)
            }

            recentSearchesLayout.visibility = View.VISIBLE
        } else {
            recentSearchesLayout.visibility = View.GONE
        }
    }

    private fun saveSearch(query: String) {
        if (query.isNotEmpty() && !recentSearches.contains(query)) {
            recentSearches.add(0, query)
            if (recentSearches.size > 5) {
                recentSearches.removeAt(recentSearches.size - 1)
            }
            saveRecentSearches()
        }
    }

    private fun loadRecentSearches() {
        val saved = sharedPrefs.getStringSet("recent_searches", emptySet())
        recentSearches.clear()
        recentSearches.addAll(saved?.toList() ?: emptyList())
    }

    private fun saveRecentSearches() {
        sharedPrefs.edit()
            .putStringSet("recent_searches", recentSearches.toSet())
            .apply()
    }

    private fun setupClickListeners() {
        btnFilter.setOnClickListener {
            showFilterDialog()
        }

        chipAll.setOnClickListener {
            if (currentFilter != "All") {
                currentFilter = "All"
                highlightChip(chipAll)
                applyFiltersAndSort()
            }
        }

        chipLost.setOnClickListener {
            if (currentFilter != "Lost") {
                currentFilter = "Lost"
                highlightChip(chipLost)
                applyFiltersAndSort()
            }
        }

        chipFound.setOnClickListener {
            if (currentFilter != "Found") {
                currentFilter = "Found"
                highlightChip(chipFound)
                applyFiltersAndSort()
            }
        }

        chipAdoption.setOnClickListener {
            if (currentFilter != "Adoption") {
                currentFilter = "Adoption"
                highlightChip(chipAdoption)
                applyFiltersAndSort()
            }
        }

        // GPS Floating Action Button
        val fabGpsMap = findViewById<View>(R.id.fab_gps_map)
        fabGpsMap.setOnClickListener {
            val intent = Intent(this, NearbyPetsMapActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_filter, null)

        val rgSortBy = dialogView.findViewById<RadioGroup>(R.id.rg_sort_by)
        val rgTimeRange = dialogView.findViewById<RadioGroup>(R.id.rg_time_range)
        val etLocation = dialogView.findViewById<EditText>(R.id.et_location)
        val btnApply = dialogView.findViewById<Button>(R.id.btn_apply_filter)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_filter)

        // Check if views exist before using them
        if (rgSortBy == null || rgTimeRange == null || etLocation == null) {
            Toast.makeText(this, "Error loading filter dialog", Toast.LENGTH_SHORT).show()
            return
        }

        // Set current values
        when (currentSort) {
            "newest" -> rgSortBy.check(R.id.rb_newest)
            "oldest" -> rgSortBy.check(R.id.rb_oldest)
            "most_liked" -> rgSortBy.check(R.id.rb_most_liked)
        }

        when (currentTimeRange) {
            "today" -> rgTimeRange.check(R.id.rb_today)
            "week" -> rgTimeRange.check(R.id.rb_week)
            "month" -> rgTimeRange.check(R.id.rb_month)
            "all" -> rgTimeRange.check(R.id.rb_all)
        }

        etLocation.setText(currentLocationFilter)

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Set up apply button
        btnApply?.setOnClickListener {
            // Get sort option
            currentSort = when (rgSortBy.checkedRadioButtonId) {
                R.id.rb_newest -> "newest"
                R.id.rb_oldest -> "oldest"
                R.id.rb_most_liked -> "most_liked"
                else -> "newest"
            }

            // Get time range
            currentTimeRange = when (rgTimeRange.checkedRadioButtonId) {
                R.id.rb_today -> "today"
                R.id.rb_week -> "week"
                R.id.rb_month -> "month"
                R.id.rb_all -> "all"
                else -> "all"
            }

            currentLocationFilter = etLocation.text.toString()

            applyFiltersAndSort()
            dialog.dismiss()
        }

        // Set up cancel button - make sure text is visible
        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun checkLocationPermissionAndSearch(etLocation: EditText) {
        currentLocationField = etLocation  // Store reference
        if (PermissionHelper.hasLocationPermission(this)) {
            getCurrentLocation()
        } else {
            PermissionHelper.requestLocationPermission(this)
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            PermissionHelper.requestLocationPermission(this)
            return
        }

        progressBar.visibility = View.VISIBLE
        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()

        // Request real-time location updates instead of just lastLocation
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000 // 1 second
            fastestInterval = 500
            numUpdates = 1 // Just get one update
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                progressBar.visibility = View.GONE

                val location = locationResult.lastLocation
                if (location != null) {
                    currentLocation = location
                    // Update location field with coordinates
                    currentLocationField?.setText(String.format("%.4f, %.4f", location.latitude, location.longitude))
                    Toast.makeText(this@FindActivity, "Location updated: ${currentLocationField?.text}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@FindActivity, "Could not get location", Toast.LENGTH_SHORT).show()
                }

                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyFiltersAndSort() {
        if (searchQuery.isNotEmpty()) {
            performSearch(searchQuery)
        } else {
            filterPosts()
            sortPosts()
            updateDisplay()
        }
    }

    private fun sortPosts() {
        filteredPosts = when (currentSort) {
            "oldest" -> filteredPosts.sortedBy { it.createdAt }
            "most_liked" -> filteredPosts.sortedByDescending { it.likesCount }
            else -> filteredPosts.sortedByDescending { it.createdAt } // newest
        }
    }

    private fun filterPosts() {
        // Filter by status
        filteredPosts = when (currentFilter) {
            "All" -> allPosts
            else -> allPosts.filter { it.status.equals(currentFilter, ignoreCase = true) }
        }

        // Filter by time range
        val now = Date()
        val calendar = Calendar.getInstance()

        filteredPosts = filteredPosts.filter { post ->
            try {
                val postDate = parseDate(post.createdAt) ?: return@filter true

                when (currentTimeRange) {
                    "today" -> {
                        calendar.time = now
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        postDate.after(calendar.time)
                    }
                    "week" -> {
                        calendar.time = now
                        calendar.add(Calendar.DAY_OF_YEAR, -7)
                        postDate.after(calendar.time)
                    }
                    "month" -> {
                        calendar.time = now
                        calendar.add(Calendar.MONTH, -1)
                        postDate.after(calendar.time)
                    }
                    else -> true
                }
            } catch (e: Exception) {
                true
            }
        }

        // Filter by location (proximity-based if user location available)
        if (currentLocationFilter.isNotEmpty() && currentLocation != null) {
            // If we have GPS coordinates, use proximity filtering (5km radius)
            val coordinates = currentLocationFilter.split(",")
            if (coordinates.size == 2) {
                try {
                    val userLat = coordinates[0].trim().toDouble()
                    val userLon = coordinates[1].trim().toDouble()
                    
                    filteredPosts = filteredPosts.filter { post ->
                        // Try to extract coordinates from post location or use proximity fallback
                        isPostNearby(post, userLat, userLon)
                    }
                } catch (e: Exception) {
                    // Fallback to text search if coordinate parsing fails
                    filteredPosts = filteredPosts.filter {
                        it.location?.contains(currentLocationFilter, ignoreCase = true) == true
                    }
                }
            } else {
                // Fallback to text search
                filteredPosts = filteredPosts.filter {
                    it.location?.contains(currentLocationFilter, ignoreCase = true) == true
                }
            }
        } else if (currentLocationFilter.isNotEmpty()) {
            // Text-based location search as fallback
            filteredPosts = filteredPosts.filter {
                it.location?.contains(currentLocationFilter, ignoreCase = true) == true
            }
        }
    }

    /**
     * Check if a post is within 5km of user location
     * Uses Haversine formula for accurate distance calculation
     */
    private fun isPostNearby(post: ApiPost, userLat: Double, userLon: Double): Boolean {
        // Try to extract coordinates from post location string
        // Format could be: "14.5995, 120.9842" or just a city name
        val postCoordinates = post.location?.split(",")
        
        if (postCoordinates?.size == 2) {
            try {
                val postLat = postCoordinates[0].trim().toDouble()
                val postLon = postCoordinates[1].trim().toDouble()
                
                // Calculate distance using Haversine formula
                val distanceKm = calculateDistance(userLat, userLon, postLat, postLon)
                
                // Show posts within 5km radius
                return distanceKm <= 5.0
            } catch (e: Exception) {
                // If parsing fails, include the post anyway
                return true
            }
        }
        
        // If post location is not in coordinate format, include it
        return true
    }

    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     * Returns distance in kilometers
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.asin(Math.sqrt(a))
        return R * c
    }

    private fun parseDate(dateString: String): Date? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    private fun refreshData() {
        if (searchQuery.isNotEmpty()) {
            performSearch(searchQuery)
        } else {
            loadPosts()
        }
    }

    private fun highlightChip(selectedChip: Button) {
        resetAllChips()
        selectedChip.alpha = 1.0f
    }

    private fun resetAllChips() {
        chipAll.alpha = 0.6f
        chipLost.alpha = 0.6f
        chipFound.alpha = 0.6f
        chipAdoption.alpha = 0.6f
    }

    private fun performSearch(query: String) {
        if (query.length < 2) {
            Toast.makeText(this, "Enter at least 2 characters", Toast.LENGTH_SHORT).show()
            return
        }

        searchQuery = query
        isSearching = true
        progressBar.visibility = View.VISIBLE
        tvSearchResults.visibility = View.VISIBLE
        tvSearchResults.text = "Searching for \"$query\"..."
        recentSearchesLayout.visibility = View.GONE

        // Save to recent searches
        saveSearch(query)

        lifecycleScope.launch {
            try {
                val status = if (currentFilter != "All") currentFilter else null
                val result = withContext(Dispatchers.IO) {
                    searchRepository.searchPosts(
                        query,
                        status,
                        sessionManager.getCurrentUser()?.firebaseUid,
                        limit = 100
                    )
                }

                if (result.isSuccess) {
                    filteredPosts = result.getOrNull() ?: emptyList()
                    sortPosts()
                    tvSearchResults.text = "Found ${filteredPosts.size} results for \"$query\""

                    if (filteredPosts.isEmpty()) {
                        showEmptyState("No pets found for \"$query\"")
                    } else {
                        updateDisplay()
                    }
                } else {
                    Toast.makeText(this@FindActivity, "Search failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@FindActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                if (swipeRefreshLayout.isRefreshing) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun clearSearch() {
        searchQuery = ""
        isSearching = false
        tvSearchResults.visibility = View.GONE
        filterPosts()
        sortPosts()
        updateDisplay()
    }

    private fun loadPosts() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    postRepository.getPosts(limit = 100, viewerUid = sessionManager.getCurrentUser()?.firebaseUid)
                }

                if (result.isSuccess) {
                    allPosts = result.getOrNull() ?: emptyList()
                    filterPosts()
                    sortPosts()
                    updateDisplay()
                } else {
                    Toast.makeText(this@FindActivity, "Failed to load posts", Toast.LENGTH_SHORT).show()
                    showEmptyState("No pets found")
                }
            } catch (e: Exception) {
                Toast.makeText(this@FindActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showEmptyState("Error loading posts")
            } finally {
                progressBar.visibility = View.GONE
                if (swipeRefreshLayout.isRefreshing) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun showEmptyState(message: String) {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        val emptyText = emptyState.findViewById<TextView>(R.id.empty_text)
        emptyText.text = message
    }

    private fun updateDisplay() {
        if (filteredPosts.isEmpty()) {
            showEmptyState(if (isSearching) "No pets found for \"$searchQuery\"" else "No pets found")
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE

            // 🔥 APPLY POST FILTERING UTILITY - Filter by hidden posts AND blocked users
            lifecycleScope.launch {
                try {
                    val currentUser = sessionManager.getCurrentUser()
                    if (currentUser != null) {
                        val filteredByUserPrefs = com.example.pawsociety.util.PostFilteringUtil.filterPosts(
                            filteredPosts,
                            currentUser.firebaseUid
                        )
                        
                        val adapter = FindAdapter(filteredByUserPrefs) { post ->
                            val intent = Intent(this@FindActivity, PostDetailsActivity::class.java)
                            intent.putExtra("post", post)
                            startActivity(intent)
                        }
                        recyclerView.adapter = adapter
                        
                        android.util.Log.d("FindActivity", "Displayed ${filteredByUserPrefs.size} posts after filtering")
                    } else {
                        // Fallback if no current user
                        val adapter = FindAdapter(filteredPosts) { post ->
                            val intent = Intent(this@FindActivity, PostDetailsActivity::class.java)
                            intent.putExtra("post", post)
                            startActivity(intent)
                        }
                        recyclerView.adapter = adapter
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FindActivity", "Error filtering posts: ${e.message}", e)
                    // Fallback: display without advanced filtering
                    val adapter = FindAdapter(filteredPosts) { post ->
                        val intent = Intent(this@FindActivity, PostDetailsActivity::class.java)
                        intent.putExtra("post", post)
                        startActivity(intent)
                    }
                    recyclerView.adapter = adapter
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission required for nearby search", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isSearching) {
            loadPosts()
        }
    }
}
