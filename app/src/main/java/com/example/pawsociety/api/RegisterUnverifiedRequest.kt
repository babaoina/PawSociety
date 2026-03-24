package com.example.pawsociety.api

import com.google.gson.annotations.SerializedName

data class RegisterUnverifiedRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("fullName")
    val fullName: String? = null,
    @SerializedName("phone")
    val phone: String = ""
)

data class RegisterUnverifiedResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("user")
    val user: UnverifiedUserData
)

data class UnverifiedUserData(
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("fullName")
    val fullName: String,
    @SerializedName("emailVerified")
    val emailVerified: Boolean = false
)

data class CheckEmailVerifiedRequest(
    @SerializedName("firebaseUid")
    val firebaseUid: String
)

data class CheckEmailVerifiedResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("emailVerified")
    val emailVerified: Boolean,
    @SerializedName("email")
    val email: String
)

data class FinalizeAccountRequest(
    @SerializedName("firebaseUid")
    val firebaseUid: String,
    @SerializedName("email")
    val email: String
)

data class FinalizeAccountResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("user")
    val user: FinalizedUserData
)

data class FinalizedUserData(
    @SerializedName("firebaseUid")
    val firebaseUid: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("fullName")
    val fullName: String,
    @SerializedName("emailVerified")
    val emailVerified: Boolean = true
)
