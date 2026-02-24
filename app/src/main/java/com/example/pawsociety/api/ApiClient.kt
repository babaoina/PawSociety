package com.example.pawsociety.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Backend URL configuration
    // For Android Emulator: use 10.0.2.2
    // For physical device: use your PC's local IP (e.g., 192.168.1.38)
    private const val BASE_URL = "http://192.168.1.38:5000/api/"
    
    // Full base URL for accessing uploaded images
    const val FULL_BASE_URL = "http://192.168.1.38:5000"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: PawSocietyApi = retrofit.create(PawSocietyApi::class.java)
    val uploadService: UploadApi = retrofit.create(UploadApi::class.java)
    
    /**
     * Update base URL for different environments
     * Call this before making API requests if needed
     */
    fun updateBaseUrl(newBaseUrl: String) {
        val newRetrofit = Retrofit.Builder()
            .baseUrl(newBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        // Note: This won't update the existing apiService, you'd need to recreate it
    }
    
    /**
     * Get the current base URL
     */
    fun getBaseUrl(): String = BASE_URL
}
