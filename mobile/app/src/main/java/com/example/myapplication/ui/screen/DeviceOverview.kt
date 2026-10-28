package com.example.myapplication.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.*
import com.example.myapplication.ui.i18n.LocalAppStrings
import java.time.*
import java.time.format.DateTimeFormatter

private fun decimal(v:Number?):String = v?.let{String.format(java.util.Locale.getDefault(),"%.1f",it.toDouble())}?:"—"
private fun stamp(value:String?):String = try{
    val normalized=if(value!!.endsWith("Z")||Regex("[+-]\\d\\d:\\d\\d$").containsMatchIn(value))value else value+"Z"
    OffsetDateTime.parse(normalized).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"))
}catch(_:Exception){"—"}

@Composable
fun OverviewTab(device:DeviceDetail?, groups:List<DeviceGroup> = emptyList(),metrics:List<Metric> = emptyList(),
    alerts:List<Alert> = emptyList(),onViewMetrics:()->Unit={},onViewProcesses:()->Unit={},
    onToggleMonitoring:((Boolean?)->Unit)?=null,onUpdateDeviceGroup:(Int?)->Unit={}){
    if(device==null){Text("…");return}
    val en=LocalAppStrings.current.isEnglish
    fun t(vi:String,enText:String)=if(en)enText else vi
    val info=device.staticInfo
    val gpu=device.gpuUsagePct?:info?.gpuUsagePct
    val gpuName=device.gpuName?:info?.gpuName
    val temp=device.cpuTempC?:info?.cpuTempC
    val stale=device.status=="Offline"||device.status=="Paused"
    var hardwareExpanded by remember {mutableStateOf(false)}
    var groupMenu by remember {mutableStateOf(false)}
    var processSort by remember {mutableStateOf("CPU")}
    val active=alerts.filter{!it.isResolved}
    val disks=device.disks.orEmpty().sortedBy{it.mountpoint}
    val recent=metrics.sortedBy{it.timestamp}.takeLast(60)
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{
            OverviewSection(t("Trạng thái thiết bị","Device status")){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text(device.name,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    Text(when(device.status){"Online"->t("Trực tuyến","Online");"Warning"->t("Cảnh báo","Warning");"Paused"->t("Tạm dừng","Paused");else->t("Ngoại tuyến","Offline")},
                        color=if(stale)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Text(t("Cập nhật cuối: ","Last updated: ")+stamp(device.lastHeartbeat),style=MaterialTheme.typography.bodySmall)
                device.uptimeSeconds?.let{Text(t("Hoạt động: ","Uptime: ")+"${it/86400}d ${it%86400/3600}h ${it%3600/60}m")}
                if(stale)Text(t("Dữ liệu lần cuối đã lưu; không phản ánh trạng thái hiện tại.","Last recorded data; current usage is unknown."),color=MaterialTheme.colorScheme.error)
            }
        }
        if(active.isNotEmpty()||disks.any{(it.usedPercent?:0f)>=90})item{
            OverviewSection(t("Cần chú ý","Needs attention")){
                active.forEach{Text(it.message,color=MaterialTheme.colorScheme.error)}
                disks.filter{(it.usedPercent?:0f)>=90}.forEach{
                    Text(t("Phân vùng ","Volume ")+(it.mountpoint?:"—")+t(" sắp đầy · Còn "," nearly full · Free ")+decimal(it.freeGb)+" GB",color=MaterialTheme.colorScheme.error)
                }
            }
        }
        item{
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                OverviewResource("CPU",device.cpuUsage,
                    t("Nhiệt độ: ","Temperature: ")+(temp?.takeIf{it>0}?.let{decimal(it)+" °C"}?:t("Không có cảm biến","No sensor")),
                    recent.map{it.cpuUsage},Modifier.weight(1f),onViewMetrics)
                val total=info?.ramTotalMb?.takeIf{it>0}?.div(1024f)
                val ramText=if(total!=null&&device.ramUsage!=null)
                    "~"+decimal(total*device.ramUsage/100)+" / "+decimal(total)+" GB" else t("Chưa có dung lượng","Capacity unavailable")
                OverviewResource("RAM",device.ramUsage,ramText,recent.map{it.ramUsage},Modifier.weight(1f),onViewMetrics)
            }
        }
        item{
            OverviewSection("GPU"){
                Text(gpuName?:t("Chưa nhận thông tin GPU","GPU information unavailable"),style=MaterialTheme.typography.bodyMedium)
                NullableResourceBar(t("Mức sử dụng","Usage"),gpu)
                val gpuTemp=device.gpuTempC?:info?.gpuTempC
                InfoRow(t("Nhiệt độ","Temperature"),gpuTemp?.takeIf{it>0}?.let{decimal(it)+" °C"}?:t("Không có cảm biến","No sensor"))
                val vram=device.gpuVramTotalMB?:info?.gpuVramTotalMB
                val used=device.gpuVramUsedMB?:info?.gpuVramUsedMB
                InfoRow("VRAM",if(vram!=null&&used!=null)decimal(used/1024f)+" / "+decimal(vram/1024f)+" GB" else "—")
                Text(t("Số liệu GPU mới nhất; chưa có lịch sử GPU.","Latest GPU snapshot; GPU history unavailable."),style=MaterialTheme.typography.bodySmall)
            }
        }
        item{Text(t("Ổ đĩa & phân vùng","Storage & volumes")+" · ${disks.size}",style=MaterialTheme.typography.titleLarge)}
        if(disks.isEmpty())item{
            OverviewSection(t("Chưa có danh sách phân vùng","Volume list unavailable")){
                Text(t("Agent chưa gửi thông tin từng phân vùng. Không mặc định đây là ổ C:.","The agent has not reported volumes. No drive letter is assumed."))
                NullableResourceBar(t("Dung lượng đã dùng (số liệu tổng hợp)","Used capacity (aggregate)"),device.diskUsage)
            }
        }
        items(disks){disk->
            OverviewSection((disk.mountpoint?:"—")+" · "+(disk.fstype?:t("Phân vùng","Volume"))){
                NullableResourceBar(t("Dung lượng đã dùng","Used capacity"),disk.usedPercent)
                InfoRow(t("Đã dùng / Tổng","Used / Total"),decimal(disk.usedGb)+" / "+decimal(disk.totalGb)+" GB")
                InfoRow(t("Còn trống","Free"),decimal(disk.freeGb)+" GB")
            }
        }
        item{Text(t("Đây là các phân vùng, không phải số ổ cứng vật lý. Chưa có số liệu đọc/ghi hay sức khỏe SSD/HDD.","These are volumes, not physical disk counts. Disk I/O and SSD/HDD health are not collected."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{
            OverviewSection(t("Kết nối mạng","Network")){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text("↓ "+decimal(device.netInKbps)+" KB/s")
                    Text("↑ "+decimal(device.netOutKbps)+" KB/s")
                }
                MiniHistory(recent.mapNotNull{it.netInKbps})
                InfoRow(t("IP nội bộ","Local IP"),info?.ipPrivate?:"—")
                InfoRow(t("IP công khai","Public IP"),info?.ipPublic?:"—")
                Text(t("Biểu đồ tải xuống · các mẫu gần nhất","Download trend · recent samples"),style=MaterialTheme.typography.bodySmall)
            }
        }
        item{
            OverviewSection(t("Tiến trình nổi bật","Top processes")){
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    FilterChip(selected=processSort=="CPU",onClick={processSort="CPU"},label={Text("CPU")})
                    FilterChip(selected=processSort=="RAM",onClick={processSort="RAM"},label={Text("RAM")})
                    FilterChip(selected=processSort=="GPU",onClick={processSort="GPU"},enabled=device.topProcesses.orEmpty().any{it.gpuUsagePct!=null},label={Text("GPU")})
                }
                TextButton(onClick=onViewProcesses){Text(t("Xem tất cả","View all"))}
                val processes=device.topProcesses.orEmpty().sortedByDescending{when(processSort){"RAM"->it.ramMb;"GPU"->it.gpuUsagePct?:-1f;else->it.cpuPercent}}.take(5)
                if(processes.isEmpty())Text(t("Chưa có dữ liệu tiến trình","No process data"))
                processes.forEach{
                    InfoRow(it.name+" · PID "+it.pid,decimal(it.cpuPercent)+"% CPU · "+decimal(it.ramMb)+" MB · GPU "+decimal(it.gpuUsagePct)+"%")
                }
            }
        }
        item{
            OverviewSection(t("Thông tin máy","System information")){
                TextButton(onClick={hardwareExpanded=!hardwareExpanded}){Text(if(hardwareExpanded)t("Thu gọn","Collapse") else t("Xem cấu hình phần cứng","Show hardware details"))}
                if(hardwareExpanded){
                    InfoRow("Hostname",info?.hostname?:"—")
                    InfoRow(t("Hệ điều hành","Operating system"),listOfNotNull(info?.osName,info?.osVersion).joinToString(" ").ifBlank{"—"})
                    InfoRow("CPU",info?.cpuModel?:"—")
                    InfoRow(t("Số CPU logic","Logical CPUs"),info?.cpuCores?.toString()?:"—")
                    InfoRow("RAM",info?.ramTotalMb?.let{decimal(it/1024f)+" GB"}?:"—")
                    InfoRow("GPU",gpuName?:"—")
                    InfoRow("Kernel",info?.kernelVersion?:"—")
                    InfoRow(t("Phiên bản Agent","Agent version"),info?.agentVersion?:"—")
                }
            }
        }
        item{
            OverviewSection(t("Quản lý thiết bị","Device management")){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(t("Theo dõi máy tính","Monitoring"),modifier=Modifier.weight(1f))
                    Switch(device.isMonitoring,{onToggleMonitoring?.invoke(it)},enabled=onToggleMonitoring!=null)
                }
                Box{
                    OutlinedButton(onClick={groupMenu=true}){Text(device.groupName?:t("Chưa phân nhóm","Ungrouped"))}
                    DropdownMenu(groupMenu,{groupMenu=false}){
                        DropdownMenuItem(text={Text(t("Chưa phân nhóm","Ungrouped"))},onClick={groupMenu=false;onUpdateDeviceGroup(null)})
                        groups.forEach{g->DropdownMenuItem(text={Text(g.groupName)},onClick={groupMenu=false;onUpdateDeviceGroup(g.groupId)})}
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(title:String,content:@Composable ColumnScope.()->Unit){
    OutlinedCard(Modifier.fillMaxWidth()){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun OverviewResource(title:String,value:Float?,subtitle:String,history:List<Float>,modifier:Modifier,onClick:()->Unit){
    OutlinedCard(modifier.clickable(onClick=onClick)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(value?.let{decimal(it)+"%"}?:"—",style=MaterialTheme.typography.headlineMedium)
            Text(subtitle,style=MaterialTheme.typography.bodySmall)
            MiniHistory(history)
        }
    }
}

@Composable
private fun NullableResourceBar(label:String,value:Float?){
    if(value!=null&&value.isFinite())ResourceBar(label,value.coerceIn(0f,100f))
    else InfoRow(label,if(LocalAppStrings.current.isEnglish)"No data" else "Chưa có dữ liệu")
}

@Composable
private fun MiniHistory(values:List<Float>){
    val color=MaterialTheme.colorScheme.primary
    val samples=values.filter{it.isFinite()}
    if(samples.size<2){Text(if(LocalAppStrings.current.isEnglish)"History unavailable" else "Chưa có lịch sử",style=MaterialTheme.typography.bodySmall);return}
    Canvas(Modifier.fillMaxWidth().height(42.dp)){
        val max=maxOf(1f,samples.maxOrNull()?:1f)
        val path=Path()
        samples.forEachIndexed{i,v->
            val x=i*size.width/(samples.size-1)
            val y=size.height-(v.coerceAtLeast(0f)/max)*(size.height-4)
            if(i==0)path.moveTo(x,y)else path.lineTo(x,y)
        }
        drawPath(path,color,style=Stroke(width=2.dp.toPx()))
    }
}
