const express = require('express');
const router = express.Router();
const Comment = require('../models/Comment');
const Post = require('../models/Post');
const User = require('../models/User');
const Notification = require('../models/Notification');
const { v4: uuidv4 } = require('uuid');

/**
 * GET /api/comments/post/:postId
 * Get all comments for a post
 */
router.get('/post/:postId', async (req, res) => {
  try {
    const comments = await Comment.find({ postId: req.params.postId })
      .sort({ createdAt: -1 });

    res.json({
      success: true,
      count: comments.length,
      comments
    });
  } catch (error) {
    console.error('Get comments error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/comments
 * Add a comment to a post
 * Body: { postId, firebaseUid, userName, text }
 */
router.post('/', async (req, res) => {
  try {
    const { postId, firebaseUid, userName, text } = req.body;

    if (!postId || !firebaseUid || !userName || !text) {
      return res.status(400).json({
        success: false,
        message: 'Missing required fields'
      });
    }

    // Verify post exists
    const post = await Post.findOne({ postId });
    if (!post) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    // Get user info to fetch profile image
    const user = await User.findOne({ firebaseUid });
    const userImageUrl = user ? user.profileImageUrl : '';
    console.log(`📝 Creating comment - User: ${userName}, Image: ${userImageUrl}`);

    // Create comment
    const comment = new Comment({
      commentId: `comment_${Date.now()}_${uuidv4().substring(0, 8)}`,
      postId,
      firebaseUid,
      userName,
      userImageUrl,
      text
    });

    await comment.save();

    // Update post comments count
    await Post.findOneAndUpdate(
      { postId },
      { $inc: { commentsCount: 1 } }
    );

    // Create notification for post owner
    if (post && post.firebaseUid !== firebaseUid) {
      const postOwner = await User.findOne({ firebaseUid: post.firebaseUid });
      const commenter = await User.findOne({ firebaseUid });
      const notification = new Notification({
        notificationId: `notif_${Date.now()}_${uuidv4().substring(0, 8)}`,
        userId: post.firebaseUid,
        fromUserId: firebaseUid,
        fromUserName: commenter ? commenter.username : 'Someone',
        fromUserImage: commenter ? commenter.profileImageUrl : '',
        type: 'comment',
        postId: post.postId,
        message: `${commenter ? commenter.username : 'Someone'} commented on your post`
      });
      await notification.save();
      console.log(`🔔 Notification created: ${notification.message}`);
    }

    res.status(201).json({
      success: true,
      message: 'Comment added',
      data: comment
    });
  } catch (error) {
    console.error('Create comment error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/comments/:commentId
 * Delete a comment
 */
router.delete('/:commentId', async (req, res) => {
  try {
    const { firebaseUid } = req.body;

    const comment = await Comment.findOneAndDelete({
      commentId: req.params.commentId,
      firebaseUid
    });

    if (!comment) {
      return res.status(404).json({
        success: false,
        message: 'Comment not found or unauthorized'
      });
    }

    // Update post comments count
    await Post.findOneAndUpdate(
      { postId: comment.postId },
      { $inc: { commentsCount: -1 } }
    );

    res.json({
      success: true,
      message: 'Comment deleted'
    });
  } catch (error) {
    console.error('Delete comment error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/comments/:commentId/like
 * Like/unlike a comment
 */
router.post('/:commentId/like', async (req, res) => {
  try {
    const { firebaseUid } = req.body;

    if (!firebaseUid) {
      return res.status(400).json({
        success: false,
        message: 'firebaseUid is required'
      });
    }

    const comment = await Comment.findOne({ commentId: req.params.commentId });

    if (!comment) {
      return res.status(404).json({
        success: false,
        message: 'Comment not found'
      });
    }

    const likedIndex = comment.likedBy.indexOf(firebaseUid);

    if (likedIndex > -1) {
      // Unlike
      comment.likedBy.splice(likedIndex, 1);
      comment.likesCount = Math.max(0, comment.likesCount - 1);
    } else {
      // Like
      comment.likedBy.push(firebaseUid);
      comment.likesCount += 1;
    }

    await comment.save();

    console.log(`❤️ Comment like - liked: ${likedIndex === -1}, count: ${comment.likesCount}`);

    res.json({
      success: true,
      liked: likedIndex === -1,
      likesCount: comment.likesCount
    });
  } catch (error) {
    console.error('Like comment error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;
