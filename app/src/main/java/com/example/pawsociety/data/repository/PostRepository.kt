package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository {
    
    private val apiService = ApiClient.apiService
    
    /**
     * Get all posts with optional filters
     */
    suspend fun getPosts(
        status: String? = null,
        firebaseUid: String? = null,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiPost>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPosts(status, firebaseUid, limit, skip)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.posts != null) {
                    Result.success(body.posts)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to get posts"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get posts"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get single post by ID
     */
    suspend fun getPost(postId: String): Result<ApiPost> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPost(postId)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Post not found"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get post"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create a new post
     */
    suspend fun createPost(
        firebaseUid: String,
        petName: String,
        petType: String,
        status: String,
        description: String,
        contactInfo: String,
        location: String? = null,
        reward: String? = null,
        imageUrls: List<String>? = null
    ): Result<ApiPost> = withContext(Dispatchers.IO) {
        try {
            val request = CreatePostRequest(
                firebaseUid = firebaseUid,
                petName = petName,
                petType = petType,
                status = status,
                description = description,
                contactInfo = contactInfo,
                location = location,
                reward = reward,
                imageUrls = imageUrls
            )

            println("📤 Creating post for user: $firebaseUid, pet: $petName")
            val response = apiService.createPost(request)
            println("📥 Response code: ${response.code()}, successful: ${response.isSuccessful}")

            if (response.isSuccessful) {
                val body = response.body()
                println("📦 Response body: success=${body?.success}, message=${body?.message}")
                if (body != null && body.success) {
                    // Backend returns post in 'data' field (from ApiResponse wrapper)
                    val createdPost = body.data
                    if (createdPost != null) {
                        println("✅ Post created successfully with ID: ${createdPost.postId}")
                        Result.success(createdPost)
                    } else {
                        println("❌ No post data in response")
                        Result.failure(Exception("Post created but no data returned"))
                    }
                } else {
                    val errorMsg = body?.message ?: "Failed to create post"
                    println("❌ Post creation failed: $errorMsg")
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ HTTP error: ${response.message()}, body: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to create post"))
            }
        } catch (e: Exception) {
            println("❌ Exception during post creation: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Delete a post
     */
    suspend fun deletePost(postId: String, firebaseUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.deletePost(postId, mapOf("firebaseUid" to firebaseUid))
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to delete post"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete post"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Like/unlike a post
     */
    suspend fun likePost(postId: String, firebaseUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.likePost(postId, mapOf("firebaseUid" to firebaseUid))
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body.liked ?: false)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to like post"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to like post"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if user liked a post
     */
    suspend fun checkLikeStatus(postId: String, firebaseUid: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.checkPostLikeStatus(postId, firebaseUid)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    Result.success(body.isLiked ?: false)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to check like status"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to check like status"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
