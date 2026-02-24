package com.example.pawsociety.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface UploadApi {
    
    /**
     * Upload post images (multiple, max 5)
     * POST /api/upload/post
     */
    @Multipart
    @POST("upload/post")
    suspend fun uploadPostImages(
        @Part images: List<MultipartBody.Part>
    ): Response<UploadResponse>
    
    /**
     * Upload pet image (single)
     * POST /api/upload/pet
     */
    @Multipart
    @POST("upload/pet")
    suspend fun uploadPetImage(
        @Part image: MultipartBody.Part
    ): Response<UploadResponse>
    
    /**
     * Upload profile picture (single)
     * POST /api/upload/profile
     */
    @Multipart
    @POST("upload/profile")
    suspend fun uploadProfilePicture(
        @Part image: MultipartBody.Part
    ): Response<UploadResponse>
    
    /**
     * Delete uploaded file
     * DELETE /api/upload/:type/:filename
     */
    @DELETE("upload/{type}/{filename}")
    suspend fun deleteFile(
        @Path("type") type: String,
        @Path("filename") filename: String
    ): Response<ApiResponse<Unit>>
}

data class UploadResponse(
    val success: Boolean,
    val message: String? = null,
    val count: Int? = null,
    val imageUrls: List<String>? = null,
    val imageUrl: String? = null
)
