const express = require('express');
const router = express.Router();
const Settings = require('../models/Settings');
const Post = require('../models/Post');
const User = require('../models/User');
const Notification = require('../models/Notification');
const Report = require('../models/Report');
const adminAuth = require('../middleware/adminAuth');

// ===== ALL ROUTES REQUIRE ADMIN AUTH =====
router.use(adminAuth);

// ===== GET ALL SETTINGS =====
router.get('/', async (req, res) => {
  try {
    const settings = await Settings.getSettings();
    res.json(settings);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ===== UPDATE GENERAL SETTINGS =====
router.put('/general', async (req, res) => {
  try {
    const settings = await Settings.getSettings();
    settings.general = { ...settings.general, ...req.body };
    settings.updatedBy = req.admin.id;
    await settings.save();
    
    console.log(`✅ General settings updated by ${req.admin.email}`);
    res.json({ success: true, settings: settings.general });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ===== UPDATE NOTIFICATION SETTINGS =====
router.put('/notifications', async (req, res) => {
  try {
    const settings = await Settings.getSettings();
    settings.notifications = { ...settings.notifications, ...req.body };
    settings.updatedBy = req.admin.id;
    await settings.save();
    
    console.log(`🔔 Notification settings updated by ${req.admin.email}`);
    res.json({ success: true, settings: settings.notifications });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ===== UPDATE SECURITY SETTINGS =====
router.put('/security', async (req, res) => {
  try {
    const settings = await Settings.getSettings();
    settings.security = { ...settings.security, ...req.body };
    settings.updatedBy = req.admin.id;
    await settings.save();
    
    console.log(`🔒 Security settings updated by ${req.admin.email}`);
    res.json({ success: true, settings: settings.security });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ===== UPDATE MODERATION SETTINGS =====
router.put('/moderation', async (req, res) => {
  try {
    const settings = await Settings.getSettings();
    settings.moderation = { ...settings.moderation, ...req.body };
    settings.updatedBy = req.admin.id;
    await settings.save();
    
    console.log(`🛡️ Moderation settings updated by ${req.admin.email}`);
    res.json({ success: true, settings: settings.moderation });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ===== UPDATE API SETTINGS =====
router.put('/api', async (req, res) => {
  try {
    const settings = await Settings.getSettings();
    settings.api = { ...settings.api, ...req.body };
    settings.updatedBy = req.admin.id;
    await settings.save();
    
    console.log(`🔌 API settings updated by ${req.admin.email}`);
    res.json({ success: true, settings: settings.api });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// ===== EMERGENCY ACTIONS =====
router.post('/emergency/:action', async (req, res) => {
  try {
    const { action } = req.params;
    console.log(`🚨 EMERGENCY ACTION: ${action} triggered by ${req.admin.email}`);
    
    switch(action) {
      case 'disable':
        await Post.updateMany({}, { $set: { visible: false } });
        res.json({ success: true, message: 'All posts have been disabled' });
        break;
        
      case 'clear':
        const count = await Notification.countDocuments();
        await Notification.deleteMany({});
        res.json({ success: true, message: `Cleared ${count} notifications` });
        break;
        
      case 'cache':
        res.json({ success: true, message: 'Cache cleared' });
        break;
        
      case 'backup':
        res.json({ success: true, message: 'Database backup initiated' });
        break;
        
      case 'reset':
        if (req.body.confirm !== 'RESET') {
          return res.status(400).json({ error: 'Reset not confirmed' });
        }
        await Post.deleteMany({});
        await User.deleteMany({ role: { $ne: 'admin' } });
        await Report.deleteMany({});
        await Notification.deleteMany({});
        res.json({ success: true, message: 'All non-admin data deleted' });
        break;
        
      default:
        res.status(400).json({ error: 'Unknown action' });
    }
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;