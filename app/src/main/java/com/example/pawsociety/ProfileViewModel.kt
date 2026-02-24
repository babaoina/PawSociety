package com.example.pawsociety

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsociety.api.ApiPet
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.FavoriteRepository
import com.example.pawsociety.data.repository.PetRepository
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val userRepository = UserRepository()
    private val postRepository = PostRepository()
    private val favoriteRepository = FavoriteRepository()
    private val petRepository = PetRepository()
    
    private val _user = MutableLiveData<ApiUser?>()
    val user: LiveData<ApiUser?> = _user

    private val _userPosts = MutableLiveData<List<ApiPost>>()
    val userPosts: LiveData<List<ApiPost>> = _userPosts

    private val _favoritePosts = MutableLiveData<List<ApiPost>>()
    val favoritePosts: LiveData<List<ApiPost>> = _favoritePosts
    
    private val _pets = MutableLiveData<List<ApiPet>>()
    val pets: LiveData<List<ApiPet>> = _pets

    private val _highlights = MutableLiveData<List<ProfileActivity.Highlight>>()
    val highlights: LiveData<List<ProfileActivity.Highlight>> = _highlights

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var sessionManager: SessionManager? = null

    init {
        _isLoading.value = false
    }

    fun setSessionManager(sessionManager: SessionManager) {
        this.sessionManager = sessionManager
        loadUserData()
    }

    fun loadUserData() {
        val cachedUser = sessionManager?.getCurrentUser()
        if (cachedUser == null) {
            println("⚠️ ProfileViewModel: No cached user found")
            return
        }

        println("📤 ProfileViewModel: Loading fresh user data from API for ${cachedUser.firebaseUid}")
        
        viewModelScope.launch {
            // ALWAYS fetch fresh data from API
            val result = userRepository.getUserByUid(cachedUser.firebaseUid)

            if (result.isSuccess) {
                val freshUser = result.getOrNull()!!
                println("✅ ProfileViewModel: Loaded fresh user - username: ${freshUser.username}, email: ${freshUser.email}, image: ${freshUser.profileImageUrl}")
                // Force trigger observer
                _user.value = freshUser
                // Update session with fresh data
                sessionManager?.saveUserSession(freshUser)

                loadUserPosts(freshUser.firebaseUid)
                loadHighlights(freshUser.firebaseUid)
                loadPets(freshUser.firebaseUid)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                println("❌ ProfileViewModel: Failed to load user from API: $errorMsg")
                // Fallback to cached user - still triggers observer
                _user.value = cachedUser.copy() // Create a copy to force change
                loadUserPosts(cachedUser.firebaseUid)
                loadHighlights(cachedUser.firebaseUid)
                loadPets(cachedUser.firebaseUid)
            }
        }
    }

    private fun loadUserPosts(userId: String) {
        viewModelScope.launch {
            val result = postRepository.getPosts(firebaseUid = userId)
            if (result.isSuccess) {
                _userPosts.value = result.getOrNull()!!
                loadFavoritePosts(userId)
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to load posts"
            }
        }
    }

    private fun loadFavoritePosts(userId: String) {
        viewModelScope.launch {
            val result = favoriteRepository.getFavorites(userId)
            if (result.isSuccess) {
                _favoritePosts.value = result.getOrNull()!!
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to load favorites"
            }
        }
    }
    
    private fun loadPets(userId: String) {
        viewModelScope.launch {
            val result = petRepository.getPets(ownerUid = userId)
            if (result.isSuccess) {
                _pets.value = result.getOrNull()!!
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to load pets"
            }
        }
    }

    fun updateProfile(
        username: String? = null,
        fullName: String? = null,
        bio: String? = null,
        profileImageUrl: String? = null,
        phone: String? = null,
        location: String? = null
    ) {
        viewModelScope.launch {
            val currentUser = _user.value ?: return@launch
            val sessionMgr = sessionManager ?: return@launch

            _isLoading.value = true
            val result = userRepository.updateUser(
                firebaseUid = currentUser.firebaseUid,
                username = username,
                fullName = fullName,
                bio = bio,
                profileImageUrl = profileImageUrl,
                phone = phone,
                location = location
            )

            if (result.isSuccess) {
                val updatedUser = result.getOrNull()!!
                println("✅ ProfileViewModel: Profile updated successfully")
                _user.value = updatedUser
                sessionMgr.saveUserSession(updatedUser)
                // Force reload to trigger observer
                loadUserData()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to update profile"
                println("❌ ProfileViewModel: Update failed - $errorMsg")
                _error.value = errorMsg
            }
            _isLoading.value = false
        }
    }

    fun saveHighlights(highlights: List<ProfileActivity.Highlight>) {
        viewModelScope.launch {
            val user = _user.value
            user?.let {
                UserDatabase.saveUserHighlights(
                    com.example.pawsociety.MyApplication.instance,
                    it.firebaseUid,
                    highlights
                )
                loadHighlights(it.firebaseUid)
            }
        }
    }

    private fun loadHighlights(userId: String) {
        val savedHighlights = UserDatabase.getUserHighlights(
            com.example.pawsociety.MyApplication.instance,
            userId
        )
        _highlights.value = savedHighlights
    }

    fun refreshData() {
        loadUserData()
    }

    fun clearError() {
        _error.value = null
    }
}
