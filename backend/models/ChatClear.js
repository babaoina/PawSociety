const mongoose = require('mongoose');

const chatClearSchema = new mongoose.Schema({
  chatId: {
    type: String,
    required: true,
    index: true
  },
  userId: {
    type: String, // The user who cleared/deleted the chat
    required: true,
    index: true
  },
  clearedAt: {
    type: Date,
    default: Date.now
  },
  deletedAt: {  // 👈 ADD THIS FIELD
    type: Date
  }
}, {
  timestamps: true
});

// Compound unique index to prevent duplicate entries
chatClearSchema.index({ chatId: 1, userId: 1 }, { unique: true });

module.exports = mongoose.model('ChatClear', chatClearSchema);