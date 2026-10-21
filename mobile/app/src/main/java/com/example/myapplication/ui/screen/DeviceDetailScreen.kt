package com.example.myapplication.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.model.*
import com.example.myapplication.ui.viewmodel.DeviceDetailUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    uiState: DeviceDetailUiState,
    deviceId: String,
    groups: List<com.example.myapplication.data.model.DeviceGroup> = emptyList(),
    onBack: () -> Unit,
    onTabSelect: (Int) -> Unit,
    onSendCommand: (String, String, Map<String, Any>?, String) -> Unit,
    onClearMessages: () -> Unit,
    onUpdateDeviceGroup: (String, Int?) -> Unit = { _, _ -> },
    onDeleteDevice: (() -> Unit)? = null
) {
    val tabs = listOf("Tổng quan", "Biểu đồ", "Tiến trình", "Lệnh")
    var showTopDeleteDialog by remember { mutableStateOf(false) }

    if (showTopDeleteDialog && onDeleteDevice != null) {
        AlertDialog(
            onDismissRequest = { showTopDeleteDialog = false },
            title = { Text("Xác nhận xóa thiết bị") },
            text = { Text("Bạn có chắc chắn muốn xóa thiết bị '${uiState.device?.name ?: deviceId}'? Thao tác này sẽ ngắt kết nối agent.") },
            confirmButton = {
                Button(
                    onClick = { showTopDeleteDialog = false; onDeleteDevice() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Xóa thiết bị") }
            },
            dismissButton = {
                TextButton(onClick = { showTopDeleteDialog = false }) { Text("Hủy") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.device?.name ?: "Chi tiết thiết bị", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    if (onDeleteDevice != null) {
                        IconButton(onClick = { showTopDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = uiState.selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { onTabSelect(index) },
                        text = { Text(title, maxLines = 1) }
                    )
                }
            }

            // Inline toast messages (same style as Org screen)
            uiState.commandSuccess?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearMessages, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            uiState.error?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFC62828))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(err, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearMessages, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (uiState.selectedTab) {
                0 -> OverviewTab(uiState.device, groups) { grpId -> onUpdateDeviceGroup(deviceId, grpId) }
                1 -> MetricsTab(uiState.metrics)
                2 -> ProcessesTab(uiState.processes, deviceId, onSendCommand)
                3 -> CommandsTab(uiState.commands, deviceId, onSendCommand)
            }
        }
    }
}

// ============================================================
// TAB 0: TỔNG QUAN
// ============================================================
@Composable
fun OverviewTab(
    device: DeviceDetail?,
    groups: List<com.example.myapplication.data.model.DeviceGroup> = emptyList(),
    onUpdateDeviceGroup: (Int?) -> Unit = {}
) {
    if (device == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Đang tải...") }
        return
    }

    val cpuTemp = device.cpuTempC ?: device.staticInfo?.cpuTempC
    val gpuName = device.gpuName ?: device.staticInfo?.gpuName
    val gpuUsage = device.gpuUsagePct ?: device.staticInfo?.gpuUsagePct
    val gpuTemp = device.gpuTempC ?: device.staticInfo?.gpuTempC
    val gpuVramTotal = device.gpuVramTotalMB ?: device.staticInfo?.gpuVramTotalMB
    val gpuVramUsed = device.gpuVramUsedMB ?: device.staticInfo?.gpuVramUsedMB

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status + uptime card
        item {
            val statusColor = when (device.status) {
                "Online" -> Color(0xFF4CAF50)
                "Warning" -> Color(0xFFFF9800)
                else -> Color.Gray
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(14.dp).background(statusColor, shape = RoundedCornerShape(7.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.status, fontWeight = FontWeight.Bold, color = statusColor)
                        device.lastHeartbeat?.let {
                            Text("Heartbeat: ${it.take(19).replace('T', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Uptime
                    device.uptimeSeconds?.let { uptime ->
                        val d = uptime / 86400; val h = (uptime % 86400) / 3600; val m = (uptime % 3600) / 60
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Uptime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${if (d > 0) "${d}d " else ""}${h}h ${m}m", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Cụm thiết bị card
        item {
            var showGroupDialog by remember { mutableStateOf(false) }
            var selectedGrpId by remember { mutableStateOf(device.groupId?.toIntOrNull()) }

            if (showGroupDialog) {
                AlertDialog(
                    onDismissRequest = { showGroupDialog = false },
                    title = { Text("Phân cụm thiết bị") },
                    text = {
                        Column {
                            Text("Gán máy này vào cụm để phân quyền cụ thể cho thành viên:", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGrpId = null }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedGrpId == null || selectedGrpId == 0,
                                    onClick = { selectedGrpId = null }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Chưa phân cụm (Mọi người đều thấy)")
                            }
                            groups.forEach { grp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedGrpId = grp.groupId }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedGrpId == grp.groupId,
                                        onClick = { selectedGrpId = grp.groupId }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("🏷️ ${grp.groupName}")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            showGroupDialog = false
                            onUpdateDeviceGroup(selectedGrpId)
                        }) { Text("Lưu thay đổi") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGroupDialog = false }) { Text("Hủy") }
                    }
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Cụm thiết bị", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(device.groupName ?: "Chưa phân cụm", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (groups.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                selectedGrpId = device.groupId?.toIntOrNull()
                                showGroupDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Đổi cụm", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Resource usage bars
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tài nguyên hiện tại", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    val cpuLabel = "CPU" + if (cpuTemp != null && cpuTemp > 0) " (${cpuTemp.roundToInt()}°C)" else ""
                    ResourceBar(label = cpuLabel, value = device.cpuUsage ?: 0f)
                    Spacer(modifier = Modifier.height(10.dp))
                    ResourceBar(label = "RAM", value = device.ramUsage ?: 0f)
                    Spacer(modifier = Modifier.height(10.dp))
                    ResourceBar(label = "Disk (C:)", value = device.diskUsage ?: 0f)

                    // GPU bar nếu có
                    if (gpuUsage != null && gpuUsage > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val gpuLabel = "GPU" +
                            (if (gpuTemp != null && gpuTemp > 0) " (${gpuTemp.roundToInt()}°C)" else "") +
                            (if (gpuVramUsed != null && gpuVramTotal != null)
                                " · VRAM $gpuVramUsed/$gpuVramTotal MB" else "")
                        ResourceBar(label = gpuLabel, value = gpuUsage)
                    }

                    // Network
                    if (device.netInKbps != null || device.netOutKbps != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ArrowDownward, null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                                Text("${device.netInKbps?.let { "%.1f".format(it) } ?: "--"} kbps", style = MaterialTheme.typography.bodySmall)
                                Text("Download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ArrowUpward, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Text("${device.netOutKbps?.let { "%.1f".format(it) } ?: "--"} kbps", style = MaterialTheme.typography.bodySmall)
                                Text("Upload", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // GPU info card
        if (!gpuName.isNullOrBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Card đồ họa (GPU)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        InfoRow("Tên GPU", gpuName)
                        gpuUsage?.let { InfoRow("GPU Usage", "%.1f%%".format(it)) }
                        gpuTemp?.let { if (it > 0) InfoRow("Nhiệt độ GPU", "${it.roundToInt()}°C") }
                        if (gpuVramTotal != null) {
                            val used = gpuVramUsed ?: 0
                            InfoRow("VRAM", "$used MB / $gpuVramTotal MB")
                        }
                    }
                }
            }
        }

        // Static hardware info
        device.staticInfo?.let { info ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thông tin phần cứng", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        InfoRow("Hostname", info.hostname ?: "--")
                        InfoRow("Hệ điều hành", "${info.osName ?: "--"} ${info.osVersion ?: ""}".trim())
                        info.kernelVersion?.let { InfoRow("Kernel", it) }
                        InfoRow("CPU", "${info.cpuModel ?: "--"} (${info.cpuCores ?: 0} cores)")
                        InfoRow("RAM", "${(info.ramTotalMb ?: 0) / 1024} GB (${info.ramTotalMb ?: 0} MB)")
                        InfoRow("Disk", "${info.diskTotalGb ?: 0} GB")
                        info.gpuName?.let { if (it.isNotBlank()) InfoRow("GPU", it) }
                        InfoRow("IP nội bộ", info.ipPrivate ?: "--")
                        InfoRow("IP công khai", info.ipPublic ?: "--")
                    }
                }
            }
        }
    }
}

@Composable
fun ResourceBar(label: String, value: Float) {
    val color = when {
        value >= 90f -> Color(0xFFF44336)
        value >= 70f -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
    val animatedValue by animateFloatAsState(targetValue = value / 100f, animationSpec = tween(600), label = "bar")

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "%.1f%%".format(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { animatedValue },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ============================================================
// TAB 1: BIỂU ĐỒ - LINE CHART đẹp hơn
// ============================================================
@Composable
fun MetricsTab(metrics: List<Metric>) {
    if (metrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BarChart, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Chưa có dữ liệu biểu đồ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LineChartCard(
                title = "CPU Usage",
                unit = "%",
                values = metrics.map { it.cpuUsage },
                color = Color(0xFF2196F3),
                maxY = 100f
            )
        }
        item {
            LineChartCard(
                title = "RAM Usage",
                unit = "%",
                values = metrics.map { it.ramUsage },
                color = Color(0xFF4CAF50),
                maxY = 100f
            )
        }
        item {
            LineChartCard(
                title = "Disk Usage",
                unit = "%",
                values = metrics.mapNotNull { it.diskUsage },
                color = Color(0xFFFF9800),
                maxY = 100f
            )
        }
        item {
            LineChartCard(
                title = "Network In",
                unit = " kbps",
                values = metrics.mapNotNull { it.netInKbps },
                color = Color(0xFF9C27B0)
            )
        }
        item {
            LineChartCard(
                title = "Network Out",
                unit = " kbps",
                values = metrics.mapNotNull { it.netOutKbps },
                color = Color(0xFFE91E63)
            )
        }
        val cpuTemps = metrics.mapNotNull { it.cpuTempC }.filter { it > 0 }
        if (cpuTemps.isNotEmpty()) {
            item {
                LineChartCard(
                    title = "Nhiệt độ CPU",
                    unit = "°C",
                    values = cpuTemps,
                    color = Color(0xFFF44336),
                    maxY = 105f
                )
            }
        }
        val gpuUsage = metrics.mapNotNull { it.gpuUsagePct }.filter { it > 0 }
        if (gpuUsage.isNotEmpty()) {
            item {
                LineChartCard(
                    title = "GPU Usage",
                    unit = "%",
                    values = gpuUsage,
                    color = Color(0xFF009688),
                    maxY = 100f
                )
            }
        }
    }
}

@Composable
fun LineChartCard(
    title: String,
    unit: String,
    values: List<Float>,
    color: Color,
    maxY: Float? = null
) {
    if (values.isEmpty()) return

    val actualMax = maxY ?: (values.max() * 1.2f).coerceAtLeast(1f)
    val minVal = values.min()
    val maxVal = values.max()
    val avgVal = values.average().toFloat()
    val lastVal = values.last()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                // Current value badge
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "%.1f%s".format(lastVal, unit),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Line Chart using Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val w = size.width
                val h = size.height
                val padding = 8f
                val chartW = w - padding * 2
                val chartH = h - padding * 2

                if (values.size < 2) return@Canvas

                // Grid lines (3 horizontal dashed lines)
                val gridColor = Color.Gray.copy(alpha = 0.2f)
                for (i in 1..3) {
                    val y = padding + chartH * (1f - i / 4f)
                    drawLine(gridColor, Offset(padding, y), Offset(w - padding, y), strokeWidth = 1f)
                }

                // Fill path (area under curve)
                val fillPath = Path()
                val step = chartW / (values.size - 1)
                val firstY = padding + chartH * (1f - (values.first() / actualMax).coerceIn(0f, 1f))
                fillPath.moveTo(padding, h - padding)
                fillPath.lineTo(padding, firstY)
                values.forEachIndexed { i, v ->
                    val x = padding + i * step
                    val y = padding + chartH * (1f - (v / actualMax).coerceIn(0f, 1f))
                    if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
                }
                fillPath.lineTo(padding + (values.size - 1) * step, h - padding)
                fillPath.close()
                drawPath(fillPath, color.copy(alpha = 0.12f))

                // Line
                val linePath = Path()
                values.forEachIndexed { i, v ->
                    val x = padding + i * step
                    val y = padding + chartH * (1f - (v / actualMax).coerceIn(0f, 1f))
                    if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                drawPath(linePath, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

                // Last point dot
                val lastX = padding + (values.size - 1) * step
                val lastY = padding + chartH * (1f - (values.last() / actualMax).coerceIn(0f, 1f))
                drawCircle(color, radius = 5f, center = Offset(lastX, lastY))
                drawCircle(Color.White, radius = 3f, center = Offset(lastX, lastY))
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatLabel("Min", "%.1f%s".format(minVal, unit), Color.Gray)
                StatLabel("TB", "%.1f%s".format(avgVal, unit), color)
                StatLabel("Max", "%.1f%s".format(maxVal, unit), if (maxY != null && maxVal >= maxY * 0.9f) Color(0xFFF44336) else Color.Gray)
                StatLabel("Điểm", "${values.size}", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatLabel(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

// ============================================================
// TAB 2: TIẾN TRÌNH — Fix kill dùng PID chính xác
// ============================================================
@Composable
fun ProcessesTab(
    processes: List<ProcessInfo>,
    deviceId: String,
    onSendCommand: (String, String, Map<String, Any>?, String) -> Unit
) {
    var showKillDialog by remember { mutableStateOf<ProcessInfo?>(null) }
    val killPresets = listOf(
        "Chiếm dụng CPU quá cao (>80%)",
        "Tràn bộ nhớ RAM",
        "Tiến trình bị treo (Not Responding)",
        "Nghi ngờ tiến trình lạ / độc hại",
        "Lý do khác..."
    )
    var selectedKillPreset by remember { mutableStateOf(killPresets.first()) }
    var customKillReason by remember { mutableStateOf("") }

    showKillDialog?.let { process ->
        val finalReason = if (selectedKillPreset == killPresets.last()) customKillReason.trim() else selectedKillPreset
        AlertDialog(
            onDismissRequest = { showKillDialog = null; customKillReason = ""; selectedKillPreset = killPresets.first() },
            title = { Text("Dừng tiến trình") },
            text = {
                Column {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Tiến trình: ${process.name}", fontWeight = FontWeight.Bold)
                            Text("PID: ${process.pid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("CPU: %.1f%% · RAM: %.0f MB".format(process.cpuPercent, process.ramMb), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Chọn lý do:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    ReasonSelector(
                        presets = killPresets,
                        selectedPreset = selectedKillPreset,
                        onPresetSelected = { selectedKillPreset = it },
                        customReason = customKillReason,
                        onCustomReasonChange = { customKillReason = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // FIX: Chỉ truyền PID (integer) — không truyền process_name để tránh kill nhầm
                        onSendCommand(
                            deviceId,
                            "KILL_PROCESS",
                            mapOf("pid" to process.pid.toLong()),
                            finalReason
                        )
                        showKillDialog = null; customKillReason = ""; selectedKillPreset = killPresets.first()
                    },
                    enabled = finalReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Dừng tiến trình") }
            },
            dismissButton = {
                TextButton(onClick = { showKillDialog = null; customKillReason = ""; selectedKillPreset = killPresets.first() }) { Text("Hủy") }
            }
        )
    }

    if (processes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Chưa có dữ liệu tiến trình") }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp)) {
                Text("PID", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.14f), fontWeight = FontWeight.Bold)
                Text("Tên", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.38f), fontWeight = FontWeight.Bold)
                Text("CPU%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.18f), fontWeight = FontWeight.Bold)
                Text("RAM", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(0.1f))
            }
            HorizontalDivider()
        }

        items(processes.sortedByDescending { it.cpuPercent }) { process ->
            val cpuHigh = process.cpuPercent > 50f
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (cpuHigh) Color(0xFFF44336).copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(process.pid.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.14f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(process.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.38f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "%.1f%%".format(process.cpuPercent),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.18f),
                        color = if (cpuHigh) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (cpuHigh) FontWeight.Bold else FontWeight.Normal
                    )
                    Text("%.0f MB".format(process.ramMb), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.2f))
                    IconButton(onClick = { showKillDialog = process }, modifier = Modifier.weight(0.1f).size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dừng", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

// ============================================================
// TAB 3: LỆNH
// ============================================================
@Composable
fun CommandsTab(
    commands: List<Command>,
    deviceId: String,
    onSendCommand: (String, String, Map<String, Any>?, String) -> Unit
) {
    var showRebootDialog by remember { mutableStateOf(false) }
    val rebootPresets = listOf(
        "Bảo trì định kỳ",
        "Hệ thống bị đơ / lag",
        "Cập nhật phần mềm hệ điều hành",
        "Khởi động lại sau cấu hình",
        "Lý do khác..."
    )
    var selectedRebootPreset by remember { mutableStateOf(rebootPresets.first()) }
    var customRebootReason by remember { mutableStateOf("") }

    if (showRebootDialog) {
        val finalReason = if (selectedRebootPreset == rebootPresets.last()) customRebootReason.trim() else selectedRebootPreset
        AlertDialog(
            onDismissRequest = { showRebootDialog = false; customRebootReason = ""; selectedRebootPreset = rebootPresets.first() },
            title = { Text("Khởi động lại máy") },
            text = {
                Column {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Máy tính sẽ khởi động lại sau 10 giây. Dữ liệu chưa lưu có thể bị mất.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Lý do:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    ReasonSelector(
                        presets = rebootPresets,
                        selectedPreset = selectedRebootPreset,
                        onPresetSelected = { selectedRebootPreset = it },
                        customReason = customRebootReason,
                        onCustomReasonChange = { customRebootReason = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // FIX: Thêm delay_seconds tường minh để executor.go nhận đúng param
                        onSendCommand(deviceId, "REBOOT", mapOf("delay_seconds" to 10), finalReason)
                        showRebootDialog = false; customRebootReason = ""; selectedRebootPreset = rebootPresets.first()
                    },
                    enabled = finalReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) { Text("Khởi động lại") }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false; customRebootReason = ""; selectedRebootPreset = rebootPresets.first() }) { Text("Hủy") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Button(
                onClick = { showRebootDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Khởi động lại máy")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Lịch sử lệnh", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        if (commands.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Chưa có lệnh nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(commands) { cmd ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    val (icon, iconColor) = when (cmd.status) {
                        "SUCCESS" -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
                        "FAILED" -> Icons.Default.Cancel to Color(0xFFF44336)
                        "PENDING" -> Icons.Default.HourglassTop to Color(0xFFFF9800)
                        else -> Icons.AutoMirrored.Filled.HelpOutline to Color.Gray
                    }
                    Icon(icon, null, tint = iconColor)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cmd.commandType, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        cmd.reason?.let { Text("Lý do: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        cmd.createdAt?.let { Text(it.take(19).replace('T', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        cmd.output?.let { if (it.isNotBlank()) Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        cmd.errorMessage?.let { Text("Lỗi: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336), maxLines = 2) }
                    }
                }
            }
        }
    }
}

// ============================================================
// SHARED: ReasonSelector
// ============================================================
@Composable
fun ReasonSelector(
    presets: List<String>,
    selectedPreset: String,
    onPresetSelected: (String) -> Unit,
    customReason: String,
    onCustomReasonChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { preset ->
            Surface(
                onClick = { onPresetSelected(preset) },
                shape = MaterialTheme.shapes.small,
                color = if (selectedPreset == preset) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                tonalElevation = if (selectedPreset == preset) 2.dp else 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedPreset == preset,
                        onClick = { onPresetSelected(preset) },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = preset,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selectedPreset == preset) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // Nếu chọn mục cuối ("Lý do khác..."), hiển thị ô nhập liệu tự do
        if (selectedPreset == presets.last()) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = customReason,
                onValueChange = onCustomReasonChange,
                label = { Text("Ghi rõ lý do cụ thể (bắt buộc)") },
                placeholder = { Text("Nhập lý do...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
