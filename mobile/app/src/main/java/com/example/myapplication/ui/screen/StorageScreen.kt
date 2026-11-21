package com.example.myapplication.ui.screen

import kotlinx.coroutines.launch
import com.example.myapplication.data.model.RetentionPreview
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.StorageSummary

@Composable internal fun StorageScreen(orgId: String) {
    var data by remember(orgId) { mutableStateOf<StorageSummary?>(null) }
    val status = remember(orgId) { FeatureStatus() }
    val context = LocalContext.current
    RefreshFeature(orgId) { status.refresh { data = RetrofitClient.apiService.storage(orgId).required() } }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(featureText("Toàn máy chủ DMS", "DMS server storage"), style = MaterialTheme.typography.titleLarge)
        FeatureMessages(status)
        data?.let { snapshot ->
            Text(featureText("Database đang dùng", "Database size") + " · " + Formatter.formatFileSize(context, snapshot.database_bytes))
            Text(featureText("Ổ chứa database còn trống", "Database disk free") + " · " +
                (snapshot.free_bytes?.let { Formatter.formatFileSize(context, it) } ?: featureText("Chưa đo được","Unavailable")))
            val result = when(snapshot.cleanup?.status) {
                "guarded" -> featureText("Đã giữ lại dữ liệu do chưa xác nhận đủ bản tổng hợp","Data retained because summaries could not be verified")
                "success" -> featureText("Thành công","Succeeded")
                "failed" -> featureText("Thất bại","Failed")
                "started" -> featureText("Đã bắt đầu","Started")
                else -> featureText("Chưa ghi nhận","No recorded run")
            }
            Text(featureText("Lần dọn gần nhất","Last cleanup") + " · " + result)
            snapshot.cleanup?.let { Text(it.updated_at) }
            AlertRetention(orgId, snapshot.alert_days) {
                data = RetrofitClient.apiService.storage(orgId).required()
            }
            snapshot.groups.forEach { group ->
                val name = when(group.kind) {
                    "raw" -> featureText("Mẫu hiệu năng gốc","Raw metrics")
                    "minute" -> featureText("Tổng hợp theo phút","Minute summaries")
                    "hour" -> featureText("Tổng hợp theo giờ","Hourly summaries")
                    "events" -> featureText("Nhật ký thiết bị","Device events")
                    "checks" -> featureText("Kết quả kiểm tra dịch vụ","Service check results")
                    "alerts" -> featureText("Lịch sử cảnh báo","Alert history")
                    else -> featureText("Các bảng khác","Other tables")
                }
                HorizontalDivider()
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(Formatter.formatFileSize(context,group.bytes) + (group.days?.let { " · " + featureText("Giữ $it ngày","Retained $it days") } ?: ""))
                group.days?.let { current ->
                    key(orgId,group.kind) { RetentionImpact(orgId,group.kind,current) }
                }
            }
        }
    }
}

@Composable private fun RetentionImpact(orgId: String, kind: String, current: Int) {
    val scope = rememberCoroutineScope()
    val status = remember { FeatureStatus() }
    var open by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(current.toString()) }
    var preview by remember { mutableStateOf<RetentionPreview?>(null) }
    TextButton(onClick={ open = !open }) { Text(featureText("Xem tác động","Preview impact")) }
    if (open) {
        OutlinedTextField(days, { days=it; preview=null }, enabled=!status.busy, singleLine=true,
            label={Text(featureText("Số ngày muốn giữ","Days to retain"))})
        Button(enabled=!status.busy && days.toIntOrNull() in 1..3650, onClick={
            scope.launch { status.perform {
                preview=RetrofitClient.apiService.previewRetention(orgId,kind,days.toInt()).required()
            } }
        }) { Text(featureText("Tính cho không gian này","Calculate for this workspace")) }
        FeatureMessages(status)
        preview?.let { result ->
            Text(featureText("Bản ghi ngoài thời hạn","Records outside retention") + " · " + result.records)
            if(result.oldest!=null) Text(result.oldest + " – " + result.newest + " UTC")
            Text(featureText(result.impact_vi,result.impact_en))
        }
    }
}

@Composable private fun AlertRetention(orgId: String, current: Int, reload: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    val status = remember { FeatureStatus() }
    var input by remember(current) { mutableStateOf(current.toString()) }
    var impact by remember(current) { mutableStateOf<Long?>(null) }
    val days = input.toIntOrNull()
    HorizontalDivider()
    Text(featureText("Lịch sử cảnh báo của không gian","Workspace alert history"), style=MaterialTheme.typography.titleMedium)
    Text(if(current==0) featureText("Đang giữ không thời hạn","Currently kept indefinitely")
         else featureText("Đang giữ $current ngày sau khi xử lý","Currently kept for $current days after resolution"))
    OutlinedTextField(input,{input=it; impact=null},enabled=!status.busy,singleLine=true,
        label={Text(featureText("Số ngày sau khi xử lý (0 = giữ mãi)","Days after resolution (0 = keep forever)"))})
    Button(enabled=!status.busy && days in 0..3650 && days!=current,onClick={
        scope.launch { status.perform {
            val selected = days ?: return@perform
            impact=if(selected==0) 0L else RetrofitClient.apiService.previewRetention(orgId,"alerts",selected).required().records
        } }
    }) { Text(featureText("Xem trước","Preview")) }
    FeatureMessages(status)
    impact?.let { count ->
        Text(if(days==0) featureText("Giữ toàn bộ lịch sử.","Keep all alert history.")
             else featureText("$count cảnh báo đã xử lý đủ điều kiện xóa ở lượt dọn theo lịch.","$count resolved alerts currently qualify for scheduled removal."))
        TextButton(enabled=!status.busy,onClick={scope.launch { status.perform {
            RetrofitClient.apiService.saveAlertRetention(orgId,days ?: return@perform).required()
            impact=null
            reload()
        } }}) { Text(featureText("Lưu chính sách","Save policy")) }
    }
}
