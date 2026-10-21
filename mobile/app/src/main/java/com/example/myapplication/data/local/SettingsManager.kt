package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * Quản lý cấu hình ứng dụng: Ngôn ngữ, Giao diện (Theme), Thông báo.
 *
 * Mỗi Flow dùng distinctUntilChanged() để tránh trigger recomposition
 * khi giá trị thực tế không thay đổi.
 */
class SettingsManager(private val context: Context) {

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode") // "SYSTEM", "LIGHT", "DARK"
        private val LANGUAGE_KEY = stringPreferencesKey("app_language") // "VI", "EN"
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val ALERT_CPU_KEY = booleanPreferencesKey("alert_cpu")
        private val ALERT_RAM_KEY = booleanPreferencesKey("alert_ram")
        private val ALERT_OFFLINE_KEY = booleanPreferencesKey("alert_offline")
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
    }

    // Đọc một lần toàn bộ preferences snapshot → tránh mở file nhiều lần
    private val prefsFlow: Flow<Preferences> = context.settingsDataStore.data

    val themeMode: Flow<String> = prefsFlow.map { it[THEME_KEY] ?: "SYSTEM" }.distinctUntilChanged()

    val language: Flow<String> = prefsFlow.map { it[LANGUAGE_KEY] ?: "VI" }.distinctUntilChanged()

    val notificationsEnabled: Flow<Boolean> = prefsFlow.map { it[NOTIFICATIONS_ENABLED_KEY] ?: true }.distinctUntilChanged()

    val alertCpu: Flow<Boolean> = prefsFlow.map { it[ALERT_CPU_KEY] ?: true }.distinctUntilChanged()

    val alertRam: Flow<Boolean> = prefsFlow.map { it[ALERT_RAM_KEY] ?: true }.distinctUntilChanged()

    val alertOffline: Flow<Boolean> = prefsFlow.map { it[ALERT_OFFLINE_KEY] ?: true }.distinctUntilChanged()

    val serverUrl: Flow<String> = prefsFlow.map {
        it[SERVER_URL_KEY] ?: com.example.myapplication.data.api.RetrofitClient.DEFAULT_BASE_URL
    }.distinctUntilChanged()

    suspend fun setServerUrl(url: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = url
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_KEY] = mode
        }
    }

    suspend fun setLanguage(lang: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[LANGUAGE_KEY] = lang
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setAlertConfig(cpu: Boolean, ram: Boolean, offline: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[ALERT_CPU_KEY] = cpu
            prefs[ALERT_RAM_KEY] = ram
            prefs[ALERT_OFFLINE_KEY] = offline
        }
    }
}
