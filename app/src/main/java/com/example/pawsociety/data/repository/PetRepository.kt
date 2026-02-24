package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PetRepository {
    
    private val apiService = ApiClient.apiService
    
    /**
     * Get all pets with optional filters
     */
    suspend fun getPets(
        ownerUid: String? = null,
        type: String? = null,
        limit: Int = 50,
        skip: Int = 0
    ): Result<List<ApiPet>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPets(ownerUid, type, limit, skip)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.pets != null) {
                    Result.success(body.pets)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to get pets"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get pets"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get single pet by ID
     */
    suspend fun getPet(petId: String): Result<ApiPet> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPet(petId)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Pet not found"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to get pet"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add a new pet
     */
    suspend fun createPet(
        ownerUid: String,
        name: String,
        type: String,
        description: String,
        breed: String? = null,
        imageUrl: String? = null,
        age: String? = null,
        gender: String? = null
    ): Result<ApiPet> = withContext(Dispatchers.IO) {
        try {
            val request = CreatePetRequest(
                ownerUid = ownerUid,
                name = name,
                type = type,
                description = description,
                breed = breed,
                imageUrl = imageUrl,
                age = age,
                gender = gender
            )
            
            val response = apiService.createPet(request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to create pet"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to create pet"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update a pet
     */
    suspend fun updatePet(
        petId: String,
        ownerUid: String,
        name: String? = null,
        type: String? = null,
        description: String? = null,
        breed: String? = null,
        imageUrl: String? = null,
        age: String? = null,
        gender: String? = null
    ): Result<ApiPet> = withContext(Dispatchers.IO) {
        try {
            val request = mutableMapOf<String, Any>()
            name?.let { request["name"] = it }
            type?.let { request["type"] = it }
            description?.let { request["description"] = it }
            breed?.let { request["breed"] = it }
            imageUrl?.let { request["imageUrl"] = it }
            age?.let { request["age"] = it }
            gender?.let { request["gender"] = it }
            
            val response = apiService.updatePet(petId, request)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.data != null) {
                    Result.success(body.data)
                } else {
                    Result.failure(Exception(body?.message ?: "Failed to update pet"))
                }
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to update pet"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a pet
     */
    suspend fun deletePet(petId: String, ownerUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.deletePet(petId, mapOf("ownerUid" to ownerUid))
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to delete pet"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
