# Java Mail App — Chi tiết công việc theo từng task

> Mỗi task gồm: mục tiêu, việc cụ thể, liên kết API, acceptance criteria.
> Backlog Details trên Lark chứa mô tả kỹ thuật tương ứng.

---


# [US-01] Người dùng đăng ký, đăng nhập và vào được client

_M01 Hạ tầng Backend & Bảo mật, M02 Xác thực, M03 Client nền tảng + Login/Register. Xong đầu tiên; cả nhóm phụ thuộc._


## [M01] Hạ tầng Backend & Bảo mật nền tảng
- **Owner:** TV1
- **Platform:** API
- **Sprint:** SPRINT 1 | **Week:** Week 1
- **Estimate:** 10h
- **Kế hoạch:** 26/10 → 28/10

**Mô tả:** Dựng project Spring Boot + MySQL/JPA, HTTPS keystore PKCS12, Spring Security + JWT filter/provider, AesCipher AES-256-GCM, xử lý lỗi thống nhất + mã lỗi, AuditLogService, seed Admin + system_settings, interface SystemSettingService.

**Endpoints:**

**Sub-tasks:**
- `[BE] AesCipher AES-256-GCM + load khoá từ env MAILAPP_AES_KEY` — TV1 | 1.5h | Week 1
  - encrypt/decrypt String; khoá 32 byte base64; test roundtrip; không commit khoá.
- `[BE] Audit log service` — TV1 | 1h | Week 1
  - API nội bộ AuditLogService.log(userId, action, detail, ip); entity AuditLog; mọi module ghi metadata ngắn, không chứa bí mật.
- `[BE] JWT provider + security filter` — TV1 | 2h | Week 1
  - JwtProvider sinh/verify HS256 hạn 24h; JwtFilter gắn chuỗi; SecurityConfig: public /api/auth/* + /api/public/config + /api/oauth2/callback, /api/admin/** chỉ ADMIN.
- `[BE] Khởi tạo project Spring Boot` — TV1 | 1.5h | Week 1
  - Tạo project theo cấu trúc thiết kế (config/controller/service/repository/model/dto/security); cấu hình datasource MySQL, JPA, port 8443, logging.
- `[BE] Schema MySQL + seed Admin` — TV1 | 2h | Week 1
  - Tạo 4 bảng users, gmail_accounts, system_settings, audit_logs + migration script; seed admin/đổi pass lần đầu, seed 9 khoá settings mặc định.
- `[BE] Service đọc cấu hình hệ thống` — TV1 | 1h | Week 1
  - SystemSettingService: getBool/getInt/getString + invalidate; cache ngắn để không đọc DB mỗi request; inject bởi mọi module kiểm tra cờ.
- `[BE] Tạo keystore PKCS12 + bật HTTPS 8443` — TV1 | 1h | Week 1
  - keytool -genkeypair RSA 2048, lưu keystore.p12 trong resources, cấu hình server.ssl.*; test https://localhost:8443 actuator/health.
- `[BE] Xử lý lỗi thống nhất` — TV1 | 1h | Week 1
  - GlobalExceptionHandler + ErrorDto {timestamp,status,error,message,code} + ErrorCode enum: VALIDATION_ERROR, UNAUTHORIZED, ACCOUNT_DISABLED, FORBIDDEN, NOT_FOUND, REGISTRATION_DISABLED, FEATURE_DISABLED, GMAIL_*.


## [M02] Xác thực tài khoản hệ thống
- **Owner:** TV1
- **Platform:** API
- **Sprint:** SPRINT 1 | **Week:** Week 1
- **Estimate:** 6h
- **Kế hoạch:** 28/10 → 30/10

**Mô tả:** Register (kiểm tra cờ đăng ký), Login, Me, Change-password; BCrypt; kiểm tra enabled + MAINTENANCE_MODE. Xong khi: register→login→me chạy bằng JWT; tắt đăng ký -> EP-A1 403 REGISTRATION_DISABLED nhưng login vẫn được; user bị khoá -> 403 ACCOUNT_DISABLED.

**Endpoints:**
- `EP-A1 POST /api/auth/register` — Đăng ký tài khoản mới {username,password,fullName,email}; kiểm tra cờ ALLOW_REGI
- `EP-A2 POST /api/auth/login` — Đăng nhập {username,password} -> JWT; kiểm tra enabled + MAINTENANCE_MODE; log L
- `EP-A3 GET /api/auth/me` — Thông tin user hiện tại từ JWT (role, mustChangePassword) — Client gọi sau login
- `EP-A4 POST /api/auth/change-password` — User tự đổi mật khẩu {currentPassword,newPassword}; xoá cờ must_change_password.

**Sub-tasks:**
- `[BE] Lấy thông tin user hiện tại` — TV1 | 1h | Week 1 | EP-A3 GET /api/auth/me
  - GET /api/auth/me — JWT required. Trả {id, username, fullName, email, role, mustChangePassword, createdAt}. Client gọi sau login để biết role và hiện khu Admin. Lỗi: 401 UNAUTHORIZED.
- `[BE] User tự đổi mật khẩu` — TV1 | 2h | Week 1 | EP-A4 POST /api/auth/change-password
  - POST /api/auth/change-password — body {currentPassword, newPassword}. Xoá cờ must_change_password sau khi đổi. Lỗi: 400 mật khẩu hiện tại sai / mật khẩu mới yếu hoặc trùng cũ.
- `[BE] Đăng ký tài khoản mới` — TV1 | 1.5h | Week 1 | EP-A1 POST /api/auth/register
  - POST /api/auth/register — body {username, password, fullName, email}. Backend kiểm tra cờ ALLOW_REGISTRATION trước khi tạo; role=USER; BCrypt; log REGISTER. Errors: 403 REGISTRATION_DISABLED, 409 trùng username, 400 VALIDATION_ERROR.
- `[BE] Đăng nhập — trả JWT` — TV1 | 1.5h | Week 1 | EP-A2 POST /api/auth/login
  - POST /api/auth/login — body {username, password}. Kiểm tra enabled + MAINTENANCE_MODE; trả {accessToken, tokenType, expiresIn, user:{id,username,fullName,role,mustChangePassword}}. Log LOGIN/LOGIN_FAILED. Errors: 401 sai tài khoản, 403 ACCOUNT_DISABLED, 403 maintenance.


## [M03] Client nền tảng & Màn hình vào hệ thống
- **Owner:** TV1
- **Platform:** UserWeb
- **Sprint:** SPRINT 1, SPRINT 2 | **Week:** Week 1, Week 2
- **Estimate:** 8h
- **Kế hoạch:** 29/10 → 01/11

**Mô tả:** Main.java + system Look&Feel, ApiClient (HttpClient+TLS+JWT+parse lỗi), SessionStore, khung MainFrame, LoginFrame, RegisterFrame (ẩn nút đăng ký theo EP-P1).

**Endpoints:**
- `EP-P1 GET /api/public/config` — Cờ công khai cho Client quyết định ẩn/hiện nút trước đăng nhập: registrationEnab
- `EP-A1 POST /api/auth/register` — Đăng ký tài khoản mới {username,password,fullName,email}; kiểm tra cờ ALLOW_REGI
- `EP-A2 POST /api/auth/login` — Đăng nhập {username,password} -> JWT; kiểm tra enabled + MAINTENANCE_MODE; log L
- `EP-A3 GET /api/auth/me` — Thông tin user hiện tại từ JWT (role, mustChangePassword) — Client gọi sau login

**Sub-tasks:**
- `[FE] ApiClient HTTPS + JWT` — TV1 | 2h | Week 1
  - java.net.http.HttpClient; SSLContext tin keystore.p12 server; helper GET/POST/PUT/DELETE trả ApiResponse<T>; parse ErrorDto khi status>=400; tự gắn Bearer JWT từ SessionStore.
- `[FE] Lưu phiên đăng nhập` — TV1 | 1h | Week 1
  - SessionStore dùng java.util.prefs.Preferences: lưu JWT + UserDto hiện tại; get/set/clear; dùng cho auto login + ApiClient.
- `[FE] Main.java + UIManager system L&F + entry mở LoginFrame` — TV1 | 0.5h | Week 1
  - Swing thuần, không FlatLaf; main() chỉ setLookAndFeel + invokeLater(mở LoginFrame).
- `[FE] MainFrame khung rỗng` — TV1 | 1.5h | Week 1
  - JMenuBar Đăng xuất + File; JSplitPane 3 vùng (trái Gmail+folder | giữa bảng mail | phải chi tiết); sẵn để M09 + M15 nhét vào; chỉ render khi đã login.
- `[FE] Màn hình đăng ký` — TV1 | 1.5h | Week 1
  - RegisterFrame: username, password, confirm, fullName, email; gọi EP-A1; validate client (khớp pass, email định dạng); báo REGISTRATION_DISABLED thân thiện.
- `[FE] Màn hình đăng nhập` — TV1 | 2h | Week 1
  - LoginFrame: username/password + nút Đăng nhập / Đăng ký; GridBag layout; gọi AuthApi.login qua SwingWorker; báo lỗi 401/403; OK → mở MainFrame.
- `[FE] Ẩn nút đăng ký theo cờ` — TV1 | 1h | Week 2
  - Khi mở app gọi GET /api/public/config trước; registrationEnabled=false → ẩn nút Đăng ký + label 'đóng đăng ký'; maintenanceMode → disable login.


# [US-02] Người dùng liên kết và quản lý tài khoản Gmail

_M04 Gmail App Password Backend, M05 Màn hình Quản lý Gmail._


## [M04] Liên kết Gmail bằng App Password
- **Owner:** TV2
- **Platform:** API
- **Sprint:** SPRINT 1, SPRINT 2 | **Week:** Week 1, Week 2
- **Estimate:** 8h
- **Kế hoạch:** 02/11 → 04/11

**Mô tả:** Thêm/liệt kê/xoá Gmail; test kết nối IMAP thật trước khi lưu; AES-256-GCM; kiểm tra trùng + giới hạn MAX_GMAIL_PER_USER. Xong khi: sai App Password -> 422 GMAIL_AUTH_FAILED, đúng -> hiện ở EP-G1, trùng -> 409, vượt giới hạn -> 409 GMAIL_LIMIT_REACHED.

**Endpoints:**
- `EP-G1 GET /api/gmail-accounts` — Danh sách Gmail của chính user; không trả credential.
- `EP-G2 POST /api/gmail-accounts/app-password` — Liên kết Gmail bằng App Password {email,appPassword}; test IMAP thật trước khi l
- `EP-G3 DELETE /api/gmail-accounts/{id}` — Xoá liên kết Gmail; OAuth2 thì revoke token trước; log UNLINK_GMAIL.

**Sub-tasks:**
- `[BE] Chuẩn bị Gmail test + entity` — TV2 | 2h | Week 1
  - Tạo 1 Gmail test (2FA + App Password) — không ghi credential vào backlog; test IMAP tay bằng openssl s_client; viết entity GmailAccount + GmailAccountDto + error mapping GMAIL_DUPLICATE/AUTH_FAILED/LIMIT_REACHED.
- `[BE] Danh sách Gmail của user` — TV2 | 1.5h | Week 2 | EP-G1 GET /api/gmail-accounts
  - GET /api/gmail-accounts — JWT. Trả [{id, email, displayName, authType, status, createdAt}]. Không bao giờ trả appPassword/refreshToken kể cả đã mã hoá.
- `[BE] Liên kết Gmail bằng App Password` — TV2 | 4h | Week 2 | EP-G2 POST /api/gmail-accounts/app-password
  - POST /api/gmail-accounts/app-password — body {email, appPassword}. Kiểm tra: ALLOW_APP_PASSWORD_LINK → trùng → MAX_GMAIL_PER_USER → test IMAP thật → mã hoá AES-256-GCM → lưu authType=APP_PASSWORD. Log LINK_GMAIL. Errors: 403 FEATURE_DISABLED, 409 GMAIL_DUPLICATE, 409 GMAIL_LIMIT_REACHED, 422 GMAIL_AUTH_FAILED, 502 GMAIL_CONNECTION_ERROR.
- `[BE] Xoá liên kết Gmail` — TV2 | 2.5h | Week 2 | EP-G3 DELETE /api/gmail-accounts/{id}
  - DELETE /api/gmail-accounts/{id} — chủ sở hữu. Xoá credential khỏi DB; nếu authType=OAUTH2 thì gọi Google revoke token trước (dùng service của TV3). Không xoá mail nào trên Gmail. Log UNLINK_GMAIL. Trả 204.


## [M05] Màn hình Quản lý Gmail
- **Owner:** TV2
- **Platform:** UserWeb
- **Sprint:** SPRINT 2, SPRINT 3 | **Week:** Week 2, Week 3
- **Estimate:** 6h
- **Kế hoạch:** 04/11 → 06/11

**Mô tả:** GmailAccountDialog: JTable Gmail đã liên kết, form App Password, nút 'Liên kết bằng Google' gọi EP-O1 + mở browser, xoá có xác nhận; chỉ hiện cách liên kết đang bật.

**Endpoints:**
- `EP-G1 GET /api/gmail-accounts` — Danh sách Gmail của chính user; không trả credential.
- `EP-G2 POST /api/gmail-accounts/app-password` — Liên kết Gmail bằng App Password {email,appPassword}; test IMAP thật trước khi l
- `EP-G3 DELETE /api/gmail-accounts/{id}` — Xoá liên kết Gmail; OAuth2 thì revoke token trước; log UNLINK_GMAIL.
- `EP-O1 GET /api/oauth2/authorize-url` — Tạo URL uỷ quyền Google + state gắn user; kiểm tra ALLOW_OAUTH2_LINK + MAX_GMAIL

**Sub-tasks:**
- `[FE] Dialog quản lý Gmail` — TV2 | 1.5h | Week 2
  - GmailAccountDialog + JTable Gmail đã liên kết (email, authType, status); gọi EP-G1 render; cột status ACTIVE/NEEDS_REAUTH; refresh sau mỗi thao tác.
- `[FE] Form thêm Gmail App Password` — TV2 | 2h | Week 2
  - Email + JPasswordField; validate định dạng; gọi EP-G2 qua SwingWorker; báo lỗi 409/422/502; OK → refresh bảng.
- `[FE] Nút xoá liên kết Gmail` — TV2 | 0.5h | Week 2
  - JOptionPane xác nhận xoá rõ email; gọi EP-G3; thành công → refresh; lỗi 404 báo không tồn tại.
- `[FE] Ẩn nút thêm theo cờ` — TV2 | 0.5h | Week 2
  - Khi mở dialog đọc EP-P1; nếu cả 2 cờ tắt → disable khu thêm + thông báo.
- `[FE] Nút liên kết Google OAuth2` — TV2 | 1.5h | Week 3
  - Nút 'Liên kết bằng Google' — chỉ hiện khi EP-P1 oauth2LinkEnabled; gọi EP-O1 lấy authorizeUrl; Desktop.getDesktop().browse(url); nút 'Đã liên kết xong' → refresh EP-G1.


## [M06] Liên kết Gmail bằng OAuth2
- **Owner:** TV3
- **Platform:** API
- **Sprint:** SPRINT 2, SPRINT 3 | **Week:** Week 2, Week 3
- **Estimate:** 14h
- **Kế hoạch:** 09/11 → 12/11

**Mô tả:** Google Cloud config (test users), authorize-url + state, callback đổi code->token, refresh access token, revoke khi xoá, trạng thái NEEDS_REAUTH. Xong khi: luồng OAuth2 end-to-end, thu hồi trên Google -> 422 GMAIL_NEEDS_REAUTH.

**Endpoints:**
- `EP-O1 GET /api/oauth2/authorize-url` — Tạo URL uỷ quyền Google + state gắn user; kiểm tra ALLOW_OAUTH2_LINK + MAX_GMAIL
- `EP-O2 GET /api/oauth2/callback` — Google callback: đổi code -> token, lấy email, lưu refresh_token mã hoá; trả HTM
- `EP-G3 DELETE /api/gmail-accounts/{id}` — Xoá liên kết Gmail; OAuth2 thì revoke token trước; log UNLINK_GMAIL.
- `EP-AD8 DELETE /api/admin/users/{id}` — Xoá user + toàn bộ Gmail liên kết (revoke OAuth2 trước).

**Sub-tasks:**
- `[BE] Hỗ trợ xoá liên kết OAuth2` — TV3 | 2h | Week 2 | EP-G3 DELETE /api/gmail-accounts/{id}
  - Service gọi Google revoke endpoint khi user xoá liên kết OAuth2 — dùng bởi EP-G3. Khi refresh token hết hiệu lực → đánh dấu NEEDS_REAUTH.
- `[BE] Hỗ trợ xoá user có Gmail OAuth2` — TV3 | 2h | Week 2 | EP-AD8 DELETE /api/admin/users/{id}
  - Dùng bởi EP-AD8 — khi Admin xoá user, revoke mọi OAuth2 token của Gmail đã liên kết trước khi xoá bản ghi.
- `[BE] Callback OAuth2 — đổi code lấy token` — TV3 | 6h | Week 3 | EP-O2 GET /api/oauth2/callback
  - GET /api/oauth2/callback?code=&state= — không cần JWT (kiểm tra state). Đổi code → access_token + refresh_token; gọi Google lấy email; kiểm tra trùng; chỉ lưu refresh_token đã mã hoá AES, authType=OAUTH2. Trả HTML 'Liên kết thành công, quay lại ứng dụng' hoặc trang lỗi.
- `[BE] Tạo URL uỷ quyền Google OAuth2` — TV3 | 4h | Week 3 | EP-O1 GET /api/oauth2/authorize-url
  - GET /api/oauth2/authorize-url — JWT. Kiểm tra ALLOW_OAUTH2_LINK + MAX_GMAIL_PER_USER; sinh state gắn user + hạn ngắn, lưu tạm server. Trả {authorizeUrl}. Client mở bằng Desktop.browse.


## [M07] MailConnectionFactory — kết nối Gmail dùng chung
- **Owner:** TV3
- **Platform:** API
- **Sprint:** SPRINT 1, SPRINT 3 | **Week:** Week 1, Week 3
- **Estimate:** 8h
- **Kế hoạch:** 26/10 → 28/10

**Mô tả:** Điểm duy nhất tạo IMAP Store / SMTP Transport theo authType: App Password (password auth) trước, OAuth2 (XOAUTH2) sau; TLS IMAPS 993 / STARTTLS 587; timeout thống nhất. Chốt interface ngay tuần 1; App Password xong tuần 2; XOAUTH2 bổ sung tuần 3.

**Endpoints:**

**Sub-tasks:**
- `[BE] Interface MailConnectionFactory` — TV3 | 1h | Week 1
  - public interface: Store openImap(GmailAccount) / Transport openSmtp(GmailAccount); GmailAuthContext{email, authType, appPassword|accessToken}. Chốt sớm cho TV2/TV4/TV5.
- `[BE] Kết nối bằng App Password` — TV3 | 2.5h | Week 1
  - Session props: mail.imaps.ssl.enable, mail.smtp.starttls.required, timeouts (10s connect, 30s read); Authenticator email/appPassword; IMAPS 993 + SMTP 587 STARTTLS.
- `[BE] Test factory kết nối thật` — TV3 | 1.5h | Week 1
  - Integration test với Gmail test App Password — kết nối imap.gmail.com:993 + smtp.gmail.com:587; assert listFolders>0; đo thời gian connect.
- `[BE] Kết nối bằng OAuth2 XOAUTH2` — TV3 | 3h | Week 3
  - SASL XOAUTH2; access token hết hạn → OAuth2Service.refreshAccessToken(refreshToken) rồi xây lại chuỗi XOAUTH2.


# [US-03] Người dùng đọc và quản lý hộp thư

_M06 OAuth2 Backend, M07 MailConnectionFactory. Chốt interface factory sớm._


## [M08] Nhận mail — IMAP Fetch
- **Owner:** TV4
- **Platform:** API
- **Sprint:** SPRINT 1, SPRINT 2 | **Week:** Week 1, Week 2
- **Estimate:** 12h
- **Kế hoạch:** 02/11 → 06/11

**Mô tả:** Folders + danh sách mail phân trang bằng FetchProfile + chi tiết parse MIME + đặt cờ \Seen + tìm kiếm IMAP search. Xong khi: INBOX khớp Gmail web, chi tiết đúng nội dung + metadata đính kèm, đọc xong hết in đậm.

**Endpoints:**
- `EP-M1 GET /api/mails/folders` — Danh sách folder IMAP của Gmail (name,total,unread). Query: gmailAccountId.
- `EP-M2 GET /api/mails` — Danh sách mail phân trang theo folder, FetchProfile header-only; q= tìm bằng IMA
- `EP-M3 GET /api/mails/{gmailAccountId}/{folder}/{uid}` — Chi tiết mail: body text/html + metadata đính kèm; mở READ_WRITE đặt cờ \Seen.

**Sub-tasks:**
- `[BE] MIME fixtures + Mail DTO` — TV4 | 2h | Week 1
  - Soạn 5-6 mail mẫu trong Gmail test (text only, html only, text+html, có đính kèm, đính kèm lồng, forward); export .eml; viết MailSummary + MailDetail DTO + contract phân trang + error mapping.
- `[BE] Chi tiết mail + parse MIME` — TV4 | 4.5h | Week 2 | EP-M3 GET /api/mails/{gmailAccountId}/{folder}/{uid}
  - GET /api/mails/{gmailAccountId}/{folder}/{uid} — mở folder READ_WRITE, parse đầy đủ: from/to/cc/subject/date/bodyText/bodyHtml/attachments metadata {index,fileName,contentType,sizeBytes}; đặt cờ \Seen trên Gmail. Errors: +404 UID không thấy.
- `[BE] Danh sách folder IMAP` — TV4 | 3h | Week 2 | EP-M1 GET /api/mails/folders
  - GET /api/mails/folders?gmailAccountId={id} — mở IMAP, liệt kê folder + total + unread trong cùng kết nối, đóng. Errors: 403 GMAIL_NOT_OWNED, 422 NEEDS_REAUTH, 502 CONN_ERROR.
- `[BE] Danh sách mail phân trang + tìm kiếm` — TV4 | 4.5h | Week 2 | EP-M2 GET /api/mails
  - GET /api/mails?gmailAccountId=&folder=&page=&size=&q= — mới nhất trước, fetch đúng trang bằng FetchProfile (ENVELOPE, FLAGS, CONTENT_INFO) không tải toàn bộ. Trả {content:[{uid,from,subject,date,isRead,hasAttachment}],page,size,totalElements,totalPages}. q: IMAP Folder.search(). Lỗi: +400 folder không tồn tại.


## [M09] Màn hình Hộp thư & Chi tiết mail
- **Owner:** TV4
- **Platform:** UserWeb
- **Sprint:** SPRINT 2 | **Week:** Week 2
- **Estimate:** 10h
- **Kế hoạch:** 04/11 → 08/11

**Mô tả:** Trong MainFrame: JTree/JList Gmail+folder, JTable mail (chưa đọc in đậm, icon đính kèm), phân trang, Làm mới, MailDetailPanel (JEditorPane/JTextArea). Mọi gọi mạng qua SwingWorker.

**Endpoints:**
- `EP-M1 GET /api/mails/folders` — Danh sách folder IMAP của Gmail (name,total,unread). Query: gmailAccountId.
- `EP-M2 GET /api/mails` — Danh sách mail phân trang theo folder, FetchProfile header-only; q= tìm bằng IMA
- `EP-M3 GET /api/mails/{gmailAccountId}/{folder}/{uid}` — Chi tiết mail: body text/html + metadata đính kèm; mở READ_WRITE đặt cờ \Seen.
- `EP-G1 GET /api/gmail-accounts` — Danh sách Gmail của chính user; không trả credential.

**Sub-tasks:**
- `[FE] Bảng danh sách mail` — TV4 | 2h | Week 2
  - JTable theo MailSummary; renderer: isRead=false → font bold; hasAttachment → icon; double-click mở chi tiết.
- `[FE] Cây Gmail + folder trái MainFrame` — TV4 | 2h | Week 2
  - JTree: gốc=Gmail, con=folder (name+unread); load Gmail từ EP-G1, folder từ EP-M1; click folder → load list mail; reload khi đổi Gmail.
- `[FE] Panel chi tiết mail` — TV4 | 2.5h | Week 2
  - MailDetailPanel: from/to/cc/date + JEditorPane (HTML) hoặc JTextArea (text) + JList đính kèm; gọi EP-M3; bấm đính kèm → flow M12.
- `[FE] Phân trang + tìm kiếm mail` — TV4 | 1.5h | Week 2
  - Prev/Next + page label; ô search → gọi lại EP-M2 với q; nút Làm mới → fetch lại trang hiện tại.
- `[FE] SwingWorker + trạng thái tải` — TV4 | 2h | Week 2
  - Mọi gọi mạng bọc SwingWorker; disable UI khi đang fetch; báo lỗi rõ không treo EDT; timeout/error state thân thiện.


## [M16] Xoá mail (Trash semantics)
- **Owner:** TV6
- **Platform:** API
- **Sprint:** SPRINT 2, SPRINT 3 | **Week:** Week 2, Week 3
- **Estimate:** 7h
- **Kế hoạch:** 02/11 → 13/11

**Mô tả:** Backend: DELETE /api/mails/{g}/{f}/{uid}. Folder=Trash → setFlags(\Deleted)+expunge() xoá vĩnh viễn. Folder khác → uidCopy sang [Gmail]/Trash + \Deleted + expunge nguồn. Audit MAIL_DELETE. Client: nút Xoá trong MailDetailPanel + context menu JTable (confirm → gọi API → refresh).

**Endpoints:**
- `EP-M6 DELETE /api/mails/{gmailAccountId}/{folder}/{uid}` — Xoá mail. Folder=Trash → setFlags(\Deleted) + expunge() xoá vĩnh viễn. Folder kh

**Sub-tasks:**
- `[BE] Xoá mail qua IMAP` — TV6 | 4h | Week 2 | EP-M6 DELETE /api/mails/{gmailAccountId}/{folder}/{uid}
  - DELETE /api/mails/{gmailAccountId}/{folder}/{uid} — mở folder READ_WRITE. Folder=Trash hoặc [Gmail]/Trash → setFlags(\Deleted) + expunge() (xoá vĩnh viễn). Folder khác → uidCopy sang [Gmail]/Trash + \Deleted + expunge nguồn. Trả 204. Errors: 404 UID, 403 GMAIL_NOT_OWNED, 422 NEEDS_REAUTH, 502 CONN_ERROR.
- `[BE] Ghi audit log khi xoá mail` — TV6 | 2h | Week 3
  - Ghi AuditLog action=MAIL_DELETE kèm metadata (userId, gmailAccount, folder, uid, thời điểm). Verify bằng Gmail web: mail chuyển Trash hoặc biến mất.
- `[FE] Nút Xoá trong chi tiết + context menu` — TV4 | 2h | Week 3
  - JOptionPane xác nhận xoá; gọi EP-M6 qua SwingWorker; thành công → đóng MailDetailPanel, refresh JTable + folder tree (unread/total thay đổi). Disable nút khi đang xoá.


## [M17] Gắn sao & đánh dấu đã/chưa đọc (flags)
- **Owner:** TV1
- **Platform:** API
- **Sprint:** SPRINT 2, SPRINT 3 | **Week:** Week 2, Week 3
- **Estimate:** 5h
- **Kế hoạch:** 06/11 → 15/11

**Mô tả:** Backend: PUT /api/mails/{g}/{f}/{uid}/flags {starred, seen} → IMAP setFlags \Flagged/\Seen. Trả {uid, starred, seen}. Client: toggle icon sao trong JTable mail + menu đánh dấu chưa đọc trong MailDetailPanel.

**Endpoints:**
- `EP-M7 PUT /api/mails/{gmailAccountId}/{folder}/{uid}/flags` — Toggle cờ mail {starred, seen} → IMAP setFlags \Flagged / \Seen. Trả {uid, starr

**Sub-tasks:**
- `[BE] Toggle cờ starred/seen qua IMAP` — TV1 | 3h | Week 2 | EP-M7 PUT /api/mails/{gmailAccountId}/{folder}/{uid}/flags
  - PUT /api/mails/{g}/{f}/{uid}/flags — body {starred:bool|null, seen:bool|null}. Mở folder READ_WRITE; setFlags \Flagged và/hoặc \Seen theo field được truyền; không đụng field null. Trả {uid, starred, seen}.
- `[FE] Toggle sao + đánh dấu chưa đọc` — TV4 | 2h | Week 3
  - Cột sao trong JTable mail — click toggle EP-M7 {starred}. Context menu 'Đánh dấu chưa đọc' → EP-M7 {seen:false}. Refresh cell renderer ngay sau response.


# [US-04] Người dùng soạn và gửi mail

_M08 IMAP Fetch Backend, M09 Màn hình Hộp thư & Chi tiết mail._


## [M10] Gửi mail — SMTP
- **Owner:** TV5
- **Platform:** API
- **Sprint:** SPRINT 1, SPRINT 2 | **Week:** Week 1, Week 2
- **Estimate:** 8h
- **Kế hoạch:** 03/11 → 06/11

**Mô tả:** Dựng MimeMessage multipart; đính kèm encode Base64; kiểm tra cờ ALLOW_SEND_MAIL + MAX_ATTACHMENT_SIZE_MB; gửi STARTTLS 587 theo authType; log SEND_MAIL.

**Endpoints:**
- `EP-M5 POST /api/mails/send` — Gửi mail SMTP STARTTLS multipart (to,cc,bcc,subject,body,isHtml,files[]); kiểm t

**Sub-tasks:**
- `[BE] SendMailRequest DTO + builder` — TV5 | 2h | Week 1
  - Chốt multipart request schema (gmailAccountId,to,cc,bcc,subject,body,isHtml,files[]); viết MimeMessage builder prototype (JavaMail) chạy độc lập — text+html+attachment encode Base64; thiết kế response + error codes.
- `[BE] Gửi mail SMTP multipart` — TV5 | 8h | Week 2 | EP-M5 POST /api/mails/send
  - POST /api/mails/send — multipart: gmailAccountId, to, cc, bcc, subject, body, isHtml, files[]. Kiểm tra ALLOW_SEND_MAIL → chủ Gmail → file ≤ MAX_ATTACHMENT_SIZE_MB → MimeMessage (Base64 RFC 2045) → Transport.send() STARTTLS 587 theo authType. Log SEND_MAIL metadata. Errors: 403 FEATURE_DISABLED, 403 GMAIL_NOT_OWNED, 413 PAYLOAD_TOO_LARGE, 422 NEEDS_REAUTH, 502 SMTP lỗi.


## [M11] Màn hình Soạn mail
- **Owner:** TV5
- **Platform:** UserWeb
- **Sprint:** SPRINT 2 | **Week:** Week 2
- **Estimate:** 6h
- **Kế hoạch:** 06/11 → 08/11

**Mô tả:** ComposeDialog: From chọn Gmail đã liên kết, To/Cc/Bcc, Subject, Body, JFileChooser đính kèm nhiều file, kiểm tra sơ bộ kích thước phía Client, gửi qua EP-M5; disable khi Admin tắt gửi.

**Endpoints:**
- `EP-M5 POST /api/mails/send` — Gửi mail SMTP STARTTLS multipart (to,cc,bcc,subject,body,isHtml,files[]); kiểm t

**Sub-tasks:**
- `[FE] Chọn file đính kèm` — TV5 | 1.5h | Week 2
  - JFileChooser multi-select + JList hiển thị từng file (tên + size); nút xoá từng; tổng size cảnh báo khi vượt MAX_ATTACHMENT_SIZE_MB.
- `[FE] Dialog soạn mail` — TV5 | 2h | Week 2
  - ComposeDialog: From chọn Gmail (JComboBox), To/Cc/Bcc, Subject, Body (JTextArea/JEditorPane), isHtml toggle; GridBag; load Gmail từ EP-G1.
- `[FE] Disable Soạn khi bị tắt` — TV5 | 1h | Week 2
  - MainFrame khởi tạo đọc cờ; allowSendMail=false → disable nút Soạn/Gửi; user không có Gmail nào → mở Compose báo 'liên kết Gmail trước'.
- `[FE] Gửi mail qua API` — TV5 | 1.5h | Week 2
  - Nút Gửi → EP-M5 multipart qua SwingWorker; progress indicator; disable khi đang gửi; OK → đóng dialog + thông báo; lỗi 413/403/502 hiện rõ.


# [US-05] Người dùng tải và mở file đính kèm

_M10 SMTP Backend, M11 Màn hình Soạn mail, M12 Đính kèm — Tải & Mở file._


## [M12] File đính kèm — Tải về & Mở file
- **Owner:** TV5
- **Platform:** API
- **Sprint:** SPRINT 3 | **Week:** Week 3
- **Estimate:** 10h
- **Kế hoạch:** 09/11 → 12/11

**Mô tả:** Backend: trích MimeBodyPart thứ index, giải Base64, stream download. Client: FileUtil lưu %USERPROFILE%\Downloads\MailApp\, chống trùng tên, Desktop.open(), đã tải rồi mở local.

**Endpoints:**
- `EP-M4 GET /api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download` — Stream file đính kèm thứ index, giải mã Base64, kiểm tra MAX_ATTACHMENT_SIZE_MB.

**Sub-tasks:**
- `[BE] Stream file đính kèm` — TV5 | 10h | Week 3 | EP-M4 GET /api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download
  - GET /api/mails/{g}/{f}/{uid}/attachments/{index}/download — mở IMAP, tìm message theo UID, lấy MimeBodyPart thứ index, giải Base64, kiểm tra MAX_ATTACHMENT_SIZE_MB, stream byte[] với Content-Disposition: attachment; filename=... và Content-Type đúng. Errors: +404 không có index, 413 PAYLOAD_TOO_LARGE.
- `[FE] Tải file đính kèm về Downloads + chống trùng tên` — TV5 | 2h | Week 3
  - FileUtil.save(bytes, gmail, fileName): lưu vào %USERPROFILE%\Downloads\MailApp\<gmail>\<file>; nếu trùng tên thêm (1),(2)... không ghi đè; trả File handle.
- `[FE] Tự mở file sau khi tải + nhớ file đã tải` — TV5 | 1h | Week 3
  - Sau khi tải: Desktop.getDesktop().open(file). Track đã tải bằng (gmailAccountId + uid + fileName + size) trong Preferences — nếu trùng và chưa đổi thì mở file local, không gọi API lại.


# [US-06] Quản trị viên quản lý và vận hành hệ thống

_M13 Admin User Backend, M14 Admin Cấu hình/Thống kê/Nhật ký, M15 Màn hình Admin. Độc lập với mail._


## [M13] Admin — Quản lý người dùng
- **Owner:** TV6
- **Platform:** API
- **Sprint:** SPRINT 1, SPRINT 2 | **Week:** Week 1, Week 2
- **Estimate:** 10h
- **Kế hoạch:** 29/10 → 02/11

**Mô tả:** Tạo hộ (không bị chặn ALLOW_REGISTRATION), danh sách/chi tiết, khoá/mở khoá, đổi role, reset-password, xoá; ràng buộc Admin cuối/chính mình; log ADMIN_*.

**Endpoints:**
- `EP-AD1 POST /api/admin/users` — Admin tạo tài khoản hộ — không bị chặn bởi ALLOW_REGISTRATION; log ADMIN_CREATE_
- `EP-AD2 GET /api/admin/users` — Danh sách user: tìm username/email/họ tên, lọc role/enabled, phân trang, kèm gma
- `EP-AD3 GET /api/admin/users/{id}` — Chi tiết user + danh sách Gmail đã liên kết (chỉ email/authType/status).
- `EP-AD4 PUT /api/admin/users/{id}/lock` — Khoá tài khoản (enabled=false); chặn JWT cũ ở request sau; log ADMIN_LOCK_USER.
- `EP-AD5 PUT /api/admin/users/{id}/unlock` — Mở khoá tài khoản (enabled=true).
- `EP-AD6 PUT /api/admin/users/{id}/role` — Đổi role USER<->ADMIN; chặn tự hạ mình / hạ Admin cuối.
- `EP-AD7 PUT /api/admin/users/{id}/reset-password` — Đặt lại mật khẩu + must_change_password=true.
- `EP-AD8 DELETE /api/admin/users/{id}` — Xoá user + toàn bộ Gmail liên kết (revoke OAuth2 trước).

**Sub-tasks:**
- `[BE] Admin tạo tài khoản hộ` — TV6 | 1.5h | Week 1 | EP-AD1 POST /api/admin/users
  - POST /api/admin/users — ADMIN only. Body như register + role tuỳ chọn. Không bị chặn bởi ALLOW_REGISTRATION. Log ADMIN_CREATE_USER. Errors: 409 trùng username.
- `[BE] Chi tiết một user cho Admin` — TV6 | 1h | Week 1 | EP-AD3 GET /api/admin/users/{id}
  - GET /api/admin/users/{id} — ADMIN. Trả thông tin + gmailAccounts [{email,authType,status}] — không credential, không nội dung mail. Errors: 404.
- `[BE] Danh sách user cho Admin` — TV6 | 1.5h | Week 1 | EP-AD2 GET /api/admin/users
  - GET /api/admin/users?q=&role=&enabled=&page=&size= — tìm theo username/email/họ tên, lọc role + trạng thái, phân trang. Trả {content:[{id,username,fullName,email,role,enabled,gmailCount,createdAt}]}.
- `[BE] Khoá tài khoản user` — TV6 | 1h | Week 1 | EP-AD4 PUT /api/admin/users/{id}/lock
  - PUT /api/admin/users/{id}/lock — ADMIN. enabled=false; JWT cũ bị chặn ở request tiếp theo. Chặn tự khoá mình / khoá Admin cuối → 400. Log ADMIN_LOCK_USER.
- `[BE] Mở khoá tài khoản user` — TV6 | 0.5h | Week 1 | EP-AD5 PUT /api/admin/users/{id}/unlock
  - PUT /api/admin/users/{id}/unlock — ADMIN. enabled=true. Errors: 404.
- `[BE] Admin đặt lại mật khẩu user` — TV6 | 1h | Week 2 | EP-AD7 PUT /api/admin/users/{id}/reset-password
  - PUT /api/admin/users/{id}/reset-password — body {newPassword}; băm BCrypt; đặt must_change_password=true để user đổi lại khi đăng nhập. Errors: 400 VALIDATION_ERROR, 404.
- `[BE] Xoá tài khoản user` — TV6 | 2h | Week 2 | EP-AD8 DELETE /api/admin/users/{id}
  - DELETE /api/admin/users/{id} — xoá user + toàn bộ gmail_accounts liên kết. OAuth2 → revoke token trước (dùng service TV3). Có confirm phía Client. Chặn tự xoá / xoá Admin cuối.
- `[BE] Đổi role user` — TV6 | 1.5h | Week 2 | EP-AD6 PUT /api/admin/users/{id}/role
  - PUT /api/admin/users/{id}/role — ADMIN. Body {role: ADMIN|USER}. Chặn tự hạ mình / hạ Admin cuối → 400.


## [M14] Admin — Cấu hình, Thống kê & Nhật ký
- **Owner:** TV6
- **Platform:** API
- **Sprint:** SPRINT 1, SPRINT 2 | **Week:** Week 1, Week 2
- **Estimate:** 8h
- **Kế hoạch:** 28/10 → 02/11

**Mô tả:** Đọc/ghi system_settings + validate + cấm tắt cả 2 cách liên kết; public/config cho Client chưa đăng nhập; stats tổng quan; audit log lọc phân trang.

**Endpoints:**
- `EP-P1 GET /api/public/config` — Cờ công khai cho Client quyết định ẩn/hiện nút trước đăng nhập: registrationEnab
- `EP-S1 GET /api/admin/settings` — Toàn bộ cấu hình system_settings (9 khoá).
- `EP-S2 PUT /api/admin/settings` — Cập nhật nhiều khoá; validate; cấm tắt cả hai cách liên kết; log ADMIN_CHANGE_SE
- `EP-ST1 GET /api/admin/stats` — Thống kê tổng quan: user, Gmail theo authType, đăng ký hôm nay/7 ngày.
- `EP-ST2 GET /api/admin/logs` — Audit log lọc theo user/action/ngày, phân trang; không chứa bí mật.

**Sub-tasks:**
- `[BE] Cập nhật cấu hình hệ thống` — TV6 | 3h | Week 1 | EP-S2 PUT /api/admin/settings
  - PUT /api/admin/settings — ADMIN. Body {settings:{key:value,...}}. Validate boolean/int; không cho tắt cả ALLOW_OAUTH2_LINK và ALLOW_APP_PASSWORD_LINK cùng lúc → 400. Log ADMIN_CHANGE_SETTING kèm old→new. Hiệu lực ngay, không restart.
- `[BE] Cờ cấu hình công khai` — TV6 | 1h | Week 1 | EP-P1 GET /api/public/config
  - GET /api/public/config — không cần JWT. Trả {registrationEnabled, oauth2LinkEnabled, appPasswordLinkEnabled, maintenanceMode} — chỉ cờ an toàn để Client ẩn/hiện nút trước đăng nhập.
- `[BE] Đọc toàn bộ cấu hình hệ thống` — TV6 | 1h | Week 1 | EP-S1 GET /api/admin/settings
  - GET /api/admin/settings — ADMIN. Trả [{key, value, description, updatedBy, updatedAt}] của 9 khoá system_settings.
- `[BE] Thống kê tổng quan` — TV6 | 1.5h | Week 2 | EP-ST1 GET /api/admin/stats
  - GET /api/admin/stats — ADMIN. Trả {totalUsers, activeUsers, lockedUsers, totalAdmins, totalGmailAccounts, gmailByAppPassword, gmailByOAuth2, registrationsToday, registrationsLast7Days}.
- `[BE] Truy vấn audit log` — TV6 | 1.5h | Week 2 | EP-ST2 GET /api/admin/logs
  - GET /api/admin/logs?userId=&action=&from=&to=&page=&size= — ADMIN. Lọc theo user/action/ngày, mới nhất trước. Không chứa nội dung mail, mật khẩu, token.


## [M15] Màn hình Admin
- **Owner:** TV6
- **Platform:** AdminWeb
- **Sprint:** SPRINT 3 | **Week:** Week 3
- **Estimate:** 8h
- **Kế hoạch:** 09/11 → 15/11

**Mô tả:** AdminFrame/tab Admin với JTabbedPane 4 tab: Người dùng, Cấu hình (JCheckBox cờ + JSpinner tham số + Lưu + Đặt lại), Thống kê, Nhật ký. Chỉ ADMIN thấy.

**Endpoints:**
- `EP-AD1 POST /api/admin/users` — Admin tạo tài khoản hộ — không bị chặn bởi ALLOW_REGISTRATION; log ADMIN_CREATE_
- `EP-AD2 GET /api/admin/users` — Danh sách user: tìm username/email/họ tên, lọc role/enabled, phân trang, kèm gma
- `EP-AD3 GET /api/admin/users/{id}` — Chi tiết user + danh sách Gmail đã liên kết (chỉ email/authType/status).
- `EP-AD4 PUT /api/admin/users/{id}/lock` — Khoá tài khoản (enabled=false); chặn JWT cũ ở request sau; log ADMIN_LOCK_USER.
- `EP-AD5 PUT /api/admin/users/{id}/unlock` — Mở khoá tài khoản (enabled=true).
- `EP-AD6 PUT /api/admin/users/{id}/role` — Đổi role USER<->ADMIN; chặn tự hạ mình / hạ Admin cuối.
- `EP-AD7 PUT /api/admin/users/{id}/reset-password` — Đặt lại mật khẩu + must_change_password=true.
- `EP-AD8 DELETE /api/admin/users/{id}` — Xoá user + toàn bộ Gmail liên kết (revoke OAuth2 trước).
- `EP-S1 GET /api/admin/settings` — Toàn bộ cấu hình system_settings (9 khoá).
- `EP-S2 PUT /api/admin/settings` — Cập nhật nhiều khoá; validate; cấm tắt cả hai cách liên kết; log ADMIN_CHANGE_SE
- `EP-ST1 GET /api/admin/stats` — Thống kê tổng quan: user, Gmail theo authType, đăng ký hôm nay/7 ngày.
- `EP-ST2 GET /api/admin/logs` — Audit log lọc theo user/action/ngày, phân trang; không chứa bí mật.
- `EP-P1 GET /api/public/config` — Cờ công khai cho Client quyết định ẩn/hiện nút trước đăng nhập: registrationEnab

**Sub-tasks:**
- `[FE-Admin] Các nút thao tác user` — TV6 | 2.5h | Week 3
  - Buttons: Tạo mới / Khoá / Mở khoá / Đổi role / Reset mật khẩu / Xoá; gọi EP-AD1/AD4-AD8; confirm cho Xoá + Đổi role; refresh bảng sau thao tác.
- `[FE-Admin] Khung AdminFrame 4 tab` — TV6 | 1h | Week 3
  - AdminFrame + JTabbedPane: Người dùng, Cấu hình, Thống kê, Nhật ký; chỉ hiện menu khi currentUser.role==ADMIN.
- `[FE-Admin] Tab cấu hình hệ thống` — TV6 | 2h | Week 3
  - 5 JCheckBox cờ boolean + 4 JSpinner/JTextField tham số số; Lưu → EP-S2; Đặt lại mặc định; validate không tắt cả 2 cách liên kết.
- `[FE-Admin] Tab danh sách user` — TV6 | 2.5h | Week 3
  - JTable: username, họ tên, email, role, enabled, gmailCount + ô tìm kiếm + filter role/enabled + phân trang; double-click mở chi tiết EP-AD3.
- `[FE-Admin] Tab nhật ký` — TV6 | 1.5h | Week 3
  - JTable audit log (thời gian, user, action, detail, ip) + filter theo user/action/khoảng ngày + phân trang; gọi EP-ST2.
- `[FE-Admin] Tab thống kê` — TV6 | 1h | Week 3
  - Hiển thị EP-ST1 dạng card/label: totalUsers, activeUsers, lockedUsers, totalGmailAccounts theo authType, registrationsToday/Last7Days; refresh khi mở tab.
