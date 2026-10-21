package com.example.myapplication.data.api

import com.example.myapplication.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Mặc định dùng IP thật của máy (10.37.9.0) hoặc có thể đổi động qua UI
    const val DEFAULT_BASE_URL = "http://10.37.9.0:8000/api/v1/"
    private var currentBaseUrl = DEFAULT_BASE_URL

    private var tokenManager: TokenManager? = null

    fun getBaseUrl(): String = currentBaseUrl

    fun init(tokenManager: TokenManager, savedUrl: String? = null) {
        this.tokenManager = tokenManager
        if (!savedUrl.isNullOrBlank()) {
            setBaseUrl(savedUrl)
        }
    }

    fun setBaseUrl(newUrl: String) {
        var url = newUrl.trim()
        if (!url.endsWith("/")) url = "$url/"
        if (!url.contains("/api/v1/")) {
            url = if (url.endsWith("api/v1/")) url else "${url}api/v1/"
        }
        currentBaseUrl = url
        _apiService = createApiService(currentBaseUrl)
    }

    private val authInterceptor = Interceptor { chain ->
        val token = tokenManager?.getAccessTokenSync()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun createApiService(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private var _apiService: ApiService? = null

    val apiService: ApiService
        get() {
            if (_apiService == null) {
                _apiService = createApiService(currentBaseUrl)
            }
            return _apiService!!
        }
}
