const express = require('express');
const router = express.Router();
const Notification = require('../models/Notification');
const { v4: uuidv4 } = require('uuid');

/**
 * GET /api/notifications/:userId
 * Get all notifications for a user
 */
router.get('/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    const { limit = 50, skip = 0 } = req.query;

    const notifications = await Notification.find({ userId })
      .sort({ createdAt: -1 })
      .limit(parseInt(limit))
      .skip(parseInt(skip));

    res.json({
      success: true,
      count: notifications.length,
      notifications
    });
  } catch (error) {
    console.error('Get notifications error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/notifications
 * Create a new notification
 * Body: { userId, fromUserId, fromUserName, fromUserImage, type, postId, message }
 */
router.post('/', async (req, res) => {
  try {
    const { userId, fromUserId, fromUserName, fromUserImage, type, postId, message } = req.body;

    if (!userId || !fromUserId || !type || !message) {
      return res.status(400).json({
        success: false,
        message: 'Missing required fields'
      });
    }

    // Create notification
    const notification = new Notification({
      notificationId: `notif_${Date.now()}_${uuidv4().substring(0, 8)}`,
      userId,
      fromUserId,
      fromUserName,
      fromUserImage,
      type,
      postId: postId || '',
      message
    });

    await notification.save();

    console.log(`🔔 Notification created for ${userId}: ${message}`);

    res.status(201).json({
      success: true,
      message: 'Notification created',
      data: notification
    });
  } catch (error) {
    console.error('Create notification error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * PUT /api/notifications/:notificationId/read
 * Mark notification as read
 */
router.put('/:notificationId/read', async (req, res) => {
  try {
    const notification = await Notification.findOneAndUpdate(
      { notificationId: req.params.notificationId },
      { isRead: true },
      { new: true }
    );

    if (!notification) {
      return res.status(404).json({
        success: false,
        message: 'Notification not found'
      });
    }

    res.json({
      success: true,
      message: 'Notification marked as read'
    });
  } catch (error) {
    console.error('Mark read error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * PUT /api/notifications/:userId/read-all
 * Mark all notifications as read for a user
 */
router.put('/:userId/read-all', async (req, res) => {
  try {
    await Notification.updateMany(
      { userId: req.params.userId },
      { isRead: true }
    );

    res.json({
      success: true,
      message: 'All notifications marked as read'
    });
  } catch (error) {
    console.error('Mark all read error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/notifications/:notificationId
 * Delete a notification
 */
router.delete('/:notificationId', async (req, res) => {
  try {
    const notification = await Notification.findOneAndDelete({
      notificationId: req.params.notificationId
    });

    if (!notification) {
      return res.status(404).json({
        success: false,
        message: 'Notification not found'
      });
    }

    res.json({
      success: true,
      message: 'Notification deleted'
    });
  } catch (error) {
    console.error('Delete notification error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;
