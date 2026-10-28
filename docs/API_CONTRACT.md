# API Contract v0.1 (nguồn sự thật chung của 3 dev)

Base URL: `http://localhost:8000/api/v1` · Swagger: `http://localhost:8000/docs`
Lỗi trả về dạng `{"detail": "..."}`. Thời gian: ISO-8601 UTC.

## Agent (auth: `Authorization: Bearer <secret_token>`)

### POST /agent/heartbeat  → 202
```json
{
  "cpu_usage": 23.5, "cpu_load_1m": 0.8, "cpu_load_5m": 0.6,
  "ram_usage": 61.2, "ram_used_mb": 9800,
  "disk_usage": 74.0, "disk_used_gb": 350.2,
  "net_in_kbps": 120.4, "net_out_kbps": 45.1,
  "ping_latency_ms": 12.3, "uptime_seconds": 86400,
  "agent_version": "0.1.0"
}
```
> Lệch với guide gốc: `device_id` và `secret_token` không đặt trong body mà dùng header — backend nhận diện thiết bị từ hash token, tránh agent giả mạo device khác.

### GET /agent/commands → 200
```json
[{"command_id": 12, "command_type": "KILL_PROCESS", "parameters": {"pid": 5678, "process_name": "java"}}]
```
`command_type` ∈ `REBOOT | RESTART_SERVICE | KILL_PROCESS`

### POST /agent/commands/{command_id}/result
```json
{"status": "SUCCESS", "output": "...", "error_message": null}
```

## Auth (Mobile)
| Method | Path | Mô tả |
|---|---|---|
| POST | /auth/register | username, email, password, full_name |
| POST | /auth/login | → `{access_token, refresh_token, token_type}` |
| POST | /auth/refresh | rotate refresh token |
| POST | /auth/logout | revoke refresh token |
| POST | /auth/fcm-token | đăng ký FCM token |
| GET | /auth/me | thông tin tài khoản hiện tại, yêu cầu access token |
| GET | /auth/me/detail | thông tin chi tiết đầy đủ tài khoản hiện tại |
| GET | /auth/users | danh sách tất cả tài khoản người dùng trong hệ thống |

`register` nhận JSON `{"username":"linh","email":"linh@example.com","password":"...","full_name":"..."}`; username dài 3–50 ký tự, password tối thiểu 8 và tối đa 72 byte UTF-8. Email/username trùng trả `409`. `login` nhận `{"identifier":"linh@example.com","password":"..."}`; `identifier` có thể là email hoặc username. Refresh và logout nhận `{"refresh_token":"..."}`. Refresh token được rotate mỗi lần dùng và chỉ bản SHA-256 được lưu trong auth_tokens. Các endpoint ngoài register/login/refresh/logout yêu cầu `Authorization: Bearer <access_token>`; FCM nhận `{"refresh_token":"...","fcm_token":"...","device_info":"..."}`. `GET /auth/users` trả về danh sách toàn bộ tài khoản với đầy đủ thông tin (ngoại trừ password_hash).

Backend dùng SQLAlchemy async với driver `asyncpg`; `DATABASE_URL` có dạng `postgresql+asyncpg://user:password@host:5432/database`. Chạy trong Docker Compose thì `host` là `db`; chạy backend trực tiếp trên máy host thì dùng `localhost`. `database/dms01.sql` được Docker nạp khi khởi tạo volume PostgreSQL lần đầu.

Với schema hiện tại, refresh token được lưu trong `auth_tokens` (chỉ lưu SHA-256). `POST /auth/fcm-token` nhận `{"refresh_token":"...","fcm_token":"...","device_info":"..."}` để gắn FCM vào đúng phiên đăng nhập; FCM được giữ khi refresh token được rotate.

## Organization
| Method | Path |
|---|---|
| GET/POST | /orgs |
| POST | /orgs/join  `{invite_code}` |
| GET/POST | /orgs/{org_id}/invitations |
| GET/PATCH/DELETE | /orgs/{org_id}/members[/{user_id}] |
| GET/POST | /orgs/{org_id}/groups |

Tất cả endpoint tổ chức yêu cầu `Authorization: Bearer <access_token>`. `POST /orgs` nhận `{"org_name":"Tên tổ chức","description":"...","logo_url":"..."}`; người tạo tự động có role `Owner`. `GET /orgs` chỉ trả về tổ chức mà user hiện tại tham gia. `POST /orgs/join` nhận `{"invite_code":"A3K9MZ"}`; mã phải còn hạn và chưa hết lượt sử dụng. Chỉ `Owner`/`Admin` được tạo hoặc liệt kê invitation và tạo group; invitation chỉ cấp role `Manager` hoặc `Viewer`. Thành viên của org có thể xem danh sách members/groups. PATCH `/orgs/{org_id}/members/{user_id}` nhận `{"role":"Admin|Manager|Viewer"}`; chỉ Owner được cấp role Admin, không thể đổi hoặc xóa Owner.

## Devices & Metrics (Mobile, JWT)
| Method | Path | Mô tả |
|---|---|---|
| GET | /orgs/{org_id}/devices | `[{id, name, status, cpu_usage, ram_usage, last_heartbeat, group_id}]` |
| POST | /orgs/{org_id}/devices | tạo thiết bị → trả `secret_token` (chỉ 1 lần) |
| GET | /devices/{id} | chi tiết + static info |
| GET | /devices/{id}/metrics?from=&to=&interval=raw\|hourly | dữ liệu biểu đồ |
| GET | /devices/{id}/processes | snapshot process mới nhất |
| POST | /devices/{id}/commands | `{command_type, parameters, reason}` (reason bắt buộc) |
| GET | /devices/{id}/commands | lịch sử lệnh |

## Alerts
| Method | Path |
|---|---|
| GET/POST | /orgs/{org_id}/alert-rules |
| GET | /orgs/{org_id}/alerts?resolved=false |
| POST | /alerts/{id}/resolve |

`status` của thiết bị: `Online | Offline | Warning | Maintenance | Archived`


## T1 — Email channels (web 1.0.21)

All routes require a valid access token and Owner/Admin membership in `{org_id}`.
- `GET /api/v1/orgs/{org_id}/email-channels`: list channels, config without password.
- `POST /api/v1/orgs/{org_id}/email-channels`: create, returns 201.
- `PUT /api/v1/orgs/{org_id}/email-channels/{channel_id}`: replace settings; omit/null password to retain the existing one.
- `DELETE /api/v1/orgs/{org_id}/email-channels/{channel_id}`: delete channel and ledger, returns 204.
- `POST /api/v1/orgs/{org_id}/email-channels/{channel_id}/test`: send a test, returns `{"status":"SENT"}` when SMTP accepts; 429 within 60 seconds, 502 on SMTP failure.
- `GET /api/v1/orgs/{org_id}/email-channels/{channel_id}/deliveries`: latest 50 entries with `alert_id`, `status`, `attempts`, `last_error`, `sent_at`.

Create/replace body:
```json
{"name":"Gmail alerts","enabled":true,"config":{"host":"smtp.gmail.com","port":587,"username":"sender@example.com","password":"APP_PASSWORD","from_email":"sender@example.com","recipients":["recipient@example.com"],"language":"vi"}}
```
Only 465/587 are accepted, with mandatory TLS. Recipients: 1–20; language: `vi`/`en`.
Passwords are encrypted at rest and never returned. Missing encryption key returns 503.
SMTP errors are safe codes: `SMTP_AUTH_FAILED`, `SMTP_CONNECTION_FAILED`, `SMTP_DELIVERY_FAILED`.

Alert rules now accept `CPU_TEMP`. Default rules have nullable `workspace_id` and `default_key`
in responses, and no device/group target. Only Owner/Admin can list workspace defaults.
Existing create rules still require exactly one device/group target. Default OFFLINE uses the
individual device setting and rejects a threshold PATCH. DELETE on a default disables it.
See `docs/T1_EMAIL.md` for delivery and migration details.
