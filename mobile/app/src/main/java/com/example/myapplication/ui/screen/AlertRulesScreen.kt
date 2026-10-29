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
import com.example.myapplication.util.RulePolicy
import kotlinx.coroutines.launch
import java.util.Locale

@Composable fun AlertRulesScreen(org:Organization?,devices:List<Device>,groups:List<DeviceGroup>) {
    if(org==null){Text(featureText("Chọn không gian làm việc trước.","Select a workspace first."));return}
    key(org.id){AlertRulesContent(org,devices,groups)}
}

@Composable private fun AlertRulesContent(org:Organization,devices:List<Device>,groups:List<DeviceGroup>) {
    val status=remember{FeatureStatus()}
    val scope=rememberCoroutineScope()
    val api=RetrofitClient.apiService
    var rules by remember{mutableStateOf<List<AlertRule>>(emptyList())}
    var creating by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<AlertRule?>(null)}
    var deleting by remember{mutableStateOf<AlertRule?>(null)}
    val canEdit=org.role in listOf("Owner","Admin")
    var selectedDevice by remember { mutableStateOf("") }
    val device=devices.find{it.id==selectedDevice}
    val visibleRules=if(device==null)rules else RulePolicy.effective(rules,device,org.id)
    suspend fun reload(){rules=api.getAlertRules(org.id).required()}
    RefreshFeature(org.id,!status.busy&&!creating&&editing==null&&deleting==null){status.refresh{reload()}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text(featureText("Ngưỡng cảnh báo","Alert rules"),style=MaterialTheme.typography.headlineSmall)
            Text(featureText("Mỗi thiết bị chỉ dùng 1 quy tắc cho mỗi chỉ số. Ưu tiên: thiết bị → nhóm → mặc định. Quy tắc tạm tắt không áp dụng.","One effective rule per device/component. Priority: device → group → workspace default. Disabled rules do not apply."))
            FeatureChoice(featureText("Xem quy tắc của","Rules for"),selectedDevice,listOf("" to featureText("Tất cả cấu hình","All configurations"))+devices.map{it.id to "${it.name} · #${it.id}"},{selectedDevice=it})
            FeatureMessages(status)
            if(canEdit)Button(onClick={status.error=null;creating=true},enabled=!status.busy){Text(featureText("Thêm quy tắc","Add rule"))}
        }
        if(visibleRules.isEmpty())item{Text(featureText("Chưa có quy tắc trong lựa chọn này.","No rules in this selection."))}
        items(visibleRules,key={it.ruleId?:0}){rule->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(if(rule.defaultKey!=null)when(rule.metricType){"CPU"->featureText("CPU cao","High CPU");"RAM"->featureText("RAM cao","High RAM");"DISK"->featureText("Ổ đĩa gần đầy","Disk nearly full");"CPU_TEMP"->featureText("Nhiệt độ CPU cao","High CPU temperature");else->featureText("Thiết bị mất kết nối","Device offline")} else rule.ruleName?:rule.metricType,style=MaterialTheme.typography.titleMedium)
            val target=if(rule.workspaceId!=null)featureText("Toàn không gian (thiết bị hiện tại và mới)","Whole workspace (existing and new devices)") else devices.find{it.id.toIntOrNull()==rule.deviceId}?.name ?: groups.find{it.groupId==rule.groupId}?.groupName ?: "#${rule.deviceId?:rule.groupId}"
            Text(target)
            Text("#${rule.ruleId} · "+if(rule.deviceId!=null)featureText("Riêng thiết bị #${rule.deviceId}","Device override #${rule.deviceId}") else if(rule.groupId!=null)featureText("Kế thừa từ nhóm","Inherited from group") else featureText("Mặc định khi chưa có quy tắc riêng","Default when no override exists"),style=MaterialTheme.typography.bodySmall)
            Text(when{rule.defaultKey=="OFFLINE"->featureText("Theo ngưỡng mất kết nối của từng thiết bị.","Uses each device's offline threshold.");rule.metricType=="DISK_HEALTH"->featureText("Báo khi Windows ghi nhận ổ đĩa có lỗi/cảnh báo.","Alerts when Windows reports a disk warning or failure.");rule.metricType=="SERVICE"->rule.serviceName?:"—";rule.metricType=="PROCESS_MISSING"->rule.processName?:"—";else->"${rule.metricType} ${rule.condition} ${featureDecimal(rule.threshold)}"})
            Text("${rule.durationMinutes} "+featureText("phút liên tục · Khoảng cách ","continuous minutes · Cooldown ")+rule.cooldownMinutes+featureText(" phút"," minutes"))
            Text((if(rule.defaultKey!=null)featureText("Mặc định · ","Default · ") else "")+if(rule.isActive)featureText("Đang bật","Enabled") else featureText("Tạm tắt","Disabled"),color=MaterialTheme.colorScheme.primary)
            if(canEdit)Row{TextButton(onClick={status.error=null;editing=rule},enabled=!status.busy){Text(featureText("Chỉnh sửa","Edit"))}
                if(rule.defaultKey==null)TextButton(onClick={deleting=rule},enabled=!status.busy){Text(featureText("Xóa","Delete"))}}
        }}}
    }
    if(creating||editing!=null)key(creating,editing?.ruleId){RuleEditor(editing,devices,groups,rules,status,{creating=false;editing=null},{body,update->scope.launch{status.perform{
        if(editing!=null)api.updateAlertRule(org.id,editing!!.ruleId!!,update).verify() else api.createAlertRule(org.id,body).verify()
        reload();creating=false;editing=null
    }}})}
    deleting?.let{rule->ConfirmFeature(featureText("Xóa quy tắc","Delete rule"),status,{deleting=null},{scope.launch{status.perform{api.deleteAlertRule(rule.ruleId!!).verify();reload();deleting=null}}})}
}

@Composable private fun RuleEditor(existing:AlertRule?,devices:List<Device>,groups:List<DeviceGroup>,rules:List<AlertRule>,status:FeatureStatus,onDismiss:()->Unit,onSave:(AlertRule,AlertRuleUpdate)->Unit) {
    var metric by remember{mutableStateOf(existing?.metricType?:"CPU")}
    var target by remember{mutableStateOf(existing?.deviceId?.let{"d:$it"}?:existing?.groupId?.let{"g:$it"}?:devices.firstOrNull()?.let{"d:${it.id}"}?:groups.firstOrNull()?.let{"g:${it.groupId}"}?:"")}
    var threshold by remember{mutableStateOf((existing?.threshold?:90f).toString())}
    var duration by remember{mutableStateOf((existing?.durationMinutes?:1).toString())}
    var cooldown by remember{mutableStateOf((existing?.cooldownMinutes?:30).toString())}
    var active by remember{mutableStateOf(existing?.isActive?:true)}
    var name by remember{mutableStateOf(existing?.ruleName?:"")}
    var condition by remember{mutableStateOf(existing?.condition?:">=")}
    var severity by remember{mutableStateOf(existing?.severity?:"WARNING")}
    var service by remember{mutableStateOf(existing?.serviceName?:"")}
    var process by remember{mutableStateOf(existing?.processName?:"")}
    val fixed=metric=="DISK_HEALTH"||existing?.defaultKey=="OFFLINE"
    val numeric=metric !in listOf("SERVICE","PROCESS_MISSING")&&!fixed
    val value=threshold.replace(',','.').toFloatOrNull()
    val min=if(metric=="OFFLINE")1f else 0f
    val max=when(metric){"CPU","RAM","GPU","DISK"->100f;"CPU_TEMP"->150f;"OFFLINE"->1440f;else->100000f}
    val candidate=existing?:AlertRule(deviceId=target.removePrefix("d:").toIntOrNull().takeIf{target.startsWith("d:")},groupId=target.removePrefix("g:").toIntOrNull().takeIf{target.startsWith("g:")},metricType=metric,serviceName=service,processName=process)
    val conflict=if(active)RulePolicy.duplicate(rules,candidate) else null
    val valid=conflict==null&&(!numeric||(value!=null&&value.isFinite()&&value in min..max))&&(duration.toIntOrNull()?:0)>0&&(cooldown.toIntOrNull()?:0)>0&&
        (existing!=null||target.isNotBlank())&&(metric!="SERVICE"||service.isNotBlank())&&(metric!="PROCESS_MISSING"||process.isNotBlank())&&name.length<=150&&service.length<=100&&process.length<=100
    FeatureDialog(featureText("Thiết lập quy tắc","Rule settings"),status,onDismiss,valid=valid,onSave={
        val body=AlertRule(deviceId=if(target.startsWith("d:"))target.substringAfter(':').toIntOrNull() else null,groupId=if(target.startsWith("g:"))target.substringAfter(':').toIntOrNull() else null,
            ruleName=name.ifBlank{"$metric · $threshold"},metricType=metric,threshold=if(metric=="DISK_HEALTH")1f else value,
            condition=if(metric=="DISK_HEALTH")">=" else condition,severity=severity,durationMinutes=duration.toInt(),cooldownMinutes=cooldown.toInt(),isActive=active,
            serviceName=service.takeIf{metric=="SERVICE"},processName=process.takeIf{metric=="PROCESS_MISSING"})
        onSave(body,AlertRuleUpdate(if(numeric)value else null,duration.toInt(),cooldown.toInt(),active))
    }){
        conflict?.let{Text(featureText("Đã có quy tắc ${it.metricType} (#${it.ruleId}). Đóng cửa sổ này và chỉnh quy tắc đó.","A ${it.metricType} rule already exists (#${it.ruleId}). Close this dialog and edit that rule."),color=MaterialTheme.colorScheme.error)}
        if(existing==null){
            OutlinedTextField(name,{name=it},label={Text(featureText("Tên quy tắc","Rule name"))},singleLine=true)
            FeatureChoice(featureText("Chỉ số","Metric"),metric,listOf("CPU","RAM","GPU","DISK","CPU_TEMP","DISK_HEALTH","NET_IN","NET_OUT","OFFLINE","SERVICE","PROCESS_MISSING").map{it to it},{metric=it;threshold=if(it=="OFFLINE")"5" else if(it=="CPU_TEMP")"85" else "90"})
            FeatureChoice(featureText("Phạm vi","Scope"),target,devices.map{"d:${it.id}" to "${it.name} · #${it.id}"}+groups.map{"g:${it.groupId}" to it.groupName},{target=it})
            if(numeric)FeatureChoice(featureText("Điều kiện","Condition"),condition,listOf(">=" to "≥",">" to ">","<=" to "≤","<" to "<"),{condition=it})
            FeatureChoice(featureText("Mức độ","Severity"),severity,listOf("WARNING" to featureText("Cảnh báo","Warning"),"CRITICAL" to featureText("Nghiêm trọng","Critical"),"INFO" to featureText("Thông tin","Info")),{severity=it})
        } else Text(existing.ruleName?:metric)
        if(numeric){
            val unit=when(metric){"CPU","RAM","GPU","DISK"->"%";"CPU_TEMP"->"°C";"OFFLINE"->featureText("phút","minutes");else->"KB/s"}
            OutlinedTextField(threshold,{threshold=it},label={Text(featureText("Ngưỡng","Threshold")+" ($unit)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
            Slider(value=(value?.takeIf{it.isFinite()}?:min).coerceIn(min,max),onValueChange={threshold=String.format(Locale.US,"%.1f",it)},valueRange=min..max,enabled=!status.busy)
        }
        if(existing?.defaultKey=="OFFLINE")Text(featureText("Dùng ngưỡng tại Chi tiết thiết bị → Cấu hình & nhãn.","Uses the setting in Device details → Settings & labels."))
        if(metric=="DISK_HEALTH")Text(featureText("Điều kiện cố định: Windows báo ổ có cảnh báo/lỗi. Không có dữ liệu không được tính là lỗi.","Fixed condition: Windows reports a warning/failure. Missing data is not treated as failure."))
        if(metric=="SERVICE")OutlinedTextField(service,{service=it},label={Text("Windows Service")},readOnly=existing!=null,singleLine=true)
        if(metric=="PROCESS_MISSING")OutlinedTextField(process,{process=it},label={Text(featureText("Tên tiến trình","Process name"))},readOnly=existing!=null,singleLine=true)
        OutlinedTextField(duration,{duration=it},label={Text(featureText("Liên tục (phút)","Continuous duration (minutes)"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
        OutlinedTextField(cooldown,{cooldown=it},label={Text(featureText("Khoảng cách cảnh báo (phút)","Cooldown (minutes)"))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
        Row{Checkbox(active,{active=it});Text(featureText("Áp dụng quy tắc","Enable rule"))}
    }
}
