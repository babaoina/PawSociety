const mongoose = require('mongoose');

const userSchema = new mongoose.Schema({
  firebaseUid: {
    type: String,
    required: false
  },
  username: {
    type: String,
    required: true,
    trim: true,
    minlength: 3,
    maxlength: 20
  },
  email: {
    type: String,
    required: true,
    lowercase: true,
    trim: true
  },
  emailVerified: {
    type: Boolean,
    default: false
  },
  emailVerificationToken: {
    type: String,
    default: null
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
  fcmToken: {
    type: String,
    default: ''
  },
  status: {
    type: String,
    enum: ['Active', 'Suspended', 'Deleted'],
    default: 'Active'
  },
  role: {
    type: String,
    enum: ['user', 'admin', 'moderator'],
    default: 'user'
  },
  // 👇 ADD THIS - Muted users array
  mutedUsers: [{
    userId: {
      type: String,  // Firebase UID of user to mute
      required: true
    },
    mutedAt: {
      type: Date,
      default: Date.now
    }
  }],
  // 👇 ADD THIS - Notification Settings
  notificationSettings: {
    postsLikes: {
      type: Boolean,
      default: true
    },
    postComments: {
      type: Boolean,
      default: true
    },
    follows: {
      type: Boolean,
      default: true
    },
    messages: {
      type: Boolean,
      default: true
    },
    highlightedPosts: {
      type: Boolean,
      default: true
    },
    announcements: {
      type: Boolean,
      default: true
    }
  },
  // 👇 ADD THIS - Security Settings
  securitySettings: {
    loginAlerts: {
      type: Boolean,
      default: true
    },
    suspiciousActivityAlerts: {
      type: Boolean,
      default: true
    }
  },
  privacySettings: {
    privateAccount: {
      type: Boolean,
      default: false
    }
  },
  // 👇 Add password and email change tracking
  passwordChangedAt: {
    type: Date,
    default: null
  },
  emailChangedAt: {
    type: Date,
    default: null
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
}, {
  timestamps: true
});

// Add sparse: true to username and email indexes to handle uniqueness properly
userSchema.index({ username: 1 }, { unique: true, sparse: true });
userSchema.index({ email: 1 }, { unique: true, sparse: true });
userSchema.index(
  { firebaseUid: 1 },
  {
    unique: true,
    partialFilterExpression: {
      firebaseUid: { $exists: true, $type: 'string' }
    }
  }
);
// Index for muted users lookups
userSchema.index({ 'mutedUsers.userId': 1 });

module.exports = mongoose.model('User', userSchema);
