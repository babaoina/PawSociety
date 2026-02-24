package com.example.pawsociety.api

import com.google.gson.annotations.SerializedName

// API Response wrappers
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null
)

data class ApiListResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("posts") val posts: List<T>? = null,
    @SerializedName("users") val users: List<T>? = null,
    @SerializedName("comments") val comments: List<T>? = null,
    @SerializedName("messages") val messages: List<T>? = null,
    @SerializedName("conversations") val conversations: List<T>? = null,
    @SerializedName("pets") val pets: List<T>? = null,
    @SerializedName("notifications") val notifications: List<T>? = null
)

// User API Models
data class ApiUser(
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("phone") val phone: String? = "",
    @SerializedName("profileImageUrl") val profileImageUrl: String? = "",
    @SerializedName("bio") val bio: String? = "",
    @SerializedName("location") val location: String? = "",
    @SerializedName("createdAt") val createdAt: String? = null
)

data class FirebaseLoginRequest(
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("phone") val phone: String? = null
)

data class UpdateUserRequest(
    @SerializedName("username") val username: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("profileImageUrl") val profileImageUrl: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("location") val location: String? = null
)

// Post API Models
data class ApiPost(
    @SerializedName("postId") val postId: String,
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("userName") val userName: String,
    @SerializedName("userImageUrl") val userImageUrl: String? = "",
    @SerializedName("petName") val petName: String,
    @SerializedName("petType") val petType: String,
    @SerializedName("status") val status: String,
    @SerializedName("description") val description: String,
    @SerializedName("location") val location: String? = "",
    @SerializedName("reward") val reward: String? = "",
    @SerializedName("contactInfo") val contactInfo: String,
    @SerializedName("imageUrls") val imageUrls: List<String>? = emptyList(),
    @SerializedName("likesCount") val likesCount: Int = 0,
    @SerializedName("commentsCount") val commentsCount: Int = 0,
    @SerializedName("shares") val shares: Int = 0,
    @SerializedName("createdAt") val createdAt: String
)

data class CreatePostRequest(
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("petName") val petName: String,
    @SerializedName("petType") val petType: String,
    @SerializedName("status") val status: String,
    @SerializedName("description") val description: String,
    @SerializedName("location") val location: String? = null,
    @SerializedName("reward") val reward: String? = null,
    @SerializedName("contactInfo") val contactInfo: String,
    @SerializedName("imageUrls") val imageUrls: List<String>? = null
)

// Comment API Models
data class ApiComment(
    @SerializedName("commentId") val commentId: String,
    @SerializedName("postId") val postId: String,
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("userName") val userName: String,
    @SerializedName("userImageUrl") val userImageUrl: String? = "",
    @SerializedName("text") val text: String,
    @SerializedName("likesCount") val likesCount: Int = 0,
    @SerializedName("createdAt") val createdAt: String
)

data class CreateCommentRequest(
    @SerializedName("postId") val postId: String,
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("userName") val userName: String,
    @SerializedName("text") val text: String
)

// Chat API Models
data class ApiMessage(
    @SerializedName("messageId") val messageId: String,
    @SerializedName("chatId") val chatId: String,
    @SerializedName("senderUid") val senderUid: String,
    @SerializedName("receiverUid") val receiverUid: String,
    @SerializedName("text") val text: String? = "",
    @SerializedName("imageUrl") val imageUrl: String? = "",
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("createdAt") val createdAt: String
)

data class SendMessageRequest(
    @SerializedName("senderUid") val senderUid: String,
    @SerializedName("receiverUid") val receiverUid: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null
)

data class ApiConversation(
    @SerializedName("chatId") val chatId: String,
    @SerializedName("participants") val participants: List<String>,
    @SerializedName("lastMessage") val lastMessage: LastMessage? = null,
    @SerializedName("lastMessageAt") val lastMessageAt: String,
    @SerializedName("createdAt") val createdAt: String
)

data class LastMessage(
    @SerializedName("text") val text: String? = "",
    @SerializedName("imageUrl") val imageUrl: String? = "",
    @SerializedName("senderUid") val senderUid: String? = "",
    @SerializedName("createdAt") val createdAt: String? = ""
)

// Pet API Models
data class ApiPet(
    @SerializedName("petId") val petId: String,
    @SerializedName("ownerUid") val ownerUid: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("breed") val breed: String? = "",
    @SerializedName("description") val description: String,
    @SerializedName("imageUrl") val imageUrl: String? = "",
    @SerializedName("age") val age: String? = "",
    @SerializedName("gender") val gender: String? = "Unknown",
    @SerializedName("createdAt") val createdAt: String
)

data class CreatePetRequest(
    @SerializedName("ownerUid") val ownerUid: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("breed") val breed: String? = null,
    @SerializedName("description") val description: String,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("age") val age: String? = null,
    @SerializedName("gender") val gender: String? = null
)

// Like Response
data class LikeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("liked") val liked: Boolean? = null,
    @SerializedName("likesCount") val likesCount: Int = 0,
    @SerializedName("isLiked") val isLiked: Boolean? = null,
    @SerializedName("isFavorite") val isFavorite: Boolean? = null
)

// Notification API Models
data class ApiNotification(
    @SerializedName("notificationId") val notificationId: String,
    @SerializedName("userId") val userId: String,
    @SerializedName("fromUserId") val fromUserId: String,
    @SerializedName("fromUserName") val fromUserName: String,
    @SerializedName("fromUserImage") val fromUserImage: String? = "",
    @SerializedName("type") val type: String,
    @SerializedName("postId") val postId: String? = "",
    @SerializedName("message") val message: String,
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("createdAt") val createdAt: String
)

data class CreateNotificationRequest(
    @SerializedName("userId") val userId: String,
    @SerializedName("fromUserId") val fromUserId: String,
    @SerializedName("fromUserName") val fromUserName: String,
    @SerializedName("fromUserImage") val fromUserImage: String? = null,
    @SerializedName("type") val type: String,
    @SerializedName("postId") val postId: String? = null,
    @SerializedName("message") val message: String
)
