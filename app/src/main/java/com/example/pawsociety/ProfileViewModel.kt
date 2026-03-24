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
import com.example.pawsociety.data.FavoritesManager
import kotlinx.coroutines.flow.collect

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

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var sessionManager: SessionManager? = null

    init {
        println("📊 ProfileViewModel INIT")
        _isLoading.value = false

        // Listen for favorite changes from Home
        viewModelScope.launch {
            try {
                FavoritesManager.favoriteChanged.collect { postId ->
                    println("📢 Favorite changed detected, refreshing favorites")
                    val currentUser = _user.value
                    if (currentUser != null) {
                        loadFavoritePosts(currentUser.firebaseUid)
                    }
                }
            } catch (e: Exception) {
                println("❌ Error in favorite listener: ${e.message}")
            }
        }
    }

    fun setSessionManager(sessionManager: SessionManager) {
        println("📊 ProfileViewModel: setSessionManager called")
        this.sessionManager = sessionManager
        loadUserData()
    }

    fun loadUserData() {
        println("📊 ProfileViewModel: loadUserData started")
        val cachedUser = sessionManager?.getCurrentUser()
        if (cachedUser == null) {
            println("⚠️ ProfileViewModel: No cached user found")
            return
        }

        println("📤 ProfileViewModel: Loading user data for ${cachedUser.username} with UID: ${cachedUser.firebaseUid}")
        _user.value = cachedUser

        viewModelScope.launch {
            try {
                // Check if firebaseUid is valid before making API call
                if (cachedUser.firebaseUid.isNullOrEmpty()) {
                    println("❌ ProfileViewModel: Firebase UID is empty")
                    _error.value = "Invalid user ID"
                    return@launch
                }

                println("📤 Calling userRepository.getUserByUid with: ${cachedUser.firebaseUid}")
                val result = userRepository.getUserByUid(cachedUser.firebaseUid)

                if (result.isSuccess) {
                    val freshUser = result.getOrNull()!!
                    println("✅ ProfileViewModel: Loaded fresh user - username: ${freshUser.username}")
                    _user.value = freshUser
                    sessionManager?.saveUserSession(freshUser)

                    // Load all data with the fresh user's UID
                    if (!freshUser.firebaseUid.isNullOrEmpty()) {
                        loadUserPosts(freshUser.firebaseUid)
                        loadPets(freshUser.firebaseUid)
                        loadFavoritePosts(freshUser.firebaseUid)
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    println("❌ ProfileViewModel: Failed to load user from API: $errorMsg")
                    // Still try to load data with cached user
                    if (!cachedUser.firebaseUid.isNullOrEmpty()) {
                        loadUserPosts(cachedUser.firebaseUid)
                        loadPets(cachedUser.firebaseUid)
                        loadFavoritePosts(cachedUser.firebaseUid)
                    }
                }
            } catch (e: Exception) {
                println("❌ ProfileViewModel: Exception in loadUserData: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun loadUserPosts(userId: String?) {
        println("📊 loadUserPosts called with userId: $userId")

        // Add null check at the beginning
        if (userId.isNullOrEmpty()) {
            println("⚠️ ProfileViewModel: Cannot load posts - userId is null or empty")
            _userPosts.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                println("📤 Calling postRepository.getPosts with firebaseUid: $userId")
                val result = postRepository.getPosts(
                    firebaseUid = userId,
                    viewerUid = sessionManager?.getCurrentUser()?.firebaseUid
                )
                if (result.isSuccess) {
                    val posts = result.getOrNull() ?: emptyList()
                    println("✅ Loaded ${posts.size} posts")
                    _userPosts.value = posts
                } else {
                    println("❌ Failed to load posts: ${result.exceptionOrNull()?.message}")
                    _userPosts.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Exception in loadUserPosts: ${e.message}")
                e.printStackTrace()
                _userPosts.value = emptyList()
            }
        }
    }

    // Public method to refresh all data
    fun refreshData() {
        println("📊 refreshData called")
        val currentUser = _user.value
        if (currentUser != null) {
            if (!currentUser.firebaseUid.isNullOrEmpty()) {
                loadFavoritePosts(currentUser.firebaseUid)
                loadUserPosts(currentUser.firebaseUid)
                loadPets(currentUser.firebaseUid)
            }
        }
    }

    fun loadFavoritePosts(userId: String?) {
        println("📊 loadFavoritePosts called with userId: $userId")

        // Add null check at the beginning
        if (userId.isNullOrEmpty()) {
            println("⚠️ ProfileViewModel: Cannot load favorites - userId is null or empty")
            _favoritePosts.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                println("📥 Loading favorite posts for user: $userId")
                val result = favoriteRepository.getFavorites(userId)
                if (result.isSuccess) {
                    val favorites = result.getOrNull()!!
                    println("✅ Loaded ${favorites.size} favorite posts")
                    _favoritePosts.value = favorites
                } else {
                    println("❌ Failed to load favorites: ${result.exceptionOrNull()?.message}")
                    _favoritePosts.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Exception in loadFavoritePosts: ${e.message}")
                e.printStackTrace()
                _favoritePosts.value = emptyList()
            }
        }
    }

    private fun loadPets(userId: String?) {
        println("📊 loadPets called with userId: $userId")

        // Add null check at the beginning
        if (userId.isNullOrEmpty()) {
            println("⚠️ ProfileViewModel: Cannot load pets - userId is null or empty")
            _pets.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                println("📤 Calling petRepository.getPets with ownerUid: $userId")
                val result = petRepository.getPets(ownerUid = userId)
                if (result.isSuccess) {
                    val pets = result.getOrNull() ?: emptyList()
                    println("✅ Loaded ${pets.size} pets")
                    _pets.value = pets
                } else {
                    println("❌ Failed to load pets: ${result.exceptionOrNull()?.message}")
                    _pets.value = emptyList()
                }
            } catch (e: Exception) {
                println("❌ Exception in loadPets: ${e.message}")
                e.printStackTrace()
                _pets.value = emptyList()
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
            try {
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
                    // Auto-refresh after update
                    loadUserData()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to update profile"
                    println("❌ ProfileViewModel: Update failed - $errorMsg")
                    _error.value = errorMsg
                }
                _isLoading.value = false
            } catch (e: Exception) {
                println("❌ Exception in updateProfile: ${e.message}")
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
