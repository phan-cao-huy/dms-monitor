package com.example.myapplication.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.api.RetrofitClient
import com.example.myapplication.ui.viewmodel.AuthUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    uiState: AuthUiState,
    onRegister: (String, String, String, String) -> Unit,
    onNavigateBack: () -> Unit,
    onClearError: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đăng ký tài khoản") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding().padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            AuthBrand("Không gian của bạn.", "Tạo tài khoản để kết nối và theo dõi thiết bị.")
            Spacer(modifier = Modifier.height(24.dp))

            // Full Name
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it; onClearError() },
                label = { Text("Họ và tên") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; onClearError() },
                label = { Text("Tên đăng nhập (3-50 ký tự)") },
                leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; onClearError() },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; onClearError() },
                label = { Text("Mật khẩu (tối thiểu 8 ký tự)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Confirm Password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; onClearError() },
                label = { Text("Xác nhận mật khẩu") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                isError = confirmPassword.isNotBlank() && password != confirmPassword,
                supportingText = {
                    if (confirmPassword.isNotBlank() && password != confirmPassword) {
                        Text("Mật khẩu không khớp")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Terms of Service Checkbox & Link
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Checkbox(
                    checked = agreedToTerms,
                    onCheckedChange = { agreedToTerms = it }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tôi đồng ý với ",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Điều khoản sử dụng",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.clickable { showTermsDialog = true }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Error
            if (uiState.error != null) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isValid = username.length >= 3 && email.contains("@") &&
                    password.length >= 8 && password == confirmPassword && fullName.isNotBlank() && agreedToTerms

            Button(
                onClick = { onRegister(username, email, password, fullName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = isValid && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Đăng ký", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onNavigateBack) {
                Text("Đã có tài khoản? Đăng nhập")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showTermsDialog) {
        val uriHandler = LocalUriHandler.current
        val termsWebUrl = RetrofitClient.getBaseUrl().replace("/api/v1/", "").trimEnd('/') + "/terms.html"
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("📜 Điều khoản sử dụng DMS Monitor") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "1. Chấp thuận điều khoản\n" +
                                "Bằng việc đăng ký tài khoản DMS Monitor, bạn đồng ý tuân thủ toàn bộ các quy định tại đây.\n\n" +
                                "2. Phạm vi dịch vụ\n" +
                                "DMS Monitor cung cấp giải pháp giám sát tình trạng máy tính (CPU, RAM, Ổ đĩa, Mạng, GPU, tiến trình) và thực thi lệnh quản trị từ xa hợp lệ.\n\n" +
                                "3. Trách nhiệm thiết bị\n" +
                                "Bạn cam kết chỉ cài đặt DMS Agent trên các máy tính thuộc quyền sở hữu hoặc được ủy quyền quản trị hợp pháp. Nghiêm cấm mọi hành vi can thiệp trái phép thiết bị của người khác.\n\n" +
                                "4. Bảo mật tài khoản & Agent Token\n" +
                                "Người dùng tự bảo quản mật khẩu đăng nhập và Secret Token của máy tính. Mọi thao tác thực thi từ tài khoản thuộc về trách nhiệm của bạn.\n\n" +
                                "5. Quyền riêng tư\n" +
                                "Hệ thống chỉ đo lường số liệu kỹ thuật, không thu thập tệp tin cá nhân, hình ảnh màn hình hay phím bấm. Toàn bộ đường truyền được bảo vệ bằng giao thức bảo mật HTTPS/TLS.\n\n" +
                                "6. Giới hạn trách nhiệm\n" +
                                "DMS Monitor không chịu trách nhiệm với sự cố phát sinh do người dùng chủ động thực thi lệnh dừng tiến trình hoặc khởi động lại máy tính từ xa.",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            try {
                                uriHandler.openUri(termsWebUrl)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Mở bản web đầy đủ ↗", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    agreedToTerms = true
                    showTermsDialog = false
                }) {
                    Text("Tôi đồng ý")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }
}
