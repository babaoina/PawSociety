const express = require('express');
const router = express.Router();
const Highlight = require('../models/Highlight');
const { v4: uuidv4 } = require('uuid');

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
router.post('/:userId/highlights', async (req, res) => {
  try {
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

    res.status(201).json({
      success: true,
      message: 'Highlight created',
      data: highlight
    });
  } catch (error) {
    console.error('Create highlight error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

// PUT /api/users/:userId/highlights/:highlightId
router.put('/:userId/highlights/:highlightId', async (req, res) => {
  try {
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

    res.json({
      success: true,
      message: 'Highlight updated',
      data: highlight
    });
  } catch (error) {
    console.error('Update highlight error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

// DELETE /api/users/:userId/highlights/:highlightId
router.delete('/:userId/highlights/:highlightId', async (req, res) => {
  try {
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

    res.json({
      success: true,
      message: 'Highlight deleted'
    });
  } catch (error) {
    console.error('Delete highlight error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;