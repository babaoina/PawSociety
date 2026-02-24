const express = require('express');
const router = express.Router();
const Chat = require('../models/Chat');
const Message = require('../models/Message');
const User = require('../models/User');
const { v4: uuidv4 } = require('uuid');

/**
 * GET /api/chat/conversations/:firebaseUid
 * Get all conversations for a user
 */
router.get('/conversations/:firebaseUid', async (req, res) => {
  try {
    const chats = await Chat.find({
      participants: req.params.firebaseUid
    })
    .sort({ lastMessageAt: -1 })
    .populate('participants', 'username profileImageUrl');

    // Get last message for each chat
    const conversations = await Promise.all(
      chats.map(async (chat) => {
        const lastMessage = await Message.findOne({ chatId: chat.chatId })
          .sort({ createdAt: -1 })
          .populate('senderUid', 'username profileImageUrl');
        
        return {
          chatId: chat.chatId,
          participants: chat.participants,
          lastMessage: lastMessage ? {
            text: lastMessage.text,
            imageUrl: lastMessage.imageUrl,
            senderUid: lastMessage.senderUid,
            createdAt: lastMessage.createdAt
          } : null,
          lastMessageAt: chat.lastMessageAt,
          createdAt: chat.createdAt
        };
      })
    );

    res.json({
      success: true,
      count: conversations.length,
      conversations
    });
  } catch (error) {
    console.error('Get conversations error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

/**
 * GET /api/chat/:chatId/messages
 * Get all messages in a chat
 */
router.get('/:chatId/messages', async (req, res) => {
  try {
    const { limit = 50, skip = 0 } = req.query;

    const messages = await Message.find({ chatId: req.params.chatId })
      .sort({ createdAt: -1 })
      .limit(parseInt(limit))
      .skip(parseInt(skip))
      .populate('senderUid', 'username profileImageUrl');

    res.json({
      success: true,
      count: messages.length,
      messages: messages.reverse() // Return in chronological order
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
 * Send a message
 * Body: { senderUid, receiverUid, text, imageUrl }
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
    }

    // Create message
    const message = new Message({
      messageId: `msg_${Date.now()}_${uuidv4().substring(0, 8)}`,
      chatId: chat.chatId,
      senderUid,
      receiverUid,
      text: text || '',
      imageUrl: imageUrl || ''
    });

    await message.save();

    // Update chat last message
    chat.lastMessage = text || imageUrl ? 'Sent media' : '';
    chat.lastMessageAt = new Date();
    await chat.save();

    // Populate sender info for response
    await message.populate('senderUid', 'username profileImageUrl');

    res.status(201).json({
      success: true,
      message: 'Message sent',
      data: {
        messageId: message.messageId,
        chatId: message.chatId,
        senderUid: message.senderUid,
        receiverUid: message.receiverUid,
        text: message.text,
        imageUrl: message.imageUrl,
        isRead: message.isRead,
        createdAt: message.createdAt
      },
      chatId: chat.chatId
    });
  } catch (error) {
    console.error('Send message error:', error);
    res.status(500).json({
      success: false,
      message: error.message
    });
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

    await Message.updateMany(
      { chatId: req.params.chatId, receiverUid: firebaseUid },
      { isRead: true }
    );

    res.json({
      success: true,
      message: 'All messages marked as read'
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
 * DELETE /api/chat/messages/:messageId
 * Delete a message
 */
router.delete('/messages/:messageId', async (req, res) => {
  try {
    const { firebaseUid } = req.body;

    const message = await Message.findOneAndDelete({
      messageId: req.params.messageId,
      senderUid: firebaseUid
    });

    if (!message) {
      return res.status(404).json({
        success: false,
        message: 'Message not found or unauthorized'
      });
    }

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

module.exports = router;
