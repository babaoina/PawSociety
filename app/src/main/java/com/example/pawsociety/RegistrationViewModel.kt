package com.example.pawsociety.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.AuthRepository
import com.example.pawsociety.data.repository.UploadRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.FCMTokenManager
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import com.example.pawsociety.util.FileHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

class RegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val authRepository = AuthRepository()
    private val uploadRepository = UploadRepository()
    private val userRepository = UserRepository()
    private lateinit var sessionManager: SessionManager

    // Registration data
    private val _email = MutableLiveData<String>()
    val email: LiveData<String> = _email

    private val _password = MutableLiveData<String>()
    val password: LiveData<String> = _password

    private val _lastName = MutableLiveData<String>()
    val lastName: LiveData<String> = _lastName

    private val _firstName = MutableLiveData<String>()
    val firstName: LiveData<String> = _firstName

    private val _middleInitial = MutableLiveData<String>()
    val middleInitial: LiveData<String> = _middleInitial

    private val _username = MutableLiveData<String>()
    val username: LiveData<String> = _username

    private val _mobile = MutableLiveData<String>()
    val mobile: LiveData<String> = _mobile

    private val _profileImageUri = MutableLiveData<String?>()
    val profileImageUri: LiveData<String?> = _profileImageUri

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isVerified = MutableLiveData<Boolean>()
    val isVerified: LiveData<Boolean> = _isVerified

    private val _registrationComplete = MutableLiveData<Boolean>()
    val registrationComplete: LiveData<Boolean> = _registrationComplete

    fun setSessionManager(sessionManager: SessionManager) {
        this.sessionManager = sessionManager
    }

    fun setEmail(email: String) {
        _email.value = email
    }

    fun setPassword(password: String) {
        _password.value = password
    }

    fun setLastName(lastName: String) {
        _lastName.value = lastName
    }

    fun setFirstName(firstName: String) {
        _firstName.value = firstName
    }

    fun setMiddleInitial(middleInitial: String) {
        _middleInitial.value = middleInitial
    }

    fun setUsername(username: String) {
        _username.value = username
    }

    fun setMobile(mobile: String) {
        _mobile.value = mobile
    }

    fun setProfileImageUri(uri: String?) {
        _profileImageUri.value = uri
    }

    fun register(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val emailValue = _email.value ?: throw Exception("Email is required")
                val passwordValue = _password.value ?: throw Exception("Password is required")

                val firebaseResult = FirebaseAuthHelper.registerWithEmail(emailValue, passwordValue)

                if (firebaseResult.isFailure) {
                    onError(firebaseResult.exceptionOrNull()?.message ?: "Registration failed")
                    _isLoading.value = false
                    return@launch
                }

                val firebaseUser = firebaseResult.getOrNull()!!
                FirebaseAuthHelper.sendEmailVerification()

                val tempUsername = emailValue.substringBefore("@") + "_temp" + UUID.randomUUID().toString().take(4)

                val backendResult = authRepository.firebaseLogin(
                    firebaseUid = firebaseUser.uid,
                    email = emailValue,
                    username = tempUsername,
                    fullName = ""
                )

                if (backendResult.isSuccess) {
                    val apiUser = backendResult.getOrNull()!!
                    sessionManager.saveUserSession(apiUser)
                } else {
                    val localUser = ApiUser(
                        firebaseUid = firebaseUser.uid,
                        email = emailValue,
                        username = tempUsername,
                        fullName = ""
                    )
                    sessionManager.saveUserSession(localUser)
                }

                onSuccess()

            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkEmailVerification(onVerified: () -> Unit, onNotVerified: () -> Unit) {
        viewModelScope.launch {
            try {
                val user = FirebaseAuthHelper.currentUser
                if (user != null) {
                    user.reload()
                    delay(1000)
                    val isVerified = user.isEmailVerified
                    _isVerified.value = isVerified
                    if (isVerified) {
                        onVerified()
                    } else {
                        onNotVerified()
                    }
                } else {
                    onNotVerified()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onNotVerified()
            }
        }
    }

    fun resendVerificationEmail(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = FirebaseAuthHelper.sendEmailVerification()
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Failed to resend")
            }
        }
    }

    fun completeRegistration() {
        viewModelScope.launch {
            try {
                println("🔥 COMPLETE REGISTRATION STARTED 🔥")

                // Ensure username is never null or empty
                var usernameValue = _username.value
                if (usernameValue.isNullOrEmpty()) {
                    val emailValue = _email.value ?: "user"
                    usernameValue = emailValue.substringBefore("@") + System.currentTimeMillis().toString().takeLast(4)
                    _username.value = usernameValue
                    println("✅ Generated username: $usernameValue")
                }

                val lastNameValue = _lastName.value ?: ""
                val firstNameValue = _firstName.value ?: ""
                val middleValue = _middleInitial.value ?: ""
                val mobileValue = _mobile.value ?: ""
                val photoUri = _profileImageUri.value

                // Try to get current user, if null, create a new one from email
                var currentUser = sessionManager.getCurrentUser()
                if (currentUser == null) {
                    println("⚠️ No session found, creating from email")
                    val emailValue = _email.value ?: throw Exception("Email is required")
                    val firebaseUser = FirebaseAuthHelper.currentUser
                    currentUser = ApiUser(
                        firebaseUid = firebaseUser?.uid ?: "",
                        email = emailValue,
                        username = emailValue.substringBefore("@"),
                        fullName = "$firstNameValue $lastNameValue"
                    )
                }

                println("📝 Current user: ${currentUser.username} (UID: ${currentUser.firebaseUid})")

                // Format full name
                val fullName = if (middleValue.isNotEmpty()) {
                    "$lastNameValue, $firstNameValue $middleValue."
                } else {
                    "$lastNameValue, $firstNameValue"
                }
                println("📝 Full name: $fullName")

                // Upload photo if selected (not from Google)
                var profileImageUrl: String? = null
                if (!photoUri.isNullOrEmpty() && !photoUri.startsWith("http")) {
                    try {
                        println("📸 Uploading photo...")
                        val file = FileHelper.uriToFile(context, Uri.parse(photoUri))
                        if (file != null) {
                            val compressedFile = FileHelper.compressImage(file, maxWidth = 512, quality = 85)
                            val uploadResult = uploadRepository.uploadProfilePicture(compressedFile)
                            if (uploadResult.isSuccess) {
                                profileImageUrl = uploadResult.getOrNull()
                                println("✅ Photo uploaded: $profileImageUrl")
                            }
                            FileHelper.deleteFile(file)
                            if (compressedFile != file) {
                                FileHelper.deleteFile(compressedFile)
                            }
                        }
                    } catch (e: Exception) {
                        println("❌ Photo upload error: ${e.message}")
                        e.printStackTrace()
                    }
                } else if (!photoUri.isNullOrEmpty() && photoUri.startsWith("http")) {
                    profileImageUrl = photoUri // Google photo URL
                    println("📸 Using Google photo: $profileImageUrl")
                }

                // Update user in backend
                try {
                    println("📤 Updating user in backend...")
                    userRepository.updateUser(
                        firebaseUid = currentUser.firebaseUid,
                        username = usernameValue,
                        fullName = fullName,
                        phone = mobileValue,
                        profileImageUrl = profileImageUrl
                    )
                    println("✅ User updated in backend")
                } catch (e: Exception) {
                    println("❌ Backend update error: ${e.message}")
                    e.printStackTrace()
                }

                // Update local session
                val updatedUser = currentUser.copy(
                    username = usernameValue,
                    fullName = fullName,
                    phone = mobileValue,
                    profileImageUrl = profileImageUrl ?: currentUser.profileImageUrl
                )
                sessionManager.saveUserSession(updatedUser)
                println("✅ Session saved")

                // Connect Socket.IO and FCM
                SocketManager.connect()
                SocketManager.joinUserRoom(updatedUser.firebaseUid)
                FCMTokenManager.initialize(updatedUser.firebaseUid)
                println("✅ Socket and FCM initialized")

                // Set registration complete
                _registrationComplete.postValue(true)
                println("🎉 Registration complete!")

            } catch (e: Exception) {
                println("❌ Registration error: ${e.message}")
                e.printStackTrace()
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun resetRegistrationComplete() {
        _registrationComplete.value = false
    }
}