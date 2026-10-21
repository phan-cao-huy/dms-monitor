package com.example.myapplication.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.local.TokenManager
import com.example.myapplication.data.model.Alert
import com.example.myapplication.data.model.Device
import com.example.myapplication.data.model.DeviceGroup
import com.example.myapplication.data.model.Organization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Trạng thái UI cho màn hình Dashboard giám sát thiết bị
 */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val organizations: List<Organization> = emptyList(),
    val selectedOrg: Organization? = null,
    val devices: List<Device> = emptyList(),
    val alerts: List<Alert> = emptyList(),
    val groups: List<DeviceGroup> = emptyList(),
    val selectedGroupFilter: Int? = null, // null: Tất cả, 0: Chưa phân cụm, >0: Group ID
    val searchQuery: String = "",
    val error: String? = null,
    val successMessage: String? = null,
    val newlyCreatedDevice: com.example.myapplication.data.model.CreateDeviceResponse? = null
)

/**
 * ViewModel quản lý danh sách tổ chức, thiết bị, cụm thiết bị và cảnh báo trên Dashboard
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadOrganizations()
    }

    /**
     * Reset toàn bộ state về trạng thái ban đầu và huỷ tất cả các job đang chạy.
     * Phải gọi trước khi đăng xuất để tránh rò rỉ dữ liệu sang tài khoản khác.
     */
    fun resetState() {
        // Huỷ tất cả các coroutine job đang chạy trong scope (network calls cũ)
        viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        _uiState.value = DashboardUiState()
    }

    fun loadOrganizations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = RetrofitClient.apiService.getOrganizations()
                if (response.isSuccessful && response.body() != null) {
                    val orgs = response.body()!!
                    if (orgs.isEmpty()) {
                        tokenManager.saveOrgId("")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            organizations = emptyList(),
                            selectedOrg = null,
                            devices = emptyList(),
                            alerts = emptyList(),
                            groups = emptyList(),
                            selectedGroupFilter = null
                        )
                    } else {
                        val savedOrgId = tokenManager.currentOrgId.first()
                        val selected = orgs.find { it.id == savedOrgId } ?: orgs.firstOrNull()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            organizations = orgs,
                            selectedOrg = selected
                        )
                        selected?.let {
                            tokenManager.saveOrgId(it.id)
                            loadDevices(it.id)
                            loadGroups(it.id)
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Không thể tải danh sách tổ chức (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể kết nối server: ${e.message}"
                )
            }
        }
    }

    fun selectOrganization(org: Organization) {
        viewModelScope.launch {
            tokenManager.saveOrgId(org.id)
            _uiState.value = _uiState.value.copy(selectedOrg = org, selectedGroupFilter = null)
            loadDevices(org.id)
            loadGroups(org.id)
        }
    }

    fun removeDeviceLocally(deviceId: String) {
        val updated = _uiState.value.devices.filter { it.id != deviceId }
        _uiState.value = _uiState.value.copy(devices = updated)
    }

    fun refreshCurrentOrgDevices() {
        val currentOrgId = _uiState.value.selectedOrg?.id
        if (currentOrgId != null) {
            loadDevices(currentOrgId)
            loadGroups(currentOrgId)
        } else {
            loadOrganizations()
        }
    }

    fun loadGroups(orgId: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getGroups(orgId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(groups = response.body()!!)
                }
            } catch (_: Exception) {}
        }
    }

    fun selectGroupFilter(groupId: Int?) {
        _uiState.value = _uiState.value.copy(selectedGroupFilter = groupId)
    }

    fun loadDevices(orgId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = RetrofitClient.apiService.getDevices(orgId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        devices = response.body()!!
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Không thể tải danh sách thiết bị (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể kết nối server: ${e.message}"
                )
            }
            loadAlerts(orgId)
        }
    }

    private fun loadAlerts(orgId: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getAlerts(orgId, resolved = false)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(alerts = response.body()!!)
                }
            } catch (_: Exception) {
                // Không hiển thị lỗi cho alerts riêng để tránh spam UI
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun getFilteredDevices(): List<Device> {
        val query = _uiState.value.searchQuery.lowercase()
        val filterGroupId = _uiState.value.selectedGroupFilter
        return _uiState.value.devices.filter { device ->
            val matchesQuery = query.isBlank() ||
                device.name.lowercase().contains(query) ||
                device.status.lowercase().contains(query) ||
                (device.groupName?.lowercase()?.contains(query) == true)

            val matchesGroup = when (filterGroupId) {
                null -> true // Tất cả
                0 -> device.groupId == null // Chưa phân cụm
                else -> device.groupId == filterGroupId.toString()
            }
            matchesQuery && matchesGroup
        }
    }

    fun createDevice(name: String, groupId: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null, newlyCreatedDevice = null)
            try {
                val orgId = _uiState.value.selectedOrg?.id ?: tokenManager.currentOrgId.first() ?: return@launch
                val response = RetrofitClient.apiService.createDevice(
                    orgId,
                    com.example.myapplication.data.model.CreateDeviceRequest(name = name, groupId = groupId?.toIntOrNull())
                )
                if (response.isSuccessful && response.body() != null) {
                    val created = response.body()!!
                    // Lưu newlyCreatedDevice TRƯỚC, sau đó load devices để không bị race condition ghi đè
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        newlyCreatedDevice = created,
                        successMessage = "Thiết bị '$name' đã được tạo thành công!"
                    )
                    // Load devices nhưng không được xóa newlyCreatedDevice
                    loadDevicesKeepToken(orgId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Tạo thiết bị thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể tạo thiết bị: ${e.message}"
                )
            }
        }
    }

    /**
     * Load lại devices nhưng GIỮ NGUYÊN newlyCreatedDevice trong state
     * (tránh race condition xóa mất token dialog)
     */
    private fun loadDevicesKeepToken(orgId: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getDevices(orgId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(
                        devices = response.body()!!
                        // KHÔNG copy isLoading = false và KHÔNG đụng đến newlyCreatedDevice
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun resolveAlert(alertId: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.resolveAlert(alertId)
                if (response.isSuccessful) {
                    val updated = _uiState.value.alerts.map {
                        if (it.id == alertId) it.copy(resolved = true, resolvedAt = java.time.Instant.now().toString()) else it
                    }
                    _uiState.value = _uiState.value.copy(alerts = updated)
                } else {
                    markAlertResolvedLocally(alertId)
                }
            } catch (e: Exception) {
                markAlertResolvedLocally(alertId)
            }
        }
    }

    private fun markAlertResolvedLocally(alertId: String) {
        val updated = _uiState.value.alerts.map {
            if (it.id == alertId) it.copy(resolved = true, resolvedAt = java.time.Instant.now().toString()) else it
        }
        _uiState.value = _uiState.value.copy(alerts = updated)
    }

    fun clearNewlyCreatedDevice() {
        _uiState.value = _uiState.value.copy(newlyCreatedDevice = null)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
