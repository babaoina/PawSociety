package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {
    
    private val apiService = ApiClient.apiService
    
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
                Result.failure(Exception(response.errorBody()?.string() ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
