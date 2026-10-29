package com.example.myapplication.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.myapplication.data.model.Device
import com.example.myapplication.data.model.UserInfo
import com.example.myapplication.ui.i18n.LocalAppStrings
import com.example.myapplication.ui.viewmodel.DashboardUiState
import com.example.myapplication.ui.viewmodel.OrganizationUiState

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

@Composable
fun MainScreen(
    dashboardState: DashboardUiState,
    orgState: OrganizationUiState,
    onDeviceClick: (String) -> Unit,
    onLogout: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefreshDashboard: () -> Unit,
    filteredDevices: List<Device>,
    onSelectGroupFilter: (Int?) -> Unit = {},
    onSelectOrg: (String) -> Unit = {},
    onCreateDevice: (String, String?) -> Unit = { _, _ -> },
    onDismissCreatedDevice: () -> Unit = {},
    // Org callbacks
    onLoadMembers: () -> Unit,
    onLoadInvitations: () -> Unit,
    onLoadGroups: () -> Unit = {},
    onUpdateRole: (String, String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onLeaveOrg: () -> Unit = {},
    onCreateGroup: (String, String?) -> Unit = { _, _ -> },
    onDeleteGroup: (Int) -> Unit = {},
    onAssignGroupMember: (Int, Int, String) -> Unit = { _, _, _ -> },
    onRemoveGroupMember: (Int, Int) -> Unit = { _, _ -> },
    onCreateInvitation: (String, Int?) -> Unit,
    onJoinOrg: (String) -> Unit,
    onCreateOrg: (String, String?) -> Unit,
    onClearOrgMessages: () -> Unit,
    // Alert callbacks
    onResolveAlert: (String) -> Unit,
    onRefreshAlerts: () -> Unit,
    // Settings callbacks & states
    currentTheme: String = "SYSTEM",
    currentLanguage: String = "VI",
    onThemeChange: (String) -> Unit = {},
    onLanguageChange: (String) -> Unit = {},
    notificationsEnabled: Boolean = true,
    alertCpu: Boolean = true,
    alertRam: Boolean = true,
    alertOffline: Boolean = true,
    onNotificationConfigChange: (Boolean, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    currentUser: UserInfo? = null,
    onNavigateToSettings: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var alertPane by remember(dashboardState.selectedOrg?.id) { mutableStateOf("history") }
    val strings = LocalAppStrings.current
    RefreshFeature(currentUser?.id to dashboardState.selectedOrg?.id,dashboardState.selectedOrg!=null){onRefreshDashboard()}

    val navItems = listOf(
        BottomNavItem(strings.navDevices, Icons.Default.Computer, Icons.Default.Computer),
        BottomNavItem(strings.navOrganization, Icons.Default.SpaceDashboard, Icons.Default.SpaceDashboard),
        BottomNavItem(strings.navAlerts, Icons.Default.Notifications, Icons.Default.Notifications),
        BottomNavItem(featureText("Công cụ","Tools"),Icons.Default.Tune,Icons.Default.Tune)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF142B24)) {
                navItems.forEachIndexed { index, item ->
                    val alertCount = if (index == 2) dashboardState.alerts.count { !it.isResolved } else 0
                    NavigationBarItem(
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = androidx.compose.ui.graphics.Color(0xFF142B24),
                            selectedTextColor = androidx.compose.ui.graphics.Color(0xFFC8F28B),
                            indicatorColor = androidx.compose.ui.graphics.Color(0xFFC8F28B),
                            unselectedIconColor = androidx.compose.ui.graphics.Color(0xFFA2B7AA),
                            unselectedTextColor = androidx.compose.ui.graphics.Color(0xFFA2B7AA)),
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            if (alertCount > 0) {
                                BadgedBox(badge = { Badge { Text(alertCount.toString()) } }) {
                                    Icon(
                                        if (selectedTab == index) item.selectedIcon else item.icon,
                                        contentDescription = item.label
                                    )
                                }
                            } else {
                                Icon(
                                    if (selectedTab == index) item.selectedIcon else item.icon,
                                    contentDescription = item.label
                                )
                            }
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    uiState = dashboardState,
                    onDeviceClick = onDeviceClick,
                    onSearchQueryChange = onSearchQueryChange,
                    onRefresh = onRefreshDashboard,
                    filteredDevices = filteredDevices,
                    onSelectGroupFilter = onSelectGroupFilter,
                    onSelectOrg = { org -> onSelectOrg(org.id) },
                    onCreateDevice = onCreateDevice,
                    onDismissCreatedDevice = onDismissCreatedDevice,
                    currentUser = currentUser,
                    onNavigateToSettings = onNavigateToSettings
                )
                1 -> OrganizationScreen(
                    uiState = orgState,
                    currentUserId = currentUser?.id,
                    onSelectOrg = onSelectOrg,
                    onLoadMembers = onLoadMembers,
                    onLoadInvitations = onLoadInvitations,
                    onLoadGroups = onLoadGroups,
                    onUpdateRole = onUpdateRole,
                    onRemoveMember = onRemoveMember,
                    onLeaveOrg = onLeaveOrg,
                    onCreateGroup = onCreateGroup,
                    onDeleteGroup = onDeleteGroup,
                    onAssignGroupMember = onAssignGroupMember,
                    onRemoveGroupMember = onRemoveGroupMember,
                    onCreateInvitation = onCreateInvitation,
                    onJoinOrg = onJoinOrg,
                    onCreateOrg = onCreateOrg,
                    onClearMessages = onClearOrgMessages
                )
                3 -> key(currentUser?.id,dashboardState.selectedOrg?.id){WorkspaceToolsScreen(dashboardState.selectedOrg,dashboardState.organizations,dashboardState.devices,onSelectOrg)}
                2 -> Column {
                    NotificationPermissionCard()
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick={alertPane="history"}){Text(featureText("Lịch sử","History"))}
                        TextButton(onClick={alertPane="rules"}){Text(featureText("Ngưỡng cảnh báo","Alert rules"))}
                        if(dashboardState.selectedOrg?.role in listOf("Owner","Admin"))TextButton(onClick={alertPane="email"}){Text("Email")}
                    }
                    if(alertPane=="rules")key(currentUser?.id,dashboardState.selectedOrg?.id){AlertRulesScreen(dashboardState.selectedOrg,dashboardState.devices,dashboardState.groups)}
                    else if(alertPane=="email")key(currentUser?.id,dashboardState.selectedOrg?.id){EmailChannelsScreen(dashboardState.selectedOrg)}
                    else AlertScreen(
                    alerts = dashboardState.alerts,
                    isLoading = dashboardState.isLoading,
                    onResolveAlert = onResolveAlert,
                    onRefresh = onRefreshAlerts
                )
                }
            }
        }
    }
}
