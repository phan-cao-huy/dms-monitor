package com.example.myapplication.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.luminance
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
    alerts: List<Alert> = emptyList(),
    deviceId: String,
    groups: List<com.example.myapplication.data.model.DeviceGroup> = emptyList(),
    onBack: () -> Unit,
    onTabSelect: (Int) -> Unit,
    onSendCommand: (String, String, Map<String, Any>?, String) -> Unit,
    onClearMessages: () -> Unit,
    onUpdateDeviceGroup: (String, Int?) -> Unit = { _, _ -> },
    onToggleMonitoring: ((String, Boolean?) -> Unit)? = null,
    onDeleteDevice: (() -> Unit)? = null
) {
    val tabs = listOf("Tổng quan", "Biểu đồ", "Tiến trình", "Lệnh")
    var showTopDeleteDialog by remember { mutableStateOf(false) }

    if (showTopDeleteDialog && onDeleteDevice != null) {
        AlertDialog(
            onDismissRequest = { showTopDeleteDialog = false },
            title = { Text("Xác nhận gỡ cài đặt & xóa PC") },
            text = {
                Text(
                    "Bạn có chắc chắn muốn xóa thiết bị '${uiState.device?.name ?: deviceId}'?\n\n" +
                    "⚠️ CẢNH BÁO: Hệ thống sẽ tự động gửi lệnh gỡ cài đặt (UNINSTALL) đến máy tính để dừng Windows Service và xóa sạch toàn bộ thư mục C:\\DMS-Agent, logs và cấu hình trên máy tính."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showTopDeleteDialog = false; onDeleteDevice() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Xóa & gỡ sạch file") }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                0 -> OverviewTab(
                    device = uiState.device,
                    metrics = uiState.metrics,
                    alerts = alerts,
                    onViewMetrics = { onTabSelect(1) },
                    onViewProcesses = { onTabSelect(2) },
                    groups = groups,
                    onToggleMonitoring = { enable -> onToggleMonitoring?.invoke(deviceId, enable) },
                    onUpdateDeviceGroup = { grpId -> onUpdateDeviceGroup(deviceId, grpId) }
                )
                1 -> MetricsTab(uiState.metrics)
                2 -> ProcessesTab(
                    processes = uiState.processes,
                    totalRamMb = uiState.device?.staticInfo?.ramTotalMb,
                    deviceId = deviceId,
                    isMonitoring = uiState.device?.isMonitoring ?: true,
                    onSendCommand = onSendCommand
                )
                3 -> CommandsTab(uiState.commands, deviceId, onSendCommand)
            }
        }
    }
}

// ============================================================
// TAB 0: TỔNG QUAN
// ============================================================
@Composable
fun ResourceBar(label: String, value: Float) {
    val color = when {
        value >= 90f -> Color(0xFFBD6254)
        value >= 70f -> Color(0xFFB58B45)
        else -> Color(0xFF6C9D61)
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
                color = Color(0xFF608A84),
                maxY = 100f
            )
        }
        item {
            LineChartCard(
                title = "RAM Usage",
                unit = "%",
                values = metrics.map { it.ramUsage },
                color = Color(0xFF6C9D61),
                maxY = 100f
            )
        }
        item {
            LineChartCard(
                title = "Disk Usage",
                unit = "%",
                values = metrics.mapNotNull { it.diskUsage },
                color = Color(0xFFB58B45),
                maxY = 100f
            )
        }
        item {
            LineChartCard(
                title = "Network In",
                unit = " kbps",
                values = metrics.mapNotNull { it.netInKbps },
                color = Color(0xFF8A7896)
            )
        }
        item {
            LineChartCard(
                title = "Network Out",
                unit = " kbps",
                values = metrics.mapNotNull { it.netOutKbps },
                color = Color(0xFFB47781)
            )
        }
        val cpuTemps = metrics.mapNotNull { it.cpuTempC }.filter { it > 0 }
        if (cpuTemps.isNotEmpty()) {
            item {
                LineChartCard(
                    title = "Nhiệt độ CPU",
                    unit = "°C",
                    values = cpuTemps,
                    color = Color(0xFFBD6254),
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
                    color = Color(0xFF608A84),
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
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

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
                StatLabel("Min", "%.1f%s".format(minVal, unit), MaterialTheme.colorScheme.onSurfaceVariant)
                StatLabel("TB", "%.1f%s".format(avgVal, unit), color)
                StatLabel("Max", "%.1f%s".format(maxVal, unit), if (maxY != null && maxVal >= maxY * 0.9f) Color(0xFFBD6254) else MaterialTheme.colorScheme.onSurfaceVariant)
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
    totalRamMb: Int? = null,
    deviceId: String,
    isMonitoring: Boolean = true,
    onSendCommand: (String, String, Map<String, Any>?, String) -> Unit
) {
    var showKillDialog by remember { mutableStateOf<ProcessInfo?>(null) }
    val en=com.example.myapplication.ui.i18n.LocalAppStrings.current.isEnglish
    var sortBy by remember { mutableStateOf("CPU") }
    var selectedPid by remember { mutableStateOf<Int?>(null) }
    var stopWholeApplication by remember { mutableStateOf(false) }
    val gpuAvailable=processes.any{it.gpuUsagePct!=null}

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
            onDismissRequest = { showKillDialog = null; stopWholeApplication=false; customKillReason = ""; selectedKillPreset = killPresets.first() },
            title = { Text("Dừng tiến trình") },
            text = {
                Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState())) {
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
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(stopWholeApplication,{stopWholeApplication=it})
                        Text(if(en)"Stop every ${process.name} process on this PC" else "Dừng tất cả tiến trình ${process.name} trên máy")
                    }
                    if(stopWholeApplication)Text(
                        if(en)"This closes all matching processes and windows on this PC, including other sessions. Unsaved work may be lost."
                        else "Đóng mọi tiến trình và cửa sổ cùng tên trên máy, kể cả phiên người dùng khác. Dữ liệu chưa lưu có thể bị mất.",
                        color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
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
                        // Keep PID scope by default; whole-app scope requires explicit selection.
                        onSendCommand(
                            deviceId,
                            "KILL_PROCESS",
                            if(stopWholeApplication)mapOf("process_name" to process.name) else mapOf("pid" to process.pid.toLong()),
                            finalReason
                        )
                        showKillDialog = null; stopWholeApplication=false; customKillReason = ""; selectedKillPreset = killPresets.first()
                    },
                    enabled = finalReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if(stopWholeApplication)if(en)"Stop entire application" else "Đóng toàn bộ ứng dụng" else if(en)"Stop selected PID" else "Dừng PID đã chọn") }
            },
            dismissButton = {
                TextButton(onClick = { showKillDialog = null; stopWholeApplication=false; customKillReason = ""; selectedKillPreset = killPresets.first() }) { Text("Hủy") }
            }
        )
    }

    if (processes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(
                    if (!isMonitoring) Icons.Default.PauseCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (!isMonitoring) Color(0xFFA87836) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (!isMonitoring)
                        "Theo dõi tiến trình đang tạm dừng trên máy tính này.\nHãy bật công tắc theo dõi ở tab Tổng quan để cập nhật lại."
                    else
                        "Chưa có dữ liệu tiến trình từ máy tính.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    val sorted=processes.sortedWith(compareByDescending<ProcessInfo>{
        when(sortBy){"RAM"->it.ramMb;"GPU"->it.gpuUsagePct?:-1f;else->it.cpuPercent}
    }.thenBy{it.pid})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{
            Text(if(en)"Resource usage ranking" else "Tiến trình dùng tài nguyên nhiều nhất",style=MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("CPU","RAM","GPU").forEach{key->
                    FilterChip(selected=sortBy==key,onClick={sortBy=key},enabled=key!="GPU"||gpuAvailable,
                        label={Text(key+if(sortBy==key)" ↓" else "")})
                }
            }
            Text(if(en)"Green: low · Amber: moderate · Red: high. Tap a row for details." else "Xanh: thấp · Vàng: vừa · Đỏ: cao. Chạm hàng để xem chi tiết.",style=MaterialTheme.typography.bodySmall)
            if(!gpuAvailable)Text(if(en)"Per-process GPU requires the new Windows Agent and supported GPU counters." else "GPU theo tiến trình cần Agent Windows mới và bộ đếm GPU được hỗ trợ.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(sorted,key={it.pid}){process->
            val interaction=remember{MutableInteractionSource()}
            val hovered by interaction.collectIsHoveredAsState()
            val selected=selectedPid==process.pid
            OutlinedCard(
                modifier=Modifier.fillMaxWidth().hoverable(interaction).clickable{selectedPid=if(selected)null else process.pid},
                border=BorderStroke(if(selected||hovered)2.dp else 1.dp,if(selected||hovered)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                colors=CardDefaults.outlinedCardColors(containerColor=if(selected||hovered)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)){
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(process.name,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text("PID ${process.pid}",style=MaterialTheme.typography.labelSmall)
                        }
                        if(isMonitoring)IconButton(onClick={stopWholeApplication=false;showKillDialog=process}){
                            Icon(Icons.Default.Close,contentDescription=if(en)"Stop process" else "Dừng tiến trình",tint=MaterialTheme.colorScheme.error)}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        ProcessHeat("CPU","%.1f%%".format(process.cpuPercent),process.cpuPercent,20f,50f,Modifier.weight(1f))
                        val share=totalRamMb?.takeIf{it>0}?.let{process.ramMb/it*100}
                        ProcessHeat("RAM","%.0f MB".format(process.ramMb),share,10f,25f,Modifier.weight(1f))
                        ProcessHeat("GPU",process.gpuUsagePct?.let{"%.1f%%".format(it)}?:"—",process.gpuUsagePct,40f,80f,Modifier.weight(1f))
                    }
                    if(selected){
                        Text((if(en)"User: " else "Người dùng: ")+(process.user?:"—"),style=MaterialTheme.typography.bodySmall)
                        Text(process.exePath?:if(en)"Executable path unavailable" else "Chưa có đường dẫn chương trình",style=MaterialTheme.typography.bodySmall)
                        if(totalRamMb!=null&&totalRamMb>0)Text((if(en)"Share of system RAM: " else "Chiếm RAM toàn máy: ")+"%.1f%%".format(process.ramMb/totalRamMb*100),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessHeat(label:String,text:String,value:Float?,warm:Float,hot:Float,modifier:Modifier){
    val dark=MaterialTheme.colorScheme.surface.luminance()<0.2f
    val color=when{value==null->MaterialTheme.colorScheme.onSurfaceVariant;value>=hot->if(dark)Color(0xFFF1AAA0) else Color(0xFFB7473D);value>=warm->if(dark)Color(0xFFEFCE83) else Color(0xFFA57420);else->if(dark)Color(0xFFA4D6A0) else Color(0xFF35764B)}
    Surface(modifier,shape=RoundedCornerShape(10.dp),color=color.copy(alpha=if(value==null).04f else .08f+value.coerceIn(0f,100f)/100*.20f)){
        Column(Modifier.padding(9.dp)){
            Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurface)
            Text(text,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,color=if(value!=null)color else MaterialTheme.colorScheme.onSurfaceVariant)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBD6254))
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBD6254)),
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
                        "SUCCESS" -> Icons.Default.CheckCircle to Color(0xFF6C9D61)
                        "FAILED" -> Icons.Default.Cancel to Color(0xFFBD6254)
                        "PENDING" -> Icons.Default.HourglassTop to Color(0xFFB58B45)
                        else -> Icons.AutoMirrored.Filled.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(icon, null, tint = iconColor)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cmd.commandType, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        cmd.reason?.let { Text("Lý do: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        cmd.createdAt?.let { Text(it.take(19).replace('T', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        cmd.output?.let { if (it.isNotBlank()) Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6C9D61), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        cmd.errorMessage?.let { Text("Lỗi: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFBD6254), maxLines = 2) }
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
