package com.example.pawsociety

import java.text.SimpleDateFormat
import java.util.*

data class AppUser(
    val uid: String = "",
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val location: String = "",
    val bio: String = "",
    val profileImageUrl: String = "",
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)

data class Post(
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userImageUrl: String = "",
    val petName: String = "",
    val petType: String = "",
    val status: String = "",
    val description: String = "",
    val location: String = "",
    val reward: String = "",
    val contactInfo: String = "",
    val imageUrls: List<String> = emptyList(),
    val likes: List<String> = emptyList(),
    val comments: List<Comment> = emptyList(), // Changed from Int to List<Comment>
    val shares: Int = 0,
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)

data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userImageUrl: String = "",
    val text: String = "",
    val likes: List<String> = emptyList(),
    val likesCount: Int = 0,
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)

data class Notification(
    val notificationId: String = "",
    val userId: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val fromUserImage: String = "",
    val type: String = "",
    val postId: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val senderName: String = "",
    val senderImage: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val isRead: Boolean = false,
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)