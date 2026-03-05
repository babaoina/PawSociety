const mongoose = require('mongoose');

const highlightSchema = new mongoose.Schema({
  highlightId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  userId: {
    type: String, // firebaseUid
    required: true,
    index: true
  },
  name: {
    type: String,
    required: true
  },
  emoji: {
    type: String,
    default: '📸'
  },
  color: {
    type: String,
    default: '#FF6B35'
  },
  imageUrl: {
    type: String,
    default: ''
  },
  postIds: [{
    type: String // References to post IDs
  }],
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

module.exports = mongoose.model('Highlight', highlightSchema);