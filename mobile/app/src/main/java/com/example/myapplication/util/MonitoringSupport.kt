package com.example.myapplication.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

data class PlotPoint(val timestamp: String, val value: Double?)

object MonitoringSupport {
    val metrics = listOf("cpu_usage", "ram_usage", "disk_usage", "net_in_kbps", "net_out_kbps", "gpu_usage_pct", "cpu_temp_c")
    val ranges = linkedMapOf("1h" to 1L,"6h" to 6L,"24h" to 24L,"3d" to 72L,"7d" to 168L,"30d" to 720L,"90d" to 2160L)
    fun smtpPassword(host: String,password: String): String? =
        (if(host.trim().equals("smtp.gmail.com",ignoreCase=true))password.replace(" ","") else password).takeIf{it.isNotEmpty()}
    fun instant(value: String?): Instant? = try {
        val text = value ?: ""
        OffsetDateTime.parse(if (text.endsWith("Z") || Regex("[+-]\\d{2}:\\d{2}$").containsMatchIn(text)) text else text+"Z").toInstant()
    } catch (_: Exception) { null }
    fun time(value: String?): String = instant(value)?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")) ?: "—"
    fun labelMatches(labels: Map<String,String>?, filter: String?): Boolean {
        if (filter.isNullOrEmpty()) return true
        val parts = filter.split(':',limit=2)
        return parts.size==2 && labels?.get(parts[0])==parts[1]
    }
    fun validLabel(key: String,value: String) = Regex("[a-z][a-z0-9_]{0,31}").matches(key) && value.codePointCount(0,value.length)<=64 && value.none { it.code<32 || it.code==127 }
    fun timeline(points: List<PlotPoint>,from: String?,to: String?,step: Int): List<PlotPoint> {
        val start=instant(from)?.toEpochMilli() ?: return points
        val end=instant(to)?.toEpochMilli() ?: return points
        if(step<=0 || end<=start)return points
        val count=ceil((end-start).toDouble()/(step*1000L)).toInt()
        if(count !in 1..500)return points
        val byTime=points.associateBy { instant(it.timestamp)?.toEpochMilli() }
        return List(count) { index -> val stamp=start+index*step*1000L; byTime[stamp] ?: PlotPoint(Instant.ofEpochMilli(stamp).toString(),null) }
    }
}
