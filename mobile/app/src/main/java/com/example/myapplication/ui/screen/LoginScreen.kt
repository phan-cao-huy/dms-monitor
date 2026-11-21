package com.example.myapplication.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.data.model.ForgotPasswordRequest
import com.example.myapplication.data.model.ResetPasswordRequest
import com.example.myapplication.ui.viewmodel.AuthUiState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

private const val GOOGLE_WEB_CLIENT_ID = "455891485973-rfehjhelt6q9e0nfqhcv5qpgci3q3at2.apps.googleusercontent.com"

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onGoogleLogin: (String) -> Unit = {},
    onNavigateToRegister: () -> Unit,
    onClearError: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotCode by remember { mutableStateOf("") }
    var forgotNewPassword by remember { mutableStateOf("") }
    var forgotConfirmPassword by remember { mutableStateOf("") }
    var forgotStep by remember { mutableStateOf(1) }
    var forgotLoading by remember { mutableStateOf(false) }
    var forgotError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember(context) { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val token = account.idToken
            if (!token.isNullOrBlank()) {
                onGoogleLogin(token)
            } else {
                Toast.makeText(context, "Không nhận được mã xác thực Google", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            if (e.statusCode != 12501 && e.statusCode != 4) {
                Toast.makeText(context, "Đăng nhập Google thất bại (mã ${e.statusCode})", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.localizedMessage ?: "Lỗi đăng nhập Google", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AuthBrand("Một tầm nhìn. Mọi thiết bị.", "Theo dõi hệ thống của bạn ở bất cứ đâu.")
        Spacer(Modifier.height(28.dp))
        Text("Chào mừng trở lại", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text("Đăng nhập để mở không gian quản lý của bạn.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))

        // Username / Email
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it; onClearError() },
            label = { Text("Email hoặc tên đăng nhập") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Password
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = { Text("Mật khẩu") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Ẩn" else "Hiện"
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (identifier.isNotBlank() && password.isNotBlank()) {
                        onLogin(identifier, password)
                    }
                }
            )
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Quên mật khẩu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    forgotEmail = identifier.trim()
                    forgotStep = 1
                    forgotError = null
                    showForgotDialog = true
                }
            ) {
                Text("Quên mật khẩu?", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Error message
        if (uiState.error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "⚠️ ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Login button
        Button(
            onClick = { onLogin(identifier, password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            enabled = identifier.isNotBlank() && password.isNotBlank() && !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Đăng nhập", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "  HOẶC  ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = {
                googleSignInClient.signOut().addOnCompleteListener {
                    googleLauncher.launch(googleSignInClient.signInIntent)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !uiState.isLoading
        ) {
            Text("Tiếp tục với Google", fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Register link
        TextButton(onClick = onNavigateToRegister) {
            Text("Chưa có tài khoản? Đăng ký ngay")
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { if (!forgotLoading) showForgotDialog = false },
            title = {
                Text(if (forgotStep == 1) "Quên mật khẩu" else "Đặt lại mật khẩu")
            },
            text = {
                Column {
                    if (forgotStep == 1) {
                        Text(
                            text = "Nhập địa chỉ email tài khoản của bạn để nhận mã xác thực đặt lại mật khẩu:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = forgotEmail,
                            onValueChange = { forgotEmail = it; forgotError = null },
                            label = { Text("Địa chỉ email") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !forgotLoading
                        )
                    } else {
                        Text(
                            text = "Nếu tài khoản tồn tại, kiểm tra hộp thư và thư rác. Nhập mã 6 số trong 15 phút và mật khẩu mới cho: $forgotEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = forgotCode,
                            onValueChange = { forgotCode = it; forgotError = null },
                            label = { Text("Mã xác thực (6 số)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !forgotLoading
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = forgotNewPassword,
                            onValueChange = { forgotNewPassword = it; forgotError = null },
                            label = { Text("Mật khẩu mới (>= 8 ký tự)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !forgotLoading
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = forgotConfirmPassword,
                            onValueChange = { forgotConfirmPassword = it; forgotError = null },
                            label = { Text("Xác nhận mật khẩu mới") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !forgotLoading,
                            isError = forgotConfirmPassword.isNotBlank() && forgotNewPassword != forgotConfirmPassword,
                            supportingText = {
                                if (forgotConfirmPassword.isNotBlank() && forgotNewPassword != forgotConfirmPassword) {
                                    Text("Mật khẩu không khớp")
                                }
                            }
                        )
                    }
                    if (forgotError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = forgotError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                if (forgotStep == 1) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                forgotLoading = true
                                forgotError = null
                                try {
                                    val res = RetrofitClient.apiService.forgotPassword(
                                        ForgotPasswordRequest(forgotEmail.trim())
                                    )
                                    if (res.isSuccessful) {
                                        forgotCode = ""
                                        forgotStep = 2
                                    } else {
                                        forgotError = if (res.code() == 503) "Server chưa cấu hình hộp thư gửi mã khôi phục." else "Yêu cầu thất bại (${res.code()})"
                                    }
                                } catch (e: Exception) {
                                    forgotError = e.localizedMessage ?: "Lỗi kết nối máy chủ"
                                } finally {
                                    forgotLoading = false
                                }
                            }
                        },
                        enabled = forgotEmail.contains("@") && !forgotLoading
                    ) {
                        if (forgotLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Nhận mã xác thực")
                        }
                    }
                } else {
                    val canSubmit = forgotCode.matches(Regex("[0-9]{6}")) && forgotNewPassword.toByteArray().size <= 72 && forgotNewPassword.length >= 8 && forgotNewPassword == forgotConfirmPassword
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                forgotLoading = true
                                forgotError = null
                                try {
                                    val res = RetrofitClient.apiService.resetPassword(
                                        ResetPasswordRequest(
                                            email = forgotEmail.trim(),
                                            resetCode = forgotCode.trim(),
                                            newPassword = forgotNewPassword
                                        )
                                    )
                                    if (res.isSuccessful) {
                                        Toast.makeText(context, "Đặt lại mật khẩu thành công! Hãy đăng nhập.", Toast.LENGTH_SHORT).show()
                                        showForgotDialog = false
                                    } else {
                                        forgotError = "Mã xác thực không đúng hoặc đã hết hạn."
                                    }
                                } catch (e: Exception) {
                                    forgotError = e.localizedMessage ?: "Lỗi kết nối máy chủ"
                                } finally {
                                    forgotLoading = false
                                }
                            }
                        },
                        enabled = canSubmit && !forgotLoading
                    ) {
                        if (forgotLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Lưu mật khẩu mới")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotDialog = false },
                    enabled = !forgotLoading
                ) {
                    Text("Đóng")
                }
            }
        )
    }
}
