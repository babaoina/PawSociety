package com.example.pawsociety.offline.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pawsociety.api.ApiUser

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val firebaseUid: String,
    val username: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val profileImageUrl: String?,
    val bio: String?,
    val location: String?,
    val createdAt: String?,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toApiUser(): ApiUser {
        return ApiUser(
            firebaseUid = firebaseUid,
            username = username,
            email = email,
            fullName = fullName,
            phone = phone,
            profileImageUrl = profileImageUrl,
            bio = bio,
            location = location,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromApiUser(user: ApiUser): UserEntity {
            return UserEntity(
                firebaseUid = user.firebaseUid,
                username = user.username,
                email = user.email,
                fullName = user.fullName,
                phone = user.phone,
                profileImageUrl = user.profileImageUrl,
                bio = user.bio,
                location = user.location,
                createdAt = user.createdAt
            )
        }
    }
}