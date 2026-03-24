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
  // 🔥 ADDED - Category field (Dogs, Cats, Fish, Birds)
  category: {
    type: String,
    enum: ['Dogs', 'Cats', 'Fish', 'Birds', ''],
    default: ''
  },
  age: {
    type: String,
    default: ''
  },
  weight: {
    type: String,
    default: ''
  },
  gender: {
    type: String,
    enum: ['Male', 'Female', 'Unknown'],
    default: 'Unknown'
  },
  status: {
    type: String,
    required: true,
    enum: ['Lost', 'Found', 'Adoption', 'Reunited', 'Returned', 'Adopted']
  },
  description: {
    type: String,
    required: true
  },
  location: {
    type: String,
    default: ''
  },
  latitude: {
    type: Number,
    default: null
  },
  longitude: {
    type: Number,
    default: null
  },
  reward: {
    type: String,
    default: ''
  },
  caseType: {
    type: String,
    enum: ['owner_lost', 'seen_lost_pet', 'found_in_care', 'adoption'],
    default: 'owner_lost'
  },
  resolvedStatus: {
    type: String,
    enum: ['', 'reunited', 'returned', 'adopted', 'resolved'],
    default: ''
  },
  isResolved: {
    type: Boolean,
    default: false
  },
  eventDate: {
    type: String,
    default: ''
  },
  eventLocation: {
    type: String,
    default: ''
  },
  currentCareStatus: {
    type: String,
    enum: ['', 'in_my_care', 'sighting_only', 'owner'],
    default: ''
  },
  identifyingMarks: {
    type: String,
    default: ''
  },
  temperament: {
    type: String,
    default: ''
  },
  healthCondition: {
    type: String,
    default: ''
  },
  hasCollar: {
    type: Boolean,
    default: false
  },
  contactPreference: {
    type: String,
    enum: ['call', 'text', 'in_app_chat'],
    default: 'call'
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
    type: String
  }],
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Indexes
postSchema.index({ firebaseUid: 1, createdAt: -1 });
postSchema.index({ createdAt: -1 });
postSchema.index({ status: 1 });
postSchema.index({ gender: 1 });

// Text indexes for search
postSchema.index({ 
  petName: 'text', 
  description: 'text', 
  location: 'text',
  petType: 'text' 
});

module.exports = mongoose.model('Post', postSchema);
