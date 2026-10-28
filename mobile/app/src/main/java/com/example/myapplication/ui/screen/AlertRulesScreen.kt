package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun AlertRulesScreen(org: Organization?, devices: List<Device>, groups: List<DeviceGroup>) {
    var rules by remember(org?.id) { mutableStateOf<List<AlertRule>>(emptyList()) }
    var error by remember(org?.id) { mutableStateOf<String?>(null) }
    var busy by remember(org?.id) { mutableStateOf(false) }
    var editing by remember(org?.id) { mutableStateOf<AlertRule?>(null) }
    var creating by remember(org?.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canEdit = org?.role in listOf("Owner", "Admin")
    suspend fun reload() {
        if(org==null)return
        val response=RetrofitClient.apiService.getAlertRules(org.id)
        if(!response.isSuccessful)error="Không tải được quy tắc (${response.code()})"
        else rules=response.body().orEmpty()
    }
    LaunchedEffect(org?.id) {
        while(true){
            try { reload() } catch(e:Exception) { if(e is CancellationException)throw e; error="Không kết nối được server" }
            delay(30000)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Text("Ngưỡng cảnh báo", style=MaterialTheme.typography.headlineSmall)
        Text("Lưu trên server, đồng bộ với web.",style=MaterialTheme.typography.bodyMedium)
        if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
        if(canEdit)Button(onClick={creating=true},enabled=org!=null&&!busy){Text("Thêm quy tắc")}
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=12.dp)) {
            if(rules.isEmpty())item { Text("Chưa có quy tắc. Tạo ngưỡng cho thiết bị hoặc nhóm thiết bị.") }
            items(rules,key={it.ruleId?:0}) { rule ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(rule.ruleName?:rule.metricType,style=MaterialTheme.typography.titleMedium)
                        val target=devices.find{it.id.toIntOrNull()==rule.deviceId}?.name
                            ?:groups.find{it.groupId==rule.groupId}?.groupName?:"Thiết bị #${rule.deviceId}"
                        Text("${rule.metricType} ${rule.condition} ${rule.threshold?:"—"} · $target")
                        Text("${rule.durationMinutes} phút liên tục · Lặp lại sau ${rule.cooldownMinutes} phút")
                        Text(if(rule.isActive)"Đang áp dụng" else "Tạm tắt",color=MaterialTheme.colorScheme.primary)
                        if(canEdit)TextButton(onClick={editing=rule},enabled=!busy){Text("Chỉnh ngưỡng")}
                    }
                }
            }
        }
    }
    if(creating||editing!=null) {
        key(org?.id,editing?.ruleId,creating) {
            RuleEditor(editing,devices,groups,busy,error,onDismiss={if(!busy){creating=false;editing=null}},
                onSave={ rule,update ->
                    scope.launch {
                        busy=true;error=null
                        try {
                            val response=if(editing!=null)RetrofitClient.apiService.updateAlertRule(org!!.id,editing!!.ruleId!!,update)
                                else RetrofitClient.apiService.createAlertRule(org!!.id,rule)
                            if(response.isSuccessful){creating=false;editing=null;reload()}
                            else error="Không lưu được quy tắc (${response.code()}). Kiểm tra quyền và giá trị."
                        } catch(e:Exception){if(e is CancellationException)throw e;error="Không kết nối được server"}
                        finally {busy=false}
                    }
                })
        }
    }
}

@Composable
private fun RuleEditor(existing: AlertRule?, devices: List<Device>, groups: List<DeviceGroup>,busy:Boolean,error:String?,
    onDismiss:()->Unit,onSave:(AlertRule,AlertRuleUpdate)->Unit) {
    val metrics=listOf("CPU","RAM","GPU","DISK","NET_IN","NET_OUT","OFFLINE")
    var metric by remember{mutableStateOf(existing?.metricType?:"CPU")}
    var threshold by remember{mutableStateOf((existing?.threshold?:90f).toString())}
    var duration by remember{mutableStateOf((existing?.durationMinutes?:1).toString())}
    var cooldown by remember{mutableStateOf((existing?.cooldownMinutes?:30).toString())}
    var active by remember{mutableStateOf(existing?.isActive?:true)}
    var target by remember{mutableStateOf(devices.firstOrNull()?.let{"d:${it.id}"}?:groups.firstOrNull()?.let{"g:${it.groupId}"}?:"")}
    var targetMenu by remember{mutableStateOf(false)}
    var metricMenu by remember{mutableStateOf(false)}
    val percent=metric in listOf("CPU","RAM","GPU","DISK")
    val numeric=metric !in listOf("SERVICE","PROCESS_MISSING")
    val min=if(metric=="OFFLINE")1f else 0f
    val max=if(percent)100f else if(metric=="OFFLINE")1440f else maxOf(10000f,existing?.threshold?:0f)
    val value=threshold.toFloatOrNull()
    val valid=(!numeric||(value!=null&&value.isFinite()&&value in min..max))&&(duration.toIntOrNull()?:0)>=1&&(cooldown.toIntOrNull()?:0)>=1
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"Tạo quy tắc" else "Chỉnh ngưỡng")},
        text={
            Column(Modifier.heightIn(max=480.dp).verticalScrollCompat(),verticalArrangement=Arrangement.spacedBy(10.dp)){
                if(error!=null)Text(error,color=MaterialTheme.colorScheme.error)
                if(existing==null){
                    Box {
                        OutlinedButton(onClick={metricMenu=true}){Text("Chỉ số: $metric")}
                        DropdownMenu(metricMenu,{metricMenu=false}){
                            metrics.forEach{m->DropdownMenuItem(text={Text(m)},onClick={metric=m;threshold=if(m=="OFFLINE")"5" else "90";metricMenu=false})}
                        }
                    }
                    Box {
                        val names=devices.map{"d:${it.id}" to it.name}+groups.map{"g:${it.groupId}" to "Nhóm: ${it.groupName}"}
                        OutlinedButton(onClick={targetMenu=true}){Text(names.find{it.first==target}?.second?:"Chọn thiết bị")}
                        DropdownMenu(targetMenu,{targetMenu=false}){names.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={target=id;targetMenu=false})}}
                    }
                }
                if(numeric){
                    OutlinedTextField(threshold,{threshold=it},label={Text("Ngưỡng "+if(percent)"(%)" else if(metric=="OFFLINE")"(phút)" else "(KB/s)")},
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,isError=value==null||!value.isFinite()||value !in min..max)
                    Slider(value=(value?:min).coerceIn(min,max),onValueChange={threshold=("%.1f".format(java.util.Locale.US,it))},valueRange=min..max,enabled=!busy)
                }
                OutlinedTextField(duration,{duration=it},label={Text("Vượt ngưỡng liên tục (phút)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                OutlinedTextField(cooldown,{cooldown=it},label={Text("Khoảng cách cảnh báo (phút)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                Row { Checkbox(active,{active=it});Text("Áp dụng quy tắc") }
                Text("Điều kiện: ${existing?.condition?:">="} · Mức độ: ${existing?.severity?:"WARNING"}",style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={Button(enabled=valid&&!busy&&(existing!=null||target.isNotBlank()),onClick={
            val rule=AlertRule(deviceId=if(target.startsWith("d:"))target.substringAfter(":").toInt() else null,
                groupId=if(target.startsWith("g:"))target.substringAfter(":").toInt() else null,
                ruleName="$metric · $threshold",metricType=metric,threshold=value,durationMinutes=duration.toInt(),cooldownMinutes=cooldown.toInt(),isActive=active)
            onSave(rule,AlertRuleUpdate(if(numeric)value else null,duration.toInt(),cooldown.toInt(),active))
        }){Text(if(busy)"Đang lưu…" else "Lưu")}},
        dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text("Hủy")}})
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
