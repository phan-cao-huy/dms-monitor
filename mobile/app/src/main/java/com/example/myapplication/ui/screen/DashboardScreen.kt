package com.example.myapplication.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.Alert
import com.example.myapplication.data.model.Device
import com.example.myapplication.data.model.Organization
import com.example.myapplication.data.model.UserInfo
import com.example.myapplication.ui.i18n.LocalAppStrings
import com.example.myapplication.ui.viewmodel.DashboardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onDeviceClick: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    filteredDevices: List<Device>,
    onSelectGroupFilter: (Int?) -> Unit = {},
    onSelectOrg: (Organization) -> Unit = {},
    onCreateDevice: (String, String?) -> Unit = { _, _ -> },
    onDismissCreatedDevice: () -> Unit = {},
    currentUser: UserInfo? = null,
    onNavigateToSettings: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var newDeviceName by remember { mutableStateOf("") }
    var selectedNewDeviceGroupId by remember { mutableStateOf<Int?>(null) }

    // Dialog 1: Tạo thiết bị mới
    if (showAddDeviceDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDeviceDialog = false
                newDeviceName = ""
                selectedNewDeviceGroupId = null
            },
            title = { Text(if (strings.isEnglish) "Add New Device" else "Thêm thiết bị mới") },
            text = {
                Column {
                    Text(
                        text = if (strings.isEnglish)
                            "Enter device name to register with backend and generate secret token for agent:"
                        else
                            "Nhập tên thiết bị để đăng ký lên hệ thống và tạo mã bảo mật kết nối Agent:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newDeviceName,
                        onValueChange = { newDeviceName = it },
                        label = { Text(if (strings.isEnglish) "Device Name (e.g. PC-Accounting-01)" else "Tên thiết bị (VD: PC-KeToan-01)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.groups.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Nhóm thiết bị (quyền truy cập):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedNewDeviceGroupId == null,
                                onClick = { selectedNewDeviceGroupId = null },
                                label = { Text("Chưa phân nhóm") }
                            )
                            uiState.groups.forEach { grp ->
                                FilterChip(
                                    selected = selectedNewDeviceGroupId == grp.groupId,
                                    onClick = { selectedNewDeviceGroupId = grp.groupId },
                                    label = { Text("${grp.groupName}") }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateDevice(newDeviceName.trim(), selectedNewDeviceGroupId?.toString())
                        showAddDeviceDialog = false
                        newDeviceName = ""
                        selectedNewDeviceGroupId = null
                    },
                    enabled = newDeviceName.isNotBlank()
                ) {
                    Text(if (strings.isEnglish) "Generate Token" else "Tạo thiết bị")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDeviceDialog = false
                    newDeviceName = ""
                    selectedNewDeviceGroupId = null
                }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Dialog 2: Hướng dẫn cài đặt Agent (Khi tạo máy thành công)
    uiState.newlyCreatedDevice?.let { created ->
        var selectedInstallTab by remember { mutableIntStateOf(0) }
        val secretToken = created.secretToken
        val serverUrl = com.example.myapplication.data.api.RetrofitClient.getBaseUrl().replace("/api/v1/", "").trimEnd('/')

        val winCommand = created.installCommand?.takeIf { it.isNotBlank() } ?: "& ([scriptblock]::Create((irm '$serverUrl/static/install.ps1'))) -Server $serverUrl -Token $secretToken -DeviceId ${created.id}"
        val linuxCommand = "curl -fsSL $serverUrl/static/dms-agent-linux -o dms-agent && chmod +x dms-agent && ./dms-agent -server '$serverUrl' -token '$secretToken' -id ${created.id}"
        val configJson = """{
  "backend_url": "$serverUrl/api/v1",
  "secret_token": "$secretToken",
  "device_id": ${created.id},
  "heartbeat_interval_seconds": 10,
  "command_poll_interval_seconds": 5
}"""

        val qrBitmap = remember(secretToken) { generateQrBitmap(secretToken, 400) }

        AlertDialog(
            onDismissRequest = onDismissCreatedDevice,
            title = {
                Column {
                    Text("🎉 ${created.name}", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (strings.isEnglish) "Agent Setup & Pairing Code" else "Mã kết nối & Hướng dẫn cài Agent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Secret token callout
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "⚠️ " + (if (strings.isEnglish) "Secret Token (Shown ONLY ONCE):" else "Token bí mật (Chỉ hiển thị 1 lần duy nhất):"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = secretToken,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Device Token", secretToken))
                                    Toast.makeText(context, "Đã sao chép token!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tabs: Windows / Linux / QR / Config
                    TabRow(selectedTabIndex = selectedInstallTab) {
                        Tab(selected = selectedInstallTab == 0, onClick = { selectedInstallTab = 0 }, text = { Text("Windows", fontSize = 12.sp) })
                        Tab(selected = selectedInstallTab == 1, onClick = { selectedInstallTab = 1 }, text = { Text("Linux", fontSize = 12.sp) })
                        Tab(selected = selectedInstallTab == 2, onClick = { selectedInstallTab = 2 }, text = { Text("QR", fontSize = 12.sp) })
                        Tab(selected = selectedInstallTab == 3, onClick = { selectedInstallTab = 3 }, text = { Text("JSON", fontSize = 12.sp) })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (selectedInstallTab) {
                        0 -> {
                            Text("Chạy lệnh trong PowerShell (Run as Admin):", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(winCommand, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Windows Command", winCommand))
                                        Toast.makeText(context, "Đã sao chép lệnh Windows!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        1 -> {
                            Text("Chạy lệnh trong Terminal Linux:", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(linuxCommand, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Linux Command", linuxCommand))
                                        Toast.makeText(context, "Đã sao chép lệnh Linux!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        2 -> {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                qrBitmap?.let {
                                    Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(160.dp))
                                }
                                Text("Quét mã để nhận secret_token", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        3 -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(configJson, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Config JSON", configJson))
                                        Toast.makeText(context, "Đã sao chép file cấu hình!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismissCreatedDevice) {
                    Text(if (strings.isEnglish) "Done" else "Hoàn tất")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            var showOrgMenu by remember { mutableStateOf(false) }
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = uiState.organizations.isNotEmpty()) {
                                    showOrgMenu = true
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        ) {
                            BrandMark(Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("DMS Monitor", style = MaterialTheme.typography.titleMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = uiState.selectedOrg?.orgName ?: "Chưa chọn không gian làm việc",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (uiState.organizations.size > 1) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Chuyển không gian",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showOrgMenu,
                            onDismissRequest = { showOrgMenu = false }
                        ) {
                            Text(
                                text = if (strings.isEnglish) "Switch Workspace" else "Chuyển không gian",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider()
                            uiState.organizations.forEach { org ->
                                val isSelected = org.id == uiState.selectedOrg?.id
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = org.orgName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                                        )
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        showOrgMenu = false
                                        if (!isSelected) {
                                            onSelectOrg(org)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Avatar button → navigate to Settings
                    // Chỉ hiện avatar khi đã có thông tin user, tránh hiển thị tên tài khoản cũ
                    val avatarLetter = currentUser?.let {
                        (it.fullName?.firstOrNull() ?: it.username.firstOrNull() ?: 'U').uppercaseChar()
                    } ?: 'U'
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clickable { onNavigateToSettings() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = avatarLetter.toString(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState.selectedOrg != null) {
                FloatingActionButton(
                    onClick = { showAddDeviceDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = strings.addDevice)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.addDevice, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Trường hợp người dùng chưa có không gian làm việc nào
                if (uiState.organizations.isEmpty() && !uiState.isLoading) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.SpaceDashboard,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Chào mừng bạn đến với DMS Monitor!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Bạn chưa có không gian làm việc nào. Hãy chuyển sang mục 'Không gian làm việc' ở thanh dưới để tạo không gian làm việc mới hoặc tham gia bằng mã mời!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    return@LazyColumn
                }

                item {
                    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                        Text(if (strings.isEnglish) "System overview" else "Tổng quan hệ thống", style = MaterialTheme.typography.headlineMedium)
                        Text(if (strings.isEnglish) "Device health at a glance" else "Nắm bắt tình trạng thiết bị trong một tầm nhìn",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Thống kê tổng quan
                item {
                    StatusSummaryRow(devices = uiState.devices)
                }

                // Cảnh báo chưa xử lý
                if (uiState.alerts.isNotEmpty()) {
                    item {
                        AlertBanner(alerts = uiState.alerts)
                    }
                }

                // Nhóm thiết bị Filter Chips
                if (uiState.groups.isNotEmpty()) {
                    item {
                        Column {
                            Text("Phân nhóm thiết bị:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.selectedGroupFilter == null,
                                    onClick = { onSelectGroupFilter(null) },
                                    label = { Text("Tất cả (${uiState.devices.size})") }
                                )
                                FilterChip(
                                    selected = uiState.selectedGroupFilter == 0,
                                    onClick = { onSelectGroupFilter(0) },
                                    label = { Text("Chưa phân nhóm") }
                                )
                                uiState.groups.forEach { grp ->
                                    val countInGroup = uiState.devices.count { it.groupId == grp.groupId.toString() }
                                    FilterChip(
                                        selected = uiState.selectedGroupFilter == grp.groupId,
                                        onClick = { onSelectGroupFilter(grp.groupId) },
                                        label = { Text("${grp.groupName} ($countInGroup)") }
                                    )
                                }
                            }
                        }
                    }
                }

                // Thanh tìm kiếm
                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(strings.searchPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        trailingIcon = {
                            if (uiState.searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa")
                                }
                            }
                        }
                    )
                }

                // Danh sách thiết bị
                items(filteredDevices, key = { it.id }) { device ->
                    DeviceCard(device = device, onClick = { onDeviceClick(device.id) })
                }

                if (filteredDevices.isEmpty() && !uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isNotBlank() || uiState.selectedGroupFilter != null)
                                    "Không tìm thấy thiết bị phù hợp bộ lọc"
                                else
                                    strings.noDevices,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusSummaryRow(devices: List<Device>) {
    val strings = LocalAppStrings.current
    val online = devices.count { it.status == "Online" }
    val offline = devices.count { it.status == "Offline" }
    val warning = devices.count { it.status == "Warning" }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile(devices.size, strings.totalDevices, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            SummaryTile(online, strings.online, Color(0xFF6C9D61), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile(offline, strings.offline, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            SummaryTile(warning, strings.warning, Color(0xFFB58B45), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryTile(count: Int, label: String, color: Color, modifier: Modifier) {
    OutlinedCard(modifier = modifier, colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(7.dp).background(color, CircleShape))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(count.toString(), style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
fun AlertBanner(alerts: List<Alert>) {
    val strings = LocalAppStrings.current
    val unresolvedCount = alerts.count { !it.isResolved }
    if (unresolvedCount == 0) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (strings.isEnglish) "$unresolvedCount active alerts" else "$unresolvedCount cảnh báo chưa xử lý",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = alerts.firstOrNull { !it.isResolved }?.message ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DeviceCard(device: Device, onClick: () -> Unit) {
    val statusColor = when (device.status) {
        "Online" -> Color(0xFF6C9D61)
        "Offline" -> MaterialTheme.colorScheme.onSurfaceVariant
        "Warning" -> Color(0xFFB58B45)
        "Maintenance" -> Color(0xFF608A84)
        "Paused" -> Color(0xFFB58B45)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when (device.status) {
        "Online" -> Icons.Default.CheckCircle
        "Offline" -> Icons.Default.Cancel
        "Warning" -> Icons.Default.Warning
        "Maintenance" -> Icons.Default.Build
        "Paused" -> Icons.Default.PauseCircle
        else -> Icons.AutoMirrored.Filled.HelpOutline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Surface(shape = RoundedCornerShape(12.dp), color = statusColor.copy(alpha = 0.12f)) {
                Icon(Icons.Default.Computer, null, tint = statusColor, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = device.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )

                    device.groupName?.let { gName ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "$gName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // CPU & RAM
            if (device.status == "Paused") {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFF3E0)
                ) {
                    Text(
                        text = "Tạm dừng",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA87836),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (device.status == "Online" || device.status == "Warning") {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "CPU ${device.cpuUsage?.let { "%.0f%%".format(it) } ?: "--"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RAM ${device.ramUsage?.let { "%.0f%%".format(it) } ?: "--"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
