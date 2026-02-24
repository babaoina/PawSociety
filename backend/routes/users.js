const express = require('express');
const router = express.Router();
const User = require('../models/User');

/**
 * GET /api/users
 * Get all users (for suggestions, inbox, etc.)
 */
router.get('/', async (req, res) => {
  try {
    const { limit = 50, skip = 0 } = req.query;
    
    const users = await User.find()
      .select('username email fullName phone profileImageUrl bio location createdAt firebaseUid')  // Include firebaseUid
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
 * GET /api/users/:firebaseUid
 * Get user by Firebase UID
 */
router.get('/:firebaseUid', async (req, res) => {
  try {
    const { firebaseUid } = req.params;
    console.log(`🔍 Looking up user by Firebase UID: ${firebaseUid}`);
    
    const user = await User.findOne({ firebaseUid })
      .select('-firebaseUid');

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
      user
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
    const user = await User.findOne({ username: req.params.username })
      .select('-firebaseUid');

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      user
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
    ).select('-firebaseUid');

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    res.json({
      success: true,
      message: 'Profile updated',
      user
    });
  } catch (error) {
    console.error('Update user error:', error);
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

    // TODO: Also delete user's posts, comments, messages, etc.

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

module.exports = router;
