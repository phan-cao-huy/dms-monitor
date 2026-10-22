package com.example.myapplication.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.AppVersionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AppUpdateManager {

    /**
     * Lấy versionCode hiện tại của ứng dụng đang chạy
     */
    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (_: Exception) {
            1L
        }
    }

    /**
     * Lấy versionName hiện tại của ứng dụng
     */
    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    /**
     * Kiểm tra phiên bản mới từ server
     */
    suspend fun checkForUpdate(context: Context): Result<Pair<AppVersionResponse, Boolean>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getAppVersion()
                if (response.isSuccessful && response.body() != null) {
                    val updateInfo = response.body()!!
                    val currentCode = getCurrentVersionCode(context)
                    val hasUpdate = updateInfo.versionCode > currentCode
                    Result.success(Pair(updateInfo, hasUpdate))
                } else {
                    Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.failure(e)
            }
        }
    }

    /**
     * Kiểm tra xem một file APK có khớp với phiên bản và kích thước mong muốn không
     */
    fun isApkMatchingVersion(
        context: Context,
        file: File,
        expectedVersionCode: Long,
        expectedSizeBytes: Long = 0L
    ): Boolean {
        return try {
            if (!file.exists() || file.length() == 0L) return false
            if (expectedSizeBytes > 0L && file.length() != expectedSizeBytes) {
                return false
            }
            val pkgInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (pkgInfo != null) {
                val code = PackageInfoCompat.getLongVersionCode(pkgInfo)
                code == expectedVersionCode
            } else {
                expectedSizeBytes > 0L && file.length() == expectedSizeBytes
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Lấy file APK đã tải về từ trước (nếu có trên máy và còn nguyên vẹn)
     */
    fun getDownloadedApkFile(
        context: Context,
        expectedVersionCode: Long,
        expectedSizeBytes: Long = 0L
    ): File? {
        val dirs = listOfNotNull(context.externalCacheDir, context.cacheDir)
        for (dir in dirs) {
            val fileNamed = File(dir, "dms-update-v$expectedVersionCode.apk")
            if (isApkMatchingVersion(context, fileNamed, expectedVersionCode, expectedSizeBytes)) {
                return fileNamed
            }
            val legacy = File(dir, "dms-update.apk")
            if (isApkMatchingVersion(context, legacy, expectedVersionCode, expectedSizeBytes)) {
                return legacy
            }
        }
        return null
    }

    /**
     * Kiểm tra xem quyền cài đặt từ nguồn không xác định đã được cấp chưa
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Mở màn hình Cài đặt để người dùng cấp quyền cài APK
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
        }
    }

    /**
     * Tải file APK từ server và báo tiến trình tải theo phần trăm
     */
    suspend fun downloadApk(
        context: Context,
        apkRelativeUrl: String,
        versionCode: Long,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val targetDir = context.externalCacheDir ?: context.cacheDir
                val targetFile = File(targetDir, "dms-update-v$versionCode.apk")
                val tempFile = File(targetDir, "dms-update-v$versionCode.tmp")

                if (tempFile.exists()) tempFile.delete()

                val base = RetrofitClient.getBaseUrl().substringBefore("/api/v1").trimEnd('/')
                val path = apkRelativeUrl.trimStart('/')
                val fullUrl = if (apkRelativeUrl.startsWith("http")) apkRelativeUrl else "$base/$path"

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(180, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(fullUrl)
                    .header("Bypass-Tunnel-Reminder", "true")
                    .header("User-Agent", "DMS-App-Updater/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: Không thể tải APK"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Phản hồi rỗng"))
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var downloadedBytes: Long = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    return@withContext Result.failure(Exception("File APK tải về không hợp lệ hoặc bị rỗng"))
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                // Cập nhật thêm file legacy nếu cần
                val legacyFile = File(targetDir, "dms-update.apk")
                try {
                    targetFile.copyTo(legacyFile, overwrite = true)
                } catch (_: Exception) {}

                Result.success(targetFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Khởi động trình cài đặt hệ thống của Android để cài APK
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception("File APK không tồn tại hoặc bị lỗi khi tải về."))
            }

            // Kiểm tra quyền cài ứng dụng từ nguồn không xác định trên Android 8.0+
            if (!canInstallPackages(context)) {
                requestInstallPermission(context)
                return Result.failure(Exception("PERMISSION_NEEDED"))
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }

            val resInfoList = context.packageManager.queryIntentActivities(installIntent, 0)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
