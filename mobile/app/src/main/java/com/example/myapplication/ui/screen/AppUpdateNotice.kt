package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.data.model.AppVersionResponse
import com.example.myapplication.ui.i18n.LocalAppStrings
import com.example.myapplication.util.AppUpdateManager
import kotlinx.coroutines.delay

@Composable
fun AppUpdateNotice(enabled:Boolean,onOpenUpdater:()->Unit){
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val en=LocalAppStrings.current.isEnglish
    var available by remember { mutableStateOf<AppVersionResponse?>(null) }
    var dismissedVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(enabled,lifecycle){
        if(!enabled){available=null;return@LaunchedEffect}
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            while(true){
                AppUpdateManager.checkForUpdate(context).onSuccess{(info,newer)->
                    available=if(newer&&info.versionCode!=dismissedVersion)info else null
                }
                delay(15*60*1000L)
            }
        }
    }
    val info=available
    if(enabled&&info!=null&&info.versionCode>AppUpdateManager.getCurrentVersionCode(context)){
        AlertDialog(onDismissRequest={dismissedVersion=info.versionCode;available=null},
            title={Text(if(en)"Update available" else "Có bản cập nhật mới")},
            text={Column{
                Text(if(en)"DMS Monitor ${info.versionName}" else "DMS Monitor phiên bản ${info.versionName}")
                Text(info.releaseNotes.orEmpty(),style=MaterialTheme.typography.bodyMedium)
            }},
            confirmButton={Button(onClick={dismissedVersion=info.versionCode;available=null;onOpenUpdater()}){
                Text(if(en)"View & download" else "Xem và tải cập nhật")
            }},
            dismissButton={TextButton(onClick={dismissedVersion=info.versionCode;available=null}){
                Text(if(en)"Later" else "Để sau")
            }})
    }
}
