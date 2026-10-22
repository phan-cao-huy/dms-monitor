package com.example.myapplication.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.myapplication.data.model.DeviceGroup
import com.example.myapplication.data.model.GroupMember
import com.example.myapplication.data.model.Invitation
import com.example.myapplication.data.model.OrgMember
import com.example.myapplication.ui.viewmodel.OrganizationUiState
import kotlinx.coroutines.delay

// === Composable popup thông báo dạng Snackbar ===
@Composable
fun OrgToastNotification(
    message: String?,
    isSuccess: Boolean,
    onDismiss: () -> Unit
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = message ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationScreen(
    uiState: OrganizationUiState,
    currentUserId: String? = null,
    onSelectOrg: (String) -> Unit = {},
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
    onClearMessages: () -> Unit
) {
    val tabs = listOf("Thành viên", "Cụm thiết bị", "Lời mời", "Tham gia")
    var selectedTab by remember { mutableIntStateOf(0) }

    var popupMessage by remember { mutableStateOf<String?>(null) }
    var popupIsSuccess by remember { mutableStateOf(true) }
    var showOrgMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.successMessage) {
        val msg = uiState.successMessage
        if (msg != null) {
            popupIsSuccess = true
            popupMessage = msg
            if (selectedTab == 3) {
                selectedTab = 0
            }
            onClearMessages()
        }
    }
    LaunchedEffect(uiState.error) {
        val err = uiState.error
        if (err != null) {
            popupIsSuccess = false
            popupMessage = err
            onClearMessages()
        }
    }

    LaunchedEffect(Unit) {
        onLoadMembers()
        onLoadInvitations()
        onLoadGroups()
    }

    val myMember = uiState.members.find { it.userId == currentUserId }
    val isOwnerOrAdmin = myMember?.role == "Owner" || myMember?.role == "Admin"

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🏢 Quản lý tổ chức",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = uiState.organizations.isNotEmpty()) {
                                    showOrgMenu = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Business,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.orgName ?: "Chưa tham gia tổ chức nào",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (uiState.organizations.size > 1) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Chuyển tổ chức",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showOrgMenu,
                            onDismissRequest = { showOrgMenu = false }
                        ) {
                            Text(
                                text = "Chọn tổ chức quản lý",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider()
                            uiState.organizations.forEach { org ->
                                val isSelected = org.id == uiState.orgId
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = org.orgName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                                            )
                                            if (!org.description.isNullOrBlank()) {
                                                Text(
                                                    text = org.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Đang chọn",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        showOrgMenu = false
                                        if (!isSelected) {
                                            onSelectOrg(org.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Tabs (4 Tabs)
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 12.sp) },
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.People, contentDescription = null)
                                1 -> Icon(Icons.Default.GridView, contentDescription = null)
                                2 -> Icon(Icons.Default.Mail, contentDescription = null)
                                3 -> Icon(Icons.Default.GroupAdd, contentDescription = null)
                            }
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                0 -> MembersTab(
                    members = uiState.members,
                    currentUserId = currentUserId,
                    orgName = uiState.orgName ?: "tổ chức",
                    onUpdateRole = onUpdateRole,
                    onRemoveMember = onRemoveMember,
                    onLeaveOrg = onLeaveOrg
                )
                1 -> GroupsTab(
                    groups = uiState.groups,
                    groupMembers = uiState.groupMembers,
                    allOrgMembers = uiState.members,
                    isOwnerOrAdmin = isOwnerOrAdmin,
                    onCreateGroup = onCreateGroup,
                    onDeleteGroup = onDeleteGroup,
                    onAssignMember = onAssignGroupMember,
                    onRemoveMemberFromGroup = onRemoveGroupMember
                )
                2 -> InvitationsTab(uiState.invitations, onCreateInvitation)
                3 -> JoinTab(onJoinOrg, onCreateOrg)
            }
        }

        OrgToastNotification(
            message = popupMessage,
            isSuccess = popupIsSuccess,
            onDismiss = { popupMessage = null }
        )
    }
}

// === Tab 0: Danh sách thành viên + Tự rời tổ chức ===
@Composable
fun MembersTab(
    members: List<OrgMember>,
    currentUserId: String?,
    orgName: String,
    onUpdateRole: (String, String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onLeaveOrg: () -> Unit
) {
    var showRoleDialog by remember { mutableStateOf<OrgMember?>(null) }
    var showRemoveDialog by remember { mutableStateOf<OrgMember?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("") }

    val myMember = members.find { it.userId == currentUserId }
    val isMeAdminOrOwner = myMember?.role == "Owner" || myMember?.role == "Admin"

    // Role change dialog
    showRoleDialog?.let { member ->
        val roles = listOf("Admin", "Manager", "Viewer")
        AlertDialog(
            onDismissRequest = { showRoleDialog = null },
            title = { Text("Đổi quyền: ${member.fullName ?: member.username}") },
            text = {
                Column {
                    Text("Quyền hiện tại: ${member.role}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    roles.forEach { role ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(role, fontWeight = FontWeight.Medium)
                                Text(
                                    text = when (role) {
                                        "Admin" -> "Quản trị viên - toàn quyền trừ xóa tổ chức"
                                        "Manager" -> "Quản lý - xem và điều khiển thiết bị"
                                        else -> "Người xem - chỉ xem thông tin"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateRole(member.userId, selectedRole)
                        showRoleDialog = null
                    },
                    enabled = selectedRole.isNotBlank() && selectedRole != member.role
                ) { Text("Cập nhật") }
            },
            dismissButton = {
                TextButton(onClick = { showRoleDialog = null }) { Text("Hủy") }
            }
        )
    }

    // Remove member dialog (Admin xoá thành viên khác)
    showRemoveDialog?.let { member ->
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = { Text("Xóa thành viên") },
            text = { Text("Bạn chắc chắn muốn xóa '${member.fullName ?: member.username}' khỏi tổ chức?") },
            confirmButton = {
                Button(
                    onClick = { onRemoveMember(member.userId); showRemoveDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) { Text("Hủy") }
            }
        )
    }

    // Leave organization dialog (Người dùng tự rời)
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Rời tổ chức") },
            text = {
                Text("Bạn có chắc chắn muốn rời khỏi '$orgName'?\nBạn sẽ không thể xem hoặc điều khiển các thiết bị trong tổ chức này nữa.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        onLeaveOrg()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Rời tổ chức") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Hủy") }
            }
        )
    }

    if (members.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Chưa có thành viên nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${members.size} thành viên",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Nút tự rời tổ chức nếu không phải Owner
                if (myMember != null && myMember.role != "Owner") {
                    OutlinedButton(
                        onClick = { showLeaveDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rời tổ chức", fontSize = 12.sp)
                    }
                }
            }
        }

        items(members) { member ->
            val isThisMe = member.userId == currentUserId
            val roleColor = when (member.role) {
                "Owner" -> Color(0xFFD32F2F)
                "Admin" -> Color(0xFFE65100)
                "Manager" -> Color(0xFF1565C0)
                else -> Color.Gray
            }
            val roleIcon = when (member.role) {
                "Owner" -> Icons.Default.Star
                "Admin" -> Icons.Default.Shield
                "Manager" -> Icons.Default.ManageAccounts
                else -> Icons.Default.Person
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(roleIcon, contentDescription = null, tint = roleColor, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = member.fullName ?: member.username,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (isThisMe) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Tôi",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Text(
                            text = "@${member.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        member.email?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Role badge
                    AssistChip(
                        onClick = {
                            if (isMeAdminOrOwner && member.role != "Owner" && !isThisMe) {
                                selectedRole = member.role
                                showRoleDialog = member
                            }
                        },
                        label = { Text(member.role, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = roleColor.copy(alpha = 0.1f))
                    )

                    // Nút xóa thành viên khác (Chỉ Owner/Admin mới thấy)
                    if (isMeAdminOrOwner && member.role != "Owner" && !isThisMe) {
                        IconButton(onClick = { showRemoveDialog = member }) {
                            Icon(
                                Icons.Default.PersonRemove,
                                contentDescription = "Xóa",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// === Tab 1: Phân cụm thiết bị & Phân quyền quản lý cụ thể ===
@Composable
fun GroupsTab(
    groups: List<DeviceGroup>,
    groupMembers: Map<Int, List<GroupMember>>,
    allOrgMembers: List<OrgMember>,
    isOwnerOrAdmin: Boolean,
    onCreateGroup: (String, String?) -> Unit,
    onDeleteGroup: (Int) -> Unit,
    onAssignMember: (Int, Int, String) -> Unit,
    onRemoveMemberFromGroup: (Int, Int) -> Unit
) {
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupDesc by remember { mutableStateOf("") }

    var assigningGroupId by remember { mutableStateOf<Int?>(null) }
    var selectedUserId by remember { mutableStateOf<Int?>(null) }
    var selectedGroupRole by remember { mutableStateOf("Viewer") }

    // Dialog tạo cụm mới
    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Tạo cụm thiết bị mới") },
            text = {
                Column {
                    Text("Phân cụm giúp bạn nhóm các máy tính lại và ủy quyền cho người khác quản lý hoặc xem cụ thể:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Tên cụm (VD: Phòng Kế Toán, Server Web)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newGroupDesc,
                        onValueChange = { newGroupDesc = it },
                        label = { Text("Mô tả (tùy chọn)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateGroup(newGroupName.trim(), newGroupDesc.trim())
                        showCreateGroupDialog = false
                        newGroupName = ""
                        newGroupDesc = ""
                    },
                    enabled = newGroupName.isNotBlank()
                ) { Text("Tạo cụm") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) { Text("Hủy") }
            }
        )
    }

    // Dialog cấp quyền người dùng vào cụm
    assigningGroupId?.let { grpId ->
        AlertDialog(
            onDismissRequest = { assigningGroupId = null; selectedUserId = null },
            title = { Text("Cấp quyền thành viên vào cụm") },
            text = {
                Column {
                    Text("Chọn thành viên trong tổ chức:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    allOrgMembers.forEach { m ->
                        val uid = m.userId.toIntOrNull() ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedUserId = uid }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedUserId == uid,
                                onClick = { selectedUserId = uid }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(m.fullName ?: m.username, fontWeight = FontWeight.Medium)
                                Text("@${m.username} · ${m.role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Quyền hạn trên các máy thuộc cụm này:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedGroupRole == "Manager",
                            onClick = { selectedGroupRole = "Manager" }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Quản lý (Manager)", fontWeight = FontWeight.Bold)
                            Text("Được xem + gửi lệnh (Reboot, Kill process)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedGroupRole == "Viewer",
                            onClick = { selectedGroupRole = "Viewer" }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Người xem (Viewer)", fontWeight = FontWeight.Bold)
                            Text("Chỉ xem thông số, KHÔNG được gửi lệnh", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedUserId?.let { uid ->
                            onAssignMember(grpId, uid, selectedGroupRole)
                        }
                        assigningGroupId = null
                        selectedUserId = null
                    },
                    enabled = selectedUserId != null
                ) { Text("Cấp quyền") }
            },
            dismissButton = {
                TextButton(onClick = { assigningGroupId = null; selectedUserId = null }) { Text("Hủy") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isOwnerOrAdmin) {
            item {
                Button(
                    onClick = { showCreateGroupDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tạo cụm thiết bị mới")
                }
            }
        }

        if (groups.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Chưa có cụm thiết bị nào", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tạo cụm thiết bị để nhóm các máy lại (VD: Server, Kế toán, Đồ họa) và phân quyền cho người khác chỉ quản lý hoặc chỉ xem các máy đó.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(groups) { group ->
            val membersInGroup = groupMembers[group.groupId].orEmpty()

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(group.groupName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                group.description?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        if (isOwnerOrAdmin) {
                            IconButton(onClick = { onDeleteGroup(group.groupId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa cụm", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    Text("Thành viên được phân quyền trong cụm:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (membersInGroup.isEmpty()) {
                        Text("Chưa gán quyền cho ai (Chỉ Owner & Admin thấy các máy trong cụm này)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        membersInGroup.forEach { gm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(gm.fullName ?: gm.username, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (gm.role == "Manager") Color(0xFF1565C0).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (gm.role == "Manager") "Quản lý (Xem & Lệnh)" else "Chỉ xem",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (gm.role == "Manager") Color(0xFF1565C0) else Color.DarkGray,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (isOwnerOrAdmin) {
                                    IconButton(
                                        onClick = { onRemoveMemberFromGroup(group.groupId, gm.userId) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Thu hồi quyền", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (isOwnerOrAdmin) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { assigningGroupId = group.groupId },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cấp quyền cho thành viên vào cụm này")
                        }
                    }
                }
            }
        }
    }
}

// === Tab 2: Lời mời + Tạo mã + QR Code ===
@Composable
fun InvitationsTab(
    invitations: List<Invitation>,
    onCreateInvitation: (String, Int?) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf<Invitation?>(null) }
    var newRole by remember { mutableStateOf("Viewer") }
    var newMaxUses by remember { mutableStateOf("10") }
    val context = LocalContext.current

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Tạo mã mời mới") },
            text = {
                Column {
                    Text("Quyền cho người được mời:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        listOf("Manager", "Viewer").forEach { role ->
                            FilterChip(
                                selected = newRole == role,
                                onClick = { newRole = role },
                                label = { Text(role) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newMaxUses,
                        onValueChange = { newMaxUses = it.filter { c -> c.isDigit() } },
                        label = { Text("Số lần sử dụng tối đa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreateInvitation(newRole, newMaxUses.toIntOrNull())
                    showCreateDialog = false
                }) { Text("Tạo mã") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Hủy") }
            }
        )
    }

    showQrDialog?.let { invitation ->
        val qrBitmap = remember(invitation.inviteCode) {
            generateQrBitmap(invitation.inviteCode)
        }
        AlertDialog(
            onDismissRequest = { showQrDialog = null },
            title = { Text("Mã QR - ${invitation.inviteCode}", textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    } ?: Text("Không thể tạo QR")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Mã mời: ${invitation.inviteCode}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Quyền: ${invitation.role} · Đã dùng: ${invitation.usedCount}/${invitation.maxUses ?: "∞"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Invite Code", invitation.inviteCode))
                        Toast.makeText(context, "Đã copy mã mời!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy mã")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { showQrDialog = null }) { Text("Đóng") }
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tạo mã mời mới")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (invitations.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Chưa có mã mời nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(invitations) { invitation ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = invitation.inviteCode,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Quyền: ${invitation.role}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Invite Code", invitation.inviteCode))
                                Toast.makeText(context, "Đã copy!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showQrDialog = invitation }) {
                                Icon(Icons.Default.QrCode, contentDescription = "QR", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (invitation.maxUses != null && invitation.maxUses > 0) invitation.usedCount.toFloat() / invitation.maxUses else 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Đã dùng: ${invitation.usedCount}/${invitation.maxUses ?: "Không giới hạn"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// === Tab 3: Tham gia tổ chức ===
@Composable
fun JoinTab(
    onJoinOrg: (String) -> Unit,
    onCreateOrg: (String, String?) -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
    var orgName by remember { mutableStateOf("") }
    var orgDesc by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔑 Tham gia bằng mã mời",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase() },
                    label = { Text("Nhập mã mời (VD: A3K9MZ)") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onJoinOrg(inviteCode); inviteCode = "" },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = inviteCode.isNotBlank()
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tham gia")
                }
            }
        }

        HorizontalDivider()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "➕ Tạo tổ chức mới",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = orgName,
                    onValueChange = { orgName = it },
                    label = { Text("Tên tổ chức") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = orgDesc,
                    onValueChange = { orgDesc = it },
                    label = { Text("Mô tả (tùy chọn)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onCreateOrg(orgName, orgDesc.ifBlank { null })
                        orgName = ""
                        orgDesc = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = orgName.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tạo tổ chức")
                }
            }
        }
    }
}

// === QR Code Generator ===
fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val blockSize = size / (content.length + 8)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }

        for (i in 0 until 7) {
            for (j in 0 until 7) {
                if (i == 0 || i == 6 || j == 0 || j == 6 || (i in 2..4 && j in 2..4)) {
                    canvas.drawRect(
                        (i * blockSize).toFloat(), (j * blockSize).toFloat(),
                        ((i + 1) * blockSize).toFloat(), ((j + 1) * blockSize).toFloat(),
                        paint
                    )
                }
            }
        }

        val offset = 8 * blockSize
        content.forEachIndexed { index, char ->
            val code = char.code
            for (bit in 0 until 8) {
                if ((code shr bit) and 1 == 1) {
                    val x = offset + ((index * 8 + bit) % (size / blockSize - 16)) * blockSize
                    val y = offset + ((index * 8 + bit) / (size / blockSize - 16)) * blockSize
                    if (x + blockSize <= size && y + blockSize <= size) {
                        canvas.drawRect(
                            x.toFloat(), y.toFloat(),
                            (x + blockSize).toFloat(), (y + blockSize).toFloat(),
                            paint
                        )
                    }
                }
            }
        }

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = (size / 12).toFloat()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText(content, size / 2f, size - 20f, textPaint)

        bitmap
    } catch (e: Exception) {
        null
    }
}
