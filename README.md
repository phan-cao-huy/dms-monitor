# DMS — Device Monitoring System

Hệ thống giám sát máy tính từ xa: **Agent** (Go) thu thập CPU/RAM/Disk gửi về **Backend** (FastAPI + PostgreSQL); **Mobile App** (Android) xem biểu đồ, nhận cảnh báo và điều khiển từ xa. Mô hình multi-tenant theo Organization.

```
dms-monitor/
├── backend/     FastAPI (Dev 1)         ├── agent/    Go agent (Dev 2)
├── database/    schema.sql (22 bảng)    ├── mobile/   Android Compose (Dev 3)
├── docs/        ARCHITECTURE, API_CONTRACT
└── docker-compose.yml, Makefile, .github/workflows
```

📐 Kiến trúc: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · 🔌 API: [docs/API_CONTRACT.md](docs/API_CONTRACT.md) · 🤝 [CONTRIBUTING.md](CONTRIBUTING.md)

## Chạy nhanh
```bash
cp .env.example .env        # sửa mật khẩu/secret
make up                     # Postgres (tự nạp schema.sql) + Backend + Worker
open http://localhost:8000/docs
```
Agent: `cp agent/configs/config.example.json agent/configs/config.json && make agent`

## Phân công
| Dev | Module | Việc đầu tiên |
|---|---|---|
| 1 | backend, database | models + `/auth/login` + `/agent/heartbeat` thật |
| 2 | agent | thu thập metrics + gửi heartbeat có retry |
| 3 | mobile | tạo project Android, Login + Dashboard (mock → API thật) |
