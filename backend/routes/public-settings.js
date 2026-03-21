    const express = require('express');
    const router = express.Router();
    const Settings = require('../models/Settings');

    // ===== PUBLIC SETTINGS - NO AUTH REQUIRED =====
    router.get('/settings', async (req, res) => {
    try {
        console.log('📢 Public settings requested - NO AUTH REQUIRED');
        const settings = await Settings.getSettings();
        
        res.json({
        general: {
            appName: settings.general?.appName || 'PawSociety',
            supportEmail: settings.general?.supportEmail || 'support@pawsociety.com',
            maintenanceMode: settings.general?.maintenanceMode || false,
            maintenanceMessage: settings.general?.maintenanceMessage || 'PawSociety is under maintenance. We\'ll be back soon! 🐾',
            allowRegistration: settings.general?.allowRegistration !== false
        },
        notifications: {
            pushEnabled: settings.notifications?.pushEnabled !== false,
            quietStart: settings.notifications?.quietStart || '22:00',
            quietEnd: settings.notifications?.quietEnd || '08:00'
        }
        });
    } catch (error) {
        console.error('❌ Error fetching public settings:', error);
        res.status(500).json({ error: error.message });
    }
    });

    module.exports = router;