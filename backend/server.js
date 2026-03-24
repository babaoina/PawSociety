const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const path = require('path');
const http = require('http');
const { Server } = require('socket.io');

// Import routes
const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/users');
const postRoutes = require('./routes/posts');
const chatRoutes = require('./routes/chat');
const petRoutes = require('./routes/pets');
const favoriteRoutes = require('./routes/favorites');
const uploadRoutes = require('./routes/upload');
const notificationsRoutes = require('./routes/notifications');
const followRoutes = require('./routes/follow');
const blockRoutes = require('./routes/blocks');
const reportRoutes = require('./routes/reports');
const settingsRoutes = require('./routes/settings');
const publicSettingsRoutes = require('./routes/public-settings');
const userSettingsRoutes = require('./routes/user-settings');

// ===== WEB ADMIN ROUTES (NEW) =====
const adminAuthRoutes = require('./routes/admin-auth');
const adminUserRoutes = require('./routes/admin-users');
const adminPostRoutes = require('./routes/admin-posts');
const adminReportRoutes = require('./routes/admin-reports');
const adminStatsRoutes = require('./routes/admin-stats');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST", "PUT", "DELETE"],
    credentials: true
  }
});

const PORT = process.env.PORT || 5000;

// ===== FIREBASE CONFIGURATION =====
// Set FIREBASE_API_KEY from environment or use the key from Firebase project
process.env.FIREBASE_API_KEY = process.env.FIREBASE_API_KEY || 'AIzaSyC0Nqi5hqfcg5vC8axwyzM8dvyjfXRihO0';
process.env.FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'device-streaming-5f25c661';

console.log(`🔐 Firebase API Key initialized: ${process.env.FIREBASE_API_KEY ? '✅ SET' : '❌ NOT SET'}`);
console.log(`🔐 Firebase Project ID: ${process.env.FIREBASE_PROJECT_ID}`);

app.set('io', io);

// Track online users
const onlineUsers = new Set();
const socketUserMap = new Map();

app.set('socketUserMap', socketUserMap);
app.set('onlineUsers', onlineUsers);

// Socket.IO connection handling
io.on('connection', (socket) => {
  console.log('🔌 New client connected:', socket.id);

  socket.on('join', (userId) => {
    if (userId) {
      socket.join(userId);
      console.log(`👤 User ${userId} joined their room`);
    }
  });

  socket.on('user-online', async (userId) => {
    if (userId) {
      // Check if user still exists in database
      try {
        const User = require('./models/User');
        const user = await User.findOne({ firebaseUid: userId });
        if (!user) {
          console.log(`🚫 Deleted user ${userId} tried to go online - rejecting`);
          socket.emit('force-logout', { reason: 'Account deleted' });
          return;
        }
        
        onlineUsers.add(userId);
        socketUserMap.set(socket.id, userId);
        io.emit('user-status', { userId, online: true });
        console.log(`🟢 User ${userId} is now ONLINE`);
      } catch (error) {
        console.error('Error checking user existence:', error);
        socket.emit('force-logout', { reason: 'Authentication error' });
      }
    }
  });

  socket.on('user-offline', (userId) => {
    if (userId) {
      onlineUsers.delete(userId);
      io.emit('user-status', { userId, online: false });
      console.log(`🔴 User ${userId} is now OFFLINE`);
    }
  });

  socket.on('join-chat', (chatId) => {
    socket.join(chatId);
    console.log(`💬 Joined chat room: ${chatId}`);
  });

  socket.on('leave-chat', (chatId) => {
    socket.leave(chatId);
    console.log(`🚪 Left chat room: ${chatId}`);
  });

  socket.on('typing', ({ chatId, userId, isTyping }) => {
    socket.to(chatId).emit('user-typing', { userId, isTyping });
  });

  socket.on('disconnect', () => {
    const userId = socketUserMap.get(socket.id);
    if (userId) {
      onlineUsers.delete(userId);
      socketUserMap.delete(socket.id);
      io.emit('user-status', { userId, online: false });
      console.log(`🔴 User ${userId} disconnected`);
    }
    console.log('🔌 Client disconnected:', socket.id);
  });
});

// Middleware
app.use(cors({
  origin: function (origin, callback) {
    callback(null, true);
  },
  credentials: true
}));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// ===== SERVE WEB ADMIN STATIC FILES =====
app.use(express.static(path.join(__dirname, 'admin-pawsociety')));

// ===== SERVE IMAGES =====
app.use('/api/uploads', express.static('uploads'));

// ===== SERVE DIGITAL ASSET LINKS FOR APP LINK VERIFICATION =====
app.use('/.well-known', express.static('.well-known'));

// ===== ADMIN ROUTES (for web admin) =====
app.use('/api/admin/auth', adminAuthRoutes);
app.use('/api/admin/users', adminUserRoutes);
app.use('/api/admin/posts', adminPostRoutes);
app.use('/api/admin/reports', adminReportRoutes);
app.use('/api/admin/stats', adminStatsRoutes);

// ===== APP ROUTES (for Android app) =====
app.use('/api/public', publicSettingsRoutes);
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/posts', postRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/pets', petRoutes);
app.use('/api/favorites', favoriteRoutes);
app.use('/api/upload', uploadRoutes);
app.use('/api/notifications', notificationsRoutes);
app.use('/api/follow', followRoutes);
app.use('/api/blocks', blockRoutes);
app.use('/api/reports', reportRoutes);
app.use('/api/settings', userSettingsRoutes);
app.use('/api/user-settings', userSettingsRoutes);
app.use('/api/admin/settings', settingsRoutes);

// ===== WEB ADMIN HTML ROUTES =====
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'admin-pawsociety', 'index.html'));
});

app.get('/dashboard', (req, res) => {
  res.sendFile(path.join(__dirname, 'admin-pawsociety', 'dashboard.html'));
});

app.get('/users', (req, res) => {
  res.sendFile(path.join(__dirname, 'admin-pawsociety', 'users.html'));
});

app.get('/accounts', (req, res) => {
  res.sendFile(path.join(__dirname, 'admin-pawsociety', 'accounts.html'));
});

app.get('/posts', (req, res) => {
  res.sendFile(path.join(__dirname, 'admin-pawsociety', 'posts.html'));
});

app.get('/reports', (req, res) => {
  res.sendFile(path.join(__dirname, 'admin-pawsociety', 'reports.html'));
});

app.get('/settings', (req, res) => {
  res.sendFile(path.join(__dirname, 'admin-pawsociety', 'settings.html'));
});

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({ 
    status: 'OK', 
    message: 'PawSociety Backend is running',
    timestamp: new Date().toISOString()
  });
});

// Request logging middleware
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);
  next();
});

// MongoDB Connection
const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/pawsociety';

const Settings = require('./models/Settings');

mongoose.connect(MONGODB_URI)
  .then(async () => {
    console.log('✅ Connected to MongoDB:', MONGODB_URI);
    await Settings.getSettings();
    console.log('✅ Settings initialized');
  })
  .catch((error) => {
    console.error('❌ MongoDB connection error:', error);
    process.exit(1);
  });

// Error handling middleware
app.use((err, req, res, next) => {
  console.error('Error:', err);
  res.status(err.status || 500).json({
    success: false,
    message: err.message || 'Internal server error',
    ...(process.env.NODE_ENV === 'development' && { stack: err.stack })
  });
});

// 404 handler
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: 'Route not found'
  });
});

function getLocalIp() {
  const os = require('os');
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        return net.address;
      }
    }
  }
  return 'Your Network IP';
}

server.listen(PORT, '0.0.0.0', () => {
  const networkIp = getLocalIp();
  console.log('🚀 PawSociety Backend running on port', PORT);
  console.log('📍 Local:', `http://localhost:${PORT}`);
  console.log('📱 Network:', `http://${networkIp}:${PORT}`);
  console.log('📱 Emulator:', `http://10.0.2.2:${PORT}`);
  console.log('🌐 Web Admin:', `http://${networkIp}:${PORT}/dashboard`);
  console.log('🔌 Socket.IO server is ready');
  console.log('📋 Available routes:');
  console.log('   - GET /api/public/settings');
  console.log('   - POST /api/auth/firebase-login');
  console.log('   - GET /api/posts');
  console.log('   - POST /api/upload/profile');
  console.log('   - GET /dashboard (Web Admin)');
  console.log('   - GET /users (Web Admin)');
  console.log('   - GET /posts (Web Admin)');
});

module.exports = app;