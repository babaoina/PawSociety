package com.example.pawsociety.util

import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility for security and session management
 */
object SecurityUtil {

    data class DeviceInfo(
        val phoneModel: String = "",
        val osVersion: String = "",
        val deviceName: String = "",
        val manufacturer: String = "",
        val lastActive: String = "",
        val sessionToken: String = ""
    )

    /**
     * Get current device information
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            phoneModel = Build.MODEL,                    // e.g., "SM-G950F" for Samsung Galaxy S8
            osVersion = "Android ${Build.VERSION.RELEASE}",  // e.g., "Android 12"
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}", // e.g., "Samsung SM-G950F"
            manufacturer = Build.MANUFACTURER,            // e.g., "Samsung"
            lastActive = getCurrentTimestamp(),
            sessionToken = generateSessionToken()
        )
    }

    /**
     * Get human-readable device name
     */
    fun getDeviceDisplayName(): String {
        return when {
            Build.MANUFACTURER.equals("samsung", ignoreCase = true) -> 
                "Samsung ${Build.MODEL} - Android ${Build.VERSION.RELEASE}"
            Build.MANUFACTURER.equals("google", ignoreCase = true) -> 
                "Google Pixel ${Build.MODEL} - Android ${Build.VERSION.RELEASE}"
            Build.MANUFACTURER.equals("oneplus", ignoreCase = true) -> 
                "OnePlus ${Build.MODEL} - Android ${Build.VERSION.RELEASE}"
            Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) -> 
                "Xiaomi ${Build.MODEL} - Android ${Build.VERSION.RELEASE}"
            else -> 
                "${Build.MANUFACTURER} ${Build.MODEL} - Android ${Build.VERSION.RELEASE}"
        }
    }

    /**
     * Generate unique session token
     */
    private fun generateSessionToken(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Get current timestamp
     */
    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * Convert DeviceInfo to JSON for sending to server
     */
    fun deviceInfoToJson(deviceInfo: DeviceInfo): JSONObject {
        return JSONObject().apply {
            put("phoneModel", deviceInfo.phoneModel)
            put("osVersion", deviceInfo.osVersion)
            put("deviceName", deviceInfo.deviceName)
            put("manufacturer", deviceInfo.manufacturer)
            put("lastActive", deviceInfo.lastActive)
            put("sessionToken", deviceInfo.sessionToken)
        }
    }

    /**
     * Format time for login information display
     * E.g., "Today at 2:30 PM", "Yesterday at 5:15 AM", "Dec 25, 2024 at 3:20 PM"
     */
    fun formatLoginTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        val nowCalendar = Calendar.getInstance()

        val diffInDays = (now - timestamp) / (24 * 60 * 60 * 1000)

        return when {
            diffInDays == 0L -> {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
                "Today at $time"
            }
            diffInDays == 1L -> {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
                "Yesterday at $time"
            }
            else -> {
                SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}
