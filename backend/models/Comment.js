const mongoose = require('mongoose');

const commentSchema = new mongoose.Schema({
  commentId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  postId: {
    type: String,
    required: true,
    index: true
  },
  firebaseUid: {
    type: String,
    required: true,
    index: true
  },
  userName: {
    type: String,
    required: true
  },
  userImageUrl: {
    type: String,
    default: ''
  },
  text: {
    type: String,
    required: true
  },
  likesCount: {
    type: Number,
    default: 0
  },
  likedBy: [{
    type: String // firebaseUid of users who liked
  }],
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Indexes
commentSchema.index({ postId: 1, createdAt: -1 });
commentSchema.index({ firebaseUid: 1 });

module.exports = mongoose.model('Comment', commentSchema);
