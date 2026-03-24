const express = require('express');
const router = express.Router();
const User = require('../models/User');
const { handleError, formatErrorMessage } = require('../utils/errorHandler');

/**
 * GET /api/users
 * Get all users (for suggestions, inbox, etc.)
 */
router.get('/', async (req, res) => {
  try {
    const { limit = 50, skip = 0 } = req.query;
    
    const users = await User.find()
      .select('username email fullName phone profileImageUrl bio location createdAt firebaseUid')
      .limit(parseInt(limit))
      .skip(parseInt(skip))
      .sort({ createdAt: -1 });

    res.json({
      success: true,
      count: users.length,
      users
    });
  } catch (error) {
    console.error('Get users error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/users/search
 * Search users by username or full name
 * Query: q (search query), limit, skip
 */
router.get('/search', async (req, res) => {
  try {
    const { q, limit = 50, skip = 0 } = req.query;
    
    if (!q || q.length < 2) {
      return res.json({
        success: true,
        count: 0,
        users: []
      });
    }
    
    const query = {
      $or: [
        { username: { $regex: q, $options: 'i' } },
        { fullName: { $regex: q, $options: 'i' } }
      ]
    };
    
    const users = await User.find(query)
      .select('username fullName profileImageUrl bio firebaseUid')
      .limit(parseInt(limit))
      .skip(parseInt(skip))
      .sort({ username: 1 });
    
    res.json({
      success: true,
      count: users.length,
      users
    });
  } catch (error) {
    console.error('Search users error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/users/batch
 * Get multiple users by their UIDs (for blocked users, etc.)
 * Query: userIds (comma-separated list)
 */
router.get('/batch', async (req, res) => {
  try {
    const { userIds } = req.query;
    
    if (!userIds) {
      return res.status(400).json({
        success: false,
        message: 'userIds are required'
      });
    }

    const uidList = userIds.split(',');
    console.log(`🔍 Batch fetching users: ${uidList}`);
    
    const users = await User.find({ 
      firebaseUid: { $in: uidList } 
    }).select('firebaseUid username fullName profileImageUrl');
    
    console.log(`✅ Found ${users.length} users`);
    
    // Create a map for easy lookup
    const userMap = {};
    users.forEach(user => {
      userMap[user.firebaseUid] = {
        username: user.username,
        fullName: user.fullName,
        profileImageUrl: user.profileImageUrl || ''
      };
      console.log(`📦 Mapped: ${user.firebaseUid} -> ${user.username}`);
    });

    // For any UID not found, add a placeholder
    uidList.forEach(uid => {
      if (!userMap[uid]) {
        console.log(`⚠️ User not found in database: ${uid}`);
        userMap[uid] = {
          username: 'Unknown User',
          fullName: 'Unknown User',
          profileImageUrl: ''
        };
      }
    });

    res.json({
      success: true,
      users: userMap
    });
  } catch (error) {
    console.error('❌ Batch get users error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/users/:firebaseUid
 * Get user by Firebase UID
 */
router.get('/:firebaseUid', async (req, res) => {
  try {
    const { firebaseUid } = req.params;
    console.log(`🔍 Looking up user by Firebase UID: ${firebaseUid}`);
    
    const user = await User.findOne({ firebaseUid });

    if (!user) {
      console.log(`❌ User not found with UID: ${firebaseUid}`);
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    console.log(`✅ User found: ${user.username}`);
    
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
    console.error('❌ Get user error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/users/username/:username
 * Get user by username
 */
router.get('/username/:username', async (req, res) => {
  try {
    const user = await User.findOne({ username: req.params.username });

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
    console.error('Get user by username error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * PUT /api/users/:firebaseUid
 * Update user profile
 */
router.put('/:firebaseUid', async (req, res) => {
  try {
    const { username, fullName, bio, profileImageUrl, phone, location } = req.body;

    // Check if username is taken by another user
    if (username) {
      const existingUser = await User.findOne({ 
        username, 
        firebaseUid: { $ne: req.params.firebaseUid } 
      });
      
      if (existingUser) {
        return res.status(400).json({
          success: false,
          message: 'Username already taken'
        });
      }
    }

    const user = await User.findOneAndUpdate(
      { firebaseUid: req.params.firebaseUid },
      { username, fullName, bio, profileImageUrl, phone, location },
      { new: true, runValidators: true }
    );

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      message: 'Profile updated',
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
    handleError(error, res, 500, 'Update user profile');
  }
});

/**
 * PUT /api/users/:firebaseUid/fcm-token
 * Save FCM token for user
 */
router.put('/:firebaseUid/fcm-token', async (req, res) => {
  try {
    const { fcmToken } = req.body;
    
    if (!fcmToken) {
      return res.status(400).json({
        success: false,
        message: 'fcmToken is required'
      });
    }

    const user = await User.findOneAndUpdate(
      { firebaseUid: req.params.firebaseUid },
      { fcmToken },
      { new: true }
    );

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      message: 'FCM token saved'
    });
  } catch (error) {
    console.error('Save FCM token error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/users/:firebaseUid
 * Delete user account
 */
router.delete('/:firebaseUid', async (req, res) => {
  try {
    const user = await User.findOneAndDelete({ firebaseUid: req.params.firebaseUid });

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      message: 'User deleted'
    });
  } catch (error) {
    console.error('Delete user error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/users/:firebaseUid/notification-settings
 * Get user's notification settings
 */
router.get('/:firebaseUid/notification-settings', async (req, res) => {
  try {
    const { firebaseUid } = req.params;

    const user = await User.findOne({ firebaseUid }).select('notificationSettings');
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      settings: user.notificationSettings || {
        postsLikes: true,
        postComments: true,
        follows: true,
        messages: true,
        highlightedPosts: true,
        announcements: true
      }
    });
  } catch (error) {
    console.error('Get notification settings error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * PUT /api/users/:firebaseUid/notification-settings
 * Update user's notification settings
 * Body: { settingKey, value } or { notificationSettings: {...} }
 */
router.put('/:firebaseUid/notification-settings', async (req, res) => {
  try {
    const { firebaseUid } = req.params;
    const { settingKey, value, notificationSettings } = req.body;

    const user = await User.findOne({ firebaseUid });
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Update single setting or entire settings object
    if (notificationSettings) {
      user.notificationSettings = notificationSettings;
    } else if (settingKey && value !== undefined) {
      if (!user.notificationSettings) {
        user.notificationSettings = {};
      }
      user.notificationSettings[settingKey] = value;
    }

    await user.save();

    console.log(`✅ Notification settings updated for ${firebaseUid}`);

    res.json({
      success: true,
      settings: user.notificationSettings
    });
  } catch (error) {
    console.error('Update notification settings error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/users/:firebaseUid/security-settings
 * Get user's security settings
 */
router.get('/:firebaseUid/security-settings', async (req, res) => {
  try {
    const { firebaseUid } = req.params;

    const user = await User.findOne({ firebaseUid }).select('securitySettings');
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      settings: user.securitySettings || {
        loginAlerts: true,
        suspiciousActivityAlerts: true
      }
    });
  } catch (error) {
    console.error('Get security settings error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * PUT /api/users/:firebaseUid/security-settings
 * Update user's security settings
 * Body: { settingKey, value } or { securitySettings: {...} }
 */
router.put('/:firebaseUid/security-settings', async (req, res) => {
  try {
    const { firebaseUid } = req.params;
    const { settingKey, value, securitySettings } = req.body;

    const user = await User.findOne({ firebaseUid });
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Update single setting or entire settings object
    if (securitySettings) {
      user.securitySettings = securitySettings;
    } else if (settingKey && value !== undefined) {
      if (!user.securitySettings) {
        user.securitySettings = {};
      }
      user.securitySettings[settingKey] = value;
    }

    await user.save();

    console.log(`✅ Security settings updated for ${firebaseUid}`);

    res.json({
      success: true,
      settings: user.securitySettings
    });
  } catch (error) {
    console.error('Update security settings error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;