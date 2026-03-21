const mongoose = require('mongoose');

const chatDeleteSchema = new mongoose.Schema({
  chatId: {
    type: String,
    required: true,
    index: true
  },
  userId: {
    type: String, // The user who deleted the conversation
    required: true,
    index: true
  },
  deletedAt: {
    type: Date,
    default: Date.now
  },
  // 🔥 ADDED FOR AUTO-RESTORE
  deletedByUser: {
    type: Boolean,
    default: true
  }
}, {
  timestamps: true
});

// Compound unique index to prevent duplicate entries
chatDeleteSchema.index({ chatId: 1, userId: 1 }, { unique: true });

module.exports = mongoose.model('ChatDelete', chatDeleteSchema);