const mongoose = require('mongoose');

const followSchema = new mongoose.Schema({
  followId: {
    type: String,
    required: true,
    unique: true
  },
  followerUid: {
    type: String,
    required: true
  },
  followingUid: {
    type: String,
    required: true
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
});

// Make sure they can't follow twice
followSchema.index({ followerUid: 1, followingUid: 1 }, { unique: true });

module.exports = mongoose.model('Follow', followSchema);