const mongoose = require('mongoose');

const blockSchema = new mongoose.Schema({
  blockId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  blockerUid: {
    type: String, // The user who is blocking
    required: true,
    index: true
  },
  blockedUid: {
    type: String, // The user being blocked
    required: true,
    index: true
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Compound unique index to prevent duplicate blocks
blockSchema.index({ blockerUid: 1, blockedUid: 1 }, { unique: true });

module.exports = mongoose.model('Block', blockSchema);