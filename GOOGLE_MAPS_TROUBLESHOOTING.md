# Google Maps Blank Loading Issue - Troubleshooting Guide

**Issue**: Map shows "Loading..." but never displays (stays blank with Google logo visible)

**APK Build**: 3/23/2026 5:56:26 AM ✅

---

## 🔍 Why This Happens

The blank/loading map is **95% cause: Invalid or improperly configured Google Maps API Key**

The UI loads (header, buttons, compass, controls visible) but map tiles don't render.

---

## ✅ Solution: Fix API Key Configuration

### **Step 1: Verify API Key is Correct**

The API key `AIzaSyA8CoDD1fBOCOB40tifAJjJuIwKJAjp-bo` is already in your `AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="AIzaSyA8CoDD1fBOCOB40tifAJjJuIwKJAjp-bo" />
```

✅ **Key is present** - but it may need configuration in Google Cloud

---

### **Step 2: Get Your App's SHA-1 Certificate Fingerprint**

The API key must be restricted to your specific app using the SHA-1 fingerprint.

Run this command in PowerShell:

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android | Select-String "SHA1"
```

**Output example**:
```
SHA1: AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD
```

📝 **Copy this value - you'll need it next**

---

### **Step 3: Go to Google Cloud Console**

1. Open: https://console.cloud.google.com/
2. Make sure you're logged in with the Google account that owns your project
3. Select your project (should appear in the dropdown at top)

---

### **Step 4: Find Your API Key**

1. Go to **APIs & Services** → **Credentials** (left sidebar)
2. Under "API keys" section, find: `AIzaSyA8CoDD1fBOCOB40tifAJjJuIwKJAjp-bo`
3. Click on it to open the details

---

### **Step 5: Check Application Restrictions**

In the API key details page:

**Look for "Application restrictions" section**:

1. If it says **"None"** or **"IP addresses"**:
   - Click **Edit** (pencil icon)
   - Change from "API restrictions" dropdown to **"Android apps"**

2. Click **"Add"** button

3. Fill in:
   - **Package name**: `com.example.pawsociety`
   - **SHA-1 certificate fingerprint**: (Paste the SHA1 from Step 2)

4. Click **Done**

5. Click **Save**

---

### **Step 6: Verify Maps SDK for Android is Enabled**

1. Go to **APIs & Services** → **Enabled APIs and services** (left sidebar)
2. Search for **"Maps SDK for Android"**
3. It should show **ENABLED**
4. If not found or disabled:
   - Search for it
   - Click **Enable**

---

### **Step 7: Wait for Changes to Propagate**

Changes can take **5-15 minutes** to take effect.

Then test the app again.

---

## 🐛 Debug Logs (New)

The updated APK now includes detailed logging. When you run the app:

### **Check Android Logcat**

1. In Android Studio:
   - Open **Logcat** tab (bottom)
   - Filter: `MapDebug`

2. You should see messages like:
   ```
   ✓ onMapReady called - map object created
   ✓ Camera positioned at: LatLng(lat=14.5995, lng=120.9842)
   ✓ My location enabled
   ✓ Map tiles loaded successfully!
   ```

### **If You See Error Messages**

Example errors and fixes:

| Error | Fix |
|-------|-----|
| `Map tiles loaded successfully!` but blank | API key restrictions wrong |
| `My location enabled` but blank | Maps SDK for Android not enabled |
| Permission warning | Grant location permission |

---

## ⚙️ Alternative: Production Release Build

If testing with debug APK doesn't work, you may need to:

1. Generate a **release keystore** for production
2. Get the **release SHA-1** fingerprint
3. Add it to the API key in Google Cloud (same steps as above)
4. Build release APK with that keystore

But start with the debug APK troubleshooting above first.

---

## 📋 Checklist

- [ ] API key copied to Manifest? (`AIzaSyA8CoDD1fBOCOB40tifAJjJuIwKJAjp-bo`)
- [ ] SHA-1 certificate fingerprint obtained?
- [ ] API key configured with Android app restrictions?
- [ ] Package name set to `com.example.pawsociety`?
- [ ] SHA-1 added to API key restrictions?
- [ ] Maps SDK for Android enabled?
- [ ] Waited 5+ minutes after changes?
- [ ] Checked Logcat filter `MapDebug` for messages?

---

## 🆘 Still Not Working?

If the map still doesn't load after all steps:

1. **Verify the API key itself is valid**:
   - Try it in a web browser: https://maps.googleapis.com/maps/api/js?key=AIzaSyA8CoDD1fBOCOB40tifAJjJuIwKJAjp-bo
   - Should NOT show "Invalid API key" error

2. **Check billing is enabled**:
   - Google Cloud → Billing → Make sure project has billing account linked
   - Maps API requires billing enabled (free tier available)

3. **Check API quota**:
   - APIs & Services → Quotas
   - Look for "Maps SDK for Android"
   - Make sure quota is not exhausted

4. **Create a new API key**:
   - If all else fails, create a new key in Google Cloud
   - Add proper restrictions
   - Update Manifest

---

## 📚 Documentation

- Google Maps Android SDK: https://developers.google.com/maps/documentation/android-sdk
- Getting API Key: https://developers.google.com/maps/documentation/android-sdk/get-api-key
- Digital Asset Links: https://developers.google.com/digital-asset-links

---

**APK Status**: ✅ Successfully built with enhanced logging  
**Current Build**: 75.85 MB | 3/23/2026 5:56:26 AM
