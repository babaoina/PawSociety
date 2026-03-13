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
// In backend/routes/posts.js - UPDATE THIS EXISTING FUNCTION
router.get('/', async (req, res) => {
  try {
    const { status, firebaseUid, petCategory, limit = 50, skip = 0 } = req.query;  // ADD petCategory
    
    const query = {};
    if (status) query.status = status;
    if (firebaseUid) query.firebaseUid = firebaseUid;
    
    // ADD THIS - Filter by pet category
    if (petCategory && petCategory !== 'All') {
      // Create regex to match petType field
      query.petType = { $regex: petCategory, $options: 'i' };
    }

    const posts = await Post.find(query)
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
 * GET /api/posts/search
 * Search posts by keyword
 */
router.get('/search', async (req, res) => {
  try {
    const { q, status, limit = 50, skip = 0 } = req.query;
    
    console.log(`🔍 Search request: query="${q}", status=${status}`);
    
    if (!q || q.length < 2) {
      return res.json({
        success: true,
        count: 0,
        posts: []
      });
    }
    
    const searchQuery = {
      $or: [
        { petName: { $regex: q, $options: 'i' } },
        { petType: { $regex: q, $options: 'i' } },
        { description: { $regex: q, $options: 'i' } },
        { location: { $regex: q, $options: 'i' } },
        { userName: { $regex: q, $options: 'i' } }
      ]
    };
    
    if (status && status !== 'All' && status !== 'all') {
      searchQuery.status = status;
    }
    
    console.log('🔍 Search query:', JSON.stringify(searchQuery));
    
    const posts = await Post.find(searchQuery)
      .limit(parseInt(limit))
      .skip(parseInt(skip))
      .sort({ createdAt: -1 });
    
    console.log(`✅ Found ${posts.length} posts matching "${q}"`);
    
    res.json({
      success: true,
      count: posts.length,
      posts: posts
    });
    
  } catch (error) {
    console.error('❌ Search error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

// ===== HIDE POST FEATURE =====

/**
 * POST /api/posts/hide
 * Hide a post for a specific user
 * Query: userUid, postId
 */
router.post('/hide', async (req, res) => {
  try {
    const { userUid, postId } = req.query;
    
    console.log(`📌 Hide post request - user: ${userUid}, post: ${postId}`);
    
    if (!userUid || !postId) {
      return res.status(400).json({
        success: false,
        message: 'userUid and postId are required'
      });
    }

    // Check if post exists
    const post = await Post.findOne({ postId });
    if (!post) {
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    // Import HiddenPost model
    const HiddenPost = require('../models/HiddenPost');
    
    // Create or update hidden post record
    const hiddenPost = await HiddenPost.findOneAndUpdate(
      { userUid, postId },
      { hiddenAt: new Date() },
      { upsert: true, new: true }
    );

    console.log(`✅ Post ${postId} hidden by user ${userUid}`);

    res.json({
      success: true,
      message: 'Post hidden successfully',
      data: hiddenPost
    });
  } catch (error) {
    console.error('❌ Hide post error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/posts/unhide
 * Unhide a post for a specific user
 * Query: userUid, postId
 */
router.post('/unhide', async (req, res) => {
  try {
    const { userUid, postId } = req.query;
    
    console.log(`📌 Unhide post request - user: ${userUid}, post: ${postId}`);
    
    if (!userUid || !postId) {
      return res.status(400).json({
        success: false,
        message: 'userUid and postId are required'
      });
    }

    const HiddenPost = require('../models/HiddenPost');
    
    const result = await HiddenPost.findOneAndDelete({ userUid, postId });

    if (!result) {
      return res.status(404).json({
        success: false,
        message: 'Hidden post not found'
      });
    }

    console.log(`✅ Post ${postId} unhidden by user ${userUid}`);

    res.json({
      success: true,
      message: 'Post unhidden successfully'
    });
  } catch (error) {
    console.error('❌ Unhide post error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/posts/hidden
 * Get all hidden posts for a user
 * Query: userUid
 */
// In your backend routes/posts.js
router.get('/hidden', async (req, res) => {
  try {
    const { userUid } = req.query;
    
    const HiddenPost = require('../models/HiddenPost');
    const hiddenEntries = await HiddenPost.find({ userUid }).sort({ hiddenAt: -1 });
    
    const postIds = hiddenEntries.map(entry => entry.postId);
    const posts = await Post.find({ postId: { $in: postIds } });
    
    // Create a map for quick lookup
    const postMap = {};
    posts.forEach(post => {
      if (post) {  // ← NULL CHECK
        postMap[post.postId] = post;
      }
    });
    
    // Filter out any null posts
    const hiddenPosts = hiddenEntries
      .map(entry => postMap[entry.postId])
      .filter(post => post !== null);  // ← FILTER NULLS

    res.json({
      success: true,
      count: hiddenPosts.length,
      data: hiddenPosts
    });
  } catch (error) {
    console.error('❌ Get hidden posts error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/posts/hidden/count
 * Get count of hidden posts for a user
 * Query: userUid
 */
router.get('/hidden/count', async (req, res) => {
  try {
    const { userUid } = req.query;
    
    console.log(`📌 Get hidden count for user: ${userUid}`);
    
    if (!userUid) {
      return res.status(400).json({
        success: false,
        message: 'userUid is required'
      });
    }

    const HiddenPost = require('../models/HiddenPost');
    
    const count = await HiddenPost.countDocuments({ userUid });

    console.log(`📊 Hidden count: ${count}`);

    res.json({
      success: true,
      count: count
    });
  } catch (error) {
    console.error('❌ Get hidden count error:', error);
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
 * Create a new post with gender
 */
/**
 * POST /api/posts
 * Create a new post with age and weight
 */
router.post('/', async (req, res) => {
  try {
    const { 
      firebaseUid, 
      petName, 
      petType, 
      age,
      weight,
      gender,
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

    // Create post with age and weight
    const post = new Post({
      postId: `post_${Date.now()}_${uuidv4().substring(0, 8)}`,
      firebaseUid,
      userName: user.username,
      userImageUrl: user.profileImageUrl || '',
      petName,
      petType,
      age: age || '',
      weight: weight || '',
      gender: gender || 'Unknown',
      status,
      description,
      location: location || '',
      reward: reward || '',
      contactInfo,
      imageUrls: imageUrls || []
    });

    await post.save();

    console.log(`✅ Post created with age: ${age}, weight: ${weight}, gender: ${gender}`);

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
    const postId = req.params.postId;
    const updateData = req.body;
    
    console.log('========== UPDATE POST REQUEST ==========');
    console.log('Post ID:', postId);
    console.log('Update data:', updateData);
    console.log('=========================================');

    const updatedPost = await Post.findOneAndUpdate(
      { postId: postId },
      { $set: updateData },
      { new: true, runValidators: true }
    );

    if (!updatedPost) {
      console.log('❌ Post not found with ID:', postId);
      return res.status(404).json({
        success: false,
        message: 'Post not found'
      });
    }

    console.log('✅ Post updated successfully:', updatedPost.petName);
    
    res.json({
      success: true,
      message: 'Post updated successfully',
      data: updatedPost
    });
  } catch (error) {
    console.error('❌ Update post error:', error);
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
    const { firebaseUid } = req.query;
    console.log(`🗑️ Delete post request - postId: ${req.params.postId}, firebaseUid: ${firebaseUid}`);

    if (!firebaseUid) {
      return res.status(400).json({
        success: false,
        message: 'firebaseUid is required'
      });
    }

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

    console.log(`✅ Post deleted successfully: ${req.params.postId}`);

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
      post.likedBy.splice(likedIndex, 1);
      post.likesCount = Math.max(0, post.likesCount - 1);
    } else {
      post.likedBy.push(firebaseUid);
      post.likesCount += 1;
    }

    await post.save();

    if (likedIndex === -1) {
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
 * GET /api/posts
 * Get all posts with optional filters
 * Query: status, firebaseUid, petCategory, limit, skip
 */
router.get('/', async (req, res) => {
  try {
    const { status, firebaseUid, petCategory, limit = 50, skip = 0 } = req.query;
    
    const query = {};
    if (status) query.status = status;
    if (firebaseUid) query.firebaseUid = firebaseUid;
    
    // Add pet category filtering
    if (petCategory && petCategory !== 'All') {
      // Map category to petType patterns
      const categoryMap = {
        'Dogs': { $regex: 'dog|puppy|canine|aspin|shih tzu|labrador|golden|german|poodle', $options: 'i' },
        'Cats': { $regex: 'cat|kitten|feline|puspin|persian|siamese', $options: 'i' },
        'Fish': { $regex: 'fish|fishes|aquarium|goldfish|koi|betta', $options: 'i' },
        'Birds': { $regex: 'bird|parrot|lovebird|canary|budgie|macaw|cockatiel', $options: 'i' }
      };
      
      if (categoryMap[petCategory]) {
        query.petType = categoryMap[petCategory];
      }
    }

    const posts = await Post.find(query)
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