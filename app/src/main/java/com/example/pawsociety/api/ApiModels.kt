package com.example.pawsociety.api

import com.google.gson.annotations.SerializedName

// API Response wrappers
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("count") val count: Int? = null
)

// Specific response for single user
data class UserResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("user") val user: ApiUser? = null
)

data class ApiListResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("posts") val posts: List<T>? = null,
    @SerializedName("users") val users: List<T>? = null,
    @SerializedName("messages") val messages: List<T>? = null,
    @SerializedName("conversations") val conversations: List<T>? = null,
    @SerializedName("pets") val pets: List<T>? = null,
    @SerializedName("notifications") val notifications: List<T>? = null,
    @SerializedName("highlights") val highlights: List<T>? = null,
    @SerializedName("blocks") val blocks: List<Block>? = null
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

// Batch user response
data class BatchUserResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("users") val users: Map<String, BatchUser>? = null,
    @SerializedName("message") val message: String? = null
)

data class BatchUser(
    @SerializedName("username") val username: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("profileImageUrl") val profileImageUrl: String?
)

// Post API Models with GENDER
// Post API Models with AGE and WEIGHT
data class ApiPost(
    @SerializedName("postId") val postId: String,
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("userName") val userName: String,
    @SerializedName("userImageUrl") val userImageUrl: String? = "",
    @SerializedName("petName") val petName: String,
    @SerializedName("petType") val petType: String,
    @SerializedName("age") val age: String? = "",  // ADD THIS
    @SerializedName("weight") val weight: String? = "",  // ADD THIS
    @SerializedName("gender") val gender: String? = "Unknown",
    @SerializedName("status") val status: String,
    @SerializedName("description") val description: String,
    @SerializedName("location") val location: String? = "",
    @SerializedName("reward") val reward: String? = "",
    @SerializedName("contactInfo") val contactInfo: String,
    @SerializedName("imageUrls") val imageUrls: List<String>? = emptyList(),
    @SerializedName("likesCount") val likesCount: Int = 0,
    @SerializedName("createdAt") val createdAt: String
) : java.io.Serializable

data class CreatePostRequest(
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("petName") val petName: String,
    @SerializedName("petType") val petType: String,
    @SerializedName("age") val age: String? = null,  // ADD THIS
    @SerializedName("weight") val weight: String? = null,  // ADD THIS
    @SerializedName("gender") val gender: String? = "Unknown",
    @SerializedName("status") val status: String,
    @SerializedName("description") val description: String,
    @SerializedName("location") val location: String? = null,
    @SerializedName("reward") val reward: String? = null,
    @SerializedName("contactInfo") val contactInfo: String,
    @SerializedName("imageUrls") val imageUrls: List<String>? = null
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
    @SerializedName("otherUser") val otherUser: OtherUser? = null,
    @SerializedName("lastMessage") val lastMessage: LastMessage? = null,
    @SerializedName("lastMessageAt") val lastMessageAt: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("unreadCount") val unreadCount: Int = 0,
    @SerializedName("pendingCount") val pendingCount: Int = 0,
    @SerializedName("clearedAt") val clearedAt: String? = null
)

data class ConversationsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("messages") val messages: List<ApiConversation>? = null,
    @SerializedName("requests") val requests: List<ApiConversation>? = null,
    @SerializedName("messagesCount") val messagesCount: Int = 0,
    @SerializedName("requestsCount") val requestsCount: Int = 0,
    @SerializedName("message") val message: String? = null
)

data class OtherUser(
    @SerializedName("firebaseUid") val firebaseUid: String,
    @SerializedName("username") val username: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("profileImageUrl") val profileImageUrl: String? = ""
)

data class LastMessage(
    @SerializedName("text") val text: String? = "",
    @SerializedName("imageUrl") val imageUrl: String? = "",
    @SerializedName("senderUid") val senderUid: String? = "",
    @SerializedName("createdAt") val createdAt: String? = "",
    @SerializedName("isRead") val isRead: Boolean = false
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

// Highlight API Models
data class ApiHighlight(
    @SerializedName("highlightId") val highlightId: String = "",
    @SerializedName("userId") val userId: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("emoji") val emoji: String = "📸",
    @SerializedName("color") val color: String = "#FF6B35",
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("postIds") val postIds: List<String> = emptyList(),
    @SerializedName("createdAt") val createdAt: String = ""
)

data class CreateHighlightRequest(
    @SerializedName("name") val name: String,
    @SerializedName("emoji") val emoji: String,
    @SerializedName("color") val color: String,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("postIds") val postIds: List<String> = emptyList()
)

data class UpdateHighlightRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("emoji") val emoji: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("postIds") val postIds: List<String>? = null
)

// Follow response models
data class FollowCheckResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("isFollowing") val isFollowing: Boolean? = false,
    @SerializedName("message") val message: String? = null
)

data class FollowCountResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("followersCount") val followersCount: Int? = 0,
    @SerializedName("followingCount") val followingCount: Int? = 0,
    @SerializedName("message") val message: String? = null
)

data class CheckUserResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("exists") val exists: Boolean? = false,
    @SerializedName("user") val user: ApiUser? = null,
    @SerializedName("message") val message: String? = null
)

// Block Models
data class BlockCheckResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("isBlocked") val isBlocked: Boolean? = false,
    @SerializedName("message") val message: String? = null
)

data class Block(
    @SerializedName("blockId") val blockId: String,
    @SerializedName("blockerUid") val blockerUid: String,
    @SerializedName("blockedUid") val blockedUid: String,
    @SerializedName("createdAt") val createdAt: String
)

// Report Models
data class ReportRequest(
    @SerializedName("reporterUid") val reporterUid: String,
    @SerializedName("reportedUid") val reportedUid: String? = null,
    @SerializedName("postId") val postId: String? = null,
    @SerializedName("commentId") val commentId: String? = null,
    @SerializedName("reason") val reason: String,
    @SerializedName("description") val description: String? = null
)

data class Report(
    @SerializedName("reportId") val reportId: String,
    @SerializedName("reporterUid") val reporterUid: String,
    @SerializedName("reportedUid") val reportedUid: String?,
    @SerializedName("postId") val postId: String?,
    @SerializedName("commentId") val commentId: String?,
    @SerializedName("reason") val reason: String,
    @SerializedName("description") val description: String?,
    @SerializedName("status") val status: String,
    @SerializedName("createdAt") val createdAt: String
)

data class UserStatusResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null
)

// Mute Feature Models
data class MutedUser(
    @SerializedName("userId") val userId: String,
    @SerializedName("mutedAt") val mutedAt: String,
    @SerializedName("username") val username: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("profileImageUrl") val profileImageUrl: String?
)