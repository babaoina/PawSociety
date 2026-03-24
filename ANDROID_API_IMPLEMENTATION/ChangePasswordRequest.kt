package com.example.pawsociety.api

/**
 * Request body for POST /api/settings/change-password
 */
data class ChangePasswordRequest(
    val firebaseUid: String,
    val oldPassword: String,
    val newPassword: String
)
