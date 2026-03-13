package com.example.pawsociety.api

import retrofit2.Response
import retrofit2.http.*

interface PawSocietyApi {

    // ==================== AUTH ====================
    @POST("auth/firebase-login")
    suspend fun firebaseLogin(@Body request: FirebaseLoginRequest): Response<ApiResponse<ApiUser>>

    @GET("users")
    suspend fun getUsers(
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiUser>>

    @GET("admin/settings")
    suspend fun getSettings(): Response<SettingsResponse>

    @POST("chat/accept-request/{chatId}")
    suspend fun acceptMessageRequest(
        @Path("chatId") chatId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>

    @DELETE("chat/reject-request/{chatId}")
    suspend fun rejectMessageRequest(
        @Path("chatId") chatId: String,
        @Query("userId") userId: String
    ): Response<ApiResponse<Unit>>

    @GET("users/batch")
    suspend fun getUsersBatch(
        @Query("userIds") userIds: String
    ): Response<BatchUserResponse>

    @DELETE("chat/{chatId}/clear")
    suspend fun clearChat(
        @Path("chatId") chatId: String,
        @Query("userId") userId: String
    ): Response<ApiResponse<Unit>>

    @GET("users/{firebaseUid}")
    suspend fun getUserByUid(@Path("firebaseUid") firebaseUid: String): Response<UserResponse>

    @PUT("users/{firebaseUid}")
    suspend fun updateUser(
        @Path("firebaseUid") firebaseUid: String,
        @Body request: UpdateUserRequest
    ): Response<ApiResponse<ApiUser>>

    @DELETE("users/{firebaseUid}")
    suspend fun deleteUser(@Path("firebaseUid") firebaseUid: String): Response<ApiResponse<Unit>>

    @PUT("users/{firebaseUid}/fcm-token")
    suspend fun saveFcmToken(
        @Path("firebaseUid") firebaseUid: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>

    // ==================== POSTS ====================
    @GET("posts")
    suspend fun getPosts(
        @Query("status") status: String? = null,
        @Query("firebaseUid") firebaseUid: String? = null,
        @Query("petCategory") petCategory: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiPost>>

    @GET("posts/{postId}")
    suspend fun getPost(@Path("postId") postId: String): Response<ApiResponse<ApiPost>>

    @POST("posts")
    suspend fun createPost(@Body request: CreatePostRequest): Response<ApiResponse<ApiPost>>

    @PUT("posts/{postId}")
    suspend fun updatePost(
        @Path("postId") postId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<ApiPost>>

    @DELETE("posts/{postId}")
    suspend fun deletePost(
        @Path("postId") postId: String,
        @Query("firebaseUid") firebaseUid: String
    ): Response<ApiResponse<Unit>>

    @POST("posts/{postId}/like")
    suspend fun likePost(
        @Path("postId") postId: String,
        @Body request: Map<String, String>
    ): Response<LikeResponse>

    @GET("posts/{postId}/is-liked")
    suspend fun checkPostLikeStatus(
        @Path("postId") postId: String,
        @Query("firebaseUid") firebaseUid: String
    ): Response<LikeResponse>

    // ==================== CHAT ====================
    @GET("chat/conversations/{firebaseUid}")
    suspend fun getConversations(
        @Path("firebaseUid") firebaseUid: String
    ): Response<ConversationsResponse>

    @GET("chat/{chatId}/messages")
    suspend fun getMessages(
        @Path("chatId") chatId: String,
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiMessage>>

    @POST("chat/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<ApiResponse<ApiMessage>>

    @PUT("chat/messages/{messageId}/read")
    suspend fun markMessageAsRead(@Path("messageId") messageId: String): Response<ApiResponse<Unit>>

    @PUT("chat/{chatId}/read-all")
    suspend fun markAllMessagesAsRead(
        @Path("chatId") chatId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>

    @DELETE("chat/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("messageId") messageId: String,
        @Query("senderUid") senderUid: String
    ): Response<ApiResponse<Unit>>

    @DELETE("chat/conversation/{chatId}")
    suspend fun deleteConversation(
        @Path("chatId") chatId: String,
        @Query("userId") userId: String
    ): Response<ApiResponse<Unit>>

    // ==================== PETS ====================
    @GET("pets")
    suspend fun getPets(
        @Query("ownerUid") ownerUid: String? = null,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiPet>>

    @GET("pets/{petId}")
    suspend fun getPet(@Path("petId") petId: String): Response<ApiResponse<ApiPet>>

    @POST("pets")
    suspend fun createPet(@Body request: CreatePetRequest): Response<ApiResponse<ApiPet>>

    @PUT("pets/{petId}")
    suspend fun updatePet(
        @Path("petId") petId: String,
        @Body request: Map<String, Any>
    ): Response<ApiResponse<ApiPet>>

    @DELETE("pets/{petId}")
    suspend fun deletePet(
        @Path("petId") petId: String,
        @Query("ownerUid") ownerUid: String
    ): Response<ApiResponse<Unit>>

    // ==================== FAVORITES ====================
    @GET("favorites/{firebaseUid}")
    suspend fun getFavorites(@Path("firebaseUid") firebaseUid: String): Response<ApiListResponse<ApiPost>>

    @POST("favorites")
    suspend fun addToFavorites(@Body request: Map<String, String>): Response<ApiResponse<Unit>>

    @DELETE("favorites/{postId}")
    suspend fun removeFromFavorites(
        @Path("postId") postId: String,
        @Query("userUid") userUid: String
    ): Response<ApiResponse<Unit>>

    @GET("favorites/check/{postId}")
    suspend fun checkFavorite(
        @Path("postId") postId: String,
        @Query("userUid") userUid: String
    ): Response<LikeResponse>

    // ==================== NOTIFICATIONS ====================
    @GET("notifications/{userId}")
    suspend fun getNotifications(
        @Path("userId") userId: String,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiNotification>>

    @POST("notifications")
    suspend fun createNotification(@Body request: CreateNotificationRequest): Response<ApiResponse<ApiNotification>>

    @PUT("notifications/{notificationId}/read")
    suspend fun markNotificationAsRead(@Path("notificationId") notificationId: String): Response<ApiResponse<Unit>>

    @PUT("notifications/{userId}/read-all")
    suspend fun markAllNotificationsAsRead(@Path("userId") userId: String): Response<ApiResponse<Unit>>

    // ==================== SEARCH ====================
    @GET("posts/search")
    suspend fun searchPosts(
        @Query("q") query: String,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiPost>>

    @GET("users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiUser>>

    // ==================== HIGHLIGHTS ====================
    @GET("users/{userId}/highlights")
    suspend fun getHighlights(
        @Path("userId") userId: String
    ): Response<ApiListResponse<ApiHighlight>>

    @POST("users/{userId}/highlights")
    suspend fun createHighlight(
        @Path("userId") userId: String,
        @Body request: CreateHighlightRequest
    ): Response<ApiResponse<ApiHighlight>>

    @PUT("users/{userId}/highlights/{highlightId}")
    suspend fun updateHighlight(
        @Path("userId") userId: String,
        @Path("highlightId") highlightId: String,
        @Body request: UpdateHighlightRequest
    ): Response<ApiResponse<ApiHighlight>>

    @DELETE("users/{userId}/highlights/{highlightId}")
    suspend fun deleteHighlight(
        @Path("userId") userId: String,
        @Path("highlightId") highlightId: String,
        @Query("userId") queryUserId: String
    ): Response<ApiResponse<Unit>>

    // ==================== FOLLOW ====================
    @POST("follow/follow")
    suspend fun followUser(@Body request: Map<String, String>): Response<ApiResponse<Unit>>

    @DELETE("follow/unfollow")
    suspend fun unfollowUser(
        @Query("followerUid") followerUid: String,
        @Query("followingUid") followingUid: String
    ): Response<ApiResponse<Unit>>

    @GET("follow/check")
    suspend fun checkFollowStatus(
        @Query("followerUid") followerUid: String,
        @Query("followingUid") followingUid: String
    ): Response<FollowCheckResponse>

    @GET("follow/counts/{userId}")
    suspend fun getFollowersCount(@Path("userId") userId: String): Response<FollowCountResponse>

    @GET("follow/counts/{userId}")
    suspend fun getFollowingCount(@Path("userId") userId: String): Response<FollowCountResponse>

    @GET("follow/followers/{userId}")
    suspend fun getFollowers(@Path("userId") userId: String): Response<ApiListResponse<ApiUser>>

    @GET("follow/following/{userId}")
    suspend fun getFollowing(@Path("userId") userId: String): Response<ApiListResponse<ApiUser>>

    // ==================== MUTE FEATURE ====================
    @POST("chat/mute")
    suspend fun muteUser(@Body request: Map<String, String>): Response<ApiResponse<Unit>>

    @POST("chat/unmute")
    suspend fun unmuteUser(@Body request: Map<String, String>): Response<ApiResponse<Unit>>

    @GET("chat/muted/{userId}")
    suspend fun getMutedUsers(@Path("userId") userId: String): Response<ApiResponse<List<MutedUser>>>

    // ==================== BLOCKS ====================
    @POST("blocks/block")
    suspend fun blockUser(@Body request: Map<String, String>): Response<ApiResponse<Unit>>

    @DELETE("blocks/unblock")
    suspend fun unblockUser(
        @Query("blockerUid") blockerUid: String,
        @Query("blockedUid") blockedUid: String
    ): Response<ApiResponse<Unit>>

    @GET("blocks/check")
    suspend fun checkBlockStatus(
        @Query("blockerUid") blockerUid: String,
        @Query("blockedUid") blockedUid: String
    ): Response<BlockCheckResponse>

    @POST("auth/check-status")
    suspend fun checkUserStatus(@Body request: Map<String, String>): Response<UserStatusResponse>

    @GET("blocks/{userId}")
    suspend fun getBlockedUsers(@Path("userId") userId: String): Response<ApiListResponse<Block>>

    // ==================== REPORTS ====================
    @POST("reports")
    suspend fun createReport(@Body request: ReportRequest): Response<ApiResponse<Report>>

    @POST("posts/hide")
    suspend fun hidePost(
        @Query("userUid") userUid: String,
        @Query("postId") postId: String
    ): Response<ApiResponse<Any>>

    @POST("posts/unhide")
    suspend fun unhidePost(
        @Query("userUid") userUid: String,
        @Query("postId") postId: String
    ): Response<ApiResponse<Any>>

    @GET("posts/hidden")
    suspend fun getHiddenPosts(
        @Query("userUid") userUid: String
    ): Response<ApiResponse<List<ApiPost>>>

    @GET("posts/hidden/count")
    suspend fun getHiddenCount(
        @Query("userUid") userUid: String
    ): Response<ApiResponse<Int>>
}