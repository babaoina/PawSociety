const mongoose = require('mongoose');

const messageSchema = new mongoose.Schema({
  messageId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  chatId: {
    type: String,
    required: true,
    index: true
  },
  senderUid: {
    type: String,
    required: true,
    index: true
  },
  receiverUid: {
    type: String,
    required: true,
    index: true
  },
  text: {
    type: String,
    default: ''
  },
  imageUrl: {
    type: String,
    default: ''
  },
  isRead: {
    type: Boolean,
    default: false
  },
  // 🔥 ADDED - Missing status field!
  status: {
    type: String,
    enum: ['pending', 'delivered', 'read'],
    default: 'delivered'
  },
  deliveredAt: {
    type: Date
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Indexes for efficient queries
messageSchema.index({ chatId: 1, createdAt: -1 });
messageSchema.index({ senderUid: 1, createdAt: -1 });
messageSchema.index({ receiverUid: 1, createdAt: -1 });
messageSchema.index({ receiverUid: 1, isRead: 1, chatId: 1 });
messageSchema.index({ chatId: 1, status: 1 }); // 🔥 ADDED for querying requests

module.exports = mongoose.model('Message', messageSchema);