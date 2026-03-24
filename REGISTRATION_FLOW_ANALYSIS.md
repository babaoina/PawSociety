# PawSociety Email Registration Flow Analysis

## Executive Summary

Email-registered users **should** be found in profile lookups because the backend user creation happens **immediately** during the `firebaseLogin()` call. However, there could be race conditions or incomplete registration where users don't reach the `completeRegistration()` step.

---

## 1. EMAIL REGISTRATION FLOW

### Two Registration Paths:
1. **RegisterActivity** - Direct registration (all fields at once)
2. **RegisterWizardActivity** - Multi-step wizard (step-by-step)

---

## 2. DETAILED REGISTRATION STEPS

### Path A: RegisterActivity (Direct Registration)

**File**: [RegisterActivity.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\RegisterActivity.kt)

#### Step 1: Firebase Registration
```kotlin
// Lines 590-600+ in RegisterActivity.kt
val firebaseResult = FirebaseAuthHelper.registerWithEmail(email, password)
val firebaseUser = firebaseResult.getOrNull()!!
```
- Creates account in Firebase Authentication
- Sends email verification
- Returns `firebaseUser.uid`

#### Step 2: Backend User Creation (CRITICAL)
```kotlin
// Lines 600-610+
val backendResult = authRepository.firebaseLogin(
    firebaseUid = firebaseUser.uid,
    email = firebaseUser.email ?: email,
    username = username,
    fullName = fullName,
    phone = phone
)
```

**API Endpoint**: `POST /api/auth/firebase-login`
**Backend File**: [routes/auth.js](file://c:\Users\Mark\Documents\PawSociety--main\backend\routes\auth.js)

**What backend does**:
```javascript
// Lines 54-95
let user = await User.findOne({ firebaseUid });
if (!user) {
  // Create new user immediately
  user = new User({
    firebaseUid,
    email,
    username: finalUsername,
    fullName: finalFullName,
    phone: phone || '',
    profileImageUrl: '',
    bio: '',
    location: ''
  });
  await user.save();
}
```

**User is created fully in database at this point** with:
- ✅ firebaseUid
- ✅ email
- ✅ username
- ✅ fullName
- ✅ phone
- ✅ profileImageUrl (empty)
- ✅ bio (empty)
- ✅ location (empty)

#### Step 3: Save Local Session
```kotlin
sessionManager.saveUserSession(apiUser)
```

#### Step 4: Navigate to HomeActivity
Direct navigation - **Registration is COMPLETE**

---

### Path B: RegisterWizardActivity (Step-by-Step)

**File**: [RegisterWizardActivity.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\RegisterWizardActivity.kt)

#### Step 1: Email/Password Registration
- Same as RegisterActivity Step 1 & 2
- **User is created in backend immediately**
- Generates temporary username if not provided: `email.split("@")[0] + "_temp_XXXX"`

#### Steps 2-6: Collect Profile Data
- Step 2: Welcome (skipped for Google users)
- Step 3: Name (first, last, middle initial)
- Step 4: Username (can override temporary)
- Step 5: Mobile number
- Step 6: Profile photo

#### Final Step: completeRegistration()
**File**: [RegistrationViewModel.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\viewmodels\RegistrationViewModel.kt) (Lines 152-285)

```kotlin
fun completeRegistration() {
    // 1. Upload profile photo if selected
    val uploadResult = uploadRepository.uploadProfilePicture(compressedFile)
    profileImageUrl = uploadResult.getOrNull()
    
    // 2. Update user in backend
    userRepository.updateUser(
        firebaseUid = currentUser.firebaseUid,
        username = usernameValue,      // Can change from temp
        fullName = fullName,            // Formatted full name
        phone = mobileValue,            // Mobile number
        profileImageUrl = profileImageUrl
    )
    
    // 3. Update session
    sessionManager.saveUserSession(updatedUser)
    
    // 4. Initialize Socket.IO and FCM
    SocketManager.connect()
    FCMTokenManager.initialize(currentUser.firebaseUid)
    
    // 5. Navigate to HomeActivity
}
```

**API Endpoint**: `PUT /api/users/{firebaseUid}`
**Backend File**: [routes/users.js](file://c:\Users\Mark\Documents\PawSociety--main\backend\routes\users.js) (Lines 138-166)

```javascript
router.put('/:firebaseUid', async (req, res) => {
  const user = await User.findOneAndUpdate(
    { firebaseUid: req.params.firebaseUid },
    { username, fullName, bio, profileImageUrl, phone, location },
    { new: true, runValidators: true }
  );
  // Updates existing user
});
```

---

## 3. GOOGLE SIGN-IN FLOW

**File**: [RegisterWizardActivity.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\RegisterWizardActivity.kt) (Lines 28-60)

### Key Differences:

1. **Email & Name from Google**:
```kotlin
if (isGoogleSignIn && googleEmail != null) {
    registrationViewModel.setEmail(googleEmail!!)
    // Parse name: "Juan Dela Cruz" -> first and last
    val nameParts = googleName?.split(" ") ?: emptyList()
    registrationViewModel.setFirstName(nameParts[0])
    registrationViewModel.setLastName(nameParts.drop(1).joinToString(" "))
}
```

2. **Skip Email/Password Step**:
```kotlin
// For Google Sign In, skip Step 1 (email/password)
if (isGoogleSignIn) {
    viewPager.currentItem = 1  // Start at Welcome screen
    currentStep = 1
}
```

3. **Rest of flow is identical**:
- Still calls `firebaseLogin()` to create backend user
- Still calls `completeRegistration()` to finalize
- Profile image comes from Google (URL starts with "http")

---

## 4. HOW USER PROFILES ARE RETRIEVED

### UserProfileActivity Flow
**File**: [UserProfileActivity.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\UserProfileActivity.kt) (Lines 270-295)

```kotlin
private fun loadUserData() {
    lifecycleScope.launch {
        try {
            // 1. Look up user by Firebase UID
            val userResult = userRepository.getUserByUid(targetUserId)
            
            if (userResult.isSuccess) {
                targetUser = userResult.getOrNull()
                updateUserUI()  // Display user data
                checkFollowStatus()
                loadFollowersCount()
                loadUserPosts(targetUserId)
                loadUserHighlights()
            } else {
                Toast.makeText(this@UserProfileActivity, "User not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            finish()
        }
    }
}
```

### UserRepository.getUserByUid()
**File**: [UserRepository.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\data\repository\UserRepository.kt) (Lines 95-145)

```kotlin
suspend fun getUserByUid(firebaseUid: String?): Result<ApiUser> {
    // 1. Validate UID
    if (firebaseUid.isNullOrEmpty()) {
        return Result.failure(Exception("Firebase UID cannot be null or empty"))
    }
    
    try {
        // 2. Make API call
        val response = apiService.getUserByUid(firebaseUid)
        
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.success && body.user != null) {
                // 3. Return user if found
                Result.success(body.user)
            } else {
                // 4. Return error if success=false
                Result.failure(Exception(body?.message ?: "User not found"))
            }
        } else {
            // 5. Return error if 404 or other HTTP error
            Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get user"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### API Endpoint: GET /api/users/{firebaseUid}
**Backend File**: [routes/users.js](file://c:\Users\Mark\Documents\PawSociety--main\backend\routes\users.js) (Lines 119-145)

```javascript
router.get('/:firebaseUid', async (req, res) => {
  const { firebaseUid } = req.params;
  
  const user = await User.findOne({ firebaseUid });

  if (!user) {
    // THIS IS THE "USER NOT FOUND" ERROR
    return res.status(404).json({
      success: false,
      message: 'User not found'
    });
  }

  res.json({
    success: true,
    user: {
      firebaseUid: user.firebaseUid,
      username: user.username,
      email: user.email,
      fullName: user.fullName,
      phone: user.phone,
      profileImageUrl: user.profileImageUrl,
      bio: user.bio,
      location: user.location,
      createdAt: user.createdAt
    }
  });
});
```

---

## 5. "USER NOT FOUND" ERROR MESSAGE SOURCE

### Where It Comes From:
**Backend File**: [routes/users.js](file://c:\Users\Mark\Documents\PawSociety--main\backend\routes\users.js) Line 139-143

```javascript
if (!user) {
  console.log(`❌ User not found with UID: ${firebaseUid}`);
  return res.status(404).json({
    success: false,
    message: 'User not found'
  });
}
```

### Why Email-Registered Users Might Not Be Found:

1. **Incomplete Registration** - User didn't complete the wizard steps
   - Registered via `firebaseLogin()` in RegisterWizardActivity Step 1
   - But **never** called `completeRegistration()`
   - User exists in database but with minimal data

2. **Race Condition** - Registration in progress
   - `firebaseLogin()` hasn't completed yet
   - Another user tries to view profile immediately
   - Backend lookup happens before user is saved

3. **Failed Backend Call** - Network error during registration
   - Firebase registration succeeded
   - `firebaseLogin()` API call failed
   - User created only local session, not in MongoDB

   From [RegisterActivity.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\RegisterActivity.kt) (Lines 615-640):
   ```kotlin
   if (backendResult.isFailure) {
       // Backend API failed!
       val localUser = com.example.pawsociety.api.ApiUser(...)
       sessionManager.saveUserSession(localUser)
       // ⚠️ User is NOT in MongoDB!
   }
   ```

4. **Wrong Firebase UID** - UID doesn't match what was saved
   - User created account with UID "abc123"
   - Later looks up profile with UID "def456"
   - No matching record in database

5. **Database Record Deleted** - User was deleted
   - User completed registration
   - Later some process deleted the user record
   - Profile lookup returns 404

---

## 6. KEY DIFFERENCES: Email vs Google Registration

| Aspect | Email Registration | Google Registration |
|--------|-------------------|----------------------|
| **Email** | User enters | From Google OAuth |
| **Password** | User enters | Not needed (OAuth) |
| **Flow** | RegisterActivity OR RegisterWizardActivity | RegisterWizardActivity only |
| **Start Step** | Step 0 (email/password) | Step 1 (welcome) - skips password |
| **Name Entry** | User enters first/last name | Google provides, parsed |
| **Profile Photo** | User selects or skips | Can use Google photo URL |
| **Backend Creation Time** | Immediately when firebaseLogin() called | Immediately when firebaseLogin() called |
| **User in MongoDB** | Yes, from Step 1 | Yes, from Step 1 |
| **Profile Update** | Via completeRegistration() | Via completeRegistration() |

---

## 7. BACKEND DATA FLOW TIMELINE

### Scenario: Email Registration via RegisterActivity

```
Time 1: User enters email/password/username/name/phone
        └→ Click "Create Account"
        
Time 2: Firebase registration
        └→ Firebase creates account
        └→ Returns firebaseUid
        
Time 3: Backend firebaseLogin() call
        └→ MongoDB User.findOne({ firebaseUid })
        └→ Not found, so User.save() creates:
           {
             firebaseUid: "abc123",
             email: "user@gmail.com",
             username: "myusername",
             fullName: "John Doe",
             phone: "09123456789",
             profileImageUrl: "",
             bio: "",
             location: ""
           }
        └→ Return ApiUser to Android
        
Time 4: Android saves session, navigates to HomeActivity
        └→ ✅ USER IS FULLY CREATED IN BACKEND
        └→ Can be found immediately via getUserByUid()
```

### Scenario: Email Registration via RegisterWizardActivity

```
Time 1: User enters email/password
        └→ Click "Continue"
        
Time 2: Firebase registration + firebaseLogin()
        └→ User created in MongoDB (WITH TEMPORARY USERNAME)
           {
             firebaseUid: "abc123",
             email: "user@gmail.com",
             username: "user_temp_x1y2",  ← TEMPORARY
             fullName: "",
             phone: "",
             profileImageUrl: ""
           }
        
Time 3-5: User goes through wizard steps
          └→ Step 3: Enters name → Stored in RegistrationViewModel
          └→ Step 4: Chooses username → Stored in RegistrationViewModel
          └→ Step 5: Enters phone → Stored in RegistrationViewModel
          └→ Step 6: Selects photo → Stored in RegistrationViewModel
          
          ℹ️ DATABASE NOT UPDATED YET - Only ViewModel in memory!
        
Time 6: completeRegistration() called
        └→ Upload photo if selected
        └→ updateUser() API call:
           PUT /api/users/{firebaseUid}
           {
             username: "myusername",  ← OVERRIDE TEMP
             fullName: "Doe, John",
             phone: "09123456789",
             profileImageUrl: "https://..."
           }
        └→ MongoDB updated
        └→ ✅ USER IS NOW COMPLETE
        
Time 7: Navigate to HomeActivity
```

---

## 8. WHY EMAIL-REGISTERED USERS MIGHT NOT BE FOUND

### Most Likely Cause: Incomplete Wizard Registration

If a user:
1. Completes Step 1 (email/password) ✅ → User created in MongoDB
2. Goes through Steps 3-5 ⏳ → Data stored in ViewModel only
3. **Closes app / crashes / loses connection** ❌
4. Never reaches Step 6 / completeRegistration()
5. Later another user tries to view their profile

**Result**: User exists in MongoDB but with temporary username and no phone/profile data

### Solution: Check Database for Users with Temporary Usernames

```javascript
// Find incomplete registrations
db.users.find({ username: /.*_temp_.*/ })
```

---

## 9. RECOMMENDATION: Audit Trail

Add logging to detect incomplete registrations:

1. **After firebaseLogin()** - Log user creation
2. **In completeRegistration()** - Log profile completion
3. **On getUserByUid()** - Log timestamp when user is found
4. **On errors** - Log all failures

This will show:
- Users created but never completed wizard
- Users with temporary usernames still active
- Firebase → MongoDB sync failures

---

## Summary Table

| File | Method | Purpose |
|------|--------|---------|
| [RegisterActivity.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\RegisterActivity.kt) | `createAccount()` | Direct email registration |
| [RegisterWizardActivity.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\RegisterWizardActivity.kt) | Multi-step fragment | Step-by-step registration |
| [RegistrationViewModel.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\viewmodels\RegistrationViewModel.kt) | `completeRegistration()` | Finalize wizard registration |
| [AuthRepository.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\data\repository\AuthRepository.kt) | `firebaseLogin()` | Call POST /auth/firebase-login |
| [UserRepository.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\data\repository\UserRepository.kt) | `getUserByUid()` | Call GET /users/{firebaseUid} |
| [routes/auth.js](file://c:\Users\Mark\Documents\PawSociety--main\backend\routes\auth.js) | POST /auth/firebase-login | Create user in MongoDB |
| [routes/users.js](file://c:\Users\Mark\Documents\PawSociety--main\backend\routes\users.js) | GET /users/{firebaseUid} | Lookup user by UID |
| [UserProfileActivity.kt](file://c:\Users\Mark\Documents\PawSociety--main\app\src\main\java\com\example\pawsociety\UserProfileActivity.kt) | `loadUserData()` | Fetch and display user |
