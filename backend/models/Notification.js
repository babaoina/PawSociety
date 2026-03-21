const mongoose = require('mongoose');

const notificationSchema = new mongoose.Schema({
  notificationId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  userId: {
    type: String, // firebaseUid of user receiving notification
    required: true,
    index: true
  },
  fromUserId: {
    type: String, // firebaseUid of user who triggered notification
    required: true
  },
  fromUserName: {
    type: String,
    required: true
  },
  fromUserImage: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    required: true,
    enum: ['like', 'comment', 'follow', 'message', 'message_request', 'report_update', 'warning', 'system']
  },
  postId: {
    type: String,
    default: ''
  },
  message: {
    type: String,
    required: true
  },
  isRead: {
    type: Boolean,
    default: false
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Index for faster queries
notificationSchema.index({ userId: 1, createdAt: -1 });
notificationSchema.index({ userId: 1, isRead: 1, createdAt: -1 });

module.exports = mongoose.model('Notification', notificationSchema);