package com.example.myapplication.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.local.TokenManager
import com.example.myapplication.data.model.LoginRequest
import com.example.myapplication.data.model.RegisterRequest
import com.example.myapplication.data.model.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Trạng thái UI cho màn hình Authentication (Đăng nhập / Đăng ký)
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: UserInfo? = null,
    val error: String? = null
)

/**
 * ViewModel quản lý logic xác thực người dùng (Login, Register, Logout)
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        RetrofitClient.init(tokenManager)
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            val isLoggedIn = tokenManager.isLoggedIn()
            if (isLoggedIn) {
                val user = try {
                    val meResponse = RetrofitClient.apiService.getMe()
                    if (meResponse.isSuccessful) meResponse.body() else null
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
                _uiState.value = _uiState.value.copy(isLoggedIn = true, user = user)
            } else {
                _uiState.value = _uiState.value.copy(isLoggedIn = false, user = null)
            }
        }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = RetrofitClient.apiService.login(
                    LoginRequest(identifier = identifier, password = password)
                )
                if (response.isSuccessful) {
                    val body = response.body()!!
                    tokenManager.saveTokens(body.accessToken, body.refreshToken)
                    val user = try {
                        val meResponse = RetrofitClient.apiService.getMe()
                        if (meResponse.isSuccessful) meResponse.body() else null
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        user = user
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Sai tài khoản hoặc mật khẩu"
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

    fun register(username: String, email: String, password: String, fullName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = RetrofitClient.apiService.register(
                    RegisterRequest(username = username, email = email, password = password, fullName = fullName)
                )
                if (response.isSuccessful) {
                    // Tự động đăng nhập để lấy token xác thực
                    login(username, password)
                } else {
                    val errorMsg = when (response.code()) {
                        409 -> "Tên đăng nhập hoặc email đã tồn tại"
                        422 -> "Dữ liệu không hợp lệ (mật khẩu >= 8 ký tự, email hợp lệ)"
                        else -> "Đăng ký thất bại (${response.code()})"
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMsg)
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

    fun logout() {
        // Xóa user khỏi state NGAY LẬP TỨC để UI không hiển thị tên tài khoản cũ
        _uiState.value = AuthUiState(isLoggedIn = false, user = null)
        viewModelScope.launch {
            try {
                val refreshToken = tokenManager.refreshToken.first()
                if (refreshToken != null) {
                    RetrofitClient.apiService.logout(
                        com.example.myapplication.data.model.RefreshRequest(refreshToken)
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            tokenManager.clearAll()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
