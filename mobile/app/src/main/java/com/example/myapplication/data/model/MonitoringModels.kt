package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

data class EmailConfig(
    val host: String = "smtp.gmail.com", val port: Int = 587, val username: String = "",
    val password: String? = null,
    @SerializedName("from_email") val fromEmail: String = "",
    val recipients: List<String> = emptyList(), val language: String = "vi"
)
data class EmailChannel(
    @SerializedName("channel_id") val id: Int, val name: String, val enabled: Boolean,
    val config: EmailConfig,
    @SerializedName("password_set") val passwordSet: Boolean = false
)
data class EmailChannelWrite(val name: String, val enabled: Boolean, val config: EmailConfig)
data class EmailDelivery(
    @SerializedName("alert_id") val alertId: Int, val status: String, val attempts: Int,
    @SerializedName("last_error") val lastError: String?, @SerializedName("sent_at") val sentAt: String?
)

data class ServiceCheck(
    @SerializedName("check_id") val id: Int, val name: String, val type: String, val target: String,
    @SerializedName("interval_seconds") val intervalSeconds: Int,
    @SerializedName("timeout_seconds") val timeoutSeconds: Int,
    @SerializedName("failure_threshold") val failureThreshold: Int, val enabled: Boolean,
    @SerializedName("consecutive_failures") val consecutiveFailures: Int,
    @SerializedName("incident_open") val incidentOpen: Boolean,
    @SerializedName("last_checked_at") val lastCheckedAt: String?, val latest: CheckResult?
)
data class CheckWrite(
    val name: String, val type: String, val target: String,
    @SerializedName("interval_seconds") val intervalSeconds: Int = 60,
    @SerializedName("timeout_seconds") val timeoutSeconds: Int = 5,
    @SerializedName("failure_threshold") val failureThreshold: Int = 3,
    val enabled: Boolean = true
)
data class CheckResult(
    val ok: Boolean, @SerializedName("latency_ms") val latencyMs: Double?, val detail: String,
    @SerializedName("checked_at") val checkedAt: String?
)
data class CheckIncident(
    val detail: String, @SerializedName("opened_at") val openedAt: String,
    @SerializedName("resolved_at") val resolvedAt: String?
)
data class CheckHistory(val results: List<CheckResult>, val incidents: List<CheckIncident>)
data class DiskHealth(
    val name: String, val healthy: Boolean?, val source: String?,
    @SerializedName("temp_c") val tempC: Float?, @SerializedName("wear_pct") val wearPct: Float?
)
data class ProcessConnection(val pid: Int, val process: String?, val laddr: String, val raddr: String, val status: String)
data class DeviceEvent(
    @SerializedName("event_pk") val id: Long, @SerializedName("log_name") val logName: String,
    @SerializedName("record_id") val recordId: Long, @SerializedName("event_id") val eventId: Int,
    val level: String, val source: String, val message: String,
    @SerializedName("occurred_at") val occurredAt: String
)
data class DeviceEventPage(val items: List<DeviceEvent>, @SerializedName("next_before") val nextBefore: Long?)
data class LabelWrite(val value: String)
data class MetricPoint(
    @SerializedName("created_at") val timestamp: String,
    @SerializedName("cpu_usage") val cpu: Double?, @SerializedName("ram_usage") val ram: Double?,
    @SerializedName("disk_usage") val disk: Double?, @SerializedName("net_in_kbps") val netIn: Double?,
    @SerializedName("net_out_kbps") val netOut: Double?, @SerializedName("gpu_usage_pct") val gpu: Double?,
    @SerializedName("cpu_temp_c") val cpuTemp: Double?,
    @SerializedName("sample_count") val samples: Int = 0
) {
    fun value(metric: String): Double? = when(metric) {
        "cpu_usage" -> cpu; "ram_usage" -> ram; "disk_usage" -> disk; "net_in_kbps" -> netIn
        "net_out_kbps" -> netOut; "gpu_usage_pct" -> gpu; "cpu_temp_c" -> cpuTemp; else -> null
    }
}
data class MetricEnvelope(
    val resolution: String, @SerializedName("step_seconds") val stepSeconds: Int,
    val from: String, val to: String, val items: List<MetricPoint>
)
data class QueryPoint(val timestamp: String, val value: Double?, @SerializedName("sample_count") val samples: Int)
data class QuerySeries(val key: String?, val name: String, val points: List<QueryPoint>)
data class MetricsQuery(
    val metric: String, val agg: String, @SerializedName("group_by") val groupBy: String,
    val resolution: String, @SerializedName("step_seconds") val stepSeconds: Int,
    val from: String, val to: String, val series: List<QuerySeries>
)

data class DeviceSettingsWrite(@SerializedName("offline_threshold_minutes") val offlineThresholdMinutes: Int)

data class DatabaseBackup(val status:String, val date:String?, val bytes:Long?)
data class StorageSummary(val disk_percent:Double?, val disk_level:String?, val backup:DatabaseBackup?, val alert_days: Int, val database_bytes: Long, val free_bytes: Long?, val cleanup: CleanupStatus?, val groups: List<StorageGroup>)
data class CleanupStatus(val status: String, val updated_at: String)
data class StorageGroup(val kind: String, val bytes: Long, val days: Int?)

data class RetentionPreview(val records: Long, val oldest: String?, val newest: String?, val impact_vi: String, val impact_en: String)
