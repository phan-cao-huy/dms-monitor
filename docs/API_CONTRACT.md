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

## Organization
| Method | Path |
|---|---|
| GET/POST | /orgs |
| POST | /orgs/join  `{invite_code}` |
| GET/POST | /orgs/{org_id}/invitations |
| GET/PATCH/DELETE | /orgs/{org_id}/members[/{user_id}] |
| GET/POST | /orgs/{org_id}/groups |

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
