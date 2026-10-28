package com.example.myapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.example.myapplication.util.CommandFeedback

/**
 * Trạng thái UI cho màn hình chi tiết thiết bị (Overview, Metrics, Processes, Commands)
 */
data class DeviceDetailUiState(
    val isLoading: Boolean = false,
    val device: DeviceDetail? = null,
    val metrics: List<Metric> = emptyList(),
    val processes: List<ProcessInfo> = emptyList(),
    val commands: List<Command> = emptyList(),
    val selectedTab: Int = 0, // 0=Overview, 1=Metrics, 2=Processes, 3=Commands
    val error: String? = null,
    val commandSuccess: String? = null
)

/**
 * ViewModel quản lý chi tiết thiết bị, telemetry metrics, danh sách tiến trình và lịch sử gửi lệnh điều khiển
 */
class DeviceDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    private var watchedCommandId:Int?=null
    private var commandPollJob:Job?=null
    private var commandEnglish=false

    fun loadDevice(deviceId: String, silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = !silent, error = null)
            try {
                val response = RetrofitClient.apiService.getDeviceDetail(deviceId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        device = response.body()!!
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Không thể tải thông tin thiết bị (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể kết nối server: ${e.message}"
                )
            }
            loadMetrics(deviceId)
            loadProcesses(deviceId)
            loadCommands(deviceId)
        }
    }

    private fun loadMetrics(deviceId: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getDeviceMetrics(deviceId, from = java.time.Instant.now().minusSeconds(3600).toString(), to = java.time.Instant.now().toString())
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(metrics = response.body()!!)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // metrics lỗi không hiển thị, giữ list rỗng
            }
        }
    }

    private fun loadProcesses(deviceId: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.getDeviceProcesses(deviceId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = _uiState.value.copy(processes = response.body()!!)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // processes lỗi không hiển thị, giữ list rỗng
            }
        }
    }

    private fun loadCommands(deviceId:String) {
        viewModelScope.launch { refreshCommands(deviceId) }
    }

    private suspend fun refreshCommands(deviceId:String) {
        try {
            val response=RetrofitClient.apiService.getCommandHistory(deviceId)
            if(response.isSuccessful&&response.body()!=null){
                val commands=response.body()!!
                val watched=commands.find{it.id==watchedCommandId}
                _uiState.value=_uiState.value.copy(commands=commands)
                if(watched!=null){
                    val failed=watched.status in listOf("FAILED","TIMEOUT")
                    _uiState.value=_uiState.value.copy(
                        commandSuccess=if(failed)null else CommandFeedback.text(watched,commandEnglish),
                        error=if(failed)CommandFeedback.text(watched,commandEnglish) else null)
                    if(CommandFeedback.terminal(watched.status)){
                        watchedCommandId=null
                        if(watched.commandType=="KILL_PROCESS")loadProcesses(deviceId)
                    }
                }
            }
        }catch(e:Exception){if(e is CancellationException)throw e}
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun sendCommand(deviceId: String, commandType: String, parameters: Map<String, Any>?, reason: String, english:Boolean=false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, commandSuccess = null, error = null)
            try {
                val response = RetrofitClient.apiService.sendCommand(
                    deviceId,
                    CommandRequest(
                        commandType = commandType,
                        parameters = parameters,
                        reason = reason
                    )
                )
                if (response.isSuccessful) {
                    val created=response.body() ?: throw IllegalStateException("Server did not return a command ID")
                    commandPollJob?.cancel()
                    commandEnglish=english
                    watchedCommandId=created.id
                    _uiState.value=_uiState.value.copy(isLoading=false,commandSuccess=CommandFeedback.text(created,english))
                    commandPollJob=launch {
                        // Poll this exact command; never infer completion from an HTTP 201.
                        repeat(150){
                            refreshCommands(deviceId)
                            if(watchedCommandId!=created.id)return@launch
                            delay(2000)
                        }
                        // The normal detail refresh continues checking server state.
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Gửi lệnh thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể gửi lệnh: ${e.message}"
                )
            }
        }
    }

    fun toggleMonitoring(deviceId: String, enable: Boolean? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, commandSuccess = null, error = null)
            try {
                val req = enable?.let { ToggleMonitoringRequest(it) }
                val response = RetrofitClient.apiService.toggleMonitoring(deviceId, req)
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    val currentDev = _uiState.value.device
                    val updatedDev = currentDev?.copy(
                        isMonitoring = result.isMonitoring,
                        status = result.status
                    )
                    val statusText = if (result.isMonitoring) {
                        "Đã kích hoạt theo dõi máy tính thành công!"
                    } else {
                        "Đã tạm dừng theo dõi máy tính! Agent sẽ không quét tiến trình & tài nguyên."
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        device = updatedDev,
                        commandSuccess = statusText
                    )
                    loadCommands(deviceId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Không thể chuyển trạng thái theo dõi (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể kết nối máy chủ: ${e.message}"
                )
            }
        }
    }

    fun updateDeviceGroup(deviceId: String, groupId: Int?, onUpdated: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = RetrofitClient.apiService.updateDeviceGroup(
                    deviceId,
                    com.example.myapplication.data.model.UpdateDeviceGroupRequest(groupId)
                )
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        commandSuccess = "Đã cập nhật nhóm thiết bị thành công!"
                    )
                    loadDevice(deviceId)
                    onUpdated()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Cập nhật nhóm thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể cập nhật nhóm: ${e.message}"
                )
            }
        }
    }

    fun deleteDevice(deviceId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = RetrofitClient.apiService.deleteDevice(deviceId)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onDeleted()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Xóa thiết bị thất bại (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Không thể xóa thiết bị: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        watchedCommandId=null
        commandPollJob?.cancel()
        _uiState.value = _uiState.value.copy(error = null, commandSuccess = null)
    }
}
