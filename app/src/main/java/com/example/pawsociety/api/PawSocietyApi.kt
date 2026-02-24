package com.example.pawsociety.api

import retrofit2.Response
import retrofit2.http.*

interface PawSocietyApi {
    
    // ==================== AUTH ====================
    
    /**
     * Login/Register using Firebase UID
     * POST /api/auth/firebase-login
     */
    @POST("auth/firebase-login")
    suspend fun firebaseLogin(@Body request: FirebaseLoginRequest): Response<ApiResponse<ApiUser>>
    
    /**
     * Get all users
     * GET /api/users
     */
    @GET("users")
    suspend fun getUsers(
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiUser>>
    
    /**
     * Get user by Firebase UID
     * GET /api/users/:firebaseUid
     */
    @GET("users/{firebaseUid}")
    suspend fun getUserByUid(@Path("firebaseUid") firebaseUid: String): Response<ApiResponse<ApiUser>>
    
    /**
     * Update user profile
     * PUT /api/users/:firebaseUid
     */
    @PUT("users/{firebaseUid}")
    suspend fun updateUser(
        @Path("firebaseUid") firebaseUid: String,
        @Body request: UpdateUserRequest
    ): Response<ApiResponse<ApiUser>>
    
    // ==================== POSTS ====================
    
    /**
     * Get all posts
     * GET /api/posts
     */
    @GET("posts")
    suspend fun getPosts(
        @Query("status") status: String? = null,
        @Query("firebaseUid") firebaseUid: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiPost>>
    
    /**
     * Get single post
     * GET /api/posts/:postId
     */
    @GET("posts/{postId}")
    suspend fun getPost(@Path("postId") postId: String): Response<ApiResponse<ApiPost>>
    
    /**
     * Create a new post
     * POST /api/posts
     */
    @POST("posts")
    suspend fun createPost(@Body request: CreatePostRequest): Response<ApiResponse<ApiPost>>
    
    /**
     * Update a post
     * PUT /api/posts/:postId
     */
    @PUT("posts/{postId}")
    suspend fun updatePost(
        @Path("postId") postId: String,
        @Body request: Map<String, Any>
    ): Response<ApiResponse<ApiPost>>
    
    /**
     * Delete a post
     * DELETE /api/posts/:postId
     */
    @DELETE("posts/{postId}")
    suspend fun deletePost(
        @Path("postId") postId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>
    
    /**
     * Like/unlike a post
     * POST /api/posts/:postId/like
     */
    @POST("posts/{postId}/like")
    suspend fun likePost(
        @Path("postId") postId: String,
        @Body request: Map<String, String>
    ): Response<LikeResponse>
    
    /**
     * Check if user liked a post
     * GET /api/posts/:postId/is-liked
     */
    @GET("posts/{postId}/is-liked")
    suspend fun checkPostLikeStatus(
        @Path("postId") postId: String,
        @Query("firebaseUid") firebaseUid: String
    ): Response<LikeResponse>
    
    // ==================== COMMENTS ====================
    
    /**
     * Get comments for a post
     * GET /api/comments/post/:postId
     */
    @GET("comments/post/{postId}")
    suspend fun getComments(@Path("postId") postId: String): Response<ApiListResponse<ApiComment>>
    
    /**
     * Add a comment
     * POST /api/comments
     */
    @POST("comments")
    suspend fun createComment(@Body request: CreateCommentRequest): Response<ApiResponse<ApiComment>>
    
    /**
     * Delete a comment
     * DELETE /api/comments/:commentId
     */
    @DELETE("comments/{commentId}")
    suspend fun deleteComment(
        @Path("commentId") commentId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>
    
    /**
     * Like/unlike a comment
     * POST /api/comments/:commentId/like
     */
    @POST("comments/{commentId}/like")
    suspend fun likeComment(
        @Path("commentId") commentId: String,
        @Body request: Map<String, String>
    ): Response<LikeResponse>
    
    // ==================== CHAT ====================
    
    /**
     * Get all conversations for a user
     * GET /api/chat/conversations/:firebaseUid
     */
    @GET("chat/conversations/{firebaseUid}")
    suspend fun getConversations(@Path("firebaseUid") firebaseUid: String): Response<ApiListResponse<ApiConversation>>
    
    /**
     * Get messages in a chat
     * GET /api/chat/:chatId/messages
     */
    @GET("chat/{chatId}/messages")
    suspend fun getMessages(
        @Path("chatId") chatId: String,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiMessage>>
    
    /**
     * Send a message
     * POST /api/chat/send
     */
    @POST("chat/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<ApiResponse<ApiMessage>>
    
    /**
     * Mark message as read
     * PUT /api/chat/messages/:messageId/read
     */
    @PUT("chat/messages/{messageId}/read")
    suspend fun markMessageAsRead(@Path("messageId") messageId: String): Response<ApiResponse<Unit>>
    
    /**
     * Mark all messages in chat as read
     * PUT /api/chat/:chatId/read-all
     */
    @PUT("chat/{chatId}/read-all")
    suspend fun markAllMessagesAsRead(
        @Path("chatId") chatId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>
    
    /**
     * Delete a message
     * DELETE /api/chat/messages/:messageId
     */
    @DELETE("chat/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("messageId") messageId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>
    
    // ==================== PETS ====================
    
    /**
     * Get all pets
     * GET /api/pets
     */
    @GET("pets")
    suspend fun getPets(
        @Query("ownerUid") ownerUid: String? = null,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiPet>>
    
    /**
     * Get single pet
     * GET /api/pets/:petId
     */
    @GET("pets/{petId}")
    suspend fun getPet(@Path("petId") petId: String): Response<ApiResponse<ApiPet>>
    
    /**
     * Add a new pet
     * POST /api/pets
     */
    @POST("pets")
    suspend fun createPet(@Body request: CreatePetRequest): Response<ApiResponse<ApiPet>>
    
    /**
     * Update a pet
     * PUT /api/pets/:petId
     */
    @PUT("pets/{petId}")
    suspend fun updatePet(
        @Path("petId") petId: String,
        @Body request: Map<String, Any>
    ): Response<ApiResponse<ApiPet>>
    
    /**
     * Delete a pet
     * DELETE /api/pets/:petId
     */
    @DELETE("pets/{petId}")
    suspend fun deletePet(
        @Path("petId") petId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>
    
    // ==================== FAVORITES ====================
    
    /**
     * Get all favorites for a user
     * GET /api/favorites/:firebaseUid
     */
    @GET("favorites/{firebaseUid}")
    suspend fun getFavorites(@Path("firebaseUid") firebaseUid: String): Response<ApiListResponse<ApiPost>>
    
    /**
     * Add to favorites
     * POST /api/favorites
     */
    @POST("favorites")
    suspend fun addToFavorites(@Body request: Map<String, String>): Response<ApiResponse<Unit>>
    
    /**
     * Remove from favorites
     * DELETE /api/favorites/:postId
     */
    @DELETE("favorites/{postId}")
    suspend fun removeFromFavorites(
        @Path("postId") postId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>
    
    /**
     * Check if post is in favorites
     * GET /api/favorites/check/:postId
     */
    @GET("favorites/check/{postId}")
    suspend fun checkFavorite(
        @Path("postId") postId: String,
        @Query("userUid") userUid: String
    ): Response<LikeResponse>
    
    // ==================== NOTIFICATIONS ====================
    
    /**
     * Get notifications for a user
     * GET /api/notifications/:userId
     */
    @GET("notifications/{userId}")
    suspend fun getNotifications(
        @Path("userId") userId: String,
        @Query("limit") limit: Int = 50,
        @Query("skip") skip: Int = 0
    ): Response<ApiListResponse<ApiNotification>>
    
    /**
     * Create a notification
     * POST /api/notifications
     */
    @POST("notifications")
    suspend fun createNotification(@Body request: CreateNotificationRequest): Response<ApiResponse<ApiNotification>>
    
    /**
     * Mark notification as read
     * PUT /api/notifications/:notificationId/read
     */
    @PUT("notifications/{notificationId}/read")
    suspend fun markNotificationAsRead(@Path("notificationId") notificationId: String): Response<ApiResponse<Unit>>
    
    /**
     * Mark all notifications as read
     * PUT /api/notifications/:userId/read-all
     */
    @PUT("notifications/{userId}/read-all")
    suspend fun markAllNotificationsAsRead(@Path("userId") userId: String): Response<ApiResponse<Unit>>
}
