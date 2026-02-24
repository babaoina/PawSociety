const mongoose = require('mongoose');

const favoriteSchema = new mongoose.Schema({
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
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Compound unique index to prevent duplicate favorites
favoriteSchema.index({ userUid: 1, postId: 1 }, { unique: true });

module.exports = mongoose.model('Favorite', favoriteSchema);
