const mongoose = require('mongoose');

const postSchema = new mongoose.Schema({
  postId: {
    type: String,
    required: true,
    unique: true,
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
  petName: {
    type: String,
    required: true
  },
  petType: {
    type: String,
    required: true
  },
  status: {
    type: String,
    required: true,
    enum: ['Lost', 'Found', 'Adoption']
  },
  description: {
    type: String,
    required: true
  },
  location: {
    type: String,
    default: ''
  },
  reward: {
    type: String,
    default: ''
  },
  contactInfo: {
    type: String,
    required: true
  },
  imageUrls: [{
    type: String
  }],
  likesCount: {
    type: Number,
    default: 0
  },
  likedBy: [{
    type: String // firebaseUid of users who liked
  }],
  commentsCount: {
    type: Number,
    default: 0
  },
  shares: {
    type: Number,
    default: 0
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Indexes for faster queries
postSchema.index({ firebaseUid: 1, createdAt: -1 });
postSchema.index({ createdAt: -1 });
postSchema.index({ status: 1 });

module.exports = mongoose.model('Post', postSchema);
