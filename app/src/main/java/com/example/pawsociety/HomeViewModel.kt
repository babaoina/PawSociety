package com.example.pawsociety

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.pawsociety.api.ApiComment
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.FavoritesManager
import com.example.pawsociety.data.repository.*
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val postRepository = PostRepository()
    private val offlinePostRepository = OfflinePostRepository(context)
    private val commentRepository = CommentRepository()
    private val favoriteRepository = FavoriteRepository()
    private val userRepository = UserRepository()
    private val followRepository = FollowRepository()
    private val blockRepository = BlockRepository()

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

    private var sessionManager: SessionManager? = null

    init {
        _isLoading.value = false
        _isOffline.value = false
        _likeStatus.value = emptyMap()
        _favoriteStatus.value = emptyMap()
        _followStatus.value = emptyMap()

        viewModelScope.launch {
            offlinePostRepository.getPostsFlow().collect { cachedPosts ->
                if (cachedPosts.isNotEmpty()) {
                    _posts.value = cachedPosts
                }
            }
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

    fun loadPosts(forceRefresh: Boolean = false) {
        if (sessionManager == null) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = offlinePostRepository.loadPosts(forceRefresh)

                if (result.isSuccess) {
                    val postsList = result.getOrNull()!!
                    val filteredPosts = filterBlockedPosts(postsList)
                    _posts.value = filteredPosts
                    _isOffline.value = false

                    val currentUser = _currentUser.value
                    if (currentUser != null) {
                        val likeMap = mutableMapOf<String, Boolean>()
                        val favMap = mutableMapOf<String, Boolean>()
                        val followMap = mutableMapOf<String, Boolean>()

                        for (post in postsList) {
                            val likeResult = postRepository.checkLikeStatus(post.postId, currentUser.firebaseUid)
                            likeMap[post.postId] = likeResult.getOrNull() ?: false

                            val favResult = favoriteRepository.checkFavorite(post.postId, currentUser.firebaseUid)
                            favMap[post.postId] = favResult.getOrNull() ?: false

                            if (post.firebaseUid != currentUser.firebaseUid) {
                                val followResult = followRepository.checkFollowStatus(currentUser.firebaseUid, post.firebaseUid)
                                followMap[post.firebaseUid] = followResult.getOrNull() ?: false
                            }
                        }

                        _likeStatus.value = likeMap
                        _favoriteStatus.value = favMap
                        _followStatus.value = followMap
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

    fun toggleLike(post: ApiPost, currentStatus: Boolean) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch
            val result = postRepository.likePost(post.postId, currentUser.firebaseUid)

            if (result.isSuccess) {
                val currentMap = _likeStatus.value?.toMutableMap() ?: mutableMapOf()
                currentMap[post.postId] = !currentStatus
                _likeStatus.value = currentMap
                loadPosts(true)
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to like post"
                Toast.makeText(getApplication(), "Failed to like post", Toast.LENGTH_SHORT).show()
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

                loadPosts(true)
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

    fun addComment(postId: String, text: String) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch
            val result = commentRepository.createComment(
                postId = postId,
                firebaseUid = currentUser.firebaseUid,
                userName = currentUser.username,
                text = text
            )

            if (result.isSuccess) {
                loadPosts(true)
                Toast.makeText(getApplication(), "Comment added", Toast.LENGTH_SHORT).show()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to add comment"
                Toast.makeText(getApplication(), "Failed to add comment", Toast.LENGTH_SHORT).show()
            }
        }
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

    fun getUserById(userId: String, onResult: (ApiUser?) -> Unit) {
        viewModelScope.launch {
            val result = userRepository.getUserByUid(userId)
            onResult(result.getOrNull())
        }
    }

    fun clearError() {
        _error.value = null
    }
}