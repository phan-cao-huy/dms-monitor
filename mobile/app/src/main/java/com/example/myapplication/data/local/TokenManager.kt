package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicReference

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val CURRENT_ORG_ID_KEY = stringPreferencesKey("current_org_id")
    }

    // Cache token trong bộ nhớ để tránh runBlocking trên OkHttp thread
    private val cachedAccessToken = AtomicReference<String?>(null)
    private val cachedRefreshToken = AtomicReference<String?>(null)

    val accessToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACCESS_TOKEN_KEY].also {
            cachedAccessToken.set(it)
            cachedRefreshToken.set(prefs[REFRESH_TOKEN_KEY])
        }
    }

    val refreshToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[REFRESH_TOKEN_KEY]
    }

    val currentOrgId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[CURRENT_ORG_ID_KEY]
    }

    // Đọc từ cache in-memory — KHÔNG chặn thread
    fun getAccessTokenSync(): String? = cachedAccessToken.get()
    fun getRefreshTokenSync(): String? = cachedRefreshToken.get()

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        cachedAccessToken.set(accessToken)
        cachedRefreshToken.set(refreshToken)
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
    }

    suspend fun saveOrgId(orgId: String) {
        context.dataStore.edit { prefs ->
            prefs[CURRENT_ORG_ID_KEY] = orgId
        }
    }

    suspend fun clearAll() {
        cachedAccessToken.set(null)
        cachedRefreshToken.set(null)
        context.dataStore.edit { it.clear() }
    }

    suspend fun isLoggedIn(): Boolean {
        val prefs = context.dataStore.data.first()
        val token = prefs[ACCESS_TOKEN_KEY]
        cachedAccessToken.set(token)
        cachedRefreshToken.set(prefs[REFRESH_TOKEN_KEY])
        return token != null
    }
}
