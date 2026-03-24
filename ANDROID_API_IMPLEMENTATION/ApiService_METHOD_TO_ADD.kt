// ADD THIS METHOD TO YOUR EXISTING ApiService.kt FILE

/**
 * Change user password
 * POST /api/settings/change-password
 * 
 * Usage:
 * val request = ChangePasswordRequest("firebase_uid", "oldPass", "newPass")
 * val response = apiService.changePassword(request)
 */
@POST("api/settings/change-password")
suspend fun changePassword(
    @Body request: ChangePasswordRequest
): Response<ChangePasswordResponse>
