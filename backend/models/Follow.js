const mongoose = require('mongoose');

const followSchema = new mongoose.Schema({
  followId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  followerUid: {
    type: String, // The user who is following
    required: true,
    index: true
  },
  followingUid: {
    type: String, // The user being followed
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

// Compound unique index to prevent duplicate follows
followSchema.index({ followerUid: 1, followingUid: 1 }, { unique: true });

module.exports = mongoose.model('Follow', followSchema);