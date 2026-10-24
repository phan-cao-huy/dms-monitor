package com.example.myapplication.data.api

import com.example.myapplication.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // === Auth ===
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<UserInfo>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<TokenResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    @GET("auth/me")
    suspend fun getMe(): Response<UserInfo>

    // === Organizations ===
    @GET("orgs")
    suspend fun getOrganizations(): Response<List<Organization>>

    @POST("orgs")
    suspend fun createOrganization(@Body request: CreateOrgRequest): Response<Organization>

    @POST("orgs/join")
    suspend fun joinOrganization(@Body request: JoinOrgRequest): Response<JoinOrgResponse>

    // === Organization Members ===
    @GET("orgs/{org_id}/members")
    suspend fun getOrgMembers(@Path("org_id") orgId: String): Response<List<OrgMember>>

    @PATCH("orgs/{org_id}/members/{user_id}")
    suspend fun updateMemberRole(
        @Path("org_id") orgId: String,
        @Path("user_id") userId: String,
        @Body request: UpdateMemberRoleRequest
    ): Response<OrgMember>

    @DELETE("orgs/{org_id}/members/{user_id}")
    suspend fun removeMember(
        @Path("org_id") orgId: String,
        @Path("user_id") userId: String
    ): Response<Unit>

    @POST("orgs/{org_id}/leave")
    suspend fun leaveOrganization(
        @Path("org_id") orgId: String
    ): Response<Map<String, String>>

    // === Device Groups (Cụm thiết bị) ===
    @GET("orgs/{org_id}/groups")
    suspend fun getGroups(@Path("org_id") orgId: String): Response<List<DeviceGroup>>

    @POST("orgs/{org_id}/groups")
    suspend fun createGroup(
        @Path("org_id") orgId: String,
        @Body request: CreateGroupRequest
    ): Response<DeviceGroup>

    @DELETE("groups/{group_id}")
    suspend fun deleteGroup(@Path("group_id") groupId: Int): Response<Unit>

    @GET("groups/{group_id}/members")
    suspend fun getGroupMembers(@Path("group_id") groupId: Int): Response<List<GroupMember>>

    @POST("groups/{group_id}/members")
    suspend fun assignGroupMember(
        @Path("group_id") groupId: Int,
        @Body request: AssignGroupMemberRequest
    ): Response<GroupMember>

    @DELETE("groups/{group_id}/members/{user_id}")
    suspend fun removeGroupMember(
        @Path("group_id") groupId: Int,
        @Path("user_id") userId: Int
    ): Response<Unit>

    @PATCH("devices/{id}")
    suspend fun updateDeviceGroup(
        @Path("id") deviceId: String,
        @Body request: UpdateDeviceGroupRequest
    ): Response<DeviceDetail>

    // === Invitations ===
    @GET("orgs/{org_id}/invitations")
    suspend fun getInvitations(@Path("org_id") orgId: String): Response<List<Invitation>>

    @POST("orgs/{org_id}/invitations")
    suspend fun createInvitation(
        @Path("org_id") orgId: String,
        @Body request: CreateInvitationRequest
    ): Response<Invitation>

    // === Devices ===
    @GET("orgs/{org_id}/devices")
    suspend fun getDevices(@Path("org_id") orgId: String): Response<List<Device>>

    @POST("orgs/{org_id}/devices")
    suspend fun createDevice(
        @Path("org_id") orgId: String,
        @Body request: CreateDeviceRequest
    ): Response<CreateDeviceResponse>

    @GET("devices/{id}")
    suspend fun getDeviceDetail(@Path("id") deviceId: String): Response<DeviceDetail>

    @POST("devices/{id}/toggle-monitoring")
    suspend fun toggleMonitoring(
        @Path("id") deviceId: String,
        @Body request: ToggleMonitoringRequest? = null
    ): Response<ToggleMonitoringResponse>

    @GET("devices/{id}/metrics")
    suspend fun getDeviceMetrics(
        @Path("id") deviceId: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("interval") interval: String = "raw"
    ): Response<List<Metric>>

    @GET("devices/{id}/processes")
    suspend fun getDeviceProcesses(@Path("id") deviceId: String): Response<List<ProcessInfo>>

    @POST("devices/{id}/commands")
    suspend fun sendCommand(
        @Path("id") deviceId: String,
        @Body request: CommandRequest
    ): Response<Command>

    @GET("devices/{id}/commands")
    suspend fun getCommandHistory(@Path("id") deviceId: String): Response<List<Command>>

    @DELETE("devices/{id}")
    suspend fun deleteDevice(@Path("id") deviceId: String): Response<Unit>

    // === Alerts ===
    @GET("orgs/{org_id}/alert-rules")
    suspend fun getAlertRules(@Path("org_id") orgId: String): Response<List<AlertRule>>

    @POST("orgs/{org_id}/alert-rules")
    suspend fun createAlertRule(
        @Path("org_id") orgId: String,
        @Body request: AlertRule
    ): Response<AlertRule>

    @GET("orgs/{org_id}/alerts")
    suspend fun getAlerts(
        @Path("org_id") orgId: String,
        @Query("resolved") resolved: Boolean? = null
    ): Response<List<Alert>>

    @POST("alerts/{id}/resolve")
    suspend fun resolveAlert(@Path("id") alertId: String): Response<Unit>

    // === App Update ===
    @GET("app/version")
    suspend fun getAppVersion(): Response<AppVersionResponse>
}
