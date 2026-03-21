const express = require('express');
const router = express.Router();
const Post = require('../models/Post');
const Report = require('../models/Report');
const User = require('../models/User');
const Notification = require('../models/Notification');
const adminAuth = require('../middleware/adminAuth');

router.use(adminAuth);

// Helper function to send notification to reporter
async function sendNotificationToReporter(userId, message, type, reportId, postId) {
  try {
    const notification = new Notification({
      notificationId: `notif_${Date.now()}_${Math.random().toString(36).substring(2, 10)}`,
      userId: userId,
      fromUserId: 'system',
      fromUserName: 'PawSociety Admin',
      fromUserImage: '',
      type: type,
      postId: postId || '',
      message: message,
      isRead: false
    });
    await notification.save();
    
    // Emit socket event for real-time
    const io = req.app.get('io');
    if (io) {
      io.to(userId).emit('new-notification', {
        notificationId: notification.notificationId,
        message: message,
        type: type,
        fromUserName: 'PawSociety Admin',
        createdAt: notification.createdAt
      });
    }
    
    return true;
  } catch (error) {
    console.error('Failed to send notification:', error);
    return false;
  }
}

// GET all posts with reports
router.get('/', async (req, res) => {
  try {
    const posts = await Post.find().sort({ createdAt: -1 }).lean();
    const reports = await Report.find().lean();
    
    const reportsByPost = {};
    reports.forEach(r => {
      if (r.postId) {
        if (!reportsByPost[r.postId]) reportsByPost[r.postId] = [];
        reportsByPost[r.postId].push(r);
      }
    });
    
    const formattedPosts = posts.map(post => ({
      id: post._id,
      postId: post.postId,
      petName: post.petName,
      petType: post.petType,
      category: post.category,
      gender: post.gender,
      age: post.age,
      weight: post.weight,
      status: post.status,
      userName: post.userName,
      location: post.location,
      description: post.description,
      contact: post.contactInfo,
      reward: post.reward,
      time: new Date(post.createdAt).toLocaleDateString(),
      createdAt: post.createdAt,
      userAvatar: post.userName?.charAt(0).toUpperCase() || '?',
      imageUrls: post.imageUrls || [],
      reported: (reportsByPost[post.postId] || []).length > 0,
      reportCount: (reportsByPost[post.postId] || []).length,
      reports: reportsByPost[post.postId] || []
    }));
    
    res.json(formattedPosts);
  } catch (error) {
    console.error('Get posts error:', error);
    res.status(500).json({ error: error.message });
  }
});

// GET single post
router.get('/:id', async (req, res) => {
  try {
    const post = await Post.findById(req.params.id).lean();
    if (!post) {
      return res.status(404).json({ error: 'Post not found' });
    }
    res.json(post);
  } catch (error) {
    console.error('Get post error:', error);
    res.status(500).json({ error: error.message });
  }
});

// DELETE post
router.delete('/:id', async (req, res) => {
  try {
    const post = await Post.findByIdAndDelete(req.params.id);
    if (!post) {
      return res.status(404).json({ error: 'Post not found' });
    }
    await Report.deleteMany({ postId: post.postId });
    res.json({ message: 'Post deleted', id: req.params.id });
  } catch (error) {
    console.error('Delete post error:', error);
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;