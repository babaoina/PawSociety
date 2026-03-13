const express = require('express');
const router = express.Router();
const Report = require('../models/Report');
const User = require('../models/User');
const Post = require('../models/Post');
const { v4: uuidv4 } = require('uuid');

/**
 * POST /api/reports
 * Create a new report
 * Body: { reporterUid, reportedUid, postId, commentId, reason, description }
 */
router.post('/', async (req, res) => {
  try {
    const { reporterUid, reportedUid, postId, commentId, reason, description } = req.body;

    if (!reporterUid || !reason) {
      return res.status(400).json({
        success: false,
        message: 'reporterUid and reason are required'
      });
    }

    // Must report either a user, post, or comment
    if (!reportedUid && !postId && !commentId) {
      return res.status(400).json({
        success: false,
        message: 'Must report a user, post, or comment'
      });
    }

    const report = new Report({
      reportId: `report_${Date.now()}_${uuidv4().substring(0, 8)}`,
      reporterUid,
      reportedUid: reportedUid || '',
      postId: postId || '',
      commentId: commentId || '',
      reason,
      description: description || '',
      status: 'pending'
    });

    await report.save();

    console.log(`📝 Report created by ${reporterUid} for reason: ${reason}`);

    res.status(201).json({
      success: true,
      message: 'Report submitted successfully',
      data: report
    });
  } catch (error) {
    console.error('Create report error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/reports/all
 * Get all reports (for admin)
 */
router.get('/all', async (req, res) => {
  try {
    const reports = await Report.find()
      .sort({ createdAt: -1 });

    // Get reporter and reported user details
    const reportsWithDetails = await Promise.all(
      reports.map(async (report) => {
        const reporter = await User.findOne({ firebaseUid: report.reporterUid })
          .select('username email');
        
        let reportedUser = null;
        if (report.reportedUid) {
          reportedUser = await User.findOne({ firebaseUid: report.reportedUid })
            .select('username email');
        }

        let post = null;
        if (report.postId) {
          post = await Post.findOne({ postId: report.postId })
            .select('petName description');
        }

        return {
          ...report.toObject(),
          reporter,
          reportedUser,
          post
        };
      })
    );

    res.json({
      success: true,
      count: reports.length,
      reports: reportsWithDetails
    });
  } catch (error) {
    console.error('Get all reports error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * PUT /api/reports/:reportId/status
 * Update report status (admin)
 */
router.put('/:reportId/status', async (req, res) => {
  try {
    const { status } = req.body;

    if (!['reviewed', 'dismissed'].includes(status)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid status'
      });
    }

    const report = await Report.findOneAndUpdate(
      { reportId: req.params.reportId },
      { status },
      { new: true }
    );

    if (!report) {
      return res.status(404).json({
        success: false,
        message: 'Report not found'
      });
    }

    res.json({
      success: true,
      message: `Report marked as ${status}`,
      report
    });
  } catch (error) {
    console.error('Update report status error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;