const express = require('express');
const router = express.Router();
const Chat = require('../models/Chat');
const Message = require('../models/Message');
const User = require('../models/User');
const ChatClear = require('../models/ChatClear');
const ChatDelete = require('../models/ChatDelete');
const { v4: uuidv4 } = require('uuid');

// 👇 ADD THIS IMPORT
const { sendPushNotification } = require('./notifications');

/**
 * GET /api/chat/conversations/:firebaseUid
 * Get all conversations for a user (separates messages and requests)
 */
router.get('/conversations/:firebaseUid', async (req, res) => {
  try {
    const userId = req.params.firebaseUid;
    
    console.log(`📬 Getting conversations for user: ${userId}`);
    
    // Get all chats where user is a participant
    const chats = await Chat.find({
      participants: userId
    })
    .sort({ lastMessageAt: -1 });

    // Get deleted conversations - FILTER THEM OUT (user's choice respected)
    const deletedChats = await ChatDelete.find({ userId });
    const deletedChatIds = deletedChats.map(d => d.chatId);

    // SIMPLE FILTER - no auto-restore, user's delete choice is respected
    const activeChats = chats.filter(chat => !deletedChatIds.includes(chat.chatId));

    // Separate messages and requests
    const messages = [];
    const requests = [];

    for (const chat of activeChats) {
      const otherParticipantUid = chat.participants.find(uid => uid !== userId);
      
      if (!otherParticipantUid) continue;
      
      const otherUser = await User.findOne({ firebaseUid: otherParticipantUid })
        .select('username fullName profileImageUrl firebaseUid');

      if (!otherUser) continue;

      // Check if user cleared this chat
      const chatClear = await ChatClear.findOne({ chatId: chat.chatId, userId });

      let lastMessageQuery = { chatId: chat.chatId };
      if (chatClear) {
        lastMessageQuery.createdAt = { $gt: chatClear.clearedAt };
      }

      // Get the latest message to determine status
      const latestMessage = await Message.findOne(lastMessageQuery)
        .sort({ createdAt: -1 });

      if (!latestMessage) continue;

      // 👇 CHECK IF THIS IS A PENDING REQUEST
      const isRequest = latestMessage.status === 'pending' && 
                        latestMessage.receiverUid === userId;

      // Count unread messages
      const unreadQuery = {
        chatId: chat.chatId,
        receiverUid: userId,
        isRead: false,
        status: { $ne: 'pending' } // Don't count pending as unread
      };
      if (chatClear) {
        unreadQuery.createdAt = { $gt: chatClear.clearedAt };
      }
      const unreadCount = await Message.countDocuments(unreadQuery);

      // Count pending requests
      const pendingCount = await Message.countDocuments({
        chatId: chat.chatId,
        receiverUid: userId,
        status: 'pending'
      });

      const conversationData = {
        chatId: chat.chatId,
        participants: chat.participants,
        otherUser: {
          firebaseUid: otherUser.firebaseUid,
          username: otherUser.username,
          fullName: otherUser.fullName,
          profileImageUrl: otherUser.profileImageUrl || ''
        },
        lastMessage: {
          text: latestMessage.text,
          imageUrl: latestMessage.imageUrl,
          senderUid: latestMessage.senderUid,
          createdAt: latestMessage.createdAt,
          isRead: latestMessage.isRead,
          status: latestMessage.status
        },
        lastMessageAt: chat.lastMessageAt,
        createdAt: chat.createdAt,
        unreadCount: unreadCount,
        pendingCount: pendingCount,
        clearedAt: chatClear ? chatClear.clearedAt : null
      };

      if (isRequest) {
        requests.push(conversationData);
      } else {
        messages.push(conversationData);
      }
    }

    console.log(`✅ Messages: ${messages.length}, Requests: ${requests.length}`);

    res.json({
      success: true,
      messages: messages,
      requests: requests,
      messagesCount: messages.length,
      requestsCount: requests.length
    });
  } catch (error) {
    console.error('❌ Get conversations error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/chat/accept-request/:chatId
 * Accept a message request (moves from requests to messages)
 */
router.post('/accept-request/:chatId', async (req, res) => {
  try {
    const { userId } = req.body;
    const { chatId } = req.params;

    if (!userId) {
      return res.status(400).json({
        success: false,
        message: 'userId is required'
      });
    }

    // Update all pending messages in this chat to 'delivered'
    const result = await Message.updateMany(
      { 
        chatId: chatId,
        receiverUid: userId,
        status: 'pending'
      },
      { 
        status: 'delivered',
        deliveredAt: new Date()
      }
    );

    console.log(`✅ Accepted ${result.modifiedCount} messages from requests`);

    // Emit socket event
    const io = req.app.get('io');
    io.to(chatId).emit('request-accepted', {
      chatId: chatId,
      acceptedBy: userId
    });

    res.json({
      success: true,
      message: 'Request accepted',
      acceptedCount: result.modifiedCount
    });
  } catch (error) {
    console.error('❌ Accept request error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/chat/reject-request/:chatId
 * Reject/delete a message request
 */
router.delete('/reject-request/:chatId', async (req, res) => {
  try {
    const { userId } = req.query;
    const { chatId } = req.params;

    if (!userId) {
      return res.status(400).json({
        success: false,
        message: 'userId is required'
      });
    }

    // Delete all pending messages in this chat
    const result = await Message.deleteMany({
      chatId: chatId,
      receiverUid: userId,
      status: 'pending'
    });

    // If no messages left, delete the chat or mark as deleted
    const remainingMessages = await Message.countDocuments({ chatId: chatId });
    
    if (remainingMessages === 0) {
      // Mark chat as deleted for this user
      await ChatDelete.findOneAndUpdate(
        { chatId, userId },
        { deletedAt: new Date() },
        { upsert: true }
      );
    }

    console.log(`❌ Rejected ${result.deletedCount} messages`);

    res.json({
      success: true,
      message: 'Request rejected',
      rejectedCount: result.deletedCount
    });
  } catch (error) {
    console.error('❌ Reject request error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/chat/:chatId/messages
 * Get all messages in a chat (filtered by who cleared)
 */
router.get('/:chatId/messages', async (req, res) => {
  try {
    const { limit = 50, skip = 0, userId } = req.query;

    if (!userId) {
      return res.status(400).json({
        success: false,
        message: 'userId is required to filter cleared messages'
      });
    }

    // Check if this user cleared the chat
    const chatClear = await ChatClear.findOne({ 
      chatId: req.params.chatId, 
      userId: userId 
    });

    let query = { chatId: req.params.chatId };
    
    // If user cleared the chat, only show messages AFTER the clear time
    if (chatClear) {
      query.createdAt = { $gt: chatClear.clearedAt };
    }

    const messages = await Message.find(query)
      .sort({ createdAt: -1 })
      .limit(parseInt(limit))
      .skip(parseInt(skip));

    res.json({
      success: true,
      count: messages.length,
      messages: messages.reverse(), // Return in chronological order
      clearedAt: chatClear ? chatClear.clearedAt : null
    });
  } catch (error) {
    console.error('Get messages error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/chat/send
 * Send a message (goes to requests if not mutual followers OR no existing accepted chat)
 * UPDATED WITH SOCKET EMIT INCLUDING USERNAME
 */
router.post('/send', async (req, res) => {
  try {
    const { senderUid, receiverUid, text, imageUrl } = req.body;

    if (!senderUid || !receiverUid || (!text && !imageUrl)) {
      return res.status(400).json({
        success: false,
        message: 'senderUid, receiverUid, and text or imageUrl are required'
      });
    }

    console.log(`📨 Send message from ${senderUid} to ${receiverUid}`);

    // Find or create chat
    let chat = await Chat.findOne({
      participants: { $all: [senderUid, receiverUid] }
    });

    if (!chat) {
      // Create new chat
      chat = new Chat({
        chatId: `chat_${Date.now()}_${uuidv4().substring(0, 8)}`,
        participants: [senderUid, receiverUid]
      });
      await chat.save();
      console.log(`✅ Created new chat: ${chat.chatId}`);
      
      // Remove any delete markers
      await ChatDelete.deleteMany({ chatId: chat.chatId });
    }

    // 👇 CHECK IF THERE'S ALREADY AN ACCEPTED CONVERSATION
    // Look for any non-pending messages in this chat
    const existingAcceptedMessage = await Message.findOne({
      chatId: chat.chatId,
      status: { $ne: 'pending' } // Any message that's not pending
    });

    // Determine message status
    let messageStatus = 'pending'; // Default to request

    if (existingAcceptedMessage) {
      // If there's already an accepted message in this chat, keep it in messages
      messageStatus = 'delivered';
      console.log(`📨 Existing accepted chat found, sending as delivered`);
    } else {
      // No accepted messages yet, check follow status
      const Follow = require('../models/Follow');

      // Check if receiver follows sender (for mutual follow)
      const receiverFollowsSender = await Follow.findOne({
        followerUid: receiverUid,
        followingUid: senderUid
      });

      // Check if sender follows receiver
      const senderFollowsReceiver = await Follow.findOne({
        followerUid: senderUid,
        followingUid: receiverUid
      });

      console.log('🔍 FOLLOW CHECK RESULTS:');
      console.log(`   receiverFollowsSender: ${receiverFollowsSender ? 'YES' : 'NO'}`);
      console.log(`   senderFollowsReceiver: ${senderFollowsReceiver ? 'YES' : 'NO'}`);

      // If they follow each other (mutual), go to inbox
      if (receiverFollowsSender && senderFollowsReceiver) {
        messageStatus = 'delivered';
      } 
      // If sender is already following receiver, still go to inbox (they initiated)
      else if (senderFollowsReceiver) {
        messageStatus = 'delivered';
      }
    }
    
    console.log(`   Final messageStatus: ${messageStatus}`);

    // Create message with appropriate status
    const message = new Message({
      messageId: `msg_${Date.now()}_${uuidv4().substring(0, 8)}`,
      chatId: chat.chatId,
      senderUid,
      receiverUid,
      text: text || '',
      imageUrl: imageUrl || '',
      status: messageStatus,
      deliveredAt: messageStatus === 'delivered' ? new Date() : null
    });

    await message.save();
    console.log(`✅ Message saved with status: ${message.status}`);

    // Update chat last message
    chat.lastMessage = text || (imageUrl ? '📷 Image' : '');
    chat.lastMessageAt = new Date();
    await chat.save();

    // Get sender info
    const sender = await User.findOne({ firebaseUid: senderUid })
      .select('username profileImageUrl');

    // 🔥 FIXED: Include username in message data
    const messageData = {
      messageId: message.messageId,
      chatId: message.chatId,
      senderUid: message.senderUid,
      receiverUid: message.receiverUid,
      text: message.text,
      imageUrl: message.imageUrl,
      isRead: false,
      status: message.status,
      createdAt: message.createdAt,
      fromUserName: sender ? sender.username : 'Someone',
      fromUserId: senderUid
    };

    // Emit socket events
    const io = req.app.get('io');
    
    // 🔥 IMPORTANT: Only emit to user-specific rooms, NOT globally
    // This prevents messages from appearing in Home feed
    
    // Always notify receiver
    io.to(receiverUid).emit('new-message', messageData);
    
    // If it's a request, emit a special event
    if (messageStatus === 'pending') {
      io.to(receiverUid).emit('new-message-request', {
        ...messageData,
        fromUserName: sender ? sender.username : 'Someone',
        message: 'Message request from ' + (sender ? sender.username : 'Someone')
      });
    }
    
    io.to(senderUid).emit('message-sent', messageData);
    io.to(chat.chatId).emit('chat-message', messageData);

    console.log(`📨 Message sent with status: ${messageStatus}`);

    // Create notification
    try {
      const Notification = require('../models/Notification');
      
      const notificationMessage = messageStatus === 'pending' 
        ? `${sender ? sender.username : 'Someone'} sent you a message request`
        : (text || (imageUrl ? '📷 Sent you an image' : 'New message'));
      
      const notification = new Notification({
        notificationId: `notif_${Date.now()}_${uuidv4().substring(0, 8)}`,
        userId: receiverUid,
        fromUserId: senderUid,
        fromUserName: sender ? sender.username : 'Someone',
        fromUserImage: sender ? sender.profileImageUrl : '',
        type: messageStatus === 'pending' ? 'message_request' : 'message',
        postId: '',
        message: notificationMessage
      });
      
      await notification.save();
      
      io.to(receiverUid).emit('new-notification', {
        notificationId: notification.notificationId,
        message: notification.message,
        type: notification.type,
        fromUserName: sender ? sender.username : 'Someone',
        createdAt: notification.createdAt
      });
      
    } catch (notifError) {
      console.error('Failed to create notification:', notifError);
    }

    res.status(201).json({
      success: true,
      message: 'Message sent',
      data: messageData,
      chatId: chat.chatId,
      status: messageStatus
    });
  } catch (error) {
    console.error('❌ Send message error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/chat/debug/:userId
 * Debug endpoint to see all chats for a user (including deleted)
 */
router.get('/debug/:userId', async (req, res) => {
  try {
    const userId = req.params.userId;
    
    const allChats = await Chat.find({ participants: userId });
    const deletedChats = await ChatDelete.find({ userId });
    const chatClears = await ChatClear.find({ userId });
    
    res.json({
      success: true,
      allChats: allChats.map(c => ({ chatId: c.chatId, lastMessageAt: c.lastMessageAt })),
      deletedChats: deletedChats.map(d => ({ chatId: d.chatId, deletedAt: d.deletedAt })),
      clearedChats: chatClears.map(c => ({ chatId: c.chatId, clearedAt: c.clearedAt }))
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * PUT /api/chat/messages/:messageId/read
 * Mark message as read
 */
router.put('/messages/:messageId/read', async (req, res) => {
  try {
    const message = await Message.findOneAndUpdate(
      { messageId: req.params.messageId },
      { isRead: true },
      { new: true }
    );

    if (!message) {
      return res.status(404).json({
        success: false,
        message: 'Message not found'
      });
    }

    // Emit read receipt
    const io = req.app.get('io');
    io.to(message.senderUid).emit('message-read', {
      messageId: message.messageId,
      chatId: message.chatId,
      readAt: new Date()
    });

    res.json({
      success: true,
      message: 'Message marked as read'
    });
  } catch (error) {
    console.error('Mark read error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * PUT /api/chat/:chatId/read-all
 * Mark all messages in chat as read
 */
router.put('/:chatId/read-all', async (req, res) => {
  try {
    const { firebaseUid } = req.body;

    // Check if user cleared this chat
    const chatClear = await ChatClear.findOne({
      chatId: req.params.chatId,
      userId: firebaseUid
    });

    let query = { 
      chatId: req.params.chatId, 
      receiverUid: firebaseUid, 
      isRead: false 
    };
    
    if (chatClear) {
      query.createdAt = { $gt: chatClear.clearedAt };
    }

    const result = await Message.updateMany(
      query,
      { isRead: true }
    );

    // Emit event that all messages are read
    if (result.modifiedCount > 0) {
      const io = req.app.get('io');
      io.to(req.params.chatId).emit('all-messages-read', {
        chatId: req.params.chatId,
        readerUid: firebaseUid
      });
    }

    res.json({
      success: true,
      message: 'All messages marked as read',
      count: result.modifiedCount
    });
  } catch (error) {
    console.error('Mark all read error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/chat/mute
 * Mute notifications from a specific user
 * Body: { userId, userToMute }
 */
router.post('/mute', async (req, res) => {
  try {
    const { userId, userToMute } = req.body;
    
    if (!userId || !userToMute) {
      return res.status(400).json({
        success: false,
        message: 'userId and userToMute are required'
      });
    }

    // Cannot mute yourself
    if (userId === userToMute) {
      return res.status(400).json({
        success: false,
        message: 'Cannot mute yourself'
      });
    }

    const user = await User.findOne({ firebaseUid: userId });
    
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Check if already muted
    const alreadyMuted = user.mutedUsers.some(m => m.userId === userToMute);
    
    if (alreadyMuted) {
      return res.status(400).json({
        success: false,
        message: 'User already muted'
      });
    }

    // Add to muted list
    user.mutedUsers.push({
      userId: userToMute,
      mutedAt: new Date()
    });

    await user.save();

    console.log(`🔇 User ${userId} muted ${userToMute}`);

    res.json({
      success: true,
      message: 'User muted successfully'
    });
  } catch (error) {
    console.error('Mute error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/chat/unmute
 * Unmute notifications from a specific user
 * Body: { userId, userToUnmute }
 */
router.post('/unmute', async (req, res) => {
  try {
    const { userId, userToUnmute } = req.body;
    
    if (!userId || !userToUnmute) {
      return res.status(400).json({
        success: false,
        message: 'userId and userToUnmute are required'
      });
    }

    const user = await User.findOne({ firebaseUid: userId });
    
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Remove from muted list
    user.mutedUsers = user.mutedUsers.filter(m => m.userId !== userToUnmute);
    await user.save();

    console.log(`🔊 User ${userId} unmuted ${userToUnmute}`);

    res.json({
      success: true,
      message: 'User unmuted successfully'
    });
  } catch (error) {
    console.error('Unmute error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/chat/muted/:userId
 * Get all muted users for a user
 */
router.get('/muted/:userId', async (req, res) => {
  try {
    const user = await User.findOne({ firebaseUid: req.params.userId })
      .select('mutedUsers');

    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'User not found'
      });
    }

    // Get details of muted users
    const mutedUserIds = user.mutedUsers.map(m => m.userId);
    
    const mutedUsersDetails = await User.find({
      firebaseUid: { $in: mutedUserIds }
    }).select('firebaseUid username fullName profileImageUrl');

    // Combine with mute timestamps
    const result = user.mutedUsers.map(mute => {
      const details = mutedUsersDetails.find(u => u.firebaseUid === mute.userId);
      return {
        userId: mute.userId,
        mutedAt: mute.mutedAt,
        username: details ? details.username : 'Unknown User',
        fullName: details ? details.fullName : 'Unknown User',
        profileImageUrl: details ? details.profileImageUrl : ''
      };
    });

    res.json({
      success: true,
      count: result.length,
      mutedUsers: result
    });
  } catch (error) {
    console.error('Get muted users error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/chat/messages/:messageId
 * Delete a message
 */
router.delete('/messages/:messageId', async (req, res) => {
  try {
    const { senderUid } = req.query;

    const message = await Message.findOneAndDelete({
      messageId: req.params.messageId,
      senderUid
    });

    if (!message) {
      return res.status(404).json({
        success: false,
        message: 'Message not found or unauthorized'
      });
    }

    // Emit message deletion event
    const io = req.app.get('io');
    io.to(message.chatId).emit('message-deleted', {
      messageId: message.messageId,
      chatId: message.chatId
    });

    res.json({
      success: true,
      message: 'Message deleted'
    });
  } catch (error) {
    console.error('Delete message error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/chat/:chatId/clear
 * Clear messages for a specific user (doesn't delete for the other person)
 * Query: userId
 */
router.delete('/:chatId/clear', async (req, res) => {
  try {
    const { userId } = req.query;
    const { chatId } = req.params;

    if (!userId) {
      return res.status(400).json({
        success: false,
        message: 'userId is required'
      });
    }

    console.log(`🗑️ User ${userId} clearing chat ${chatId} (for themselves only)`);

    // Verify user is part of this chat
    const chat = await Chat.findOne({ 
      chatId: chatId,
      participants: userId 
    });

    if (!chat) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found or unauthorized'
      });
    }

    // Save that this user cleared the chat
    await ChatClear.findOneAndUpdate(
      { chatId, userId },
      { clearedAt: new Date() },
      { upsert: true, new: true }
    );

    console.log(`✅ Chat cleared for user ${userId}`);

    // Emit socket event to this specific user only
    const io = req.app.get('io');
    io.to(userId).emit('chat-cleared', { 
      chatId, 
      clearedBy: userId 
    });

    res.json({
      success: true,
      message: 'Chat cleared successfully (for you only)'
    });
  } catch (error) {
    console.error('❌ Clear chat error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * DELETE /api/chat/conversation/:chatId
 * Remove conversation from user's inbox (hide it completely)
 */
router.delete('/conversation/:chatId', async (req, res) => {
  try {
    const { userId } = req.query;
    const { chatId } = req.params;

    if (!userId) {
      return res.status(400).json({
        success: false,
        message: 'userId is required'
      });
    }

    console.log(`🗑️ User ${userId} removing conversation ${chatId} from inbox`);

    // Verify user is part of this chat
    const chat = await Chat.findOne({ 
      chatId: chatId,
      participants: userId 
    });

    if (!chat) {
      return res.status(404).json({
        success: false,
        message: 'Chat not found or unauthorized'
      });
    }

    // Save that this user deleted the conversation
    await ChatDelete.findOneAndUpdate(
      { chatId, userId },
      { 
        deletedAt: new Date(),
        deletedByUser: true 
      },
      { upsert: true, new: true }
    );

    console.log(`✅ Conversation marked as deleted for user ${userId}`);

    res.json({
      success: true,
      message: 'Conversation removed from inbox successfully'
    });
  } catch (error) {
    console.error('❌ Delete conversation error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * POST /api/chat/conversation/:chatId/restore
 * Restore a deleted conversation
 */
router.post('/conversation/:chatId/restore', async (req, res) => {
  try {
    const { userId } = req.body;
    const { chatId } = req.params;

    if (!userId) {
      return res.status(400).json({
        success: false,
        message: 'userId is required'
      });
    }

    console.log(`🔄 Restoring conversation ${chatId} for user ${userId}`);

    // Remove the delete marker
    const result = await ChatDelete.findOneAndDelete({ chatId, userId });

    if (!result) {
      return res.status(404).json({
        success: false,
        message: 'Conversation not found in deleted list'
      });
    }

    console.log(`✅ Conversation restored for user ${userId}`);

    res.json({
      success: true,
      message: 'Conversation restored successfully'
    });
  } catch (error) {
    console.error('❌ Restore conversation error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

module.exports = router;