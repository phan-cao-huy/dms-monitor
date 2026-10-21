package com.example.myapplication.data.mock

import com.example.myapplication.data.model.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

object MockData {

    val currentUser = UserInfo(
        id = "user-001",
        username = "admin",
        email = "admin@dms.vn",
        fullName = "Nguyễn Văn Admin"
    )

    val organizations = listOf(
        Organization(id = "org-001", orgName = "Công ty ABC", description = "Tổ chức chính"),
        Organization(id = "org-002", orgName = "Phòng IT", description = "Phòng công nghệ")
    )

    val devices = listOf(
        Device(id = "dev-001", name = "PC-KeToan-01", status = "Online", cpuUsage = 35.5f, ramUsage = 62.0f, lastHeartbeat = Instant.now().toString()),
        Device(id = "dev-002", name = "Server-Storage", status = "Offline", cpuUsage = 0f, ramUsage = 0f, lastHeartbeat = Instant.now().minus(2, ChronoUnit.HOURS).toString()),
        Device(id = "dev-003", name = "PC-GiamDoc", status = "Warning", cpuUsage = 92.0f, ramUsage = 88.5f, lastHeartbeat = Instant.now().toString()),
        Device(id = "dev-004", name = "PC-Marketing-01", status = "Online", cpuUsage = 15.2f, ramUsage = 45.3f, lastHeartbeat = Instant.now().toString()),
        Device(id = "dev-005", name = "PC-NhanSu-02", status = "Maintenance", cpuUsage = 0f, ramUsage = 0f, lastHeartbeat = Instant.now().minus(1, ChronoUnit.DAYS).toString()),
        Device(id = "dev-006", name = "Server-DB-Primary", status = "Online", cpuUsage = 55.8f, ramUsage = 71.2f, lastHeartbeat = Instant.now().toString())
    )

    val deviceDetail = DeviceDetail(
        id = "dev-001",
        name = "PC-KeToan-01",
        status = "Online",
        cpuUsage = 35.5f,
        ramUsage = 62.0f,
        diskUsage = 74.0f,
        lastHeartbeat = Instant.now().toString(),
        staticInfo = StaticInfo(
            hostname = "DESKTOP-KETOAN01",
            osName = "Windows 11 Pro",
            osVersion = "23H2",
            cpuModel = "Intel Core i5-12400",
            cpuCores = 6,
            ramTotalMb = 16384,
            diskTotalGb = 512.0f,
            ipPrivate = "192.168.1.101",
            ipPublic = "113.23.45.67"
        )
    )

    fun generateMetrics(hours: Int = 24): List<Metric> {
        val now = Instant.now()
        return (0 until hours).map { i ->
            Metric(
                timestamp = now.minus((hours - i).toLong(), ChronoUnit.HOURS).toString(),
                cpuUsage = Random.nextFloat() * 80 + 10,
                ramUsage = Random.nextFloat() * 40 + 40,
                diskUsage = 74.0f + Random.nextFloat() * 2,
                netInKbps = Random.nextFloat() * 500,
                netOutKbps = Random.nextFloat() * 200
            )
        }
    }

    val processes = listOf(
        ProcessInfo(pid = 4, name = "System", cpuPercent = 0.5f, ramMb = 120f, status = "running"),
        ProcessInfo(pid = 1234, name = "chrome.exe", cpuPercent = 12.3f, ramMb = 850f, status = "running"),
        ProcessInfo(pid = 5678, name = "java.exe", cpuPercent = 25.1f, ramMb = 1200f, status = "running"),
        ProcessInfo(pid = 9012, name = "explorer.exe", cpuPercent = 1.2f, ramMb = 95f, status = "running"),
        ProcessInfo(pid = 3456, name = "svchost.exe", cpuPercent = 3.4f, ramMb = 45f, status = "running"),
        ProcessInfo(pid = 7890, name = "code.exe", cpuPercent = 8.7f, ramMb = 650f, status = "running"),
        ProcessInfo(pid = 2345, name = "python.exe", cpuPercent = 15.0f, ramMb = 320f, status = "running"),
        ProcessInfo(pid = 6789, name = "mysqld.exe", cpuPercent = 5.5f, ramMb = 400f, status = "running")
    )

    val commands = listOf(
        Command(id = 1, commandType = "REBOOT", reason = "Bảo trì định kỳ", status = "SUCCESS", createdAt = Instant.now().minus(2, ChronoUnit.DAYS).toString(), executedAt = Instant.now().minus(2, ChronoUnit.DAYS).toString()),
        Command(id = 2, commandType = "KILL_PROCESS", parameters = mapOf("pid" to 5678, "process_name" to "java"), reason = "Tiến trình bị treo", status = "SUCCESS", createdAt = Instant.now().minus(1, ChronoUnit.DAYS).toString()),
        Command(id = 3, commandType = "KILL_PROCESS", parameters = mapOf("pid" to 9999, "process_name" to "malware"), reason = "Nghi ngờ mã độc", status = "FAILED", createdAt = Instant.now().minus(6, ChronoUnit.HOURS).toString(), errorMessage = "Process not found")
    )

    val alerts = listOf(
        Alert(id = "alert-001", deviceId = "dev-003", deviceName = "PC-GiamDoc", message = "CPU vượt ngưỡng 90%", severity = "HIGH", resolved = false, createdAt = Instant.now().minus(30, ChronoUnit.MINUTES).toString()),
        Alert(id = "alert-002", deviceId = "dev-002", deviceName = "Server-Storage", message = "Thiết bị mất kết nối", severity = "CRITICAL", resolved = false, createdAt = Instant.now().minus(2, ChronoUnit.HOURS).toString())
    )

    val members = listOf(
        OrgMember(userId = "user-001", username = "admin", email = "admin@dms.vn", fullName = "Nguyễn Văn Admin", role = "Owner", joinedAt = Instant.now().minus(30, ChronoUnit.DAYS).toString()),
        OrgMember(userId = "user-002", username = "trungit", email = "trung@dms.vn", fullName = "Trần Văn Trung", role = "Admin", joinedAt = Instant.now().minus(20, ChronoUnit.DAYS).toString()),
        OrgMember(userId = "user-003", username = "linhhr", email = "linh@dms.vn", fullName = "Phạm Thị Linh", role = "Manager", joinedAt = Instant.now().minus(10, ChronoUnit.DAYS).toString()),
        OrgMember(userId = "user-004", username = "hoangsv", email = "hoang@dms.vn", fullName = "Lê Hoàng", role = "Viewer", joinedAt = Instant.now().minus(5, ChronoUnit.DAYS).toString())
    )

    val invitations = listOf(
        Invitation(id = "inv-001", inviteCode = "A3K9MZ", role = "Manager", maxUses = 5, usedCount = 2, expiresAt = Instant.now().plus(2, ChronoUnit.DAYS).toString(), createdAt = Instant.now().minus(1, ChronoUnit.DAYS).toString()),
        Invitation(id = "inv-002", inviteCode = "X7P2QW", role = "Viewer", maxUses = 10, usedCount = 0, expiresAt = Instant.now().plus(5, ChronoUnit.DAYS).toString(), createdAt = Instant.now().toString())
    )
}
