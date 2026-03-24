# Android PawSociety Post Loading Analysis

## Overview
This document details how posts are loaded, displayed, and filtered across the three main activities in the PawSociety Android app. Medium thoroughness analysis includes code snippets and architectural patterns.

---

## 1. HomeActivity.kt - Post Loading & Display

### Post Loading Mechanism
Posts are loaded through **HomeViewModel** via **MutableLiveData** observer pattern:

```kotlin
// HomeActivity.kt - setupObservers() method
viewModel.posts.observe(this, Observer { posts ->
    Log.d("HomeActivity", "📊 Received ${posts.size} posts")
    
    // Filter out hidden posts
    val visiblePosts = if (hiddenPostIds.isNotEmpty()) {
        posts.filter { post ->
            post.postId != null && !hiddenPostIds.contains(post.postId)
        }
    } else {
        posts
    }
    
    // Apply POST FILTERING UTILITY - filters by hidden posts AND blocked users
    lifecycleScope.launch {
        val filteredPosts = com.example.pawsociety.util.PostFilteringUtil.filterPosts(
            visiblePosts,
            currentUserUid
        )
        
        // Sort posts - NEWEST FIRST
        val sortedPosts = filteredPosts.sortedByDescending { it.createdAt }
        
        // Create views for each post
        for (post in sortedPosts) {
            createPostView(post, currentLikeMap, currentFavMap)
        }
    }
})
```

### Display Method
**No RecyclerView adapter used.** HomeActivity uses a **LinearLayout with programmatically created views**:

```kotlin
// HomeActivity.kt
private lateinit var postsContainer: LinearLayout  // Main posts container

// Posts are added/removed programmatically:
postsContainer.removeAllViews()  // Clear all posts

for (post in sortedPosts) {
    val postView = inflater.inflate(R.layout.item_post, postsContainer, false)
    postView.tag = post.postId
    
    // Configure all UI elements
    // ... (see createPostView method)
    
    postsContainer.addView(postView)
}
```

### Key Method: createPostView()
Responsible for inflating and configuring individual post layouts:

```kotlin
private fun createPostView(
    post: ApiPost,
    likeStatusMap: Map<String, Boolean>,
    favoriteStatusMap: Map<String, Boolean>
) {
    val inflater = LayoutInflater.from(this)
    val postView = inflater.inflate(R.layout.item_post, postsContainer, false)
    postView.tag = post.postId
    
    // Find views with null safety
    val userNameText = postView.findViewById<TextView>(R.id.post_user_name)
    val petNameText = postView.findViewById<TextView>(R.id.post_pet_name)
    val petTypeText = postView.findViewById<TextView>(R.id.post_pet_type)
    val statusBadge = postView.findViewById<TextView>(R.id.post_status)
    val categoryBadge = postView.findViewById<TextView>(R.id.post_category_badge)
    val viewPager = postView.findViewById<ViewPager2>(R.id.post_view_pager)
    val likeButton = postView.findViewById<com.example.pawsociety.widget.LikeButton>(R.id.btn_like_lottie_view)
    val tvLikeCount = postView.findViewById<TextView>(R.id.tv_like_count)
    
    // Set basic data
    userNameText?.text = post.userName ?: "Unknown"
    petNameText?.text = post.petName ?: "Unnamed Pet"
    petTypeText?.text = post.petType ?: "Unknown breed"
    tvLikeCount?.text = post.likesCount.toString()
    
    // Set status badge
    statusBadge?.text = post.status.uppercase()
    when (post.status.lowercase()) {
        "lost" -> statusBadge?.setBackgroundColor(Color.parseColor("#F44336"))
        "found" -> statusBadge?.setBackgroundColor(Color.parseColor("#4CAF50"))
        "adoption" -> statusBadge?.setBackgroundColor(Color.parseColor("#2196F3"))
    }
    
    // Setup image carousel with ViewPager2
    if (post.imageUrls.isNotEmpty()) {
        viewPager?.adapter = ImageCarouselAdapter(post.imageUrls)
        // Setup page indicators
    }
    
    // Set like button state
    val isLiked = likeStatusMap[post.postId] ?: false
    likeButton?.setLiked(isLiked, animate = false)
    
    postsContainer.addView(postView)
}
```

### Image Display
Uses **ViewPager2** with **ImageCarouselAdapter**:

```kotlin
// ImageCarouselAdapter - handles image carousel
class ImageCarouselAdapter(
    private val imageList: List<Int>
) : RecyclerView.Adapter<ImageCarouselAdapter.ImageViewHolder>() {
    
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageResId = imageList[position]
        Glide.with(holder.itemView.context)
            .load(imageResId)
            .centerCrop()
            .into(holder.imageView)
    }
}
```

### Category Filtering
HomeActivity implements **category-based filtering** with 5 categories: All, Dogs, Cats, Fish, Birds

```kotlin
// HomeActivity.kt - setupCategoryClickListeners()
layoutDogs.setOnClickListener {
    currentCategory = "Dogs"
    highlightCategory("Dogs")
    viewModel.forceRefreshAndFilter("Dogs")  // Triggers filtered post load
}

// HomeViewModel applies the filter
fun forceRefreshAndFilter(category: String) {
    filterPostsByCategory(category)
    loadPosts()
}
```

### Refresh Mechanism
- **SwipeRefreshLayout** - Pull to refresh
- **Socket listeners** - Real-time updates via `SocketManager`
- **Multiple refreshes on post creation** - Delayed refresh handlers to ensure new posts appear

---

## 2. FindActivity.kt - Post Loading & Display

### Post Loading Mechanism
Uses **PostRepository** with Kotlin Coroutines, supports search and filtering:

```kotlin
// FindActivity.kt - loadPosts()
private fun loadPosts() {
    progressBar.visibility = View.VISIBLE
    lifecycleScope.launch {
        try {
            val result = withContext(Dispatchers.IO) {
                postRepository.getPosts(limit = 100)
            }
            
            if (result.isSuccess) {
                allPosts = result.getOrNull() ?: emptyList()
                filterPosts()      // Apply initial filter
                sortPosts()        // Apply sort
                updateDisplay()    // Update UI with adapter
            }
        } finally {
            progressBar.visibility = View.GONE
        }
    }
}
```

### Display Method: RecyclerView with FindAdapter

```kotlin
// FindActivity.kt - updateDisplay() method
private fun updateDisplay() {
    if (filteredPosts.isEmpty()) {
        showEmptyState("No pets found")
    } else {
        recyclerView.visibility = View.VISIBLE
        
        // Apply post filtering utility
        lifecycleScope.launch {
            val currentUser = sessionManager.getCurrentUser()
            if (currentUser != null) {
                val filteredByUserPrefs = PostFilteringUtil.filterPosts(
                    filteredPosts,
                    currentUser.firebaseUid
                )
                
                // Create and attach adapter
                val adapter = FindAdapter(filteredByUserPrefs) { post ->
                    val intent = Intent(this@FindActivity, PostDetailsActivity::class.java)
                    intent.putExtra("post", post)
                    startActivity(intent)
                }
                recyclerView.adapter = adapter
            }
        }
    }
}
```

### Adapter Details: FindAdapter

```kotlin
// FindAdapter.kt
class FindAdapter(
    private val posts: List<ApiPost>,
    private val onItemClick: (ApiPost) -> Unit
) : RecyclerView.Adapter<FindAdapter.FindViewHolder>() {
    
    class FindViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: FrameLayout = itemView.findViewById(R.id.post_container)
        val postImage: ImageView = itemView.findViewById(R.id.post_image)
        val petName: TextView = itemView.findViewById(R.id.pet_name)
        val statusBadge: TextView = itemView.findViewById(R.id.status_badge)
        val categoryBadge: TextView = itemView.findViewById(R.id.category_badge)
        
        init {
            // Force square container
            container.post {
                val width = container.width
                if (width > 0) {
                    val layoutParams = container.layoutParams
                    layoutParams.height = width  // Square aspect ratio
                    container.layoutParams = layoutParams
                }
            }
        }
    }
    
    override fun onBindViewHolder(holder: FindViewHolder, position: Int) {
        val post = posts[position]
        
        holder.petName.text = post.petName
        
        // Set status badge with color-coding
        when (post.status.lowercase()) {
            "lost" -> {
                holder.statusBadge.text = "LOST"
                holder.statusBadge.background.setTint(Color.parseColor("#F44336"))  // Red
            }
            "found" -> {
                holder.statusBadge.text = "FOUND"
                holder.statusBadge.background.setTint(Color.parseColor("#4CAF50"))  // Green
            }
            "adoption" -> {
                holder.statusBadge.text = "ADOPTION"
                holder.statusBadge.background.setTint(Color.parseColor("#2196F3"))  // Blue
            }
        }
        
        // Set category badge (DOG/CAT/FISH/BIRD)
        var category = detectCategory(post)
        holder.categoryBadge.text = category
        
        // Load image
        Glide.with(holder.itemView.context)
            .load(post.imageUrls.firstOrNull())
            .placeholder(R.drawable.ic_launcher_foreground)
            .into(holder.postImage)
        
        holder.itemView.setOnClickListener {
            onItemClick(post)
        }
    }
    
    private fun detectCategory(post: ApiPost): String? {
        return when (post.category) {
            "Dogs" -> "DOG"
            "Cats" -> "CAT"
            "Fish" -> "FISH"
            "Birds" -> "BIRD"
            else -> {
                // Fallback: detect from petType
                val petTypeLower = post.petType.lowercase()
                when {
                    petTypeLower.contains("dog") || petTypeLower.contains("aspin") -> "DOG"
                    petTypeLower.contains("cat") || petTypeLower.contains("puspin") -> "CAT"
                    petTypeLower.contains("fish") -> "FISH"
                    petTypeLower.contains("bird") -> "BIRD"
                    else -> null
                }
            }
        }
    }
}
```

### Post Filtering Pattern

```kotlin
// FindActivity.kt - filterPosts()
private fun filterPosts() {
    // Filter by status (Lost, Found, Adoption)
    filteredPosts = when (currentFilter) {
        "All" -> allPosts
        else -> allPosts.filter { it.status.equals(currentFilter, ignoreCase = true) }
    }
    
    // Filter by time range
    val now = Date()
    val calendar = Calendar.getInstance()
    
    filteredPosts = filteredPosts.filter { post ->
        val postDate = parseDate(post.createdAt) ?: return@filter true
        
        when (currentTimeRange) {
            "today" -> {
                calendar.time = now
                calendar.set(Calendar.HOUR_OF_DAY, 0)
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
    }
    
    // Filter by location (substring match)
    if (currentLocationFilter.isNotEmpty()) {
        filteredPosts = filteredPosts.filter {
            it.location?.contains(currentLocationFilter, ignoreCase = true) == true
        }
    }
}

// Sort options
private fun sortPosts() {
    filteredPosts = when (currentSort) {
        "oldest" -> filteredPosts.sortedBy { it.createdAt }
        "most_liked" -> filteredPosts.sortedByDescending { it.likesCount }
        else -> filteredPosts.sortedByDescending { it.createdAt }  // newest (default)
    }
}
```

### Search Implementation

```kotlin
// FindActivity.kt - performSearch()
private fun performSearch(query: String) {
    searchQuery = query
    lifecycleScope.launch {
        try {
            val status = if (currentFilter != "All") currentFilter else null
            val result = withContext(Dispatchers.IO) {
                searchRepository.searchPosts(query, status, limit = 100)
            }
            
            if (result.isSuccess) {
                filteredPosts = result.getOrNull() ?: emptyList()
                sortPosts()
                tvSearchResults.text = "Found ${filteredPosts.size} results"
                updateDisplay()
            }
        } catch (e: Exception) {
            Toast.makeText(this@FindActivity, "Search failed", Toast.LENGTH_SHORT).show()
        }
    }
}
```

### Layout Structure
- **GridLayoutManager** with 3 columns
- **GridSpacingItemDecoration** for consistent spacing (4dp)
- Square-shaped post items (aspect ratio 1:1)

---

## 3. UserProfileActivity.kt - Post Loading & Display

### Post Loading Mechanism

```kotlin
// UserProfileActivity.kt - loadUserPosts()
private fun loadUserPosts(userId: String) {
    lifecycleScope.launch {
        try {
            val result = postRepository.getPosts(firebaseUid = userId)
            
            if (result.isSuccess) {
                var posts = result.getOrNull()!!
                
                // Apply post filtering utility
                try {
                    val currentUserUid = currentUser?.firebaseUid
                    if (currentUserUid != null) {
                        posts = PostFilteringUtil.filterPosts(
                            posts,
                            currentUserUid
                        )
                    }
                } catch (e: Exception) {
                    // Continue with unfiltered posts as fallback
                }
                
                if (posts.isNotEmpty()) {
                    createPostsGrid(posts)
                    tvPostCount.text = posts.size.toString()
                } else {
                    showEmptyState()
                }
            }
        } catch (e: Exception) {
            showEmptyState()
        }
    }
}
```

### Display Method: Programmatically Created Grid

```kotlin
// UserProfileActivity.kt - createPostsGrid()
private fun createPostsGrid(posts: List<ApiPost>) {
    postsGrid.removeAllViews()
    
    val displayMetrics = resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels
    val spacing = 2
    val columns = 3
    val itemSize = (screenWidth - (spacing * (columns - 1))) / columns
    
    val rows = (posts.size + columns - 1) / columns
    var postIndex = 0
    
    for (row in 0 until rows) {
        val rowLayout = LinearLayout(this)
        rowLayout.orientation = LinearLayout.HORIZONTAL
        rowLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        
        for (col in 0 until columns) {
            if (postIndex >= posts.size) {
                // Add empty placeholder
                val emptyContainer = FrameLayout(this)
                rowLayout.addView(emptyContainer)
                continue
            }
            
            val post = posts[postIndex++]
            val postView = layoutInflater.inflate(R.layout.item_profile_post, null)
            
            val postImage = postView.findViewById<ImageView>(R.id.post_image)
            val categoryBadge = postView.findViewById<TextView>(R.id.category_badge)
            val statusBadge = postView.findViewById<TextView>(R.id.status_badge)
            
            // Detect and set category
            var category = when (post.category) {
                "Dogs" -> "DOG"
                "Cats" -> "CAT"
                "Fish" -> "FISH"
                "Birds" -> "BIRD"
                else -> null
            }
            
            if (category != null && categoryBadge != null) {
                categoryBadge.text = category
                categoryBadge.visibility = View.VISIBLE
            }
            
            // Set status badge
            if (statusBadge != null) {
                statusBadge.text = post.status.uppercase()
            }
            
            // Load image with Glide
            Glide.with(this)
                .load(post.imageUrls.firstOrNull())
                .centerCrop()
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(postImage)
            
            rowLayout.addView(postView)
        }
        
        postsGrid.addView(rowLayout)
    }
}
```

### Grid Layout Structure
- **3-column grid** (programmatically created with LinearLayout rows)
- **Square items** (itemSize = screenWidth / 3)
- **2dp spacing** between items
- Used in **HorizontalScrollView** for profile layout

---

## 4. Unified Post Filtering Pattern - PostFilteringUtil.kt

All three activities use **PostFilteringUtil** for consistent filtering:

```kotlin
// PostFilteringUtil.kt
object PostFilteringUtil {
    
    /**
     * Main filtering method - removes hidden posts and posts from blocked users
     */
    suspend fun filterPosts(
        posts: List<ApiPost>,
        currentUserUid: String
    ): List<ApiPost> = coroutineScope {
        try {
            // Fetch data in parallel
            val hiddenPostsDeferred = async {
                HidePostRepository().getHiddenPostIds(currentUserUid).getOrNull() ?: emptySet()
            }
            val blockedUsersDeferred = async {
                BlockRepository().getBlockedUserIds(currentUserUid).getOrNull() ?: emptySet()
            }
            
            val hiddenPostIds = hiddenPostsDeferred.await()
            val blockedUserIds = blockedUsersDeferred.await()
            
            // Filter: Keep if NOT hidden AND NOT from blocked user
            posts.filter { post ->
                post.postId !in hiddenPostIds && post.firebaseUid !in blockedUserIds
            }
        } catch (e: Exception) {
            // Return unfiltered on error
            posts
        }
    }
}
```

### Filtering Locations
- **HomeActivity**: After post load, before display
- **FindActivity**: In `updateDisplay()` before adapter attachment
- **UserProfileActivity**: After fetching user's posts, before grid creation

---

## 5. Key Filtering Patterns Summary

### Pattern 1: Category-Based Filtering (HomeActivity Only)
```kotlin
currentCategory = "Dogs"
viewModel.forceRefreshAndFilter("Dogs")  // Filters by post.category or post.petType
```

### Pattern 2: Status-Based Filtering (FindActivity Only)
```kotlin
currentFilter = "Lost"  // Filters: All, Lost, Found, Adoption
filteredPosts = posts.filter { it.status.equals(currentFilter, ignoreCase = true) }
```

### Pattern 3: Time Range Filtering (FindActivity Only)
```kotlin
currentTimeRange = "week"  // Options: today, week, month, all
// Filters posts by createdAt timestamp
```

### Pattern 4: Location Filtering (FindActivity Only)
```kotlin
currentLocationFilter = "Manila"
filteredPosts = filteredPosts.filter { 
    it.location?.contains(currentLocationFilter, ignoreCase = true) == true 
}
```

### Pattern 5: Search Filtering (FindActivity Only)
```kotlin
searchRepository.searchPosts(query, status, limit = 100)
// Performs server-side search with optional status filter
```

### Pattern 6: User Preference Filtering (All Activities)
```kotlin
val filteredPosts = PostFilteringUtil.filterPosts(posts, currentUserUid)
// Removes: hidden posts + posts from blocked users
```

### Pattern 7: Sorting Options (FindActivity)
```kotlin
// Sort options: newest (default), oldest, most_liked
filteredPosts = when (currentSort) {
    "oldest" -> filteredPosts.sortedBy { it.createdAt }
    "most_liked" -> filteredPosts.sortedByDescending { it.likesCount }
    else -> filteredPosts.sortedByDescending { it.createdAt }
}
```

---

## 6. Architecture Comparison

| Aspect | HomeActivity | FindActivity | UserProfileActivity |
|--------|------|------|---------|
| **Post Loading** | PostRepository via HomeViewModel | PostRepository directly | PostRepository directly |
| **Display Method** | LinearLayout + programmatic views | RecyclerView + FindAdapter | LinearLayout (3-col grid) |
| **Pagination** | No pagination | Limit: 100 posts | Limit: all user posts |
| **Category Filter** | ✅ 5 categories (All, Dogs, Cats, Fish, Birds) | ❌ | ❌ |
| **Status Filter** | ✅ (via category) | ✅ (Lost, Found, Adoption) | ❌ |
| **Time Range** | ❌ | ✅ (today, week, month, all) | ❌ |
| **Location Filter** | ❌ | ✅ (substring match + GPS) | ❌ |
| **Search** | ❌ | ✅ (SearchRepository) | ❌ |
| **Sort Options** | ✅ (newest first) | ✅ (newest, oldest, most_liked) | ❌ |
| **Image Display** | ViewPager2 + ImageCarouselAdapter | Single image per grid item | Single image per grid item |
| **Real-time Updates** | ✅ (Socket listeners) | ❌ | ❌ |
| **User Filtering** | ✅ (PostFilteringUtil) | ✅ (PostFilteringUtil) | ✅ (PostFilteringUtil) |

---

## 7. Data Models

### ApiPost (Core Post Model)
```kotlin
data class ApiPost(
    val postId: String,           // Unique post ID
    val firebaseUid: String,       // Post creator's Firebase UID
    val userName: String,         // Creator's username
    val userImageUrl: String,     // Creator's profile image URL
    val petName: String,          // Pet's name
    val petType: String,          // Breed/type (e.g., "Aspin", "Puspin")
    val category: String,         // Category (Dogs, Cats, Fish, Birds)
    val age: String,              // Pet's age
    val weight: String,           // Pet's weight
    val gender: String,           // Pet's gender
    val status: String,           // Post status (Lost, Found, Adoption)
    val description: String,      // Post description
    val location: String,         // Location (for search/filter)
    val reward: String,           // Reward amount (if applicable)
    val contactInfo: String,      // Contact phone/email
    val imageUrls: List<String>,  // Post images
    val likesCount: Int,          // Number of likes
    val createdAt: String         // Creation timestamp (ISO format)
)
```

---

## 8. Summary of Key Insights

1. **No Unified Adapter Pattern**: HomeActivity and UserProfileActivity bypass RecyclerView entirely and create views programmatically
2. **Filtering is Universal**: PostFilteringUtil ensures consistent hidden post/blocked user filtering across all activities
3. **FindActivity Most Feature-Rich**: Only activity with comprehensive search, sorting, and time-range filtering
4. **Real-time Updates**: HomeActivity uses WebSocket (SocketManager) for live post updates
5. **Image Handling**: 
   - HomeActivity uses ViewPager2 for image carousels
   - FindActivity and UserProfileActivity use single images with grid layout
6. **Category Detection**: Intelligent fallback system that detects category from `post.category` or `post.petType`
7. **Performance**: Uses Glide for image loading with caching and coroutines for async operations
