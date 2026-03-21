const express = require('express');
const router = express.Router();
const Follow = require('../models/Follow');
const User = require('../models/User');
const Notification = require('../models/Notification');
const { v4: uuidv4 } = require('uuid');

// ========== GET FOLLOWERS ==========
router.get('/followers/:userId', async (req, res) => {
  try {
    const follows = await Follow.find({ followingUid: req.params.userId });
    const users = [];
    
    for (const f of follows) {
      const user = await User.findOne({ firebaseUid: f.followerUid })
        .select('username fullName profileImageUrl bio firebaseUid');
      if (user) users.push(user);
    }
    
    res.json({ 
      success: true, 
      count: users.length,
      users: users 
    });
  } catch (error) {
    console.error('Get followers error:', error);
    res.status(500).json({ success: false, message: error.message });
  }
});

// ========== GET FOLLOWING ==========
router.get('/following/:userId', async (req, res) => {
  try {
    const follows = await Follow.find({ followerUid: req.params.userId });
    const users = [];
    
    for (const f of follows) {
      const user = await User.findOne({ firebaseUid: f.followingUid })
        .select('username fullName profileImageUrl bio firebaseUid');
      if (user) users.push(user);
    }
    
    res.json({ 
      success: true, 
      count: users.length,
      users: users 
    });
  } catch (error) {
    console.error('Get following error:', error);
    res.status(500).json({ success: false, message: error.message });
  }
});

// ========== FOLLOW A USER - UPDATED WITH SOCKET EMIT ==========
router.post('/follow', async (req, res) => {
  try {
    const { followerUid, followingUid } = req.body;

    if (!followerUid || !followingUid) {
      return res.status(400).json({
        success: false,
        message: 'followerUid and followingUid are required'
      });
    }

    // Cannot follow yourself
    if (followerUid === followingUid) {
      return res.status(400).json({
        success: false,
        message: 'Cannot follow yourself'
      });
    }

    // Check if already following
    const existing = await Follow.findOne({ followerUid, followingUid });
    if (existing) {
      return res.status(400).json({
        success: false,
        message: 'Already following this user'
      });
    }

    // Check if both users exist
    const follower = await User.findOne({ firebaseUid: followerUid });
    const following = await User.findOne({ firebaseUid: followingUid });

    if (!follower || !following) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Create follow
    const follow = new Follow({
      followId: `follow_${Date.now()}_${uuidv4().substring(0, 8)}`,
      followerUid,
      followingUid
    });

    await follow.save();
    console.log(`✅ User ${followerUid} followed ${followingUid}`);

    // 🔥 FIXED: Emit socket event with username
    const io = req.app.get('io');
    if (io) {
      io.to(followingUid).emit('new-follow', {
        fromUserId: followerUid,
        fromUserName: follower.username,
        type: 'follow'
      });
    }

    // Create notification
    try {
      const Notification = require('../models/Notification');
      
      const notification = new Notification({
        notificationId: `notif_${Date.now()}_${uuidv4().substring(0, 8)}`,
        userId: followingUid,
        fromUserId: followerUid,
        fromUserName: follower.username,
        fromUserImage: follower.profileImageUrl || '',
        type: 'follow',
        postId: '',
        message: `${follower.username} started following you`
      });
      
      await notification.save();
      
      if (io) {
        io.to(followingUid).emit('new-notification', {
          notificationId: notification.notificationId,
          message: notification.message,
          type: notification.type,
          fromUserName: follower.username,
          createdAt: notification.createdAt
        });
      }
      
    } catch (notifError) {
      console.error('Failed to create notification:', notifError);
    }

    res.status(201).json({
      success: true,
      message: 'Followed successfully',
      data: follow
    });
  } catch (error) {
    console.error('Follow error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

// ========== UNFOLLOW A USER ==========
router.delete('/unfollow', async (req, res) => {
  try {
    const { followerUid, followingUid } = req.query;
    console.log(`🗑️ Unfollow request - follower: ${followerUid}, following: ${followingUid}`);

    if (!followerUid || !followingUid) {
      return res.status(400).json({
        success: false,
        message: 'followerUid and followingUid are required'
      });
    }

    const follow = await Follow.findOneAndDelete({
      followerUid,
      followingUid
    });

    if (!follow) {
      return res.status(404).json({
        success: false,
        message: 'Follow relationship not found'
      });
    }

    console.log(`✅ Unfollowed successfully`);
    res.json({
      success: true,
      message: 'Unfollowed successfully'
    });
  } catch (error) {
    console.error('Unfollow error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

// ========== CHECK FOLLOW STATUS ==========
router.get('/check', async (req, res) => {
  try {
    const { followerUid, followingUid } = req.query;

    if (!followerUid || !followingUid) {
      return res.status(400).json({
        success: false,
        message: 'followerUid and followingUid are required'
      });
    }

    const follow = await Follow.findOne({ followerUid, followingUid });

    res.json({
      success: true,
      isFollowing: !!follow
    });
  } catch (error) {
    console.error('Check follow error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

// ========== GET FOLLOW COUNTS ==========
router.get('/counts/:userId', async (req, res) => {
  try {
    const followersCount = await Follow.countDocuments({ followingUid: req.params.userId });
    const followingCount = await Follow.countDocuments({ followerUid: req.params.userId });

    res.json({
      success: true,
      followersCount,
      followingCount
    });
  } catch (error) {
    console.error('Get counts error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;