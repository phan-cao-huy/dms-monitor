# DMS MONITOR — PROJECT COMPREHENSIVE CONTEXT & SPECIFICATION

> **Quy ước người dùng:** Mọi thay đổi tính năng/giao diện từ nay phải đồng bộ web và Android; dùng chung API/database.

> **Release hiện tại: v1.0.20 (Build 21)** — server HTTPS cố định, ký release, đã khôi phục cập nhật APK qua server. Xem [docs/ANDROID_RELEASE.md](docs/ANDROID_RELEASE.md).

> **26/10/2026 — đối chiếu và bản sửa:** Xem [docs/RELIABILITY_FIXES.md](docs/RELIABILITY_FIXES.md) để biết hành vi mới, kết quả kiểm tra và phần chưa triển khai. Các mô tả cũ bên dưới có điểm đã lỗi thời.
> **Tài liệu chuyển giao ngữ cảnh toàn diện dự án DMS (Device Monitoring System)**  
> *Dành cho AI Assistant, Developer hoặc Team kế thừa tiếp tục phát triển trên bất kỳ nền tảng nào.*  
> *Ngày cập nhật: 25/10/2026 | Phiên bản hệ thống: v1.0.7 (Build 8)*

---

## MỤC LỤC
1. [Tổng quan dự án (Executive Summary)](#1-tổng-quan-dự-án-executive-summary)
2. [Kiến trúc hệ thống (System Architecture)](#2-kiến-trúc-hệ-thống-system-architecture)
3. [Cấu trúc thư mục & Folder chính (Project Folder Structure)](#3-cấu-trúc-thư-mục--folder-chính-project-folder-structure)
4. [Cơ sở dữ liệu & Schema (Database & PostgreSQL Specs)](#4-cơ-sở-dữ-liệu--schema-database--postgresql-specs)
5. [Đặc tả API Contracts (API Specifications)](#5-đặc-tả-api-contracts-api-specifications)
6. [Các cơ chế nghiệp vụ trọng yếu (Core Workflows)](#6-các-cơ-chế-nghiệp-vụ-trọng-yếu-core-workflows)
   - [6.1. Cài đặt 1 câu lệnh PowerShell (1-liner Silent Install)](#61-cài-đặt-1-câu-lệnh-powershell-1-liner-silent-install)
   - [6.2. Bật / Tắt theo dõi từ xa (Toggle Remote Monitoring)](#62-bật--tắt-theo-dõi-từ-xa-toggle-remote-monitoring)
   - [6.3. Tự động gỡ cài đặt và dọn sạch file khi xóa PC (Remote Uninstall)](#63-tự-động-gỡ-cài-đặt-và-dọn-sạch-file-khi-xóa-pc-remote-uninstall)
   - [6.4. Cơ chế Cập nhật ứng dụng di động qua Server (In-App Update)](#64-cơ-chế-cập-nhật-ứng-dụng-di-động-qua-server-in-app-update)
   - [6.5. Thu thập phần cứng Native WMI ẩn hoàn toàn (No-CMD Window)](#65-thu-thập-phần-cứng-native-wmi-ẩn-hoàn-toàn-no-cmd-window)
7. [Lịch sử sửa lỗi & Cạm bẫy kỹ thuật (Bug Fixes & Technical Gotchas)](#7-lịch-sử-sửa-lỗi--cạm-bẫy-kỹ-thuật-bug-fixes--technical-gotchas)
8. [Hướng dẫn cài đặt, chạy & kiểm thử (Run & Verification Guide)](#8-hướng-dẫn-cài-đặt-chạy--kiểm-thử-run--verification-guide)
9. [Thông tin Git & Trạng thái hiện tại (Repository & Git State)](#9-thông-tin-git--trạng-thái-hiện-tại-repository--git-state)

---

## 1. Tổng quan dự án (Executive Summary)

**DMS (Device Monitoring System)** là giải pháp toàn diện phục vụ giám sát, quản lý và điều khiển từ xa các máy tính cá nhân/máy trạm (PC) trong doanh nghiệp hoặc tổ chức theo mô hình **Multi-tenant** (Đa tổ chức).

Hệ thống bao gồm 3 trụ cột chính:
1. **DMS Backend** (`backend/`):
   - Ngôn ngữ & Framework: **Python 3.12**, **FastAPI** (REST API bất đồng bộ).
   - Cơ sở dữ liệu: **PostgreSQL 15** sử dụng **SQLAlchemy 2.0 Async** (`asyncpg`).
   - Xử lý nền: **APScheduler** chạy định kỳ kiểm tra thiết bị Offline, RAM cao, timeout lệnh và partition bảng dữ liệu lớn.
   - Quản trị tải & cập nhật: Static file server cung cấp file cài đặt Agent (`install.ps1`, `dms-agent.exe`) và APK di động (`dms-app.apk`).
2. **DMS Agent** (`agent/`):
   - Ngôn ngữ: **Go (Golang 1.22+)**.
   - Cơ chế chạy: **Windows Service** chuẩn (`golang.org/x/sys/windows/svc`) chạy ngầm trong **Session 0**, hoàn toàn ẩn (không có cửa sổ console, không làm nháy màn hình người dùng).
   - Nhiệm vụ: Thu thập thông tin phần cứng tĩnh (CPU Model, Cores, RAM, Disk, GPU, IP), telemetry động (CPU%, RAM%, Disk%, GPU Usage/Temp/VRAM, Network in/out kbps, Top tiến trình tiêu thụ tài nguyên).
   - Giao tiếp: Chủ động gọi ra (Outbound HTTPS/HTTP) gửi Heartbeat mỗi 5–10s, kéo lệnh điều khiển (`REBOOT`, `RESTART_SERVICE`, `KILL_PROCESS`, `PAUSE_MONITORING`, `RESUME_MONITORING`, `UNINSTALL`). Không yêu cầu mở cổng (NAT/Firewall friendly).
3. **DMS Mobile App** (`mobile/`):
   - Nền tảng: **Android** (SDK 29–36).
   - Công nghệ: **Kotlin 2.0**, **Jetpack Compose**, **Material 3**, **Retrofit 2**, **Kotlin Coroutines / Flow**, **DataStore**.
   - Kiến trúc: **MVVM (Model-View-ViewModel)**.
   - Tính năng: Đăng nhập/Đăng ký/JWT, Quản lý đa tổ chức & Cụm thiết bị (Group), Xem tình trạng máy tính thời gian thực, Bật/Tắt theo dõi, Dừng tiến trình (Kill PID), Khởi động lại máy, Xóa thiết bị & gửi lệnh tự hủy từ xa, In-app updater tự cập nhật không cần Google Play Store.

---

## 2. Kiến trúc hệ thống (System Architecture)

```mermaid
flowchart TB
    subgraph Monitored_PC["Máy tính được giám sát (Windows PC)"]
        direction TB
        AgentService["DMS-Agent (Windows Service - Session 0)"]
        WMI["Native COM WMI (CPU Temp / GPU)"]
        ProcCollect["Process & Resource Collector"]
        ExecEngine["Executor (Whitelist Commands)"]
        
        AgentService --> ProcCollect
        AgentService --> WMI
        AgentService --> ExecEngine
    end

    subgraph Server_Infrastructure["DMS Server (Backend & Database)"]
        direction TB
        FastAPIApp["FastAPI REST API (/api/v1)"]
        StaticServer["Static Server (/static: install.ps1, agent.exe, dms-app.apk)"]
        SchedWorkers["APScheduler Workers (Offline Check, Ram Alert, Cleanups)"]
        PostgresDB[("PostgreSQL 15 (22 tables, Weekly Partitioning)")]

        FastAPIApp <--> PostgresDB
        SchedWorkers <--> PostgresDB
    end

    subgraph Mobile_Client["Quản trị viên (Mobile Client)"]
        ComposeApp["DMS Monitor Android App (Jetpack Compose)"]
        InAppUpdater["In-App Updater (AppUpdateManager)"]
        ComposeApp --> InAppUpdater
    end

    %% Giao tiếp luồng
    ProcCollect -- "POST /api/v1/agent/heartbeat (5-10s)" --> FastAPIApp
    FastAPIApp -- "Resp: accepted, monitoring_enabled" --> AgentService
    ExecEngine -- "GET /api/v1/agent/commands (Poll)" --> FastAPIApp
    ExecEngine -- "POST /api/v1/agent/commands/{id}/result" --> FastAPIApp

    ComposeApp -- "JWT Auth: REST API" --> FastAPIApp
    InAppUpdater -- "GET /api/v1/app/version & /static/dms-app.apk" --> StaticServer
```

---

## 3. Cấu trúc thư mục & Folder chính (Project Folder Structure)

```
dms-monitor/
├── .github/                      # CI/CD Workflows (Agent & Backend test/build)
│   └── workflows/
│       ├── agent.yml             # Go test & build
│       └── backend.yml           # Python ruff check & pytest
├── agent/                        # Mã nguồn DMS Agent (Go)
│   ├── cmd/agent/
│   │   ├── main.go               # Entrypoint agent, cờ CLI (-server, -token, -install, -uninstall)
│   │   ├── service_windows.go    # Cài đặt & chạy dạng Windows Service chuẩn (Session 0)
│   │   └── service_other.go      # Fallback no-op cho Linux/macOS
│   ├── configs/
│   │   └── config.example.json   # Mẫu cấu hình JSON
│   ├── internal/
│   │   ├── collector/
│   │   │   ├── collector.go      # Thu thập CPU, RAM, Disk, Net, Top Processes
│   │   │   ├── wmi_windows.go    # Đọc GPU & CPU Temp qua native COM OLE WMI (không nháy CMD)
│   │   │   ├── wmi_other.go      # Fallback WMI no-op cho non-Windows
│   │   │   ├── exec_windows.go   # Thực thi lệnh con với cờ CREATE_NO_WINDOW
│   │   │   └── exec_other.go     # Fallback non-Windows
│   │   ├── config/
│   │   │   └── config.go         # Đọc/ghi config.json, tự strip UTF-8 BOM
│   │   ├── executor/
│   │   │   ├── executor.go       # Xử lý lệnh: REBOOT, RESTART_SERVICE, KILL_PROCESS, PAUSE, RESUME, UNINSTALL
│   │   │   ├── exec_windows.go   # hideWindow() SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
│   │   │   └── exec_other.go     # Fallback
│   │   └── sender/
│   │       └── sender.go         # Gửi HTTP Heartbeat, lấy commands, trả command results
│   ├── INSTALL.md                # Hướng dẫn cài đặt Agent thủ công
│   └── go.mod, go.sum            # Go dependencies (gopsutil, golang.org/x/sys, go-ole)
├── backend/                      # Mã nguồn DMS Backend (Python FastAPI)
│   ├── app/
│   │   ├── api/v1/
│   │   │   ├── routers/
│   │   │   │   ├── agent.py      # /agent/heartbeat, /agent/commands, /agent/commands/{id}/result
│   │   │   │   ├── alerts.py     # /orgs/{id}/alert-rules, /orgs/{id}/alerts, resolve alert
│   │   │   │   ├── auth.py       # /auth/register, /auth/login, refresh, logout, me, users
│   │   │   │   ├── devices.py    # /orgs/{id}/devices, /devices/{id}, /devices/{id}/toggle-monitoring
│   │   │   │   └── orgs.py       # /orgs, invitations, members, device groups, user group access
│   │   │   └── router.py         # Gom toàn bộ routers vào api_v1_router
│   │   ├── core/
│   │   │   ├── config.py         # Settings Pydantic (DATABASE_URL, SECRET_KEY, CORS...)
│   │   │   ├── deps.py           # Dependencies: get_db, get_current_user, require_org_role
│   │   │   └── security.py       # bcrypt hashing, JWT access token, SHA-256 token hashing
│   │   ├── db/
│   │   │   ├── base.py           # Base declarative class
│   │   │   └── session.py        # Async engine & async_sessionmaker
│   │   ├── models/               # SQLAlchemy Models
│   │   │   ├── alert.py          # AlertRule, AlertHistory, NotificationQueue
│   │   │   ├── auth.py           # User, AuthToken, FCMToken, AuditLog
│   │   │   ├── device.py         # Device, DeviceCommand, DeviceMetric, DeviceMetricHourly
│   │   │   └── organization.py   # Organization, OrganizationMember, OrgInvitation, DeviceGroup, UserGroupAccess
│   │   ├── schemas/              # Pydantic Schemas (Request / Response DTOs)
│   │   ├── services/             # Business Logic độc lập HTTP (devices.py, auth.py, alerts.py...)
│   │   ├── workers/
│   │   │   └── runner.py         # APScheduler: check_offline_devices, check_high_ram_devices, check_command_timeouts
│   │   └── main.py               # FastAPI App, lifespan, auto db migration (_ensure_db_schema), /api/v1/app/version
│   ├── static/                   # Static distribution files
│   │   ├── dms-agent.exe         # Binary Agent Silent (pre-built)
│   │   ├── dms-agent-debug.exe   # Binary Agent Console Debug
│   │   ├── dms-app.apk           # File cài đặt Android App v1.0.7 (Build 8)
│   │   └── install.ps1           # Script PowerShell tự động cài đặt Agent 1-click
│   ├── tests/                    # Bộ 22 unit tests đạt 100% (pytest, TestClient)
│   ├── Dockerfile, requirements.txt
│   └── ruff.toml
├── database/                     # Thiết kế cơ sở dữ liệu & Migration
│   ├── dms01.sql                 # DDL toàn bộ 22 bảng, triggers, indexes, partitioning functions
│   └── migrate_v1.0.7.sql        # Migration cập nhật cột is_monitoring và nới lỏng CHECK constraints
├── docs/                         # Tài liệu quy chuẩn chung
│   ├── ARCHITECTURE.md           # Kiến trúc phân tầng và phân quyền
│   └── API_CONTRACT.md           # Hợp đồng API giữa Backend, Agent và Mobile
├── mobile/                       # Mã nguồn DMS Mobile App (Android Studio Project)
│   ├── app/
│   │   ├── build.gradle.kts      # Dependencies, versionCode = 8, versionName = "1.0.7"
│   │   └── src/main/
│   │       ├── AndroidManifest.xml # Permissions (Internet, Install Packages, Post Notifications)
│   │       ├── java/com/example/myapplication/
│   │       │   ├── data/
│   │       │   │   ├── api/      # ApiService.kt, RetrofitClient.kt
│   │       │   │   ├── local/    # TokenManager.kt, SettingsManager.kt
│   │       │   │   └── model/    # Models.kt (Device, DeviceDetail, ToggleMonitoring..., User, Org)
│   │       │   ├── ui/
│   │       │   │   ├── navigation/ NavGraph.kt (Điều hướng toàn bộ màn hình)
│   │       │   │   ├── screen/   # Compose Screens: DashboardScreen, DeviceDetailScreen, OrgScreen, ProfileScreen...
│   │       │   │   ├── theme/    # Theme.kt, Color.kt, Type.kt
│   │       │   │   └── viewmodel/# ViewModels: DashboardViewModel, DeviceDetailViewModel, AuthViewModel...
│   │       │   ├── util/
│   │       │   │   └── AppUpdateManager.kt # Tự tải và gọi Intent cài APK mới qua FileProvider
│   │       │   └── MainActivity.kt
│   │       └── res/              # Layouts, icons, drawables, strings.xml, file_paths.xml
│   ├── build.gradle.kts, settings.gradle.kts, gradle.properties
│   └── gradlew, gradlew.bat      # Gradle Wrapper (Gradle 8.13, Kotlin 2.0.21)
├── docker-compose.yml            # Khởi chạy PostgreSQL + Backend
├── Makefile                      # Lệnh tiện ích: make up, make test, make agent
├── .env.example, .env            # Biến môi trường
└── README.md                     # Hướng dẫn cơ bản ban đầu
```

---

## 4. Cơ sở dữ liệu & Schema (Database & PostgreSQL Specs)

### 4.1. Thông tin chung
- **Hệ quản trị:** PostgreSQL 15.
- **Async Driver:** `asyncpg` qua SQLAlchemy URL: `postgresql+asyncpg://user:password@host:5432/dms_db`.
- **Tập tin tạo bảng gốc:** [database/dms01.sql](database/dms01.sql).
- **Tập tin cập nhật v1.0.7:** [database/migrate_v1.0.7.sql](database/migrate_v1.0.7.sql).

### 4.2. Các bảng dữ liệu cốt lõi
1. **`users`**: Tài khoản quản trị/thành viên (`user_id`, `username`, `email`, `password_hash`, `full_name`, `status`).
2. **`auth_tokens`**: Refresh token (`token_hash` SHA-256, `device_info`, `expires_at`, `revoked_at`).
3. **`organizations`**: Tổ chức khách hàng (`org_id`, `org_name`, `created_by`, `deleted_at`).
4. **`org_members`**: Thành viên tổ chức (`org_id`, `user_id`, `role`).
   - `role` ∈ `'Owner'`, `'Admin'`, `'Manager'`, `'Viewer'`.
5. **`org_invitations`**: Lời mời vào tổ chức qua mã mời (`invite_code` 6 ký tự, `max_uses`, `expires_at`).
6. **`device_groups`**: Cụm thiết bị (`group_id`, `org_id`, `group_name`).
7. **`user_group_access`**: Phân quyền chi tiết thành viên vào cụm (`user_id`, `group_id`, `role`).
8. **`devices`**: Danh mục máy tính được theo dõi:
   - `device_id` (PK), `org_id` (FK), `group_id` (FK nullable), `device_name`.
   - `secret_token_hash` (SHA-256), `secret_token_prefix` (8 ký tự).
   - `status`: `'Online'`, `'Offline'`, `'Warning'`, `'Archived'`, `'Paused'`.
   - `is_monitoring`: `BOOLEAN DEFAULT TRUE` (Cờ bật/tắt theo dõi).
   - `last_heartbeat`, `offline_threshold_minutes` (mặc định 5 phút).
   - `static_info` (JSONB): CPU Model, RAM, Disk, OS version, GPU name...
9. **`device_metrics`** (Partitioned theo tuần): Lưu telemetry raw (`cpu_usage`, `ram_usage`, `disk_usage`, `net_in_kbps`, `net_out_kbps`, `gpu_usage_pct`, `cpu_temp_c`, `created_at`).
10. **`device_metrics_hourly`**: Dữ liệu nén theo giờ để vẽ biểu đồ dài ngày (lưu 90 ngày).
11. **`device_commands`**: Lịch sử và hàng đợi lệnh gửi tới PC:
    - `command_id`, `device_id`, `created_by`, `command_type`, `parameters` (JSONB), `reason`, `status` (`PENDING`, `EXECUTING`, `SUCCESS`, `FAILED`, `TIMEOUT`), `output`, `created_at`, `completed_at`.
12. **`alert_rules` & `alert_history`**: Cấu hình ngưỡng cảnh báo (CPU > 90%, RAM > 90%, Offline...).
13. **`audit_logs`**: Nhật ký bảo mật ghi lại mọi hành vi can thiệp nhạy cảm.

### 4.3. Các ràng buộc CHECK bắt buộc ghi nhớ (Constraints)
- **`chk_device_status`** trên bảng `devices`:
  ```sql
  CHECK (status IN ('Online', 'Offline', 'Warning', 'Archived', 'Paused'))
  ```
- **`chk_cmd_type`** trên bảng `device_commands`:
  ```sql
  CHECK (command_type IN ('REBOOT', 'RESTART_SERVICE', 'KILL_PROCESS', 'PAUSE_MONITORING', 'RESUME_MONITORING', 'UNINSTALL'))
  ```
- **`chk_org_role`** trên bảng `org_members`:
  ```sql
  CHECK (role IN ('Owner', 'Admin', 'Manager', 'Viewer'))
  ```

---

## 5. Đặc tả API Contracts (API Specifications)

### 5.1. Dành cho Agent (Xác thực qua Bearer Secret Token)
Header bắt buộc: `Authorization: Bearer <secret_token>`

- **`POST /api/v1/agent/heartbeat`**:
  - Gửi dữ liệu định kỳ mỗi 5–10s.
  - Body:
    ```json
    {
      "cpu_usage": 45.2,
      "ram_usage": 68.1,
      "disk_usage": 82.0,
      "net_in_kbps": 12.5,
      "net_out_kbps": 30.1,
      "uptime_seconds": 342000,
      "cpu_temp_c": 55.0,
      "gpu_name": "NVIDIA GeForce GTX 1650",
      "gpu_usage_pct": 14.0,
      "gpu_temp_c": 60.0,
      "gpu_vram_used_mb": 1200,
      "gpu_vram_total_mb": 4096,
      "static_info": { "os_name": "Windows", "cpu_model": "Intel Core i7", "ram_total_mb": 16384 },
      "top_processes": [ { "pid": 1234, "name": "chrome.exe", "cpu_percent": 15.2, "ram_mb": 850 } ]
    }
    ```
  - Response (202 Accepted):
    ```json
    {
      "accepted": true,
      "monitoring_enabled": true
    }
    ```
    *(Nếu `monitoring_enabled == false`, Agent tự động ngừng quét tiến trình & metrics).*
- **`GET /api/v1/agent/commands`**:
  - Trả về danh sách lệnh `PENDING` được giao cho thiết bị:
    ```json
    [
      {
        "command_id": 101,
        "command_type": "KILL_PROCESS",
        "parameters": { "pid": 1234 },
        "reason": "Chiếm CPU quá cao"
      }
    ]
    ```
- **`POST /api/v1/agent/commands/{command_id}/result`**:
  - Agent báo cáo kết quả thực thi:
    ```json
    { "status": "SUCCESS", "output": "Process 1234 killed successfully", "error_message": null }
    ```

### 5.2. Dành cho Mobile Client (Xác thực qua JWT Access Token)
Header: `Authorization: Bearer <access_token>`

- **Auth**: `POST /auth/login`, `POST /auth/register`, `POST /auth/refresh`, `GET /auth/me`.
- **Organizations & Groups**: `GET/POST /orgs`, `POST /orgs/join`, `GET /orgs/{id}/devices`, `POST /orgs/{id}/groups`.
- **Devices**:
  - `POST /orgs/{org_id}/devices`: Tạo thiết bị, trả về `secret_token` và câu lệnh cài đặt tự động `install_command`.
  - `GET /devices/{id}`: Xem chi tiết thông số phần cứng, live metrics và top tiến trình.
  - `POST /devices/{id}/toggle-monitoring`: Bật / Tắt theo dõi máy tính từ xa.
    - Body (tùy chọn): `{"is_monitoring": false}` (hoặc rỗng để tự đảo trạng thái).
    - Response: `{"device_id": 1, "is_monitoring": false, "status": "Paused"}`.
  - `DELETE /devices/{id}`: Xóa thiết bị khỏi hệ thống (Backend sẽ tự đẩy lệnh `UNINSTALL` xuống máy tính để gỡ sạch file).
  - `POST /devices/{id}/commands`: Gửi lệnh điều khiển (`REBOOT`, `RESTART_SERVICE`, `KILL_PROCESS`).
- **App Update**:
  - `GET /api/v1/app/version` (Public):
    ```json
    {
      "version_code": 8,
      "version_name": "1.0.7",
      "apk_url": "/static/dms-app.apk",
      "apk_size_bytes": 66705349,
      "release_notes": "• Thêm nút bật/tắt theo dõi máy tính từ xa\n• Tự động gỡ cài đặt và xóa toàn bộ file khi xóa PC trên App",
      "force_update": false
    }
    ```

---

## 6. Các cơ chế nghiệp vụ trọng yếu (Core Workflows)

### 6.1. Cài đặt 1 câu lệnh PowerShell (1-liner Silent Install)
Khi người dùng tạo máy tính mới trên App, Backend sinh mã và trả về câu lệnh PowerShell duy nhất:
```powershell
& ([scriptblock]::Create((irm 'http://<IP_SERVER>:8000/static/install.ps1'))) -Server http://<IP_SERVER>:8000 -Token <SECRET_TOKEN> -DeviceId <ID>
```
Script [backend/static/install.ps1](backend/static/install.ps1) thực hiện toàn bộ:
1. Tạo thư mục `C:\DMS-Agent\`.
2. Dừng và gỡ bỏ service cũ nếu tồn tại (`net stop DMS-Agent`, `sc delete DMS-Agent`).
3. Tải file thực thi silent `dms-agent.exe` từ server.
4. Ghi file cấu hình `config.json` với bảng mã UTF-8 không BOM.
5. Đăng ký Windows Service chuẩn và tự động chạy (`sc create DMS-Agent binPath="C:\DMS-Agent\dms-agent.exe" start=auto`).
6. Khởi động service và kiểm tra thiết bị chuyển sang trạng thái `Online` trên App.

---

### 6.2. Bật / Tắt theo dõi từ xa (Toggle Remote Monitoring)
Tính năng cho phép người dùng tạm ngừng theo dõi hoạt động tiến trình và tài nguyên trên máy tính khi muốn đảm bảo tính riêng tư, và kích hoạt lại bất kỳ lúc nào ngay từ điện thoại.
- **Trên App:** Nút gạt Switch nằm ở màn hình Chi tiết thiết bị (Tab Tổng quan).
- **Backend xử lý:**
  1. Cập nhật `device.is_monitoring = False/True`.
  2. Đổi trạng thái thiết bị thành `Paused` (khi tắt) hoặc `Online/Offline` (khi bật).
  3. Đẩy lệnh `PAUSE_MONITORING` hoặc `RESUME_MONITORING` vào hàng đợi `device_commands`.
  4. Trả về cờ `monitoring_enabled` trong mỗi nhịp `Heartbeat` như một kênh đồng bộ kép dự phòng.
- **Agent xử lý:**
  - Khi `is_monitoring == false`: Tạm dừng toàn bộ tác vụ quét WMI, GPU, và danh sách tiến trình (`top_processes`). Chỉ gửi một gói tin heartbeat tối thiểu để duy trì kết nối sống.
  - Khi `is_monitoring == true`: Tự động phục hồi thu thập dữ liệu đầy đủ ngay lập tức mà không cần thao tác nào trên máy tính.

---

### 6.3. Tự động gỡ cài đặt và dọn sạch file khi xóa PC (Remote Uninstall)
Khi quản trị viên nhấn **Xóa thiết bị** trên App:
1. Backend gọi `archive_device()`, chuyển trạng thái thiết bị sang `Archived`.
2. Backend tự động đẩy một lệnh `UNINSTALL` vào bảng `device_commands`.
3. Agent kéo lệnh `UNINSTALL`, thực hiện quy trình tự hủy sạch sẽ:
   - Tạo script độc lập `%TEMP%\dms-agent-uninstall.bat`.
   - Báo cáo kết quả `SUCCESS` về cho Backend trước để đóng hoàn tất phiên làm việc.
   - Chạy script `.bat` ngầm hoàn toàn ẩn (dùng cờ `CREATE_NO_WINDOW`).
   - Script chờ 2 giây để gói HTTP gửi xong, sau đó:
     ```bat
     net stop DMS-Agent
     sc delete DMS-Agent
     timeout /t 2 /nobreak >nul
     rmdir /s /q "C:\DMS-Agent"
     del "%~f0"
     ```
   - Kết quả: Máy tính được dọn dẹp sạch 100%, không còn file rác, service bị xóa vĩnh viễn.

---

### 6.4. Cơ chế Cập nhật ứng dụng di động qua Server (In-App Update)
Ứng dụng Android không phụ thuộc Google Play Store mà sử dụng module cập nhật nội bộ [AppUpdateManager.kt](mobile/app/src/main/java/com/example/myapplication/util/AppUpdateManager.kt):
1. Mỗi khi mở tab **Tài khoản (Profile) -> Cập nhật ứng dụng**, App gọi `GET /api/v1/app/version`.
2. So sánh `updateInfo.versionCode > currentVersionCode`.
3. Nếu có bản mới (hiện tại là `versionCode: 8`, `versionName: "1.0.7"`), App hiển thị dialog kèm release notes.
4. Bấm **Tải & Cài đặt ngay**:
   - Sử dụng `OkHttpClient` tải file APK từ `http://<SERVER>/static/dms-app.apk` lưu vào bộ nhớ cache riêng (`context.cacheDir/updates/`).
   - Sau khi tải xong, gọi Android `FileProvider` (`content://com.example.myapplication.fileprovider/...`) và mở `Intent(ACTION_VIEW)` với cờ `FLAG_GRANT_READ_URI_PERMISSION` để mở màn hình cài đặt của hệ điều hành.

---

### 6.5. Thu thập phần cứng Native WMI ẩn hoàn toàn (No-CMD Window)
- **Vấn đề trước đây:** Gọi lệnh PowerShell con (`powershell -Command Get-CimInstance...`) trên Windows khiến cửa sổ đen CMD/Console nhấp nháy liên tục trên màn hình máy tính của người dùng mỗi 10 giây.
- **Giải pháp dứt điểm:**
  1. Viết module [agent/internal/collector/wmi_windows.go](agent/internal/collector/wmi_windows.go) sử dụng trực tiếp Windows COM OLE API qua thư viện `github.com/go-ole/go-ole`. Truy vấn WMI trực tiếp trong RAM mà không spawn tiến trình con.
  2. Tất cả các lệnh thực thi hệ thống bắt buộc trong `collector` và `executor` đều được bọc qua hàm `hideWindow()`:
     ```go
     cmd.SysProcAttr = &windows.SysProcAttr{
         HideWindow:    true,
         CreationFlags: 0x08000000, // CREATE_NO_WINDOW
     }
     ```
  3. Biên dịch Agent với cờ GUI subsystem: `go build -ldflags="-H=windowsgui"`.

---

## 7. Lịch sử sửa lỗi & Cạm bẫy kỹ thuật (Bug Fixes & Technical Gotchas)

| Vấn đề gặp phải | Nguyên nhân gốc rễ | Giải pháp đã áp dụng triệt để |
|---|---|---|
| **Lỗi 500 khi Toggle Monitoring trên App** | `device_commands` có constraint `chk_cmd_type` chỉ cho phép `REBOOT, RESTART_SERVICE, KILL_PROCESS`. Khi lưu `PAUSE_MONITORING`, PostgreSQL ném `CheckViolationError`. | Cập nhật `chk_cmd_type` và `chk_device_status` trong SQL schema; bổ sung cơ chế **Self-healing** trong `services/devices.py` tự động sửa constraint và retry hoặc fallback cập nhật trạng thái nếu DB chưa migrate. |
| **Cửa sổ CMD nhấp nháy trên máy tính người dùng** | Agent chạy subprocess PowerShell con để đo nhiệt độ CPU & GPU. | Thay bằng native COM WMI (`go-ole`) và ép cờ `CREATE_NO_WINDOW (0x08000000)`. |
| **Agent lỗi parse JSON cấu hình** | Lệnh PowerShell `Out-File` mặc định tạo file UTF-8 có kèm BOM (Byte Order Mark `EF BB BF`). | Bổ sung hàm tự động phát hiện và cắt bỏ UTF-8 BOM trong `config.go`. |
| **App không nhận diện bản cập nhật APK mới** | File APK trên server là bản build cũ (code 7), `versionCode` chưa được tăng đồng bộ giữa `build.gradle.kts` và `main.py`. | Tăng version lên `v1.0.7 (Build 8)`, biên dịch debug APK mới, copy vào `backend/static/dms-app.apk` và unignore trên Git. |
| **Mock Object trong test SQLAlchemy** | Trong unit test khi khởi tạo `Device(device_id=1)`, trường `is_monitoring` có giá trị `None` (chưa qua DB server default). | Trong code kiểm tra logic, dùng `getattr(device, "is_monitoring", None) is not False` thay vì `if device.is_monitoring:`. |

---

## 8. Hướng dẫn cài đặt, chạy & kiểm thử (Run & Verification Guide)

### 8.1. Chạy Backend (FastAPI)
- **Môi trường:** Python 3.12+
- **Cài đặt thư viện:**
  ```bash
  cd backend
  pip install -r requirements.txt
  ```
- **Khởi động server phát triển:**
  ```bash
  uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
  ```
- **Kiểm tra chất lượng code & Unit tests:**
  ```bash
  ruff check .
  pytest -q
  # Kết quả chuẩn: 22 passed in ~2s
  ```

### 8.2. Chạy & Biên dịch Agent (Go)
- **Môi trường:** Go 1.22+ trên Windows 64-bit
- **Biên dịch bản Silent (chạy ngầm không cửa sổ):**
  ```powershell
  cd agent
  go build -ldflags="-H=windowsgui -s -w" -o dms-agent.exe ./cmd/agent
  ```
- **Biên dịch bản Debug (hiện console xem log):**
  ```powershell
  go build -o dms-agent-debug.exe ./cmd/agent
  ```
- **Đưa binary vào backend để phân phối:**
  ```powershell
  Copy-Item dms-agent.exe ..\backend\static\dms-agent.exe -Force
  Copy-Item dms-agent-debug.exe ..\backend\static\dms-agent-debug.exe -Force
  ```

### 8.3. Biên dịch Android App (Kotlin Compose)
- **Môi trường:** JDK 17, Android SDK Platform 36.
- **Biên dịch Debug APK:**
  ```powershell
  cd mobile
  .\gradlew assembleDebug
  ```
- **File APK đầu ra:**
  `mobile/app/build/outputs/apk/debug/app-debug.apk`
- **Đưa APK vào backend phục vụ In-App Update:**
  ```powershell
  Copy-Item mobile\app\build\outputs\apk\debug\app-debug.apk backend\static\dms-app.apk -Force
  ```

---

## 9. Thông tin Git & Trạng thái hiện tại (Repository & Git State)

- **GitHub Repository Remote:**
  `https://github.com/phan-cao-huy/dms-monitor.git`
- **Các nhánh hoạt động chính:**
  - `main` (Nhánh phát hành chính thức, luôn đồng bộ).
  - `feature/backend-devices` (Nhánh tính năng backend & devices, đã rebase khớp 100% với `main`).
- **Trạng thái Commit mới nhất:**
  - Đã tích hợp đầy đủ:
    - Tính năng Bật/Tắt theo dõi từ xa (Toggle Switch trên App + Heartbeat sync + Pause/Resume command).
    - Tự động gỡ cài đặt sạch sẽ khi xóa PC (Uninstall command + Detached batch cleanup).
    - Cơ chế tự vá constraint PostgreSQL tránh lỗi HTTP 500.
    - File APK v1.0.7 (Build 8) lưu sẵn trong `backend/static/dms-app.apk`.
- **Ghi chú cho Developer / AI kế thừa:**
  - Khi thêm bất kỳ `command_type` mới nào, hãy cập nhật đồng thời ở 3 nơi:
    1. Schema regex trong `backend/app/schemas/device.py`.
    2. CHECK constraint `chk_cmd_type` trong `database/dms01.sql` và migration `_ensure_db_schema()`.
    3. Whitelist switch-case trong `agent/internal/executor/executor.go`.
  - Mọi subprocess chạy trên Agent Windows **bắt buộc** phải sử dụng `hideWindow()` với cờ `CREATE_NO_WINDOW` để giữ trải nghiệm người dùng hoàn toàn tĩnh lặng.

> Ngưỡng cảnh báo và phần chuẩn bị push Android: xem docs/ALERT_NOTIFICATIONS.md. Đã cấu hình Firebase Android + worker, dry-run FCM thành công; chưa xác nhận nhận push trên điện thoại thật.

> Thuật ngữ giao diện: Tổ chức → Không gian làm việc (điều hướng: Không gian); Cụm thiết bị → Nhóm thiết bị; Phân quyền → Quyền truy cập. Dùng icon SpaceDashboard/workspace dạng vector, bỏ emoji ở tiêu đề quản lý không gian. API/database vẫn dùng org/group như cũ.

> Tổng quan thiết bị (1.0.17): Android đọc disks đầy đủ; trạng thái/cảnh báo, CPU/RAM/GPU, phân vùng, mạng, top 5 tiến trình và phần cứng thu gọn đồng bộ web. Dữ liệu thiếu hiển thị —, GPU 0% vẫn hiện; RAM GB dùng được ước tính từ tỷ lệ và tổng RAM (~). Android map created_at và cpu_pct đúng hợp đồng API. Chi tiết tự làm mới khi hiển thị mỗi 15 giây. Không suy đoán SSD/HDD hoặc disk I/O.

> 1.0.18: Hỏi quyền thông báo lần đầu mở Android; cảnh báo rõ tài nguyên/ngưỡng và top tiến trình, VI/EN. Tiến trình sắp CPU/RAM/GPU, heatmap, chọn/hover. Agent 1.2.0: CPU delta toàn máy, union top CPU/RAM/GPU, GPU per-process WMI busiest engine; cần cập nhật Agent trên PC. Backend tối đa 100 tiến trình, message_en và quy tắc GPU.

> 1.0.19: phản hồi lệnh web/Android theo command_id thực, polling tuần tự 2 giây; SUCCESS mới báo dừng PID thành công, FAILED/TIMEOUT hiện lỗi. Android sửa alias command_id/id, không giữ mã giả 0; tải lại tiến trình sau kết quả kill. Không thực thi kill thật để kiểm thử.

> 1.0.20: Giữ nguyên câu “Đang thực thi lệnh...” theo yêu cầu người dùng. Chrome có nhiều tiến trình: PID con bị kill không đồng nghĩa cửa sổ đóng. Dialog dừng cho chọn PID (mặc định) hoặc tất cả tiến trình cùng tên trên máy (chủ động chọn, cảnh báo mọi phiên và dữ liệu chưa lưu). Không tự mở rộng phạm vi dừng và không kill Chrome thật khi kiểm thử.
