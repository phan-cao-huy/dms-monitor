package com.example.myapplication.util

object DeviceConnection {
    fun isOnline(status:String?)=status=="Online"||status=="Warning"
}
