package com.example.pawsociety.util

import android.util.Log
import com.example.pawsociety.api.ApiClient
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FCMTokenManager {

    private const val TAG = "FCMTokenManager"

    fun initialize(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get FCM token
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d(TAG, "FCM Token: $token")

                // Save token to server
                saveTokenToServer(userId, token)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting FCM token: ${e.message}")
            }
        }
    }

    suspend fun saveTokenToServer(userId: String, token: String) {
        try {
            val apiService = ApiClient.apiService
            val response = apiService.saveFcmToken(userId, mapOf("fcmToken" to token))

            if (response.isSuccessful) {
                Log.d(TAG, "FCM token saved successfully for user: $userId")
            } else {
                Log.e(TAG, "Failed to save FCM token: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving FCM token: ${e.message}")
        }
    }

    fun refreshToken(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                saveTokenToServer(userId, token)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing token: ${e.message}")
            }
        }
    }
}