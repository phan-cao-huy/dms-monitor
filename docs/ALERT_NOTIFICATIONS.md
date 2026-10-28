# Ngưỡng cảnh báo và thông báo Android

## Dùng chung web / Android
- Web: Cảnh báo → Quy tắc cảnh báo → Tạo quy tắc / Chỉnh ngưỡng.
- Android: Cảnh báo → Ngưỡng cảnh báo → Thêm quy tắc / Chỉnh ngưỡng.
- Owner/Admin quản lý quy tắc trên server. Có ô số và thanh kéo, thời lượng liên tục, cooldown và tạm tắt.
- Quy tắc cho thiết bị hoặc cụm. CPU/RAM/DISK: 0–100%; mạng: KB/s; OFFLINE: phút.
- PATCH /api/v1/orgs/{org_id}/alert-rules/{rule_id} giữ nguyên ID và phạm vi; đổi ngưỡng sẽ bắt đầu lại cửa sổ đánh giá liên tục.
- Worker đánh giá mỗi phút. Cảnh báo cần thỏa thời lượng quy tắc; không kỳ vọng cảnh báo tức thì ngay sau khi kéo thanh.
- Dữ liệu web và Android cùng database. Android tải lại quy tắc khi mở mục và mỗi 30 giây khi mục đang tồn tại.

## Trạng thái hiện tại (1.0.13)
Đã cấu hình Android và server cho cùng dự án Firebase projectx-c57b9.
Khóa dịch vụ nằm ngoài Git, worker mount chỉ đọc qua docker-compose.override.yml (local, gitignore).
FCM_ENABLED=true trên worker. Firebase Admin SDK dry-run gửi tới topic kiểm tra thành công; không phát thông báo.
APK 1.0.13 có FIREBASE_CONFIGURED=true. Cần cài trên điện thoại, đăng nhập và cho phép thông báo để đăng ký token.
Chưa xác minh nhận push trên thiết bị thật. Nút Gửi thông báo thử chỉ kiểm tra thông báo cục bộ.

## Cấu hình Firebase một lần
1. Mở https://console.firebase.google.com/ và tạo dự án DMS (không bắt buộc Analytics).
2. Thêm ứng dụng Android, package chính xác: com.example.myapplication.
3. Tải google-services.json vào mobile/app/google-services.json (đã gitignore).
4. Trong Project settings → Service accounts → Generate new private key.
   Lưu JSON này bên ngoài Git, ví dụ C:/Users/NTK215/.android/dms-firebase/service-account.json.
   Đây là khóa bí mật, không đưa vào APK/web hoặc commit lên Git.
5. Bật Firebase Cloud Messaging API (HTTP v1) cho cùng dự án.
6. Build APK lại bằng cùng keystore release. Gradle chỉ áp dụng Google Services khi file cấu hình tồn tại.
7. Mount khóa server vào worker. Dùng docker-compose.firebase.yml.example làm mẫu:
   đặt FIREBASE_CREDENTIALS_HOST là đường dẫn tuyệt đối đến khóa server trong .env,
   rồi chạy docker compose -f docker-compose.yml -f docker-compose.firebase.yml.example up -d --build worker.
8. Mở APK mới, đăng nhập, vào Cảnh báo → Cho phép thông báo.
   Trong Cài đặt tài khoản, bật nhận thông báo và loại CPU/RAM/ngoại tuyến theo nhu cầu.
9. Thử một quy tắc trên thiết bị thử nghiệm, đưa app về nền, kiểm tra thông báo thực
   và notification_status trong lịch sử server. Không dùng dữ liệu giả làm bằng chứng push.

## Hành vi
- FCM high-priority data message → FirebaseMessagingService → NotificationCompat + kênh dms_alerts IMPORTANCE_HIGH.
- Android 13+ cần quyền POST_NOTIFICATIONS; người dùng có thể tắt âm thanh/kênh trong cài đặt Android.
- Token đăng ký theo phiên refresh, tự đăng ký lại khi đổi token; logout ngừng nhận và xóa thông báo.
- Payload gắn user_id; client bỏ tin trễ của tài khoản trước và tôn trọng tùy chọn thông báo.
- Server chỉ gửi tới thành viên có quyền trên thiết bị/cụm, thử lại lỗi gửi trong 24 giờ.
- Không cần giữ app chạy nền liên tục. Android force-stop, không có mạng, không có Google Play Services,
  tắt quyền/kênh hoặc giới hạn pin của hãng có thể ngăn/chậm thông báo.
- Đã xác thực kết nối FCM bằng dry-run; chưa kiểm chứng end-to-end trên điện thoại thật.

Tham khảo:
https://firebase.google.com/docs/cloud-messaging/android/get-started
https://firebase.google.com/docs/cloud-messaging/android/receive
