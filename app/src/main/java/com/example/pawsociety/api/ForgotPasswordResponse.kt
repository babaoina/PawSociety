package com.example.pawsociety.api

/**
 * Response body for POST /api/auth/forgot-password
 */
data class ForgotPasswordResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val emailSent: Boolean = false
)
