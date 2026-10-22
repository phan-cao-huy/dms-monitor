package com.example.myapplication.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.local.TokenManager
import com.example.myapplication.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class OrganizationUiState(
    val isLoading: Boolean = false,
    val orgId: String? = null,
    val orgName: String? = null,
    val members: List<OrgMember> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
    val groups: List<DeviceGroup> = emptyList(),
    val groupMembers: Map<Int, List<GroupMember>> = emptyMap(),
    val error: String? = null,
    val successMessage: String? = null
)

class OrganizationViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val _uiState = MutableStateFlow(OrganizationUiState())
    val uiState: StateFlow<OrganizationUiState> = _uiState.asStateFlow()

    fun resetState() {
        // Huỷ tất cả job đang chạy để tránh network call cũ ghi đè state mới
        viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        _uiState.value = OrganizationUiState()
    }

    fun loadData() {
        loadMembers()
        loadInvitations()
        loadGroups()
    }

    fun loadMembers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                var orgId = tokenManager.currentOrgId.first()
                val orgsResponse = try {
                    RetrofitClient.apiService.getOrganizations()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }

                val orgList = if (orgsResponse?.isSuccessful == true) orgsResponse.body().orEmpty() else emptyList()

                if (orgList.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        orgId = null,
                        orgName = null,
                        members = emptyList(),
                        invitations = emptyList(),
                        groups = emptyList(),
                        groupMembers = emptyMap()
                    )
                    return@launch
                }

                val currentOrg = orgList.find { it.id == orgId } ?: orgList.first()
                orgId = currentOrg.id
                tokenManager.saveOrgId(orgId)
                _uiState.value = _uiState.value.copy(orgId = orgId, orgName = currentOrg.orgName)

                val response = RetrofitClient.apiService.getOrgMembers(orgId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, members = response.body()!!)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Không thể tải danh sách thành viên (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể kết nối server: ${e.message}"
                )
            }
        }
    }

    fun loadInvitations() {
        viewModelScope.launch {
            try {
                val orgId = _uiState.value.orgId ?: tokenManager.currentOrgId.first() ?: return@launch
                val response = RetrofitClient.apiService.getInvitations(orgId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(invitations = response.body()!!)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            try {
                val orgId = _uiState.value.orgId ?: tokenManager.currentOrgId.first() ?: return@launch
                val response = RetrofitClient.apiService.getGroups(orgId)
                if (response.isSuccessful && response.body() != null) {
                    val groups = response.body()!!
                    _uiState.value = _uiState.value.copy(groups = groups)
                    // Tải danh sách thành viên cho từng nhóm
                    groups.forEach { group ->
                        loadGroupMembers(group.groupId)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun loadGroupMembers(groupId: Int) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getGroupMembers(groupId)
                if (response.isSuccessful && response.body() != null) {
                    val currentMap = _uiState.value.groupMembers.toMutableMap()
                    currentMap[groupId] = response.body()!!
                    _uiState.value = _uiState.value.copy(groupMembers = currentMap)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun createGroup(name: String, description: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val orgId = _uiState.value.orgId ?: tokenManager.currentOrgId.first() ?: return@launch
                val response = RetrofitClient.apiService.createGroup(
                    orgId,
                    CreateGroupRequest(groupName = name.trim(), description = description?.ifBlank { null })
                )
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Đã tạo cụm thiết bị '$name' thành công!"
                    )
                    loadGroups()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Tạo cụm thiết bị thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể tạo cụm: ${e.message}"
                )
            }
        }
    }

    fun deleteGroup(groupId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val response = RetrofitClient.apiService.deleteGroup(groupId)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Đã xóa cụm thiết bị thành công"
                    )
                    loadGroups()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Xóa cụm thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể xóa cụm: ${e.message}"
                )
            }
        }
    }

    fun assignGroupMember(groupId: Int, userId: Int, role: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val response = RetrofitClient.apiService.assignGroupMember(
                    groupId,
                    AssignGroupMemberRequest(userId = userId, role = role)
                )
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Đã cấp quyền $role cho thành viên trong cụm!"
                    )
                    loadGroupMembers(groupId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Cấp quyền thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Lỗi khi cấp quyền: ${e.message}"
                )
            }
        }
    }

    fun removeGroupMember(groupId: Int, userId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val response = RetrofitClient.apiService.removeGroupMember(groupId, userId)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Đã xóa quyền thành viên khỏi cụm"
                    )
                    loadGroupMembers(groupId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Xóa quyền thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Lỗi khi xóa quyền: ${e.message}"
                )
            }
        }
    }

    fun updateMemberRole(userId: String, newRole: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val orgId = _uiState.value.orgId ?: tokenManager.currentOrgId.first() ?: return@launch
                val response = RetrofitClient.apiService.updateMemberRole(
                    orgId, userId, UpdateMemberRoleRequest(role = newRole)
                )
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Đã cập nhật quyền thành $newRole"
                    )
                    loadMembers()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Cập nhật thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể cập nhật quyền: ${e.message}"
                )
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val orgId = _uiState.value.orgId ?: tokenManager.currentOrgId.first() ?: return@launch
                val response = RetrofitClient.apiService.removeMember(orgId, userId)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Đã xóa thành viên khỏi tổ chức"
                    )
                    loadMembers()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Xóa thành viên thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể xóa thành viên: ${e.message}"
                )
            }
        }
    }

    fun leaveOrganization(onLeft: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val orgId = _uiState.value.orgId ?: tokenManager.currentOrgId.first() ?: return@launch
                val response = RetrofitClient.apiService.leaveOrganization(orgId)
                if (response.isSuccessful) {
                    tokenManager.saveOrgId("")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Bạn đã rời khỏi tổ chức thành công!"
                    )
                    loadData()
                    onLeft()
                } else {
                    val errorMsg = when (response.code()) {
                        403 -> "Chủ sở hữu (Owner) không thể rời tổ chức. Bạn cần chuyển quyền hoặc xóa tổ chức."
                        else -> "Rời tổ chức thất bại (${response.code()})"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể rời tổ chức: ${e.message}"
                )
            }
        }
    }

    fun createInvitation(role: String, maxUses: Int?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val orgId = _uiState.value.orgId ?: tokenManager.currentOrgId.first() ?: return@launch
                val response = RetrofitClient.apiService.createInvitation(
                    orgId, CreateInvitationRequest(role = role, maxUses = maxUses)
                )
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Đã tạo mã mời: ${response.body()!!.inviteCode}"
                    )
                    loadInvitations()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Tạo mã mời thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể tạo mã mời: ${e.message}"
                )
            }
        }
    }

    fun joinOrganization(inviteCode: String, onJoined: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val response = RetrofitClient.apiService.joinOrganization(
                    JoinOrgRequest(inviteCode = inviteCode.trim().uppercase())
                )
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Đã tham gia tổ chức thành công!"
                    )
                    loadData()
                    onJoined()
                } else {
                    val errorMsg = when (response.code()) {
                        404 -> "Mã mời không tồn tại hoặc đã hết hạn"
                        409 -> "Bạn đã là thành viên của tổ chức này rồi"
                        410 -> "Mã mời đã hết lượt sử dụng"
                        else -> "Tham gia thất bại (${response.code()})"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể tham gia tổ chức: ${e.message}"
                )
            }
        }
    }

    fun createOrganization(orgName: String, description: String?, onCreated: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val response = RetrofitClient.apiService.createOrganization(
                    CreateOrgRequest(orgName = orgName.trim(), description = description?.trim())
                )
                if (response.isSuccessful && response.body() != null) {
                    val created = response.body()!!
                    tokenManager.saveOrgId(created.id)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Đã tạo tổ chức '$orgName' thành công!"
                    )
                    loadData()
                    onCreated()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Tạo tổ chức thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể tạo tổ chức: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
