// ADD THIS METHOD TO YOUR EXISTING UserRepository.kt FILE

import com.example.pawsociety.api.ChangePasswordRequest
import com.example.pawsociety.api.ChangePasswordResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Change user's password
 * Calls: POST /api/settings/change-password
 * 
 * @param firebaseUid User's Firebase UID
 * @param oldPassword Current password (for verification)
 * @param newPassword New password to set
 * @return Result containing ChangePasswordResponse on success
 * 
 * Usage:
 * val result = userRepository.changePassword(firebaseUid, "oldPass", "newPass")
 * if (result.isSuccess) {
 *     showMessage("Password changed successfully")
 * } else {
 *     showError(result.exceptionOrNull()?.message)
 * }
 */
suspend fun changePassword(
    firebaseUid: String,
    oldPassword: String,
    newPassword: String
): Result<ChangePasswordResponse> = withContext(Dispatchers.IO) {
    try {
        // Create request object
        val request = ChangePasswordRequest(
            firebaseUid = firebaseUid,
            oldPassword = oldPassword,
            newPassword = newPassword
        )

        // Call the API endpoint
        val response = apiService.changePassword(request)

        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.success) {
                // Return success with the response data
                Result.success(body)
            } else {
                // Backend returned success=false with error message
                Result.failure(Exception(body?.error ?: "Failed to change password"))
            }
        } else {
            // Handle HTTP errors
            val errorMessage = response.errorBody()?.string() ?: "Failed to change password"
            try {
                // Try to parse JSON error response
                val errorJson = JSONObject(errorMessage)
                val error = errorJson.optString("error", "Failed to change password")
                Result.failure(Exception(error))
            } catch (e: Exception) {
                // Fallback if not JSON
                Result.failure(Exception(errorMessage))
            }
        }
    } catch (e: Exception) {
        // Network or other exceptions
        Result.failure(e)
    }
}
