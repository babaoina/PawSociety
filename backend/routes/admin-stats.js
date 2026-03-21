const express = require('express');
const router = express.Router();
const User = require('../models/User');
const Post = require('../models/Post');
const Report = require('../models/Report');
const adminAuth = require('../middleware/adminAuth');

router.use(adminAuth);

// Get dashboard stats
router.get('/', async (req, res) => {
  try {
    const totalUsers = await User.countDocuments();
    const totalPosts = await Post.countDocuments();
    const lostPets = await Post.countDocuments({ status: 'Lost' });
    const foundPets = await Post.countDocuments({ status: 'Found' });
    const adoptions = await Post.countDocuments({ status: 'Adoption' });
    
    // Get user growth (last 7 months)
    const userGrowth = [];
    const today = new Date();
    for (let i = 6; i >= 0; i--) {
      const date = new Date(today.getFullYear(), today.getMonth() - i, 1);
      const nextDate = new Date(today.getFullYear(), today.getMonth() - i + 1, 1);
      const count = await User.countDocuments({
        createdAt: { $gte: date, $lt: nextDate }
      });
      userGrowth.push(count);
    }
    
    // Get recent activity (last 5 posts)
    const recentPosts = await Post.find()
      .sort({ createdAt: -1 })
      .limit(5)
      .lean();
    
    const recentActivity = recentPosts.map(post => ({
      type: post.status === 'Lost' ? 'lost' : post.status === 'Found' ? 'found' : 'adoption',
      text: `${post.petName} - ${post.status} post by ${post.userName}`,
      time: new Date(post.createdAt).toLocaleDateString(),
      status: 'new'
    }));
    
    res.json({
      totalUsers,
      totalPosts,
      lostPets,
      foundPets,
      adoptions,
      userGrowth,
      postsByStatus: {
        lost: lostPets,
        found: foundPets,
        adoption: adoptions
      },
      recentActivity
    });
  } catch (error) {
    console.error('Stats error:', error);
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;