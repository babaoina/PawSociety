package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository {

    private val apiService = ApiClient.apiService

    suspend fun getPosts(
        status: String? = null,
        firebaseUid: String? = null,
        viewerUid: String? = null,
        petCategory: String? = null,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiPost>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPosts(status, firebaseUid, viewerUid, petCategory, limit, skip)

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

    suspend fun getPostsByCategory(
        category: String,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiPost>> = withContext(Dispatchers.IO) {
        try {
            val petCategory = when (category) {
                "Dogs" -> "dog"
                "Cats" -> "cat"
                "Fish" -> "fish"
                "Birds" -> "bird"
                else -> null
            }

            getPosts(
                status = null,
                firebaseUid = null,
                viewerUid = null,
                petCategory = petCategory,
                limit = limit,
                skip = skip
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    // In PostRepository.kt, update createPost function

    suspend fun createPost(
        firebaseUid: String,
        petName: String,
        petType: String,
        category: String? = null,
        age: String? = "",
        weight: String? = "",
        gender: String? = "Unknown",
        status: String,
        description: String,
        contactInfo: String,
        location: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        reward: String? = null,
        caseType: String? = null,
        resolvedStatus: String? = null,
        isResolved: Boolean = false,
        eventDate: String? = null,
        eventLocation: String? = null,
        currentCareStatus: String? = null,
        identifyingMarks: String? = null,
        temperament: String? = null,
        healthCondition: String? = null,
        hasCollar: Boolean = false,
        contactPreference: String? = "call",
        imageUrls: List<String>? = null
    ): Result<ApiPost> = withContext(Dispatchers.IO) {
        try {
            val request = CreatePostRequest(
                firebaseUid = firebaseUid,
                petName = petName,
                petType = petType,
                category = category,
                age = age,
                weight = weight,
                gender = gender,
                status = status,
                description = description,
                contactInfo = contactInfo,
                location = location,
                latitude = latitude,
                longitude = longitude,
                reward = reward,
                caseType = caseType,
                resolvedStatus = resolvedStatus,
                isResolved = isResolved,
                eventDate = eventDate,
                eventLocation = eventLocation,
                currentCareStatus = currentCareStatus,
                identifyingMarks = identifyingMarks,
                temperament = temperament,
                healthCondition = healthCondition,
                hasCollar = hasCollar,
                contactPreference = contactPreference,
                imageUrls = imageUrls
            )

            println("📤 Creating post with location: $location, lat: $latitude, lon: $longitude")
            val response = apiService.createPost(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    val createdPost = body.data
                    if (createdPost != null) {
                        println("✅ Post created successfully with ID: ${createdPost.postId}")
                        Result.success(createdPost)
                    } else {
                        Result.failure(Exception("Post created but no data returned"))
                    }
                } else {
                    val errorMsg = body?.message ?: "Failed to create post"
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception(errorBody ?: "Failed to create post"))
            }
        } catch (e: Exception) {
            println("❌ Exception during post creation: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String, firebaseUid: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                println("🗑️ Attempting to delete post: $postId by user: $firebaseUid")
                val response = apiService.deletePost(postId, firebaseUid)
                println("📥 Delete response code: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        println("✅ Post deleted successfully")
                        Result.success(Unit)
                    } else {
                        val errorMsg = body?.message ?: "Failed to delete post"
                        println("❌ Delete failed: $errorMsg")
                        Result.failure(Exception(errorMsg))
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    println("❌ HTTP error: ${response.message()}, body: $errorBody")

                    if (errorBody?.contains("not found") == true) {
                        println("⚠️ Post not found, considering it already deleted")
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception(errorBody ?: "Failed to delete post"))
                    }
                }
            } catch (e: Exception) {
                println("❌ Exception during delete: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun resolvePost(postId: String, firebaseUid: String): Result<ApiPost> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.resolvePost(postId, mapOf("firebaseUid" to firebaseUid))

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        Result.success(body.data)
                    } else {
                        Result.failure(Exception(body?.message ?: "Failed to resolve post"))
                    }
                } else {
                    Result.failure(Exception(response.errorBody()?.string() ?: "Failed to resolve post"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun likePost(postId: String, firebaseUid: String): Result<LikeResponse> = withContext(Dispatchers.IO) {
        try {
            println("📤 Like post request - postId: $postId, user: $firebaseUid")
            val response = apiService.likePost(postId, mapOf("firebaseUid" to firebaseUid))

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Like post successful - liked: ${body.liked}, count: ${body.likesCount}")
                    Result.success(body)
                } else {
                    println("❌ Like post failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to like post"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "HTTP error ${response.code()}"
                println("❌ Like post HTTP error: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            println("❌ Like post exception: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updatePost(
        postId: String,
        updates: Map<String, Any>,
        firebaseUid: String
    ): Result<ApiPost> = withContext(Dispatchers.IO) {
        try {
            println("📝 Updating post: $postId")
            println("📝 Updates: $updates")

            val response = apiService.updatePost(postId, updates)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    println("✅ Post updated successfully")
                    Result.success(body.data)
                } else {
                    println("❌ Update failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to update post"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ HTTP error ${response.code()}: $errorBody")
                Result.failure(Exception(errorBody ?: "HTTP error ${response.code()}"))
            }
        } catch (e: Exception) {
            println("❌ Exception: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun checkLikeStatus(postId: String, firebaseUid: String): Result<LikeResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.checkPostLikeStatus(postId, firebaseUid)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        Result.success(body)
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

    /**
     * Add post to favorites
     */
    suspend fun createFavorite(postId: String, firebaseUid: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.addToFavorites(mapOf(
                    "postId" to postId,
                    "firebaseUid" to firebaseUid
                ))

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.errorBody()?.string() ?: "Failed to save favorite"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Report a post
     */
    suspend fun reportPost(postId: String, firebaseUid: String, reason: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val reportRequest = ReportRequest(
                    reporterUid = firebaseUid,
                    postId = postId,
                    reason = reason
                )
                val response = apiService.createReport(reportRequest)

                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.errorBody()?.string() ?: "Failed to report post"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
