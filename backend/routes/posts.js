const express = require('express');
const router = express.Router();
const Post = require('../models/Post');
const User = require('../models/User');
const Notification = require('../models/Notification');
const { v4: uuidv4 } = require('uuid');

/**
 * GET /api/posts
 * Get all posts with optional filters
 * Query: status, firebaseUid, limit, skip
 */
router.get('/', async (req, res) => {
  try {
    const { status, firebaseUid, limit = 50, skip = 0 } = req.query;
    
    const query = {};
    if (status) query.status = status;
    if (firebaseUid) query.firebaseUid = firebaseUid;

    const posts = await Post.find(query)
      .populate('firebaseUid', 'username profileImageUrl')
      .limit(parseInt(limit))
      .skip(parseInt(skip))
      .sort({ createdAt: -1 });

    res.json({
      success: true,
      count: posts.length,
      posts
    });
  } catch (error) {
    console.error('Get posts error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/posts/:postId
 * Get single post by ID
 */
router.get('/:postId', async (req, res) => {
  try {
    const post = await Post.findOne({ postId: req.params.postId });

    if (!post) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    res.json({
      success: true,
      post
    });
  } catch (error) {
    console.error('Get post error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/posts
 * Create a new post
 * Body: { firebaseUid, petName, petType, status, description, location, reward, contactInfo, imageUrls }
 */
router.post('/', async (req, res) => {
  try {
    const { 
      firebaseUid, 
      petName, 
      petType, 
      status, 
      description, 
      location, 
      reward, 
      contactInfo, 
      imageUrls 
    } = req.body;

    // Validate required fields
    if (!firebaseUid || !petName || !petType || !status || !description || !contactInfo) {
      return res.status(400).json({
        success: false,
        message: 'Missing required fields'
      });
    }

    // Get user info
    const user = await User.findOne({ firebaseUid });
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Create post
    const post = new Post({
      postId: `post_${Date.now()}_${uuidv4().substring(0, 8)}`,
      firebaseUid,
      userName: user.username,
      userImageUrl: user.profileImageUrl || '',
      petName,
      petType,
      status,
      description,
      location: location || '',
      reward: reward || '',
      contactInfo,
      imageUrls: imageUrls || []
    });

    await post.save();

    res.status(201).json({
      success: true,
      message: 'Post created',
      data: post
    });
  } catch (error) {
    console.error('Create post error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * PUT /api/posts/:postId
 * Update a post
 */
router.put('/:postId', async (req, res) => {
  try {
    const { firebaseUid } = req.body;
    const updateData = { ...req.body };
    delete updateData.firebaseUid; // Don't allow changing owner

    const post = await Post.findOneAndUpdate(
      { postId: req.params.postId, firebaseUid },
      updateData,
      { new: true, runValidators: true }
    );

    if (!post) {
      return res.status(404).json({
        success: false,
        message: 'Post not found or unauthorized'
      });
    }

    res.json({
      success: true,
      message: 'Post updated',
      post
    });
  } catch (error) {
    console.error('Update post error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/posts/:postId
 * Delete a post
 */
router.delete('/:postId', async (req, res) => {
  try {
    const { firebaseUid } = req.body;

    const post = await Post.findOneAndDelete({ 
      postId: req.params.postId,
      firebaseUid 
    });

    if (!post) {
      return res.status(404).json({
        success: false,
        message: 'Post not found or unauthorized'
      });
    }

    // TODO: Also delete related comments and favorites

    res.json({
      success: true,
      message: 'Post deleted'
    });
  } catch (error) {
    console.error('Delete post error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/posts/:postId/like
 * Like/unlike a post
 */
router.post('/:postId/like', async (req, res) => {
  try {
    const { firebaseUid } = req.body;

    if (!firebaseUid) {
      return res.status(400).json({
        success: false,
        message: 'firebaseUid is required'
      });
    }

    const post = await Post.findOne({ postId: req.params.postId });

    if (!post) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    const likedIndex = post.likedBy.indexOf(firebaseUid);
    
    if (likedIndex > -1) {
      // Unlike
      post.likedBy.splice(likedIndex, 1);
      post.likesCount = Math.max(0, post.likesCount - 1);
    } else {
      // Like
      post.likedBy.push(firebaseUid);
      post.likesCount += 1;
    }

    await post.save();

    // Create notification for post owner
    if (likedIndex === -1) {  // Only notify on like, not unlike
      const postOwner = await User.findOne({ firebaseUid: post.firebaseUid });
      if (postOwner && postOwner.firebaseUid !== firebaseUid) {
        const liker = await User.findOne({ firebaseUid });
        const notification = new Notification({
          notificationId: `notif_${Date.now()}_${uuidv4().substring(0, 8)}`,
          userId: post.firebaseUid,
          fromUserId: firebaseUid,
          fromUserName: liker ? liker.username : 'Someone',
          fromUserImage: liker ? liker.profileImageUrl : '',
          type: 'like',
          postId: post.postId,
          message: `${liker ? liker.username : 'Someone'} liked your post`
        });
        await notification.save();
        console.log(`🔔 Notification created: ${notification.message}`);
      }
    }

    res.json({
      success: true,
      liked: likedIndex === -1,
      likesCount: post.likesCount
    });
  } catch (error) {
    console.error('Like post error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/posts/:postId/is-liked
 * Check if user liked a post
 */
router.get('/:postId/is-liked', async (req, res) => {
  try {
    const { firebaseUid } = req.query;

    if (!firebaseUid) {
      return res.status(400).json({
        success: false,
        message: 'firebaseUid is required'
      });
    }

    const post = await Post.findOne({ postId: req.params.postId });

    if (!post) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    const isLiked = post.likedBy.includes(firebaseUid);

    res.json({
      success: true,
      isLiked,
      likesCount: post.likesCount
    });
  } catch (error) {
    console.error('Check like status error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;
