const express = require('express');
const router = express.Router();
const User = require('../models/User');
const admin = require('firebase-admin');
const axios = require('axios');

const SECURITY_CHANGE_COOLDOWN_MS = 30 * 24 * 60 * 60 * 1000;

function getCooldownRemainingMs(lastChangedAt) {
  if (!lastChangedAt) return 0;
  const timestamp = new Date(lastChangedAt).getTime();
  if (Number.isNaN(timestamp)) return 0;
  return Math.max(0, timestamp + SECURITY_CHANGE_COOLDOWN_MS - Date.now());
}

function formatCooldownRemaining(remainingMs) {
  const totalHours = Math.ceil(remainingMs / (60 * 60 * 1000));
  const days = Math.floor(totalHours / 24);
  const hours = totalHours % 24;

  if (days > 0) {
    return `${days} day${days === 1 ? '' : 's'}${hours > 0 ? ` and ${hours} hour${hours === 1 ? '' : 's'}` : ''}`;
  }

  if (hours > 0) {
    return `${hours} hour${hours === 1 ? '' : 's'}`;
  }

  return 'less than 1 hour';
}

// ===== GET USER NOTIFICATION SETTINGS =====
router.get('/notifications/:firebaseUid', async (req, res) => {
  try {
    const { firebaseUid } = req.params;

    if (!firebaseUid) {
      return res.status(400).json({ error: 'firebaseUid is required' });
    }

    const user = await User.findOne({ firebaseUid });

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    console.log(`📥 Retrieved notification settings for user: ${firebaseUid}`);
    
    // Map settings to match Android app keys
    const settings = user.notificationSettings || {
      likes: true,
      comments: true,
      follows: true,
      messages: true,
      post_reminders: false
    };
    
    // Convert database keys to app keys if needed
    const appSettings = {
      likes: settings.postsLikes !== undefined ? settings.postsLikes : settings.likes !== undefined ? settings.likes : true,
      comments: settings.postComments !== undefined ? settings.postComments : settings.comments !== undefined ? settings.comments : true,
      follows: settings.follows || true,
      messages: settings.messages || true,
      post_reminders: settings.highlightedPosts !== undefined ? settings.highlightedPosts : settings.post_reminders || false
    };
    
    res.json({
      success: true,
      settings: appSettings
    });
  } catch (error) {
    console.error('❌ Error fetching notification settings:', error);
    res.status(500).json({ error: error.message });
  }
});

// ===== UPDATE SINGLE NOTIFICATION SETTING =====
router.post('/update', async (req, res) => {
  try {
    const { firebaseUid, settingKey, value } = req.body;

    if (!firebaseUid || !settingKey) {
      return res.status(400).json({ 
        error: 'firebaseUid and settingKey are required' 
      });
    }

    // Validate settingKey
    const validSettings = [
      'postsLikes',
      'postComments',
      'follows',
      'messages',
      'highlightedPosts',
      'announcements',
      'likes',
      'comments'
    ];

    if (!validSettings.includes(settingKey)) {
      return res.status(400).json({ 
        error: `Invalid setting key: ${settingKey}` 
      });
    }

    // Map short names to full names
    const settingMap = {
      'likes': 'postsLikes',
      'comments': 'postComments'
    };

    const actualKey = settingMap[settingKey] || settingKey;

    const user = await User.findOne({ firebaseUid });

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Initialize notificationSettings if not present
    if (!user.notificationSettings) {
      user.notificationSettings = {
        postsLikes: true,
        postComments: true,
        follows: true,
        messages: true,
        highlightedPosts: true,
        announcements: true
      };
    }

    // Update the specific setting
    user.notificationSettings[actualKey] = value;
    await user.save();

    console.log(`✅ Updated ${actualKey} = ${value} for user: ${firebaseUid}`);
    
    // Emit Socket.io event for real-time sync across devices
    const io = req.app.get('io');
    if (io) {
      io.to(firebaseUid).emit('notifications-updated', {
        firebaseUid,
        settingKey: actualKey,
        value,
        timestamp: new Date().toISOString()
      });
    }

    res.json({
      success: true,
      message: `Setting ${settingKey} updated successfully`,
      setting: {
        key: actualKey,
        value: value
      }
    });
  } catch (error) {
    console.error('❌ Error updating notification settings:', error);
    res.status(500).json({ error: error.message });
  }
});

// ===== SAVE ALL NOTIFICATION SETTINGS AT ONCE =====
router.post('/notifications/save', async (req, res) => {
  try {
    const { firebaseUid, notificationSettings } = req.body;

    if (!firebaseUid || !notificationSettings) {
      return res.status(400).json({ 
        error: 'firebaseUid and notificationSettings are required' 
      });
    }

    const user = await User.findOne({ firebaseUid });

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Map incoming setting names
    const settingMap = {
      'likes': 'postsLikes',
      'comments': 'postComments'
    };

    const mappedSettings = {};
    for (const [key, value] of Object.entries(notificationSettings)) {
      const actualKey = settingMap[key] || key;
      mappedSettings[actualKey] = value;
    }

    // Update all notification settings
    user.notificationSettings = {
      ...user.notificationSettings,
      ...mappedSettings
    };

    await user.save();

    console.log(`✅ All notification settings saved for user: ${firebaseUid}`);
    console.log(`📝 Settings: ${JSON.stringify(user.notificationSettings)}`);
    
    // Emit Socket.io event for real-time sync
    const io = req.app.get('io');
    if (io) {
      io.to(firebaseUid).emit('all-notifications-updated', {
        firebaseUid,
        settings: user.notificationSettings,
        timestamp: new Date().toISOString()
      });
    }

    res.json({
      success: true,
      message: 'All notification settings saved successfully',
      settings: user.notificationSettings
    });
  } catch (error) {
    console.error('❌ Error saving notification settings:', error);
    res.status(500).json({ error: error.message });
  }
});

router.get('/privacy/:firebaseUid', async (req, res) => {
  try {
    const { firebaseUid } = req.params;

    const user = await User.findOne({ firebaseUid });
    if (!user) {
      return res.status(404).json({
        success: false,
        error: 'User not found'
      });
    }

    res.json({
      success: true,
      settings: {
        privateAccount: user.privacySettings?.privateAccount ?? false
      }
    });
  } catch (error) {
    console.error('❌ Error fetching privacy settings:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

router.post('/privacy/update', async (req, res) => {
  try {
    const { firebaseUid, privateAccount } = req.body;

    if (!firebaseUid || typeof privateAccount !== 'boolean') {
      return res.status(400).json({
        success: false,
        error: 'firebaseUid and privateAccount are required'
      });
    }

    const user = await User.findOne({ firebaseUid });
    if (!user) {
      return res.status(404).json({
        success: false,
        error: 'User not found'
      });
    }

    user.privacySettings = {
      ...user.privacySettings,
      privateAccount
    };
    await user.save();

    res.json({
      success: true,
      message: 'Privacy settings updated successfully',
      settings: user.privacySettings
    });
  } catch (error) {
    console.error('❌ Error updating privacy settings:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

// ===== GET ALL USER SETTINGS (including security, privacy, etc.) =====
router.get('/:firebaseUid', async (req, res) => {
  try {
    const { firebaseUid } = req.params;

    if (!firebaseUid) {
      return res.status(400).json({ error: 'firebaseUid is required' });
    }

    const user = await User.findOne({ firebaseUid }).select(
      'notificationSettings securitySettings privacySettings'
    );

    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    console.log(`📥 Retrieved all settings for user: ${firebaseUid}`);
    
    res.json({
      success: true,
      settings: {
        notifications: user.notificationSettings || {},
        security: user.securitySettings || {},
        privacy: user.privacySettings || {}
      }
    });
  } catch (error) {
    console.error('❌ Error fetching user settings:', error);
    res.status(500).json({ error: error.message });
  }
});

// ===== CHANGE PASSWORD =====
/**
 * POST /api/user-settings/change-password
 * Change user's password with old password verification
 * Body: { firebaseUid, oldPassword, newPassword }
 */
router.post('/change-password', async (req, res) => {
  try {
    const { firebaseUid, oldPassword, newPassword } = req.body;

    console.log(`\n🔐 PASSWORD CHANGE REQUEST FOR USER: ${firebaseUid}`);

    // Validate input
    if (!firebaseUid || !oldPassword || !newPassword) {
      console.error('❌ Missing required fields');
      return res.status(400).json({
        success: false,
        error: 'firebaseUid, oldPassword, and newPassword are required'
      });
    }

    // Trim whitespace from passwords (common issue)
    const trimmedOldPassword = oldPassword.trim();
    const trimmedNewPassword = newPassword.trim();

    // Check if passwords are different
    if (trimmedOldPassword === trimmedNewPassword) {
      console.error('❌ New password same as old password');
      return res.status(400).json({
        success: false,
        error: 'New password must be different from old password'
      });
    }

    // Validate new password strength (at least 6 characters)
    if (trimmedNewPassword.length < 6) {
      console.error('❌ New password too short');
      return res.status(400).json({
        success: false,
        error: 'New password must be at least 6 characters long'
      });
    }

    // Get user from database
    const user = await User.findOne({ firebaseUid });
    if (!user) {
      console.error('❌ User not found in database');
      return res.status(404).json({
        success: false,
        error: 'User not found'
      });
    }

    const passwordCooldownRemainingMs = getCooldownRemainingMs(user.passwordChangedAt);
    if (passwordCooldownRemainingMs > 0) {
      return res.status(429).json({
        success: false,
        error: `You can change your password again in ${formatCooldownRemaining(passwordCooldownRemainingMs)}.`,
        passwordCooldownRemainingMs
      });
    }

    // Verify Firebase user exists
    let firebaseUser;
    try {
      firebaseUser = await admin.auth().getUser(firebaseUid);
      console.log(`✅ Step 1: Firebase user found: ${firebaseUser.email}`);
    } catch (getUserError) {
      console.error(`❌ Step 1 FAILED: Firebase user not found:`, getUserError.message);
      return res.status(404).json({
        success: false,
        error: 'User not found in Firebase'
      });
    }

    // Verify old password by attempting Firebase reauthentication
    console.log(`🔄 Step 2: Verifying old password...`);
    try {
      // Check if API key is set
      if (!process.env.FIREBASE_API_KEY) {
        console.error('❌ FIREBASE_API_KEY not set in environment');
        return res.status(500).json({
          success: false,
          error: 'Server configuration error: Firebase API key not set'
        });
      }

      // Use Firebase REST API to verify old password
      const verifyResponse = await axios.post(
        `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${process.env.FIREBASE_API_KEY}`,
        {
          email: firebaseUser.email,
          password: trimmedOldPassword,
          returnSecureToken: true
        },
        { timeout: 10000 } // 10 second timeout
      );

      if (!verifyResponse.data || !verifyResponse.data.idToken) {
        console.error('❌ Step 2 FAILED: No idToken in response');
        return res.status(401).json({
          success: false,
          error: 'Invalid current password'
        });
      }

      console.log(`✅ Step 2: Old password verified successfully`);
    } catch (verifyError) {
      const errorMessage = verifyError.response?.data?.error?.message || 'Unknown error';
      console.error(`❌ Step 2 FAILED: Old password verification failed:`, errorMessage);
      console.error(`   Error details:`, verifyError.response?.data);
      
      // Provide specific error based on Firebase response
      if (errorMessage.includes('INVALID_PASSWORD') || errorMessage.includes('INVALID_LOGIN_CREDENTIALS')) {
        return res.status(401).json({
          success: false,
          error: 'The current password is incorrect. Please try again.'
        });
      }
      
      return res.status(401).json({
        success: false,
        error: errorMessage || 'Password verification failed. Please try again.'
      });
    }

    // Password verified - Update password in Firebase
    console.log(`🔄 Step 3: Updating password in Firebase...`);
    try {
      await admin.auth().updateUser(firebaseUid, {
        password: trimmedNewPassword
      });

      console.log(`✅ Step 3: Firebase password updated successfully`);
      
      // Verify the password change actually worked by trying to sign in with new password
      console.log(`🔄 Step 4: Verifying new password works...`);
      try {
        const verifyNewPassword = await axios.post(
          `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${process.env.FIREBASE_API_KEY}`,
          {
            email: firebaseUser.email,
            password: trimmedNewPassword,
            returnSecureToken: true
          },
          { timeout: 10000 } // 10 second timeout
        );
        
        if (verifyNewPassword.data && verifyNewPassword.data.idToken) {
          console.log(`✅ Step 4: Password change verified - new password works!`);
          
          // Step 5: Update passwordChangedAt timestamp in MongoDB
          console.log(`🔄 Step 5: Updating password change timestamp...`);
          try {
            user.passwordChangedAt = new Date();
            await user.save();
            console.log(`✅ Step 5: Password timestamp updated`);
          } catch (dbError) {
            console.warn(`⚠️ Step 5 WARNING: Could not update timestamp:`, dbError.message);
            // Continue anyway - password was changed successfully
          }
          
          console.log(`✅ PASSWORD CHANGE SUCCESSFUL FOR USER: ${firebaseUid}\n`);
          
          return res.json({
            success: true,
            message: 'Password changed successfully',
            passwordChangedAt: user.passwordChangedAt
          });
        }
      } catch (verifyNewPassError) {
        const newPassError = verifyNewPassError.response?.data?.error?.message || 'Unknown error';
        console.error(`❌ Step 4 FAILED: New password verification failed:`, newPassError);
        return res.status(500).json({
          success: false,
          error: 'Password was updated but verification failed. Please try logging in with new password.'
        });
      }
    } catch (updateError) {
      console.error(`❌ Step 3 FAILED: Failed to update password in Firebase`);
      console.error(`   Error code: ${updateError.code}`);
      console.error(`   Error message: ${updateError.message}`);
      return res.status(500).json({
        success: false,
        error: `Failed to update password: ${updateError.message}`
      });
    }
  } catch (error) {
    console.error('❌ CRITICAL ERROR in change password:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

// ===== CHANGE EMAIL ADDRESS =====
/**
 * POST /api/user-settings/change-email
 * Change user's email address
 * Body: { firebaseUid, newEmail, password }
 */
router.post('/change-email', async (req, res) => {
  try {
    const { firebaseUid, newEmail, password } = req.body;

    console.log(`\n📧 EMAIL CHANGE REQUEST FOR USER: ${firebaseUid}`);

    // Validate input
    if (!firebaseUid || !newEmail || !password) {
      console.error('❌ Missing required fields');
      return res.status(400).json({
        success: false,
        error: 'firebaseUid, newEmail, and password are required'
      });
    }

    // Validate email format
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(newEmail)) {
      console.error('❌ Invalid email format');
      return res.status(400).json({
        success: false,
        error: 'Invalid email format'
      });
    }

    // Trim and lowercase email
    const trimmedEmail = newEmail.trim().toLowerCase();

    // Get user from database
    const user = await User.findOne({ firebaseUid });
    if (!user) {
      console.error('❌ User not found in database');
      return res.status(404).json({
        success: false,
        error: 'User not found'
      });
    }

    const emailCooldownRemainingMs = getCooldownRemainingMs(user.emailChangedAt);
    if (emailCooldownRemainingMs > 0) {
      return res.status(429).json({
        success: false,
        error: `You can change your email again in ${formatCooldownRemaining(emailCooldownRemainingMs)}.`,
        emailCooldownRemainingMs
      });
    }

    // Check if new email already exists
    const existingEmail = await User.findOne({ email: trimmedEmail, firebaseUid: { $ne: firebaseUid } });
    if (existingEmail) {
      console.error('❌ Email already in use by another account');
      return res.status(409).json({
        success: false,
        error: 'This email is already registered to another account'
      });
    }

    // Verify password first
    console.log(`🔄 Step 1: Verifying password...`);
    try {
      const firebaseUser = await admin.auth().getUser(firebaseUid);
      console.log(`✅ Firebase user found: ${firebaseUser.email}`);
      
      if (!process.env.FIREBASE_API_KEY) {
        console.error('❌ FIREBASE_API_KEY not set');
        return res.status(500).json({
          success: false,
          error: 'Server configuration error'
        });
      }

      // Verify password
      const verifyResponse = await axios.post(
        `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${process.env.FIREBASE_API_KEY}`,
        {
          email: firebaseUser.email,
          password: password.trim(),
          returnSecureToken: true
        },
        { timeout: 10000 }
      );

      if (!verifyResponse.data?.idToken) {
        console.error('❌ Password verification failed');
        return res.status(401).json({
          success: false,
          error: 'Invalid password'
        });
      }

      console.log(`✅ Step 1: Password verified`);
    } catch (verifyError) {
      const errorMsg = verifyError.response?.data?.error?.message || 'Unknown error';
      console.error(`❌ Password verification failed:`, errorMsg);
      return res.status(401).json({
        success: false,
        error: 'The password you entered is incorrect'
      });
    }

    // Update email in Firebase Auth
    console.log(`🔄 Step 2: Updating email in Firebase...`);
    try {
      await admin.auth().updateUser(firebaseUid, {
        email: trimmedEmail
      });
      console.log(`✅ Step 2: Firebase email updated`);
    } catch (firebaseError) {
      console.error(`❌ Failed to update email in Firebase:`, firebaseError.message);
      return res.status(500).json({
        success: false,
        error: `Failed to update email: ${firebaseError.message}`
      });
    }

    // Update email in MongoDB
    console.log(`🔄 Step 3: Updating email in database...`);
    try {
      user.email = trimmedEmail;
      user.emailChangedAt = new Date();
      await user.save();
      console.log(`✅ Step 3: Database email updated`);
    } catch (dbError) {
      console.error(`❌ Failed to update email in database:`, dbError.message);
      // Try to rollback Firebase change
      try {
        await admin.auth().updateUser(firebaseUid, { email: user.email });
      } catch (rollbackError) {
        console.error('⚠️ Rollback failed - manual intervention needed');
      }
      return res.status(500).json({
        success: false,
        error: 'Failed to update email in database'
      });
    }

    console.log(`✅ EMAIL CHANGE SUCCESSFUL FOR USER: ${firebaseUid}\n`);
    res.json({
      success: true,
      message: 'Email changed successfully',
      newEmail: trimmedEmail,
      emailChangedAt: user.emailChangedAt
    });

  } catch (error) {
    console.error('❌ CRITICAL ERROR in email change:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

// ===== UPDATE PHONE NUMBER =====
/**
 * POST /api/user-settings/update-phone
 * Update user's phone number
 * Body: { firebaseUid, phoneNumber }
 */
router.post('/update-phone', async (req, res) => {
  try {
    const { firebaseUid, phoneNumber } = req.body;

    console.log(`\n📱 PHONE NUMBER UPDATE REQUEST FOR USER: ${firebaseUid}`);

    // Validate input
    if (!firebaseUid || !phoneNumber) {
      console.error('❌ Missing required fields');
      return res.status(400).json({
        success: false,
        error: 'firebaseUid and phoneNumber are required'
      });
    }

    // Validate phone number format (basic validation)
    const trimmedPhone = phoneNumber.trim();
    if (trimmedPhone.length < 5) {
      console.error('❌ Phone number too short');
      return res.status(400).json({
        success: false,
        error: 'Phone number must be at least 5 characters'
      });
    }

    // Get user from database
    const user = await User.findOne({ firebaseUid });
    if (!user) {
      console.error('❌ User not found');
      return res.status(404).json({
        success: false,
        error: 'User not found'
      });
    }

    // Update phone number in MongoDB
    console.log(`🔄 Step 1: Updating phone number in database...`);
    try {
      user.phone = trimmedPhone;
      await user.save();
      console.log(`✅ Step 1: Phone number updated in database`);
    } catch (dbError) {
      console.error(`❌ Failed to update phone in database:`, dbError.message);
      return res.status(500).json({
        success: false,
        error: 'Failed to update phone number'
      });
    }

    // Update phone in Firebase (if Firebase supports it)
    console.log(`🔄 Step 2: Updating phone number in Firebase...`);
    try {
      await admin.auth().updateUser(firebaseUid, {
        phoneNumber: trimmedPhone.startsWith('+') ? trimmedPhone : '+' + trimmedPhone.replace(/\D/g, '')
      });
      console.log(`✅ Step 2: Phone number updated in Firebase`);
    } catch (firebaseError) {
      console.warn(`⚠️ Firebase phone update skipped:`, firebaseError.message);
      // Continue anyway - MongoDB update is the important one
    }

    console.log(`✅ PHONE NUMBER UPDATE SUCCESSFUL FOR USER: ${firebaseUid}\n`);
    res.json({
      success: true,
      message: 'Phone number updated successfully',
      phoneNumber: trimmedPhone
    });

  } catch (error) {
    console.error('❌ CRITICAL ERROR in phone update:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

// ===== GET SECURITY SETTINGS =====
/**
 * GET /api/user-settings/security/:firebaseUid
 * Get user's security settings
 */
router.get('/security/:firebaseUid', async (req, res) => {
  try {
    const { firebaseUid } = req.params;

    const user = await User.findOne({ firebaseUid });
    if (!user) {
      return res.status(404).json({
        success: false,
        error: 'User not found'
      });
    }

    res.json({
      success: true,
      settings: {
        email: user.email,
        phone: user.phone || '',
        loginAlerts: user.securitySettings?.loginAlerts ?? true,
        suspiciousActivityAlerts: user.securitySettings?.suspiciousActivityAlerts ?? true,
        passwordChangedAt: user.passwordChangedAt,
        emailChangedAt: user.emailChangedAt,
        passwordCooldownRemainingMs: getCooldownRemainingMs(user.passwordChangedAt),
        emailCooldownRemainingMs: getCooldownRemainingMs(user.emailChangedAt)
      }
    });
  } catch (error) {
    console.error('❌ Error fetching security settings:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

module.exports = router;
