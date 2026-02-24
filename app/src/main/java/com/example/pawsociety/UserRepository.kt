package com.example.pawsociety

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(private val context: Context) {

    // Get current user
    suspend fun getCurrentUser(): AppUser? {
        return UserDatabase.getCurrentUser(context)
    }

    // Login
    suspend fun login(email: String, password: String): AppUser? = withContext(Dispatchers.IO) {
        UserDatabase.loginUser(context, email, password)
    }

    // Register
    suspend fun register(user: AppUser, password: String): Boolean = withContext(Dispatchers.IO) {
        UserDatabase.registerUser(context, user, password)
    }

    // Update user
    suspend fun updateUser(user: AppUser): Boolean = withContext(Dispatchers.IO) {
        UserDatabase.updateUser(context, user)
    }

    // Logout
    fun logout() {
        UserDatabase.logout(context)
    }

    // Get other users (for inbox)
    suspend fun getOtherUsers(): List<AppUser> {
        return UserDatabase.getOtherUsers(context)
    }
}