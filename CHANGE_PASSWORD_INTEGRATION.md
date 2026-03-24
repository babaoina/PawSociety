# Change Password Feature - Complete Integration Guide

## ✅ What's Been Done

### 1. Backend
- ✅ **Endpoint Created**: `POST /api/settings/change-password`
- ✅ **Location**: `routes/user-settings.js`
- ✅ **Validation**: Old password verification, new password strength check
- ✅ **Firebase Integration**: Updates password in Firebase Auth

### 2. Android Frontend
- ✅ **SecurityActivity.kt Updated**: Now has full `changePassword()` method with API calls
- ✅ **Error Handling**: Shows proper error messages for all cases
- ✅ **Real-time Sync**: Emits Socket.io event after password change
- ✅ **UI/UX**: Progress bar, button disabling during API call

---

## 📋 What Needs to Be Done (Android Developer Tasks)

### Step 1: Add changePassword Method to UserRepository

In your `app/src/main/java/com/example/pawsociety/data/repository/UserRepository.kt`, add this method:

```kotlin
/**
 * Change user's password
 * Calls: POST /api/settings/change-password
 */
suspend fun changePassword(
    firebaseUid: String,
    oldPassword: String,
    newPassword: String
): Result<ChangePasswordResponse> = withContext(Dispatchers.IO) {
    try {
        val request = ChangePasswordRequest(
            firebaseUid = firebaseUid,
            oldPassword = oldPassword,
            newPassword = newPassword
        )

        val response = apiService.changePassword(request)

        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.success) {
                Result.success(body)
            } else {
                Result.failure(Exception(body?.error ?: "Failed to change password"))
            }
        } else {
            val errorMessage = response.errorBody()?.string() ?: "Failed to change password"
            // Parse error JSON to get actual error message
            try {
                val errorJson = JSONObject(errorMessage)
                val error = errorJson.optString("error", "Failed to change password")
                Result.failure(Exception(error))
            } catch (e: Exception) {
                Result.failure(Exception(errorMessage))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### Step 2: Add API Request/Response Classes

In your `app/src/main/java/com/example/pawsociety/api/` folder, add these data classes:

```kotlin
// ChangePasswordRequest.kt
data class ChangePasswordRequest(
    val firebaseUid: String,
    val oldPassword: String,
    val newPassword: String
)

// ChangePasswordResponse.kt
data class ChangePasswordResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)
```

### Step 3: Add API Method to ApiService

In your `app/src/main/java/com/example/pawsociety/api/ApiService.kt`, add:

```kotlin
@POST("api/settings/change-password")
suspend fun changePassword(
    @Body request: ChangePasswordRequest
): Response<ChangePasswordResponse>
```

### Step 4: Ensure SecurityActivity is Called from Settings

In `SettingsActivity.kt`, make sure clicking "Change Password" launches `SecurityActivity`:

```kotlin
// In SettingsActivity.kt
btnChangePassword.setOnClickListener {
    startActivity(Intent(this, SecurityActivity::class.java))
}
```

---

## 🧪 Testing

### Backend Test
```bash
curl -X POST http://localhost:8000/api/settings/change-password \
  -H "Content-Type: application/json" \
  -d '{
    "firebaseUid": "test_uid_123",
    "oldPassword": "oldPass123",
    "newPassword": "newPass456"
  }'
```

**Success Response**:
```json
{
  "success": true,
  "message": "Password changed successfully"
}
```

**Error Response**:
```json
{
  "success": false,
  "error": "Invalid current password"
}
```

### Android Test
1. Open Settings → Security
2. Click "Change Password"
3. Enter:
   - Current Password: Your current Firebase password
   - New Password: New password (6+ chars, different from current)
   - Confirm Password: Same as new password
4. Click "Change"
5. Should see "Password changed successfully" toast
6. Try logging in with new password on login screen

---

## 🔐 Error Cases Handled

| Scenario | Error Message |
|----------|--------------|
| Missing fields | "All fields are required" |
| Passwords don't match | "New passwords don't match" |
| Password too short | "Password must be at least 6 characters" |
| Same as old password | "New password must be different from current password" |
| Invalid old password | "Invalid current password" |
| Firebase error | "Error: [Firebase error message]" |

---

## 🔄 Flow Diagram

```
User Opens Settings
    ↓
Clicks "Change Password"
    ↓
SecurityActivity → showChangePasswordDialog()
    ↓
User enters old/new/confirm passwords
    ↓
Validation checks (length, match, different)
    ↓
[PASS] → Show progress bar, disable inputs
    ↓
UserRepository.changePassword()
    ↓
ApiService.changePassword(request)
    ↓
Backend: /api/settings/change-password
    ↓
Firebase: confirmPasswordReset()
    ↓
[SUCCESS] → Toast "Password changed successfully"
           → Socket.io emit "security-settings-updated"
           → Close dialog
    ↓
[ERROR] → Toast error message
        → Keep dialog open for retry
```

---

## 📝 Notes

- ✅ Backend endpoint is **READY** to receive requests
- ✅ SecurityActivity UI is **READY** with API calling logic
- ⏳ Android API layer needs the 3 classes/methods mentioned above
- 🔒 Passwords are always sent over HTTPS (ensure your ApiClient uses https://your-backend-url)
- 📱 Real-time sync enabled via Socket.io for multi-device logout

---

## 🚀 Next Steps

1. Add the three items from "What Needs to Be Done" section
2. Rebuild and test on Android emulator/device
3. Verify logs show API calls being made
4. Test with incorrect old password (should fail)
5. Test with valid old password (should succeed)
6. Verify new password works for login on LoginActivity

**Backend is fully integrated and waiting for Android to connect!** ✅
