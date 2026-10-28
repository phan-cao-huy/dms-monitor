package com.example.myapplication.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.BuildConfig
import com.example.myapplication.notifications.AlertNotifications
import com.example.myapplication.data.local.TokenManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import android.util.Base64

@Composable
fun NotificationPermissionCard(){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var enabled by remember{mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){enabled=it}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)enabled=NotificationManagerCompat.from(context).areNotificationsEnabled()}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    OutlinedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Thông báo trên điện thoại",style=MaterialTheme.typography.titleMedium)
            Text(if(enabled)"Đã cho phép thông báo hệ thống" else "Cần cho phép để hiện cảnh báo trên thanh thông báo",
                style=MaterialTheme.typography.bodySmall)
            if(!BuildConfig.FIREBASE_CONFIGURED)Text("Chưa nối Firebase. Bản này chưa nhận được thông báo đẩy khi đóng app.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            TextButton(onClick={
                if(Build.VERSION.SDK_INT>=33&&!enabled)launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
            }){Text(if(enabled)"Cài đặt thông báo" else "Cho phép thông báo")}
            if(enabled)TextButton(onClick={scope.launch{
                val tokens=TokenManager(context)
                if(tokens.isLoggedIn()){
                    val sub=try{JSONObject(String(Base64.decode(tokens.getAccessTokenSync()!!.split(".")[1],Base64.URL_SAFE or Base64.NO_WRAP))).optString("sub")}catch(_:Exception){""}
                    AlertNotifications.show(context,mapOf("user_id" to sub,"alert_id" to "2147483646","title_vi" to "DMS · Thông báo thử","body_vi" to "Thông báo trên điện thoại hoạt động bình thường. Đây là kiểm tra cục bộ.","title_en" to "DMS · Test notification","body_en" to "Phone notifications are working. This is a local test."))
                }
            }}){Text("Gửi thông báo thử")}

        }
    }
}
