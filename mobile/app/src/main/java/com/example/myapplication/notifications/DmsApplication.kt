package com.example.myapplication.notifications

import android.app.Application
import com.example.myapplication.data.local.TokenManager
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.FcmRegistration
import com.example.myapplication.BuildConfig
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

class DmsApplication: Application() {
    private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        AlertNotifications.createChannel(this)
        if(!BuildConfig.FIREBASE_CONFIGURED)return
        val tokens=TokenManager(this)
        appScope.launch {
            tokens.refreshToken.distinctUntilChanged().collectLatest { refresh ->
                if(refresh.isNullOrBlank()){AlertNotifications.clear(this@DmsApplication);return@collectLatest}
                tokens.isLoggedIn()
                RetrofitClient.init(tokens)
                // Re-register on every refresh rotation and retry temporary network failures.
                while(isActive) {
                    try {
                        val fcm=suspendCancellableCoroutine<String> { continuation ->
                            FirebaseMessaging.getInstance().token.addOnCompleteListener {
                                if(continuation.isActive) {
                                    if(it.isSuccessful)continuation.resumeWith(Result.success(it.result))
                                    else continuation.resumeWith(Result.failure(it.exception?:IllegalStateException("FCM unavailable")))
                                }
                            }
                        }
                        if(RetrofitClient.apiService.registerFcmToken(FcmRegistration(refresh,fcm)).isSuccessful)break
                    } catch(e:CancellationException){throw e} catch(_:Exception){}
                    delay(60000)
                }
            }
        }
    }
}
