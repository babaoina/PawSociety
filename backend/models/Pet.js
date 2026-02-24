const mongoose = require('mongoose');

const petSchema = new mongoose.Schema({
  petId: {
    type: String,
    required: true,
    unique: true
  },
  ownerUid: {
    type: String,
    required: true
  },
  name: {
    type: String,
    required: true
  },
  type: {
    type: String,
    required: true
  },
  breed: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    required: true
  },
  imageUrl: {
    type: String,
    default: ''
  },
  age: {
    type: String,
    default: ''
  },
  gender: {
    type: String,
    enum: ['Male', 'Female', 'Unknown'],
    default: 'Unknown'
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Indexes
petSchema.index({ petId: 1 });
petSchema.index({ ownerUid: 1 });
petSchema.index({ type: 1 });

module.exports = mongoose.model('Pet', petSchema);
