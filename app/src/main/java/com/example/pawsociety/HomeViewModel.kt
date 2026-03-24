package com.example.pawsociety

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.FavoritesManager
import com.example.pawsociety.data.repository.*
import com.example.pawsociety.util.PostFilteringUtil
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.util.Log
import kotlin.math.max
import kotlinx.coroutines.delay

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val postRepository = PostRepository()
    private val offlinePostRepository = OfflinePostRepository(context)
    private val favoriteRepository = FavoriteRepository()
    private val userRepository = UserRepository()
    private val followRepository = FollowRepository()
    private val blockRepository = BlockRepository()
    private val chatRepository = ChatRepository()

    private val _posts = MutableLiveData<List<ApiPost>>()
    val posts: LiveData<List<ApiPost>> = _posts

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isOffline = MutableLiveData<Boolean>()
    val isOffline: LiveData<Boolean> = _isOffline

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _currentUser = MutableLiveData<ApiUser?>()
    val currentUser: LiveData<ApiUser?> = _currentUser

    private val _likeStatus = MutableLiveData<Map<String, Boolean>>()
    val likeStatus: LiveData<Map<String, Boolean>> = _likeStatus

    private val _favoriteStatus = MutableLiveData<Map<String, Boolean>>()
    val favoriteStatus: LiveData<Map<String, Boolean>> = _favoriteStatus

    private val _followStatus = MutableLiveData<Map<String, Boolean>>()
    val followStatus: LiveData<Map<String, Boolean>> = _followStatus

    // 🔥 ADD THIS - Unread count for inbox badge
    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private var sessionManager: SessionManager? = null

    // Category Filter Variables
    private val _currentCategory = MutableLiveData<String>("All")
    val currentCategory: LiveData<String> = _currentCategory

    private val _categoryPosts = MutableLiveData<List<ApiPost>>()
    val categoryPosts: LiveData<List<ApiPost>> = _categoryPosts

    // Store all posts (unfiltered)
    private var allPosts = listOf<ApiPost>()

    // 🔥 ADD THIS - Singleton instance for global access
    companion object {
        private var instance: HomeViewModel? = null

        fun refreshInboxCount() {
            instance?.loadInboxCounts()
        }
    }

    init {
        _isLoading.value = false
        _isOffline.value = false
        _likeStatus.value = emptyMap()
        _favoriteStatus.value = emptyMap()
        _followStatus.value = emptyMap()
        _currentCategory.value = "All"
        _unreadCount.value = 0

        instance = this

        viewModelScope.launch {
            try {
                val result = offlinePostRepository.loadPosts(false)
                if (result.isSuccess) {
                    allPosts = result.getOrNull() ?: emptyList()
                    // 🔥 FIXED: Only filter out messages, NOT based on age/weight
                    allPosts = filterOutMessages(allPosts)
                    filterPostsByCategory(_currentCategory.value ?: "All")
                    Log.d("HomeViewModel", "Initial posts loaded: ${allPosts.size}")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading posts", e)
            }
        }
    }

    // 🔥 ADD THIS - Load inbox unread count
    fun loadInboxCounts() {
        viewModelScope.launch {
            try {
                val currentUser = _currentUser.value ?: return@launch
                val result = chatRepository.getConversations(currentUser.firebaseUid)

                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    val messagesUnread = response.messages?.sumOf { it.unreadCount } ?: 0
                    val requestsCount = response.requests?.size ?: 0
                    val totalUnread = messagesUnread + requestsCount

                    _unreadCount.value = totalUnread
                    Log.d("HomeViewModel", "📊 Inbox unread count: $totalUnread")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading inbox counts", e)
            }
        }
    }

    // 🔥 FIXED: Only filter out obvious messages, NEVER filter based on age/weight
    private fun filterOutMessages(posts: List<ApiPost>): List<ApiPost> {
        return posts.filter { post ->
            // ✅ ALWAYS keep posts that have petName (all real posts have this)
            if (post.petName != null && post.petName.isNotEmpty()) {
                Log.d("HomeViewModel", "✅ Keeping post: ${post.petName} - ${post.postId}")
                return@filter true
            }

            // ❌ Only filter out if it's DEFINITELY a message
            val isDefinitelyMessage = post.postId.startsWith("msg_") ||
                    post.petName.equals("message", ignoreCase = true) ||
                    (post.description?.startsWith("New message") == true)

            if (isDefinitelyMessage) {
                Log.w("HomeViewModel", "🚫 Filtered out message: ${post.postId}")
                return@filter false
            }

            // Default to keeping it - DO NOT filter based on age/weight
            Log.d("HomeViewModel", "✅ Keeping post by default: ${post.postId}")
            true
        }
    }

    fun setSessionManager(sessionManager: SessionManager) {
        this.sessionManager = sessionManager
        loadCurrentUser()
        loadPosts()
    }

    fun loadCurrentUser() {
        val cachedUser = sessionManager?.getCurrentUser() ?: return
        _currentUser.value = cachedUser

        viewModelScope.launch {
            val result = userRepository.getUserByUid(cachedUser.firebaseUid)
            if (result.isSuccess) {
                val freshUser = result.getOrNull()!!
                _currentUser.value = freshUser
                sessionManager?.saveUserSession(freshUser)
            }
        }
    }

    fun refreshLikeStatus(postId: String) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch
            val result = postRepository.checkLikeStatus(postId, currentUser.firebaseUid)

            if (result.isSuccess) {
                val response = result.getOrNull()!!
                val isLiked = response.isLiked ?: response.liked ?: false
                val likesCount = response.likesCount

                val currentMap = _likeStatus.value?.toMutableMap() ?: mutableMapOf()
                currentMap[postId] = isLiked
                _likeStatus.value = currentMap

                val currentPosts = _posts.value?.toMutableList() ?: return@launch
                val index = currentPosts.indexOfFirst { it.postId == postId }
                if (index >= 0) {
                    currentPosts[index] = currentPosts[index].copy(likesCount = likesCount)
                    _posts.value = currentPosts
                }

                Log.d("LikeDebug", "Refreshed like status for $postId: $isLiked, count: $likesCount")
            }
        }
    }

    fun loadPostsByCategory(category: String) {
        forceRefreshAndFilter(category)
        Log.d("HomeViewModel", "Loading posts for category: $category")

        if (allPosts.isNotEmpty()) {
            filterPostsByCategory(category)
        } else {
            loadPosts(forceRefresh = true)
        }
    }

    private fun filterPostsByCategory(category: String) {
        Log.d("HomeViewModel", "🔍 FILTERING ${allPosts.size} posts for category: $category")

        allPosts.forEachIndexed { index, post ->
            Log.d("HomeViewModel", "   Post $index: petType='${post.petType}'")
        }

        val filteredPosts = when (category) {
            "All" -> allPosts
            "Dogs" -> allPosts.filter { post ->
                val petType = post.petType.lowercase()
                val isDog = petType.contains("dog") ||
                        PetData.dogBreeds.any { breed ->
                            petType.contains(breed.lowercase())
                        }
                if (isDog) Log.d("HomeViewModel", "🐕 DOG MATCH: ${post.petName} (${post.petType})")
                isDog
            }
            "Cats" -> allPosts.filter { post ->
                val petType = post.petType.lowercase()
                val isCat = petType.contains("cat") ||
                        PetData.catBreeds.any { breed ->
                            petType.contains(breed.lowercase())
                        }
                if (isCat) Log.d("HomeViewModel", "🐱 CAT MATCH: ${post.petName} (${post.petType})")
                isCat
            }
            "Fish" -> allPosts.filter { post ->
                val petType = post.petType.lowercase()
                val isFish = petType.contains("fish") ||
                        PetData.fishBreeds.any { breed ->
                            petType.contains(breed.lowercase())
                        }
                if (isFish) Log.d("HomeViewModel", "🐟 FISH MATCH: ${post.petName} (${post.petType})")
                isFish
            }
            "Birds" -> allPosts.filter { post ->
                val petType = post.petType.lowercase()
                val isBird = petType.contains("bird") ||
                        PetData.birdBreeds.any { breed ->
                            petType.contains(breed.lowercase())
                        }
                if (isBird) Log.d("HomeViewModel", "🐦 BIRD MATCH: ${post.petName} (${post.petType})")
                isBird
            }
            else -> allPosts
        }

        _posts.value = filteredPosts
        _categoryPosts.value = filteredPosts
        Log.d("HomeViewModel", "✅ Filtered posts for $category: ${filteredPosts.size} out of ${allPosts.size}")
    }

    fun loadPosts(forceRefresh: Boolean = false) {
        if (sessionManager == null) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val category = _currentCategory.value ?: "All"

                val result = if (forceRefresh) {
                    postRepository.getPosts(limit = 100, viewerUid = _currentUser.value?.firebaseUid)
                } else {
                    offlinePostRepository.loadPosts(false)
                }

                if (result.isSuccess) {
                    // IMPORTANT: Update allPosts FIRST
                    allPosts = result.getOrNull()!!

                    Log.d("HomeViewModel", "📥 Received ${allPosts.size} posts from repository")

                    // Log raw posts before filtering
                    allPosts.forEachIndexed { index, post ->
                        Log.d("HomeViewModel", "   RAW $index: ID=${post.postId}, Name=${post.petName}, Type=${post.petType}")
                    }

                    // 🔥 FIXED: Only filter out messages, NOT based on age/weight
                    allPosts = filterOutMessages(allPosts)

                    // Filter blocked posts
                    allPosts = filterBlockedPosts(allPosts)

                    Log.d("HomeViewModel", "✅ allPosts updated: ${allPosts.size} total posts after filtering")

                    // THEN apply current category filter
                    filterPostsByCategory(_currentCategory.value ?: "All")

                    _isOffline.value = false

                    val currentUser = _currentUser.value
                    if (currentUser != null) {
                        loadLikeAndFavoriteStatuses(allPosts, currentUser)
                    }

                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to load posts"
                    _isOffline.value = true
                }
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
                _isOffline.value = true
            }
        }
    }

    // UPDATED: Force refresh posts from server and filter messages only
    fun forceRefreshPosts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d("HomeViewModel", "🔄 Force refreshing all posts from server")

                val result = postRepository.getPosts(limit = 100, viewerUid = _currentUser.value?.firebaseUid)

                if (result.isSuccess) {
                    allPosts = result.getOrNull()!!

                    Log.d("HomeViewModel", "📥 Force refresh received ${allPosts.size} posts")

                    // 🔥 FIXED: Only filter out messages, NOT based on age/weight
                    allPosts = filterOutMessages(allPosts)

                    val currentUser = _currentUser.value
                    if (currentUser != null) {
                        val blockResult = blockRepository.getBlockedUsers(currentUser.firebaseUid)
                        if (blockResult.isSuccess) {
                            val blockedUsers = blockResult.getOrNull() ?: emptyList()
                            val blockedUids = blockedUsers.map { it.blockedUid }
                            allPosts = allPosts.filter { !blockedUids.contains(it.firebaseUid) }
                        }
                    }

                    Log.d("HomeViewModel", "✅ Force refreshed: ${allPosts.size} posts from server")

                    filterPostsByCategory(_currentCategory.value ?: "All")

                    if (currentUser != null) {
                        loadLikeAndFavoriteStatuses(allPosts, currentUser)
                    }
                } else {
                    Log.e("HomeViewModel", "❌ Failed to force refresh: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error force refreshing: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Force refresh and then filter by category
    fun forceRefreshAndFilter(category: String) {
        _currentCategory.value = category

        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d("HomeViewModel", "🔄 FORCE REFRESHING for category: $category")

                val result = postRepository.getPosts(limit = 100, viewerUid = _currentUser.value?.firebaseUid)

                if (result.isSuccess) {
                    allPosts = result.getOrNull()!!

                    Log.d("HomeViewModel", "📥 Got ${allPosts.size} posts from server")

                    // 🔥 FIXED: Only filter out messages, NOT based on age/weight
                    allPosts = filterOutMessages(allPosts)

                    val currentUser = _currentUser.value
                    if (currentUser != null) {
                        val blockResult = blockRepository.getBlockedUsers(currentUser.firebaseUid)
                        if (blockResult.isSuccess) {
                            val blockedUsers = blockResult.getOrNull() ?: emptyList()
                            val blockedUids = blockedUsers.map { it.blockedUid }
                            allPosts = allPosts.filter { !blockedUids.contains(it.firebaseUid) }
                        }
                    }

                    Log.d("HomeViewModel", "✅ Got ${allPosts.size} total posts from server for $category")

                    filterPostsByCategory(category)

                    if (currentUser != null) {
                        loadLikeAndFavoriteStatuses(allPosts, currentUser)
                    }
                } else {
                    Log.e("HomeViewModel", "❌ Failed to refresh: ${result.exceptionOrNull()?.message}")
                    filterPostsByCategory(category)
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error in forceRefreshAndFilter: ${e.message}")
                filterPostsByCategory(category)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun filterBlockedPosts(posts: List<ApiPost>): List<ApiPost> {
        val currentUser = _currentUser.value ?: return posts
        val result = blockRepository.getBlockedUsers(currentUser.firebaseUid)
        if (result.isSuccess) {
            val blockedUsers = result.getOrNull() ?: emptyList()
            val blockedUids = blockedUsers.map { it.blockedUid }
            return posts.filter { !blockedUids.contains(it.firebaseUid) }
        }
        return posts
    }

    // Helper function to load like/favorite statuses
    private suspend fun loadLikeAndFavoriteStatuses(posts: List<ApiPost>, currentUser: ApiUser) {
        val likeMap = mutableMapOf<String, Boolean>()
        val favMap = mutableMapOf<String, Boolean>()
        val followMap = mutableMapOf<String, Boolean>()

        for (post in posts) {
            try {
                val likeResult = postRepository.checkLikeStatus(post.postId, currentUser.firebaseUid)
                likeMap[post.postId] = likeResult.getOrNull()?.isLiked ?: likeResult.getOrNull()?.liked ?: false
            } catch (e: Exception) {
                likeMap[post.postId] = false
            }

            try {
                val favResult = favoriteRepository.checkFavorite(post.postId, currentUser.firebaseUid)
                favMap[post.postId] = favResult.getOrNull() ?: false
            } catch (e: Exception) {
                favMap[post.postId] = false
            }

            if (post.firebaseUid != currentUser.firebaseUid) {
                try {
                    val followResult = followRepository.checkFollowStatus(currentUser.firebaseUid, post.firebaseUid)
                    followMap[post.firebaseUid] = followResult.getOrNull() ?: false
                } catch (e: Exception) {
                    followMap[post.firebaseUid] = false
                }
            }
        }

        _likeStatus.value = likeMap
        _favoriteStatus.value = favMap
        _followStatus.value = followMap
    }

    fun updatePostInFeed(updatedPost: ApiPost) {
        val currentPosts = _posts.value?.toMutableList() ?: return
        val index = currentPosts.indexOfFirst { it.postId == updatedPost.postId }

        if (index >= 0) {
            currentPosts[index] = updatedPost
            _posts.value = currentPosts
            Log.d("HomeViewModel", "✅ Updated single post in feed: ${updatedPost.postId} with count: ${updatedPost.likesCount}")
        }
    }

    fun toggleLike(post: ApiPost, currentStatus: Boolean) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch

            // 🔥 FIXED: Only update like status map, NOT posts list
            // This prevents triggering full view recreation in HomeActivity
            val newLikeStatus = !currentStatus
            val currentMap = _likeStatus.value?.toMutableMap() ?: mutableMapOf()
            currentMap[post.postId] = newLikeStatus
            _likeStatus.value = currentMap

            Log.d("LikeDebug", "Toggled like optimistically for ${post.postId}: $newLikeStatus")

            // Call API to sync with server
            val result = postRepository.likePost(post.postId, currentUser.firebaseUid)

            if (result.isSuccess) {
                val response = result.getOrNull()!!
                val serverLiked = response.isLiked ?: response.liked ?: newLikeStatus
                val serverCount = response.likesCount

                // 🔥 FIXED: Only update like status, NEVER touch posts list
                // Updating posts list causes full feed refresh
                val finalMap = _likeStatus.value?.toMutableMap() ?: mutableMapOf()
                finalMap[post.postId] = serverLiked
                _likeStatus.value = finalMap

                Log.d("LikeDebug", "✅ Like synced for ${post.postId}: liked=$serverLiked, count=$serverCount")
            } else {
                // Revert on error
                val revertMap = _likeStatus.value?.toMutableMap() ?: mutableMapOf()
                revertMap[post.postId] = currentStatus
                _likeStatus.value = revertMap
                Log.e("LikeDebug", "Failed to like post: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun toggleFavorite(post: ApiPost, currentStatus: Boolean) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch

            val result = if (currentStatus) {
                favoriteRepository.removeFromFavorites(currentUser.firebaseUid, post.postId)
            } else {
                favoriteRepository.addToFavorites(currentUser.firebaseUid, post.postId)
            }

            if (result.isSuccess) {
                val currentMap = _favoriteStatus.value?.toMutableMap() ?: mutableMapOf()
                currentMap[post.postId] = !currentStatus
                _favoriteStatus.value = currentMap

                val message = if (!currentStatus) "Added to favorites" else "Removed from favorites"
                Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()

                viewModelScope.launch {
                    FavoritesManager.notifyFavoriteChanged(post.postId)
                }
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to update favorite"
                Toast.makeText(getApplication(), "Failed to update favorite", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleFollow(targetUserId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch

            val result = if (currentStatus) {
                followRepository.unfollowUser(currentUser.firebaseUid, targetUserId)
            } else {
                followRepository.followUser(currentUser.firebaseUid, targetUserId)
            }

            if (result.isSuccess) {
                val currentMap = _followStatus.value?.toMutableMap() ?: mutableMapOf()
                currentMap[targetUserId] = !currentStatus
                _followStatus.value = currentMap

                val message = if (!currentStatus) "Followed user" else "Unfollowed user"
                Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()

                loadPosts(true)
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to update follow"
                Toast.makeText(getApplication(), "Failed to update follow", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun checkFollowStatus(targetUserId: String): Boolean {
        return _followStatus.value?.get(targetUserId) ?: false
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch

            val result = offlinePostRepository.deletePost(postId, currentUser.firebaseUid)

            if (result.isSuccess) {
                val currentPosts = _posts.value?.toMutableList() ?: mutableListOf()
                currentPosts.removeAll { it.postId == postId }
                _posts.value = currentPosts

                val currentLikeMap = _likeStatus.value?.toMutableMap() ?: mutableMapOf()
                currentLikeMap.remove(postId)
                _likeStatus.value = currentLikeMap

                val currentFavMap = _favoriteStatus.value?.toMutableMap() ?: mutableMapOf()
                currentFavMap.remove(postId)
                _favoriteStatus.value = currentFavMap

                Toast.makeText(getApplication(), "Post deleted", Toast.LENGTH_SHORT).show()
                loadPosts(true)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to delete post"
                Toast.makeText(getApplication(), errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val _hiddenPostIds = MutableLiveData<Set<String>>(emptySet())
    val hiddenPostIds: LiveData<Set<String>> = _hiddenPostIds

    fun loadHiddenPosts(userUid: String) {
        viewModelScope.launch {
            try {
                val hideRepository = HidePostRepository()
                val result = hideRepository.getHiddenPosts(userUid)
                if (result.isSuccess) {
                    val hiddenPosts = result.getOrNull() ?: emptyList()
                    val hiddenIds = hiddenPosts.map { it.postId }.toSet()
                    _hiddenPostIds.value = hiddenIds
                    Log.d("HomeViewModel", "Loaded ${hiddenIds.size} hidden post IDs")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading hidden posts: ${e.message}")
            }
        }
    }

    fun getUserById(userId: String, onResult: (ApiUser?) -> Unit) {
        viewModelScope.launch {
            val result = userRepository.getUserByUid(userId)
            onResult(result.getOrNull())
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun debugPrintPosts() {
        viewModelScope.launch {
            Log.d("VM_DEBUG", "=== POSTS IN VIEWMODEL ===")
            _posts.value?.forEachIndexed { index, post ->
                Log.d("VM_DEBUG", "[$index] ${post.postId}: petType=${post.petType}, likesCount=${post.likesCount}")
            }
            Log.d("VM_DEBUG", "=== END POSTS ===")

            Log.d("VM_DEBUG", "=== LIKE STATUS MAP ===")
            _likeStatus.value?.forEach { (postId, status) ->
                Log.d("VM_DEBUG", "$postId: $status")
            }
            Log.d("VM_DEBUG", "=== END LIKE STATUS ===")
        }
    }
}
