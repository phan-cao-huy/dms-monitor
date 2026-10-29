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
    var soundEnabled by remember{mutableStateOf(AlertNotifications.soundEnabled(context))}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){enabled=it}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME){enabled=NotificationManagerCompat.from(context).areNotificationsEnabled();soundEnabled=AlertNotifications.soundEnabled(context)}}
        owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}
    }
    OutlinedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(featureText("Thông báo trên điện thoại","Phone notifications"),style=MaterialTheme.typography.titleMedium)
            Text(if(enabled)featureText("Đã cho phép thông báo hệ thống","System notifications allowed") else featureText("Cần cho phép để hiện cảnh báo trên thanh thông báo","Allow notifications to show alerts in the notification tray"),
                style=MaterialTheme.typography.bodySmall)
            if(enabled){
                Text(if(soundEnabled)featureText("Âm báo đã bật cho kênh cảnh báo","Alert channel sound is enabled") else featureText("Kênh cảnh báo đang tắt tiếng. Mở cài đặt âm báo để bật.","Alert channel is silent. Open sound settings to enable it."),style=MaterialTheme.typography.bodySmall)
                Text(featureText("Âm lượng thông báo và chế độ Im lặng / Không làm phiền do điện thoại quản lý.","Notification volume, silent mode and Do Not Disturb are controlled by your phone."),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={
                    AlertNotifications.createChannel(context)
                    context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID,AlertNotifications.CHANNEL))
                }){Text(featureText("Cài đặt âm báo","Notification sound settings"))}
            }
            if(!BuildConfig.FIREBASE_CONFIGURED)Text(featureText("Chưa nối Firebase. Bản này chưa nhận được thông báo đẩy khi đóng app.","Firebase is not configured. Background push notifications are unavailable."),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            TextButton(onClick={
                if(Build.VERSION.SDK_INT>=33&&!enabled)launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
            }){Text(if(enabled)featureText("Cài đặt thông báo","Notification settings") else featureText("Cho phép thông báo","Allow notifications"))}
            if(enabled)TextButton(onClick={scope.launch{
                val tokens=TokenManager(context)
                if(tokens.isLoggedIn()){
                    val sub=try{JSONObject(String(Base64.decode(tokens.getAccessTokenSync()!!.split(".")[1],Base64.URL_SAFE or Base64.NO_WRAP))).optString("sub")}catch(_:Exception){""}
                    AlertNotifications.prepareTest(context)
                    AlertNotifications.show(context,mapOf("user_id" to sub,"alert_id" to AlertNotifications.TEST_ID.toString(),"title_vi" to "DMS · Thông báo thử","body_vi" to "Kiểm tra âm báo: bạn sẽ nghe tiếng nếu âm lượng thông báo đang bật và điện thoại không ở chế độ im lặng.","title_en" to "DMS · Test notification","body_en" to "Sound test: you should hear a tone when notification volume is on and your phone is not silenced."))
                }
            }}){Text(featureText("Gửi thông báo thử","Send test notification"))}

        }
    }
}
