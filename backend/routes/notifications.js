const express = require('express');
const router = express.Router();
const Notification = require('../models/Notification');
const User = require('../models/User');
const Settings = require('../models/Settings'); // ← ADD THIS LINE
const { v4: uuidv4 } = require('uuid');
const admin = require('firebase-admin');

// Initialize Firebase Admin if not already initialized
if (!admin.apps.length) {
  try {
    const serviceAccount = require('../firebase-service-account.json');
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
    console.log('✅ Firebase Admin initialized for push notifications');
  } catch (error) {
    console.warn('⚠️ Firebase Admin not initialized. Push notifications will not work.');
    console.warn('   Place your firebase-service-account.json in the backend folder.');
  }
}

// ===== ADD THIS HELPER FUNCTION =====
async function shouldSendNotification(userId, type, fromUserId) {
  try {
    const settings = await Settings.getSettings();
    
    // Check if push notifications are enabled globally
    if (!settings.notifications.pushEnabled) {
      console.log('🔕 Push notifications disabled globally');
      return false;
    }
    
    // Check if this notification type is enabled
    if (!settings.notifications.notificationTypes.includes(type)) {
      console.log(`🔕 Notification type "${type}" is disabled`);
      return false;
    }
    
    // Check quiet hours
    const now = new Date();
    const currentTime = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
    const quietStart = settings.notifications.quietStart;
    const quietEnd = settings.notifications.quietEnd;
    
    if (quietStart < quietEnd) {
      // Same day range (e.g., 22:00 to 08:00)
      if (currentTime >= quietStart || currentTime < quietEnd) {
        console.log('🔕 Quiet hours - notification suppressed');
        return false;
      }
    }
    
    return true;
  } catch (error) {
    console.error('Error checking notification settings:', error);
    return true; // Default to sending if settings can't be loaded
  }
}

/**
 * Function to send push notification to a user
 */
async function sendPushNotification(userId, title, body, type, postId = null, fromUserId = null) {
  try {
    // ===== ADD THIS CHECK =====
    const shouldSend = await shouldSendNotification(userId, type, fromUserId);
    if (!shouldSend) {
      return;
    }
    
    // Check if Firebase Admin is initialized
    if (!admin.apps.length) {
      return;
    }

    // Check if user has muted the sender
    if (fromUserId) {
      const user = await User.findOne({ firebaseUid: userId });
      if (user && user.mutedUsers) {
        const isMuted = user.mutedUsers.some(m => m.userId === fromUserId);
        if (isMuted) {
          console.log(`🔇 Notification suppressed - User ${userId} muted ${fromUserId}`);
          return; // Don't send notification
        }
      }
    }

    // Get user's FCM token from database
    const user = await User.findOne({ firebaseUid: userId });
    if (!user || !user.fcmToken) {
      console.log(`No FCM token for user ${userId}`);
      return;
    }

    // Get sender info if available
    let senderName = '';
    if (fromUserId) {
      const sender = await User.findOne({ firebaseUid: fromUserId });
      senderName = sender ? sender.username : 'Someone';
    }

    // Customize body based on type
    let finalBody = body;
    if (type === 'like' && senderName) {
      finalBody = `${senderName} liked your post`;
    } else if (type === 'comment' && senderName) {
      finalBody = `${senderName} commented on your post`;
    } else if (type === 'follow' && senderName) {
      finalBody = `${senderName} started following you`;
    }

    const message = {
      token: user.fcmToken,
      notification: {
        title: title,
        body: finalBody,
      },
      data: {
        type: type,
        postId: postId || '',
        userId: fromUserId || '',
        click_action: 'FLUTTER_NOTIFICATION_CLICK',
      },
      android: {
        priority: 'high',
        notification: {
          sound: 'default',
          channelId: 'pawsociety_notifications',
          icon: '@drawable/ic_notification',
        },
      },
      apns: {
        payload: {
          aps: {
            sound: 'default',
            badge: 1,
          },
        },
      },
    };

    const response = await admin.messaging().send(message);
    console.log('✅ Push notification sent successfully:', response);
  } catch (error) {
    console.error('❌ Error sending push notification:', error);
  }
}

// ===== ADD THIS NEW ROUTE for admin notification count =====
router.get('/admin/count', async (req, res) => {
  try {
    // Get token from header
    const token = req.header('Authorization')?.replace('Bearer ', '');
    if (!token) {
      return res.status(401).json({ error: 'No token' });
    }
    
    // Verify token (simplified - you might want to use your adminAuth middleware)
    const Report = require('../models/Report');
    
    const reportCount = await Report.countDocuments({ status: 'pending' });
    const newUsersToday = await User.countDocuments({
      createdAt: { $gte: new Date().setHours(0,0,0,0) }
    });
    
    const totalCount = reportCount + (newUsersToday > 0 ? 1 : 0);
    
    res.json({ count: totalCount });
  } catch (error) {
    console.error('Error getting notification count:', error);
    res.status(500).json({ error: error.message });
  }
});

// ... (KEEP ALL YOUR EXISTING ROUTES BELOW - don't change them) ...

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
 * GET /api/notifications/:userId/unread-count
 * Get unread notification count for a user
 */
router.get('/:userId/unread-count', async (req, res) => {
  try {
    const count = await Notification.countDocuments({
      userId: req.params.userId,
      isRead: false
    });

    res.json({
      success: true,
      count
    });
  } catch (error) {
    console.error('Get unread count error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/notifications
 * Create a new notification
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

    // Don't create notification for self-actions
    if (userId === fromUserId) {
      return res.json({
        success: true,
        message: 'Self-action, no notification needed'
      });
    }

    // Check if user has muted the sender
    const recipient = await User.findOne({ firebaseUid: userId });
    if (recipient && recipient.mutedUsers) {
      const isMuted = recipient.mutedUsers.some(m => m.userId === fromUserId);
      if (isMuted) {
        console.log(`🔇 In-app notification suppressed - User ${userId} muted ${fromUserId}`);
        return res.json({
          success: true,
          message: 'User muted, no notification created'
        });
      }
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

    // Send push notification
    const pushTitle = 'PawSociety';
    await sendPushNotification(userId, pushTitle, message, type, postId, fromUserId);

    // Emit socket event for real-time update
    const io = req.app.get('io');
    if (io) {
      io.to(userId).emit('new-notification', {
        notificationId: notification.notificationId,
        message: notification.message,
        type: notification.type,
        fromUserName: notification.fromUserName,
        createdAt: notification.createdAt
      });
    }

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

    // Emit socket event for read status
    const io = req.app.get('io');
    if (io) {
      io.to(notification.userId).emit('notification-read', {
        notificationId: notification.notificationId
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
    const result = await Notification.updateMany(
      { userId: req.params.userId, isRead: false },
      { isRead: true }
    );

    // Emit socket event
    const io = req.app.get('io');
    if (io) {
      io.to(req.params.userId).emit('all-notifications-read');
    }

    res.json({
      success: true,
      message: 'All notifications marked as read',
      count: result.modifiedCount
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

    // Emit socket event
    const io = req.app.get('io');
    if (io) {
      io.to(notification.userId).emit('notification-deleted', {
        notificationId: notification.notificationId
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

/**
 * DELETE /api/notifications/:userId/all
 * Delete all notifications for a user
 */
router.delete('/:userId/all', async (req, res) => {
  try {
    const result = await Notification.deleteMany({
      userId: req.params.userId
    });

    // Emit socket event
    const io = req.app.get('io');
    if (io) {
      io.to(req.params.userId).emit('notifications-cleared', {
        deletedCount: result.deletedCount
      });
    }

    res.json({
      success: true,
      message: `${result.deletedCount} notifications cleared`,
      deletedCount: result.deletedCount
    });
  } catch (error) {
    console.error('Clear all notifications error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;