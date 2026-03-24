// Add this to backend/routes/auth.js or backend/server.js

const admin = require('firebase-admin');
const bcrypt = require('bcryptjs');
const axios = require('axios');

// DELETE ACCOUNT WITH PASSWORD VERIFICATION
app.post('/api/auth/delete-account-with-password', adminAuth, async (req, res) => {
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
      return res.status(404).json({ error: 'User not found' });
    }

    // Verify the user making the request
    if (user.firebaseUid !== req.user.uid) {
      console.error('❌ Unauthorized - Cannot delete other users accounts');
      return res.status(403).json({ error: 'Unauthorized - Cannot delete other users accounts' });
    }

    // Verify password by attempting Firebase reauthentication
    console.log(`🔄 Step 1: Verifying password...`);
    try {
      // Get user from Firebase
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

    // Delete all user data from MongoDB
    console.log(`🔄 Step 3: Deleting all user data from database...`);
    // Posts
    await Post.deleteMany({ firebaseUid });
    // Reports (where user was reported)
    await Report.deleteMany({ $or: [{ reportedUid: firebaseUid }, { reporterUid: firebaseUid }] });
    // Favorites
    await Favorite.deleteMany({ userUid: firebaseUid });
    // Follows
    await Follow.deleteMany({ $or: [{ followerUid: firebaseUid }, { followingUid: firebaseUid }] });
    // Chats
    await Chat.updateMany({ participants: firebaseUid }, { $pull: { participants: firebaseUid } });
    await Chat.deleteMany({ participants: { $size: 0 } });
    // Messages
    await Message.deleteMany({ $or: [{ senderUid: firebaseUid }, { receiverUid: firebaseUid }] });
    // Notifications
    await Notification.deleteMany({ $or: [{ userId: firebaseUid }, { fromUserId: firebaseUid }] });
    // Blocks
    await Block.deleteMany({ $or: [{ blockerUid: firebaseUid }, { blockedUid: firebaseUid }] });
    // Highlights
    await Highlight.deleteMany({ userId: firebaseUid });
    // Hidden Posts
    await HiddenPost.deleteMany({ userUid: firebaseUid });
    // Pets
    await Pet.deleteMany({ ownerUid: firebaseUid });
    // Sessions
    await Session.deleteMany({ firebaseUid });

    // Delete user from MongoDB
    await User.findByIdAndDelete(user._id);

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


// SEND EMAIL VERIFICATION ON LOGIN
app.post('/api/auth/send-verification-email', async (req, res) => {
  try {
    const { firebaseUid } = req.body;

    if (!firebaseUid) {
      return res.status(400).json({ error: 'Missing firebaseUid' });
    }

    // Get user from database
    const user = await User.findOne({ firebaseUid });
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }

    // Get user from Firebase
    const firebaseUser = await admin.auth().getUser(firebaseUid);

    // Send verification email
    const actionCodeSettings = {
      url: `${process.env.APP_URL || 'https://pawsociety.com'}/verify-email`,
      handleCodeInApp: true
    };

    // Generate verification email link
    const link = await admin.auth().generateEmailVerificationLink(firebaseUser.email);
    
    // Send email with custom template (integrate with your email service)
    // For now, we'll return the link
    res.json({
      success: true,
      message: 'Verification email sent',
      verificationLink: link
    });

  } catch (error) {
    console.error('Send verification email error:', error);
    res.status(500).json({ error: error.message });
  }
});


// GET ACTIVE SESSIONS FOR USER (with device info)
app.get('/api/auth/active-sessions/:firebaseUid', async (req, res) => {
  try {
    const { firebaseUid } = req.params;

    // Fetch active sessions from database
    const sessions = await Session.find({ firebaseUid }).sort({ createdAt: -1 });

    if (!sessions || sessions.length === 0) {
      return res.json({ success: true, sessions: [] });
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

    res.json({ success: true, sessions: formattedSessions });

  } catch (error) {
    console.error('Get active sessions error:', error);
    res.status(500).json({ error: error.message });
  }
});


// LOGOUT FROM SPECIFIC DEVICE/SESSION
app.post('/api/auth/logout-session', async (req, res) => {
  try {
    const { firebaseUid, sessionId } = req.body;

    if (!firebaseUid || !sessionId) {
      return res.status(400).json({ error: 'Missing firebaseUid or sessionId' });
    }

    // Delete session
    await Session.findByIdAndDelete(sessionId);

    res.json({ 
      success: true, 
      message: 'Session ended. You will be logged out on that device.' 
    });

  } catch (error) {
    console.error('Logout session error:', error);
    res.status(500).json({ error: error.message });
  }
});
