# DMS Agent — Hướng dẫn cài đặt

> **Agent v1.1.0** — Chạy ngầm, không cần Go, không cần cửa sổ CMD

---

## Yêu cầu

- Windows 10/11 (64-bit) hoặc Linux x86_64
- Quyền Admin (để cài Windows Service)
- Server DMS Monitor đang chạy và có thể truy cập từ máy cài agent

---

## Bước 1 — Tải file agent

Tải file `dms-agent.exe` từ server:
```
http://<IP_SERVER>:8000/static/dms-agent.exe
```

Hoặc dùng phiên bản debug (hiển thị cửa sổ console để xem log trực tiếp):
```
http://<IP_SERVER>:8000/static/dms-agent-debug.exe
```

Tạo thư mục riêng, ví dụ: `C:\DMS-Agent\`

---

## Bước 2 — Lấy token và Device ID từ app

1. Mở app DMS Monitor → Đăng nhập
2. Vào **Danh sách thiết bị** → nhấn **"Thêm thiết bị mới"**
3. Điền tên máy → nhấn **Tạo**
4. Copy **Device ID** và **Secret Token** (token chỉ hiện 1 lần!)

---

## Bước 3 — Cấu hình agent (1 lần duy nhất)

Mở **Command Prompt** hoặc **PowerShell** với quyền Admin, chạy:

```powershell
cd C:\DMS-Agent
.\dms-agent.exe -server http://<IP_SERVER>:8000 -token <SECRET_TOKEN> -id <DEVICE_ID>
```

Agent sẽ tự động tạo file `config.json` trong cùng thư mục. Sau lần đầu không cần nhập lại.

**Ví dụ:**
```powershell
.\dms-agent.exe -server http://192.168.1.100:8000 -token dms_AbCdEfGhIj123 -id 15
```

Sau khi thấy log `[SENDER] OK Heartbeat gui thanh cong` → thiết bị đã Online trên app.

---

## Bước 4 — Cài lam Windows Service (chạy ngầm, tự khởi động)

Để agent chạy ngầm, **không cần mở cửa sổ CMD**, tự động bật khi máy khởi động:

```powershell
# Chạy với quyền Admin
cd C:\DMS-Agent
.\dms-agent.exe -install
```

Kết quả:
```
Da cai DMS Agent thanh Windows Service thanh cong!
Service se tu dong chay khi may tinh khoi dong.
```

Kiểm tra trong **Services** (`services.msc`) → tìm **"DMS-Agent"** → Status: Running

### Gỡ cài đặt Service:
```powershell
.\dms-agent.exe -uninstall
```

---

## Các file agent tạo ra

| File | Mô tả |
|------|-------|
| `config.json` | Cấu hình (server, token, device_id) — tự tạo lần đầu |
| `dms-agent.log` | Log của agent — ghi thường xuyên |

---

## Thông tin thu thập

Agent gửi heartbeat mỗi **10 giây** bao gồm:

| Thông số | Chi tiết |
|----------|---------|
| CPU | Phần trăm sử dụng, nhiệt độ (nếu có) |
| RAM | % và MB đang dùng |
| **Tất cả ổ cứng** | Từng ổ: tên, dung lượng, đã dùng, còn trống, % |
| Mạng | Tốc độ download/upload (KB/s) |
| GPU | Tên, %, nhiệt độ, VRAM (NVIDIA & AMD) |
| Uptime | Thời gian máy đã chạy |
| Process | Top 15 tiến trình chiếm CPU nhiều nhất |

---

## Các lệnh điều khiển từ xa

Qua app DMS Monitor, admin có thể gửi lệnh:

| Lệnh | Mô tả |
|------|-------|
| `REBOOT` | Khởi động lại máy (sau N giây) |
| `RESTART_SERVICE` | Khởi động lại Windows Service |
| `KILL_PROCESS` | Tắt tiến trình theo PID hoặc tên |

---

## Xử lý sự cố

**Máy vẫn bật nhưng app hiển thị Offline?**
1. Kiểm tra service đang chạy: `sc query DMS-Agent`
2. Xem log: `Get-Content C:\DMS-Agent\dms-agent.log -Tail 30`
3. Kiểm tra máy server có thể ping được không: `ping <IP_SERVER>`
4. Kiểm tra cổng 8000 mở: `Test-NetConnection <IP_SERVER> -Port 8000`

**Agent không kết nối được?**
- Đảm bảo `backend_url` trong `config.json` đúng IP server
- Server phải chạy (`docker ps` trên máy server)
- Firewall không chặn port 8000

---

## Build từ mã nguồn (tùy chọn)

Nếu muốn tự build (cần Go 1.22+):

```powershell
cd dms-monitor/agent

# Build ẩn cửa sổ (production)
go build -ldflags="-s -w -H=windowsgui" -o dms-agent.exe ./cmd/agent/

# Build hiện console (debug)
go build -ldflags="-s -w" -o dms-agent-debug.exe ./cmd/agent/
```
