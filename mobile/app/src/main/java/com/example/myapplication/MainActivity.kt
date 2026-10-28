package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.local.SettingsManager
import com.example.myapplication.ui.i18n.AppStrings
import com.example.myapplication.ui.i18n.LocalAppStrings
import com.example.myapplication.ui.navigation.NavGraph
import com.example.myapplication.ui.theme.DmsMonitorTheme
import com.example.myapplication.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val permissionLauncher=registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
        val permissionPrefs=getSharedPreferences("notification_permission", MODE_PRIVATE)
        if(android.os.Build.VERSION.SDK_INT>=33&&
            androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED&&
            !permissionPrefs.getBoolean("asked",false)){
            permissionPrefs.edit().putBoolean("asked",true).apply()
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val settingsManager = SettingsManager(applicationContext)

        setContent {
            // NavController pháº£i Ä‘Æ°á»£c táº¡o NGOÃ€I Theme Ä‘á»ƒ Ä‘á»•i theme khÃ´ng reset navigation
            val navController = rememberNavController()

            val themeMode by settingsManager.themeMode.collectAsState(initial = "SYSTEM")
            val language by settingsManager.language.collectAsState(initial = "VI")
            val notificationsEnabled by settingsManager.notificationsEnabled.collectAsState(initial = true)
            val alertCpu by settingsManager.alertCpu.collectAsState(initial = true)
            val alertRam by settingsManager.alertRam.collectAsState(initial = true)
            val alertOffline by settingsManager.alertOffline.collectAsState(initial = true)

            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            val appStrings = remember(language) { AppStrings(isEnglish = language == "EN") }

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                DmsMonitorTheme(darkTheme = isDark) {
                    Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        val authViewModel: AuthViewModel = viewModel()

                        NavGraph(
                            navController = navController,
                            authViewModel = authViewModel,
                            currentTheme = themeMode,
                            currentLanguage = language,
                            onThemeChange = { mode ->
                                lifecycleScope.launch {
                                    settingsManager.setThemeMode(mode)
                                }
                            },
                            onLanguageChange = { lang ->
                                lifecycleScope.launch {
                                    settingsManager.setLanguage(lang)
                                }
                            },
                            notificationsEnabled = notificationsEnabled,
                            alertCpu = alertCpu,
                            alertRam = alertRam,
                            alertOffline = alertOffline,
                            onNotificationConfigChange = { push, cpu, ram, offline ->
                                lifecycleScope.launch {
                                    settingsManager.setNotificationsEnabled(push)
                                    settingsManager.setAlertConfig(cpu, ram, offline)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}