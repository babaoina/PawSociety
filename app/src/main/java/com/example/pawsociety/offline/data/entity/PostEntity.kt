package com.example.pawsociety.offline.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pawsociety.api.ApiPost

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey
    val postId: String,
    val firebaseUid: String,
    val userName: String,
    val userImageUrl: String?,
    val petName: String,
    val petType: String,
    val status: String,
    val description: String,
    val location: String?,
    val reward: String?,
    val contactInfo: String,
    val imageUrls: List<String>,
    val likesCount: Int,
    val commentsCount: Int,
    val shares: Int,
    val createdAt: String,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toApiPost(): ApiPost {
        return ApiPost(
            postId = postId,
            firebaseUid = firebaseUid,
            userName = userName,
            userImageUrl = userImageUrl,
            petName = petName,
            petType = petType,
            status = status,
            description = description,
            location = location,
            reward = reward,
            contactInfo = contactInfo,
            imageUrls = imageUrls,
            likesCount = likesCount,
            commentsCount = commentsCount,
            shares = shares,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromApiPost(post: ApiPost): PostEntity {
            return PostEntity(
                postId = post.postId,
                firebaseUid = post.firebaseUid,
                userName = post.userName,
                userImageUrl = post.userImageUrl,
                petName = post.petName,
                petType = post.petType,
                status = post.status,
                description = post.description,
                location = post.location,
                reward = post.reward,
                contactInfo = post.contactInfo,
                imageUrls = post.imageUrls ?: emptyList(),
                likesCount = post.likesCount,
                commentsCount = post.commentsCount,
                shares = post.shares,
                createdAt = post.createdAt
            )
        }
    }
}