package com.example.pawsociety.data.repository

import com.example.pawsociety.api.UploadApi
import com.example.pawsociety.api.UploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class UploadRepository {
    
    private val uploadService: UploadApi = com.example.pawsociety.api.ApiClient.uploadService
    
    /**
     * Upload post images (multiple, max 5)
     */
    suspend fun uploadPostImages(images: List<File>): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val imageParts = images.map { file ->
                val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("images", file.name, requestBody)
            }
            
            val response = uploadService.uploadPostImages(imageParts)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.imageUrls != null) {
                    Result.success(body.imageUrls)
                } else {
                    Result.failure(Exception(body?.message ?: "Upload failed"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Upload pet image (single)
     */
    suspend fun uploadPetImage(image: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = image.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", image.name, requestBody)
            
            val response = uploadService.uploadPetImage(imagePart)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.imageUrl != null) {
                    Result.success(body.imageUrl)
                } else {
                    Result.failure(Exception(body?.message ?: "Upload failed"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Upload profile picture (single)
     */
    suspend fun uploadProfilePicture(image: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = image.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", image.name, requestBody)
            
            val response = uploadService.uploadProfilePicture(imagePart)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.imageUrl != null) {
                    Result.success(body.imageUrl)
                } else {
                    Result.failure(Exception(body?.message ?: "Upload failed"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete uploaded file
     */
    suspend fun deleteFile(type: String, filename: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = uploadService.deleteFile(type, filename)
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Delete failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
