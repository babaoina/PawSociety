const express = require('express');
const router = express.Router();
const Follow = require('../models/Follow');
const User = require('../models/User');
const Notification = require('../models/Notification');
const { v4: uuidv4 } = require('uuid');

/**
 * GET /api/follow/followers/:userId
 * Get all followers of a user
 */
router.get('/followers/:userId', async (req, res) => {
  try {
    const follows = await Follow.find({ followingUid: req.params.userId })
      .sort({ createdAt: -1 });

    // Get user details for each follower
    const followers = await Promise.all(
      follows.map(async (follow) => {
        const user = await User.findOne({ firebaseUid: follow.followerUid })
          .select('username fullName profileImageUrl bio firebaseUid');
        return user;
      })
    );

    const validFollowers = followers.filter(f => f !== null);

    res.json({
      success: true,
      count: validFollowers.length,
      users: validFollowers
    });
  } catch (error) {
    console.error('Get followers error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/follow/following/:userId
 * Get all users that a user is following
 */
router.get('/following/:userId', async (req, res) => {
  try {
    const follows = await Follow.find({ followerUid: req.params.userId })
      .sort({ createdAt: -1 });

    // Get user details for each following
    const following = await Promise.all(
      follows.map(async (follow) => {
        const user = await User.findOne({ firebaseUid: follow.followingUid })
          .select('username fullName profileImageUrl bio firebaseUid');
        return user;
      })
    );

    const validFollowing = following.filter(f => f !== null);

    res.json({
      success: true,
      count: validFollowing.length,
      users: validFollowing
    });
  } catch (error) {
    console.error('Get following error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/follow/follow
 * Follow a user
 * Body: { followerUid, followingUid }
 */
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

    // Create notification for the user being followed
    const notification = new Notification({
      notificationId: `notif_${Date.now()}_${uuidv4().substring(0, 8)}`,
      userId: followingUid,
      fromUserId: followerUid,
      fromUserName: follower.username,
      fromUserImage: follower.profileImageUrl || '',
      type: 'follow',
      message: `${follower.username} started following you`
    });
    await notification.save();

    console.log(`🔔 Follow notification created: ${follower.username} -> ${following.username}`);

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

/**
 * DELETE /api/follow/unfollow
 * Unfollow a user
 * Query: followerUid, followingUid
 */
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

/**
 * GET /api/follow/check
 * Check if a user is following another user
 * Query: followerUid, followingUid
 */
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

/**
 * GET /api/follow/counts/:userId
 * Get follower and following counts for a user
 */
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