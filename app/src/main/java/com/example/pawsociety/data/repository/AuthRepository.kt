package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    private val apiService = ApiClient.apiService

    private fun parseApiError(raw: String?, fallback: String): String {
        val body = raw?.trim().orEmpty()
        if (body.isEmpty()) return fallback

        fun extractValue(key: String): String? {
            val regex = """"$key"\s*:\s*"([^"]+)"""".toRegex()
            return regex.find(body)?.groupValues?.getOrNull(1)
        }

        return extractValue("error")
            ?: extractValue("message")
            ?: body.takeIf { !it.startsWith("{") }
            ?: fallback
    }

    /**
     * Login/Register using Firebase UID
     * Creates new user if doesn't exist, otherwise returns existing user
     */
    suspend fun firebaseLogin(
        firebaseUid: String,
        email: String,
        username: String? = null,
        fullName: String? = null,
        phone: String? = null
    ): Result<ApiUser> = withContext(Dispatchers.IO) {
        try {
            val request = FirebaseLoginRequest(
                firebaseUid = firebaseUid,
                email = email,
                username = username,
                fullName = fullName,
                phone = phone
            )

            val response = apiService.firebaseLogin(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Login failed"))
                }
            } else {
                Result.failure(Exception(parseApiError(response.errorBody()?.string(), "Unable to sign in right now.")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send password reset email
     * Backend endpoint wraps Firebase password reset functionality
     */
    suspend fun forgotPassword(email: String): Result<ForgotPasswordResponse> = withContext(Dispatchers.IO) {
        try {
            val request = mapOf("email" to email)
            val response = apiService.forgotPassword(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body?.error ?: "Failed to send reset email"))
                }
            } else {
                Result.failure(Exception(parseApiError(response.errorBody()?.string(), "Unable to send the reset email.")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Register unverified user (creates account in MongoDB without Firebase UID)
     * User must verify email before account can be fully activated
     * Username and fullName are optional on Step 1
     */
    suspend fun registerUnverified(
        email: String,
        username: String? = null,
        fullName: String? = null,
        phone: String? = null
    ): Result<RegisterUnverifiedResponse> = withContext(Dispatchers.IO) {
        try {
            val request = RegisterUnverifiedRequest(
                email = email,
                username = username,
                fullName = fullName,
                phone = phone ?: ""
            )

            val response = apiService.registerUnverified(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body?.message ?: "Registration failed"))
                }
            } else {
                Result.failure(Exception(parseApiError(response.errorBody()?.string(), "We couldn't create your account.")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if email is verified in Firebase
     */
    suspend fun checkEmailVerified(firebaseUid: String): Result<CheckEmailVerifiedResponse> = withContext(Dispatchers.IO) {
        try {
            val request = CheckEmailVerifiedRequest(firebaseUid = firebaseUid)
            val response = apiService.checkEmailVerified(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Failed to check email verification"))
                }
            } else {
                Result.failure(Exception(parseApiError(response.errorBody()?.string(), "Unable to verify your email yet.")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Finalize account after email is verified
     * Links Firebase UID to unverified user account and activates it
     */
    suspend fun finalizeAccount(firebaseUid: String, email: String): Result<FinalizeAccountResponse> = withContext(Dispatchers.IO) {
        try {
            val request = FinalizeAccountRequest(
                firebaseUid = firebaseUid,
                email = email
            )

            val response = apiService.finalizeAccount(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body)
                } else {
                    Result.failure(Exception(body?.message ?: "Account finalization failed"))
                }
            } else {
                Result.failure(Exception(parseApiError(response.errorBody()?.string(), "We couldn't finish setting up your account.")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
