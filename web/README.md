# DMS Monitor Web

Trang chủ và ứng dụng quản trị responsive, dùng API hiện có tại
`https://dms.ky215.io.vn/api/v1/`. Không có database, tài khoản hoặc hệ thống
phân quyền thứ hai. Tài khoản Android dùng được trên web.

## Mở web

Không mở `dist/index.html` trực tiếp bằng `file://`: trình duyệt chặn ES modules.

Chạy trong thư mục `web`:

```powershell
python -m http.server 4173 --bind 127.0.0.1 --directory dist
```

Mở `http://127.0.0.1:4173/`. Nút **Khám phá giao diện** dùng dữ liệu minh họa
và chặn toàn bộ thao tác ghi lên API. Đăng nhập để xem dữ liệu thật.

## Dùng cùng server DMS

```powershell
node scripts/sync.mjs
```

Lệnh này đồng bộ `dist/` sang `backend/static/web/`. FastAPI phục vụ trang chủ
tại `/`, sau các route API, tài liệu API và file cài đặt hiện có. Docker image
backend mang theo các file này khi build. Sau khi đưa mã lên server, rebuild
backend theo quy trình triển khai để dùng tại `https://dms.ky215.io.vn/`.
Các route ứng dụng dùng hash (`/#/devices`) nên không cần rewrite ở Cloudflare.

## Tính năng

- Trang chủ, đăng ký, đăng nhập, refresh token, đăng xuất/thu hồi phiên.
- Chọn tổ chức; dashboard và bộ lọc/tìm kiếm/phân trang thiết bị.
- Thông tin phần cứng, ổ đĩa, GPU, mạng, nhiệt độ, uptime, heartbeat.
- Biểu đồ raw/hourly và xuất CSV; dữ liệu thiếu được hiển thị là thiếu.
- Tiến trình, dừng PID, reboot, restart service, bật/tắt theo dõi.
- Tạo/sửa/xóa thiết bị và tạo lại token agent; lệnh cài dùng server HTTPS.
- Cảnh báo, tiếp nhận/giải quyết; rule CPU/RAM/Disk/Network/Offline/Service/Process.
- Tổ chức, tham gia/rời, cụm thiết bị, thành viên, vai trò và mã mời.
- Lịch sử lệnh, tài khoản, giao diện sáng/tối và tải APK.
- Theo dõi tự động mỗi 15 giây khi tab hiển thị; tạm ngừng khi đang mở dialog.

Các thao tác nguy hiểm yêu cầu xác nhận/lý do. Xóa thiết bị và tạo lại token
yêu cầu nhập tên thiết bị. Server là nơi quyết định quyền cuối cùng; web phản hồi
403 như lỗi, không giả báo thành công. Token agent mới chỉ hiện trong dialog
hiện tại. Session JWT lưu trong `sessionStorage`, không ghi vào URL hay log.

API hiện tại chưa có endpoint chỉnh hồ sơ/đổi mật khẩu. Trang tài khoản hiển thị
thông tin thực tế và giải thích giới hạn này, không tạo nút lưu giả.

## Kiểm tra

```powershell
npm run check
npm test
node scripts/sync.mjs
```

Backend có `tests/test_web.py` kiểm tra trang chủ/assets và API vẫn hoạt động
chung một server. Kiểm thử luồng đăng nhập/thao tác trên tài khoản thật cần
tài khoản do chủ hệ thống cung cấp; không tự tạo dữ liệu vào database production.

## Tham khảo thiết kế

Giao diện được viết mới, không sao chép mã hoặc tài nguyên của các dự án tham khảo:

- [CoreObs](https://github.com/crocofied/CoreObs): phân cấp tổng quan/thiết bị/chi tiết.
- [FastAPI full-stack template](https://github.com/fastapi/full-stack-fastapi-template): sidebar, theme, tài khoản và điều hướng theo quyền.
- [Industrial monitoring](https://github.com/AntonKli/industrial-monitoring-backend): health/telemetry, bộ lọc và trạng thái loading/empty/error.

Không thêm database hoặc hệ thống xác thực của các repo này vào DMS.

## Sites

`.openai/hosting.json` gắn cùng frontend với bản Sites riêng tư. Nội dung buildless
để trong `dist/`; các helper Sites không cần truy cập JWT DMS, khóa Android, `.env`
hoặc dữ liệu PostgreSQL. Các dữ liệu đó không nằm trong source frontend.
