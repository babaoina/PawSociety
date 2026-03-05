const express = require('express');
const router = express.Router();
const Block = require('../models/Block');
const { v4: uuidv4 } = require('uuid');

/**
 * POST /api/blocks/block
 * Block a user
 * Body: { blockerUid, blockedUid }
 */
router.post('/block', async (req, res) => {
  try {
    const { blockerUid, blockedUid } = req.body;

    if (!blockerUid || !blockedUid) {
      return res.status(400).json({
        success: false,
        message: 'blockerUid and blockedUid are required'
      });
    }

    // Cannot block yourself
    if (blockerUid === blockedUid) {
      return res.status(400).json({
        success: false,
        message: 'Cannot block yourself'
      });
    }

    // Check if already blocked
    const existing = await Block.findOne({ blockerUid, blockedUid });
    if (existing) {
      return res.status(400).json({
        success: false,
        message: 'User already blocked'
      });
    }

    const block = new Block({
      blockId: `block_${Date.now()}_${uuidv4().substring(0, 8)}`,
      blockerUid,
      blockedUid
    });

    await block.save();

    console.log(`🚫 User ${blockerUid} blocked ${blockedUid}`);

    res.status(201).json({
      success: true,
      message: 'User blocked successfully',
      data: block
    });
  } catch (error) {
    console.error('Block error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/blocks/unblock
 * Unblock a user
 * Query: blockerUid, blockedUid
 */
router.delete('/unblock', async (req, res) => {
  try {
    const { blockerUid, blockedUid } = req.query;

    if (!blockerUid || !blockedUid) {
      return res.status(400).json({
        success: false,
        message: 'blockerUid and blockedUid are required'
      });
    }

    const block = await Block.findOneAndDelete({
      blockerUid,
      blockedUid
    });

    if (!block) {
      return res.status(404).json({
        success: false,
        message: 'Block relationship not found'
      });
    }

    console.log(`✅ User ${blockerUid} unblocked ${blockedUid}`);

    res.json({
      success: true,
      message: 'User unblocked successfully'
    });
  } catch (error) {
    console.error('Unblock error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/blocks/check
 * Check if a user is blocked
 * Query: blockerUid, blockedUid
 */
router.get('/check', async (req, res) => {
  try {
    const { blockerUid, blockedUid } = req.query;

    if (!blockerUid || !blockedUid) {
      return res.status(400).json({
        success: false,
        message: 'blockerUid and blockedUid are required'
      });
    }

    const block = await Block.findOne({ blockerUid, blockedUid });

    res.json({
      success: true,
      isBlocked: !!block
    });
  } catch (error) {
    console.error('Check block error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/blocks/:userId
 * Get all users blocked by a user
 */
router.get('/:userId', async (req, res) => {
  try {
    const blocks = await Block.find({ blockerUid: req.params.userId })
      .sort({ createdAt: -1 });

    res.json({
      success: true,
      count: blocks.length,
      blocks
    });
  } catch (error) {
    console.error('Get blocks error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;