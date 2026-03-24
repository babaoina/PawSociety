const express = require('express');
const router = express.Router();
const Highlight = require('../models/Highlight');
const { v4: uuidv4 } = require('uuid');
const { handleError } = require('../utils/errorHandler');

// GET /api/users/:userId/highlights
router.get('/:userId/highlights', async (req, res) => {
  try {
    const highlights = await Highlight.find({ userId: req.params.userId })
      .sort({ createdAt: -1 });

    res.json({
      success: true,
      count: highlights.length,
      highlights
    });
  } catch (error) {
    console.error('Get highlights error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

// POST /api/users/:userId/highlights
// ✅ Only the owner can create highlights for themselves
router.post('/:userId/highlights', async (req, res) => {
  try {
    // Verify ownership - user can only create highlights for their own account
    const authUserId = req.headers['x-user-id'] || req.body.firebaseUid;
    if (authUserId !== req.params.userId) {
      return res.status(403).json({
        success: false,
        message: 'You can only create highlights for your own account'
      });
    }

    const { name, emoji, color, imageUrl, postIds } = req.body;

    if (!name) {
      return res.status(400).json({
        success: false,
        message: 'Highlight name is required'
      });
    }

    const highlight = new Highlight({
      highlightId: `highlight_${Date.now()}_${uuidv4().substring(0, 8)}`,
      userId: req.params.userId,
      name,
      emoji: emoji || '📸',
      color: color || '#FF6B35',
      imageUrl: imageUrl || '',
      postIds: postIds || []
    });

    await highlight.save();
    console.log(`✅ Highlight created: ${highlight.name} for user ${req.params.userId}`);

    res.status(201).json({
      success: true,
      message: 'Highlight created successfully',
      data: highlight
    });
  } catch (error) {
    handleError(error, res, 500, 'Create highlight');
  }
});

// PUT /api/users/:userId/highlights/:highlightId
// ✅ Only the owner can update their highlights
router.put('/:userId/highlights/:highlightId', async (req, res) => {
  try {
    // Verify ownership
    const authUserId = req.headers['x-user-id'] || req.body.firebaseUid;
    if (authUserId !== req.params.userId) {
      return res.status(403).json({
        success: false,
        message: 'You can only edit your own highlights'
      });
    }

    const { name, emoji, color, imageUrl, postIds } = req.body;

    const highlight = await Highlight.findOneAndUpdate(
      { 
        highlightId: req.params.highlightId,
        userId: req.params.userId 
      },
      { name, emoji, color, imageUrl, postIds },
      { new: true, runValidators: true }
    );

    if (!highlight) {
      return res.status(404).json({
        success: false,
        message: 'Highlight not found'
      });
    }

    console.log(`✅ Highlight updated: ${highlight.name}`);

    res.json({
      success: true,
      message: 'Highlight updated successfully',
      data: highlight
    });
  } catch (error) {
    handleError(error, res, 500, 'Update highlight');
  }
});

// DELETE /api/users/:userId/highlights/:highlightId
// ✅ Only the owner can delete their highlights
router.delete('/:userId/highlights/:highlightId', async (req, res) => {
  try {
    // Verify ownership
    const authUserId = req.headers['x-user-id'] || req.query.firebaseUid;
    if (authUserId !== req.params.userId) {
      return res.status(403).json({
        success: false,
        message: 'You can only delete your own highlights'
      });
    }

    const highlight = await Highlight.findOneAndDelete({
      highlightId: req.params.highlightId,
      userId: req.params.userId
    });

    if (!highlight) {
      return res.status(404).json({
        success: false,
        message: 'Highlight not found'
      });
    }

    console.log(`✅ Highlight deleted: ${highlight.name}`);

    res.json({
      success: true,
      message: 'Highlight deleted successfully'
    });
  } catch (error) {
    handleError(error, res, 500, 'Delete highlight');
  }
});

module.exports = router;