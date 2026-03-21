package com.example.pawsociety.data.repository

import com.example.pawsociety.api.ApiClient
import com.example.pawsociety.api.SettingsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsRepository {

    // 🔥 CHANGED: Use public API service (no auth needed)
    private val publicApiService = ApiClient.publicApiService

    // Cache settings to avoid too many API calls
    private var cachedSettings: SettingsResponse? = null
    private var lastFetchTime: Long = 0
    private val CACHE_DURATION = 5 * 60 * 1000 // 5 minutes

    // 🔥 NEW: Get public settings (no auth required)
    suspend fun getPublicSettings(forceRefresh: Boolean = false): Result<SettingsResponse> = withContext(Dispatchers.IO) {
        try {
            // Return cached settings if still valid
            if (!forceRefresh && cachedSettings != null && System.currentTimeMillis() - lastFetchTime < CACHE_DURATION) {
                println("📦 Using cached settings")
                return@withContext Result.success(cachedSettings!!)
            }

            println("📡 Fetching public settings from server...")
            val response = publicApiService.getPublicSettings()

            println("📥 Settings API response code: ${response.code()}")

            if (response.isSuccessful) {
                val settings = response.body()
                println("📦 Settings response: $settings")

                if (settings != null) {
                    // Update cache
                    cachedSettings = settings
                    lastFetchTime = System.currentTimeMillis()

                    // Log specific values
                    println("🔧 Maintenance Mode: ${settings.general?.maintenanceMode}")
                    println("🔧 App Name: ${settings.general?.appName}")
                    println("🔧 Allow Registration: ${settings.general?.allowRegistration}")

                    Result.success(settings)
                } else {
                    println("❌ Settings body is null")
                    Result.failure(Exception("Empty response"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Settings error ${response.code()}: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to fetch settings"))
            }
        } catch (e: Exception) {
            println("❌ Settings exception: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // 🔥 UPDATED: Use public settings
    suspend fun isMaintenanceMode(): Boolean {
        return try {
            println("🔍 Checking maintenance mode...")
            val result = getPublicSettings(forceRefresh = true)
            val isMaintenance = if (result.isSuccess) {
                result.getOrNull()?.general?.maintenanceMode ?: false
            } else {
                false
            }
            println("🚦 Maintenance mode: $isMaintenance")
            isMaintenance
        } catch (e: Exception) {
            println("❌ Error checking maintenance: ${e.message}")
            false
        }
    }

    // 🔥 UPDATED: Use public settings
    suspend fun getMaintenanceMessage(): String {
        return try {
            val result = getPublicSettings()
            val message = if (result.isSuccess) {
                result.getOrNull()?.general?.maintenanceMessage ?: "App is under maintenance. Please try again later."
            } else {
                "App is under maintenance. Please try again later."
            }
            println("📝 Maintenance message: $message")
            message
        } catch (e: Exception) {
            println("❌ Error getting maintenance message: ${e.message}")
            "App is under maintenance. Please try again later."
        }
    }

    // 🔥 UPDATED: Use public settings
    suspend fun isRegistrationAllowed(): Boolean {
        return try {
            val result = getPublicSettings()
            val allowed = if (result.isSuccess) {
                result.getOrNull()?.general?.allowRegistration ?: true
            } else {
                true
            }
            println("🔓 Registration allowed: $allowed")
            allowed
        } catch (e: Exception) {
            println("❌ Error checking registration: ${e.message}")
            true
        }
    }

    // 🔥 UPDATED: Use public settings
    suspend fun arePushNotificationsEnabled(): Boolean {
        return try {
            val result = getPublicSettings()
            val enabled = if (result.isSuccess) {
                result.getOrNull()?.notifications?.pushEnabled ?: true
            } else {
                true
            }
            println("🔔 Push notifications enabled: $enabled")
            enabled
        } catch (e: Exception) {
            println("❌ Error checking notifications: ${e.message}")
            true
        }
    }

    // 🔥 UPDATED: Use public settings
    suspend fun getAppName(): String {
        return try {
            val result = getPublicSettings()
            val appName = if (result.isSuccess) {
                result.getOrNull()?.general?.appName ?: "PawSociety"
            } else {
                "PawSociety"
            }
            println("📱 App name: $appName")
            appName
        } catch (e: Exception) {
            println("❌ Error getting app name: ${e.message}")
            "PawSociety"
        }
    }

    // 🔥 UPDATED: Use public settings
    suspend fun getSupportEmail(): String {
        return try {
            val result = getPublicSettings()
            val email = if (result.isSuccess) {
                result.getOrNull()?.general?.supportEmail ?: "support@pawsociety.com"
            } else {
                "support@pawsociety.com"
            }
            println("📧 Support email: $email")
            email
        } catch (e: Exception) {
            println("❌ Error getting support email: ${e.message}")
            "support@pawsociety.com"
        }
    }
}