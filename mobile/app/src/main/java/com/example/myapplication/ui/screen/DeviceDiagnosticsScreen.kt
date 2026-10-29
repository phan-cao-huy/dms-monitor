package com.example.myapplication.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.*
import com.example.myapplication.util.MonitoringSupport
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch

@Composable internal fun DeviceHealthCard(device:DeviceDetail) {
    val info=device.staticInfo
    val sampled=MonitoringSupport.instant(info?.t2CollectedAt)
    val stale=device.status in listOf("Offline","Paused")||sampled?.let{Duration.between(it,Instant.now()).seconds>120}==true
    OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(featureText("Sức khỏe ổ đĩa vật lý","Physical disk health"),style=MaterialTheme.typography.titleMedium)
        Text(featureText("Tách biệt với dung lượng phân vùng.","Separate from partition space usage."),style=MaterialTheme.typography.bodySmall)
        if(stale)Text(featureText("Mẫu đã cũ, thiết bị ngoại tuyến hoặc tạm dừng.","Snapshot is stale, offline or paused."),color=MaterialTheme.colorScheme.error)
        if(info?.disksHealth.isNullOrEmpty())Text(featureText("Chưa đọc được. Cần Agent 1.3.0 và driver/phần cứng hỗ trợ; không có dữ liệu không có nghĩa ổ bị lỗi.","Unavailable. Requires Agent 1.3.0 and supported hardware/driver; no data does not mean disk failure."))
        info?.disksHealth.orEmpty().forEach{disk->
            HorizontalDivider();Text(disk.name,style=MaterialTheme.typography.titleSmall)
            Text(when(disk.healthy){true->featureText("Bình thường","Healthy");false->featureText("Có cảnh báo / lỗi","Warning / unhealthy");null->featureText("Không xác định","Unknown")},color=if(disk.healthy==false)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(featureText("Nhiệt độ: ","Temperature: ")+(disk.tempC?.let{featureDecimal(it)+" °C"}?:featureText("Không đọc được","Unavailable")))
            Text(featureText("Độ hao mòn: ","Wear: ")+(disk.wearPct?.let{featureDecimal(it)+"%"}?:featureText("Không đọc được","Unavailable")))
            Text(disk.source?:"—",style=MaterialTheme.typography.bodySmall)
        }
        Text(featureText("Mẫu gần nhất: ","Last snapshot: ")+MonitoringSupport.time(info?.t2CollectedAt),style=MaterialTheme.typography.bodySmall)
    }}
}

@Composable internal fun DeviceConnectionsScreen(device:DeviceDetail?) {
    val rows=device?.staticInfo?.connections
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text(featureText("Kết nối mạng theo tiến trình","Process network connections"),style=MaterialTheme.typography.headlineSmall)
            Text(featureText("Snapshot tối đa 50 kết nối ESTABLISHED/LISTEN; không đo băng thông theo tiến trình.","Snapshot of up to 50 ESTABLISHED/LISTEN connections; not per-process bandwidth."))
            if(device?.status in listOf("Offline","Paused"))Text(featureText("Thiết bị ngoại tuyến/tạm dừng: đây là dữ liệu cũ.","Device offline/paused: this is an old snapshot."),color=MaterialTheme.colorScheme.error)
            Text(MonitoringSupport.time(device?.staticInfo?.t2CollectedAt))
            if(rows.isNullOrEmpty())Text(if(rows==null)featureText("Chưa có dữ liệu. Cần Agent 1.3.0 và quyền đọc kết nối.","Unavailable. Requires Agent 1.3.0 and connection access.") else featureText("Không có kết nối phù hợp trong mẫu này.","No matching connections in this snapshot."))
        }
        items(rows.orEmpty()){row->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text((row.process?.takeIf{it.isNotBlank()}?:featureText("Không đọc được tên","Name unavailable"))+" · PID ${row.pid}",style=MaterialTheme.typography.titleSmall)
            Text(row.status,color=MaterialTheme.colorScheme.primary)
            Text(featureText("Máy: ","Local: ")+row.laddr)
            Text(featureText("Đích: ","Remote: ")+row.raddr)
        }}}
    }
}

@Composable internal fun DeviceEventsScreen(deviceId:String,device:DeviceDetail?) {
    key(deviceId){DeviceEventsContent(deviceId,device)}
}

@Composable private fun DeviceEventsContent(deviceId:String,device:DeviceDetail?) {
    var level by remember{mutableStateOf("")}
    var before by remember{mutableStateOf<Long?>(null)}
    var page by remember{mutableStateOf<DeviceEventPage?>(null)}
    val status=remember{FeatureStatus()}
    val scope=rememberCoroutineScope()
    suspend fun reload(){page=RetrofitClient.apiService.deviceEvents(deviceId,level.takeIf{it.isNotBlank()},before).required()}
    RefreshFeature(Triple(deviceId,level,before),!status.busy){status.refresh{reload()}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text(featureText("Nhật ký Windows","Windows events"),style=MaterialTheme.typography.headlineSmall)
            Text(featureText("System/Application: lỗi, nghiêm trọng. Security: đăng nhập thất bại 4625. Agent bắt đầu từ sự kiện mới; giữ 7 ngày mặc định.","System/Application: Error/Critical. Security: failed sign-in 4625. Agent starts with new events; default retention is 7 days."))
            device?.staticInfo?.eventLogStatus?.forEach{(log,state)->Text(log+": "+when(state){"OK"->featureText("Đọc được","Readable");"ACCESS_DENIED"->featureText("Thiếu quyền","Access denied");else->featureText("Không đọc được","Unavailable")},style=MaterialTheme.typography.bodySmall)}
            FeatureChoice(featureText("Mức độ","Level"),level,listOf("" to featureText("Tất cả","All"),"ERROR" to featureText("Lỗi","Error"),"CRITICAL" to featureText("Nghiêm trọng","Critical"),"AUDIT_FAILURE" to featureText("Đăng nhập thất bại","Failed sign-in")),{level=it;before=null;page=null})
            Row{TextButton(onClick={before=null;page=null;scope.launch{status.perform{reload()}}},enabled=!status.busy){Text(featureText("Mới nhất","Latest"))}
                page?.nextBefore?.let{cursor->TextButton(onClick={before=cursor;page=null},enabled=!status.busy){Text(featureText("Cũ hơn","Older"))}}}
            FeatureMessages(status)
            if(page?.items?.isEmpty()==true)Text(featureText("Không có sự kiện trong lựa chọn này.","No events in this selection."))
        }
        items(page?.items.orEmpty(),key={it.id}){row->
            var expanded by remember(row.id){mutableStateOf(false)}
            OutlinedCard(Modifier.fillMaxWidth().clickable{expanded=!expanded}){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(row.source+" · ${row.eventId}",style=MaterialTheme.typography.titleSmall)
                Text(row.logName+" · "+row.level+" · Record ${row.recordId}",color=if(row.level=="CRITICAL")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(MonitoringSupport.time(row.occurredAt),style=MaterialTheme.typography.bodySmall)
                Text(row.message,maxLines=if(expanded)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis)
                Text(featureText("Nhấn để mở rộng/thu gọn","Tap to expand/collapse"),style=MaterialTheme.typography.labelSmall)
            }}
        }
    }
}

@Composable internal fun DeviceLabelsScreen(deviceId:String,canEdit:Boolean,onChanged:()->Unit,device:DeviceDetail?=null) {
    key(deviceId){DeviceLabelsContent(deviceId,canEdit,onChanged,device)}
}

@Composable private fun DeviceLabelsContent(deviceId:String,canEdit:Boolean,onChanged:()->Unit,device:DeviceDetail?) {
    val api=RetrofitClient.apiService
    val status=remember{FeatureStatus()}
    val scope=rememberCoroutineScope()
    var offlineEdit by remember{mutableStateOf(false)}
    var labels by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    var create by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<String?>(null)}
    var deleting by remember{mutableStateOf<String?>(null)}
    suspend fun reload(){labels=api.deviceLabels(deviceId).required()}
    RefreshFeature(deviceId,!status.busy&&!create&&editing==null&&deleting==null&&!offlineEdit){status.refresh{reload()}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text(featureText("Nhãn thiết bị","Device labels"),style=MaterialTheme.typography.headlineSmall)
            Text(featureText("Tối đa 20 nhãn. Dùng để lọc và so sánh tài nguyên theo môi trường, vị trí hoặc vai trò.","Up to 20 labels. Filter and compare resources by environment, location or role."))
            FeatureMessages(status)
            Text(featureText("Ngưỡng mất kết nối: ","Offline threshold: ")+(device?.offlineThresholdMinutes?.toString()?:"—")+featureText(" phút"," minutes"))
            if(canEdit)TextButton(onClick={status.error=null;offlineEdit=true},enabled=!status.busy&&device!=null){Text(featureText("Chỉnh ngưỡng mất kết nối","Edit offline threshold"))}
            if(canEdit)Button(onClick={status.error=null;create=true},enabled=!status.busy&&labels.size<20){Text(featureText("Thêm nhãn","Add label"))}
            if(!canEdit)Text(featureText("Chỉ xem. Cần quyền Owner/Admin hoặc Manager của thiết bị để chỉnh sửa.","Read-only. Requires device Owner/Admin or Manager access to edit."))
            if(labels.isEmpty())Text(featureText("Chưa có nhãn.","No labels yet."))
        }
        items(labels.toList(),key={it.first}){(key,value)->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
            Text(key,style=MaterialTheme.typography.titleMedium);Text(value)
            if(canEdit)Row{TextButton(onClick={status.error=null;editing=key},enabled=!status.busy){Text(featureText("Sửa","Edit"))};TextButton(onClick={deleting=key},enabled=!status.busy){Text(featureText("Xóa","Delete"))}}
        }}}
    }
    if(create||editing!=null)key(editing,create){
        var key by remember{mutableStateOf(editing?:"")}
        var value by remember{mutableStateOf(editing?.let{labels[it]}?:"")}
        val labelKey=MonitoringSupport.normalizeLabelKey(key)
        val keyInvalid=!MonitoringSupport.validLabel(labelKey,"")
        val valueInvalid=!MonitoringSupport.validLabel("env",value)
        val duplicate=create&&labels.containsKey(labelKey)
        FeatureDialog(featureText("Lưu nhãn thiết bị","Save device label"),status,{create=false;editing=null},valid=!keyInvalid&&!valueInvalid&&!duplicate,onSave={scope.launch{status.perform{
            api.setDeviceLabel(deviceId,labelKey,LabelWrite(value)).verify();reload();onChanged();create=false;editing=null
        }}}){
            OutlinedTextField(key,{key=MonitoringSupport.normalizeLabelKey(it)},readOnly=editing!=null,label={Text(featureText("Khóa nhãn","Label key"))},singleLine=true,
                isError=(key.isNotEmpty()&&keyInvalid)||duplicate,
                supportingText={Text(when{duplicate->featureText("Nhãn này đã tồn tại. Hãy chọn Sửa trên nhãn hiện có.","This label already exists. Edit the existing label.");editing!=null->featureText("Khóa giữ nguyên; bạn có thể sửa giá trị bên dưới.","The key stays fixed; edit its value below.");else->featureText("Bắt đầu bằng a–z; dùng chữ, số, dấu _. Tối đa 32 ký tự. Chữ hoa tự chuyển thành chữ thường.","Start with a–z; use letters, numbers or _. Up to 32 characters. Uppercase is converted to lowercase.")})})
            OutlinedTextField(value,{value=it},label={Text(featureText("Giá trị (tối đa 64 ký tự)","Value (up to 64 characters)"))},singleLine=true,isError=valueInvalid,
                supportingText={Text(if(valueInvalid)featureText("Tối đa 64 ký tự, không dùng xuống dòng hoặc ký tự điều khiển.","Up to 64 characters; no line breaks or control characters.") else "${value.codePointCount(0,value.length)} / 64")})
            Text("env:production · location:hanoi · role:database",style=MaterialTheme.typography.bodySmall)
        }
    }
    if(offlineEdit){
        var threshold by remember{mutableStateOf((device?.offlineThresholdMinutes?:5).toString())}
        FeatureDialog(featureText("Ngưỡng mất kết nối","Offline threshold"),status,{offlineEdit=false},valid=(threshold.toIntOrNull()?:0) in 1..1440,onSave={scope.launch{status.perform{
            api.updateDeviceSettings(deviceId,DeviceSettingsWrite(threshold.toInt())).verify();onChanged();offlineEdit=false
        }}}){
            Text(featureText("Quy tắc mặc định OFFLINE dùng giá trị này. Áp dụng cho thiết bị đang chọn.","The default OFFLINE rule uses this value for the selected device."))
            OutlinedTextField(threshold,{threshold=it},label={Text(featureText("Số phút (1–1440)","Minutes (1–1440)"))},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
        }
    }
    deleting?.let{key->ConfirmFeature(featureText("Xóa nhãn: ","Delete label: ")+key,status,{deleting=null},{scope.launch{status.perform{api.deleteDeviceLabel(deviceId,key).verify();reload();onChanged();deleting=null}}})}
}
