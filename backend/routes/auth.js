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
 * Body: { firebaseUid, email, username?, fullName? }
 */
router.post('/firebase-login', async (req, res) => {
  try {
    const { firebaseUid, email, username, fullName, phone } = req.body;

    console.log(`📝 Firebase login request - UID: ${firebaseUid}, Email: ${email}, Username: ${username}`);

    if (!firebaseUid || !email) {
      return res.status(400).json({
        success: false,
        message: 'firebaseUid and email are required'
      });
    }

    // Find or create user
    let user = await User.findOne({ firebaseUid });
    console.log(`🔍 User lookup result: ${user ? 'FOUND' : 'NOT FOUND'}`);

    if (!user) {
      // Create new user
      console.log(`➕ Creating new user with UID: ${firebaseUid}`);
      user = new User({
        firebaseUid,
        email,
        username: username || `user_${firebaseUid.substring(0, 8)}`,
        fullName: fullName || email.split('@')[0],
        phone: phone || ''
      });
      await user.save();
      console.log(`✅ User created successfully: ${user.username}`);
    } else {
      console.log(`👤 Existing user logged in: ${user.username}`);
    }

    res.json({
      success: true,
      message: user.createdAt.getTime() === user.updatedAt.getTime() ? 'User created' : 'User logged in',
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
    console.error('❌ Firebase login error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
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
  } catch (error) {
    console.error('Verify token error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;
