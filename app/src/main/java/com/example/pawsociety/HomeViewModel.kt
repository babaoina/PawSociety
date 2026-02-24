package com.example.pawsociety

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsociety.api.ApiComment
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.CommentRepository
import com.example.pawsociety.data.repository.FavoriteRepository
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val postRepository = PostRepository()
    private val commentRepository = CommentRepository()
    private val favoriteRepository = FavoriteRepository()
    private val userRepository = UserRepository()

    // UI States
    private val _posts = MutableLiveData<List<ApiPost>>()
    val posts: LiveData<List<ApiPost>> = _posts

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _currentUser = MutableLiveData<com.example.pawsociety.api.ApiUser?>()
    val currentUser: LiveData<com.example.pawsociety.api.ApiUser?> = _currentUser

    private val _favoriteStatus = MutableLiveData<Map<String, Boolean>>()
    val favoriteStatus: LiveData<Map<String, Boolean>> = _favoriteStatus

    private var sessionManager: SessionManager? = null

    init {
        _favoriteStatus.value = emptyMap()
        // Don't load data until sessionManager is set
    }

    fun setSessionManager(sessionManager: SessionManager) {
        this.sessionManager = sessionManager
        // Only load data once sessionManager is set
        loadCurrentUser()
        loadPosts()
    }

    fun followUser(userId: String) {
        viewModelScope.launch {
            val currentUser = _currentUser.value
            if (currentUser != null) {
                // TODO: Implement API call to follow user
                println("Following user: $userId")
            }
        }
    }

    fun unfollowUser(userId: String) {
        viewModelScope.launch {
            val currentUser = _currentUser.value
            if (currentUser != null) {
                // TODO: Implement API call to unfollow user
                println("Unfollowing user: $userId")
            }
        }
    }

    fun loadCurrentUser() {
        val cachedUser = sessionManager?.getCurrentUser()
        if (cachedUser == null) {
            return
        }
        
        // Set cached user immediately
        _currentUser.value = cachedUser
        
        // Fetch fresh user data from API in background
        viewModelScope.launch {
            val result = userRepository.getUserByUid(cachedUser.firebaseUid)
            if (result.isSuccess) {
                val freshUser = result.getOrNull()!!
                _currentUser.value = freshUser
                sessionManager?.saveUserSession(freshUser)
            }
        }
    }

    fun loadPosts() {
        // Don't load posts if sessionManager is not set
        if (sessionManager == null) {
            return
        }
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = postRepository.getPosts(limit = 50)

                if (result.isSuccess) {
                    val postsList = result.getOrNull()!!
                    _posts.value = postsList
                    _isLoading.value = false

                    // Check favorite status for each post
                    val currentUser = _currentUser.value
                    if (currentUser != null) {
                        val favMap = mutableMapOf<String, Boolean>()
                        for (post in postsList) {
                            val favResult = favoriteRepository.checkFavorite(post.postId, currentUser.firebaseUid)
                            favMap[post.postId] = favResult.getOrNull() ?: false
                        }
                        _favoriteStatus.value = favMap
                    }
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to load posts"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun toggleLike(post: ApiPost, currentStatus: Boolean) {
        viewModelScope.launch {
            val currentUser = _currentUser.value
            if (currentUser != null) {
                // Call the posts like API
                val result = postRepository.likePost(post.postId, currentUser.firebaseUid)
                
                if (result.isSuccess) {
                    // Update local favorite status
                    val currentMap = _favoriteStatus.value?.toMutableMap() ?: mutableMapOf()
                    currentMap[post.postId] = !currentStatus
                    _favoriteStatus.value = currentMap
                    
                    // Also refresh posts to get updated like count
                    loadPosts()
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to like post"
                }
            }
        }
    }

    fun toggleFavorite(post: ApiPost, currentStatus: Boolean) {
        viewModelScope.launch {
            val currentUser = _currentUser.value
            if (currentUser != null) {
                val result = if (currentStatus) {
                    favoriteRepository.removeFromFavorites(currentUser.firebaseUid, post.postId)
                } else {
                    favoriteRepository.addToFavorites(currentUser.firebaseUid, post.postId)
                }

                if (result.isSuccess) {
                    // Update local favorite status
                    val currentMap = _favoriteStatus.value?.toMutableMap() ?: mutableMapOf()
                    currentMap[post.postId] = !currentStatus
                    _favoriteStatus.value = currentMap
                    
                    // Refresh posts
                    loadPosts()
                }
            }
        }
    }

    fun addComment(postId: String, text: String) {
        viewModelScope.launch {
            val currentUser = _currentUser.value
            if (currentUser != null) {
                val result = commentRepository.createComment(
                    postId = postId,
                    firebaseUid = currentUser.firebaseUid,
                    userName = currentUser.username,
                    text = text
                )
                
                if (result.isSuccess) {
                    // Refresh posts to show new comment count
                    loadPosts()
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to add comment"
                }
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val currentUser = _currentUser.value
            if (currentUser != null) {
                val result = postRepository.deletePost(postId, currentUser.firebaseUid)
                if (result.isSuccess) {
                    loadPosts() // Refresh the list
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to delete post"
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
