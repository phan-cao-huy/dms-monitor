package com.example.myapplication.ui.i18n

import androidx.compose.runtime.compositionLocalOf

/**
 * Hỗ trợ song ngữ Tiếng Việt & Tiếng Anh
 */
class AppStrings(val isEnglish: Boolean = false) {
    // Navigation
    val navDevices get() = if (isEnglish) "Devices" else "Thiết bị"
    val navOrganization get() = if (isEnglish) "Organization" else "Tổ chức"
    val navAlerts get() = if (isEnglish) "Alerts" else "Cảnh báo"
    val navAccount get() = if (isEnglish) "Account" else "Tài khoản"

    // Dashboard
    val totalDevices get() = if (isEnglish) "Total" else "Tổng"
    val online get() = if (isEnglish) "Online" else "Online"
    val offline get() = if (isEnglish) "Offline" else "Offline"
    val warning get() = if (isEnglish) "Warning" else "Cảnh báo"
    val searchPlaceholder get() = if (isEnglish) "Search devices..." else "Tìm kiếm thiết bị..."
    val noDevices get() = if (isEnglish) "No devices found" else "Không tìm thấy thiết bị"
    val addDevice get() = if (isEnglish) "Add Device" else "Thêm thiết bị"
    val refresh get() = if (isEnglish) "Refresh" else "Làm mới"
    val logout get() = if (isEnglish) "Logout" else "Đăng xuất"

    // Device Detail
    val tabOverview get() = if (isEnglish) "Overview" else "Tổng quan"
    val tabMetrics get() = if (isEnglish) "Metrics" else "Biểu đồ"
    val tabProcesses get() = if (isEnglish) "Processes" else "Tiến trình"
    val tabCommands get() = if (isEnglish) "Commands" else "Lệnh"
    val restartDevice get() = if (isEnglish) "Reboot Device" else "Khởi động lại"
    val deleteDevice get() = if (isEnglish) "Delete Device" else "Xóa thiết bị"
    val killProcess get() = if (isEnglish) "Kill Process" else "Dừng tiến trình"
    val hardwareInfo get() = if (isEnglish) "Hardware Information" else "Thông tin phần cứng"
    val resourceUsage get() = if (isEnglish) "Resource Usage" else "Tài nguyên"

    // Reasons
    val reasonLabel get() = if (isEnglish) "Select reason (Required)" else "Chọn lý do (Bắt buộc)"
    val otherReasonPrompt get() = if (isEnglish) "Enter custom reason..." else "Nhập lý do cụ thể..."
    val reasonOther get() = if (isEnglish) "Other reason..." else "Lý do khác..."

    // Reboot preset reasons
    val rebootReasons get() = if (isEnglish) listOf(
        "Scheduled maintenance",
        "System frozen / laggy",
        "OS / software update",
        "Restart after configuration change",
        "Other reason..."
    ) else listOf(
        "Bảo trì định kỳ",
        "Hệ thống bị đơ / lag",
        "Cập nhật phần mềm hệ thống",
        "Khởi động lại sau cấu hình",
        "Lý do khác..."
    )

    // Kill preset reasons
    val killReasons get() = if (isEnglish) listOf(
        "High CPU usage (>80%)",
        "High RAM memory leak",
        "Process not responding",
        "Suspected unauthorized process",
        "Other reason..."
    ) else listOf(
        "Chiếm dụng CPU quá cao (>80%)",
        "Tràn bộ nhớ RAM",
        "Tiến trình bị treo (Not Responding)",
        "Nghi ngờ tiến trình lạ / độc hại",
        "Lý do khác..."
    )

    // Profile & Settings
    val settingsAccount get() = if (isEnglish) "Account" else "Tài khoản"
    val editInfo get() = if (isEnglish) "Edit Profile" else "Chỉnh sửa thông tin"
    val editInfoSub get() = if (isEnglish) "Change name, email" else "Thay đổi tên, email"
    val changePassword get() = if (isEnglish) "Change Password" else "Đổi mật khẩu"
    val changePasswordSub get() = if (isEnglish) "Update account password" else "Thay đổi mật khẩu đăng nhập"
    val settingsApp get() = if (isEnglish) "Application" else "Ứng dụng"
    val notifications get() = if (isEnglish) "Notifications" else "Thông báo"
    val notificationsSub get() = if (isEnglish) "Push alerts configuration" else "Cấu hình thông báo đẩy"
    val appearance get() = if (isEnglish) "Appearance" else "Giao diện"
    val appearanceSub get() = if (isEnglish) "Light / Dark theme mode" else "Chế độ sáng / tối"
    val language get() = if (isEnglish) "Language" else "Ngôn ngữ"
    val languageSub get() = if (isEnglish) "English" else "Tiếng Việt"
    val about get() = if (isEnglish) "About" else "Thông tin"
    val version get() = if (isEnglish) "Version" else "Phiên bản"
    val license get() = if (isEnglish) "License" else "Giấy phép"
    val cancel get() = if (isEnglish) "Cancel" else "Hủy"
    val confirm get() = if (isEnglish) "Confirm" else "Xác nhận"
    val save get() = if (isEnglish) "Save" else "Lưu"
    val close get() = if (isEnglish) "Close" else "Đóng"
}

val LocalAppStrings = compositionLocalOf { AppStrings(false) }
