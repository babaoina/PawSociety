const express = require('express');
const router = express.Router();
const admin = require('firebase-admin');
const path = require('path');
const User = require('../models/User');

// Initialize Firebase Admin SDK
const initializeFirebaseAdmin = () => {
  try {
    const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || './firebase-service-account.json';
    const serviceAccount = require(path.resolve(__dirname, '..', serviceAccountPath));
    
    if (!admin.apps.length) {
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      console.log('✅ Firebase Admin initialized');
    }
  } catch (error) {
    console.warn('⚠️ Firebase Admin not configured. Firebase auth verification will be skipped in dev mode.');
    console.warn('   Place your firebase-service-account.json in the backend folder.');
  }
};

initializeFirebaseAdmin();

// Verify Firebase ID token
const verifyFirebaseToken = async (req, res, next) => {
  const idToken = req.headers.authorization?.replace('Bearer ', '');
  
  if (!idToken) {
    return res.status(401).json({
      success: false,
      message: 'No authorization token provided'
    });
  }

  try {
    // If Firebase Admin is not initialized, skip verification (dev mode)
    if (!admin.apps.length) {
      req.userUid = idToken; // Use token as UID for local testing
      return next();
    }

    const decodedToken = await admin.auth().verifyIdToken(idToken);
    req.userUid = decodedToken.uid;
    req.userEmail = decodedToken.email;
    next();
  } catch (error) {
    console.error('Token verification error:', error.message);
    return res.status(401).json({
      success: false,
      message: 'Invalid or expired token'
    });
  }
};

/**
 * POST /api/auth/firebase-login
 * Login/Register using Firebase UID
 * Body: { firebaseUid, email, username?, fullName?, phone? }
 */
router.post('/firebase-login', async (req, res) => {
  try {
    const { firebaseUid, email, username, fullName, phone } = req.body;

    console.log(`📝 Firebase login request - UID: ${firebaseUid}, Email: ${email}, Username: ${username}`);

    // Validate required fields
    if (!firebaseUid || !email) {
      return res.status(400).json({
        success: false,
        message: 'firebaseUid and email are required'
      });
    }

    // Find user by Firebase UID
    let user = await User.findOne({ firebaseUid });
    console.log(`🔍 User lookup result: ${user ? 'FOUND' : 'NOT FOUND'}`);

    if (!user) {
      // Check if email already exists with different UID (rare case)
      const existingEmail = await User.findOne({ email });
      if (existingEmail) {
        console.log(`⚠️ Email ${email} already exists with different UID`);
        // Update the existing user with this Firebase UID
        existingEmail.firebaseUid = firebaseUid;
        await existingEmail.save();
        user = existingEmail;
        console.log(`✅ Updated existing user with new Firebase UID: ${user.username}`);
      } else {
        // Create new user
        console.log(`➕ Creating new user with UID: ${firebaseUid}`);
        
        // Generate username if not provided
        const finalUsername = username || email.split('@')[0] + '_' + Math.random().toString(36).substring(2, 6);
        
        // Generate full name if not provided
        const finalFullName = fullName || email.split('@')[0];
        
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
        console.log(`✅ User created successfully: ${user.username} (ID: ${user._id})`);
      }
    } else {
      console.log(`👤 Existing user logged in: ${user.username}`);
      
      // Update user info if needed (optional)
      let updated = false;
      if (username && user.username !== username) {
        user.username = username;
        updated = true;
      }
      if (fullName && user.fullName !== fullName) {
        user.fullName = fullName;
        updated = true;
      }
      if (phone && user.phone !== phone) {
        user.phone = phone;
        updated = true;
      }
      
      if (updated) {
        await user.save();
        console.log(`✅ User info updated: ${user.username}`);
      }
    }

    // Return user data (excluding sensitive fields)
    res.json({
      success: true,
      message: 'Login successful',
      data: {
        firebaseUid: user.firebaseUid,
        username: user.username,
        email: user.email,
        fullName: user.fullName,
        phone: user.phone || '',
        profileImageUrl: user.profileImageUrl || '',
        bio: user.bio || '',
        location: user.location || '',
        createdAt: user.createdAt
      }
    });

  } catch (error) {
    console.error('❌ Firebase login error:', error);
    res.status(500).json({
      success: false,
      message: error.message || 'Internal server error during login'
    });
  }
});

/**
 * POST /api/auth/check-status
 * Check if user is still active/suspended
 */
router.post('/check-status', async (req, res) => {
  try {
    const { firebaseUid } = req.body;
    
    const user = await User.findOne({ firebaseUid });
    
    if (!user) {
      return res.json({ 
        success: false, 
        message: 'User not found',
        status: 'deleted'
      });
    }
    
    // Return user status
    res.json({
      success: true,
      status: user.status || 'Active',
      message: user.status === 'Suspended' ? 'Account suspended' : 'Account active'
    });
    
  } catch (error) {
    console.error('Check status error:', error);
    res.status(500).json({ success: false, message: error.message });
  }
});

/**
 * POST /api/auth/verify-token
 * Verify Firebase token and get user data
 */
router.post('/verify-token', verifyFirebaseToken, async (req, res) => {
  try {
    const user = await User.findOne({ firebaseUid: req.userUid });

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      data: {
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
  } catch (error) {
    console.error('Verify token error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/auth/check-user/:firebaseUid
 * Check if user exists in MongoDB (helper endpoint)
 */
router.get('/check-user/:firebaseUid', async (req, res) => {
  try {
    const user = await User.findOne({ firebaseUid: req.params.firebaseUid });
    res.json({
      success: true,
      exists: !!user,
      user: user ? {
        firebaseUid: user.firebaseUid,
        username: user.username,
        email: user.email
      } : null
    });
  } catch (error) {
    console.error('Check user error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;