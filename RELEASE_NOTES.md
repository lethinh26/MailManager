# Release Notes — MailManager v0.1.0

**Ngày phát hành:** 2026-11-22  
**Kiến trúc:** Java 21 • Spring Boot 3.3.5 (`Mail Server`) • Pure Java Swing + Java2D (`Mail Client`) • MySQL 8 + Flyway • TLS 2 lớp (Client ↔ Server HTTPS 8443, Server ↔ Gmail IMAPS 993 / SMTPS 465)

---

## 1. Đã hoàn thiện trong v0.1.0

### Mail Server (`Mail Server/`)
- **Hạ tầng & Triển khai**: Docker Compose (`mailapp-server` + `mailapp-mysql`), Flyway migrations (`V1__init.sql`, `V2__gmail_last_verified_at.sql`), script tạo keystore TLS PKCS12, script khởi chạy/dọn dẹp và cấu hình IntelliJ `.run/`.
- **Xác thực & Bảo mật**:
  - Đăng ký, đăng nhập JWT (`HS256`, khóa ≥ 256-bit), bắt buộc đổi mật khẩu lần đầu (`MustChangePasswordFilter`), xem hồ sơ `/api/auth/me`, đăng xuất thu hồi tức thì.
  - Mã hóa AES-256-GCM cho toàn bộ refresh token và App Password trong MySQL (`AesCipher`); BCrypt cho mật khẩu tài khoản hệ thống.
  - Chặn tấn công brute-force / rate limit cho đăng nhập, đăng ký, đổi mật khẩu, gửi thư và tải tệp đính kèm (`429 Too Many Requests` + `Retry-After`).
  - Security headers đầy đủ (`HSTS`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`), không dùng cookie phiên.
- **Liên kết Gmail (M06 & M07)**:
  - Google OAuth2 Authorization Code + **PKCE (S256)** + CSRF state một lần + xác minh scope `https://mail.google.com/` + tự động làm mới access token (`XOAUTH2`) chống race condition + thu hồi token khi hủy liên kết.
  - Hỗ trợ liên kết App Password 16 ký tự, kiểm tra kết nối IMAP/SMTP thực (`PATCH /api/gmail-accounts/{id}` và `/test`).
  - Tối ưu `MailConnectionFactory` với `mail.imaps.partialfetch=true`, `mail.imaps.fetchsize=262144` (256 KB) và stream buffer `64 KB` (giảm thời gian tải stream 25 MB từ `46,1s` xuống `4,2s` trong GreenMail benchmark).
- **Hộp thư IMAP/SMTP & An toàn nội dung (M08/M10/M12/M16/M17)**:
  - Liệt kê thư mục & danh sách thư phân trang (không lưu nội dung thư vào DB), đọc chi tiết thư với **Jsoup HTML Sanitizer** (chặn XSS, script/CSS độc, ảnh theo dõi từ xa), gửi thư đính kèm MIME/Base64, stream tải tệp đính kèm, đánh dấu đọc/gắn sao/xóa thư (Trash & vĩnh viễn).
  - Phòng chống path traversal & tên tệp đặc biệt trên Windows (`FilenameSanitizer`).
- **Module Quản trị (Admin APIs)**:
  - Đầy đủ `EP-AD1..AD8`, `EP-S1/S2`, `EP-ST1/ST2`: quản lý người dùng (tạo, lọc, khóa/mở khóa vô hiệu JWT ngay, đổi vai trò, đặt lại mật khẩu tạm trả một lần, xóa cascade + thu hồi OAuth2), chống tự khóa/xóa và bảo vệ Admin cuối cùng (`409 LAST_ADMIN`).
  - Cấu hình hệ thống động có cache & validate (không cho tắt đồng thời OAuth2 và App Password), thống kê tổng quan, nhật ký kiểm toán + xuất CSV chống CSV Injection.
  - Bảo đảm ranh giới bảo mật: Admin **không** có API xem nội dung thư hay mật khẩu/token Gmail của người dùng.

### Mail Client (`Mail Client/`)
- **Giao diện thuần Java Swing + Java2D** (không FlatLaf/thư viện UI ngoài), hỗ trợ giao diện Sáng/Tối, mọi lời gọi mạng chạy bất đồng bộ qua `SwingWorker` có hỗ trợ hủy và đóng luồng sạch.
- **TLS Pinning & An toàn cục bộ**: Xác thực chứng chỉ máy chủ qua `certs/mailapp.cer`, không lưu mật khẩu/token xuống đĩa (`~/.mailclient/config.properties` chỉ lưu cấu hình giao diện).
- **Nghiệp vụ đầy đủ**: Đăng nhập, đăng ký, đổi mật khẩu bắt buộc, quản lý tài khoản Gmail (OAuth2 trình duyệt + App Password), đọc thư HTML sạch (chặn ảnh từ xa mặc định, xác nhận trước khi mở liên kết ngoài, cảnh báo trước khi mở 30 đuôi tệp thực thi nguy hiểm `.exe/.bat/.js/.jar/.ps1/.lnk/...`), soạn & gửi thư có đính kèm.
- **Tự động tải tệp đính kèm rồi xóa thư (`AttachmentCleanupService`)**: Xem trước trước khi chạy, tiến trình + nút Hủy, chống trùng tên tệp, bảo vệ an toàn khi đầy ổ đĩa/lỗi tải (**tuyệt đối không xóa thư nếu tải chưa hoàn tất**).
- **AdminFrame 4 tab**: Người dùng, Cấu hình hệ thống, Thống kê (biểu đồ Java2D tự vẽ), Nhật ký kiểm toán (lọc & xuất CSV).
- **Thông báo lỗi tiếng Việt thân thiện** kèm hướng dẫn khắc phục cho mọi lỗi mạng/TLS/HTTP/đĩa.

### Kiểm thử & Chất lượng
- **192 bài kiểm thử tự động** (`29` file Server = `136` tests, `11` file Client = `56` tests; kết quả `190 passed`, `2 skipped` là `RealGmailIntegrationTest` cần Gmail thật, `0 fail`).
- **Tải 20 người dùng đồng thời** trên Docker HTTPS thật: `100/100 OK`, `0 lỗi`, `313.5 RPS`, `P95 = 114 ms`.

---

## 2. Chưa làm / Phạm vi ngoài v0.1.0
- Chưa hỗ trợ nhiều nhà cung cấp ngoài Gmail (Outlook/Yahoo/IMAP tùy biến).
- Chưa hỗ trợ soạn thảo Rich Text (WYSIWYG) và lưu nháp (Drafts) phía Client.
- Chưa tích hợp đồng bộ đẩy thời gian thực (IMAP IDLE / WebSocket push); Client làm mới chủ động hoặc theo thao tác người dùng.
- Chưa phát hành chứng chỉ TLS công khai (CA thương mại); bản phát hành dùng chứng chỉ tự ký (self-signed PKCS12) kèm truststore pinning cho Client.

---

## 3. Giới hạn đã biết (Known Limitations)
- **Google OAuth2 chế độ Testing**: Refresh token hết hạn sau **7 ngày** và chỉ các tài khoản nằm trong danh sách *Test users* của Google Auth Platform mới liên kết được (cho tới khi ứng dụng được Google xác minh Production).
- **Ràng buộc phiên bản Spring Boot 3.3.5**: Một số advisory mới trên `tomcat-embed-core:10.1.31` / `spring-security-core:6.3.4` (thuộc BOM Spring Boot 3.3.5) cần nâng lên Spring Boot 3.4+/3.5+ nên tạm giữ nguyên `3.3.5` theo ràng buộc dự án; các thư viện leaf tương thích (`jsoup 1.19.1`, Client `jackson 2.18.3`) đã được vá.
- **Kích thước tải lên / đính kèm**: Giới hạn mặc định theo `MAX_ATTACHMENT_SIZE_MB` (mặc định 25 MB, tổng MIME gửi đi ≤ 25 MB theo giới hạn Gmail).

---

## 4. Cách chạy nhanh

### Yêu cầu môi trường
- **Java 21 (JDK 21)**, **Docker Desktop** (chạy MySQL + Mail Server), **PowerShell 5.1+ / 7+**.

### Bước 1 — Khởi tạo cấu hình & chứng chỉ cho Mail Server
1. Tạo tệp `Mail Server/.env` với các biến bắt buộc (`DB_PASSWORD`, `JWT_SECRET` ≥ 32 byte, `AES_KEY` Base64 đúng 32 byte, `KEYSTORE_PASSWORD`, `ADMIN_INITIAL_PASSWORD`, và tùy chọn `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI=https://localhost:8443/api/oauth2/callback`). Xem hướng dẫn chi tiết tại [README.md](README.md) và [Mail Server/docs/oauth2-setup.md](Mail%20Server/docs/oauth2-setup.md).
2. Sinh chứng chỉ TLS tự ký và sao chép chứng chỉ công khai sang Client:
   ```powershell
   cd "Mail Server"
   .\scripts\gen-keystore.ps1
   ```
3. Khởi động stack Docker:
   ```powershell
   docker compose up -d --build
   ```
   Kiểm tra sức khỏe: `curl.exe -k https://localhost:8443/actuator/health` → `{"status":"UP"}`.

### Bước 2 — Chạy Mail Client (Java Swing)
```powershell
cd "Mail Client"
.\gradlew.bat run
```
- Đăng nhập lần đầu bằng tài khoản `admin` với mật khẩu `ADMIN_INITIAL_PASSWORD` (hệ thống sẽ yêu cầu đổi mật khẩu ngay) hoặc đăng ký tài khoản người dùng mới.

### Bước 3 — Chạy kiểm thử tự động
```powershell
# Mail Server (136 tests: 134 pass, 2 skip RealGmailIntegrationTest)
cd "Mail Server"
.\gradlew.bat clean test --rerun-tasks

# Mail Client (56 tests; đặt MAILAPP_E2E=1 nếu Mail Server đang chạy ở localhost:8443)
cd "..\Mail Client"
$env:MAILAPP_E2E = "1"
.\gradlew.bat clean test --rerun-tasks
```

