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

    private fun isGoogleAccountRegistration(): Boolean {
        val firebaseUser = FirebaseAuthHelper.currentUser ?: return false
        return firebaseUser.providerData.any { it.providerId == "google.com" }
    }

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

                // STEP 1: Create Firebase account first.
                // If the backend step fails after this, we roll Firebase back immediately.
                val firebaseResult = FirebaseAuthHelper.registerWithEmail(emailValue, passwordValue)

                if (firebaseResult.isFailure) {
                    onError(firebaseResult.exceptionOrNull()?.message ?: "Firebase registration failed")
                    _isLoading.value = false
                    return@launch
                }

                val firebaseUser = firebaseResult.getOrNull()!!

                try {
                    // STEP 2: Create or resume the unverified backend placeholder.
                    val registerResult = authRepository.registerUnverified(
                        email = emailValue,
                        username = null,
                        fullName = null,
                        phone = null
                    )

                    if (registerResult.isFailure) {
                        throw Exception(registerResult.exceptionOrNull()?.message ?: "Registration failed")
                    }

                    // STEP 3: Send email verification.
                    val verificationResult = FirebaseAuthHelper.sendEmailVerification()
                    if (verificationResult.isFailure) {
                        throw Exception(
                            verificationResult.exceptionOrNull()?.message
                                ?: "We couldn't send the verification email."
                        )
                    }

                    // STEP 4: Store temporary data for later finalization.
                    sessionManager.saveTempRegistrationData(
                        firebaseUid = firebaseUser.uid,
                        email = emailValue,
                        username = null,
                        fullName = null,
                        password = passwordValue
                    )

                    onSuccess()
                } catch (backendOrVerificationError: Exception) {
                    FirebaseAuthHelper.deleteCurrentUser()
                    FirebaseAuthHelper.signOut()
                    throw backendOrVerificationError
                }

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
                    // Generate username respecting 20 char limit
                    val emailPart = emailValue.substringBefore("@").take(15) // Limit to 15 chars
                    val timestamp = System.currentTimeMillis().toString().takeLast(4) // 4 chars
                    usernameValue = "${emailPart}_${timestamp}" // Max: 15 + 1 + 4 = 20 chars
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
                    val firebaseUser = FirebaseAuthHelper.currentUser
                    if (firebaseUser?.uid.isNullOrEmpty()) {
                        _error.value = "Firebase user not authenticated. Please login again."
                        return@launch
                    }
                    currentUser = ApiUser(
                        firebaseUid = firebaseUser!!.uid,  // ✅ Guaranteed non-empty
                        email = _email.value ?: "",
                        username = _username.value ?: _email.value?.substringBefore("@") ?: "user",
                        fullName = "${_firstName.value ?: ""} ${_lastName.value ?: ""}".trim(),
                        phone = _mobile.value ?: ""
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

                val backendUser = if (isGoogleAccountRegistration()) {
                    try {
                        println("🔗 Completing Google account setup...")
                        val googleLoginResult = authRepository.firebaseLogin(
                            firebaseUid = currentUser.firebaseUid,
                            email = currentUser.email,
                            username = usernameValue,
                            fullName = fullName,
                            phone = mobileValue
                        )
                        if (googleLoginResult.isFailure) {
                            throw Exception(googleLoginResult.exceptionOrNull()?.message ?: "We couldn't complete your Google sign-up.")
                        }
                        println("✅ Google account synced")
                        googleLoginResult.getOrNull() ?: currentUser
                    } catch (e: Exception) {
                        println("❌ Google setup error: ${e.message}")
                        _error.value = e.message ?: "We couldn't complete your Google sign-up."
                        return@launch
                    }
                } else {
                    try {
                        println("🔗 Finalizing account (linking Firebase UID to MongoDB user)...")
                        val finalizeResult = authRepository.finalizeAccount(
                            firebaseUid = currentUser.firebaseUid,
                            email = currentUser.email
                        )
                        if (finalizeResult.isFailure) {
                            throw Exception(finalizeResult.exceptionOrNull()?.message ?: "We couldn't finish setting up your account.")
                        }
                        println("✅ Account finalized")
                        currentUser
                    } catch (e: Exception) {
                        println("❌ Finalization error: ${e.message}")
                        _error.value = e.message ?: "We couldn't finish setting up your account."
                        return@launch
                    }
                }

                // STEP 2: Update user in backend with profile data
                try {
                    println("📤 Updating user in backend...")
                    val updateResult = userRepository.updateUser(
                        firebaseUid = backendUser.firebaseUid,
                        username = usernameValue,
                        fullName = fullName,
                        phone = mobileValue,
                        profileImageUrl = profileImageUrl
                    )
                    if (updateResult.isFailure) {
                        throw Exception(updateResult.exceptionOrNull()?.message ?: "We couldn't save your profile.")
                    }
                    println("✅ User updated in backend")
                } catch (e: Exception) {
                    println("❌ Backend update error: ${e.message}")
                    _error.value = e.message ?: "We couldn't save your profile."
                    return@launch  // ✅ STOP here, don't continue
                }

                // STEP 3: Update local session
                val updatedUser = backendUser.copy(
                    username = usernameValue,
                    fullName = fullName,
                    phone = mobileValue,
                    profileImageUrl = profileImageUrl ?: backendUser.profileImageUrl
                )
                sessionManager.saveUserSession(updatedUser)
                sessionManager.clearTempRegistrationData()
                println("✅ Session saved")

                // STEP 4: Connect Socket.IO and FCM
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
                _error.value = e.message ?: "We couldn't complete your registration."
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
