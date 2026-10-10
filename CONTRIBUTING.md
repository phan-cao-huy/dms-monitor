# Quy trình làm việc nhóm

## Nhánh
- `main`: luôn chạy được, **cấm push trực tiếp** (bật Branch protection, yêu cầu 1 review + CI xanh).
- `develop`: nhánh tích hợp.
- `feature/<module>-<mô-tả>`: vd `feature/backend-auth`, `feature/agent-heartbeat`, `feature/mobile-dashboard`.

## Commit (Conventional Commits)
`feat(backend): add login endpoint` · `fix(agent): retry on timeout` · `docs: update API contract`

## Quy tắc ghép nối
1. Muốn đổi API → sửa `docs/API_CONTRACT.md` trong cùng PR, tag người dùng API đó.
2. Không commit secret (`.env`, `google-services.json`, token). Dùng `.env.example`.
3. Mỗi PR chỉ đụng thư mục của mình, trừ `docs/`.
