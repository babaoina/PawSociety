const express = require('express');
const router = express.Router();
const User = require('../models/User');
const Post = require('../models/Post');
const Report = require('../models/Report');
const Notification = require('../models/Notification');
const Favorite = require('../models/Favorite');
const Follow = require('../models/Follow');
const Chat = require('../models/Chat');
const Message = require('../models/Message');
const Block = require('../models/Block');
const Highlight = require('../models/Highlight');
const HiddenPost = require('../models/HiddenPost');
const Pet = require('../models/Pet');
const ChatClear = require('../models/ChatClear');
const ChatDelete = require('../models/ChatDelete');
const adminAuth = require('../middleware/adminAuth');
const admin = require('firebase-admin');

router.use(adminAuth);

// Helper to send notification
async function sendNotificationToUser(userId, message, type, io) {
  try {
    const notification = new Notification({
      notificationId: `notif_${Date.now()}_${Math.random().toString(36).substring(2, 10)}`,
      userId: userId,
      fromUserId: 'system',
      fromUserName: 'PawSociety Admin',
      fromUserImage: '',
      type: type,
      postId: '',
      message: message,
      isRead: false
    });
    await notification.save();
    
    if (io) {
      io.to(userId).emit('new-notification', {
        notificationId: notification.notificationId,
        message: message,
        type: type,
        fromUserName: 'PawSociety Admin',
        createdAt: notification.createdAt
      });
    }
    return true;
  } catch (error) {
    console.error('Failed to send notification:', error);
    return false;
  }
}

// GET all users (no role filter)
router.get('/', async (req, res) => {
  try {
    const { search, status } = req.query;
    let query = {};
    
    if (search) {
      query.$or = [
        { username: { $regex: search, $options: 'i' } },
        { fullName: { $regex: search, $options: 'i' } },
        { email: { $regex: search, $options: 'i' } }
      ];
    }
    if (status) query.status = status;
    
    const users = await User.find(query).sort({ createdAt: -1 }).lean();
    
    const formattedUsers = await Promise.all(
      users.map(async (user) => {
        const reportCount = await Report.countDocuments({ 
          $or: [
            { reportedUid: user.firebaseUid },
            { reporterUid: user.firebaseUid }
          ]
        });
        
        const posts = await Post.countDocuments({ firebaseUid: user.firebaseUid });
        
        return {
          id: user._id,
          firebaseUid: user.firebaseUid,
          name: user.fullName || user.username,
          username: user.username,
          email: user.email,
          status: user.status || 'Active',
          joined: user.createdAt,
          posts: posts,
          reportCount: reportCount
        };
      })
    );
    
    res.json(formattedUsers);
  } catch (error) {
    console.error('Get users error:', error);
    res.status(500).json({ error: error.message });
  }
});

// GET single user
router.get('/:id', async (req, res) => {
  try {
    let user = await User.findById(req.params.id).lean();
    if (!user) {
      user = await User.findOne({ firebaseUid: req.params.id }).lean();
    }
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    res.json(user);
  } catch (error) {
    console.error('Get user error:', error);
    res.status(500).json({ error: error.message });
  }
});

// UPDATE user (no role field)
router.put('/:id', async (req, res) => {
  try {
    const { name, email, status } = req.body;
    
    let user = await User.findById(req.params.id);
    if (!user) {
      user = await User.findOne({ firebaseUid: req.params.id });
    }
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    
    const updateData = {};
    if (name) updateData.fullName = name;
    if (email) updateData.email = email;
    if (status) updateData.status = status;
    
    const updatedUser = await User.findByIdAndUpdate(
      user._id,
      updateData,
      { new: true }
    );
    
    // Send notification to user about status change
    if (status && status !== user.status) {
      const message = status === 'Suspended' 
        ? `⚠️ Your account has been suspended. Please contact support for more information.`
        : `✅ Your account has been activated. You can now use the app again.`;
      await sendNotificationToUser(user.firebaseUid, message, 'account_update');
    }
    
    res.json({ success: true, user: updatedUser });
  } catch (error) {
    console.error('Update user error:', error);
    res.status(500).json({ error: error.message });
  }
});

// DELETE user
router.delete('/:id', async (req, res) => {
  try {
    console.log('Delete user request for ID:', req.params.id);
    let user = await User.findById(req.params.id);
    if (!user) {
      console.log('User not found by _id, trying firebaseUid');
      user = await User.findOne({ firebaseUid: req.params.id });
    }
    if (!user) {
      console.log('User not found');
      return res.status(404).json({ error: 'User not found' });
    }
    console.log('Found user:', user._id, user.firebaseUid);
    
    // Delete from Firebase Auth
    try {
      await admin.auth().deleteUser(user.firebaseUid);
      console.log(`User ${user.firebaseUid} deleted from Firebase Auth`);
    } catch (firebaseError) {
      console.error('Failed to delete from Firebase:', firebaseError);
      // Continue with database deletion even if Firebase fails
    }
    
    // Delete all related data
    await Post.deleteMany({ firebaseUid: user.firebaseUid });
    console.log('Deleted posts');
    await Report.deleteMany({ 
      $or: [
        { reporterUid: user.firebaseUid },
        { reportedUid: user.firebaseUid }
      ]
    });
    console.log('Deleted reports');
    await Favorite.deleteMany({ userUid: user.firebaseUid });
    console.log('Deleted favorites');
    await Follow.deleteMany({
      $or: [
        { followerUid: user.firebaseUid },
        { followingUid: user.firebaseUid }
      ]
    });
    console.log('Deleted follows');
    // For chats, remove user from participants, delete if empty
    const chats = await Chat.find({ participants: user.firebaseUid });
    for (const chat of chats) {
      await Chat.updateOne(
        { chatId: chat.chatId },
        { $pull: { participants: user.firebaseUid } }
      );
      const updatedChat = await Chat.findOne({ chatId: chat.chatId });
      if (updatedChat.participants.length === 0) {
        await Chat.deleteOne({ chatId: chat.chatId });
        await Message.deleteMany({ chatId: chat.chatId });
      }
    }
    console.log('Updated/deleted chats');
    await Message.deleteMany({
      $or: [
        { senderUid: user.firebaseUid },
        { receiverUid: user.firebaseUid }
      ]
    });
    console.log('Deleted messages');
    await Notification.deleteMany({
      $or: [
        { userId: user.firebaseUid },
        { fromUserId: user.firebaseUid }
      ]
    });
    console.log('Deleted notifications');
    await Block.deleteMany({
      $or: [
        { blockerUid: user.firebaseUid },
        { blockedUid: user.firebaseUid }
      ]
    });
    console.log('Deleted blocks');
    await Highlight.deleteMany({ userId: user.firebaseUid });
    console.log('Deleted highlights');
    await HiddenPost.deleteMany({ userUid: user.firebaseUid });
    console.log('Deleted hidden posts');
    await Pet.deleteMany({ ownerUid: user.firebaseUid });
    console.log('Deleted pets');
    await ChatClear.deleteMany({ userId: user.firebaseUid });
    console.log('Deleted chat clears');
    await ChatDelete.deleteMany({ userId: user.firebaseUid });
    console.log('Deleted chat deletes');
    
    await User.findByIdAndDelete(user._id);
    console.log('Deleted user from DB');
    
    // Send notification and force logout
    const io = req.app.get('io');
    const socketUserMap = req.app.get('socketUserMap');
    const onlineUsers = req.app.get('onlineUsers');
    await sendNotificationToUser(user._id, 'Your account has been deleted by an administrator.', 'system', io);
    
    // Force logout by emitting to user's socket room and disconnecting existing connections
    if (io && socketUserMap && onlineUsers) {
      console.log(`🔴 Force logging out user ${user._id} (${user.firebaseUid})`);
      io.to(user._id).emit('force-logout', { reason: 'Account deleted by admin' });
      
      // Remove from online users
      onlineUsers.delete(user._id);
      io.emit('user-status', { userId: user._id, online: false });
      
      // Also disconnect any existing sockets for this user
      const connectedSockets = await io.fetchSockets();
      let disconnectedCount = 0;
      for (const socket of connectedSockets) {
        if (socketUserMap.get(socket.id) === user._id) {
          socket.disconnect(true);
          socketUserMap.delete(socket.id);
          disconnectedCount++;
          console.log(`Disconnected socket ${socket.id} for deleted user ${user._id}`);
        }
      }
      console.log(`Total sockets disconnected for user ${user._id}: ${disconnectedCount}`);
    }
    
    res.json({ message: 'User deleted', id: req.params.id });
  } catch (error) {
    console.error('Delete user error:', error);
    res.status(500).json({ error: error.message });
  }
});

// GET user reports
router.get('/:userId/reports', async (req, res) => {
  try {
    const userId = req.params.userId;
    
    let user = await User.findById(userId).lean();
    if (!user) {
      user = await User.findOne({ firebaseUid: userId }).lean();
    }
    
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    
    const reports = await Report.find({ 
      $or: [
        { reportedUid: user.firebaseUid },
        { reporterUid: user.firebaseUid }
      ]
    }).sort({ createdAt: -1 }).lean();
    
    for (let report of reports) {
      const reporter = await User.findOne({ firebaseUid: report.reporterUid })
        .select('username email')
        .lean();
      report.reporterName = reporter ? reporter.username : 'Unknown User';
      report.reporterEmail = reporter ? reporter.email : '';
    }
    
    res.json(reports);
  } catch (error) {
    console.error('Get user reports error:', error);
    res.status(500).json({ error: error.message });
  }
});

// GET user posts
router.get('/:userId/posts', async (req, res) => {
  try {
    const userId = req.params.userId;
    
    let user = await User.findOne({ firebaseUid: userId }).lean();
    if (!user) {
      if (userId && userId.match(/^[0-9a-fA-F]{24}$/)) {
        user = await User.findById(userId).lean();
      }
    }
    if (!user) {
      user = await User.findOne({ username: userId }).lean();
    }
    
    if (!user) {
      return res.status(404).json({ error: 'User not found' });
    }
    
    const posts = await Post.find({ firebaseUid: user.firebaseUid })
      .sort({ createdAt: -1 })
      .lean();
    
    const formattedPosts = posts.map(post => ({
      id: post._id,
      postId: post.postId,
      petName: post.petName,
      petType: post.petType,
      category: post.category,
      gender: post.gender,
      age: post.age,
      weight: post.weight,
      status: post.status,
      userName: post.userName,
      location: post.location,
      description: post.description,
      contact: post.contactInfo,
      reward: post.reward,
      createdAt: post.createdAt,
      imageUrls: post.imageUrls || []
    }));
    
    res.json(formattedPosts);
  } catch (error) {
    console.error('Get user posts error:', error);
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;