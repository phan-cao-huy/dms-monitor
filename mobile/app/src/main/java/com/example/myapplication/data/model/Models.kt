package com.example.myapplication.data.model

import com.google.gson.annotations.SerializedName

// === Auth ===
data class LoginRequest(
    val identifier: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    @SerializedName("full_name") val fullName: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class UserInfo(
    @SerializedName("user_id", alternate = ["id"]) val id: String = "",
    val username: String = "",
    val email: String? = null,
    @SerializedName("full_name") val fullName: String? = null
)

// === Organization ===
data class Organization(
    @SerializedName("org_id", alternate = ["id"]) val id: String = "",
    @SerializedName("org_name", alternate = ["name"]) val orgName: String = "",
    val description: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    val role: String? = null
)

data class CreateOrgRequest(
    @SerializedName("org_name") val orgName: String,
    val description: String? = null
)

data class JoinOrgRequest(
    @SerializedName("invite_code") val inviteCode: String
)

data class JoinOrgResponse(
    @SerializedName("org_id", alternate = ["id"]) val orgId: String,
    @SerializedName("user_id") val userId: String? = null,
    val role: String? = null,
    @SerializedName("joined_at") val joinedAt: String? = null
)

// === Organization Members ===
data class OrgMember(
    @SerializedName("user_id") val userId: String,
    val username: String,
    val email: String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    val role: String, // Owner, Admin, Manager, Viewer
    @SerializedName("joined_at") val joinedAt: String? = null
)

data class UpdateMemberRoleRequest(
    val role: String // Admin, Manager, Viewer
)

// === Invitations ===
data class Invitation(
    @SerializedName("invite_id", alternate = ["id"]) val id: String? = null,
    @SerializedName("invite_code") val inviteCode: String = "",
    @SerializedName("default_role", alternate = ["role"]) val role: String = "Viewer", // Manager, Viewer
    @SerializedName("max_uses") val maxUses: Int? = null,
    @SerializedName("use_count", alternate = ["used_count"]) val usedCount: Int = 0,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CreateInvitationRequest(
    @SerializedName("default_role", alternate = ["role"]) val role: String = "Viewer", // Manager, Viewer
    @SerializedName("max_uses") val maxUses: Int? = null,
    @SerializedName("expires_hours") val expiresHours: Int? = 72
)

// === Device Groups (Cụm thiết bị) ===
data class DeviceGroup(
    @SerializedName("group_id", alternate = ["id"]) val groupId: Int = 0,
    @SerializedName("org_id") val orgId: Int = 0,
    @SerializedName("group_name", alternate = ["name"]) val groupName: String = "",
    val description: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CreateGroupRequest(
    @SerializedName("group_name") val groupName: String,
    val description: String? = null
)

data class GroupMember(
    @SerializedName("access_id") val accessId: Int = 0,
    @SerializedName("user_id") val userId: Int = 0,
    @SerializedName("group_id") val groupId: Int = 0,
    val username: String = "",
    val email: String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    val role: String = "Viewer", // Manager, Viewer
    @SerializedName("granted_at") val grantedAt: String? = null
)

data class AssignGroupMemberRequest(
    @SerializedName("user_id") val userId: Int,
    val role: String = "Viewer" // Manager, Viewer
)

data class UpdateDeviceGroupRequest(
    @SerializedName("group_id") val groupId: Int? = null
)

// === Device ===
data class Device(
    @SerializedName("device_id", alternate = ["id"]) val id: String = "",
    @SerializedName("device_name", alternate = ["name"]) val name: String = "",
    val status: String = "Offline", // Online, Offline, Warning, Maintenance, Archived, Paused
    @SerializedName("is_monitoring") val isMonitoring: Boolean = true,
    @SerializedName("cpu_usage") val cpuUsage: Float? = null,
    @SerializedName("ram_usage") val ramUsage: Float? = null,
    @SerializedName("disk_usage") val diskUsage: Float? = null,
    @SerializedName("last_heartbeat") val lastHeartbeat: String? = null,
    @SerializedName("group_id") val groupId: String? = null,
    @SerializedName("group_name") val groupName: String? = null
)

data class DiskInfo(
    val mountpoint: String? = null,
    val fstype: String? = null,
    @SerializedName("total_gb") val totalGb: Float? = null,
    @SerializedName("used_gb") val usedGb: Float? = null,
    @SerializedName("free_gb") val freeGb: Float? = null,
    @SerializedName("used_percent") val usedPercent: Float? = null
)

data class DeviceDetail(
    @SerializedName("device_id", alternate = ["id"]) val id: String = "",
    @SerializedName("device_name", alternate = ["name"]) val name: String = "",
    val status: String = "Offline",
    @SerializedName("is_monitoring") val isMonitoring: Boolean = true,
    @SerializedName("group_id") val groupId: String? = null,
    @SerializedName("group_name") val groupName: String? = null,
    val description: String? = null,
    val disks: List<DiskInfo>? = null,
    @SerializedName("cpu_usage") val cpuUsage: Float? = null,
    @SerializedName("ram_usage") val ramUsage: Float? = null,
    @SerializedName("disk_usage") val diskUsage: Float? = null,
    @SerializedName("last_heartbeat") val lastHeartbeat: String? = null,
    @SerializedName("static_info") val staticInfo: StaticInfo? = null,
    @SerializedName("top_processes") val topProcesses: List<ProcessInfo>? = null,
    // Live metrics từ heartbeat gần nhất
    @SerializedName("cpu_temp_c") val cpuTempC: Float? = null,
    @SerializedName("gpu_name") val gpuName: String? = null,
    @SerializedName("gpu_usage_pct") val gpuUsagePct: Float? = null,
    @SerializedName("gpu_temp_c") val gpuTempC: Float? = null,
    @SerializedName("gpu_vram_total_mb") val gpuVramTotalMB: Long? = null,
    @SerializedName("gpu_vram_used_mb") val gpuVramUsedMB: Long? = null,
    @SerializedName("net_in_kbps") val netInKbps: Float? = null,
    @SerializedName("net_out_kbps") val netOutKbps: Float? = null,
    @SerializedName("uptime_seconds") val uptimeSeconds: Long? = null
)

data class ToggleMonitoringRequest(
    @SerializedName("is_monitoring") val isMonitoring: Boolean? = null
)

data class ToggleMonitoringResponse(
    @SerializedName("device_id") val deviceId: Int = 0,
    @SerializedName("is_monitoring") val isMonitoring: Boolean = true,
    @SerializedName("status") val status: String = ""
)

data class StaticInfo(
    val hostname: String? = null,
    @SerializedName("agent_version") val agentVersion: String? = null,
    @SerializedName("os_name") val osName: String? = null,
    @SerializedName("os_version") val osVersion: String? = null,
    @SerializedName("kernel_version") val kernelVersion: String? = null,
    @SerializedName("cpu_model") val cpuModel: String? = null,
    @SerializedName("cpu_cores") val cpuCores: Int? = null,
    @SerializedName("ram_total_mb") val ramTotalMb: Int? = null,
    @SerializedName("disk_total_gb") val diskTotalGb: Float? = null,
    @SerializedName("ip_private") val ipPrivate: String? = null,
    @SerializedName("ip_public") val ipPublic: String? = null,
    @SerializedName("gpu_name") val gpuName: String? = null,
    @SerializedName("cpu_temp_c") val cpuTempC: Float? = null,
    @SerializedName("gpu_temp_c") val gpuTempC: Float? = null,
    @SerializedName("gpu_usage_pct") val gpuUsagePct: Float? = null,
    @SerializedName("gpu_vram_total_mb") val gpuVramTotalMB: Long? = null,
    @SerializedName("gpu_vram_used_mb") val gpuVramUsedMB: Long? = null
)

data class CreateDeviceRequest(
    @SerializedName("device_name", alternate = ["name"]) val name: String,
    @SerializedName("group_id") val groupId: Int? = null,
    val description: String? = null
)

data class CreateDeviceResponse(
    @SerializedName("device_id", alternate = ["id"]) val id: String = "",
    @SerializedName("device_name", alternate = ["name"]) val name: String = "",
    @SerializedName("secret_token") val secretToken: String = "",
    @SerializedName("install_command") val installCommand: String? = null
)

// === Metrics ===
data class Metric(
    @SerializedName("created_at", alternate = ["timestamp", "hour_bucket"]) val timestamp: String = "",
    @SerializedName("cpu_usage") val cpuUsage: Float,
    @SerializedName("ram_usage") val ramUsage: Float,
    @SerializedName("disk_usage") val diskUsage: Float? = null,
    @SerializedName("net_in_kbps") val netInKbps: Float? = null,
    @SerializedName("net_out_kbps") val netOutKbps: Float? = null,
    @SerializedName("cpu_temp_c") val cpuTempC: Float? = null,
    @SerializedName("gpu_usage_pct") val gpuUsagePct: Float? = null,
    @SerializedName("gpu_temp_c") val gpuTempC: Float? = null
)

// === Process ===
data class ProcessInfo(
    val pid: Int,
    val name: String,
    @SerializedName("cpu_percent", alternate = ["cpu_pct"]) val cpuPercent: Float,
    @SerializedName("ram_mb") val ramMb: Float,
    val status: String? = null,
    @SerializedName("gpu_usage_pct") val gpuUsagePct: Float? = null,
    @SerializedName("exe_path") val exePath: String? = null,
    val user: String? = null
)

// === Command ===
data class CommandRequest(
    @SerializedName("command_type") val commandType: String, // REBOOT, RESTART_SERVICE, KILL_PROCESS
    val parameters: Map<String, Any>? = null,
    val reason: String
)

data class Command(
    @SerializedName("command_id", alternate = ["id"]) val id: Int,
    @SerializedName("command_type") val commandType: String,
    val parameters: Map<String, Any>? = null,
    val reason: String? = null,
    val status: String? = null, // PENDING, SUCCESS, FAILED
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("executed_at") val executedAt: String? = null,
    val output: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null
)

// === Alert ===
data class AlertRule(
    @SerializedName("rule_id") val ruleId: Int? = null,
    @SerializedName("device_id") val deviceId: Int? = null,
    @SerializedName("group_id") val groupId: Int? = null,
    @SerializedName("rule_name") val ruleName: String? = null,
    @SerializedName("metric_type") val metricType: String = "CPU",
    val condition: String = ">=",
    val threshold: Float? = 90f,
    @SerializedName("duration_minutes") val durationMinutes: Int = 1,
    @SerializedName("cooldown_minutes") val cooldownMinutes: Int = 30,
    val severity: String = "WARNING",
    @SerializedName("is_active") val isActive: Boolean = true
)

data class AlertRuleUpdate(
    val threshold: Float? = null,
    @SerializedName("duration_minutes") val durationMinutes: Int,
    @SerializedName("cooldown_minutes") val cooldownMinutes: Int,
    @SerializedName("is_active") val isActive: Boolean
)

data class FcmRegistration(
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device_info") val deviceInfo: String = android.os.Build.MODEL
)

data class Alert(
    @SerializedName("alert_id", alternate = ["id"]) val id: String = "",
    @SerializedName("device_id") val deviceId: String = "",
    @SerializedName("device_name") val deviceName: String? = null,
    val message: String = "",
    @SerializedName("message_en") val messageEn: String? = null,
    val severity: String? = null,
    val resolved: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("resolved_at") val resolvedAt: String? = null
) {
    val isResolved: Boolean get() = resolved || resolvedAt != null
}

// === API Error ===
data class ApiError(
    val detail: String
)

// === App Update ===
data class AppVersionResponse(
    @SerializedName("version_code") val versionCode: Int = 1,
    @SerializedName("version_name") val versionName: String = "1.0",
    @SerializedName("apk_url") val apkUrl: String = "/static/dms-app.apk",
    @SerializedName("apk_size_bytes") val apkSizeBytes: Long = 0,
    @SerializedName("release_notes") val releaseNotes: String? = null,
    @SerializedName("force_update") val forceUpdate: Boolean = false
)
