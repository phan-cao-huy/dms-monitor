package com.example.myapplication.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.myapplication.data.local.TokenManager
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.FcmRegistration
import kotlinx.coroutines.runBlocking

class DmsMessagingService:FirebaseMessagingService(){
    override fun onMessageReceived(message:RemoteMessage){
        // Local DataStore checks and posting only; no network work on delivery.
        runBlocking { AlertNotifications.show(this@DmsMessagingService,message.data) }
    }
    override fun onNewToken(token:String){
        // The Application collector also registers on startup/session rotation.
        runBlocking {
            val tokens=TokenManager(this@DmsMessagingService)
            if(tokens.isLoggedIn()){
                RetrofitClient.init(tokens)
                try{RetrofitClient.apiService.registerFcmToken(FcmRegistration(tokens.getRefreshTokenSync()?:return@runBlocking,token))}
                catch(_:Exception){}
            }
        }
    }
}
