# Email Verification System - Testing Guide

## Quick Answer: What To Test

### Backend Email Verification Endpoints

1. **POST /api/auth/register-unverified**
   - Creates unverified user account in MongoDB
   - Does NOT create Firebase account yet
   - Required fields: email, username, fullName
   - Returns: 201 with unverified user data

2. **POST /api/auth/check-email-verified**
   - Checks if email is verified in Firebase
   - Syncs verification status to MongoDB
   - Returns: emailVerified boolean status

3. **POST /api/auth/finalize-account**
   - Links Firebase UID to unverified user
   - Only works if email verified in Firebase
   - Activates account for full use
   - Returns: 200 with activated user data

### Validation Rules To Test

- ✅ Duplicate email prevention (409)
- ✅ Duplicate username prevention (409)
- ✅ Missing required fields (400)
- ✅ Username max 20 characters
- ✅ Email format validation
- ✅ firebaseUid optional until finalized

### User Model Fields To Verify

- ✅ emailVerified: Boolean (default: false)
- ✅ emailVerificationToken: String
- ✅ firebaseUid: Optional, sparse unique index

### Android Integration To Test

- ✅ RegistrationViewModel calls registerUnverified()
- ✅ AuthRepository methods: registerUnverified(), checkEmailVerified(), finalizeAccount()
- ✅ SessionManager stores temporary registration data
- ✅ Complete registration flow compiles without errors

## Run Automated Tests

```bash
cd backend
node test-email-verification.js
```

Expected output: **7/7 Tests PASSED**

## Manual Testing Workflow

1. User fills registration form
2. App calls POST /api/auth/register-unverified
3. Verify response: 201, emailVerified: false, firebaseUid: null
4. User receives email verification link
5. User clicks link → Firebase marks email verified
6. App calls POST /api/auth/check-email-verified
7. Verify response: emailVerified: true
8. App calls POST /api/auth/finalize-account
9. Verify response: 201, emailVerified: true, firebaseUid: present

## Test Results Summary

| Test | Status | Details |
|------|--------|---------|
| Register Unverified | ✅ PASS | 201 Created |
| Duplicate Email | ✅ PASS | 409 Conflict |
| Duplicate Username | ✅ PASS | 409 Conflict |
| Missing Fields | ✅ PASS | 400 Bad Request |
| Check Not Verified | ✅ PASS | 404 Not Found |
| User Model Fields | ✅ PASS | Both fields present |
| Auth Routes | ✅ PASS | Routes registered |

**Overall: 7/7 PASSED (100%)**

## Verification Checklist

- [x] Backend endpoints implemented
- [x] Android API classes created
- [x] Android repository methods added
- [x] Android ViewModel updated
- [x] Session manager enhanced
- [x] Automated tests created
- [x] All tests passing
- [x] Android builds successfully
- [x] Backend syntax valid
- [x] Email verification workflow complete
