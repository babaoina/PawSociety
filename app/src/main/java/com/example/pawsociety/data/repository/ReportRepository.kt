package com.example.pawsociety.data.repository

import com.example.pawsociety.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportRepository {

    private val apiService = ApiClient.apiService

    /**
     * Report a user, post, or comment
     */
    suspend fun createReport(
        reporterUid: String,
        reason: String,
        reportedUid: String? = null,
        postId: String? = null,
        commentId: String? = null,
        description: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("📝 Creating report from $reporterUid for reason: $reason")

            val request = ReportRequest(
                reporterUid = reporterUid,  // This should be Firebase UID
                reportedUid = reportedUid,   // This should be Firebase UID of reported user
                postId = postId,
                commentId = commentId,
                reason = reason,
                description = description
            )

            val response = apiService.createReport(request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success) {
                    println("✅ Report submitted successfully")
                    Result.success(Unit)
                } else {
                    println("❌ Report failed: ${body?.message}")
                    Result.failure(Exception(body?.message ?: "Failed to submit report"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                println("❌ Report error: $errorBody")
                Result.failure(Exception(errorBody ?: "Failed to submit report"))
            }
        } catch (e: Exception) {
            println("❌ Report exception: ${e.message}")
            Result.failure(e)
        }
    }
}