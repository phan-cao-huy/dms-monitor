package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.ui.screen.DeviceDetailScreen
import com.example.myapplication.ui.screen.LoginScreen
import com.example.myapplication.ui.screen.MainScreen
import com.example.myapplication.ui.screen.ProfileScreen
import com.example.myapplication.ui.screen.RegisterScreen
import com.example.myapplication.ui.viewmodel.AuthViewModel
import com.example.myapplication.ui.viewmodel.DashboardViewModel
import com.example.myapplication.ui.viewmodel.DeviceDetailViewModel
import com.example.myapplication.ui.viewmodel.OrganizationViewModel

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object Main : Screen("main")
    data object Settings : Screen("settings")
    data object DeviceDetail : Screen("device_detail/{deviceId}") {
        fun createRoute(deviceId: String) = "device_detail/$deviceId"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    currentTheme: String = "SYSTEM",
    currentLanguage: String = "VI",
    onThemeChange: (String) -> Unit = {},
    onLanguageChange: (String) -> Unit = {},
    notificationsEnabled: Boolean = true,
    alertCpu: Boolean = true,
    alertRam: Boolean = true,
    alertOffline: Boolean = true,
    onNotificationConfigChange: (Boolean, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> }
) {
    val authState by authViewModel.uiState.collectAsState()
    val serverUrl by authViewModel.serverUrl.collectAsState()

    val dashboardViewModel: DashboardViewModel = viewModel()
    val orgViewModel: OrganizationViewModel = viewModel()
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val orgState by orgViewModel.uiState.collectAsState()

    // Điều hướng theo trạng thái đăng nhập và reset dữ liệu tránh rò rỉ giữa các tài khoản.
    // Dùng cặp (isLoggedIn, user?.id) để đảm bảo navigate SAU KHI user đã được load xong,
    // tránh hiển thị tên tài khoản cũ ở header.
    val authKey = Pair(authState.isLoggedIn, authState.user?.id)
    LaunchedEffect(authKey) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (authState.isLoggedIn && authState.user != null) {
            // Reset và tải lại dữ liệu cho tài khoản mới
            dashboardViewModel.loadOrganizations()
            orgViewModel.loadData()
            if (currentRoute != Screen.Main.route) {
                navController.navigate(Screen.Main.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        } else if (!authState.isLoggedIn) {
            dashboardViewModel.resetState()
            orgViewModel.resetState()
        }
    }

    val handleLogout: () -> Unit = {
        // Reset state NGAY LẬP TỨC trước để UI không render dữ liệu cũ
        dashboardViewModel.resetState()
        orgViewModel.resetState()
        // Logout async (thu hồi token server-side) + xóa token local
        authViewModel.logout()
        // Navigate về login, xoá toàn bộ back-stack
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        // Login
        composable(Screen.Login.route) {
            LoginScreen(
                uiState = authState,
                serverUrl = serverUrl,
                onUpdateServerUrl = { authViewModel.updateServerUrl(it) },
                onLogin = { identifier, password ->
                    authViewModel.login(identifier, password)
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onClearError = { authViewModel.clearError() }
            )
        }

        // Register
        composable(Screen.Register.route) {
            RegisterScreen(
                uiState = authState,
                onRegister = { username, email, password, fullName ->
                    authViewModel.register(username, email, password, fullName)
                },
                onNavigateBack = { navController.popBackStack() },
                onClearError = { authViewModel.clearError() }
            )
        }

        // Main (3-tab bottom navigation: Devices, Org, Alerts)
        composable(Screen.Main.route) {
            // Không cần gọi load thêm ở đây vì LaunchedEffect(authKey) đã xử lý rồi.
            // refreshCurrentOrgDevices chỉ khi đã có org (tránh gọi khi vừa login state chưa sẵn sàng)
            LaunchedEffect(dashboardState.selectedOrg?.id) {
                val orgId = dashboardState.selectedOrg?.id
                if (orgId != null) {
                    dashboardViewModel.refreshCurrentOrgDevices()
                }
            }

            MainScreen(
                dashboardState = dashboardState,
                orgState = orgState,
                onDeviceClick = { deviceId ->
                    navController.navigate(Screen.DeviceDetail.createRoute(deviceId))
                },
                onLogout = handleLogout,
                onSearchQueryChange = { dashboardViewModel.updateSearchQuery(it) },
                onRefreshDashboard = {
                    dashboardViewModel.loadOrganizations()
                    orgViewModel.loadData()
                },
                filteredDevices = dashboardViewModel.getFilteredDevices(),
                onSelectGroupFilter = { dashboardViewModel.selectGroupFilter(it) },
                onSelectOrg = { orgId ->
                    orgViewModel.selectOrganization(orgId)
                    val org = dashboardState.organizations.find { it.id == orgId }
                    if (org != null) {
                        dashboardViewModel.selectOrganization(org)
                    } else {
                        dashboardViewModel.loadOrganizations()
                    }
                },
                onCreateDevice = { name, grpId -> dashboardViewModel.createDevice(name, grpId) },
                onDismissCreatedDevice = { dashboardViewModel.clearNewlyCreatedDevice() },
                // Org callbacks
                onLoadMembers = { orgViewModel.loadMembers() },
                onLoadInvitations = { orgViewModel.loadInvitations() },
                onLoadGroups = { orgViewModel.loadGroups() },
                onUpdateRole = { userId, role -> orgViewModel.updateMemberRole(userId, role) },
                onRemoveMember = { orgViewModel.removeMember(it) },
                onLeaveOrg = {
                    orgViewModel.leaveOrganization {
                        dashboardViewModel.loadOrganizations()
                    }
                },
                onCreateGroup = { name, desc -> orgViewModel.createGroup(name, desc) },
                onDeleteGroup = { orgViewModel.deleteGroup(it) },
                onAssignGroupMember = { grpId, uId, role -> orgViewModel.assignGroupMember(grpId, uId, role) },
                onRemoveGroupMember = { grpId, uId -> orgViewModel.removeGroupMember(grpId, uId) },
                onCreateInvitation = { role, maxUses -> orgViewModel.createInvitation(role, maxUses) },
                onJoinOrg = { code ->
                    orgViewModel.joinOrganization(code) {
                        dashboardViewModel.loadOrganizations()
                    }
                },
                onCreateOrg = { name, desc ->
                    orgViewModel.createOrganization(name, desc) {
                        dashboardViewModel.loadOrganizations()
                    }
                },
                onClearOrgMessages = { orgViewModel.clearMessages() },
                // Alert callbacks
                onResolveAlert = { alertId -> dashboardViewModel.resolveAlert(alertId) },
                onRefreshAlerts = { dashboardViewModel.loadOrganizations() },
                // Settings callbacks
                currentTheme = currentTheme,
                currentLanguage = currentLanguage,
                onThemeChange = onThemeChange,
                onLanguageChange = onLanguageChange,
                notificationsEnabled = notificationsEnabled,
                alertCpu = alertCpu,
                alertRam = alertRam,
                alertOffline = alertOffline,
                onNotificationConfigChange = onNotificationConfigChange,
                currentUser = authState.user,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // Settings (ProfileScreen as separate screen)
        composable(Screen.Settings.route) {
            ProfileScreen(
                user = authState.user,
                onLogout = handleLogout,
                onSwitchAccount = handleLogout,
                currentTheme = currentTheme,
                currentLanguage = currentLanguage,
                onThemeChange = onThemeChange,
                onLanguageChange = onLanguageChange,
                notificationsEnabled = notificationsEnabled,
                alertCpu = alertCpu,
                alertRam = alertRam,
                alertOffline = alertOffline,
                onNotificationConfigChange = onNotificationConfigChange
            )
        }

        // Device Detail
        composable(Screen.DeviceDetail.route) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            val detailViewModel: DeviceDetailViewModel = viewModel()
            val detailState by detailViewModel.uiState.collectAsState()

            LaunchedEffect(deviceId) {
                detailViewModel.loadDevice(deviceId)
            }

            DeviceDetailScreen(
                uiState = detailState,
                deviceId = deviceId,
                groups = dashboardState.groups,
                onBack = { navController.popBackStack() },
                onTabSelect = { detailViewModel.selectTab(it) },
                onSendCommand = { id, type, params, reason ->
                    detailViewModel.sendCommand(id, type, params, reason)
                },
                onClearMessages = { detailViewModel.clearMessages() },
                onToggleMonitoring = { devId, enable ->
                    detailViewModel.toggleMonitoring(devId, enable)
                },
                onUpdateDeviceGroup = { devId, grpId ->
                    detailViewModel.updateDeviceGroup(devId, grpId) {
                        dashboardViewModel.refreshCurrentOrgDevices()
                    }
                },
                onDeleteDevice = {
                    detailViewModel.deleteDevice(deviceId) {
                        dashboardViewModel.removeDeviceLocally(deviceId)
                        dashboardViewModel.refreshCurrentOrgDevices()
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
