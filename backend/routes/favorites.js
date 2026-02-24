const express = require('express');
const router = express.Router();
const Favorite = require('../models/Favorite');
const Post = require('../models/Post');

/**
 * GET /api/favorites/:firebaseUid
 * Get all favorites for a user
 */
router.get('/:firebaseUid', async (req, res) => {
  try {
    const favorites = await Favorite.find({ userUid: req.params.firebaseUid })
      .sort({ createdAt: -1 });

    // Get post details for each favorite
    const posts = await Promise.all(
      favorites.map(async (fav) => {
        const post = await Post.findOne({ postId: fav.postId });
        return post;
      })
    );

    const validPosts = posts.filter(p => p !== null);

    res.json({
      success: true,
      count: validPosts.length,
      posts: validPosts
    });
  } catch (error) {
    console.error('Get favorites error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/favorites
 * Add to favorites
 * Body: { userUid, postId }
 */
router.post('/', async (req, res) => {
  try {
    const { userUid, postId } = req.body;

    if (!userUid || !postId) {
      return res.status(400).json({
        success: false,
        message: 'userUid and postId are required'
      });
    }

    // Check if already favorited
    const existing = await Favorite.findOne({ userUid, postId });
    if (existing) {
      return res.status(400).json({
        success: false,
        message: 'Already in favorites'
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

    const favorite = new Favorite({ userUid, postId });
    await favorite.save();

    res.status(201).json({
      success: true,
      message: 'Added to favorites',
      data: favorite
    });
  } catch (error) {
    console.error('Add favorite error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/favorites/:postId
 * Remove from favorites
 */
router.delete('/:postId', async (req, res) => {
  try {
    const { userUid } = req.body;

    const favorite = await Favorite.findOneAndDelete({
      userUid,
      postId: req.params.postId
    });

    if (!favorite) {
      return res.status(404).json({
        success: false,
        message: 'Favorite not found'
      });
    }

    res.json({
      success: true,
      message: 'Removed from favorites'
    });
  } catch (error) {
    console.error('Remove favorite error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/favorites/check/:postId
 * Check if post is in favorites
 */
router.get('/check/:postId', async (req, res) => {
  try {
    const { userUid } = req.query;

    if (!userUid) {
      return res.status(400).json({
        success: false,
        message: 'userUid is required'
      });
    }

    const favorite = await Favorite.findOne({
      userUid,
      postId: req.params.postId
    });

    res.json({
      success: true,
      isFavorite: !!favorite
    });
  } catch (error) {
    console.error('Check favorite error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;
