# Java Mail App — Danh sách Endpoint & Phân công 6 thành viên

> Tài liệu tiếp theo của: `01-thiet-ke-tong-the.md` (Thiết kế hệ thống v2, 2026-10-23)
> Phiên bản: **v1 — 2026-10-23** | Backend: Spring Boot + MySQL 8 | Client: Java Swing thuần
> Kết quả: **27 endpoint** chia thành 8 nhóm, **15 module** chia cho **6 thành viên — mỗi thành viên phụ trách từ 2 module trở lên**.

---

# PHẦN A — DANH SÁCH ENDPOINT

## A0. Quy ước chung (mọi thành viên phải tuân thủ)

| Hạng mục | Quy ước |
|---|---|
| Base URL | `https://localhost:8443` — mọi endpoint có tiền tố `/api` |
| Xác thực | Header `Authorization: Bearer <JWT>`, trừ các endpoint ghi "Công khai" / "Google callback" ở cột Quyền |
| Content-Type | `application/json`, riêng gửi mail là `multipart/form-data`, tải đính kèm trả về stream nhị phân |
| Phân trang | Query `page` (bắt đầu từ 0), `size`. Response dạng: `{ "content": [...], "page": 0, "size": 20, "totalElements": 123, "totalPages": 7 }` |
| Lỗi thống nhất | `{ "timestamp": "...", "status": 403, "error": "Forbidden", "message": "...", "code": "REGISTRATION_DISABLED" }` — Client hiển thị theo `message` / `code`, không hiện mã HTTP thô cho user |
| Quyền sở hữu | Mọi endpoint đụng tới Gmail / mail đều phải kiểm tra `gmail_account.user_id = user đang đăng nhập` (trừ nhóm Admin). Sai quyền → `404` hoặc `403 GMAIL_NOT_OWNED` thống nhất trong nhóm |
| Folder Gmail | Giá trị folder là tên IMAP của Gmail: `INBOX`, `[Gmail]/Sent Mail`, `[Gmail]/Drafts`, `[Gmail]/Spam`, `[Gmail]/Trash`, `[Gmail]/All Mail`. Khi folder nằm trong **path** (EP-M3, EP-M4) Client phải URL-encode, ví dụ `[Gmail]%2FSent%20Mail` |
| Fetch trực tiếp | Nhóm Mail (A5) **không đọc DB để lấy mail**: mỗi request mở kết nối IMAP tới Gmail, fetch, đóng kết nối. Không thành viên nào được tự thêm bảng cache mail |

**Các mã lỗi dùng chung:** `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401 — chưa đăng nhập / JWT hết hạn), `ACCOUNT_DISABLED` (403 — tài khoản bị Admin khoá), `FORBIDDEN` (403 — sai role), `NOT_FOUND` (404), `REGISTRATION_DISABLED` (403), `FEATURE_DISABLED` (403 — tính năng bị Admin tắt), `GMAIL_NOT_OWNED` (403), `GMAIL_DUPLICATE` (409), `GMAIL_LIMIT_REACHED` (409), `GMAIL_AUTH_FAILED` (422 — App Password sai / IMAP từ chối), `GMAIL_NEEDS_REAUTH` (422 — OAuth2 refresh token hết hiệu lực), `GMAIL_CONNECTION_ERROR` (502 — không nối được Gmail / timeout), `PAYLOAD_TOO_LARGE` (413 — đính kèm vượt `MAX_ATTACHMENT_SIZE_MB`).

---

## A1. Nhóm Công khai — 1 endpoint

| ID | Method & Path | Quyền | Mô tả | Response chính | Người làm |
|---|---|---|---|---|---|
| EP-P1 | `GET /api/public/config` | Công khai | Trả các cờ an toàn để Client quyết định ẩn / hiện nút trước khi đăng nhập. Client gọi ngay khi mở app | `{ registrationEnabled, oauth2LinkEnabled, appPasswordLinkEnabled, maintenanceMode }` | TV6 (dữ liệu) + TV1 (Client gọi) |

## A2. Nhóm Xác thực (Auth) — 4 endpoints

| ID | Method & Path | Quyền | Mô tả / Request | Response chính | Lỗi chính | Người làm |
|---|---|---|---|---|---|---|
| EP-A1 | `POST /api/auth/register` | Công khai, có điều kiện | Đăng ký tài khoản mới. Body: `{ username, password, fullName, email }`. Backend kiểm tra cờ `ALLOW_REGISTRATION` trước khi tạo, `role=USER`, mật khẩu băm BCrypt. Ghi log `REGISTER` | `201 { id, username, fullName, email, role }` | `403 REGISTRATION_DISABLED`, `409` trùng username, `400 VALIDATION_ERROR` | TV1 |
| EP-A2 | `POST /api/auth/login` | Công khai | Đăng nhập. Body: `{ username, password }`. **Không** phụ thuộc cờ đăng ký. Kiểm tra `enabled`, chế độ bảo trì (chỉ ADMIN qua được). Ghi log `LOGIN` / `LOGIN_FAILED` | `200 { accessToken, tokenType: "Bearer", expiresIn, user: { id, username, fullName, role, mustChangePassword } }` | `401` sai tài khoản / mật khẩu, `403 ACCOUNT_DISABLED`, `403` khi bảo trì | TV1 |
| EP-A3 | `GET /api/auth/me` | Đã đăng nhập | Thông tin user hiện tại từ JWT — Client gọi sau login để biết role và hiện khu Admin nếu là ADMIN | `200 { id, username, fullName, email, role, mustChangePassword, createdAt }` | `401 UNAUTHORIZED` | TV1 |
| EP-A4 | `POST /api/auth/change-password` | Đã đăng nhập | User tự đổi mật khẩu. Body: `{ currentPassword, newPassword }`. **Endpoint bổ sung để khép kín luồng đã có trong thiết kế tổng thể:** Admin đặt lại mật khẩu / tài khoản Admin seed đều đặt `must_change_password=true`, user cần endpoint này để đổi. Đổi xong xoá cờ `must_change_password`, ghi log | `200 { message }` | `400` mật khẩu hiện tại sai, `400 VALIDATION_ERROR` mật khẩu mới yếu / trùng cũ | TV1 |

> Đăng xuất **không** có endpoint: JWT stateless, Client tự xoá token khỏi `SessionStore` là xong.

## A3. Nhóm Liên kết Gmail (App Password & quản lý chung) — 3 endpoints

| ID | Method & Path | Quyền | Mô tả / Request | Response chính | Lỗi chính | Người làm |
|---|---|---|---|---|---|---|
| EP-G1 | `GET /api/gmail-accounts` | Đã đăng nhập | Danh sách Gmail **của chính user**. Không bao giờ trả App Password / refresh token, kể cả đã mã hoá | `200 [ { id, email, displayName, authType, status, createdAt } ]` | `401` | TV2 |
| EP-G2 | `POST /api/gmail-accounts/app-password` | Đã đăng nhập | Liên kết Gmail bằng App Password. Body: `{ email, appPassword }`. Backend lần lượt kiểm tra: cờ `ALLOW_APP_PASSWORD_LINK` đang bật → chưa trùng Gmail này → chưa vượt `MAX_GMAIL_PER_USER` → **thử kết nối IMAP thật** → đúng mới mã hoá AES và lưu, `authType=APP_PASSWORD`. Ghi log `LINK_GMAIL` | `201 { id, email, authType, status }` | `403 FEATURE_DISABLED`, `409 GMAIL_DUPLICATE`, `409 GMAIL_LIMIT_REACHED`, `422 GMAIL_AUTH_FAILED`, `502 GMAIL_CONNECTION_ERROR` | TV2 |
| EP-G3 | `DELETE /api/gmail-accounts/{id}` | Đã đăng nhập, chủ sở hữu | Xoá liên kết Gmail: xoá credential khỏi DB; nếu `authType=OAUTH2` thì gọi Google revoke token trước (phần revoke do TV3 cung cấp service). Không xoá mail nào trên Gmail. Ghi log `UNLINK_GMAIL` | `204 No Content` | `404` không tồn tại / không phải của mình | TV2 (revoke: TV3 hỗ trợ) |

## A4. Nhóm OAuth2 — 2 endpoints

| ID | Method & Path | Quyền | Mô tả / Request | Response chính | Lỗi chính | Người làm |
|---|---|---|---|---|---|---|
| EP-O1 | `GET /api/oauth2/authorize-url` | Đã đăng nhập | Tạo URL uỷ quyền Google. Backend kiểm tra cờ `ALLOW_OAUTH2_LINK`, kiểm tra giới hạn số Gmail, sinh `state` gắn với user + thời hạn ngắn, lưu tạm phía server. Client nhận URL rồi mở bằng `Desktop.getDesktop().browse(...)` | `200 { authorizeUrl }` | `403 FEATURE_DISABLED`, `409 GMAIL_LIMIT_REACHED` | TV3 |
| EP-O2 | `GET /api/oauth2/callback?code=&state=` | Google gọi về (không JWT, kiểm tra bằng `state`) | Đổi `code` → `access_token` + `refresh_token`, gọi Google lấy email của Gmail vừa uỷ quyền, kiểm tra trùng, chỉ lưu `refresh_token` đã mã hoá AES, `authType=OAUTH2`. Trả về trang HTML tĩnh đơn giản "Liên kết thành công, hãy quay lại ứng dụng" (trang này là trang web của Backend, không phải màn hình Swing) | `200 text/html` | `state` sai / hết hạn → trang HTML báo lỗi; trùng Gmail → trang báo đã liên kết | TV3 |

## A5. Nhóm Mail — fetch trực tiếp từ Gmail — 5 endpoints

| ID | Method & Path | Quyền | Mô tả / Request | Response chính | Lỗi chính | Người làm |
|---|---|---|---|---|---|---|
| EP-M1 | `GET /api/mails/folders?gmailAccountId={id}` | Đã đăng nhập, chủ Gmail | Danh sách folder của Gmail đó: mở IMAP, liệt kê folder, kèm tổng số mail và số chưa đọc nếu lấy được trong cùng kết nối | `200 [ { name, total, unread } ]` | `403 GMAIL_NOT_OWNED`, `422 GMAIL_NEEDS_REAUTH`, `502 GMAIL_CONNECTION_ERROR` | TV4 |
| EP-M2 | `GET /api/mails?gmailAccountId=&folder=INBOX&page=0&size=20&q=` | Đã đăng nhập, chủ Gmail | Danh sách mail của một folder, **mới nhất trước**. Chỉ fetch đúng trang bằng `FetchProfile` (ENVELOPE, FLAGS, CONTENT_INFO), không tải toàn bộ nội dung. Tham số `q` (tuỳ chọn): tìm theo từ khoá bằng IMAP `Folder.search(...)` ngay tại Gmail | `200 { content: [ { uid, from, subject, date, isRead, hasAttachment } ], page, size, totalElements, totalPages }` | Như EP-M1, thêm `400` folder không tồn tại | TV4 |
| EP-M3 | `GET /api/mails/{gmailAccountId}/{folder}/{uid}` | Đã đăng nhập, chủ Gmail | Chi tiết một mail: mở IMAP theo UID, parse `from, to, cc, subject, date, bodyText, bodyHtml`, danh sách đính kèm **chỉ metadata** `{ index, fileName, contentType, sizeBytes }` — không gửi nội dung file ở endpoint này. Mở folder `READ_WRITE` để đặt cờ `\Seen` trên Gmail | `200 { uid, from, to, cc, subject, date, bodyText, bodyHtml, attachments: [...] }` | Như EP-M1, thêm `404` không thấy UID | TV4 |
| EP-M4 | `GET /api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download` | Đã đăng nhập, chủ Gmail | Tải một file đính kèm: mở IMAP, lấy đúng `MimeBodyPart` thứ `index`, giải mã Base64, kiểm tra `MAX_ATTACHMENT_SIZE_MB`, stream về Client kèm header `Content-Disposition: attachment; filename="..."` và đúng `Content-Type` của file | `200 application/octet-stream` (hoặc content type thật) + body là byte của file | Như EP-M3, thêm `404` không có đính kèm thứ `index`, `413 PAYLOAD_TOO_LARGE` | TV5 |
| EP-M5 | `POST /api/mails/send` | Đã đăng nhập, chủ Gmail | Gửi mail qua SMTP. `multipart/form-data`: `gmailAccountId, to, cc, bcc, subject, body, isHtml, files[]`. Backend kiểm tra: cờ `ALLOW_SEND_MAIL` → Gmail thuộc về user → từng file không vượt `MAX_ATTACHMENT_SIZE_MB` → dựng `MimeMessage` (đính kèm encode Base64 theo MIME) → `Transport.send()` qua STARTTLS 587, xác thực theo loại liên kết (App Password / XOAUTH2). Ghi log `SEND_MAIL` chỉ metadata | `200 { message: "Sent", sentAt }` | `403 FEATURE_DISABLED`, `403 GMAIL_NOT_OWNED`, `413 PAYLOAD_TOO_LARGE`, `422 GMAIL_NEEDS_REAUTH`, `502` SMTP lỗi | TV5 |

## A6. Nhóm Admin — Quản lý người dùng — 8 endpoints

Tất cả endpoint nhóm A6 + A7 + A8 đều yêu cầu **JWT + role ADMIN**; user thường gọi vào trả `403 FORBIDDEN`.

| ID | Method & Path | Mô tả / Request | Response chính | Lỗi / ràng buộc chính | Người làm |
|---|---|---|---|---|---|
| EP-AD1 | `POST /api/admin/users` | Admin tạo tài khoản hộ. Body như đăng ký + `role` tuỳ chọn. **Không bị chặn bởi `ALLOW_REGISTRATION`** — dùng khi đăng ký công khai đang đóng mà vẫn cần cấp tài khoản. Ghi log `ADMIN_CREATE_USER` | `201 { id, username, fullName, email, role, enabled }` | `409` trùng username | TV6 |
| EP-AD2 | `GET /api/admin/users?q=&role=&enabled=&page=&size=` | Danh sách user, tìm theo username / email / họ tên, lọc role / trạng thái, phân trang | `200 { content: [ { id, username, fullName, email, role, enabled, gmailCount, createdAt } ], ... }` | — | TV6 |
| EP-AD3 | `GET /api/admin/users/{id}` | Chi tiết một user, kèm danh sách địa chỉ Gmail đã liên kết (chỉ email + `authType` + trạng thái, **không** credential, **không** nội dung mail) | `200 { ..., gmailAccounts: [...] }` | `404` | TV6 |
| EP-AD4 | `PUT /api/admin/users/{id}/lock` | Khoá tài khoản: `enabled=false`. User bị khoá không đăng nhập được, JWT cũ bị chặn ở request tiếp theo. Ghi log `ADMIN_LOCK_USER` | `200 { id, enabled: false }` | `400` tự khoá chính mình / khoá Admin cuối cùng | TV6 |
| EP-AD5 | `PUT /api/admin/users/{id}/unlock` | Mở khoá tài khoản: `enabled=true` | `200 { id, enabled: true }` | `404` | TV6 |
| EP-AD6 | `PUT /api/admin/users/{id}/role` | Đổi role. Body: `{ role: "ADMIN" \| "USER" }` | `200 { id, role }` | `400` tự hạ role chính mình / hạ Admin cuối cùng | TV6 |
| EP-AD7 | `PUT /api/admin/users/{id}/reset-password` | Đặt lại mật khẩu. Body: `{ newPassword }` (băm BCrypt) và đặt `must_change_password=true` để user đổi lại khi đăng nhập (dùng EP-A4) | `200 { message }` | `400 VALIDATION_ERROR`, `404` | TV6 |
| EP-AD8 | `DELETE /api/admin/users/{id}` | Xoá user + toàn bộ liên kết Gmail của user (OAuth2 thì revoke token trước — dùng service của TV3). Client phải có hộp xác nhận trước khi gọi | `204 No Content` | `400` tự xoá chính mình / xoá Admin cuối cùng | TV6 |

## A7. Nhóm Admin — Cấu hình hệ thống — 2 endpoints

| ID | Method & Path | Quyền | Mô tả / Request | Response chính | Lỗi chính | Người làm |
|---|---|---|---|---|---|---|
| EP-S1 | `GET /api/admin/settings` | ADMIN | Toàn bộ cấu hình trong `system_settings`: `ALLOW_REGISTRATION, ALLOW_OAUTH2_LINK, ALLOW_APP_PASSWORD_LINK, ALLOW_SEND_MAIL, MAINTENANCE_MODE, MAX_GMAIL_PER_USER, MAX_ATTACHMENT_SIZE_MB, MAIL_PAGE_SIZE, LOGIN_MAX_FAILED_ATTEMPTS` | `200 [ { key, value, description, updatedBy, updatedAt } ]` | `403 FORBIDDEN` | TV6 |
| EP-S2 | `PUT /api/admin/settings` | ADMIN | Cập nhật nhiều khoá cùng lúc. Body: `{ settings: { "ALLOW_REGISTRATION": "false", ... } }`. Validate: giá trị boolean / số hợp lệ; **không cho tắt cả `ALLOW_OAUTH2_LINK` và `ALLOW_APP_PASSWORD_LINK` cùng lúc**. Ghi log `ADMIN_CHANGE_SETTING` cho từng khoá đổi, kèm cũ → mới. Có hiệu lực ngay, không restart | `200` danh sách cấu hình sau khi lưu | `400 VALIDATION_ERROR` | TV6 |

## A8. Nhóm Admin — Thống kê & Nhật ký — 2 endpoints

| ID | Method & Path | Quyền | Mô tả | Response chính | Người làm |
|---|---|---|---|---|---|
| EP-ST1 | `GET /api/admin/stats` | ADMIN | Tổng quan hệ thống: tổng user, user đang hoạt động / bị khoá, số Admin, tổng Gmail đã liên kết, số Gmail theo `authType`, lượt đăng ký hôm nay / 7 ngày gần nhất | `200 { totalUsers, activeUsers, lockedUsers, totalAdmins, totalGmailAccounts, gmailByAppPassword, gmailByOAuth2, registrationsToday, registrationsLast7Days }` | TV6 |
| EP-ST2 | `GET /api/admin/logs?userId=&action=&from=&to=&page=&size=` | ADMIN | Nhật ký hệ thống từ `audit_logs`, lọc theo user / hành động / khoảng ngày, mới nhất trước. Log không chứa nội dung mail, mật khẩu, token | `200 { content: [ { id, userId, username, action, detail, ipAddress, createdAt } ], ... }` | TV6 |

## A9. Bảng tổng kết endpoint

| Nhóm | Số endpoint | ID | Người phụ trách chính |
|---|---|---|---|
| A1 Công khai | 1 | EP-P1 | TV6 + TV1 |
| A2 Xác thực | 4 | EP-A1 → A4 | TV1 |
| A3 Liên kết Gmail | 3 | EP-G1 → G3 | TV2 |
| A4 OAuth2 | 2 | EP-O1 → O2 | TV3 |
| A5 Mail | 5 | EP-M1 → M5 | TV4 (M1–M3), TV5 (M4–M5) |
| A6 Admin — User | 8 | EP-AD1 → AD8 | TV6 |
| A7 Admin — Cấu hình | 2 | EP-S1 → S2 | TV6 |
| A8 Admin — Thống kê & Nhật ký | 2 | EP-ST1 → ST2 | TV6 |
| **Tổng** | **27** | | |

---

# PHẦN B — DANH SÁCH MODULE

Chia hệ thống thành **15 module**. "Module" ở đây là một khối chức năng hoàn chỉnh (gồm cả Backend và / hoặc Client như ghi rõ), không phải một endpoint lẻ — vì vậy mỗi thành viên giữ nhiều module là giữ trọn nhiều chức năng liên quan, có sản phẩm bàn giao rõ ràng.

| Mã | Module | Thuộc phía | Nội dung chính | Endpoint liên quan |
|---|---|---|---|---|
| M01 | **Hạ tầng Backend & Bảo mật nền tảng** | Backend | Dựng project, cấu hình MySQL / JPA, HTTPS keystore PKCS12, Spring Security + `JwtFilter` / `JwtProvider`, `AesCipher` (AES-256-GCM), xử lý lỗi thống nhất + mã lỗi, `AuditLogService`, seed Admin + seed `system_settings`, interface `SystemSettingService` | Nền cho tất cả |
| M02 | **Xác thực tài khoản hệ thống** | Backend | Register (kiểm tra cờ đăng ký), Login, Me, Change-password, BCrypt | EP-A1 → A4 |
| M03 | **Client nền tảng & Màn hình vào hệ thống** | Client Swing | `Main.java` + System Look & Feel, `ApiClient` (HttpClient + TLS + gắn JWT + parse lỗi), `SessionStore`, khung `MainFrame`, `LoginFrame`, `RegisterFrame` (ẩn nút đăng ký theo EP-P1) | EP-P1, EP-A1 → A3 |
| M04 | **Liên kết Gmail bằng App Password** | Backend | Thêm / liệt kê / xoá Gmail, test kết nối IMAP khi liên kết, mã hoá & lưu App Password, kiểm tra trùng + giới hạn số Gmail | EP-G1, EP-G2, EP-G3 |
| M05 | **Màn hình Quản lý Gmail** | Client Swing | `GmailAccountDialog`: bảng Gmail đã liên kết, form nhập App Password, nút mở luồng OAuth2 (gọi EP-O1 + mở browser), xoá liên kết có xác nhận. Chỉ hiện cách liên kết đang được Admin bật | EP-G1 → G3, EP-O1 |
| M06 | **Liên kết Gmail bằng OAuth2** | Backend | Google Cloud config, authorize-url + `state`, callback đổi code → token, refresh access token, revoke khi xoá liên kết, trạng thái `NEEDS_REAUTH` | EP-O1, EP-O2 (+ hỗ trợ EP-G3, EP-AD8) |
| M07 | **MailConnectionFactory — kết nối Gmail dùng chung** | Backend | Điểm duy nhất tạo `Session` / IMAP `Store` / SMTP `Transport` theo `authType`: App Password (password auth) và OAuth2 (`XOAUTH2` + access token mới), cấu hình TLS (IMAPS 993, STARTTLS 587), timeout thống nhất | Dùng bởi M04, M06, M08, M10, M12 |
| M08 | **Nhận mail — IMAP Fetch** | Backend | Folders, danh sách mail phân trang bằng `FetchProfile`, chi tiết mail + parse MIME (text / html / metadata đính kèm), đặt cờ `\Seen`, tìm kiếm bằng IMAP search | EP-M1, EP-M2, EP-M3 |
| M09 | **Màn hình Hộp thư & Chi tiết mail** | Client Swing | Vùng hộp thư trong `MainFrame`: cây Gmail + folder, `JTable` mail (chưa đọc in đậm, icon đính kèm), phân trang, nút Làm mới, `MailDetailPanel` (`JEditorPane` / `JTextArea`). Mọi lời gọi mạng qua `SwingWorker` + thanh trạng thái | EP-M1 → M3, EP-G1 |
| M10 | **Gửi mail — SMTP** | Backend | Dựng `MimeMessage`, gửi qua SMTP STARTTLS, đính kèm encode Base64, kiểm tra cờ gửi mail + kích thước file, ghi log gửi | EP-M5 |
| M11 | **Màn hình Soạn mail** | Client Swing | `ComposeDialog`: chọn From trong các Gmail đã liên kết, To / Cc / Bcc, Subject, Body, chọn nhiều file bằng `JFileChooser`, kiểm tra sơ bộ kích thước file phía Client, gửi qua API | EP-M5 |
| M12 | **File đính kèm — Tải về & Mở file** | Backend + Client | Backend: trích đúng `MimeBodyPart`, giải mã Base64, stream download. Client: `FileUtil` lưu file vào `%USERPROFILE%\Downloads\MailApp\...`, chống trùng tên, tự mở bằng `Desktop.open()`, đã tải rồi thì mở file local | EP-M4 |
| M13 | **Admin — Quản lý người dùng** | Backend | Tạo tài khoản hộ, danh sách / chi tiết user, khoá / mở khoá, đổi role, đặt lại mật khẩu, xoá tài khoản — kèm các ràng buộc bảo vệ Admin cuối cùng / chính mình | EP-AD1 → AD8 |
| M14 | **Admin — Cấu hình, Thống kê & Nhật ký** | Backend | Đọc / ghi `system_settings` + validate, `public/config` cho Client chưa đăng nhập, thống kê tổng quan, truy vấn audit log | EP-P1, EP-S1, EP-S2, EP-ST1, EP-ST2 |
| M15 | **Màn hình Admin** | Client Swing | `AdminFrame` / tab Admin với `JTabbedPane` 4 tab: Người dùng (bảng, tìm / lọc, các nút thao tác, hộp xác nhận), Cấu hình hệ thống (`JCheckBox` cho cờ — công tắc đăng ký đặt đầu tab, kèm số user hiện tại — và `JSpinner` / ô số cho tham số), Thống kê, Nhật ký | Toàn bộ nhóm Admin + EP-P1 |

---

# PHẦN C — PHÂN CÔNG 6 THÀNH VIÊN

## C1. Bảng phân công tổng

Mỗi thành viên phụ trách **ít nhất 2 module** (thực tế 2–3 module), theo lát cắt chức năng trọn vẹn để tự demo được phần của mình.

| Thành viên | Vai trò gợi ý | Module phụ trách | Số module | Endpoint chính |
|---|---|---|---|---|
| **TV1** | Trưởng nhóm — Nền tảng & Xác thực | **M01** Hạ tầng Backend & Bảo mật, **M02** Xác thực Backend, **M03** Client nền tảng + Login / Register | 3 | EP-A1 → A4, EP-P1 (phía Client) |
| **TV2** | Liên kết Gmail — App Password | **M04** Gmail App Password Backend, **M05** Màn hình Quản lý Gmail | 2 | EP-G1 → G3 |
| **TV3** | OAuth2 & Kết nối Gmail | **M06** OAuth2 Backend, **M07** MailConnectionFactory | 2 | EP-O1, EP-O2 |
| **TV4** | Nhận mail | **M08** IMAP Fetch Backend, **M09** Màn hình Hộp thư & Chi tiết mail | 2 | EP-M1 → M3 |
| **TV5** | Gửi mail & Đính kèm | **M10** SMTP Backend, **M11** Màn hình Soạn mail, **M12** Đính kèm — Tải & Mở file | 3 | EP-M5, EP-M4 |
| **TV6** | Quản trị hệ thống (Admin) | **M13** Admin User Backend, **M14** Admin Cấu hình / Thống kê / Nhật ký Backend, **M15** Màn hình Admin | 3 | EP-AD1 → AD8, EP-S1, EP-S2, EP-ST1, EP-ST2, EP-P1 (phía Backend) |

> Ghi chú cân bằng: TV2 / TV3 / TV4 giữ 2 module nhưng đều là module **lớn và khó** (kết nối Gmail thật, parse MIME, OAuth2). TV1 / TV5 / TV6 giữ 3 module nhưng trong đó có module vừa / nhỏ (M03, M11) hoặc các module Admin tuy nhiều endpoint nhưng cùng một mẫu CRUD. Không thành viên nào chỉ làm giao diện hoặc chỉ làm một endpoint lẻ.

## C2. Chi tiết công việc, sản phẩm bàn giao & tiêu chí xong

### TV1 — Nền tảng & Xác thực (M01, M02, M03)

- **Việc:** dựng skeleton 2 project theo cấu trúc ở thiết kế tổng thể mục 2; làm xong M01 trước để cả nhóm có code nền; cài JWT + AES + bắt lỗi thống nhất; seed Admin & settings; viết `ApiClient` cho Client; làm Login / Register chạy thật qua HTTPS.
- **Bàn giao:** 2 project chạy được; file cấu hình mẫu (`.env.example` / `application.yml` không chứa bí mật thật); tài khoản Admin seed; bộ sưu tập Postman / file `.http` cho nhóm Auth.
- **Xong khi:** đăng ký → đăng nhập → gọi `EP-A3` bằng JWT thành công trên cả Postman và Swing; tắt cờ đăng ký thì `EP-A1` trả `403 REGISTRATION_DISABLED` còn đăng nhập vẫn bình thường; user bị khoá thì đăng nhập trả `403 ACCOUNT_DISABLED`.
- **Phụ thuộc:** không phụ thuộc ai — **phải xong đầu tiên**. Ngược lại, mọi thành viên đều phụ thuộc TV1.

### TV2 — Liên kết Gmail bằng App Password (M04, M05)

- **Việc:** API liên kết Gmail bằng App Password (test IMAP thật trước khi lưu), danh sách / xoá liên kết; dialog Quản lý Gmail hoàn chỉnh, trong đó nút "Liên kết bằng Google" chỉ việc gọi EP-O1 và mở browser (logic phía Google do TV3 làm).
- **Bàn giao:** API nhóm G chạy thật với một Gmail test đã bật 2FA; dialog thêm / xoá / xem danh sách Gmail.
- **Xong khi:** nhập sai App Password → `422 GMAIL_AUTH_FAILED` và không lưu gì; nhập đúng → Gmail xuất hiện ở EP-G1 và dùng được cho TV4 fetch thử; liên kết trùng → `409`; vượt giới hạn → `409 GMAIL_LIMIT_REACHED`.
- **Phụ thuộc:** M01 / M02 của TV1 (JWT, AES, settings). Hỗ trợ TV3: cùng thống nhất cấu trúc bản ghi `gmail_accounts`.

### TV3 — OAuth2 & MailConnectionFactory (M06, M07)

- **Việc:** đăng ký Google Cloud Project (test users cho các Gmail demo), làm authorize-url / callback / refresh / revoke; xây `MailConnectionFactory` làm **hợp đồng dùng chung**: TV2, TV4, TV5 chỉ gọi factory để lấy `Store` / `Transport`, không tự viết code đăng nhập Gmail riêng. Chốt sớm interface của factory ngay đầu giai đoạn 2 để nhóm không chờ nhau.
- **Bàn giao:** luồng OAuth2 chạy end-to-end (bấm ở Swing → browser → callback → Gmail hiện trong danh sách); `MailConnectionFactory` + tài liệu ngắn hướng dẫn 3 nhóm còn lại cách dùng.
- **Xong khi:** liên kết OAuth2 thành công và fetch / gửi thử được bằng Gmail đó; thu hồi quyền trên Google → lần dùng tiếp theo trả `422 GMAIL_NEEDS_REAUTH` và tài khoản chuyển trạng thái `NEEDS_REAUTH`; xoá liên kết OAuth2 có gọi revoke.
- **Phụ thuộc:** M01 của TV1; cấu trúc `gmail_accounts` thống nhất với TV2.

### TV4 — Nhận mail (M08, M09)

- **Việc:** 3 endpoint fetch trực tiếp; parse MIME cẩn thận (mail chỉ text, chỉ html, cả hai, có / không đính kèm); giao diện hộp thư hoàn chỉnh với `SwingWorker`, phân trang, làm mới, trạng thái đã đọc lấy từ cờ `\Seen`.
- **Bàn giao:** API nhóm M1–M3 + màn hình hộp thư dùng được hằng ngày bằng Gmail test.
- **Xong khi:** mở INBOX thấy đúng các mail mới nhất trước, số lượng khớp Gmail web; mở chi tiết thấy đúng nội dung và danh sách đính kèm (chưa cần tải); đọc xong quay lại danh sách thì mail hết in đậm; đổi folder / đổi Gmail fetch lại đúng, không treo giao diện khi mạng chậm.
- **Phụ thuộc:** TV1 (nền), TV2 (có Gmail App Password để test sớm — không cần chờ OAuth2), TV3 (factory: giai đoạn đầu dùng bản App Password trước, bản XOAUTH2 bổ sung sau mà không sửa API).

### TV5 — Gửi mail & File đính kèm (M10, M11, M12)

- **Việc:** endpoint gửi mail multipart; endpoint tải đính kèm (tái sử dụng cách tìm message theo UID và parse `MimeBodyPart` thống nhất với TV4 — thống nhất một hàm dùng chung, không viết hai bản parse khác nhau); `ComposeDialog`; phía Client của việc tải file + `Desktop.open()`.
- **Bàn giao:** gửi được mail có / không đính kèm từ app và người nhận nhận đúng; bấm vào đính kèm trong chi tiết mail (màn hình của TV4) → file tải về đúng thư mục và tự mở.
- **Xong khi:** gửi mail từ Gmail App Password **và** Gmail OAuth2 đều thành công; file vượt giới hạn → `413` ở cả gửi lẫn tải; Admin tắt `ALLOW_SEND_MAIL` → gửi trả `403 FEATURE_DISABLED` và nút Soạn / Gửi phía Client bị vô hiệu; tải trùng tên file không ghi đè file cũ.
- **Phụ thuộc:** TV1 (nền, settings), TV2 / TV3 (Gmail + factory để gửi), TV4 (chi tiết mail có danh sách đính kèm để bấm tải).

### TV6 — Admin (M13, M14, M15)

- **Việc:** toàn bộ Backend Admin + màn hình Admin 4 tab; là người **sở hữu dữ liệu `system_settings`** — các cờ TV1 / TV2 / TV3 / TV5 kiểm tra (đăng ký, hai cách liên kết, gửi mail) đều đọc qua `SystemSettingService` / EP-P1 mà TV6 + TV1 thống nhất định dạng ngay giai đoạn 1 (TV1 dựng interface + dữ liệu seed, TV6 hoàn thiện logic ghi + validate + giao diện).
- **Bàn giao:** API nhóm Admin + EP-P1; màn hình Admin dùng được trọn vẹn bằng tài khoản Admin seed của TV1.
- **Xong khi:** khoá một user → user đó đăng nhập bị chặn ngay; tắt đăng ký → Client mất nút Đăng ký, `EP-A1` trả 403, nhưng Admin vẫn tạo được tài khoản hộ bằng EP-AD1 và user cũ đăng nhập bình thường; không tắt được cả hai cách liên kết Gmail cùng lúc; mọi thao tác Admin đều có mặt trong EP-ST2.
- **Phụ thuộc:** TV1 (Auth, seed, interface settings) — nhưng **không phải chờ TV2–TV5**: phần lớn Admin làm độc lập trên bảng `users` / `system_settings` / `audit_logs` và bắt đầu được ngay sau giai đoạn 1.

## C3. Quan hệ phụ thuộc & thứ tự làm (để không ai chờ ai)

```
Giai đoạn 1 — Nền tảng:
  TV1: M01 + M02 + M03  ── xong sớm nhất, cả nhóm dựa vào đây
  TV6: thống nhất định dạng settings với TV1, bắt đầu M13 / M14 trên dữ liệu seed

Giai đoạn 2 — Có bản chạy được đầu tiên (làm song song):
  TV2: M04 + M05  (App Password — cách liên kết đơn giản, làm trước)
  TV3: chốt interface M07 (bản App Password) -> TV4 / TV5 dùng ngay, không chờ OAuth2
  TV4: M08 + M09  (fetch mail bằng Gmail App Password của TV2)
  TV5: M10 + M11  (gửi mail bằng Gmail App Password)
  TV6: M13 + M14 + M15 (Admin — độc lập với mail)

Giai đoạn 3 — Bổ sung OAuth2 & hoàn thiện:
  TV3: M06 + hoàn thiện M07 (thêm XOAUTH2) -> TV4 / TV5 tự chạy được với Gmail OAuth2
       vì cả hai đã đi qua factory, không phải sửa lại
  TV5: M12 (tải & mở đính kèm — cần M09 của TV4 đã có danh sách đính kèm)
  Cả nhóm: kiểm thử chéo + sửa lỗi

Giai đoạn 4 — Đóng gói:
  Kiểm thử toàn hệ thống, viết báo cáo theo đúng module mình phụ trách,
  TV1 tổng hợp báo cáo + chuẩn bị kịch bản demo
```

**Nguyên tắc tránh giẫm chân nhau:**

1. Mỗi Controller / Service / màn hình Swing chỉ có **một chủ sở hữu** theo bảng C1 — người khác cần sửa thì báo chủ sở hữu, không tự sửa chéo.
2. Ba điểm dùng chung phải chốt interface trước khi code phần chính: `MailConnectionFactory` (TV3 chốt, TV2 / TV4 / TV5 dùng), `SystemSettingService` + định dạng EP-P1 (TV1 + TV6 chốt), cấu trúc DTO lỗi + phân trang (TV1 chốt ở M01).
3. File / thư mục dùng chung ở Client: `MainFrame` do TV1 dựng khung, TV4 làm vùng hộp thư bên trong, TV6 làm khu Admin riêng — đã tách vùng sẵn để merge không xung đột.

## C4. Việc chung cả nhóm (không tính vào module của riêng ai)

| Việc | Cách chia |
|---|---|
| Kiểm thử | Mỗi người tự test module mình bằng Postman + Swing, sau đó **test chéo một vòng**: TV2 ↔ TV4, TV3 ↔ TV5, TV1 ↔ TV6 — người test chéo ghi lỗi vào danh sách chung, chủ module sửa |
| Dữ liệu demo | Cả nhóm dùng chung 2 Gmail test (1 liên kết App Password, 1 liên kết OAuth2, đều khai là test user trên Google Cloud) + tài khoản Admin seed. Không dùng Gmail cá nhân chính để demo |
| Báo cáo | Mỗi thành viên viết phần mô tả module của mình (sơ đồ luồng + endpoint + ảnh màn hình). TV1 tổng hợp, viết phần kiến trúc / bảo mật TLS / Base64 dùng chung |
| Demo khi bảo vệ | Mỗi thành viên demo đúng phần mình làm và trả lời câu hỏi trong phạm vi module của mình; TV1 demo phần mở đầu (đăng ký / đăng nhập / kiến trúc) |

---

# PHẦN D — ƯỚC LƯỢNG THỜI GIAN

**Kết luận: lên kế hoạch 4 tuần để code + test xong bản demo hoàn chỉnh, cộng thêm 1 tuần nếu tính cả báo cáo / slide / tập demo — tổng 5 tuần là an toàn.** Điều kiện: 6 thành viên làm việc thật, mỗi người khoảng 10–15 giờ / tuần, họp nhóm ngắn 1–2 lần / tuần để gỡ điểm nghẽn.

| Tuần | Mục tiêu cuối tuần (phải demo được) | Nội dung chính |
|---|---|---|
| **Tuần 1** | Đăng ký / đăng nhập chạy thật trên Swing qua HTTPS | TV1 xong M01 + M02 + M03; TV6 bắt đầu M13 / M14 trên dữ liệu seed; TV3 đăng ký xong Google Cloud Project + khai test users (làm sớm vì thủ tục chờ đợi, không tốn công code); cả nhóm chốt 3 interface dùng chung ở mục C3 |
| **Tuần 2** | **Bản chạy được v1:** liên kết Gmail bằng App Password → fetch được mail → gửi được mail | TV2: M04 + M05; TV4: M08 + M09; TV5: M10 + M11; TV3: MailConnectionFactory bản App Password; TV6: xong Backend Admin |
| **Tuần 3** | Đủ tính năng theo thiết kế v2 | TV3: M06 OAuth2 + factory bản XOAUTH2; TV5: M12 tải & mở đính kèm; TV6: M15 màn hình Admin; cả nhóm nối các cờ bật / tắt (đăng ký, liên kết, gửi mail) end-to-end |
| **Tuần 4** | Đóng băng tính năng, chỉ sửa lỗi | Test chéo theo mục C4, sửa lỗi, xử lý các trường hợp biên (Gmail sai mật khẩu, token hết hạn, mất mạng, file quá lớn); cuối tuần 4 phải có bản demo ổn định |
| **Tuần 5** *(nếu tính cả nộp bài)* | Sẵn sàng bảo vệ | Viết báo cáo theo module, làm slide, viết kịch bản demo + quay / chuẩn bị phương án dự phòng khi demo mất mạng, tập trả lời câu hỏi |

**Các mốc co / giãn:**

- **Ép còn 3 tuần:** làm được, nhưng phải cắt giảm — gộp tuần 3 + 4, chấp nhận test ít hơn và OAuth2 chỉ demo với Gmail test đã khai sẵn. Rủi ro cao nếu OAuth2 trục trặc.
- **2 tuần:** không nên cam kết đủ cả hai cách liên kết. Chỉ khả thi nếu giữ App Password, hoãn OAuth2 thành "hướng phát triển", và Admin chỉ làm phần user + tắt / mở đăng ký.
- **Kéo dài quá 6 tuần** thường không phải vì code khó thêm, mà vì chờ nhau: TV1 chưa xong nền, interface dùng chung chưa chốt, hoặc để dồn test / báo cáo tới cuối.

**Ba rủi ro ngốn thời gian nhất (chừa sẵn thời gian đệm, đừng hứa sát nút):**

1. **OAuth2 (TV3)** — Google Cloud, redirect URI với HTTPS self-signed, refresh token: dễ mất thêm 3–5 ngày nếu lần đầu làm. Vì vậy luồng App Password phải xong và demo được độc lập từ tuần 2, OAuth2 trục trặc cũng không kéo sập cả nhóm.
2. **Parse MIME khi fetch mail (TV4)** — mail thực tế rất đa dạng (chỉ text, chỉ HTML, đính kèm lồng nhau). Cần test bằng nhiều mail thật ngay từ tuần 2, không để tới tuần test mới phát hiện.
3. **Ghép nối cuối dự án** — kinh nghiệm chung: dành nguyên tuần 4 cho việc này. Nhóm nào code tới sát ngày nộp mới ghép thường trễ vì lỗi tích hợp, không phải vì từng module chưa xong.

---

## Chờ duyệt

Tài liệu này bám đúng thiết kế tổng thể v2: không thêm tính năng mới ngoài một endpoint khép kín luồng đổi mật khẩu (EP-A4, đã ghi rõ lý do ở A2). Sau khi duyệt phần endpoint và phân công, bước tiếp theo là dựng skeleton code theo đúng thứ tự giai đoạn 1 (TV1: M01 → M02 → M03) để cả nhóm có nền bắt đầu song song.
