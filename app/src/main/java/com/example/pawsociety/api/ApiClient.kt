package com.example.pawsociety.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Linux laptop backend IP on the same Wi-Fi network
    private const val SERVER_URL = "http://192.168.254.107:5000"

    val BASE_URL = "$SERVER_URL/api/"
    val PUBLIC_BASE_URL = "$SERVER_URL/api/public/"
    val FULL_BASE_URL = SERVER_URL
    val BASE_URL_NO_API = "$SERVER_URL/"

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

    private val publicRetrofit = Retrofit.Builder()
        .baseUrl(PUBLIC_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: PawSocietyApi = retrofit.create(PawSocietyApi::class.java)
    val publicApiService: PawSocietyApi = publicRetrofit.create(PawSocietyApi::class.java)
    val uploadService: UploadApi = retrofit.create(UploadApi::class.java)

    fun getImageBaseUrl(): String = BASE_URL_NO_API
}
