package com.example.pawsociety.data.repository

import com.example.pawsociety.api.ApiClient
import com.example.pawsociety.api.SettingsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsRepository {

    private val apiService = ApiClient.apiService

    // Cache settings to avoid too many API calls
    private var cachedSettings: SettingsResponse? = null
    private var lastFetchTime: Long = 0
    private val CACHE_DURATION = 5 * 60 * 1000 // 5 minutes

    suspend fun getSettings(forceRefresh: Boolean = false): Result<SettingsResponse> = withContext(Dispatchers.IO) {
        try {
            // Return cached settings if still valid
            if (!forceRefresh && cachedSettings != null && System.currentTimeMillis() - lastFetchTime < CACHE_DURATION) {
                return@withContext Result.success(cachedSettings!!)
            }

            val response = apiService.getSettings()

            if (response.isSuccessful) {
                val settings = response.body()
                if (settings != null) {
                    // Update cache
                    cachedSettings = settings
                    lastFetchTime = System.currentTimeMillis()
                    Result.success(settings)
                } else {
                    Result.failure(Exception("Empty response"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to fetch settings"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    // Add this function if it doesn't exist
    suspend fun isMaintenanceMode(): Boolean {
        return try {
            val result = getSettings(forceRefresh = true) // Force refresh to get latest
            if (result.isSuccess) {
                result.getOrNull()?.general?.maintenanceMode ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // Get maintenance message
    suspend fun getMaintenanceMessage(): String {
        return try {
            val result = getSettings()
            if (result.isSuccess) {
                result.getOrNull()?.general?.maintenanceMessage ?: "App is under maintenance. Please try again later."
            } else {
                "App is under maintenance. Please try again later."
            }
        } catch (e: Exception) {
            "App is under maintenance. Please try again later."
        }
    }

    // Check if registration is allowed
    suspend fun isRegistrationAllowed(): Boolean {
        return try {
            val result = getSettings()
            if (result.isSuccess) {
                result.getOrNull()?.general?.allowRegistration ?: true
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    // Check if push notifications are enabled
    suspend fun arePushNotificationsEnabled(): Boolean {
        return try {
            val result = getSettings()
            if (result.isSuccess) {
                result.getOrNull()?.notifications?.pushEnabled ?: true
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }
}