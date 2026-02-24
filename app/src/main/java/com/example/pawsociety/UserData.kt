package com.example.pawsociety

import android.content.Context
import android.content.SharedPreferences
// import at.favre.lib.crypto.bcrypt.BCrypt // No longer needed - using Firebase Auth
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object UserDatabase {
    private const val PREFS_NAME = "pawsociety_users"
    private const val USERS_KEY = "users_list"
    private const val CURRENT_USER_KEY = "current_user"
    private const val POSTS_KEY = "posts_list"
    private const val NOTIFICATIONS_KEY = "notifications_list"
    private const val MESSAGES_KEY = "messages_list"

    // ==================== USER FUNCTIONS ====================

    // Convert AppUser to JSON
    private fun userToJson(user: AppUser): String {
        return JSONObject().apply {
            put("uid", user.uid)
            put("fullName", user.fullName)
            put("username", user.username)
            put("email", user.email)
            put("phone", user.phone)
            put("location", user.location)
            put("bio", user.bio)
            put("profileImageUrl", user.profileImageUrl)
            put("createdAt", user.createdAt)
        }.toString()
    }

    // Convert JSON to AppUser
    private fun jsonToUser(json: String): AppUser {
        val obj = JSONObject(json)
        return AppUser(
            uid = obj.optString("uid", ""),
            fullName = obj.getString("fullName"),
            username = obj.getString("username"),
            email = obj.getString("email"),
            phone = obj.optString("phone", ""),
            location = obj.optString("location", ""),
            bio = obj.optString("bio", ""),
            profileImageUrl = obj.optString("profileImageUrl", ""),
            createdAt = obj.optString("createdAt", "")
        )
    }

    // ==================== POST FUNCTIONS ====================

    // Convert Post to JSON
    private fun postToJson(post: Post): String {
        return JSONObject().apply {
            put("postId", post.postId)
            put("userId", post.userId)
            put("userName", post.userName)
            put("userImageUrl", post.userImageUrl)
            put("petName", post.petName)
            put("petType", post.petType)
            put("status", post.status)
            put("description", post.description)
            put("location", post.location)
            put("reward", post.reward)
            put("contactInfo", post.contactInfo)

            // Convert imageUrls List to JSONArray
            val imageUrlsArray = JSONArray()
            post.imageUrls.forEach { imageUrlsArray.put(it) }
            put("imageUrls", imageUrlsArray)

            // Convert likes List to JSONArray
            val likesArray = JSONArray()
            post.likes.forEach { likesArray.put(it) }
            put("likes", likesArray)

            // Convert comments List to JSONArray
            val commentsArray = JSONArray()
            post.comments.forEach { comment ->
                val commentObj = JSONObject().apply {
                    put("commentId", comment.commentId)
                    put("postId", comment.postId)
                    put("userId", comment.userId)
                    put("userName", comment.userName)
                    put("userImageUrl", comment.userImageUrl)
                    put("text", comment.text)

                    // Convert comment likes to JSONArray
                    val commentLikesArray = JSONArray()
                    comment.likes.forEach { commentLikesArray.put(it) }
                    put("likes", commentLikesArray)

                    put("createdAt", comment.createdAt)
                }
                commentsArray.put(commentObj)
            }
            put("comments", commentsArray)

            put("shares", post.shares)
            put("createdAt", post.createdAt)
        }.toString()
    }

    // Convert JSON to Post
    fun jsonToPost(json: String): Post {
        val obj = JSONObject(json)

        // Convert imageUrls JSONArray to List
        val imageUrlsList = mutableListOf<String>()
        val imageUrlsArray = obj.optJSONArray("imageUrls")
        if (imageUrlsArray != null) {
            for (i in 0 until imageUrlsArray.length()) {
                imageUrlsList.add(imageUrlsArray.getString(i))
            }
        }

        // Convert likes JSONArray to List
        val likesList = mutableListOf<String>()
        val likesArray = obj.optJSONArray("likes")
        if (likesArray != null) {
            for (i in 0 until likesArray.length()) {
                likesList.add(likesArray.getString(i))
            }
        }

        // Convert comments JSONArray to List
        val commentsList = mutableListOf<Comment>()
        val commentsArray = obj.optJSONArray("comments")
        if (commentsArray != null) {
            for (i in 0 until commentsArray.length()) {
                val commentObj = commentsArray.getJSONObject(i)

                // Convert comment likes to List
                val commentLikesList = mutableListOf<String>()
                val commentLikesArray = commentObj.optJSONArray("likes")
                if (commentLikesArray != null) {
                    for (j in 0 until commentLikesArray.length()) {
                        commentLikesList.add(commentLikesArray.getString(j))
                    }
                }

                val comment = Comment(
                    commentId = commentObj.optString("commentId", ""),
                    postId = commentObj.optString("postId", ""),
                    userId = commentObj.optString("userId", ""),
                    userName = commentObj.optString("userName", ""),
                    userImageUrl = commentObj.optString("userImageUrl", ""),
                    text = commentObj.optString("text", ""),
                    likes = commentLikesList,
                    createdAt = commentObj.optString("createdAt", "")
                )
                commentsList.add(comment)
            }
        }

        return Post(
            postId = obj.optString("postId", ""),
            userId = obj.optString("userId", ""),
            userName = obj.optString("userName", ""),
            userImageUrl = obj.optString("userImageUrl", ""),
            petName = obj.optString("petName", ""),
            petType = obj.optString("petType", ""),
            status = obj.optString("status", ""),
            description = obj.optString("description", ""),
            location = obj.optString("location", ""),
            reward = obj.optString("reward", ""),
            contactInfo = obj.optString("contactInfo", ""),
            imageUrls = imageUrlsList,
            likes = likesList,
            comments = commentsList,
            shares = obj.optInt("shares", 0),
            createdAt = obj.optString("createdAt", "")
        )
    }

    // ==================== DATABASE OPERATIONS ====================

    // Get all users
    fun getAllUsers(context: Context): MutableList<AppUser> {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val usersJson = sharedPref.getString(USERS_KEY, "[]")
        val usersArray = JSONArray(usersJson)
        val users = mutableListOf<AppUser>()

        for (i in 0 until usersArray.length()) {
            users.add(jsonToUser(usersArray.getString(i)))
        }

        return users
    }

    // Save all users
    private fun saveAllUsers(context: Context, users: List<AppUser>) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val usersArray = JSONArray()

        users.forEach { user ->
            usersArray.put(userToJson(user))
        }

        editor.putString(USERS_KEY, usersArray.toString())
        editor.apply()
    }

    // Get all posts
    fun getAllPosts(context: Context): List<Post> {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val postsJson = sharedPref.getString(POSTS_KEY, "[]")
        val postsArray = JSONArray(postsJson)
        val posts = mutableListOf<Post>()

        for (i in 0 until postsArray.length()) {
            posts.add(jsonToPost(postsArray.getString(i)))
        }

        return posts.sortedByDescending { it.createdAt }
    }

    // Save a post
    fun savePost(context: Context, post: Post): Boolean {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        val postsJson = sharedPref.getString(POSTS_KEY, "[]")
        val postsArray = JSONArray(postsJson)

        postsArray.put(postToJson(post))

        editor.putString(POSTS_KEY, postsArray.toString())
        editor.apply()

        return true
    }

    // Like a post
    fun likePost(context: Context, postId: String, userId: String): Boolean {
        val allPosts = getAllPosts(context).toMutableList()
        val postIndex = allPosts.indexOfFirst { it.postId == postId }

        if (postIndex == -1) return false

        val post = allPosts[postIndex]
        val updatedLikes = post.likes.toMutableList()

        if (updatedLikes.contains(userId)) {
            updatedLikes.remove(userId)
        } else {
            updatedLikes.add(userId)
        }

        val updatedPost = post.copy(likes = updatedLikes)
        allPosts[postIndex] = updatedPost

        saveAllPosts(context, allPosts)
        return true
    }

    // Save all posts
    private fun saveAllPosts(context: Context, posts: List<Post>) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val postsArray = JSONArray()

        posts.forEach { post ->
            postsArray.put(postToJson(post))
        }

        editor.putString(POSTS_KEY, postsArray.toString())
        editor.apply()
    }

    // ==================== COMMENT FUNCTIONS ====================

    // Add a comment to a post
    fun addComment(context: Context, postId: String, userId: String, userName: String, text: String): Boolean {
        val allPosts = getAllPosts(context).toMutableList()
        val postIndex = allPosts.indexOfFirst { it.postId == postId }

        if (postIndex == -1) return false

        val post = allPosts[postIndex]
        val currentComments = post.comments.toMutableList()

        val newComment = Comment(
            commentId = "comment_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 4)}",
            postId = postId,
            userId = userId,
            userName = userName,
            text = text,
            createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        currentComments.add(newComment)
        val updatedPost = post.copy(comments = currentComments)
        allPosts[postIndex] = updatedPost

        saveAllPosts(context, allPosts)
        return true
    }

    // Get comments for a post
    fun getCommentsForPost(context: Context, postId: String): List<Comment> {
        val allPosts = getAllPosts(context)
        val post = allPosts.find { it.postId == postId }
        return post?.comments ?: emptyList()
    }

    // Like a comment
    fun likeComment(context: Context, postId: String, commentId: String, userId: String): Boolean {
        val allPosts = getAllPosts(context).toMutableList()
        val postIndex = allPosts.indexOfFirst { it.postId == postId }

        if (postIndex == -1) return false

        val post = allPosts[postIndex]
        val comments = post.comments.toMutableList()
        val commentIndex = comments.indexOfFirst { it.commentId == commentId }

        if (commentIndex == -1) return false

        val comment = comments[commentIndex]
        val likes = comment.likes.toMutableList()

        if (likes.contains(userId)) {
            likes.remove(userId)
        } else {
            likes.add(userId)
        }

        val updatedComment = comment.copy(likes = likes)
        comments[commentIndex] = updatedComment
        val updatedPost = post.copy(comments = comments)
        allPosts[postIndex] = updatedPost

        saveAllPosts(context, allPosts)
        return true
    }

    // ==================== FAVORITES FUNCTIONS ====================

    fun saveToFavorites(context: Context, userId: String, postId: String): Boolean {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        val favoritesJson = sharedPref.getString("favorites_$userId", "[]")
        val favoritesArray = JSONArray(favoritesJson)

        for (i in 0 until favoritesArray.length()) {
            if (favoritesArray.getString(i) == postId) {
                return false
            }
        }

        favoritesArray.put(postId)
        editor.putString("favorites_$userId", favoritesArray.toString())
        editor.apply()
        return true
    }

    fun removeFromFavorites(context: Context, userId: String, postId: String): Boolean {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        val favoritesJson = sharedPref.getString("favorites_$userId", "[]")
        val favoritesArray = JSONArray(favoritesJson)
        val newArray = JSONArray()

        for (i in 0 until favoritesArray.length()) {
            if (favoritesArray.getString(i) != postId) {
                newArray.put(favoritesArray.getString(i))
            }
        }

        editor.putString("favorites_$userId", newArray.toString())
        editor.apply()
        return true
    }

    fun getUserFavorites(context: Context, userId: String): List<String> {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val favoritesJson = sharedPref.getString("favorites_$userId", "[]")
        val favoritesArray = JSONArray(favoritesJson)
        val favorites = mutableListOf<String>()

        for (i in 0 until favoritesArray.length()) {
            favorites.add(favoritesArray.getString(i))
        }

        return favorites
    }

    fun isFavorite(context: Context, userId: String, postId: String): Boolean {
        val favorites = getUserFavorites(context, userId)
        return favorites.contains(postId)
    }

    fun deletePost(context: Context, postId: String): Boolean {
        val allPosts = getAllPosts(context).toMutableList()
        val filteredPosts = allPosts.filter { it.postId != postId }

        if (filteredPosts.size == allPosts.size) {
            return false
        }

        saveAllPosts(context, filteredPosts)
        return true
    }

    fun getFavoritePosts(context: Context, userId: String): List<Post> {
        val favoriteIds = getUserFavorites(context, userId)
        val allPosts = getAllPosts(context)
        return allPosts.filter { it.postId in favoriteIds }
    }

    // ==================== USER MANAGEMENT ====================

    fun registerUser(context: Context, user: AppUser, password: String): Boolean {
        val users = getAllUsers(context)

        if (users.any { it.email.equals(user.email, ignoreCase = true) }) {
            return false
        }

        if (users.any { it.username.equals(user.username, ignoreCase = true) }) {
            return false
        }

        // Using simple password storage (not secure - for development only)
        // In production, use Firebase Auth only
        val hashedPassword = password // BCrypt.withDefaults().hashToString(12, password.toCharArray())

        val userWithUid = user.copy(
            uid = "user_${System.currentTimeMillis()}_${users.size + 1}",
            createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        users.add(userWithUid)
        saveAllUsers(context, users)

        val passwordPref = context.getSharedPreferences("user_passwords", Context.MODE_PRIVATE)
        passwordPref.edit().putString(userWithUid.uid, hashedPassword).apply()

        return true
    }

    fun loginUser(context: Context, email: String, password: String): AppUser? {
        val users = getAllUsers(context)

        val user = users.find { it.email.equals(email, ignoreCase = true) }

        if (user == null) return null

        val passwordPref = context.getSharedPreferences("user_passwords", Context.MODE_PRIVATE)
        val storedHashedPassword = passwordPref.getString(user.uid, null)

        // Simple password comparison (not secure - for development only)
        return if (storedHashedPassword != null && storedHashedPassword == password) {
            val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPref.edit().putString(CURRENT_USER_KEY, userToJson(user)).apply()
            user
        } else {
            null
        }
    }

    fun getCurrentUser(context: Context): AppUser? {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userJson = sharedPref.getString(CURRENT_USER_KEY, null)

        return if (userJson != null) {
            jsonToUser(userJson)
        } else {
            null
        }
    }

    fun logout(context: Context) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().remove(CURRENT_USER_KEY).apply()
    }

    fun checkEmailExists(context: Context, email: String): Boolean {
        val users = getAllUsers(context)
        return users.any { it.email.equals(email, ignoreCase = true) }
    }

    fun getUserById(context: Context, userId: String): AppUser? {
        val users = getAllUsers(context)
        return users.find { it.uid == userId }
    }

    fun updateUser(context: Context, updatedUser: AppUser): Boolean {
        val users = getAllUsers(context)
        val index = users.indexOfFirst { it.uid == updatedUser.uid }

        if (index == -1) return false

        users[index] = updatedUser
        saveAllUsers(context, users)

        val currentUser = getCurrentUser(context)
        if (currentUser?.uid == updatedUser.uid) {
            val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPref.edit().putString(CURRENT_USER_KEY, userToJson(updatedUser)).apply()
        }

        return true
    }

    fun isUsernameTaken(context: Context, username: String, excludeUserId: String? = null): Boolean {
        val users = getAllUsers(context)
        return if (excludeUserId != null) {
            users.any { it.username.equals(username, ignoreCase = true) && it.uid != excludeUserId }
        } else {
            users.any { it.username.equals(username, ignoreCase = true) }
        }
    }

    fun getOtherUsers(context: Context): List<AppUser> {
        val currentUser = getCurrentUser(context)
        val allUsers = getAllUsers(context)
        return if (currentUser != null) {
            allUsers.filter { it.uid != currentUser.uid }
        } else {
            allUsers
        }
    }

    // ==================== HIGHLIGHT FUNCTIONS ====================

    fun saveUserHighlights(context: Context, userId: String, highlights: List<ProfileActivity.Highlight>): Boolean {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        val highlightsArray = JSONArray()
        highlights.forEach { highlight ->
            val highlightObj = JSONObject().apply {
                put("name", highlight.name)
                put("emoji", highlight.emoji)
                put("color", highlight.color)
            }
            highlightsArray.put(highlightObj)
        }

        editor.putString("highlights_$userId", highlightsArray.toString())
        editor.apply()
        return true
    }

    fun getUserHighlights(context: Context, userId: String): MutableList<ProfileActivity.Highlight> {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val highlightsJson = sharedPref.getString("highlights_$userId", "[]")
        val highlightsArray = JSONArray(highlightsJson)
        val highlights = mutableListOf<ProfileActivity.Highlight>()

        for (i in 0 until highlightsArray.length()) {
            val obj = highlightsArray.getJSONObject(i)
            val highlight = ProfileActivity.Highlight(
                name = obj.getString("name"),
                emoji = obj.getString("emoji"),
                color = obj.getString("color")
            )
            highlights.add(highlight)
        }

        return highlights
    }

    // ==================== NOTIFICATIONS ====================

    fun getNotifications(context: Context, userId: String): List<Notification> {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notificationsJson = sharedPref.getString(NOTIFICATIONS_KEY, "[]")
        val notificationsArray = JSONArray(notificationsJson)
        val notifications = mutableListOf<Notification>()

        for (i in 0 until notificationsArray.length()) {
            // You'll need to implement jsonToNotification
        }

        return notifications.filter { it.userId == userId }
    }

    // ==================== CLEAR DATA (for testing) ====================

    fun clearAllData(context: Context) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.clear()
        editor.apply()

        val passwordPref = context.getSharedPreferences("user_passwords", Context.MODE_PRIVATE)
        passwordPref.edit().clear().apply()
    }
}