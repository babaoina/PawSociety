package com.example.pawsociety.util

import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.BlockRepository
import com.example.pawsociety.data.repository.HidePostRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Utility class for filtering posts based on user's hidden posts and blocked users
 */
object PostFilteringUtil {

    /**
     * Filter a list of posts by removing:
     * 1. Posts the user has hidden
     * 2. Posts from users the user has blocked
     */
    suspend fun filterPosts(
        posts: List<ApiPost>,
        currentUserUid: String
    ): List<ApiPost> = coroutineScope {
        try {
            // Fetch hidden post IDs and blocked user IDs in parallel
            val hiddenPostsDeferred = async {
                HidePostRepository().getHiddenPostIds(currentUserUid).getOrNull() ?: emptySet()
            }
            val blockedUsersDeferred = async {
                BlockRepository().getBlockedUserIds(currentUserUid).getOrNull() ?: emptySet()
            }

            val hiddenPostIds = hiddenPostsDeferred.await()
            val blockedUserIds = blockedUsersDeferred.await()

            // Filter posts
            posts.filter { post ->
                // Keep post if it's not hidden AND from a non-blocked user
                post.postId !in hiddenPostIds && post.firebaseUid !in blockedUserIds
            }
        } catch (e: Exception) {
            println("⚠️ Error filtering posts: ${e.message}")
            // If filtering fails, return posts unfiltered
            posts
        }
    }

    /**
     * Quick check if a single post should be shown
     */
    suspend fun shouldShowPost(
        post: ApiPost,
        hiddenPostIds: Set<String>,
        blockedUserIds: Set<String>
    ): Boolean {
        return post.postId !in hiddenPostIds && post.firebaseUid !in blockedUserIds
    }

    /**
     * Get filtered posts optimized for large lists (caches the filters)
     */
    suspend fun getFilteredPostsOptimized(
        posts: List<ApiPost>,
        currentUserUid: String,
        cacheKey: String = "default"
    ): Pair<List<ApiPost>, Pair<Set<String>, Set<String>>> = coroutineScope {
        try {
            val hiddenPostsDeferred = async {
                HidePostRepository().getHiddenPostIds(currentUserUid).getOrNull() ?: emptySet()
            }
            val blockedUsersDeferred = async {
                BlockRepository().getBlockedUserIds(currentUserUid).getOrNull() ?: emptySet()
            }

            val hiddenPostIds = hiddenPostsDeferred.await()
            val blockedUserIds = blockedUsersDeferred.await()

            val filtered = posts.filter { post ->
                post.postId !in hiddenPostIds && post.firebaseUid !in blockedUserIds
            }

            Pair(filtered, Pair(hiddenPostIds, blockedUserIds))
        } catch (e: Exception) {
            println("⚠️ Error in optimized filtering: ${e.message}")
            Pair(posts, Pair(emptySet(), emptySet()))
        }
    }
}
