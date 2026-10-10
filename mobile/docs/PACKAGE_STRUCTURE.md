# Cấu trúc Android (Kotlin + Compose + MVVM)

Tạo project bằng Android Studio (Empty Activity - Compose) trong thư mục `mobile/`, package `com.dms.monitor`.

```
app/src/main/java/com/dms/monitor/
├── DmsApp.kt
├── MainActivity.kt
├── data/
│   ├── remote/      ApiService.kt, AuthInterceptor.kt (gắn JWT, tự refresh), dto/
│   ├── local/       TokenStore.kt (DataStore)
│   └── repository/  AuthRepo, OrgRepo, DeviceRepo, CommandRepo
├── domain/model/    Device, Metric, Org, Alert
├── ui/
│   ├── navigation/  NavGraph.kt
│   ├── auth/        LoginScreen, RegisterScreen, AuthViewModel
│   ├── org/         OrgListScreen, JoinOrgScreen (mã 6 ký tự / QR)
│   ├── dashboard/   DashboardScreen, DashboardViewModel
│   ├── device/      DeviceDetailScreen (Vico chart 7 ngày), CommandDialog
│   ├── alerts/      AlertListScreen
│   └── theme/
├── di/              NetworkModule, RepoModule (Hilt)
└── fcm/             DmsMessagingService.kt
```
