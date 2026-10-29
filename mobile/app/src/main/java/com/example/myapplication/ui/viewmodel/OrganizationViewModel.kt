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
    val organizations: List<Organization> = emptyList(),
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
        refreshGeneration++
        _uiState.value = OrganizationUiState()
    }

    private var refreshGeneration = 0
    private var refreshJob: kotlinx.coroutines.Job? = null

    fun selectOrganization(orgId: String) {
        if (_uiState.value.orgId != orgId) {
            refreshGeneration++
            refreshJob?.cancel()
            _uiState.value = _uiState.value.copy(orgId=orgId,orgName=null,members=emptyList(),groups=emptyList(),invitations=emptyList(),groupMembers=emptyMap(),error=null)
        }
        loadData(orgId)
    }

    fun loadData(targetOrgId: String? = null) {
        val generation=++refreshGeneration
        refreshJob?.cancel()
        refreshJob=viewModelScope.launch {
            _uiState.value=_uiState.value.copy(isLoading=true,error=null)
            try {
                val requested=targetOrgId ?: _uiState.value.orgId ?: tokenManager.currentOrgId.first()
                val response=RetrofitClient.apiService.getOrganizations()
                if(generation!=refreshGeneration)return@launch
                if(!response.isSuccessful)throw IllegalStateException("Không tải được không gian (${response.code()})")
                val organizations=response.body().orEmpty()
                val org=if(targetOrgId!=null)organizations.find{it.id==targetOrgId} else organizations.find{it.id==requested} ?: organizations.firstOrNull()
                if(org==null && targetOrgId!=null && organizations.isNotEmpty())throw IllegalStateException("Không tải được không gian đã chọn. Hãy làm mới.")
                if(org==null){
                    _uiState.value=OrganizationUiState(organizations=organizations)
                    return@launch
                }
                val changed=_uiState.value.orgId!=org.id
                _uiState.value=_uiState.value.copy(organizations=organizations,orgId=org.id,orgName=org.orgName,
                    members=if(changed)emptyList() else _uiState.value.members,
                    groups=if(changed)emptyList() else _uiState.value.groups,
                    invitations=if(changed)emptyList() else _uiState.value.invitations,
                    groupMembers=if(changed)emptyMap() else _uiState.value.groupMembers)
                tokenManager.saveOrgId(org.id)
                val members=RetrofitClient.apiService.getOrgMembers(org.id)
                if(generation!=refreshGeneration)return@launch
                if(!members.isSuccessful)throw IllegalStateException("Không tải được thành viên (${members.code()})")
                _uiState.value=_uiState.value.copy(members=members.body().orEmpty())
                val groups=RetrofitClient.apiService.getGroups(org.id)
                if(generation!=refreshGeneration)return@launch
                if(!groups.isSuccessful)throw IllegalStateException("Không tải được nhóm (${groups.code()})")
                _uiState.value=_uiState.value.copy(groups=groups.body().orEmpty())
                if(org.role in listOf("Owner","Admin")){
                    val invites=RetrofitClient.apiService.getInvitations(org.id)
                    if(generation!=refreshGeneration)return@launch
                    _uiState.value=_uiState.value.copy(invitations=if(invites.isSuccessful)invites.body().orEmpty() else emptyList())
                }else _uiState.value=_uiState.value.copy(invitations=emptyList())
                _uiState.value=_uiState.value.copy(isLoading=false)
                _uiState.value.groups.forEach{loadGroupMembers(it.groupId)}
            }catch(e:Exception){
                if(e is CancellationException)throw e
                if(generation==refreshGeneration)_uiState.value=_uiState.value.copy(isLoading=false,error=e.message ?: "Không tải được dữ liệu")
            }
        }
    }
    fun loadMembers(targetOrgId: String? = null) = loadData(targetOrgId)
    fun loadInvitations(targetOrgId: String? = null) = loadData(targetOrgId)
    fun loadGroups(targetOrgId: String? = null) = loadData(targetOrgId)

    fun loadGroupMembers(groupId: Int) {
        val generation=refreshGeneration
        val orgId=_uiState.value.orgId
        viewModelScope.launch {
            try {
                val response=RetrofitClient.apiService.getGroupMembers(groupId)
                if(generation!=refreshGeneration || _uiState.value.orgId!=orgId)return@launch
                if(response.isSuccessful && _uiState.value.groups.any{it.groupId==groupId}){
                    _uiState.value=_uiState.value.copy(groupMembers=_uiState.value.groupMembers+(groupId to response.body().orEmpty()))
                }
            }catch(e:Exception){if(e is CancellationException)throw e}
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
                        successMessage = "Đã tạo nhóm thiết bị '$name' thành công!"
                    )
                    loadGroups()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Tạo nhóm thiết bị thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể tạo nhóm: ${e.message}"
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
                        successMessage = "Đã xóa nhóm thiết bị thành công"
                    )
                    loadGroups()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Xóa nhóm thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể xóa nhóm: ${e.message}"
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
                        successMessage = "Đã cấp quyền $role cho thành viên trong nhóm!"
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
                        successMessage = "Đã xóa quyền thành viên khỏi nhóm"
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
                        successMessage = "Đã xóa thành viên khỏi không gian làm việc"
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
                        successMessage = "Bạn đã rời khỏi không gian làm việc thành công!"
                    )
                    loadData()
                    onLeft()
                } else {
                    val errorMsg = when (response.code()) {
                        403 -> "Chủ sở hữu (Owner) không thể rời không gian làm việc. Bạn cần chuyển quyền hoặc xóa không gian làm việc."
                        else -> "Rời không gian thất bại (${response.code()})"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể rời không gian làm việc: ${e.message}"
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

    fun joinOrganization(inviteCode: String, onJoined: (String) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val response = RetrofitClient.apiService.joinOrganization(
                    JoinOrgRequest(inviteCode = inviteCode.trim().uppercase())
                )
                if (response.isSuccessful && response.body() != null) {
                    val joinedOrgId = response.body()!!.orgId
                    tokenManager.saveOrgId(joinedOrgId)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        orgId = joinedOrgId,
                        orgName=null,members=emptyList(),groups=emptyList(),invitations=emptyList(),groupMembers=emptyMap(),
                        successMessage = "Đã tham gia không gian làm việc thành công!"
                    )
                    selectOrganization(joinedOrgId)
                    onJoined(joinedOrgId)
                } else {
                    val errorMsg = when (response.code()) {
                        404 -> "Mã mời không tồn tại hoặc đã hết hạn"
                        409 -> "Bạn đã là thành viên của không gian làm việc này rồi"
                        410 -> "Mã mời đã hết lượt sử dụng"
                        else -> "Tham gia thất bại (${response.code()})"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể tham gia không gian làm việc: ${e.message}"
                )
            }
        }
    }

    fun createOrganization(orgName: String, description: String?, onCreated: (String) -> Unit = {}) {
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
                        orgId = created.id,
                        orgName=created.orgName,members=emptyList(),groups=emptyList(),invitations=emptyList(),groupMembers=emptyMap(),
                        successMessage = "Đã tạo không gian làm việc '$orgName' thành công!"
                    )
                    loadData(targetOrgId = created.id)
                    onCreated(created.id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Tạo không gian thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể tạo không gian làm việc: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
