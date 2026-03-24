const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const fs = require('fs');
const path = require('path');
const axios = require('axios');
const admin = require('firebase-admin');
const User = require('../models/User');

const PRIMARY_ADMIN_EMAIL = (process.env.ADMIN_EMAIL || 'markdeverauwu143@gmail.com').toLowerCase();

function getFirebaseWebApiKey() {
  if (process.env.FIREBASE_WEB_API_KEY) {
    return process.env.FIREBASE_WEB_API_KEY;
  }

  try {
    const googleServicesPath = path.resolve(__dirname, '../../app/google-services.json');
    const googleServices = JSON.parse(fs.readFileSync(googleServicesPath, 'utf8'));
    return googleServices?.client?.[0]?.api_key?.[0]?.current_key || '';
  } catch (error) {
    console.error('Failed to read Firebase Web API key:', error.message);
    return '';
  }
}

function isAdminUser(user) {
  return !!user && (user.role === 'admin' || String(user.email || '').toLowerCase() === PRIMARY_ADMIN_EMAIL);
}

// Admin login
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    
    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password required' });
    }
    
    // Find user by email
    const user = await User.findOne({ email });
    
    if (!user) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }
    
    if (!isAdminUser(user)) {
      return res.status(403).json({ error: 'Admin access only' });
    }

    const apiKey = getFirebaseWebApiKey();
    if (!apiKey) {
      return res.status(500).json({ error: 'Firebase Web API key is not configured' });
    }

    try {
      await axios.post(
        `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`,
        {
          email,
          password,
          returnSecureToken: true
        },
        {
          headers: { 'Content-Type': 'application/json' }
        }
      );
    } catch (firebaseError) {
      const firebaseMessage = firebaseError.response?.data?.error?.message;
      console.error('Firebase admin login failed:', firebaseMessage || firebaseError.message);
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    if (user.role !== 'admin') {
      await User.updateOne({ _id: user._id }, { $set: { role: 'admin' } });
    }
    
    // Create token
    const token = jwt.sign(
      { 
        id: user._id, 
        username: user.username, 
        email: user.email,
        role: 'admin' 
      },
      process.env.JWT_SECRET || 'your-secret-key-change-this',
      { expiresIn: '8h' }
    );
    
    res.json({
      token,
      user: {
        id: user._id,
        username: user.username,
        email: user.email,
        name: user.fullName,
        role: 'admin'
      }
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

router.post('/reset-password', async (req, res) => {
  try {
    const { email } = req.body;

    if (!email) {
      return res.status(400).json({ error: 'Email is required' });
    }

    const user = await User.findOne({ email });
    if (!isAdminUser(user)) {
      return res.status(404).json({ error: 'Admin account not found' });
    }

    const apiKey = getFirebaseWebApiKey();
    if (!apiKey) {
      return res.status(500).json({ error: 'Firebase Web API key is not configured' });
    }

    await axios.post(
      `https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${apiKey}`,
      {
        requestType: 'PASSWORD_RESET',
        email
      },
      {
        headers: { 'Content-Type': 'application/json' }
      }
    );

    try {
      const firebaseUser = await admin.auth().getUserByEmail(email);
      if (firebaseUser.uid !== user.firebaseUid) {
        await User.updateOne({ _id: user._id }, { $set: { firebaseUid: firebaseUser.uid, role: 'admin' } });
      }
    } catch (firebaseLookupError) {
      console.warn('Admin Firebase lookup warning:', firebaseLookupError.message);
    }

    res.json({ success: true, message: 'Firebase password reset email sent successfully' });
  } catch (error) {
    console.error('Reset password error:', error);
    const firebaseMessage = error.response?.data?.error?.message;
    if (firebaseMessage === 'EMAIL_NOT_FOUND') {
      return res.status(404).json({ error: 'Admin account not found in Firebase' });
    }
    res.status(500).json({ error: firebaseMessage || 'Server error' });
  }
});

module.exports = router;
