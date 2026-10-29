package com.example.myapplication.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.*
import com.example.myapplication.util.MonitoringSupport
import com.example.myapplication.util.PlotPoint
import java.time.Instant
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable private fun metricName(key:String):String = when(key){
    "cpu_usage"->"CPU";"ram_usage"->"RAM";"disk_usage"->featureText("Dung lượng ổ đĩa","Disk space")
    "gpu_usage_pct"->"GPU";"cpu_temp_c"->featureText("Nhiệt độ CPU","CPU temperature")
    "net_in_kbps"->featureText("Mạng tải xuống","Download");else->featureText("Mạng tải lên","Upload")
}
private fun metricUnit(key:String)=if(key=="cpu_temp_c")" °C" else if(key.contains("kbps"))" KB/s" else "%"
@Composable private fun resolutionName(value:String)=when(value){"minute"->featureText("Tầng phút","Minute tier");"hourly"->featureText("Tầng giờ","Hourly tier");else->featureText("Tầng mẫu gốc","Raw tier")}
@Composable private fun rangeOptions()=MonitoringSupport.ranges.keys.map{it to when(it){"1h"->featureText("1 giờ","1 hour");"6h"->featureText("6 giờ","6 hours");"24h"->featureText("24 giờ","24 hours");"3d"->featureText("3 ngày","3 days");"7d"->featureText("7 ngày","7 days");"30d"->featureText("30 ngày","30 days");else->featureText("90 ngày","90 days")}}

@Composable internal fun MetricCurve(title:String,points:List<PlotPoint>,unit:String,color:Color=MaterialTheme.colorScheme.primary) {
    val rows=remember(points){points.filter{MonitoringSupport.instant(it.timestamp)!=null}.sortedBy{MonitoringSupport.instant(it.timestamp)}}
    val times=remember(rows){rows.map{MonitoringSupport.instant(it.timestamp)!!.toEpochMilli()}}
    var selected by remember(points){mutableIntStateOf(-1)}
    val available=rows.mapNotNull{it.value?.takeIf{value->value.isFinite()}}
    val start=times.firstOrNull()?:0L
    val end=times.lastOrNull()?:start
    val top=if(unit=="%")100.0 else (available.maxOrNull()?.times(1.15)?:1.0).coerceAtLeast(1.0)
    val grid=MaterialTheme.colorScheme.outlineVariant
    OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(title,style=MaterialTheme.typography.titleMedium)
        if(available.isEmpty())Text(featureText("Chưa có dữ liệu cho tài nguyên này. Giá trị thiếu được giữ trống.","No data for this resource yet. Missing values remain blank."))
        else {
            Canvas(Modifier.fillMaxWidth().height(170.dp).pointerInput(rows){detectTapGestures{tap->
                val fraction=(tap.x/size.width).coerceIn(0f,1f)
                val target=start+((end-start)*fraction).toLong()
                selected=rows.indices.minByOrNull{abs(times[it]-target)}?:-1
            }}){
                val bottom=size.height-8.dp.toPx()
                val height=bottom-8.dp.toPx()
                repeat(5){val y=bottom-height*it/4f;drawLine(grid,Offset(0f,y),Offset(size.width,y),1.dp.toPx())}
                fun x(index:Int):Float=if(end==start)size.width/2 else ((times[index]-start).toDouble()/(end-start)*size.width).toFloat()
                fun y(value:Double)=(bottom-(value/top).coerceIn(0.0,1.0)*height).toFloat()
                var previous:Offset?=null
                rows.forEachIndexed{index,row->
                    val value=row.value?.takeIf{it.isFinite()}
                    if(value==null)previous=null else {
                        val position=Offset(x(index),y(value))
                        previous?.let{drawLine(color,it,position,2.dp.toPx())}
                        drawCircle(color,if(index==selected)5.dp.toPx() else 2.dp.toPx(),position)
                        previous=position
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(MonitoringSupport.time(rows.firstOrNull()?.timestamp),style=MaterialTheme.typography.labelSmall);Text(MonitoringSupport.time(rows.lastOrNull()?.timestamp),style=MaterialTheme.typography.labelSmall)}
            Text("0 — "+featureDecimal(top)+unit,style=MaterialTheme.typography.labelSmall)
            if(selected in rows.indices){val row=rows[selected];Text(MonitoringSupport.time(row.timestamp)+" · "+featureDecimal(row.value)+unit,style=MaterialTheme.typography.titleSmall)}
            else Text(featureText("Chạm biểu đồ để xem giá trị theo thời điểm.","Tap the chart to inspect a time point."),style=MaterialTheme.typography.bodySmall)
            Text(featureText("Điểm thấp nhất: ","Lowest point: ")+featureDecimal(available.minOrNull())+unit+featureText(" · Cao nhất: "," · Highest: ")+featureDecimal(available.maxOrNull())+unit,style=MaterialTheme.typography.bodySmall)
        }
    }}
}

@Composable internal fun DeviceHistoryScreen(deviceId:String) {
    key(deviceId){DeviceHistoryContent(deviceId)}
}
@Composable private fun DeviceHistoryContent(deviceId:String) {
    var range by remember{mutableStateOf("1h")}
    var envelope by remember{mutableStateOf<MetricEnvelope?>(null)}
    val status=remember{FeatureStatus()}
    val scope=rememberCoroutineScope()
    suspend fun reload(){val end=Instant.now();envelope=RetrofitClient.apiService.metricSeries(deviceId,end.minusSeconds((MonitoringSupport.ranges[range]?:1)*3600).toString(),end.toString()).required()}
    RefreshFeature(deviceId to range,!status.busy){status.refresh{reload()}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text(featureText("Lịch sử tài nguyên","Resource history"),style=MaterialTheme.typography.headlineSmall)
            FeatureChoice(featureText("Khoảng thời gian","Time range"),range,rangeOptions(),{range=it;envelope=null})
            TextButton(onClick={scope.launch{status.perform{reload()}}},enabled=!status.busy){Text(featureText("Làm mới","Refresh"))}
            FeatureMessages(status)
            envelope?.let{Text(resolutionName(it.resolution)+featureText(" · Mỗi điểm "," · Point spacing ")+it.stepSeconds+featureText(" giây"," seconds"))}
            Text(featureText("GPU/nhiệt độ chỉ có từ các mẫu mới được lưu. Khoảng thiếu dữ liệu không được nối thành đường liên tục.","GPU/temperature history starts with newly stored samples. Missing periods are not connected by a line."),style=MaterialTheme.typography.bodySmall)
        }
        envelope?.let{data->MonitoringSupport.metrics.forEach{metric->item(key=metric){MetricCurve(metricName(metric),MonitoringSupport.timeline(data.items.map{PlotPoint(it.timestamp,it.value(metric))},data.from,data.to,data.stepSeconds),metricUnit(metric))}}}
    }
}

@Composable fun MetricAnalysisScreen(org:Organization?,devices:List<Device>) {
    if(org==null){Text(featureText("Chọn không gian làm việc trước.","Select a workspace first."));return}
    key(org.id){MetricAnalysisContent(org.id,devices)}
}
@Composable private fun MetricAnalysisContent(orgId:String,devices:List<Device>) {
    var metric by remember{mutableStateOf("cpu_usage")}
    var agg by remember{mutableStateOf("avg")}
    var group by remember{mutableStateOf("device")}
    var range by remember{mutableStateOf("24h")}
    var label by remember{mutableStateOf("")}
    var step by remember{mutableStateOf("")}
    var result by remember{mutableStateOf<MetricsQuery?>(null)}
    val status=remember{FeatureStatus()}
    val scope=rememberCoroutineScope()
    val labelOptions=devices.flatMap{it.labels.orEmpty().map{(k,v)->"$k:$v"}}.distinct().sorted()
    val labelKeys=devices.flatMap{it.labels.orEmpty().keys}.distinct().sorted()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {
            Text(featureText("Phân tích tài nguyên","Resource analytics"),style=MaterialTheme.typography.headlineSmall)
            Text(featureText("Chỉ gộp thiết bị bạn được phép xem. Trung bình tính theo số mẫu, không lấy trung bình của các trung bình.","Only authorized devices are aggregated. Averages are weighted by sample count."))
            FeatureChoice(featureText("Tài nguyên","Resource"),metric,MonitoringSupport.metrics.map{it to metricName(it)},{metric=it},!status.busy)
            FeatureChoice(featureText("Phép tính","Aggregation"),agg,listOf("avg" to featureText("Trung bình có trọng số","Weighted average"),"min" to featureText("Thấp nhất","Minimum"),"max" to featureText("Cao nhất","Maximum")),{agg=it},!status.busy)
            FeatureChoice(featureText("Gom theo","Group by"),group,listOf("device" to featureText("Thiết bị","Device"),"group" to featureText("Nhóm thiết bị","Device group"))+labelKeys.map{"label:$it" to featureText("Nhãn: ","Label: ")+it},{group=it},!status.busy)
            FeatureChoice(featureText("Khoảng thời gian","Time range"),range,rangeOptions(),{range=it},!status.busy)
            FeatureChoice(featureText("Lọc nhãn","Label filter"),label,listOf("" to featureText("Tất cả","All"))+labelOptions.map{it to it},{label=it},!status.busy)
            FeatureChoice(featureText("Bước biểu đồ","Chart step"),step,listOf("" to featureText("Tự động (≤500 điểm)","Auto (≤500 points)"),"60" to featureText("1 phút","1 minute"),"300" to featureText("5 phút","5 minutes"),"3600" to featureText("1 giờ","1 hour")),{step=it},!status.busy)
            Button(enabled=!status.busy,onClick={scope.launch{status.perform{
                val end=Instant.now();result=RetrofitClient.apiService.queryMetrics(orgId,metric,agg,group,end.minusSeconds((MonitoringSupport.ranges[range]?:24)*3600).toString(),end.toString(),label.takeIf{it.isNotBlank()},step.toIntOrNull()).required()
            }}}){Text(featureText("So sánh dữ liệu","Compare data"))}
            FeatureMessages(status)
            result?.let{data->Text(metricName(data.metric)+" · "+data.agg+" · "+resolutionName(data.resolution)+" · ${data.stepSeconds}s")
                if(data.series.isEmpty())Text(featureText("Không có số liệu trong lựa chọn này.","No data in this selection."))
                if(data.series.size>12)Text(featureText("Vẽ 12 chuỗi đầu; lọc nhãn để thu hẹp.","Showing the first 12 series; narrow by label."))}
        }
        result?.let{data->data.series.take(12).forEachIndexed{index,series->item(key="series$index"){
            MetricCurve(series.name,MonitoringSupport.timeline(series.points.map{PlotPoint(it.timestamp,it.value)},data.from,data.to,data.stepSeconds),metricUnit(data.metric))
        }}
        if(data.series.size>12)item {Text(data.series.drop(12).joinToString("\n"){series->series.name+" · "+featureDecimal(series.points.lastOrNull{it.value!=null}?.value)+metricUnit(data.metric)})}
        }
    }
}
