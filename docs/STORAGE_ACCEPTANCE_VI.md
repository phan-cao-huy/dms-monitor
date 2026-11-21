# DMS 1.0.38 — tự kiểm tra 5 chức năng lưu trữ

Dùng hai tài khoản Owner/Admin và Viewer trong không gian thử. Trên web mở **Cài đặt → Dung lượng & lưu trữ**. Trên Android mở **Công cụ → Dung lượng & lưu trữ**. Cả hai đọc cùng server. Khi so sánh số đo, cho phép sai khác do thời điểm và đơn vị hiển thị.

| Mục | Thao tác | Kết quả mong đợi |
|---|---|---|
| 1. Tổng quan | Mở hai giao diện bằng Owner/Admin, đối chiếu tổng database, ổ trống, từng nhóm và lần dọn gần nhất. Chuyển sang Viewer. | Quản trị thấy số liệu toàn server. Viewer không có mục quản lý, truy cập API trực tiếp trả 403. Trước lần dọn đầu tiên có thể hiện “Chưa ghi nhận”. |
| 2. Xem trước | Trong một không gian có dữ liệu, lần lượt nhập 1 và 30 ngày cho cùng loại dữ liệu. Thử số âm, 0 hoặc hơn 3650 trong **xem trước hiệu năng**. | 1 ngày cho số bản ghi ngoài hạn lớn hơn hoặc bằng 30 ngày. Có mô tả ảnh hưởng và khoảng thời gian nếu có bản ghi. Dữ liệu chỉ thuộc không gian đã chọn. Giá trị sai bị từ chối. Mở lại biểu đồ, việc xem trước không làm mất dữ liệu. |
| 3. Dọn có kiểm tra | Trên database thử, kiểm tra mẫu hết hạn trong ba tình huống: thiếu bản phút, sai đỉnh CPU ở bản phút, sai số mẫu ở bản giờ. Sau đó sửa bản tổng hợp và chạy lại. | Nguồn cần thiết cho báo cáo được giữ nếu bản tổng hợp không khớp. Trạng thái ghi nhận dữ liệu được giữ lại. Khi đã khớp, dữ liệu hết hạn đủ điều kiện được dọn. |
| 4. Lịch sử cảnh báo | Tạo hai không gian thử. A có cảnh báo đang mở, đã xử lý gần đây và đã xử lý quá 30 ngày. Để B giữ mãi. Đặt A giữ 30 ngày, xem trước, lưu, mở lại trên thiết bị kia. | Chính sách đồng bộ. Sau lượt dọn 08:15 giờ Việt Nam, chỉ cảnh báo **đã xử lý quá 30 ngày** của A bị xóa. Chọn 0 để dừng hết hạn về sau. Tài khoản Viewer không thể đổi chính sách. |
| 5a. Sao lưu | Chạy Compose, mở màn hình lưu trữ và kiểm tra thư mục backup trên máy chủ. Khởi động lại dịch vụ backup trong cùng ngày. | Có một file dms-YYYY-MM-DD.dump theo ngày UTC, kích thước lớn hơn 0. Màn hình báo ngày và dung lượng. Khởi động lại cùng ngày không tạo thêm bản trùng. |
| 5b. Khôi phục | Trên môi trường thử, tạo database PostgreSQL 15 rỗng riêng và phục hồi bằng lệnh dưới đây. Đọc bảng thiết bị, cảnh báo và dữ liệu hiệu năng. | pg_restore thoát với mã 0. Dữ liệu có thể truy vấn. Các thay đổi phát sinh sau thời điểm chụp không nằm trong bản sao. |
| 5c. Cảnh báo ổ đĩa | Dùng số đo mô phỏng 79,99%, 80%, 89,99% và 90%. Trên môi trường thử có FCM, dùng một điện thoại Owner/Admin đã bật quyền thông báo và một Viewer. | Trạng thái lần lượt bình thường, cảnh báo, cảnh báo, nghiêm trọng. Worker kiểm tra mỗi 5 phút. Mức mới kích hoạt thông báo cho Owner/Admin có phiên FCM hợp lệ. Cùng mức nhắc lại sau 24 giờ. Khi trở về bình thường, một đợt mới có thể phát cảnh báo lại. |
| 5d. Mất dịch vụ sao lưu | Trong môi trường thử, dừng riêng container backup trên 20 phút rồi mở màn hình. Khởi động lại. | Trạng thái báo đã cũ. Sau khi chạy lại, dịch vụ cập nhật trạng thái và tạo bản ngày hiện tại nếu còn thiếu. |

## Chạy các ca khó tạo bằng tay

Từ thư mục dự án, với Docker Compose và database thử đang hoạt động:

```powershell
docker compose exec -T -e TEST_POSTGRES=1 backend python -m pytest -q tests/test_postgres_reliability.py tests/test_alert_history_retention.py tests/test_storage_pressure.py
```

Các test PostgreSQL dùng schema giao dịch hoặc bảng tạm. Test dung lượng dùng số đo giả lập để không cần làm đầy ổ thật.

## Nơi lưu và cách phục hồi

Dịch vụ **backup** chạy trên máy chủ Docker. Đặt `DMS_BACKUP_DIR=D:/DMS-Backups` trong .env trên Windows, hoặc đặt đường dẫn tới ổ gắn cho VM/NAS trên Linux. Nếu không cấu hình, Compose dùng thư mục dms-backups bên cạnh thư mục dự án. Cho tài khoản chạy Docker quyền ghi và giới hạn quyền đọc cho quản trị viên. Bản dump chứa dữ liệu vận hành, tài khoản và hash mật khẩu. Không đưa file này vào Git hay thư mục static.

Dịch vụ chạy pg_dump định dạng custom, đọc lại toàn bộ archive bằng pg_restore rồi mới công bố file cuối. Dịch vụ kiểm tra mỗi 5 phút và tạo bản đầu tiên ngay khi khởi động. Sau khi ghi thành công, dịch vụ dọn các bản theo mẫu tên của DMS đã đủ 14 ngày. Ổ cùng máy vẫn chịu rủi ro khi mất máy. Có thể đặt thư mục đích trên thiết bị lưu trữ riêng. Chỉ số % dung lượng trên giao diện thuộc ổ dữ liệu PostgreSQL trong Docker, có thể khác ổ vật lý Windows và ổ lưu backup.

Ví dụ phục hồi vào **database thử rỗng**, dùng tài khoản có quyền tạo các đối tượng:

```sh
pg_restore --exit-on-error --no-owner --no-acl -U <user> -d <database_thu> <duong_dan_dump>
```

Bản sao gồm database DMS. Khi chuyển máy chủ, cấu hình .env, khóa mã hóa, Firebase, khóa ký Android và cấu hình tunnel cần được lưu riêng ở nơi bảo mật.
