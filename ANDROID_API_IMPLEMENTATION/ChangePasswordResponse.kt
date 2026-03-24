package com.example.pawsociety.api

/**
 * Response body for POST /api/settings/change-password
 */
data class ChangePasswordResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)
