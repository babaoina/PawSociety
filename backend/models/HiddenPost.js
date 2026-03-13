const mongoose = require('mongoose');

const hiddenPostSchema = new mongoose.Schema({
  userUid: {
    type: String,
    required: true,
    index: true
  },
  postId: {
    type: String,
    required: true,
    index: true
  },
  hiddenAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Compound unique index to prevent duplicate hidden entries
hiddenPostSchema.index({ userUid: 1, postId: 1 }, { unique: true });

// Also index for faster queries
hiddenPostSchema.index({ userUid: 1, hiddenAt: -1 });

module.exports = mongoose.model('HiddenPost', hiddenPostSchema);