package com.example.pawsociety.api

import com.google.gson.annotations.SerializedName

data class SettingsResponse(
    @SerializedName("general") val general: GeneralSettings,
    @SerializedName("security") val security: SecuritySettings,
    @SerializedName("notifications") val notifications: NotificationSettings,
    @SerializedName("moderation") val moderation: ModerationSettings,
    @SerializedName("api") val api: ApiSettings
)

data class GeneralSettings(
    @SerializedName("appName") val appName: String,
    @SerializedName("supportEmail") val supportEmail: String,
    @SerializedName("minVersion") val minVersion: String,
    @SerializedName("maintenanceMode") val maintenanceMode: Boolean,
    @SerializedName("maintenanceMessage") val maintenanceMessage: String,
    @SerializedName("allowRegistration") val allowRegistration: Boolean,
    @SerializedName("emailVerification") val emailVerification: Boolean,
    @SerializedName("phoneVerification") val phoneVerification: Boolean
)

data class SecuritySettings(
    @SerializedName("maxLoginAttempts") val maxLoginAttempts: Int,
    @SerializedName("lockoutDuration") val lockoutDuration: Int,
    @SerializedName("sessionTimeout") val sessionTimeout: Int,
    @SerializedName("admin2FA") val admin2FA: Boolean
)

data class NotificationSettings(
    @SerializedName("pushEnabled") val pushEnabled: Boolean,
    @SerializedName("notificationTypes") val notificationTypes: List<String>,
    @SerializedName("quietStart") val quietStart: String,
    @SerializedName("quietEnd") val quietEnd: String
)

data class ModerationSettings(
    @SerializedName("autoApprove") val autoApprove: Boolean,
    @SerializedName("profanityFilter") val profanityFilter: Boolean,
    @SerializedName("flagThreshold") val flagThreshold: Int,
    @SerializedName("blockedWords") val blockedWords: List<String>
)

data class ApiSettings(
    @SerializedName("apiStatus") val apiStatus: Boolean,
    @SerializedName("rateLimit") val rateLimit: Int,
    @SerializedName("allowedOrigins") val allowedOrigins: List<String>
)