require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
const path = require('path');
const os = require('os');
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
const highlightRoutes = require('./routes/highlights');
const followRoutes = require('./routes/follow');
const blockRoutes = require('./routes/blocks');
const reportRoutes = require('./routes/reports');
const settingsRoutes = require('./routes/settings'); // ✅ ADDED

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

// Make io accessible to routes
app.set('io', io);

// Track online users
const onlineUsers = new Set();
// Track socket to user mapping
const socketUserMap = new Map();

// Socket.IO connection handling
io.on('connection', (socket) => {
  console.log('🔌 New client connected:', socket.id);

  // Join a room for the user (based on their UID)
  socket.on('join', (userId) => {
    if (userId) {
      socket.join(userId);
      console.log(`👤 User ${userId} joined their room`);
    }
  });

  // Handle user online
  socket.on('user-online', (userId) => {
    if (userId) {
      onlineUsers.add(userId);
      socketUserMap.set(socket.id, userId);
      // Broadcast to all connected clients that this user is online
      io.emit('user-status', { userId, online: true });
      console.log(`🟢 User ${userId} is now ONLINE (Total online: ${onlineUsers.size})`);
    }
  });

  // Handle user offline
  socket.on('user-offline', (userId) => {
    if (userId) {
      onlineUsers.delete(userId);
      // Broadcast to all connected clients that this user is offline
      io.emit('user-status', { userId, online: false });
      console.log(`🔴 User ${userId} is now OFFLINE (Total online: ${onlineUsers.size})`);
    }
  });

  // Handle joining a chat room
  socket.on('join-chat', (chatId) => {
    socket.join(chatId);
    console.log(`💬 Joined chat room: ${chatId}`);
  });

  // Handle leaving a chat room
  socket.on('leave-chat', (chatId) => {
    socket.leave(chatId);
    console.log(`🚪 Left chat room: ${chatId}`);
  });

  // Handle typing status
  socket.on('typing', ({ chatId, userId, isTyping }) => {
    socket.to(chatId).emit('user-typing', { userId, isTyping });
  });

  // Handle disconnection
  socket.on('disconnect', () => {
    const userId = socketUserMap.get(socket.id);
    if (userId) {
      onlineUsers.delete(userId);
      socketUserMap.delete(socket.id);
      // Broadcast that user is offline
      io.emit('user-status', { userId, online: false });
      console.log(`🔴 User ${userId} disconnected (offline) - Total online: ${onlineUsers.size}`);
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

// Serve static files for uploaded images
app.use('/api/uploads', express.static('uploads'));

// Request logging middleware
app.use((req, res, next) => {
  console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);
  next();
});

// MongoDB Connection
const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/pawsociety';

// After MongoDB connects, ensure settings exist
const Settings = require('./models/Settings');

mongoose.connect(MONGODB_URI)
  .then(async () => {
    console.log('✅ Connected to MongoDB:', MONGODB_URI);
    
    // ✅ Initialize settings if they don't exist
    await Settings.getSettings();
    console.log('✅ Settings initialized');
  })
  .catch((error) => {
    console.error('❌ MongoDB connection error:', error);
    process.exit(1);
  });

// Routes
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/posts', postRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/pets', petRoutes);
app.use('/api/favorites', favoriteRoutes);
app.use('/api/upload', uploadRoutes);
app.use('/api/notifications', notificationsRoutes);
app.use('/api/users', highlightRoutes);
app.use('/api/follow', followRoutes);
app.use('/api/blocks', blockRoutes);
app.use('/api/reports', reportRoutes);
app.use('/api/admin/settings', settingsRoutes); // ✅ ADDED

// Health check endpoint
app.get('/api/health', (req, res) => {
  res.json({ 
    status: 'OK', 
    message: 'PawSociety Backend is running',
    timestamp: new Date().toISOString()
  });
});

// Test endpoint to verify hide post routes are working
app.get('/api/test', (req, res) => {
  res.json({ 
    success: true, 
    message: 'Server is running',
    routes: {
      hidePost: '/api/posts/hide (POST)',
      unhidePost: '/api/posts/unhide (POST)',
      getHidden: '/api/posts/hidden (GET)',
      getHiddenCount: '/api/posts/hidden/count (GET)'
    }
  });
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

// Function to get local network IP
function getLocalIp() {
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

// Start server
server.listen(PORT, '0.0.0.0', () => {
  const networkIp = getLocalIp();
  console.log('🚀 PawSociety Backend running on port', PORT);
  console.log('📍 Local:', `http://localhost:${PORT}`);
  console.log('📱 Network:', `http://${networkIp}:${PORT}`);
  console.log('📲 Emulator:', `http://10.0.2.2:${PORT}`);
  console.log('📱 For phone on same Wi-Fi use:', `http://${networkIp}:${PORT}`);
  console.log('🔌 Socket.IO server is ready');
  console.log('📋 Available routes:');
  console.log('   - POST /api/posts/hide');
  console.log('   - POST /api/posts/unhide');
  console.log('   - GET /api/posts/hidden');
  console.log('   - GET /api/posts/hidden/count');
  console.log('   - GET /api/admin/settings'); // ✅ New route
  console.log('   - PUT /api/admin/settings/:section'); // ✅ New route
});

module.exports = app;