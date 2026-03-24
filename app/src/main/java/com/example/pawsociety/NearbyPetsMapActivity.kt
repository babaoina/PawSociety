package com.example.pawsociety

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.Toast
import android.widget.TextView
import android.widget.Button
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.data.repository.FavoriteRepository
import com.example.pawsociety.util.PermissionHelper
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.example.pawsociety.ChatActivity
import android.app.AlertDialog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.Locale

class NearbyPetsMapActivity : BaseNavigationActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val postRepository = PostRepository()
    private val favoriteRepository = FavoriteRepository()

    private var currentLocation: Location? = null
    private val locationRequestCode = 1002
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    
    // Store post data by marker ID for when marker is clicked
    private val markerToPostMap = mutableMapOf<String, ApiPost>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nearby_pets_map)

        android.util.Log.d("MapDebug", "NearbyPetsMapActivity onCreate started")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up back button
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            onBackPressed()
        }

        try {
            // Get the SupportMapFragment and request to be notified when the map is ready
            val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as? SupportMapFragment
            
            if (mapFragment != null) {
                android.util.Log.d("MapDebug", "MapFragment found, calling getMapAsync")
                mapFragment.getMapAsync(this)
            } else {
                android.util.Log.e("MapDebug", "MapFragment is null!")
                Toast.makeText(this, "Error: Map fragment not found", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MapDebug", "Error getting map fragment: ${e.message}", e)
            Toast.makeText(this, "Error loading map: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Get user location first
        requestUserLocation()
    }

    override fun onResume() {
        super.onResume()
        // Refresh posts when returning to map from other activities
        android.util.Log.d("MapDebug", "onResume called - refreshing posts")
        showNearbyPostsOnMap()
    }

    /**
     * Request user's current location with HIGH_ACCURACY
     */
    private fun requestUserLocation() {
        if (PermissionHelper.hasLocationPermission(this)) {
            startLocationUpdates()
        } else {
            PermissionHelper.requestLocationPermission(this)
        }
    }

    /**
     * Start getting real-time location updates
     */
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000  // 1 second
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1  // Only get one update
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        currentLocation = location
                        userLatitude = location.latitude
                        userLongitude = location.longitude

                        // Once we have location, show nearby posts on map
                        showNearbyPostsOnMap()
                    }
                }
            },
            null
        )
    }

    /**
     * Manipulates the map once it's available
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        android.util.Log.d("MapDebug", "✓ onMapReady called - map object created")
        
        // Add success callback for when map loads
        mMap.setOnMapLoadedCallback {
            android.util.Log.d("MapDebug", "✓ Map tiles loaded successfully!")
            Toast.makeText(this, "Map loaded!", Toast.LENGTH_SHORT).show()
        }
        
        // Apply map settings BEFORE moving camera
        mMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = true
        }
        
        // Set map type to NORMAL for standard Google Maps
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        
        android.util.Log.d("MapDebug", "Map UI settings configured")
        
        // Move camera to Pangasinan (default location)
        val defaultLocation = LatLng(15.75, 120.5)  // Pangasinan center
        try {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))
            android.util.Log.d("MapDebug", "✓ Camera positioned at Pangasinan: $defaultLocation")
        } catch (e: Exception) {
            android.util.Log.e("MapDebug", "Error moving camera: ${e.message}")
        }

        // Enable location if permission is granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                mMap.isMyLocationEnabled = true
                android.util.Log.d("MapDebug", "✓ My location enabled")
            } catch (e: Exception) {
                android.util.Log.e("MapDebug", "Error enabling my location: ${e.message}")
            }
        } else {
            android.util.Log.w("MapDebug", "⚠ Location permission not granted")
        }
        
        // Add marker click listener to show post details
        mMap.setOnMarkerClickListener { marker ->
            val post = markerToPostMap[marker.id]
            if (post != null) {
                // Zoom in on the marker location
                if (post.latitude != null && post.longitude != null) {
                    mMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(post.latitude!!, post.longitude!!),
                            16f  // Zoom level
                        ),
                        500,  // Animation duration in ms
                        null
                    )
                }
                // Show details after a short delay to let zoom animation play
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showPostDetailsBottomSheet(post)
                }, 300)
                true  // Return true to consume the event
            } else {
                false
            }
        }
        
        android.util.Log.d("MapDebug", "✓ onMapReady initialization complete")
    }

    /**
     * Fetch all pet posts and display them on the map using actual GPS coordinates
     */
    private fun showNearbyPostsOnMap() {
        lifecycleScope.launch {
            try {
                // Fetch all posts from repository
                val allPosts = withContext(Dispatchers.IO) {
                    postRepository.getPosts(
                        limit = 100,
                        viewerUid = sessionManager.getCurrentUser()?.firebaseUid
                    ).getOrNull() ?: emptyList()
                }

                withContext(Dispatchers.Main) {
                    if (!::mMap.isInitialized) {
                        return@withContext
                    }

                    mMap.clear()
                    markerToPostMap.clear()

                    // Add user's location marker (only if we have valid coordinates)
                    if (userLatitude != 0.0 && userLongitude != 0.0) {
                        val userMarker = MarkerOptions()
                            .position(LatLng(userLatitude, userLongitude))
                            .title("Your Location")
                            .snippet("You are here")
                        mMap.addMarker(userMarker)

                        android.util.Log.d("MapDebug", "✓ User location marker added at ($userLatitude, $userLongitude)")
                    }

                    // Add markers for ALL posts using latitude/longitude with custom pet image
                    var validPostCount = 0
                    allPosts.forEach { post ->
                        val markerPosition = getMarkerPosition(post)
                        if (markerPosition != null) {
                            try {
                                // Create custom marker with pet image
                                val firstImageUrl = post.imageUrls?.firstOrNull()
                                android.util.Log.d("MapDebug", "Creating marker for ${post.petName}: imageUrls=${post.imageUrls}, firstUrl='$firstImageUrl'")
                                
                                val markerBitmap = createCustomMarkerBitmap(firstImageUrl)
                                val markerOptions = MarkerOptions()
                                    .position(markerPosition)
                                    .title(post.petName)
                                    .snippet("${post.petType} - ${post.status}")
                                    .icon(BitmapDescriptorFactory.fromBitmap(markerBitmap))
                                
                                val marker = mMap.addMarker(markerOptions)
                                if (marker != null) {
                                    markerToPostMap[marker.id] = post
                                }
                                validPostCount++
                                android.util.Log.d("MapDebug", "✓ Added custom marker for ${post.petName} at (${markerPosition.latitude}, ${markerPosition.longitude})")
                            } catch (e: Exception) {
                                android.util.Log.e("MapDebug", "Error adding marker for ${post.petName}: ${e.message}")
                            }
                        } else {
                            android.util.Log.w("MapDebug", "Post ${post.petName} has no usable location for the map")
                        }
                    }

                    // Show count of posts added to map
                    Toast.makeText(
                        this@NearbyPetsMapActivity,
                        "Showing $validPostCount pet posts on map",
                        Toast.LENGTH_LONG
                    ).show()

                    android.util.Log.d("MapDebug", "✓ Added $validPostCount pet posts to map")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@NearbyPetsMapActivity,
                        "Error loading posts: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    android.util.Log.e("MapDebug", "Error loading posts: ${e.message}")
                }
            }
        }
    }

    private suspend fun getMarkerPosition(post: ApiPost): LatLng? {
        val savedLat = post.latitude
        val savedLon = post.longitude
        if (savedLat != null && savedLon != null) {
            if (isWithinPangasinan(savedLat, savedLon)) {
                return LatLng(savedLat, savedLon)
            }
            return null
        }

        val locationQuery = post.eventLocation?.takeIf { it.isNotBlank() && it.contains("Pangasinan", ignoreCase = true) }
            ?: post.location?.takeIf { it.isNotBlank() && it.contains("Pangasinan", ignoreCase = true) }
            ?: return null

        return geocodePostLocation(locationQuery)?.also { resolvedLatLng ->
            persistResolvedCoordinates(post, resolvedLatLng)
        }
    }

    private suspend fun geocodePostLocation(locationQuery: String): LatLng? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(this@NearbyPetsMapActivity, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(locationQuery, 1)
            val firstAddress = addresses?.firstOrNull() ?: return@withContext null
            val latLng = LatLng(firstAddress.latitude, firstAddress.longitude)
            if (isWithinPangasinan(latLng.latitude, latLng.longitude)) latLng else null
        } catch (e: Exception) {
            android.util.Log.e("MapDebug", "Failed to geocode '$locationQuery': ${e.message}")
            null
        }
    }

    private fun persistResolvedCoordinates(post: ApiPost, latLng: LatLng) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updateMap = mapOf<String, Any>(
                    "latitude" to latLng.latitude,
                    "longitude" to latLng.longitude
                )
                postRepository.updatePost(post.postId, updateMap, post.firebaseUid)
            } catch (e: Exception) {
                android.util.Log.e("MapDebug", "Failed to persist coordinates for ${post.postId}: ${e.message}")
            }
        }
    }

    private fun isWithinPangasinan(lat: Double, lon: Double): Boolean {
        return lat in 15.78..16.37 && lon in 119.80..120.89
    }

    /**
     * Handle location permission results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    mMap.isMyLocationEnabled = true
                }
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Create a circular marker bitmap using pawbadge.png drawable
     */
    private fun createCustomMarkerBitmap(imageUrl: String?): Bitmap {
        val markerSize = 120  // 120x120 pixel marker
        val markerBitmap = Bitmap.createBitmap(markerSize, markerSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(markerBitmap)
        
        // 🔥 SIMPLIFIED: Use pawbadge.png instead of fetching from URL
        // This avoids network issues and always shows a consistent marker icon
        android.util.Log.d("MapDebug", "📌 Creating marker with pawbadge.png drawable")
        
        val imageBitmap = try {
            // Load pawbadge.png from drawable resources
            val drawable = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.pawbadge)
            if (drawable != null) {
                // Convert drawable to bitmap, scaled to fit the marker size
                val bitmapFromDrawable = Bitmap.createBitmap(
                    markerSize, markerSize, Bitmap.Config.ARGB_8888
                )
                val canvasFromDrawable = Canvas(bitmapFromDrawable)
                // Scale drawable to fill the entire marker (with some padding for border)
                val padding = 8  // pixels for border padding
                drawable.setBounds(padding, padding, markerSize - padding, markerSize - padding)
                drawable.draw(canvasFromDrawable)
                
                android.util.Log.d("MapDebug", "✅ Loaded pawbadge.png successfully - scaled to ${markerSize - 2 * padding}x${markerSize - 2 * padding}")
                bitmapFromDrawable
            } else {
                android.util.Log.e("MapDebug", "❌ pawbadge.png drawable not found")
                // Fallback: gray circle
                Bitmap.createBitmap(markerSize, markerSize, Bitmap.Config.ARGB_8888).apply {
                    Canvas(this).drawColor(Color.GRAY)
                }
            }
        } catch (e: Exception) {
            // Fallback placeholder
            android.util.Log.e("MapDebug", "❌ Error loading pawbadge.png: ${e.message}")
            Bitmap.createBitmap(markerSize, markerSize, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).drawColor(Color.GRAY)
            }
        }
        
        // Draw white border around the marker
        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        
        // Draw the image on canvas
        canvas.drawBitmap(imageBitmap, 0f, 0f, null)
        
        // Draw white circle border
        canvas.drawCircle(
            markerSize / 2f,
            markerSize / 2f,
            markerSize / 2f - 2,
            borderPaint
        )
        
        return markerBitmap
    }

    /**
     * Show bottom sheet with post details when marker is clicked
     */
    private fun showPostDetailsBottomSheet(post: ApiPost) {
        // Debug: Log complete post data
        android.util.Log.d("MapDebug", """
            📋 POST DATA:
            - postId: ${post.postId}
            - firebaseUid: '${post.firebaseUid}' (empty: ${post.firebaseUid.isNullOrEmpty()})
            - userName: '${post.userName}' 
            - userImageUrl: '${post.userImageUrl}'
            - petName: ${post.petName}
            - imageUrls: ${post.imageUrls}
        """.trimIndent())
        
        // Create a bottom sheet dialog for post details
        val bottomSheetView = layoutInflater.inflate(
            R.layout.bottom_sheet_post_details,
            null
        )

        // Get views from bottom sheet
        val petImageView = bottomSheetView.findViewById<ImageView>(R.id.petImageView)
        val petNameView = bottomSheetView.findViewById<android.widget.TextView>(R.id.petNameView)
        val posterUsernameView = bottomSheetView.findViewById<android.widget.TextView>(R.id.posterUsernameView)
        val petTypeView = bottomSheetView.findViewById<android.widget.TextView>(R.id.petTypeView)
        val petStatusView = bottomSheetView.findViewById<android.widget.TextView>(R.id.petStatusView)
        val petLocationView = bottomSheetView.findViewById<android.widget.TextView>(R.id.petLocationView)
        val petDescriptionView = bottomSheetView.findViewById<android.widget.TextView>(R.id.petDescriptionView)
        
        // Get buttons
        val messageButton = bottomSheetView.findViewById<Button>(R.id.messageButton)
        val saveButton = bottomSheetView.findViewById<Button>(R.id.saveButton)
        
        // Get current user once
        val currentUser = FirebaseAuth.getInstance().currentUser
        
        // Check if post is already saved (for persisting button state)
        if (currentUser != null) {
            // Default to SAVE, will be updated if already saved
            saveButton?.text = "SAVE"
            
            // Check save status asynchronously
            lifecycleScope.launch {
                try {
                    val isPostSaved = withContext(Dispatchers.IO) {
                        val result = favoriteRepository.checkFavorite(post.postId, currentUser.uid)
                        result.getOrNull() ?: false
                    }
                    
                    // Update button text based on save status
                    if (isPostSaved) {
                        saveButton?.text = "UNSAVE"
                        android.util.Log.d("MapDebug", "✓ Post ${post.postId} is already saved")
                    } else {
                        saveButton?.text = "SAVE"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MapDebug", "Error checking save status: ${e.message}")
                    saveButton?.text = "SAVE"
                }
            }
        }

        // Load image with better error handling
        if (!post.imageUrls.isNullOrEmpty()) {
            val imageUrl = post.imageUrls?.get(0)
            // Properly construct full image URL (same as FindAdapter)
            val fullImageUrl = if (!imageUrl.isNullOrEmpty()) {
                if (imageUrl.startsWith("http")) {
                    imageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
                }
            } else {
                null
            }
            
            android.util.Log.d("MapDebug", "Image URL for Glide: $imageUrl -> Full URL: $fullImageUrl")
            
            if (fullImageUrl != null) {
                Glide.with(this)
                    .load(fullImageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .centerCrop()
                    .into(petImageView)
                android.util.Log.d("MapDebug", "✓ Loading image: $fullImageUrl")
            } else {
                android.util.Log.e("MapDebug", "✗ imageUrl is null or empty in imageUrls: ${post.imageUrls}")
                petImageView?.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            android.util.Log.e("MapDebug", "✗ post.imageUrls is null or empty for postId: ${post.postId}")
            petImageView?.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Set post details
        val isOwnPost = currentUser != null && currentUser.uid == post.firebaseUid
        
        petNameView?.text = post.petName
        posterUsernameView?.text = "@${post.userName?.ifBlank { "unknown_user" } ?: "unknown_user"}"
        petTypeView?.text = post.petType
        petStatusView?.text = post.status.uppercase()
        
        // Set status with color coding
        val statusColor = when(post.status) {
            "Lost" -> android.graphics.Color.RED
            "Found" -> android.graphics.Color.GREEN
            "Adoption" -> android.graphics.Color.BLUE
            else -> android.graphics.Color.GRAY
        }
        petStatusView?.setTextColor(statusColor)
        
        // Set location
        petLocationView?.text = post.location
        
        petDescriptionView?.text = post.description

        // Set up button click listeners and visibility
        if (isOwnPost) {
            // Own post - show only Save button
            messageButton?.visibility = View.GONE
            saveButton?.visibility = View.VISIBLE
            // Set initial listener (will be reassigned based on save status)
            saveButton?.setOnClickListener {
                if (saveButton.text == "SAVE") {
                    savePostAsFavorite(post, saveButton)
                } else {
                    removePostAsFavorite(post, saveButton)
                }
            }
            android.util.Log.d("MapDebug", "✓ Own post detected - hiding message button")
        } else {
            // Others' posts - show Message and Save buttons
            messageButton?.visibility = View.VISIBLE
            saveButton?.visibility = View.VISIBLE
            
            messageButton?.setOnClickListener {
                startMessageChat(post)
            }
            
            // Set initial listener (will be reassigned based on save status)
            saveButton?.setOnClickListener {
                if (saveButton.text == "SAVE") {
                    savePostAsFavorite(post, saveButton)
                } else {
                    removePostAsFavorite(post, saveButton)
                }
            }
        }

        // Create and show bottom sheet dialog
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)
        
        // Set height behavior to expand to 50% of screen
        val behavior = BottomSheetBehavior.from(
            bottomSheetView.parent as android.view.View
        )
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.5).toInt()
        
        bottomSheetDialog.show()
        
        android.util.Log.d("MapDebug", "✓ Showing post details for ${post.petName} (own post: $isOwnPost)")
    }

    /**
     * Start a message chat with post owner
     */
    private fun startMessageChat(post: ApiPost) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login to message", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Debug log to check post data
        android.util.Log.d("MapDebug", "Post details - UID: '${post.firebaseUid}' | UserName: '${post.userName}' | PostId: '${post.postId}'")
        
        // Validate post owner UID
        val ownerUid = post.firebaseUid?.trim()
        if (ownerUid.isNullOrEmpty()) {
            Toast.makeText(
                this, 
                "Error: Post owner information not available. Please try again or contact support.",
                Toast.LENGTH_LONG
            ).show()
            android.util.Log.e("MapDebug", "✗ Post owner UID is empty or null. Post ID: ${post.postId}")
            return
        }
        
        // Validate post owner name
        if (post.userName.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Post owner name not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Prevent messaging yourself
        if (ownerUid == currentUser.uid) {
            Toast.makeText(this, "Cannot message yourself", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Navigate to ChatActivity (fix: use correct key for receiverUid)
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("receiverUid", ownerUid)
            putExtra("receiverUsername", post.userName)
            putExtra("userImage", post.userImageUrl ?: "")
        }
        startActivity(intent)
        android.util.Log.d("MapDebug", "✓ Starting chat with ${post.userName} (UID: ${ownerUid})")
    }

    /**
     * Save post as favorite
     */
    private fun savePostAsFavorite(post: ApiPost, saveButton: Button?) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login to save posts", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                // Call favorite endpoint via PostRepository
                // POST /api/favorites with { postId, firebaseUid }
                val result = withContext(Dispatchers.IO) {
                    val response = postRepository.createFavorite(
                        postId = post.postId,
                        firebaseUid = currentUser.uid
                    )
                    response
                }
                android.util.Log.d("MapDebug", "Favorite result: isSuccess=${result.isSuccess}, exception=${result.exceptionOrNull()?.message}")
                if (result.isSuccess) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@NearbyPetsMapActivity,
                            "✓ Post saved to favorites! Check your profile.",
                            Toast.LENGTH_LONG
                        ).show()
                        android.util.Log.d("MapDebug", "✓ Saved ${post.petName} as favorite for user ${currentUser.uid}")
                        
                        // Update button text to "Unsave" and toggle behavior
                        saveButton?.text = "UNSAVE"
                        saveButton?.setOnClickListener {
                            removePostAsFavorite(post, saveButton)
                        }
                        
                        // Trigger favorites refresh - notify FavoritesManager
                        try {
                            com.example.pawsociety.data.FavoritesManager.notifyFavoriteChanged(post.postId)
                            android.util.Log.d("MapDebug", "✓ Notified FavoritesManager of new favorite")
                        } catch (e: Exception) {
                            android.util.Log.e("MapDebug", "Error notifying FavoritesManager: ${e.message}")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@NearbyPetsMapActivity,
                            "Failed to save: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        android.util.Log.e("MapDebug", "✗ Failed to save favorite: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@NearbyPetsMapActivity,
                        "Error saving post: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    android.util.Log.e("MapDebug", "Error saving favorite: ${e.message}")
                }
            }
        }
    }

    /**
     * Report a post
     */
    private fun reportPost(post: ApiPost) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login to report posts", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show report reason dialog
        val reasons = arrayOf(
            "Inappropriate content",
            "Spam",
            "Scam/Fraud",
            "Missing pet already found",
            "Other"
        )
        
        android.app.AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setTitle("Report Post")
            .setItems(reasons) { _, which ->
                val reason = reasons[which]
                submitReport(post, reason, currentUser.uid)
            }
            .show()
    }

    /**
     * Submit report to backend
     */
    private fun submitReport(post: ApiPost, reason: String, userId: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    postRepository.reportPost(
                        postId = post.postId,
                        firebaseUid = userId,
                        reason = reason
                    )
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@NearbyPetsMapActivity,
                        "✓ Report submitted",
                        Toast.LENGTH_SHORT
                    ).show()
                    android.util.Log.d("MapDebug", "✓ Reported post: $reason")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@NearbyPetsMapActivity,
                        "Error submitting report: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    android.util.Log.e("MapDebug", "Error reporting: ${e.message}")
                }
            }
        }
    }

    /**
     * Remove post from favorites (Unsave)
     */
    private fun removePostAsFavorite(post: ApiPost, saveButton: Button?) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login to manage favorites", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Make DELETE request to /api/favorites/{postId}?userUid={uid}
                val result = withContext(Dispatchers.IO) {
                    try {
                        val url = "${com.example.pawsociety.api.ApiClient.BASE_URL}favorites/${post.postId}?userUid=${currentUser.uid}"
                        android.util.Log.d("MapDebug", "Removing favorite - URL: $url")
                        
                        val okHttpClient = okhttp3.OkHttpClient()
                        val request = okhttp3.Request.Builder()
                            .url(url)
                            .delete()
                            .build()
                        
                        val response = okHttpClient.newCall(request).execute()
                        
                        if (response.isSuccessful) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Exception(response.body?.string() ?: "Failed to remove favorite"))
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }

                if (result.isSuccess) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@NearbyPetsMapActivity,
                            "✓ Post removed from favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                        android.util.Log.d("MapDebug", "✓ Unsaved ${post.petName}")

                        // Update button text back to "SAVE" and toggle behavior
                        saveButton?.text = "SAVE"
                        saveButton?.setOnClickListener {
                            savePostAsFavorite(post, saveButton)
                        }

                        // Notify FavoritesManager
                        try {
                            com.example.pawsociety.data.FavoritesManager.notifyFavoriteChanged(post.postId)
                        } catch (e: Exception) {
                            android.util.Log.e("MapDebug", "Error notifying FavoritesManager: ${e.message}")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@NearbyPetsMapActivity,
                            "Failed to remove: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        android.util.Log.e("MapDebug", "✗ Failed to remove favorite: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@NearbyPetsMapActivity,
                        "Error removing from favorites: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    android.util.Log.e("MapDebug", "Error removing favorite: ${e.message}")
                }
            }
        }
    }
}
