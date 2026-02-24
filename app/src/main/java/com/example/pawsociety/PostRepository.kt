package com.example.pawsociety

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository(private val context: Context) {

    // Get all posts
    suspend fun getAllPosts(): List<Post> {
        return UserDatabase.getAllPosts(context)
    }

    // Save a post
    suspend fun savePost(post: Post): Boolean = withContext(Dispatchers.IO) {
        UserDatabase.savePost(context, post)
    }

    // Delete a post
    suspend fun deletePost(postId: String): Boolean = withContext(Dispatchers.IO) {
        UserDatabase.deletePost(context, postId)
    }

    // Save to favorites
    suspend fun saveToFavorites(userId: String, postId: String): Boolean = withContext(Dispatchers.IO) {
        UserDatabase.saveToFavorites(context, userId, postId)
    }

    // Remove from favorites
    suspend fun removeFromFavorites(userId: String, postId: String): Boolean = withContext(Dispatchers.IO) {
        UserDatabase.removeFromFavorites(context, userId, postId)
    }

    // Check if post is favorite
    suspend fun isFavorite(userId: String, postId: String): Boolean {
        return UserDatabase.isFavorite(context, userId, postId)
    }

    // Add comment
    suspend fun addComment(postId: String, userId: String, userName: String, text: String): Boolean = withContext(Dispatchers.IO) {
        UserDatabase.addComment(context, postId, userId, userName, text)
    }

    // Get comments for post
    suspend fun getComments(postId: String): List<Comment> {
        return UserDatabase.getCommentsForPost(context, postId)
    }
}