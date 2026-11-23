# TÀI LIỆU BÀN GIAO KỸ THUẬT (HANDOVER DOCUMENT)

**Người bàn giao:** Lê Thanh Cường — Backend & Desktop Client Engineer  
**Dự án:** MailManager — Đồ án Lập trình mạng PTIT (Nhóm 6 người)  
**Phạm vi hoàn tất:** Toàn bộ xác thực JWT (`HS256`), M06 (Google OAuth2 PKCE + Granular Consent Check), M07 (`MailConnectionFactory` + XOAUTH2 + App Password), API Hộp thư thật (M08, M10, M12, M16, M17, EP-G2, PATCH), Mail Client Swing thuần 3 cột kèm tính năng cốt lõi **Tải tệp đính kèm rồi Xóa thư**, bộ nhận diện thương hiệu Java2D và hạ tầng Docker/TLS.

---

## 1. HƯỚNG DẪN KHỞI CHẠY TỪNG BƯỚC

### 1.1. Môi Trường Yêu Cầu
- **JDK:** OpenJDK 21 LTS / Eclipse Temurin 21.
- **Build tool:** Gradle 8.10 (dùng Gradle Wrapper đi kèm `gradlew.bat` / `./gradlew`).
- **Hệ điều hành:** Windows 10/11, Linux hoặc macOS.
- **Cơ sở dữ liệu:** MySQL 8.0 (Docker Compose) hoặc H2 in-memory tương thích MySQL trong bài test.

### 1.2. Khởi Chạy Bằng Docker Compose (Khuyến nghị)
1. Sao chép `.env.example` thành `.env` trong `Mail Server/`:
   ```powershell
   cd "Mail Server"
   Copy-Item .env.example .env
   ```
2. Điền các giá trị bảo mật vào tệp `.env` (tuyệt đối không commit tệp này lên Git).
3. Dựng và khởi động các container (lưu ý: sau khi sửa `.env` luôn chạy `up -d`, KHÔNG dùng `restart`):
   ```powershell
   docker compose up -d --build
   docker compose ps
   ```
4. Máy chủ lắng nghe tại `https://localhost:8443`, Flyway tự động chạy migration `V1__init.sql` và `V2__gmail_last_verified_at.sql`.

### 1.3. Chạy Profile `dev` Trên Host / IntelliJ IDEA
- Chọn Run Configuration **`MailServerApplication`** (tự bật `SPRING_PROFILES_ACTIVE=dev`, tự nạp `Mail Server/.env`, tự phát hiện và báo lỗi rõ ràng nếu cổng `8443` bị Docker chiếm).
- Hoặc chạy bằng lệnh:
  ```powershell
  cd "Mail Server"
  .\gradlew.bat bootRun --args="--spring.profiles.active=dev"
  ```
- Khởi chạy Mail Client (Pure Swing):
  ```powershell
  cd "Mail Client"
  .\gradlew.bat run
  ```

---

## 2. DANH SÁCH BIẾN MÔI TRƯỜNG CẤU HÌNH

> [!CAUTION] QUY TẮC BẢO MẬT
> Bảng dưới đây **CHỈ CHỨA TÊN VÀ Ý NGHĨA** của biến môi trường, tuyệt đối không lưu giá trị bí mật thực tế.
> Ở profile non-dev (Production), ứng dụng **chặn đứng khởi động** nếu thiếu hoặc yếu các biến bảo mật bắt buộc (`KEYSTORE_PASSWORD`, `JWT_SECRET`, `AES_KEY`, `DB_PASSWORD`, `ADMIN_INITIAL_PASSWORD`).

| Tên Biến Môi Trường (Chuẩn) | Kiểu / Ràng buộc | Bắt buộc (Prod) | Mô tả mục đích sử dụng |
| :--- | :--- | :---: | :--- |
| `SERVER_ADDRESS` | Chuỗi IP | Không | Địa chỉ interface lắng nghe (mặc định `0.0.0.0` cho mạng LAN). |
| `SERVER_PORT` | Số nguyên | Không | Cổng dịch vụ HTTPS (mặc định `8443`). |
| `DB_PASSWORD` | Chuỗi ký tự | **Có** | Mật khẩu tài khoản MySQL (`spring.datasource.password`). |
| `JWT_SECRET` | Chuỗi >= 32 ký tự | **Có** | Khóa HMAC-SHA256 ký JWT token (thuật toán cố định **`HS256`**). |
| `AES_KEY` | Base64 (32 bytes) | **Có** | Khóa đối xứng AES-256-GCM mã hóa token/mật khẩu ứng dụng Gmail trong DB. |
| `KEYSTORE_PASSWORD` | Chuỗi ký tự | **Có** | Mật khẩu mở tệp PKCS12 Keystore (`keystore.p12`). |
| `ADMIN_INITIAL_PASSWORD` | >= 8 ký tự, chữ & số | **Có** | Mật khẩu khởi tạo ban đầu cho `admin` (mặc định chỉ tồn tại trong `application-dev.properties`; không có default ở non-dev). |
| `GOOGLE_CLIENT_ID` | Chuỗi ký tự | **Có** (OAuth2) | OAuth 2.0 Client ID trên Google Auth Platform. |
| `GOOGLE_CLIENT_SECRET` | Chuỗi ký tự | **Có** (OAuth2) | OAuth 2.0 Client Secret trên Google Auth Platform. |
| `GOOGLE_REDIRECT_URI` | URL | Không | Redirect URI callback (mặc định `https://localhost:8443/api/oauth2/callback`). |

---

## 3. BẢNG DANH MỤC ENDPOINTS ĐÃ HOÀN THÀNH

| Mã Endpoint | Giao thức & URL | Xác thực | Trạng thái | Ghi chú nghiệp vụ & Bảo mật |
| :--- | :--- | :---: | :---: | :--- |
| **EP-A1** | `POST /api/auth/register` | Công khai | **HOÀN TẤT** | Bị chặn khi `ALLOW_REGISTRATION=false`; luôn ép role=`USER`; rate limit 5 req/phút/IP. |
| **EP-A2** | `POST /api/auth/login` | Công khai | **HOÀN TẤT** | Cấp JWT ký **`HS256`**; rate limit 5 lần sai/15 phút/IP (`429` + `Retry-After`). |
| **EP-A3** | `GET /api/auth/me` | Bearer JWT | **HOÀN TẤT** | Trả thông tin người dùng hiện tại, không lộ hash mật khẩu. |
| **EP-A4** | `POST /api/auth/change-password` | Bearer JWT | **HOÀN TẤT** | Kiểm tra BCrypt, tắt `mustChangePassword`, cấp JWT mới, ghi audit. |
| **EP-P1** | `GET /api/public/config` | Công khai | **HOÀN TẤT** | Trả cờ UI an toàn (`registrationEnabled`, `oauth2LinkEnabled`, `appPasswordLinkEnabled`, `maintenanceMode`, `allowSendMail`, `maxAttachmentSizeMb`). |
| **EP-O1** | `GET /api/oauth2/authorize-url` | Bearer JWT | **HOÀN TẤT** | Sinh URL Google OAuth2 kèm PKCE `S256` & CSRF `state` (TTL 10 phút). |
| **EP-O2** | `GET /api/oauth2/callback` | Công khai (State) | **HOÀN TẤT** | Kiểm tra trường `scope` chứa `https://mail.google.com/`; thiếu thì từ chối lưu + audit `LINK_GMAIL_DENIED_SCOPE`; trả HTML tiếng Việt nhúng logo SVG. |
| **EP-G1** | `GET /api/gmail-accounts` | Bearer JWT | **HOÀN TẤT** | Trả danh sách tài khoản Gmail của user kèm `lastVerifiedAt` (Flyway V2), che toàn bộ secret. |
| **EP-G2** | `POST /api/gmail-accounts/app-password` | Bearer JWT | **HOÀN TẤT** | Kiểm tra `ALLOW_APP_PASSWORD_LINK`, test IMAP thực trước khi mã hóa AES-256-GCM lưu DB; phân biệt rõ `IMAP_DISABLED` và `TWO_STEP_VERIFICATION_REQUIRED`. |
| **EP-G3** | `DELETE /api/gmail-accounts/{id}` | Bearer JWT | **HOÀN TẤT** | Chống IDOR, thu hồi Google token (nếu OAuth2), xóa DB, ghi audit `UNLINK_GMAIL`. |
| **EP-G4** *(mở rộng)* | `PATCH /api/gmail-accounts/{id}` | Bearer JWT | **HOÀN TẤT** | Cập nhật tên hiển thị (`displayName`) của tài khoản Gmail, kiểm tra sở hữu chống IDOR. |
| **M08-F** | `GET /api/mails/folders?gmailId=` | Bearer JWT | **HOÀN TẤT** | Trả danh sách thư mục chuẩn hóa tên tiếng Việt, `unread`, `total` qua `GmailFolderResolver`. |
| **M08-L** | `GET /api/mails?gmailId=&folder=...` | Bearer JWT | **HOÀN TẤT** | Phân trang mới nhất trước; chỉ dùng `FetchProfile` (ENVELOPE/FLAGS/CONTENT_INFO), hỗ trợ tìm kiếm `X-GM-RAW` + fallback `SearchTerm`. |
| **M08-D** | `GET /api/mails/{g}/{f}/{uid}?loadRemote=false` | Bearer JWT | **HOÀN TẤT** | Chi tiết MIME (cắt gọn an toàn >2 MB), làm sạch HTML bằng Jsoup Safelist, chặn ảnh từ xa mặc định (giữ `cid:`), kiểm tra `uidValidity` (`409 UID_VALIDITY_CHANGED`). **Không lưu nội dung vào DB.** |
| **M12** | `GET /api/mails/{g}/{f}/{uid}/attachments/{idx}/download` | Bearer JWT | **HOÀN TẤT** | Stream từng chunk 8 KB, `Content-Disposition` chuẩn RFC 5987, `X-Content-Type-Options: nosniff`. |
| **M10** | `POST /api/mails/send` | Bearer JWT | **HOÀN TẤT** | Multipart JSON + đính kèm; kiểm tra `ALLOW_SEND_MAIL`, `MAX_ATTACHMENT_SIZE_MB`, chống CRLF injection, giới hạn 50 người nhận, rate limit 20 thư/giờ/user, ghi audit `SEND_MAIL` không lưu nội dung. |
| **M16** | `DELETE /api/mails/{g}/{f}/{uid}?permanent=false` | Bearer JWT | **HOÀN TẤT** | Xóa mềm sang Thùng rác; `permanent=true` chỉ được phép khi thư đã nằm trong Thùng rác; ghi audit `DELETE_MAIL`. |
| **M17** | `PUT /api/mails/{g}/{f}/{uid}/flags` | Bearer JWT | **HOÀN TẤT** | Cập nhật cờ `\Seen` và `\Flagged`. |
| **EP-AD1..AD8** | `/api/admin/users/**` (`POST`, `GET`, `PUT lock/unlock/role/reset-password`, `DELETE`) | Bearer JWT (`ADMIN`) | **HOÀN TẤT** | Quản lý người dùng đầy đủ: chống tự khóa/hạ quyền/xóa (`400`), chống tác động ADMIN cuối cùng (`409 LAST_ADMIN`), khóa user vô hiệu JWT tức thì, mật khẩu tạm một lần (`no-store`), xóa cascade Gmail + revoke OAuth2 best-effort. Xem [`docs/api-admin-contract.md`](api-admin-contract.md). |
| **EP-S1 / S2** | `GET / PUT /api/admin/settings` | Bearer JWT (`ADMIN`) | **HOÀN TẤT** | Quản lý 9 khóa cấu hình, validate kiểu & khoảng, chặn tắt cả OAuth2 & App Password (`400`), lưu `updated_by`, xóa cache tức thì, ghi audit cũ → mới. |
| **EP-ST1 / ST2** | `GET /api/admin/stats`, `GET /api/admin/logs`, `GET /api/admin/logs/export` | Bearer JWT (`ADMIN`) | **HOÀN TẤT** | Thống kê hệ thống (gồm lượng mail gửi 7 ngày theo ngày), lọc & phân trang audit log, xuất CSV chặn CSV Injection (`=`, `+`, `-`, `@`). |
| **HEALTH** | `GET /actuator/health`, `GET /api/public/health` | Công khai | **HOÀN TẤT** | Ẩn chi tiết nhạy cảm (`show-details=never`). |

---

## 4. TỔNG HỢP KIỂM THỬ TỰ ĐỘNG

- **Mail Server**: **136 test** trên 29 test class (**134 pass**, **2 skipped** `RealGmailIntegrationTest`, **0 fail**).
- **Mail Client**: **56 test** trên 11 test class (**46 pass / 10 skipped** khi chạy offline mặc định; **56 pass / 0 skipped** khi bật `MAILAPP_E2E=1` với Docker Server sống; **0 fail**).
- **Tổng toàn dự án**: **192 test** (**180 pass / 12 skipped** offline; **190 pass / 2 skipped** với `MAILAPP_E2E=1`, **0 fail**).
- Xem chi tiết ánh xạ từng yêu cầu tại [`docs/testing/test-matrix.md`](testing/test-matrix.md), hợp đồng API hộp thư tại [`docs/api-mail-contract.md`](api-mail-contract.md) và hợp đồng API Admin tại [`docs/api-admin-contract.md`](api-admin-contract.md).
