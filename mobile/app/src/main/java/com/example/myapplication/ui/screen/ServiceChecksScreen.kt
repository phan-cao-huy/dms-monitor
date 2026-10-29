package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.*
import com.example.myapplication.util.MonitoringSupport
import kotlinx.coroutines.launch

@Composable internal fun resultText(code:String?):String {
    if(code==null)return featureText("Chưa kiểm tra","Not checked yet")
    if(code.startsWith("HTTP_"))return "HTTP "+code.removePrefix("HTTP_")
    if(code.startsWith("SSL_DAYS:"))return featureText("Chứng chỉ còn ","Certificate expires in ")+code.substringAfter(':')+featureText(" ngày"," days")
    return when(code){
        "PING_OK"->featureText("Ping thành công","Ping succeeded")
        "PING_FAILED"->featureText("Không nhận phản hồi Ping","No Ping response")
        "TCP_OK"->featureText("Kết nối TCP thành công","TCP connected")
        "TIMEOUT"->featureText("Quá thời gian chờ","Timed out")
        "TLS_VALIDATION_FAILED"->featureText("Chứng chỉ HTTPS không hợp lệ / hết hạn","Invalid or expired HTTPS certificate")
        "TARGET_BLOCKED"->featureText("Địa chỉ bị chặn bởi chính sách mạng của server","Target blocked by server network policy")
        "TOO_MANY_REDIRECTS"->featureText("Chuyển hướng quá 3 lần","More than 3 redirects")
        "PING_UNAVAILABLE"->featureText("Server chưa hỗ trợ Ping","Ping unavailable on server")
        else->featureText("Không kết nối được hoặc phản hồi không hợp lệ","Cannot connect or response is invalid")
    }
}

@Composable fun ServiceChecksScreen(org:Organization?) {
    if(org==null){Text(featureText("Chọn không gian làm việc trước.","Select a workspace first."));return}
    if(org.role !in listOf("Owner","Admin")){Text(featureText("Chỉ Owner/Admin được quản lý kiểm tra dịch vụ.","Only Owner/Admin can access service checks."));return}
    key(org.id){ServiceChecksContent(org.id)}
}

@Composable private fun ServiceChecksContent(orgId:String) {
    val api=RetrofitClient.apiService
    val status=remember {FeatureStatus()}
    val scope=rememberCoroutineScope()
    var checks by remember {mutableStateOf<List<ServiceCheck>>(emptyList())}
    var create by remember {mutableStateOf(false)}
    var editing by remember {mutableStateOf<ServiceCheck?>(null)}
    var deleting by remember {mutableStateOf<ServiceCheck?>(null)}
    var history by remember {mutableStateOf<CheckHistory?>(null)}
    suspend fun reload(){checks=api.serviceChecks(orgId).required()}
    RefreshFeature(orgId,!status.busy&&!create&&editing==null&&deleting==null&&history==null){status.refresh{reload()}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text(featureText("Kiểm tra dịch vụ","Service checks"),style=MaterialTheme.typography.headlineSmall)
            Text(featureText("Kiểm tra từ server DMS, không phải từ điện thoại hay máy Agent. Mạng riêng cần được quản trị server cho phép.","Checks run from the DMS server, not this phone or an Agent. Private networks require server administrator approval."))
            FeatureMessages(status)
            Button(onClick={status.error=null;create=true},enabled=!status.busy&&checks.size<20){Text(featureText("Thêm phép kiểm tra","Add service check"))}
        }
        if(checks.isEmpty())item{Text(featureText("Chưa có phép kiểm tra.","No service checks yet."))}
        items(checks,key={it.id}){check->OutlinedCard(Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(check.name,style=MaterialTheme.typography.titleMedium)
                Text(check.type+" · "+check.target)
                Text(when{!check.enabled->featureText("Tạm tắt","Disabled");check.incidentOpen->featureText("Có sự cố","Incident open");check.latest?.ok==true->featureText("Bình thường","Healthy");check.latest!=null->featureText("Đang theo dõi lỗi","Tracking failures");else->featureText("Chờ kiểm tra","Pending")},color=if(check.incidentOpen&&check.enabled)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(resultText(check.latest?.detail))
                Text(featureText("Độ trễ: ","Latency: ")+featureDecimal(check.latest?.latencyMs)+" ms · ${check.intervalSeconds}s")
                Text(featureText("Lỗi liên tiếp: ","Consecutive failures: ")+"${check.consecutiveFailures}/${check.failureThreshold}")
                Text(MonitoringSupport.time(check.lastCheckedAt),style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    TextButton(enabled=!status.busy,onClick={scope.launch{status.perform{api.runCheck(orgId,check.id).required();reload();status.message="Đã chạy kiểm tra; xem kết quả trên thẻ dịch vụ." to "Check completed; see the result on its card."}}}){Text(featureText("Chạy ngay","Run now"))}
                    TextButton(enabled=!status.busy,onClick={scope.launch{status.perform{history=api.checkHistory(orgId,check.id).required()}}}){Text(featureText("Lịch sử","History"))}
                    TextButton(enabled=!status.busy,onClick={status.error=null;editing=check}){Text(featureText("Sửa","Edit"))}
                }
                TextButton(enabled=!status.busy,onClick={deleting=check}){Text(featureText("Xóa","Delete"),color=MaterialTheme.colorScheme.error)}
            }
        }}
    }
    if(create||editing!=null)key(create,editing?.id){CheckEditor(editing,status,{create=false;editing=null},{body->scope.launch{status.perform{
        if(editing==null)api.createCheck(orgId,body).verify() else api.updateCheck(orgId,editing!!.id,body).verify()
        reload();create=false;editing=null
    }}})}
    deleting?.let{row->ConfirmFeature(featureText("Xóa phép kiểm tra: ","Delete check: ")+row.name,status,{deleting=null},{scope.launch{status.perform{api.deleteCheck(orgId,row.id).verify();reload();deleting=null}}})}
    history?.let{data->FeatureDialog(featureText("Lịch sử dịch vụ","Service history"),status,{history=null}){
        Text(featureText("Sự cố gần đây","Recent incidents"),style=MaterialTheme.typography.titleMedium)
        if(data.incidents.isEmpty())Text(featureText("Chưa có sự cố.","No incidents."))
        data.incidents.forEach{row->HorizontalDivider();Text(if(row.resolvedAt==null)featureText("Đang xảy ra","Open") else featureText("Đã phục hồi","Recovered"));Text(resultText(row.detail));Text(MonitoringSupport.time(row.openedAt)+" → "+MonitoringSupport.time(row.resolvedAt))}
        Text(featureText("Kết quả gần đây","Recent results"),style=MaterialTheme.typography.titleMedium)
        data.results.forEach{row->HorizontalDivider();Text((if(row.ok)"✓ " else "! ")+resultText(row.detail));Text(MonitoringSupport.time(row.checkedAt)+" · "+featureDecimal(row.latencyMs)+" ms")}
        if(data.results.isEmpty())Text(featureText("Chưa có kết quả.","No results yet."))
    }}
}

@Composable private fun CheckEditor(check:ServiceCheck?,status:FeatureStatus,onDismiss:()->Unit,onSave:(CheckWrite)->Unit) {
    var name by remember{mutableStateOf(check?.name?:"")}
    var type by remember{mutableStateOf(check?.type?:"HTTP")}
    var target by remember{mutableStateOf(check?.target?:"")}
    var interval by remember{mutableStateOf((check?.intervalSeconds?:60).toString())}
    var timeout by remember{mutableStateOf((check?.timeoutSeconds?:5).toString())}
    var failures by remember{mutableStateOf((check?.failureThreshold?:3).toString())}
    var enabled by remember{mutableStateOf(check?.enabled?:true)}
    val valid=name.isNotBlank()&&name.length<=100&&target.isNotBlank()&&target.length<=2048&&target.none{it.code<=32}&&
        (interval.toIntOrNull()?:0) in 30..86400&&(timeout.toIntOrNull()?:0) in 1..5&&(failures.toIntOrNull()?:0) in 1..20
    FeatureDialog(featureText("Cấu hình kiểm tra dịch vụ","Service check settings"),status,onDismiss,
        onSave={onSave(CheckWrite(name.trim(),type,target.trim(),interval.toInt(),timeout.toInt(),failures.toInt(),enabled))},valid=valid){
        OutlinedTextField(name,{name=it},label={Text(featureText("Tên dịch vụ","Service name"))},singleLine=true)
        FeatureChoice(featureText("Loại kiểm tra","Check type"),type,listOf("HTTP" to "HTTP / API","SSL" to featureText("Hạn HTTPS","HTTPS expiry"),"TCP" to "TCP","PING" to "Ping"),{type=it})
        OutlinedTextField(target,{target=it},label={Text(featureText("Địa chỉ đích","Target"))},singleLine=true)
        Text(when(type){"HTTP"->"https://example.com/health";"SSL"->"https://example.com";"TCP"->"example.com:443";else->"example.com"},style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(interval,{interval=it},label={Text(featureText("Chu kỳ (giây, ≥30)","Interval (seconds, ≥30)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        OutlinedTextField(timeout,{timeout=it},label={Text(featureText("Thời gian chờ (1–5 giây)","Timeout (1–5 seconds)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        OutlinedTextField(failures,{failures=it},label={Text(featureText("Mở sự cố sau số lần lỗi (1–20)","Open incident after failures (1–20)"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        Row{Checkbox(enabled,{enabled=it});Text(featureText("Bật kiểm tra","Enable check"))}
        Text(featureText("HTTPS còn ≤14 ngày được cảnh báo ngay. Sự cố gửi qua kênh email đã bật. Lưu chỉnh sửa sẽ bắt đầu lại chu kỳ đánh giá.","HTTPS expiry within 14 days opens an incident immediately. Alerts use enabled email channels. Saving restarts the evaluation window."))
    }
}
