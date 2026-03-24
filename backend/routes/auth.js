const express = require('express');
const router = express.Router();
const admin = require('firebase-admin');
const path = require('path');
const User = require('../models/User');
const { handleError, formatErrorMessage } = require('../utils/errorHandler');

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

const normalizeEmail = (email = '') => email.trim().toLowerCase();

const isFirebaseUserNotFoundError = (error) =>
  error?.code === 'auth/user-not-found' ||
  error?.errorInfo?.code === 'auth/user-not-found';

const getFirebaseUserSafe = async (firebaseUid) => {
  if (!firebaseUid || !admin.apps.length) {
    return null;
  }

  try {
    return await admin.auth().getUser(firebaseUid);
  } catch (error) {
    if (isFirebaseUserNotFoundError(error)) {
      return null;
    }
    throw error;
  }
};

const purgeUserData = async (firebaseUid, userDoc = null) => {
  if (!firebaseUid) return;

  const Post = require('../models/Post');
  const Report = require('../models/Report');
  const Favorite = require('../models/Favorite');
  const Follow = require('../models/Follow');
  const Chat = require('../models/Chat');
  const Message = require('../models/Message');
  const Notification = require('../models/Notification');
  const Block = require('../models/Block');
  const HiddenPost = require('../models/HiddenPost');
  const Pet = require('../models/Pet');
  const Session = require('../models/Session');

  await Post.deleteMany({ firebaseUid });
  await Report.deleteMany({ $or: [{ reportedUid: firebaseUid }, { reporterUid: firebaseUid }] });
  await Favorite.deleteMany({ userUid: firebaseUid });
  await Follow.deleteMany({ $or: [{ followerUid: firebaseUid }, { followingUid: firebaseUid }] });
  await Chat.updateMany({ participants: firebaseUid }, { $pull: { participants: firebaseUid } });
  await Chat.deleteMany({ participants: { $size: 0 } });
  await Message.deleteMany({ $or: [{ senderUid: firebaseUid }, { receiverUid: firebaseUid }] });
  await Notification.deleteMany({ $or: [{ userId: firebaseUid }, { fromUserId: firebaseUid }] });
  await Block.deleteMany({ $or: [{ blockerUid: firebaseUid }, { blockedUid: firebaseUid }] });
  await HiddenPost.deleteMany({ userUid: firebaseUid });
  await Pet.deleteMany({ ownerUid: firebaseUid });
  await Session.deleteMany({ firebaseUid });

  if (userDoc?._id) {
    await User.findByIdAndDelete(userDoc._id);
  } else {
    await User.deleteMany({ firebaseUid });
  }
};

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
        emailVerified: user.emailVerified || false,
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

    if (firebaseUid && admin.apps.length) {
      const firebaseUser = await getFirebaseUserSafe(firebaseUid);
      if (!firebaseUser) {
        await purgeUserData(firebaseUid, user);
        return res.json({
          success: false,
          message: 'Account deleted',
          status: 'deleted'
        });
      }
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
        emailVerified: user.emailVerified || false,
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

/**
 * POST /api/auth/delete-account-with-password
 * Delete user account with password verification
 */
router.post('/delete-account-with-password', async (req, res) => {
  try {
    const { firebaseUid, password } = req.body;

    console.log(`\n🗑️ DELETE ACCOUNT REQUEST FOR USER: ${firebaseUid}`);

    if (!firebaseUid || !password) {
      console.error('❌ Missing required fields');
      return res.status(400).json({ 
        success: false,
        error: 'Missing firebaseUid or password' 
      });
    }

    // Trim whitespace from password (common issue)
    const trimmedPassword = password.trim();

    // Get user from database
    const user = await User.findOne({ firebaseUid });
    if (!user) {
      console.error('❌ User not found in database');
      return res.status(404).json({ 
        success: false,
        error: 'User not found' 
      });
    }

    // Verify password by attempting Firebase reauthentication
    console.log(`🔄 Step 1: Verifying password...`);
    try {
      const axios = require('axios');
      const firebaseUser = await admin.auth().getUser(firebaseUid);
      console.log(`✅ Firebase user found: ${firebaseUser.email}`);
      
      // Check if API key is set
      if (!process.env.FIREBASE_API_KEY) {
        console.error('❌ FIREBASE_API_KEY not set in environment');
        return res.status(500).json({
          success: false,
          error: 'Server configuration error: Firebase API key not set'
        });
      }
      
      // Attempt to sign in with email and password to verify credentials
      const response = await axios.post(
        `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${process.env.FIREBASE_API_KEY}`,
        {
          email: firebaseUser.email,
          password: trimmedPassword,
          returnSecureToken: true
        },
        { timeout: 10000 } // 10 second timeout
      );

      if (!response.data || !response.data.idToken) {
        console.error('❌ Step 1 FAILED: No idToken in response');
        return res.status(401).json({ 
          success: false,
          error: 'Invalid password' 
        });
      }

      console.log(`✅ Step 1: Password verified successfully`);
    } catch (error) {
      const errorMessage = error.response?.data?.error?.message || 'Unknown error';
      console.error('❌ Step 1 FAILED: Password verification failed:', errorMessage);
      console.error(`   Error details:`, error.response?.data);
      
      // Provide specific error based on Firebase response
      if (errorMessage.includes('INVALID_PASSWORD') || errorMessage.includes('INVALID_LOGIN_CREDENTIALS')) {
        return res.status(401).json({ 
          success: false,
          error: 'The password you entered is incorrect. Please try again.' 
        });
      }
      
      return res.status(401).json({ 
        success: false,
        error: errorMessage || 'Invalid password. Please try again.' 
      });
    }

    // PASSWORD VERIFIED - Proceed with account deletion
    console.log(`🔄 Step 2: Deleting account for user: ${firebaseUid}`);

    // Delete from Firebase Auth
    try {
      await admin.auth().deleteUser(firebaseUid);
      console.log(`✅ Deleted from Firebase Auth`);
    } catch (firebaseError) {
      console.error('⚠️ Failed to delete from Firebase:', firebaseError);
      // Continue with database deletion even if Firebase fails
    }

    // Delete model imports
    console.log(`🔄 Step 3: Deleting all user data from database...`);
    await purgeUserData(firebaseUid, user);

    console.log(`✅ Step 3: All user data deleted from database`);
    console.log(`✅ ACCOUNT DELETION SUCCESSFUL FOR USER: ${firebaseUid}\n`);

    res.json({ 
      success: true, 
      message: 'Account permanently deleted. We hope to see you again!',
      deleted: true 
    });

  } catch (error) {
    console.error('❌ CRITICAL ERROR in delete account:', error);
    res.status(500).json({ 
      success: false,
      error: error.message 
    });
  }
});

/**
 * POST /api/auth/send-verification-email
 * Send email verification link on login
 */
router.post('/send-verification-email', async (req, res) => {
  try {
    const { firebaseUid } = req.body;

    if (!firebaseUid) {
      return res.status(400).json({ 
        success: false,
        error: 'Missing firebaseUid' 
      });
    }

    // Get user from database
    const user = await User.findOne({ firebaseUid });
    if (!user) {
      return res.status(404).json({ 
        success: false,
        error: 'User not found' 
      });
    }

    // Get user from Firebase
    const firebaseUser = await admin.auth().getUser(firebaseUid);

    // Generate verification email link
    const link = await admin.auth().generateEmailVerificationLink(firebaseUser.email);
    
    console.log(`📧 Verification email generated for ${firebaseUser.email}`);

    res.json({
      success: true,
      message: 'Verification email sent',
      verificationLink: link
    });

  } catch (error) {
    console.error('Send verification email error:', error);
    res.status(500).json({ 
      success: false,
      error: error.message 
    });
  }
});

/**
 * GET /api/auth/active-sessions/:firebaseUid
 * Fetch active sessions for user with device info
 */
router.get('/active-sessions/:firebaseUid', async (req, res) => {
  try {
    const { firebaseUid } = req.params;
    const Session = require('../models/Session');

    // Fetch active sessions from database
    const sessions = await Session.find({ firebaseUid }).sort({ createdAt: -1 });

    if (!sessions || sessions.length === 0) {
      return res.json({ 
        success: true, 
        sessions: [] 
      });
    }

    const formattedSessions = sessions.map(session => ({
      sessionId: session._id,
      deviceModel: session.deviceModel || 'Unknown Device',
      osVersion: session.osVersion || 'Unknown',
      lastActive: session.lastActive,
      deviceName: session.deviceName || `${session.deviceModel} - ${session.osVersion}`,
      createdAt: session.createdAt,
      isCurrentSession: session.sessionToken === req.headers['x-session-token']
    }));

    res.json({ 
      success: true, 
      sessions: formattedSessions 
    });

  } catch (error) {
    console.error('Get active sessions error:', error);
    res.status(500).json({ 
      success: false,
      error: error.message 
    });
  }
});

/**
 * POST /api/auth/create-session
 * Create a new session for device tracking
 * Body: { firebaseUid, deviceModel, osVersion, deviceName }
 */
router.post('/create-session', async (req, res) => {
  try {
    const { firebaseUid, deviceModel, osVersion, deviceName } = req.body;
    const Session = require('../models/Session');
    const crypto = require('crypto');

    if (!firebaseUid) {
      return res.status(400).json({
        success: false,
        error: 'firebaseUid is required'
      });
    }

    console.log(`🔐 Creating new session for user: ${firebaseUid}, Device: ${deviceModel}`);

    // Generate session token
    const sessionToken = crypto.randomBytes(32).toString('hex');

    // Get IP from request
    const ipAddress = req.headers['x-forwarded-for'] || req.connection.remoteAddress || 'Unknown';
    const userAgent = req.headers['user-agent'] || 'Unknown';

    // Create new session
    const session = new Session({
      firebaseUid,
      sessionToken,
      deviceModel: deviceModel || 'Unknown Device',
      osVersion: osVersion || 'Unknown',
      deviceName: deviceName || `${deviceModel} - ${osVersion}`,
      ipAddress,
      userAgent,
      lastActive: new Date()
    });

    await session.save();

    console.log(`✅ Session created successfully: ${session._id}`);

    res.json({
      success: true,
      message: 'Session created successfully',
      sessionId: session._id,
      sessionToken: sessionToken
    });

  } catch (error) {
    handleError(error, res, 500, 'Register unverified');
  }
});

/**
 * POST /api/auth/update-session-activity
 * Update last active time for a session
 * Body: { firebaseUid, sessionId }
 */
router.post('/update-session-activity', async (req, res) => {
  try {
    const { firebaseUid, sessionId } = req.body;
    const Session = require('../models/Session');

    if (!firebaseUid || !sessionId) {
      return res.status(400).json({
        success: false,
        error: 'firebaseUid and sessionId are required'
      });
    }

    // Update last active time
    const session = await Session.findByIdAndUpdate(
      sessionId,
      { lastActive: new Date() },
      { new: true }
    );

    if (!session) {
      return res.status(404).json({
        success: false,
        error: 'Session not found'
      });
    }

    res.json({
      success: true,
      message: 'Session activity updated'
    });

  } catch (error) {
    console.error('Update session activity error:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

/**
 * POST /api/auth/logout-session
 * Logout from specific device/session
 */
router.post('/logout-session', async (req, res) => {
  try {
    const { firebaseUid, sessionId } = req.body;
    const Session = require('../models/Session');

    if (!firebaseUid || !sessionId) {
      return res.status(400).json({ 
        success: false,
        error: 'Missing firebaseUid or sessionId' 
      });
    }

    // Delete session
    await Session.findByIdAndDelete(sessionId);

    console.log(`🚪 Session ended for user ${firebaseUid}: ${sessionId}`);

    res.json({ 
      success: true, 
      message: 'Session ended. You will be logged out on that device.' 
    });

  } catch (error) {
    console.error('Logout session error:', error);
    res.status(500).json({ 
      success: false,
      error: error.message 
    });
  }
});

/**
 * POST /api/auth/logout-all-sessions
 * Logout from all devices
 */
router.post('/logout-all-sessions', async (req, res) => {
  try {
    const { firebaseUid } = req.body;
    const Session = require('../models/Session');

    if (!firebaseUid) {
      return res.status(400).json({ 
        success: false,
        error: 'Missing firebaseUid' 
      });
    }

    // Delete all sessions for user
    await Session.deleteMany({ firebaseUid });

    console.log(`🚪 All sessions ended for user: ${firebaseUid}`);

    res.json({ 
      success: true, 
      message: 'Logged out from all devices' 
    });

  } catch (error) {
    console.error('Logout all sessions error:', error);
    res.status(500).json({ 
      success: false,
      error: error.message 
    });
  }
});

/**
 * POST /api/auth/forgot-password
 * Send password reset email using Firebase
 * Body: { email }
 */
router.post('/forgot-password', async (req, res) => {
  try {
    const { email } = req.body;

    if (!email) {
      return res.status(400).json({
        success: false,
        error: 'Email is required'
      });
    }

    // Get user from MongoDB
    const user = await User.findOne({ email });
    if (!user) {
      // For security, don't reveal if email exists
      return res.json({
        success: true,
        message: 'If an account exists for this email, a password reset link will be sent',
        emailSent: false
      });
    }

    try {
      // Generate password reset link using Firebase Admin SDK
      const resetLink = await admin.auth().generatePasswordResetLink(email);
      
      console.log(`📧 Password reset link generated for ${email}`);

      // In a production environment, you would send this link via email service
      // For now, we just confirm the link was generated
      res.json({
        success: true,
        message: 'Password reset link generated successfully',
        emailSent: true,
        resetLink: resetLink // In production, don't return this in response
      });

    } catch (firebaseError) {
      console.error('Firebase reset link error:', firebaseError);
      return res.status(500).json({
        success: false,
        error: 'Failed to generate password reset link'
      });
    }

  } catch (error) {
    console.error('Forgot password error:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

/**
 * POST /api/auth/check-email-verified
 * Check if email is verified for user
 * Body: { firebaseUid }
 */
router.post('/check-email-verified', async (req, res) => {
  try {
    const { firebaseUid } = req.body;

    if (!firebaseUid) {
      return res.status(400).json({
        success: false,
        error: 'firebaseUid is required'
      });
    }

    // Get user from database
    const user = await User.findOne({ firebaseUid });
    if (!user) {
      return res.status(404).json({
        success: false,
        error: 'User not found'
      });
    }

    // Check Firebase user's email verification status
    try {
      const firebaseUser = await admin.auth().getUser(firebaseUid);
      const isEmailVerified = firebaseUser.emailVerified || false;

      // Update our database
      if (isEmailVerified && !user.emailVerified) {
        user.emailVerified = true;
        user.emailVerificationToken = null;
        await user.save();
        console.log(`✅ Email verified for user: ${user.username}`);
      }

      res.json({
        success: true,
        emailVerified: isEmailVerified,
        email: user.email
      });

    } catch (firebaseError) {
      console.error('Firebase check error:', firebaseError);
      // If Firebase check fails, return our stored status
      res.json({
        success: true,
        emailVerified: user.emailVerified || false,
        email: user.email
      });
    }

  } catch (error) {
    console.error('Check email verified error:', error);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

/**
 * POST /api/auth/register-unverified
 * Register user with unverified email (creates account in MongoDB only)
 * Body: { email, username, fullName, phone }
 */
router.post('/register-unverified', async (req, res) => {
  try {
    const { email, username, fullName, phone } = req.body;
    const normalizedEmail = normalizeEmail(email);

    // Validate required fields - only email is required on Step 1
    if (!normalizedEmail) {
      return res.status(400).json({
        success: false,
        error: 'Email is required'
      });
    }

    // Username and fullName are optional on Step 1 (can be completed in later steps)
    // If not provided, use defaults
    const finalUsername = username || `user_${Math.random().toString(36).substring(2, 8)}`;
    const finalFullName = fullName || 'User';

    // Check if email already exists
    const existingUser = await User.findOne({ email: normalizedEmail });
    if (existingUser) {
      if (existingUser.firebaseUid) {
        const existingFirebaseUser = await getFirebaseUserSafe(existingUser.firebaseUid);
        if (!existingFirebaseUser) {
          await purgeUserData(existingUser.firebaseUid, existingUser);
        } else {
          return res.status(409).json({
            success: false,
            error: 'This email is already linked to an existing account.'
          });
        }
      } else if (!existingUser.emailVerified) {
        existingUser.username = username || existingUser.username || finalUsername;
        existingUser.fullName = fullName || existingUser.fullName || finalFullName;
        existingUser.phone = phone || existingUser.phone || '';
        existingUser.emailVerificationToken = require('crypto').randomBytes(32).toString('hex');
        await existingUser.save();

        return res.status(200).json({
          success: true,
          message: 'Registration resumed. Please verify your email to continue.',
          user: {
            username: existingUser.username,
            email: existingUser.email,
            fullName: existingUser.fullName,
            emailVerified: false
          }
        });
      } else {
        return res.status(409).json({
          success: false,
          error: 'This email is already linked to an existing account.'
        });
      }
    }

    // Check if username already exists
    const existingUsername = await User.findOne({ username: finalUsername });
    if (existingUsername) {
      return res.status(409).json({
        success: false,
        error: 'Username already taken'
      });
    }

    // Create unverified user (no firebaseUid yet)
    const newUser = new User({
      email: normalizedEmail,
      username: finalUsername,
      fullName: finalFullName,
      phone: phone || '',
      emailVerified: false,
      emailVerificationToken: require('crypto').randomBytes(32).toString('hex'),
      profileImageUrl: '',
      bio: '',
      location: ''
    });

    await newUser.save();
    console.log(`✅ Unverified user created: ${finalUsername}`);

    res.status(201).json({
      success: true,
      message: 'User created. Please verify your email to activate your account.',
      user: {
        username: newUser.username,
        email: newUser.email,
        fullName: newUser.fullName,
        emailVerified: false
      }
    });

  } catch (error) {
    handleError(error, res, 500, 'Register unverified');
  }
});

/**
 * POST /api/auth/finalize-account
 * Finalize account after email verification (link Firebase UID)
 * Body: { firebaseUid, email }
 */
router.post('/finalize-account', async (req, res) => {
  try {
    const { firebaseUid, email } = req.body;
    const normalizedEmail = normalizeEmail(email);

    if (!firebaseUid || !normalizedEmail) {
      return res.status(400).json({
        success: false,
        error: 'firebaseUid and email are required'
      });
    }

    // Get unverified user from MongoDB
    const user = await User.findOne({ email: normalizedEmail });
    if (!user) {
      return res.status(404).json({
        success: false,
        error: 'User not found'
      });
    }

    // Check Firebase user's email verification status
    const firebaseUser = await admin.auth().getUser(firebaseUid);
    if (!firebaseUser.emailVerified) {
      return res.status(400).json({
        success: false,
        error: 'Email is not verified in Firebase'
      });
    }

    if (normalizeEmail(firebaseUser.email || '') !== normalizedEmail) {
      return res.status(400).json({
        success: false,
        error: 'The Firebase account email does not match the registration email.'
      });
    }

    if (user.firebaseUid && user.firebaseUid !== firebaseUid) {
      const linkedFirebaseUser = await getFirebaseUserSafe(user.firebaseUid);
      if (linkedFirebaseUser) {
        return res.status(409).json({
          success: false,
          error: 'This email is already linked to another active account.'
        });
      }
    }

    // Update user with Firebase UID and mark as verified
    user.firebaseUid = firebaseUid;
    user.emailVerified = true;
    user.emailVerificationToken = null;
    await user.save();

    console.log(`✅ Account finalized for user: ${user.username}`);

    res.json({
      success: true,
      message: 'Account activated successfully',
      user: {
        firebaseUid: user.firebaseUid,
        username: user.username,
        email: user.email,
        fullName: user.fullName,
        emailVerified: true
      }
    });

  } catch (error) {
    handleError(error, res, 500, 'Finalize account');
  }
});

/**
 * GET /api/auth/reset-password
 * Handle password reset deep link redirect
 * Query: oobCode (Firebase password reset code), continueUrl (optional redirect after reset)
 * Redirects to mobile app with oobCode parameter
 */
router.get('/reset-password', async (req, res) => {
  try {
    const { oobCode, continueUrl } = req.query;

    if (!oobCode) {
      return res.status(400).json({
        success: false,
        error: 'Missing oobCode parameter'
      });
    }

    console.log(`🔐 Password reset redirect received for oobCode: ${oobCode.substring(0, 20)}...`);

    // Option 1: Redirect to custom app scheme (universal deep link)
    // This will open the app directly if installed
    const appDeepLink = `pawsociety://reset-password?oobCode=${encodeURIComponent(oobCode)}`;
    
    // Option 2: Also support App Links (standard Android approach)
    // The app will intercept this and extract the oobCode
    const appLinkUrl = `${process.env.APP_DOMAIN || 'https://yourapp.com'}/auth/reset-password?oobCode=${encodeURIComponent(oobCode)}`;

    // HTML response that attempts both methods
    const html = `
      <!DOCTYPE html>
      <html>
        <head>
          <title>Password Reset</title>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
              display: flex;
              justify-content: center;
              align-items: center;
              min-height: 100vh;
              margin: 0;
              background: linear-gradient(135deg, #B88B4A 0%, #7A4F2B 100%);
            }
            .container {
              text-align: center;
              background: white;
              padding: 40px;
              border-radius: 12px;
              box-shadow: 0 10px 40px rgba(0,0,0,0.2);
              max-width: 400px;
            }
            h1 {
              color: #7A4F2B;
              margin: 0 0 10px 0;
              font-size: 28px;
            }
            p {
              color: #666;
              margin: 0 0 20px 0;
              line-height: 1.5;
            }
            .button {
              display: inline-block;
              padding: 12px 28px;
              background: #7A4F2B;
              color: white;
              text-decoration: none;
              border-radius: 6px;
              font-weight: 600;
              transition: background 0.3s;
              margin: 10px 5px;
            }
            .button:hover {
              background: #B88B4A;
            }
            .loading {
              color: #7A4F2B;
              font-size: 14px;
              margin-top: 20px;
            }
            .code-display {
              background: #f5f5f5;
              padding: 12px;
              border-radius: 6px;
              font-family: monospace;
              font-size: 12px;
              word-break: break-all;
              margin: 20px 0;
              color: #333;
            }
          </style>
        </head>
        <body>
          <div class="container">
            <h1>🐾 PawSociety</h1>
            <h2 style="color: #B88B4A; font-size: 20px; margin: 0 0 20px 0;">Reset Your Password</h2>
            <p>Opening PawSociety app to reset your password...</p>
            
            <div class="code-display">${oobCode.substring(0, 30)}...</div>
            
            <p>If the app doesn't open automatically, tap the button below:</p>
            
            <a href="${appDeepLink}" class="button">Open PawSociety App</a>
            
            <p style="font-size: 12px; color: #999; margin-top: 20px;">
              Don't have the app installed? 
              <a href="https://play.google.com/store/apps/details?id=com.example.pawsociety" style="color: #7A4F2B; text-decoration: none;">Download it here</a>
            </p>
            
            <div class="loading">
              <p>Redirecting...</p>
            </div>
          </div>

          <script>
            // Try to open the app via custom scheme
            window.location.href = "${appDeepLink}";
            
            // Fallback: show page content if app doesn't open
            setTimeout(() => {
              document.querySelector('.loading').style.display = 'none';
            }, 3000);
          </script>
        </body>
      </html>
    `;

    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.send(html);

  } catch (error) {
    console.error('Password reset redirect error:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to process password reset link'
    });
  }
});

/**
 * POST /api/auth/verify-reset-code
 * Verify if a password reset code is valid
 * Body: { oobCode }
 */
router.post('/verify-reset-code', async (req, res) => {
  try {
    const { oobCode } = req.body;

    if (!oobCode) {
      return res.status(400).json({
        success: false,
        error: 'oobCode is required'
      });
    }

    // Verify the code is valid by getting the email associated with it
    const email = await admin.auth().verifyPasswordResetCode(oobCode);

    res.json({
      success: true,
      message: 'Reset code is valid',
      email: email
    });

  } catch (error) {
    console.error('Verify reset code error:', error);
    
    if (error.code === 'auth/invalid-action-code') {
      return res.status(400).json({
        success: false,
        error: 'Invalid or expired password reset code'
      });
    }

    res.status(500).json({
      success: false,
      error: 'Failed to verify reset code'
    });
  }
});

/**
 * POST /api/auth/confirm-password-reset
 * Complete password reset with new password
 * Body: { oobCode, newPassword }
 */
router.post('/confirm-password-reset', async (req, res) => {
  try {
    const { oobCode, newPassword } = req.body;

    if (!oobCode || !newPassword) {
      return res.status(400).json({
        success: false,
        error: 'oobCode and newPassword are required'
      });
    }

    if (newPassword.length < 6) {
      return res.status(400).json({
        success: false,
        error: 'Password must be at least 6 characters'
      });
    }

    // Confirm the password reset
    const email = await admin.auth().confirmPasswordReset(oobCode, newPassword);

    console.log(`✅ Password reset confirmed for ${email}`);

    res.json({
      success: true,
      message: 'Password has been reset successfully',
      email: email
    });

  } catch (error) {
    console.error('Confirm password reset error:', error);
    
    if (error.code === 'auth/invalid-action-code') {
      return res.status(400).json({
        success: false,
        error: 'Invalid or expired password reset code'
      });
    }

    if (error.code === 'auth/invalid-password') {
      return res.status(400).json({
        success: false,
        error: 'Password is invalid. Use a stronger password.'
      });
    }

    res.status(500).json({
      success: false,
      error: 'Failed to reset password'
    });
  }
});

module.exports = router;
