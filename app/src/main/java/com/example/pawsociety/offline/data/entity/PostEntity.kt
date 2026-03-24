package com.example.pawsociety.offline.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pawsociety.api.ApiPost
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// In PostEntity.kt, add category field

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey
    val postId: String,
    val firebaseUid: String,
    val userName: String,
    val userImageUrl: String?,
    val petName: String,
    val petType: String,
    val category: String? = null,  // 🔥 ADD THIS
    val age: String? = "",
    val weight: String? = "",
    val gender: String? = "Unknown",
    val status: String,
    val description: String,
    val location: String?,
    val reward: String?,
    val caseType: String? = null,
    val resolvedStatus: String? = "",
    val isResolved: Boolean = false,
    val eventDate: String? = "",
    val eventLocation: String? = "",
    val currentCareStatus: String? = "",
    val identifyingMarks: String? = "",
    val temperament: String? = "",
    val healthCondition: String? = "",
    val hasCollar: Boolean = false,
    val contactPreference: String? = "call",
    val contactInfo: String,
    val imageUrls: String,  // Store as JSON string
    val likesCount: Int,
    val createdAt: String,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toApiPost(): ApiPost {
        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type
        val imageList: List<String> = try {
            gson.fromJson(imageUrls, type)
        } catch (e: Exception) {
            emptyList()
        }

        return ApiPost(
            postId = postId,
            firebaseUid = firebaseUid,
            userName = userName,
            userImageUrl = userImageUrl,
            petName = petName,
            petType = petType,
            category = category,  // 🔥 ADD THIS
            age = age,
            weight = weight,
            gender = gender,
            status = status,
            description = description,
            location = location,
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
            contactInfo = contactInfo,
            imageUrls = imageList,
            likesCount = likesCount,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromApiPost(post: ApiPost): PostEntity {
            val gson = Gson()
            val imageUrlsJson = gson.toJson(post.imageUrls ?: emptyList<String>())

            return PostEntity(
                postId = post.postId,
                firebaseUid = post.firebaseUid,
                userName = post.userName,
                userImageUrl = post.userImageUrl,
                petName = post.petName,
                petType = post.petType,
                category = post.category,  // 🔥 ADD THIS
                age = post.age ?: "",
                weight = post.weight ?: "",
                gender = post.gender ?: "Unknown",
                status = post.status,
                description = post.description,
                location = post.location,
                reward = post.reward,
                caseType = post.caseType,
                resolvedStatus = post.resolvedStatus,
                isResolved = post.isResolved,
                eventDate = post.eventDate,
                eventLocation = post.eventLocation,
                currentCareStatus = post.currentCareStatus,
                identifyingMarks = post.identifyingMarks,
                temperament = post.temperament,
                healthCondition = post.healthCondition,
                hasCollar = post.hasCollar,
                contactPreference = post.contactPreference,
                contactInfo = post.contactInfo,
                imageUrls = imageUrlsJson,
                likesCount = post.likesCount,
                createdAt = post.createdAt
            )
        }
    }
}
