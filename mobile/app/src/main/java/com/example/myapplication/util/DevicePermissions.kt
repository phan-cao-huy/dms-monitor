package com.example.myapplication.util

object DevicePermissions {
    fun canControl(workspaceRole:String?,groupId:String?,groupRole:String?) =
        workspaceRole in listOf("Owner","Admin") || (workspaceRole=="Manager" && (groupId==null || groupRole=="Manager"))
}
