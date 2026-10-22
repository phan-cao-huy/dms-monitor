package com.example.myapplication.data.api

import com.example.myapplication.data.local.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Mặc định dùng IP thật của máy (10.37.9.0) hoặc có thể đổi động qua UI
    const val DEFAULT_BASE_URL = "http://10.37.9.0:8000/api/v1/"
    private var currentBaseUrl = DEFAULT_BASE_URL

    private var tokenManager: TokenManager? = null
    private val tokenRefreshLock = Any()

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

    private val tokenAuthenticator = Authenticator { _, response ->
        refreshAfterUnauthorized(response)
    }

    private fun refreshAfterUnauthorized(response: Response): Request? {
        var responseCount = 1
        var prior = response.priorResponse
        while (prior != null) {
            responseCount++
            prior = prior.priorResponse
        }
        if (responseCount >= 2) return null

        val manager = tokenManager ?: return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val currentToken = manager.getAccessTokenSync() ?: return null
        if (currentToken != failedToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .build()
        }

        synchronized(tokenRefreshLock) {
            val latestToken = manager.getAccessTokenSync() ?: return null
            if (latestToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .build()
            }

            val refreshToken = manager.getRefreshTokenSync() ?: return null
            val refreshRequest = Request.Builder()
                .url("${currentBaseUrl}auth/refresh")
                .post(
                    JSONObject().put("refresh_token", refreshToken).toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()

            return try {
                refreshClient.newCall(refreshRequest).execute().use { refreshResponse ->
                    if (!refreshResponse.isSuccessful) return null
                    val json = JSONObject(refreshResponse.body?.string() ?: return null)
                    val access = json.optString("access_token").takeIf(String::isNotBlank) ?: return null
                    val refresh = json.optString("refresh_token").takeIf(String::isNotBlank) ?: return null
                    runBlocking { manager.saveTokens(access, refresh) }
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $access")
                        .build()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private val refreshClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenAuthenticator)
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
