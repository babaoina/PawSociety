const mongoose = require('mongoose');

const reportSchema = new mongoose.Schema({
  reportId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  reporterUid: {
    type: String, // User who is reporting
    required: true,
    index: true
  },
  reportedUid: {
    type: String, // User being reported (if reporting a user)
    default: ''
  },
  postId: {
    type: String, // Post being reported (if reporting a post)
    default: ''
  },
  commentId: {
    type: String, // Comment being reported (if reporting a comment)
    default: ''
  },
  reason: {
    type: String,
    required: true,
    enum: ['spam', 'harassment', 'inappropriate', 'fake', 'violence', 'other']
  },
  description: {
    type: String,
    default: ''
  },
  status: {
    type: String,
    enum: ['pending', 'reviewed', 'dismissed'],
    default: 'pending'
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

reportSchema.index({ reporterUid: 1, createdAt: -1 });
reportSchema.index({ status: 1 });

module.exports = mongoose.model('Report', reportSchema);