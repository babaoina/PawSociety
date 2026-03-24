const mongoose = require('mongoose');

const sessionSchema = new mongoose.Schema({
  firebaseUid: {
    type: String,
    required: true,
    index: true
  },
  sessionToken: {
    type: String,
    required: true,
    unique: true
  },
  deviceModel: {
    type: String,
    default: 'Unknown Device'
  },
  osVersion: {
    type: String,
    default: 'Unknown'
  },
  deviceName: {
    type: String
  },
  ipAddress: {
    type: String
  },
  userAgent: {
    type: String
  },
  lastActive: {
    type: Date,
    default: Date.now
  },
  createdAt: {
    type: Date,
    default: Date.now,
    index: true,
    expires: 7776000 // 90 days TTL - auto-delete after 90 days
  }
});

module.exports = mongoose.model('Session', sessionSchema);
