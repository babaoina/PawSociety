package com.example.pawsociety.data.repository

import android.content.Context
import android.util.Log
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.offline.data.AppDatabase
import com.example.pawsociety.offline.data.entity.PostEntity
import com.example.pawsociety.util.NetworkUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class OfflinePostRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val postDao = database.postDao()
    private val postRepository = PostRepository()
    private val tag = "OfflinePostRepository"

    fun getPostsFlow(): Flow<List<ApiPost>> {
        return postDao.getAllPosts().map { entities ->
            entities.map { it.toApiPost() }
        }
    }

    /**
     * Update a single post in the offline cache
     */
    /**
     * Update a single post in the offline cache
     */
    suspend fun updatePostInCache(postEntity: PostEntity) = withContext(Dispatchers.IO) {
        try {
            postDao.insertPost(postEntity) // OnConflictStrategy.REPLACE will update existing
            Log.d(tag, "✅ Updated post in cache: ${postEntity.postId} with likesCount: ${postEntity.likesCount}")

            // Force refresh the flow to update UI
            val updatedList = postDao.getAllPosts().first()
            Log.d(tag, "📊 Cache now has ${updatedList.size} posts")
        } catch (e: Exception) {
            Log.e(tag, "❌ Error updating post in cache: ${e.message}")
        }
    }

    suspend fun loadPosts(forceRefresh: Boolean = false): Result<List<ApiPost>> {
        return withContext(Dispatchers.IO) {
            try {
                val isNetworkAvailable = NetworkUtils.isNetworkAvailable(context)
                Log.d(tag, "Network available: $isNetworkAvailable, forceRefresh: $forceRefresh")

                // Try network first if available
                if (isNetworkAvailable) {
                    Log.d(tag, "Attempting to load from network...")
                    val result = postRepository.getPosts(limit = 100)

                    if (result.isSuccess) {
                        val posts = result.getOrNull() ?: emptyList()
                        Log.d(tag, "Network success: loaded ${posts.size} posts")

                        // Save to database
                        val entities = posts.map { PostEntity.fromApiPost(it) }
                        postDao.insertAllPosts(entities)
                        updateLastRefreshTime()

                        return@withContext Result.success(posts)
                    } else {
                        Log.e(tag, "Network failed: ${result.exceptionOrNull()?.message}")
                    }
                }

                // Fall back to cache
                Log.d(tag, "Falling back to cache...")
                val cachedPosts = postDao.getAllPosts().first()
                if (cachedPosts.isNotEmpty()) {
                    Log.d(tag, "Cache success: loaded ${cachedPosts.size} posts")
                    val apiPosts = cachedPosts.map { it.toApiPost() }
                    Result.success(apiPosts)
                } else {
                    Log.e(tag, "Cache empty and network failed")
                    Result.failure(Exception("No internet connection and no cached data"))
                }

            } catch (e: Exception) {
                Log.e(tag, "Error loading posts: ${e.message}")
                e.printStackTrace()

                // Try cache on error
                try {
                    val cachedPosts = postDao.getAllPosts().first()
                    if (cachedPosts.isNotEmpty()) {
                        val apiPosts = cachedPosts.map { it.toApiPost() }
                        Result.success(apiPosts)
                    } else {
                        Result.failure(e)
                    }
                } catch (cacheException: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun getPost(postId: String): Result<ApiPost> {
        return withContext(Dispatchers.IO) {
            try {
                // Try cache first
                val cachedPost = postDao.getPostById(postId)

                if (cachedPost != null) {
                    Log.d(tag, "Post found in cache: $postId")
                    Result.success(cachedPost.toApiPost())
                } else {
                    // Try network
                    if (NetworkUtils.isNetworkAvailable(context)) {
                        Log.d(tag, "Post not in cache, trying network: $postId")
                        val result = postRepository.getPost(postId)
                        if (result.isSuccess) {
                            val post = result.getOrNull()!!
                            postDao.insertPost(PostEntity.fromApiPost(post))
                            Result.success(post)
                        } else {
                            result
                        }
                    } else {
                        Result.failure(Exception("Post not found in cache and no internet connection"))
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error getting post: ${e.message}")
                Result.failure(e)
            }
        }
    }

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
    ): Result<ApiPost> {
        return withContext(Dispatchers.IO) {
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    return@withContext Result.failure(Exception("No internet connection. Please try again when online."))
                }

                val result = postRepository.createPost(
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

                if (result.isSuccess) {
                    val post = result.getOrNull()!!
                    Log.d(tag, "Post created, caching: ${post.postId}")
                    postDao.insertPost(PostEntity.fromApiPost(post))
                }

                result
            } catch (e: Exception) {
                Log.e(tag, "Error creating post: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun deletePost(postId: String, firebaseUid: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (NetworkUtils.isNetworkAvailable(context)) {
                    val result = postRepository.deletePost(postId, firebaseUid)
                    if (result.isSuccess) {
                        Log.d(tag, "Post deleted from network and cache: $postId")
                        postDao.deletePost(postId)
                    }
                    result
                } else {
                    Log.d(tag, "Offline delete from cache only: $postId")
                    postDao.deletePost(postId)
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error deleting post: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            Log.d(tag, "Clearing all cache")
            postDao.deleteAllPosts()
        }
    }

    private suspend fun shouldRefreshFromNetwork(): Boolean {
        val prefs = context.getSharedPreferences("pawsociety_prefs", Context.MODE_PRIVATE)
        val lastRefresh = prefs.getLong("last_posts_refresh", 0)
        val currentTime = System.currentTimeMillis()
        return currentTime - lastRefresh > 5 * 60 * 1000
    }

    private fun updateLastRefreshTime() {
        val prefs = context.getSharedPreferences("pawsociety_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_posts_refresh", System.currentTimeMillis()).apply()
    }
}