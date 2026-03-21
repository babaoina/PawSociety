const express = require('express');
const router = express.Router();
const Report = require('../models/Report');
const User = require('../models/User');
const Post = require('../models/Post');
const Notification = require('../models/Notification');
const adminAuth = require('../middleware/adminAuth');

router.use(adminAuth);

// Helper function to send notification
async function sendNotificationToUser(userId, message, type, postId = null, io = null) {
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
    
    const socket = io;
    if (socket) {
      socket.to(userId).emit('new-notification', {
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

// GET all reports
router.get('/', async (req, res) => {
  try {
    const reports = await Report.find().sort({ createdAt: -1 }).lean();
    
    const reportsWithDetails = await Promise.all(
      reports.map(async (report) => {
        let post = null;
        if (report.postId) {
          post = await Post.findOne({ postId: report.postId }).lean();
        }
        
        let reporter = null;
        reporter = await User.findOne({ firebaseUid: report.reporterUid })
          .select('username email firebaseUid')
          .lean();
        
        let reportedUser = null;
        if (report.reportedUid) {
          reportedUser = await User.findOne({ firebaseUid: report.reportedUid })
            .select('username email')
            .lean();
        }
        
        return {
          ...report,
          post: post,
          reporter: reporter,
          reportedUser: reportedUser
        };
      })
    );
    
    res.json(reportsWithDetails);
  } catch (error) {
    console.error('Get reports error:', error);
    res.status(500).json({ error: error.message });
  }
});

// UPDATE report status
router.put('/:reportId/status', async (req, res) => {
  try {
    const { status } = req.body;
    
    if (!['reviewed', 'dismissed'].includes(status)) {
      return res.status(400).json({ error: 'Invalid status' });
    }
    
    const report = await Report.findOneAndUpdate(
      { reportId: req.params.reportId },
      { status: status },
      { new: true }
    ).lean();
    
    if (!report) {
      return res.status(404).json({ error: 'Report not found' });
    }
    
    // Send notification to reporter
    try {
      const reporter = await User.findOne({ firebaseUid: report.reporterUid })
        .select('username email firebaseUid')
        .lean();
      
      if (reporter) {
        let message = '';
        if (status === 'reviewed') {
          message = `✅ Your report has been reviewed. Thank you for helping keep PawSociety safe!`;
        } else if (status === 'dismissed') {
          message = `ℹ️ Your report has been reviewed and dismissed. Thank you for your feedback.`;
        }

        const io = req.app.get('io');
        console.log(`Notifying reporter ${reporter.firebaseUid} with message:`, message);
        const reporterNotifSent = await sendNotificationToUser(reporter.firebaseUid, message, 'report_update', report.postId, io);
        console.log(`Reporter notification sent: ${reporterNotifSent}`);
      } else {
        console.warn(`Reporter user not found for firebaseUid: ${report.reporterUid}`);
      }
    } catch (notifError) {
      console.error('Failed to send notification:', notifError.message);
    }
    
    // If report is reviewed and has a post, send warning to post owner
    if (status === 'reviewed' && report.postId) {
      try {
        const post = await Post.findOne({ postId: report.postId }).lean();
        if (post && post.firebaseUid) {
          const warningMessage = `⚠️ Your post "${post.petName}" has been reported and reviewed. Please review our community guidelines.`;
          const io = req.app.get('io');
          console.log(`Notifying post owner ${post.firebaseUid} about report review on post ${report.postId}`);
          const warningSent = await sendNotificationToUser(post.firebaseUid, warningMessage, 'warning', report.postId, io);
          console.log(`Post owner warning notification sent: ${warningSent}`);
        } else {
          console.warn(`Post or post owner not found for postId: ${report.postId}`);
        }
      } catch (warningError) {
        console.error('Failed to send warning:', warningError);
      }
    }
    
    res.json({ success: true, report });
  } catch (error) {
    console.error('Update report error:', error);
    res.status(500).json({ error: error.message });
  }
});

// DELETE report
router.delete('/:reportId', async (req, res) => {
  try {
    const report = await Report.findOneAndDelete({
      reportId: req.params.reportId
    });
    
    if (!report) {
      return res.status(404).json({ error: 'Report not found' });
    }
    
    res.json({ success: true, message: 'Report deleted' });
  } catch (error) {
    console.error('Delete report error:', error);
    res.status(500).json({ error: error.message });
  }
});

// NEW: force-send report notifications endpoint for testing/fixing
router.post('/:reportId/notify', async (req, res) => {
  try {
    const report = await Report.findOne({ reportId: req.params.reportId }).lean();
    if (!report) return res.status(404).json({ error: 'Report not found' });

    const io = req.app.get('io');

    const reporter = await User.findOne({ firebaseUid: report.reporterUid }).select('firebaseUid').lean();
    const post = report.postId ? await Post.findOne({ postId: report.postId }).select('firebaseUid petName').lean() : null;

    const results = {
      reporterNotified: false,
      ownerNotified: false,
      report: report.reportId
    };

    if (reporter) {
      const msg = `✅ Your report has been processed by admin. Status: ${report.status || 'pending'}.`;
      results.reporterNotified = await sendNotificationToUser(reporter.firebaseUid, msg, 'report_update', report.postId, io);
    }

    if (post && post.firebaseUid) {
      const msg = `⚠️ Your post "${post.petName || 'untitled'}" received a report and was reviewed by admin.`;
      results.ownerNotified = await sendNotificationToUser(post.firebaseUid, msg, 'warning', report.postId, io);
    }

    res.json({ success: true, results });
  } catch (error) {
    console.error('Force notify report error:', error);
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;