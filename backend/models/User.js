const mongoose = require('mongoose');

const userSchema = new mongoose.Schema({
  firebaseUid: {
    type: String,
    required: true,
    unique: true
  },
  username: {
    type: String,
    required: true,
    unique: true,
    trim: true,
    minlength: 3,
    maxlength: 20
  },
  email: {
    type: String,
    required: true,
    unique: true,
    lowercase: true,
    trim: true
  },
  fullName: {
    type: String,
    required: true,
    trim: true
  },
  phone: {
    type: String,
    trim: true
  },
  profileImageUrl: {
    type: String,
    default: ''
  },
  bio: {
    type: String,
    default: '',
    maxlength: 150
  },
  location: {
    type: String,
    default: ''
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Indexes for faster queries
userSchema.index({ firebaseUid: 1 });
userSchema.index({ username: 1 });
userSchema.index({ email: 1 });

module.exports = mongoose.model('User', userSchema);
