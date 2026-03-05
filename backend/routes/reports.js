const express = require('express');
const router = express.Router();
const Report = require('../models/Report');
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

module.exports = router;