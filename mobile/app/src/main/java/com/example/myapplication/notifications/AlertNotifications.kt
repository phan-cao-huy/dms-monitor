package com.example.myapplication.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.local.TokenManager
import com.example.myapplication.data.local.SettingsManager
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import android.util.Base64

object AlertNotifications {
    const val CHANNEL="dms_alerts"
    fun createChannel(context:Context){
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"Cảnh báo thiết bị",NotificationManager.IMPORTANCE_HIGH).apply{
                description="Cảnh báo vượt ngưỡng và mất kết nối từ DMS";enableVibration(true)
            })
    }
    fun clear(context:Context){NotificationManagerCompat.from(context).cancelAll()}
    suspend fun show(context:Context,data:Map<String,String>){
        val tokens=TokenManager(context)
        if(!tokens.isLoggedIn())return
        // Ignore delayed messages for a previous signed-in account on the same phone.
        val sub=try {
            val payload=tokens.getAccessTokenSync()!!.split(".")[1]
            JSONObject(String(Base64.decode(payload,Base64.URL_SAFE or Base64.NO_WRAP))).optString("sub")
        }catch(_:Exception){return}
        if(data["user_id"]!=sub)return
        val settings=SettingsManager(context)
        if(!settings.notificationsEnabled.first())return
        val kind=data["metric_type"]
        if(kind=="CPU"&&!settings.alertCpu.first()||kind=="RAM"&&!settings.alertRam.first()||kind=="OFFLINE"&&!settings.alertOffline.first())return
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        if(!NotificationManagerCompat.from(context).areNotificationsEnabled())return
        createChannel(context)
        val language=if(settings.language.first()=="EN")"en" else "vi"
        val title=data["title_$language"]?:data["title"]?:"DMS Monitor"
        val body=data["body_$language"]?:data["body"]?:if(language=="en")"Open DMS Monitor for details." else "Mở DMS Monitor để xem chi tiết."
        val id=data["alert_id"]?.toIntOrNull()?:return
        val pending=PendingIntent.getActivity(context,id,Intent(context,MainActivity::class.java).apply{
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(pending).build()
        NotificationManagerCompat.from(context).notify(id,notification)
    }
}
