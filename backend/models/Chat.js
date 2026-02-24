const mongoose = require('mongoose');

const chatSchema = new mongoose.Schema({
  chatId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  participants: [{
    type: String, // firebaseUid
    required: true
  }],
  lastMessage: {
    type: String,
    default: ''
  },
  lastMessageAt: {
    type: Date,
    default: Date.now
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Compound index for finding chats between users
chatSchema.index({ participants: 1 });

module.exports = mongoose.model('Chat', chatSchema);
