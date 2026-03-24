const mongoose = require('mongoose');

const settingsSchema = new mongoose.Schema({
  // General Settings
  general: {
    appName: { type: String, default: 'PawSociety' },
    supportEmail: { type: String, default: 'support@pawsociety.com' },
    minVersion: { type: String, default: '1.0.0' },
    maintenanceMode: { type: Boolean, default: false },
    maintenanceMessage: { 
      type: String, 
      default: 'PawSociety is under maintenance. We\'ll be back soon! 🐾' 
    },
    allowRegistration: { type: Boolean, default: true },
    emailVerification: { type: Boolean, default: true },
    phoneVerification: { type: Boolean, default: false }
  },
  
  // Security Settings
  security: {
    maxLoginAttempts: { type: Number, default: 5 },
    lockoutDuration: { type: Number, default: 30 },
    sessionTimeout: { type: Number, default: 120 },
    admin2FA: { type: Boolean, default: false },
    adminPassword: { type: String, default: 'admin123' }
  },
  
  // Push Notification Settings
  notifications: {
    pushEnabled: { type: Boolean, default: true },
    fcmServerKey: { type: String, default: '' },
    notificationTypes: {
      type: [String],
      default: ['like', 'comment', 'follow', 'message']
    },
    quietStart: { type: String, default: '22:00' },
    quietEnd: { type: String, default: '08:00' }
  },
  
  // Moderation Settings
  moderation: {
    autoApprove: { type: Boolean, default: false },
    profanityFilter: { type: Boolean, default: true },
    flagThreshold: { type: Number, default: 3 },
    blockedWords: { 
      type: [String], 
      default: ['spam', 'scam', 'inappropriate']
    }
  },
  
  // API Settings
  api: {
    apiStatus: { type: Boolean, default: true },
    rateLimit: { type: Number, default: 60 },
    allowedOrigins: { 
      type: [String], 
      default: ['http://localhost:3000', 'http://192.168.254.100:3000']
    }
  },

  updatedAt: { type: Date, default: Date.now },
  updatedBy: { type: String }
});

// Ensure only ONE settings document exists
settingsSchema.statics.getSettings = async function() {
  let settings = await this.findOne();
  if (!settings) {
    settings = await this.create({});
    console.log('✅ Default settings created in database');
  }
  return settings;
};

module.exports = mongoose.model('Settings', settingsSchema);
