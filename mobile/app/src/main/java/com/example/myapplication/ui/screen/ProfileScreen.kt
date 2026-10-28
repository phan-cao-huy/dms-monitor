package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.example.myapplication.data.model.AppVersionResponse
import com.example.myapplication.data.model.UserInfo
import com.example.myapplication.ui.i18n.LocalAppStrings
import com.example.myapplication.util.AppUpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: UserInfo?,
    openUpdateOnLaunch: Boolean = false,
    onUpdateOpened: () -> Unit = {},
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit = {},
    currentTheme: String = "SYSTEM",
    currentLanguage: String = "VI",
    onThemeChange: (String) -> Unit = {},
    onLanguageChange: (String) -> Unit = {},
    notificationsEnabled: Boolean = true,
    alertCpu: Boolean = true,
    alertRam: Boolean = true,
    alertOffline: Boolean = true,
    onNotificationConfigChange: (Boolean, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> }
) {
    val strings = LocalAppStrings.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showUpdateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(openUpdateOnLaunch) {
        if(openUpdateOnLaunch){showUpdateDialog=true;onUpdateOpened()}
    }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableFloatStateOf(0f) }
    var downloadText by remember { mutableStateOf("") }
    var updateInfo by remember { mutableStateOf<AppVersionResponse?>(null) }
    var hasUpdateAvailable by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var downloadedApkFile by remember { mutableStateOf<java.io.File?>(null) }
    var waitingForInstallPermission by remember { mutableStateOf(false) }
    var currentVersion by remember { mutableStateOf(AppUpdateManager.getCurrentVersionName(context)) }

    val refreshApkCache: (AppVersionResponse?) -> Unit = { info ->
        if (info != null) {
            val existing = AppUpdateManager.getDownloadedApkFile(context, info.versionCode.toLong(), info.apkSizeBytes)
            downloadedApkFile = existing
        }
    }

    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdate(context).onSuccess { (info, hasUpdate) ->
            updateInfo = info
            hasUpdateAvailable = hasUpdate
            refreshApkCache(info)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                currentVersion=AppUpdateManager.getCurrentVersionName(context)
                hasUpdateAvailable=(updateInfo?.versionCode?.toLong()?:0)>AppUpdateManager.getCurrentVersionCode(context)
                if(!hasUpdateAvailable){downloadedApkFile=null;waitingForInstallPermission=false}
                coroutineScope.launch {
                    AppUpdateManager.checkForUpdate(context).onSuccess { (info,newer) ->
                        updateInfo=info;hasUpdateAvailable=newer
                        if(newer)refreshApkCache(info) else downloadedApkFile=null
                    }
                }
                refreshApkCache(updateInfo)
                if (waitingForInstallPermission) {
                    if (AppUpdateManager.canInstallPackages(context)) {
                        waitingForInstallPermission = false
                        downloadedApkFile?.let { apk ->
                            AppUpdateManager.installApk(context, apk).onFailure { err ->
                                if (err.message != "PERMISSION_NEEDED") {
                                    updateError = err.message
                                }
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollState = rememberScrollState()

    // 1. Chỉnh sửa thông tin
    if (showEditDialog) {
        var editName by remember { mutableStateOf(user?.fullName ?: "") }
        var editEmail by remember { mutableStateOf(user?.email ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(if (strings.isEnglish) "Edit Profile" else "Chỉnh sửa thông tin") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(if (strings.isEnglish) "Full Name" else "Họ và tên") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showEditDialog = false }) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    // 2. Đổi mật khẩu
    if (showPasswordDialog) {
        var oldPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(if (strings.isEnglish) "Change Password" else "Đổi mật khẩu") },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        label = { Text(if (strings.isEnglish) "Current Password" else "Mật khẩu hiện tại") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(if (strings.isEnglish) "New Password (min 8 chars)" else "Mật khẩu mới (tối thiểu 8 ký tự)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(if (strings.isEnglish) "Confirm New Password" else "Xác nhận mật khẩu mới") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = confirmPassword.isNotBlank() && newPassword != confirmPassword,
                        supportingText = {
                            if (confirmPassword.isNotBlank() && newPassword != confirmPassword) {
                                Text(if (strings.isEnglish) "Passwords do not match" else "Mật khẩu không khớp")
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPasswordDialog = false },
                    enabled = oldPassword.isNotBlank() && newPassword.length >= 8 && newPassword == confirmPassword
                ) {
                    Text(if (strings.isEnglish) "Update Password" else "Đổi mật khẩu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    // 3. Dialog Cấu hình thông báo (Tương tác được 100%)
    if (showNotificationDialog) {
        var notifyPush by remember { mutableStateOf(notificationsEnabled) }
        var notifyCpu by remember { mutableStateOf(alertCpu) }
        var notifyRam by remember { mutableStateOf(alertRam) }
        var notifyOffline by remember { mutableStateOf(alertOffline) }

        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = { Text(if (strings.isEnglish) "Notification Settings" else "Cấu hình thông báo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (strings.isEnglish) "Push Notifications" else "Thông báo đẩy (FCM)", fontWeight = FontWeight.SemiBold)
                            Text(if (strings.isEnglish) "Receive alerts on lock screen" else "Nhận thông báo khi máy khóa màn hình", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = notifyPush, onCheckedChange = { notifyPush = it })
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (strings.isEnglish) "High CPU Alert (>85%)" else "Cảnh báo CPU quá tải (>85%)", fontWeight = FontWeight.SemiBold)
                            Text(if (strings.isEnglish) "Notify when CPU exceeds limit" else "Báo động khi vi xử lý quá nóng/tải", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = notifyCpu, onCheckedChange = { notifyCpu = it }, enabled = notifyPush)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (strings.isEnglish) "High RAM Alert (>90%)" else "Cảnh báo RAM vượt 90%", fontWeight = FontWeight.SemiBold)
                            Text(if (strings.isEnglish) "Notify when RAM usage exceeds 90%" else "Cảnh báo khi mức sử dụng RAM vượt 90%", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = notifyRam, onCheckedChange = { notifyRam = it }, enabled = notifyPush)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (strings.isEnglish) "Device Offline Alert" else "Cảnh báo máy mất kết nối", fontWeight = FontWeight.SemiBold)
                            Text(if (strings.isEnglish) "Notify when agent disconnects" else "Báo động khi máy tắt hoặc mất mạng", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = notifyOffline, onCheckedChange = { notifyOffline = it }, enabled = notifyPush)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onNotificationConfigChange(notifyPush, notifyCpu, notifyRam, notifyOffline)
                    showNotificationDialog = false
                }) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    // 4. Dialog Chọn giao diện Sáng / Tối (Tương tác được 100%)
    if (showThemeDialog) {
        var selectedTheme by remember { mutableStateOf(currentTheme) }
        val themeOptions = listOf(
            Triple("SYSTEM", if (strings.isEnglish) "System default" else "Theo hệ thống", Icons.Default.BrightnessAuto),
            Triple("LIGHT", if (strings.isEnglish) "Light mode" else "Chế độ sáng", Icons.Default.LightMode),
            Triple("DARK", if (strings.isEnglish) "Dark mode" else "Chế độ tối", Icons.Default.DarkMode)
        )

        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(if (strings.isEnglish) "Choose Theme" else "Chọn giao diện") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    themeOptions.forEach { (mode, label, icon) ->
                        Surface(
                            onClick = { selectedTheme = mode },
                            shape = MaterialTheme.shapes.small,
                            color = if (selectedTheme == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTheme == mode,
                                    onClick = { selectedTheme = mode }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onThemeChange(selectedTheme)
                    showThemeDialog = false
                }) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    // 5. Dialog Chọn Ngôn ngữ Tiếng Việt / English (Tương tác được 100%)
    if (showLanguageDialog) {
        var selectedLang by remember { mutableStateOf(currentLanguage) }
        val langOptions = listOf(
            Pair("VI", "Tiếng Việt 🇻🇳 (Vietnamese)"),
            Pair("EN", "English 🇬🇧 (Tiếng Anh)")
        )

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(if (strings.isEnglish) "Select Language" else "Chọn ngôn ngữ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    langOptions.forEach { (code, label) ->
                        Surface(
                            onClick = { selectedLang = code },
                            shape = MaterialTheme.shapes.small,
                            color = if (selectedLang == code) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedLang == code,
                                    onClick = { selectedLang = code }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onLanguageChange(selectedLang)
                    showLanguageDialog = false
                }) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    // 6. Dialog Thông tin phiên bản (Tương tác được 100%)
    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = { Text("DMS Monitor v1.0.0") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🖥️ Hệ thống giám sát máy tính từ xa qua ứng dụng di động", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Phiên bản ứng dụng: 1.0.0 (Build 2026.10)")
                    Text("• Nền tảng: Kotlin, Jetpack Compose, Material 3")
                    Text("• Máy chủ API: FastAPI + PostgreSQL 15 (Docker)")
                    Text("• Agent thu thập: Golang (gopsutil)")
                    Text("• Đồ án Tốt nghiệp chuyên ngành CNTT")
                }
            },
            confirmButton = {
                Button(onClick = { showVersionDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    // 7. Dialog Giấy phép (Tương tác được 100%)
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(if (strings.isEnglish) "Open Source License" else "Giấy phép bản quyền") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MIT License © 2026 DMS Monitor Team", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Dự án được phát triển phục vụ học phần Đồ án Tốt nghiệp. " +
                        "Bản quyền mã nguồn mở theo giấy phép MIT. Toàn quyền sử dụng, sửa đổi và phân phối phục vụ nghiên cứu và học tập."
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showLicenseDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    // 8. Dialog Cập nhật ứng dụng qua Server (OTA)
    if (showUpdateDialog) {
        val performDownloadAndInstall: (Boolean) -> Unit = { forceReDownload ->
            if (!isDownloadingUpdate && updateInfo != null && updateInfo!!.versionCode > AppUpdateManager.getCurrentVersionCode(context)) {
                val info = updateInfo!!
                val existing = if (!forceReDownload) AppUpdateManager.getDownloadedApkFile(context, info.versionCode.toLong(), info.apkSizeBytes) else null
                if (existing != null) {
                    downloadedApkFile = existing
                    AppUpdateManager.installApk(context, existing).onFailure { err ->
                        if (err.message == "PERMISSION_NEEDED") {
                            waitingForInstallPermission = true
                        } else {
                            updateError = err.message
                        }
                    }
                } else {
                    isDownloadingUpdate = true
                    updateError = null
                    downloadedApkFile = null
                    coroutineScope.launch {
                        val res = AppUpdateManager.downloadApk(
                            context = context,
                            apkRelativeUrl = info.apkUrl,
                            versionCode = info.versionCode.toLong(),
                            expectedSizeBytes = info.apkSizeBytes,
                            onProgress = { progress, downloaded, total ->
                                updateProgress = progress
                                val downMB = String.format("%.1f", downloaded / (1024f * 1024f))
                                val totMB = String.format("%.1f", total / (1024f * 1024f))
                                val pct = (progress * 100).toInt()
                                downloadText = if (strings.isEnglish) "Downloading: $pct% ($downMB / $totMB MB)" else "Đang tải: $pct% ($downMB / $totMB MB)"
                            }
                        )
                        isDownloadingUpdate = false
                        res.fold(
                            onSuccess = { apkFile ->
                                downloadedApkFile = apkFile
                                AppUpdateManager.installApk(context, apkFile).onFailure { err ->
                                    if (err.message == "PERMISSION_NEEDED") {
                                        waitingForInstallPermission = true
                                    } else {
                                        updateError = err.message
                                    }
                                }
                            },
                            onFailure = { err ->
                                updateError = (if (strings.isEnglish) "Download failed: " else "Tải thất bại: ") + err.message
                            }
                        )
                    }
                }
            }
        }

        val directDownloadUrl = remember {
            com.example.myapplication.util.UpdateLink.resolve(
                com.example.myapplication.data.api.RetrofitClient.getBaseUrl(), "/static/dms-app.apk")
        }

        AlertDialog(
            onDismissRequest = {
                if (!isDownloadingUpdate) showUpdateDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (downloadedApkFile != null) Icons.Default.CheckCircle
                        else if (hasUpdateAvailable) Icons.Default.SystemUpdate
                        else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (hasUpdateAvailable) MaterialTheme.colorScheme.primary else Color(0xFF6C9D61)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isCheckingUpdate) (if (strings.isEnglish) "Checking for updates..." else "Kiểm tra phiên bản...")
                        else if (downloadedApkFile != null) (if (strings.isEnglish) "Ready to Install" else "Sẵn sàng cài đặt")
                        else if (hasUpdateAvailable) (if (strings.isEnglish) "App Update Available" else "Cập nhật ứng dụng")
                        else (if (strings.isEnglish) "Latest Version" else "Phiên bản mới nhất")
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isCheckingUpdate) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(if (strings.isEnglish) "Connecting to server to check latest release..." else "Đang kết nối server để kiểm tra bản phát hành mới...")
                    } else if (waitingForInstallPermission) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "ℹ️ Đang chờ cấp quyền cài đặt",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Vui lòng gạt nút 'Cho phép từ nguồn này' trong Cài đặt, sau đó quay lại ứng dụng để hệ thống tự động cài đặt ngay.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { AppUpdateManager.requestInstallPermission(context) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Mở lại màn hình Cài đặt cấp quyền")
                                }
                            }
                        }
                    } else if (updateError != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ $updateError",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (!isCheckingUpdate && updateInfo != null) {
                        val info = updateInfo!!
                        if (downloadedApkFile != null && !isDownloadingUpdate) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Đã có sẵn file cài đặt v${info.versionName}!",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "File APK đã lưu sẵn trên máy. Bấm 'Cài đặt ngay' bên dưới mà không cần tải lại.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF1B5E20)
                                        )
                                    }
                                }
                            }
                        } else if (hasUpdateAvailable) {
                            Text(
                                text = if (strings.isEnglish) "🎉 New version v${info.versionName} is ready!" else "🎉 Đã có bản cập nhật mới v${info.versionName}!",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(if (strings.isEnglish) "• Current installed: v$currentVersion" else "• Phiên bản hiện tại: v$currentVersion")
                            Text(if (strings.isEnglish) "• Server release: v${info.versionName} (Build ${info.versionCode})" else "• Phiên bản mới: v${info.versionName} (Build ${info.versionCode})")
                        } else {
                            Text(
                                text = if (strings.isEnglish) "✅ You are using the latest version!" else "✅ Bạn đang sử dụng phiên bản mới nhất!",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32)
                            )
                            Text(if (strings.isEnglish) "• Installed: v$currentVersion | Server: v${info.versionName}" else "• Ứng dụng: v$currentVersion | Server: v${info.versionName}")
                        }

                        if (info.apkSizeBytes > 0) {
                            val sizeMB = String.format("%.1f", info.apkSizeBytes / (1024f * 1024f))
                            Text(if (strings.isEnglish) "• Download size: ~$sizeMB MB" else "• Dung lượng: ~$sizeMB MB")
                        }
                        if (!info.releaseNotes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(if (strings.isEnglish) "Changelog:" else "Nhật ký thay đổi:", fontWeight = FontWeight.SemiBold)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(info.releaseNotes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }

                    if (isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { updateProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = downloadText.ifBlank { if (strings.isEnglish) "Downloading..." else "Đang tải xuống..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!isDownloadingUpdate) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        OutlinedButton(
                            onClick = {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(directDownloadUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(browserIntent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (strings.isEnglish) "Download via Browser" else "Tải trực tiếp qua Trình duyệt")
                        }
                    }
                }
            },
            confirmButton = {
                if (downloadedApkFile != null) {
                    Button(
                        onClick = { performDownloadAndInstall(false) },
                        enabled = !isDownloadingUpdate && !isCheckingUpdate
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (strings.isEnglish) "Install Now" else "Cài đặt ngay")
                    }
                } else if (updateInfo != null) {
                    Button(
                        onClick = { performDownloadAndInstall(false) },
                        enabled = !isDownloadingUpdate && !isCheckingUpdate
                    ) {
                        Text(
                            if (isDownloadingUpdate) (if (strings.isEnglish) "Downloading..." else "Đang tải...")
                            else if (hasUpdateAvailable) (if (strings.isEnglish) "Download & Install" else "Tải & Cài đặt ngay")
                            else (if (strings.isEnglish) "Re-download APK" else "Tải lại bản cài đặt")
                        )
                    }
                } else if (!isCheckingUpdate) {
                    Button(onClick = { showUpdateDialog = false }) {
                        Text(strings.close)
                    }
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    Row {
                        if (downloadedApkFile != null) {
                            TextButton(onClick = { performDownloadAndInstall(true) }) {
                                Text("Tải lại")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text(strings.close)
                        }
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Profile header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (user?.fullName?.firstOrNull() ?: user?.username?.firstOrNull() ?: '?').uppercase(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = user?.fullName ?: (if (strings.isEnglish) "Administrator" else "Người dùng"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@${user?.username ?: "admin"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                user?.email?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account section
        Text(
            text = strings.settingsAccount,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            icon = Icons.Default.Edit,
            title = strings.editInfo,
            subtitle = strings.editInfoSub,
            onClick = { showEditDialog = true }
        )
        SettingsItem(
            icon = Icons.Default.Lock,
            title = strings.changePassword,
            subtitle = strings.changePasswordSub,
            onClick = { showPasswordDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // App section
        Text(
            text = strings.settingsApp,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        val themeLabel = when (currentTheme) {
            "LIGHT" -> if (strings.isEnglish) "Light mode" else "Chế độ sáng"
            "DARK" -> if (strings.isEnglish) "Dark mode" else "Chế độ tối"
            else -> if (strings.isEnglish) "System default" else "Theo hệ thống"
        }

        val langLabel = if (currentLanguage == "EN") "English 🇬🇧" else "Tiếng Việt 🇻🇳"

        SettingsItem(
            icon = Icons.Default.Notifications,
            title = strings.notifications,
            subtitle = if (notificationsEnabled) (if (strings.isEnglish) "Push alerts enabled" else "Đang bật thông báo đẩy") else (if (strings.isEnglish) "Disabled" else "Đang tắt"),
            onClick = { showNotificationDialog = true }
        )
        SettingsItem(
            icon = Icons.Default.DarkMode,
            title = strings.appearance,
            subtitle = themeLabel,
            onClick = { showThemeDialog = true }
        )
        SettingsItem(
            icon = Icons.Default.Language,
            title = strings.language,
            subtitle = langLabel,
            onClick = { showLanguageDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // About section
        Text(
            text = strings.about,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        SettingsItem(
            icon = Icons.Default.Info,
            title = strings.version,
            subtitle = "DMS Monitor v$currentVersion (Build 2026.10)",
            onClick = { showVersionDialog = true }
        )
        val hasDownloaded = downloadedApkFile != null
        SettingsItem(
            icon = Icons.Default.SystemUpdate,
            title = if (strings.isEnglish) "Check for Updates" else "Cập nhật ứng dụng qua Server",
            subtitle = if (hasDownloaded) "📦 Đã tải sẵn v${updateInfo?.versionName ?: ""} (Bấm để cài)"
                       else if (hasUpdateAvailable) "🎉 Đã có bản v${updateInfo?.versionName ?: ""} (Bấm để tải)"
                       else (if (strings.isEnglish) "Download and install latest APK" else "Tự động tải & cài đặt bản mới nhất từ server"),
            onClick = {
                showUpdateDialog = true
                isCheckingUpdate = true
                updateError = null
                coroutineScope.launch {
                    AppUpdateManager.checkForUpdate(context).fold(
                        onSuccess = { (info, hasUpdate) ->
                            updateInfo = info
                            hasUpdateAvailable = hasUpdate
                            refreshApkCache(info)
                            isCheckingUpdate = false
                        },
                        onFailure = { err ->
                            updateError = err.message
                            isCheckingUpdate = false
                        }
                    )
                }
            }
        )
        SettingsItem(
            icon = Icons.Default.Description,
            title = strings.license,
            subtitle = if (strings.isEnglish) "MIT Open Source License" else "Bản quyền mở MIT - Đồ án tốt nghiệp",
            onClick = { showLicenseDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Phần tài khoản — Chuyển đổi & Đăng xuất
        Text(
            text = if (strings.isEnglish) "Session" else "Phiên làm việc",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        // Chuyển đổi tài khoản
        SettingsItem(
            icon = Icons.Default.SwitchAccount,
            title = if (strings.isEnglish) "Switch Account" else "Chuyển đổi tài khoản",
            subtitle = if (strings.isEnglish) "Sign in with a different account" else "Đăng nhập bằng tài khoản khác",
            onClick = onSwitchAccount
        )

        // Đăng xuất
        SettingsItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = strings.logout,
            subtitle = if (strings.isEnglish) "Sign out of this session" else "Thoát khỏi tài khoản hiện tại",
            onClick = onLogout,
            titleColor = MaterialTheme.colorScheme.error,
            iconColor = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
