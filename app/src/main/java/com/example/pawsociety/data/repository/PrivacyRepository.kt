package com.example.pawsociety.data.repository

import com.example.pawsociety.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrivacyRepository {

    private val apiService = ApiClient.apiService

    suspend fun getPrivateAccountSetting(firebaseUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPrivacySettings(firebaseUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body.settings?.privateAccount ?: false)
                } else {
                    Result.failure(Exception(body?.error ?: body?.message ?: "Failed to load privacy settings"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to load privacy settings"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePrivateAccountSetting(firebaseUid: String, privateAccount: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.updatePrivacySettings(
                    mapOf(
                        "firebaseUid" to firebaseUid,
                        "privateAccount" to privateAccount
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception(body?.error ?: body?.message ?: "Failed to update privacy settings"))
                    }
                } else {
                    Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update privacy settings"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

