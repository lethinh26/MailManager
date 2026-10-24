# Java Mail App — Thiết kế hệ thống v2 (để duyệt)

> Dự án môn học: Ứng dụng Windows quản lý nhiều Gmail, gửi / nhận mail an toàn qua TLS
> Client: **Java Swing thuần** | Backend: **Spring Boot** | DB: **MySQL 8**
> Phiên bản: **v2 — 2026-10-23** — thay thế v1
> Các quyết định đã chốt ở v2: (1) Liên kết Gmail bằng **OAuth2 hoặc App Password, user tự chọn**, (2) MySQL, (3) **Fetch mail trực tiếp từ Gmail mỗi lần, không cache vào DB**, (4) Swing thuần, không dùng thư viện UI ngoài. Bổ sung module Admin đầy đủ.

---

## 1. Tổng quan hệ thống

Hệ thống gồm **2 ứng dụng độc lập** giao tiếp qua HTTPS (TLS):

```
                        TLS lớp 1                          TLS lớp 2
[Gmail Server] <-----------------------> [Backend Spring Boot] <-----------------------> [Client Java Swing]
 smtp.gmail.com:587  STARTTLS               Server trung gian              HTTPS 8443        App Windows
 imap.gmail.com:993  SSL/TLS               - Xác thực user                + JWT             - Không nối thẳng Gmail
                                           - Nối Gmail thay user
                                           - Không lưu nội dung mail
```

Luồng ý tưởng theo đề bài:

1. Gmail có mail mới → Backend kết nối Gmail bằng **IMAP over TLS (lớp 1)** và fetch mail về ngay tại thời điểm user yêu cầu.
2. Backend trả mail cho Client qua **HTTPS (TLS lớp 2)**.
3. Nội dung mail và file đính kèm được mã hoá **Base64** theo chuẩn MIME (RFC 2045) khi truyền qua SMTP / IMAP.

**Phạm vi:**

- Nhiều người dùng dùng chung hệ thống, tự đăng ký tài khoản mới (nếu Admin đang bật tính năng đăng ký).
- **1 User — Nhiều Gmail:** một tài khoản hệ thống liên kết được nhiều tài khoản Gmail.
- Role: `ADMIN` và `USER`.
- Gửi mail qua SMTP, nhận / đọc mail qua IMAP, tải và mở file đính kèm trên máy user.

**Nguyên tắc thiết kế quan trọng ở v2:**

- **Không cache mail:** Backend không lưu nội dung mail, tiêu đề mail hay file đính kèm vào DB. Mỗi lần user xem hộp thư, xem chi tiết hoặc tải đính kèm, Backend đều fetch trực tiếp từ Gmail rồi trả kết quả và đóng kết nối. DB chỉ lưu tài khoản hệ thống, thông tin liên kết Gmail (đã mã hoá), cấu hình hệ thống và nhật ký.
- **Client không kết nối trực tiếp Gmail:** mọi kết nối Gmail đều do Backend thực hiện. Client chỉ gọi REST API của Backend.

---

## 2. Kiến trúc chi tiết

### 2.1 Backend — Spring Boot

```
com.mailapp.server
├── config/
│   ├── SecurityConfig.java        # Spring Security, JWT filter, phân quyền ADMIN / USER
│   ├── SslConfig.java             # HTTPS keystore PKCS12
│   └── GoogleOAuthConfig.java     # client-id / client-secret / redirect-uri của Google
├── controller/
│   ├── AuthController.java        # register / login / me
│   ├── GmailAccountController.java
│   ├── OAuth2Controller.java      # tạo link ủy quyền + callback từ Google
│   ├── MailController.java        # list / detail / send / attachment / folders
│   └── admin/
│       ├── AdminUserController.java
│       ├── AdminSettingController.java
│       └── AdminStatsController.java
├── service/
│   ├── AuthService.java
│   ├── SystemSettingService.java  # đọc / ghi cấu hình, kiểm tra cờ bật-tắt tính năng
│   ├── GmailAccountService.java
│   ├── OAuth2Service.java         # đổi code -> token, refresh access token, thu hồi token
│   ├── MailConnectionFactory.java # tạo Session / Store / Transport theo loại liên kết (OAuth2 | App Password)
│   ├── ImapFetchService.java      # fetch list / detail / attachment trực tiếp từ Gmail, không cache
│   ├── SmtpSendService.java       # gửi mail SMTP
│   └── AdminService.java
├── repository/                    # User, GmailAccount, SystemSetting, AuditLog
├── model/entity/
├── dto/
└── security/                      # JwtProvider, JwtFilter, AesCipher (mã hoá credential Gmail)
```

**Công nghệ:** Spring Boot 3.x (Java 17+), Spring Security + JWT, Spring Data JPA / Hibernate, MySQL 8, Jakarta Mail (Angus Mail), Google API Client for Java (chỉ cho OAuth2 token), Lombok, Bean Validation.

**HTTPS cho Backend:**

```bash
keytool -genkeypair -alias mailapp -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 -validity 365
# application.yml: server.port=8443, server.ssl.enabled=true, key-store=classpath:keystore.p12
```

### 2.2 Client — Java Swing thuần

**Swing thuần** nghĩa là: chỉ dùng thư viện chuẩn của JDK (`javax.swing`, `java.awt`), **không** dùng FlatLaf hay bất kỳ thư viện giao diện ngoài nào. Giao diện dùng Look and Feel hệ thống của Windows (`UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())`) để trông native trên Windows.

```
com.mailapp.client
├── Main.java                      # set system Look & Feel, mở LoginFrame
├── api/
│   ├── ApiClient.java             # java.net.http.HttpClient (TLS), gắn JWT, xử lý lỗi
│   ├── AuthApi.java
│   ├── MailApi.java
│   └── AdminApi.java
├── ui/
│   ├── LoginFrame.java
│   ├── RegisterFrame.java         # ẩn / disable nếu Admin tắt đăng ký (hỏi API public config trước)
│   ├── MainFrame.java             # JSplitPane: trái = cây Gmail + folder, giữa = bảng mail, phải = chi tiết
│   ├── MailDetailPanel.java       # JEditorPane hiển thị HTML, JTextArea cho plain text
│   ├── ComposeDialog.java
│   ├── GmailAccountDialog.java    # chọn cách liên kết: OAuth2 | App Password
│   └── admin/
│       ├── AdminFrame.java        # hoặc tab Admin trong MainFrame, chỉ ADMIN thấy
│       ├── UserManagePanel.java
│       ├── SystemSettingPanel.java
│       └── StatsPanel.java
├── model/                         # UserDto, GmailAccountDto, MailSummary, MailDetail, ...
└── util/
    ├── FileUtil.java              # lưu file tải về + Desktop.open()
    └── SessionStore.java          # lưu JWT ở bộ nhớ / java.util.prefs.Preferences
```

**Thành phần Swing sử dụng (đều là chuẩn):** `JFrame`, `JDialog`, `JTable` (danh sách mail / user), `JTree` hoặc `JList` (danh sách Gmail + folder), `JSplitPane`, `JTabbedPane` (khu Admin), `JEditorPane`, `JTextArea / JTextField / JPasswordField`, `JFileChooser` (chọn file đính kèm khi soạn / chọn nơi lưu khi tải), `JCheckBox` (công tắc bật / tắt tính năng ở Admin), `SwingWorker` (gọi API ở luồng nền, không treo giao diện khi fetch mail).

> Vì fetch trực tiếp từ Gmail nên thao tác tải mail có thể mất vài giây. Mọi lời gọi API trên Client **bắt buộc** chạy bằng `SwingWorker`, hiển thị thanh trạng thái / con trỏ chờ, không gọi mạng trên Event Dispatch Thread.

---

## 3. Liên kết Gmail — User tự chọn OAuth2 hoặc App Password

Khi thêm một Gmail mới, user chọn **một trong hai cách**. Cả hai đều lưu vào cùng bảng `gmail_accounts` với trường `auth_type`, và cả hai đều được Backend tôn trọng như nhau ở các thao tác fetch / send sau đó. Admin có quyền bật / tắt từng cách (xem mục 6).

### 3.1 Cách A — App Password

Điều kiện: Gmail đã bật xác thực 2 bước (2FA), user tạo App Password 16 ký tự trong Google Account.

```
User nhập email + App Password trên Swing
  -> POST /api/gmail-accounts/app-password  (HTTPS + JWT)
  -> Backend thử kết nối IMAP (imaps, port 993) ngay để kiểm tra thông tin đúng / sai
  -> Đúng: mã hoá App Password bằng AES-256-GCM, lưu DB, auth_type = APP_PASSWORD
  -> Sai: trả lỗi, không lưu
```

### 3.2 Cách B — OAuth2 (Google Sign-In + Gmail scope)

Điều kiện hệ thống: Admin / người vận hành đã đăng ký Google Cloud Project, có `client_id`, `client_secret`, và redirect URI trỏ về Backend, ví dụ `https://localhost:8443/api/oauth2/callback`. Scope yêu cầu: đọc và gửi mail (khai báo rõ cho user ở màn hình đồng ý của Google).

```
User bấm "Liên kết bằng Google" trên Swing
  -> GET /api/oauth2/authorize-url?state=...  (HTTPS + JWT)
     Backend tạo URL ủy quyền của Google, gắn state gắn với user đang đăng nhập
  -> Client mở trình duyệt mặc định: Desktop.getDesktop().browse(url)
  -> User đăng nhập Google + đồng ý cấp quyền ngay trên trình duyệt
  -> Google redirect về Backend: GET /api/oauth2/callback?code=...&state=...
  -> Backend đổi code lấy access_token + refresh_token
     Chỉ lưu refresh_token (đã mã hoá AES-256-GCM), auth_type = OAUTH2
     Trang callback trả về HTML đơn giản "Liên kết thành công, quay lại ứng dụng"
  -> Client poll / refresh danh sách Gmail -> thấy Gmail mới đã liên kết
```

Khi cần dùng Gmail OAuth2, Backend tự dùng `refresh_token` để lấy `access_token` mới (hạn ngắn, khoảng 1 giờ) rồi đăng nhập IMAP / SMTP bằng cơ chế `XOAUTH2`. User không phải đăng nhập lại trừ khi thu hồi quyền hoặc refresh token bị Google thu hồi — khi đó tài khoản được đánh dấu `NEEDS_REAUTH` và user liên kết lại.

### 3.3 Dùng chung cho cả hai cách

`MailConnectionFactory` là nơi duy nhất quyết định cách xác thực:

| | App Password | OAuth2 |
|---|---|---|
| IMAP | `store.connect("imap.gmail.com", email, appPassword)` | SASL `XOAUTH2` với access token |
| SMTP | `mail.smtp.auth=true`, Authenticator email / appPassword | `mail.smtp.auth.mechanisms=XOAUTH2` |
| TLS | IMAPS 993, SMTP STARTTLS 587 bắt buộc | Giống hệt |
| Dữ liệu lưu DB | App Password (mã hoá) | Refresh token (mã hoá), email, thời hạn token gần nhất |
| Thu hồi | User xoá liên kết trong app | Xoá liên kết + Backend gọi endpoint revoke của Google |

Khi user **xoá liên kết Gmail**: xoá bản ghi credential khỏi DB. Nếu là OAuth2, gọi thêm API thu hồi token của Google. Không có mail nào bị xoá vì hệ thống không lưu mail.

---

## 4. Luồng nghiệp vụ chính

### 4.1 Gửi mail — SMTP

```
User soạn mail trên ComposeDialog, chọn From = một Gmail đã liên kết
  -> POST /api/mails/send  (HTTPS + JWT, multipart: to, cc, bcc, subject, body, files[])
  -> Backend kiểm tra: Gmail đó thuộc về user, tính năng gửi mail đang bật
  -> MailConnectionFactory tạo Session SMTP: smtp.gmail.com:587
     mail.smtp.starttls.enable=true, mail.smtp.starttls.required=true
  -> Dựng MimeMessage: phần text / html và mỗi file đính kèm là một MimeBodyPart
     được encode Base64 theo chuẩn MIME khi truyền đi
  -> Transport.send() -> Gmail -> người nhận
  -> Backend ghi audit log "SEND_MAIL" (chỉ ghi metadata: ai gửi, từ Gmail nào, tới ai, lúc nào
     — không lưu nội dung). Client báo "Gửi thành công"
```

Mail đã gửi nằm trong thư mục Sent **trên Gmail** (do Gmail / IMAP quản lý), hệ thống không tự lưu bản sao.

### 4.2 Nhận / xem mail mới — Fetch trực tiếp mỗi lần (không cache)

Không có scheduler, không có đồng bộ nền, không có bảng cache mail.

```
User mở app / bấm Làm mới / chọn một Gmail + folder (INBOX, Sent, Drafts, Spam, Trash, All Mail)
  -> GET /api/mails?gmailAccountId={id}&folder=INBOX&page=0&size=20  (HTTPS + JWT)
  -> Backend:
     1. Kiểm tra Gmail thuộc về user
     2. Mở Store IMAPS (TLS) theo auth_type của Gmail đó
     3. Mở folder ở chế độ READ_ONLY, lấy tổng số mail
     4. Lấy đúng trang cần: các mail mới nhất trước, dùng FetchProfile
        (ENVELOPE, FLAGS, CONTENT_INFO) để chỉ tải tiêu đề / cờ, không tải toàn bộ nội dung
     5. Map sang MailSummary: uid, from, subject, date, isRead (cờ \Seen trên Gmail),
        hasAttachment, snippet ngắn nếu lấy được
     6. Đóng folder + store, trả JSON cho Client
  -> Client hiển thị lên JTable: mail chưa đọc in đậm, có icon đính kèm nếu hasAttachment

Xem chi tiết một mail:
  -> GET /api/mails/{gmailAccountId}/{folder}/{uid}
  -> Backend mở lại IMAP, tìm message theo UID, parse toàn bộ:
     from / to / cc, subject, date, body text, body html, danh sách đính kèm (tên, loại, kích thước)
  -> Nếu folder mở READ_WRITE: đặt cờ \Seen trên Gmail (đánh dấu đã đọc tại nguồn)
  -> Trả MailDetail (không kèm nội dung file đính kèm — chỉ metadata). Đóng kết nối.

Tải mail mới: user thấy mail mới bằng cách bấm Làm mới hoặc chuyển folder / Gmail.
Client có thể có nút / phím tắt Refresh, không tự poll nền ở v1 (ghi ở mục hướng phát triển nếu muốn).
```

**Hệ quả phải chấp nhận và trình bày được khi bảo vệ:**

- Tốc độ phụ thuộc mạng và Gmail, chậm hơn có cache. Đổi lại: dữ liệu luôn mới nhất, đúng trạng thái trên Gmail (đã đọc / chưa đọc / đã xoá ở nơi khác là thấy ngay), không tốn DB lưu mail, không lo đồng bộ sai.
- Mỗi thao tác là một kết nối IMAP ngắn (mở → fetch → đóng). Phải đặt timeout rõ ràng và báo lỗi thân thiện khi Gmail / mạng lỗi, không treo Client (nhờ `SwingWorker`).
- Phân trang thực hiện **phía IMAP**: chỉ fetch đúng khoảng message của trang đó, không tải cả hộp thư.
- Tìm kiếm (nếu làm): dùng `Folder.search(SearchTerm)` của IMAP ngay tại Gmail, vẫn không cache.

### 4.3 Mở file đính kèm — Download rồi mở file mới tải

```
User mở chi tiết mail có đính kèm -> thấy danh sách file (tên, dung lượng, loại)
  -> Bấm vào một file (hoặc nút Tải / Mở)
  -> GET /api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download
  -> Backend mở IMAP, tìm message theo UID, lấy đúng MimeBodyPart thứ index,
     giải mã Base64 -> stream byte[] về Client qua HTTPS
     Kiểm tra kích thước theo cấu hình MAX_ATTACHMENT_SIZE_MB của Admin
  -> Client:
     1. Lưu file vào thư mục tải của app, mặc định:
        %USERPROFILE%\Downloads\MailApp\<gmail>\<tên file>
        (nếu trùng tên: thêm (1), (2)... không ghi đè)
     2. Tự mở file vừa tải bằng ứng dụng mặc định của Windows:
        Desktop.getDesktop().open(file);
     3. Nếu file đó đã tải rồi và chưa đổi: mở thẳng file local, không tải lại
        (Client tự nhớ theo gmail + uid + tên file + kích thước — đây là file trên máy
         của user, không phải cache phía server)
```

### 4.4 Đăng ký / Đăng nhập tài khoản hệ thống

```
Đăng ký:
  Client hỏi trước: GET /api/public/config -> { registrationEnabled: true/false, ... }
  - Nếu false: ẩn / disable nút Đăng ký, hiện thông báo "Đăng ký đang tạm đóng"
  - Nếu true: RegisterFrame hoạt động bình thường
  POST /api/auth/register { username, password, fullName, email }
  -> Backend kiểm tra lại cờ ALLOW_REGISTRATION trong DB (không tin Client)
     Tắt -> 403 REGISTRATION_DISABLED
     Bật -> tạo user role=USER, password băm BCrypt, enabled=true

Đăng nhập:
  POST /api/auth/login -> kiểm tra enabled (chưa bị Admin khoá) -> trả JWT
  Client lưu JWT, gọi GET /api/auth/me để biết role -> ADMIN thì hiện thêm khu Admin
  LƯU Ý: đăng nhập KHÔNG phụ thuộc vào cờ đăng ký. Dù Admin đã tắt đăng ký
  (xem mục 6.2), mọi tài khoản đã có vẫn đăng nhập và dùng mail bình thường —
  hệ thống lúc đó chỉ đơn giản là "đóng cửa với người mới, người cũ dùng tiếp".

Tài khoản Admin đầu tiên: seed khi khởi tạo DB (ví dụ username=admin), bắt đổi mật khẩu —
không đăng ký qua form đăng ký công khai.
```

---

## 5. Bảo mật

| Lớp | Cơ chế | Chi tiết |
|---|---|---|
| TLS lớp 1 — Gmail ↔ Backend | IMAPS 993 (SSL/TLS), SMTP 587 (STARTTLS bắt buộc) | `mail.imaps.ssl.enable=true`, `mail.smtp.starttls.required=true`, kiểm tra chứng chỉ mặc định của JVM |
| TLS lớp 2 — Backend ↔ Client | HTTPS 8443 | Keystore PKCS12 tự ký cho đồ án; Client dùng `HttpClient` với SSLContext tin keystore này |
| OAuth2 callback | HTTPS + `state` chống CSRF | State gắn với user + hết hạn ngắn, Backend kiểm tra khớp trước khi đổi token |
| Mã hoá nội dung truyền | Base64 (MIME, RFC 2045) | Áp dụng cho body / attachment qua SMTP và IMAP. **Lưu ý trình bày: Base64 là encoding, không phải encryption** — bảo mật thật đến từ TLS. Nói đúng điểm này khi bảo vệ. |
| Mật khẩu hệ thống | BCrypt | Không bao giờ lưu / log mật khẩu gốc |
| Credential Gmail | AES-256-GCM mã hoá trước khi lưu DB | Khoá lấy từ biến môi trường `MAILAPP_AES_KEY`, không hardcode, không commit lên git. Áp dụng cho cả App Password và OAuth2 refresh token |
| Access token OAuth2 | Chỉ giữ ở bộ nhớ khi dùng | Không lưu access token xuống DB; hết hạn thì dùng refresh token lấy token mới |
| Xác thực API | JWT + Spring Security | Access token hạn 24 giờ (cấu hình được), mọi API trừ `/api/auth/*`, `/api/public/config`, `/api/oauth2/callback` đều cần JWT |
| Phân quyền | Kiểm tra role tại Backend | Client ẩn nút chỉ là trải nghiệm, Backend mới là nơi chặn thật: `/api/admin/**` chỉ ADMIN |
| Quyền sở hữu Gmail | Kiểm tra mọi request mail | User chỉ fetch / gửi được bằng Gmail của chính mình (`gmail_account.user_id = user đang đăng nhập`) |
| Admin không xem được mail user | Theo thiết kế | Admin quản lý tài khoản và cấu hình, không có API đọc nội dung mail hay credential Gmail của user |

**Vì sao không dùng mật khẩu Gmail thật:** Google đã chặn đăng nhập bằng mật khẩu thường cho ứng dụng bên thứ ba ("less secure apps"). Hai cách hợp lệ còn lại là App Password và OAuth2 — đó là lý do hệ thống hỗ trợ cả hai và cho user chọn.

---

## 6. Module Admin

Admin đăng nhập bằng cùng Client Swing. Sau khi đăng nhập, nếu `role = ADMIN` thì MainFrame hiện thêm khu vực Admin (tab / cửa sổ riêng). Mọi chức năng dưới đây đều kiểm tra role ở Backend.

### 6.1 Quản lý người dùng

| Chức năng | Mô tả | API |
|---|---|---|
| Tạo tài khoản hộ | Admin tự tạo tài khoản cho một người cụ thể. Cách này **không bị chặn bởi cờ tắt đăng ký** (mục 6.2) — đăng ký công khai đóng nhưng Admin vẫn thêm người được, có kiểm soát. Ghi audit log `ADMIN_CREATE_USER` | `POST /api/admin/users` |
| Xem danh sách user | Tìm kiếm theo username / email / họ tên, lọc theo role / trạng thái, phân trang | `GET /api/admin/users?q=&role=&enabled=&page=&size=` |
| Xem chi tiết user | Thông tin cơ bản, ngày tạo, số Gmail đã liên kết (chỉ số lượng + địa chỉ Gmail, không xem credential, không xem mail) | `GET /api/admin/users/{id}` |
| Khoá / Mở khoá tài khoản | User bị khoá (`enabled=false`) không đăng nhập được, JWT cũ bị chặn ở lần gọi API tiếp theo. Không khoá được chính mình và không khoá Admin cuối cùng | `PUT /api/admin/users/{id}/lock` / `PUT /api/admin/users/{id}/unlock` |
| Đổi role | Chuyển `USER` ↔ `ADMIN`. Không tự hạ role của chính mình, không hạ Admin cuối cùng | `PUT /api/admin/users/{id}/role` |
| Đặt lại mật khẩu | Admin đặt mật khẩu tạm / buộc user đổi ở lần đăng nhập sau (`must_change_password=true`). Mật khẩu mới vẫn băm BCrypt | `PUT /api/admin/users/{id}/reset-password` |
| Xoá tài khoản | Xoá user kèm toàn bộ liên kết Gmail của user đó. Nếu là OAuth2 thì thu hồi token với Google trước khi xoá. Có hộp xác nhận trên Swing | `DELETE /api/admin/users/{id}` |

### 6.2 Đóng / mở đăng ký tài khoản mới — `ALLOW_REGISTRATION`

**Đây là tính năng riêng, nêu rõ ở đây để dễ duyệt và dễ trình bày khi bảo vệ.**

**Mục đích:** Kiểm soát số lượng người dùng của hệ thống. Ban đầu Admin **mở đăng ký** để ai cũng tự tạo tài khoản được. Khi thấy hệ thống đã có **quá nhiều user** (hoặc chỉ muốn phục vụ một nhóm người cố định), Admin **tắt đăng ký** đi — từ lúc đó hệ thống chuyển sang chế độ **chỉ cho đăng nhập, không cho đăng ký mới nữa**.

**Vị trí trong giao diện Admin (Swing):**

- Tab **Cấu hình hệ thống** → công tắc dạng `JCheckBox`: **"Cho phép đăng ký tài khoản mới"**, đặt ngay đầu tab, kèm dòng hiển thị số user hiện tại (lấy từ `GET /api/admin/stats`) để Admin nhìn con số rồi quyết định đóng / mở.
- Admin gạt tắt → bấm **Lưu** → có hiệu lực ngay với mọi Client đang mở, không cần khởi động lại Backend.

**Hành vi khi đang BẬT (`ALLOW_REGISTRATION = true`) — trạng thái mặc định:**

- Màn hình Login hiện nút **Đăng ký**, `RegisterFrame` mở và dùng bình thường.
- Người chưa có tài khoản tự đăng ký → tạo user mới với `role = USER`, `enabled = true`.

**Hành vi khi TẮT (`ALLOW_REGISTRATION = false`) — chế độ chỉ đăng nhập:**

| Đối tượng | Hành vi |
|---|---|
| Người chưa có tài khoản | Không đăng ký được nữa. Client gọi `GET /api/public/config` thấy `registrationEnabled=false` → **ẩn / vô hiệu hoá nút Đăng ký** ở Login và không mở được `RegisterFrame`, hiện thông báo: *"Hệ thống đã đóng đăng ký tài khoản mới. Vui lòng liên hệ quản trị viên nếu bạn cần tài khoản."* |
| Người cố tình gọi thẳng API | Backend kiểm tra lại cờ trong DB ở `POST /api/auth/register` → trả **403 `REGISTRATION_DISABLED`**, không tạo user. Ẩn nút ở Client chỉ là giao diện, Backend mới là nơi chặn thật |
| **User đã có tài khoản** | **Không ảnh hưởng gì.** Đăng nhập, đọc mail, gửi mail, liên kết Gmail — tất cả dùng bình thường như trước. Tắt đăng ký **không** khoá, **không** xoá, **không** đăng xuất bất kỳ tài khoản đang có nào |
| Admin | Vẫn đăng nhập và quản trị bình thường. Nếu thật sự cần thêm người trong lúc đang đóng đăng ký, Admin dùng chức năng **Tạo tài khoản hộ** ở mục 6.1 (`POST /api/admin/users`) — chỉ Admin mới làm được việc này, người ngoài thì không |

**Mở lại:** Admin gạt công tắc bật lại → đăng ký công khai hoạt động lại ngay, không cần thao tác thêm.

**Lưu trữ & kiểm tra:**

- Cờ lưu trong `system_settings` với khoá `ALLOW_REGISTRATION` (mặc định `true`), cập nhật qua `PUT /api/admin/settings`.
- Mỗi lần Admin đóng / mở đăng ký đều ghi audit log `ADMIN_CHANGE_SETTING` kèm giá trị cũ → mới, để truy vết được ai đã đóng đăng ký và lúc nào.

### 6.3 Các bật / tắt tính năng & cấu hình hệ thống khác

Admin quản lý các cờ và tham số trong bảng `system_settings`. Client và Backend **đều** phải tôn trọng các cờ này — Backend kiểm tra lại ở mỗi request liên quan, không chỉ ẩn nút ở Client.

| Khoá cấu hình | Kiểu | Mặc định | Ý nghĩa / nơi áp dụng |
|---|---|---|---|
| `ALLOW_REGISTRATION` | boolean | `true` | Đóng / mở đăng ký tài khoản mới — **chi tiết đầy đủ ở mục 6.2**. Tắt = chỉ cho đăng nhập, user cũ không bị ảnh hưởng |
| `ALLOW_OAUTH2_LINK` | boolean | `true` | Cho phép liên kết Gmail bằng OAuth2. Tắt → ẩn lựa chọn OAuth2 khi thêm Gmail |
| `ALLOW_APP_PASSWORD_LINK` | boolean | `true` | Cho phép liên kết Gmail bằng App Password. Không được tắt cả hai cách liên kết cùng lúc (Backend chặn khi lưu) |
| `ALLOW_SEND_MAIL` | boolean | `true` | Cho phép gửi mail. Tắt → disable nút Soạn / Gửi, `POST /api/mails/send` trả 403 |
| `MAINTENANCE_MODE` | boolean | `false` | Chế độ bảo trì: chỉ ADMIN đăng nhập / dùng được, USER nhận thông báo bảo trì |
| `MAX_GMAIL_PER_USER` | int | `5` | Số Gmail tối đa mỗi user được liên kết. `0` = không giới hạn (hiển thị rõ cho Admin) |
| `MAX_ATTACHMENT_SIZE_MB` | int | `25` | Kích thước tối đa một file đính kèm khi gửi và khi tải |
| `MAIL_PAGE_SIZE` | int | `20` | Số mail mặc định mỗi lần fetch (Client vẫn truyền được size trong giới hạn hợp lý) |
| `LOGIN_MAX_FAILED_ATTEMPTS` | int | `5` | (Tuỳ chọn) Số lần đăng nhập sai liên tiếp trước khi tạm khoá ngắn hạn |

Màn hình Admin — Cấu hình hệ thống: mỗi cờ boolean là một `JCheckBox`, mỗi tham số số là `JTextField` / `JSpinner`, có nút Lưu và nút Đặt lại mặc định. Lưu cấu hình ghi audit log (Admin nào, đổi khoá nào, từ giá trị nào sang giá trị nào, lúc nào).

API:

```
GET /api/public/config                  # không cần đăng nhập — chỉ trả các cờ an toàn cho Client
                                          # { registrationEnabled, oauth2LinkEnabled, appPasswordLinkEnabled, maintenanceMode }
GET /api/admin/settings                 # ADMIN — toàn bộ cấu hình
PUT /api/admin/settings                 # ADMIN — cập nhật nhiều khoá cùng lúc, có validate
```

### 6.4 Thống kê & nhật ký

| Chức năng | Mô tả | API |
|---|---|---|
| Thống kê tổng quan | Tổng số user, user đang hoạt động / bị khoá, số Admin, tổng số Gmail đã liên kết, số Gmail theo loại (OAuth2 / App Password), số lượt đăng ký hôm nay / 7 ngày | `GET /api/admin/stats` |
| Nhật ký hệ thống (audit log) | Ai làm gì, lúc nào: `LOGIN`, `LOGIN_FAILED`, `REGISTER`, `LINK_GMAIL`, `UNLINK_GMAIL`, `SEND_MAIL`, `ADMIN_LOCK_USER`, `ADMIN_CHANGE_SETTING`, ... Lọc theo user / hành động / khoảng ngày, phân trang. **Không ghi nội dung mail, mật khẩu hay token vào log** | `GET /api/admin/logs?userId=&action=&from=&to=&page=&size=` |

Admin **không có** chức năng: đọc mail của user, xem App Password / refresh token của user, gửi mail thay user. Đây là ranh giới quyền cố ý và nên nêu trong báo cáo.

---

## 7. Cơ sở dữ liệu — MySQL 8

Chỉ có 4 bảng. **Không có bảng mail, không có bảng attachment** vì fetch trực tiếp từ Gmail mỗi lần.

```sql
-- Tài khoản hệ thống
users(
  id BIGINT PK AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,          -- BCrypt
  full_name VARCHAR(100),
  email VARCHAR(100),
  role ENUM('ADMIN','USER') NOT NULL DEFAULT 'USER',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,        -- FALSE = Admin đã khoá
  must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME, updated_at DATETIME
)

-- Gmail đã liên kết: 1 user - N gmail
gmail_accounts(
  id BIGINT PK AUTO_INCREMENT,
  user_id BIGINT FK -> users(id) ON DELETE CASCADE,
  email VARCHAR(100) NOT NULL,
  display_name VARCHAR(100),
  auth_type ENUM('OAUTH2','APP_PASSWORD') NOT NULL,
  app_password_encrypted TEXT NULL,             -- AES, chỉ có khi auth_type = APP_PASSWORD
  refresh_token_encrypted TEXT NULL,            -- AES, chỉ có khi auth_type = OAUTH2
  token_expires_at DATETIME NULL,               -- thông tin phụ của OAuth2 nếu cần hiển thị
  status ENUM('ACTIVE','NEEDS_REAUTH') NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME, updated_at DATETIME,
  UNIQUE KEY uk_user_gmail (user_id, email)     -- một user không liên kết trùng một Gmail hai lần
)

-- Cấu hình hệ thống do Admin quản lý (key-value)
system_settings(
  setting_key VARCHAR(100) PK,
  setting_value VARCHAR(255) NOT NULL,
  description VARCHAR(255),
  updated_by BIGINT FK -> users(id) NULL,
  updated_at DATETIME
)

-- Nhật ký hệ thống
audit_logs(
  id BIGINT PK AUTO_INCREMENT,
  user_id BIGINT FK -> users(id) NULL,          -- người thực hiện (NULL nếu là khách / đăng ký)
  action VARCHAR(50) NOT NULL,
  detail VARCHAR(500),                          -- metadata ngắn, không chứa nội dung mail / bí mật
  ip_address VARCHAR(45),
  created_at DATETIME,
  INDEX (user_id), INDEX (action), INDEX (created_at)
)
```

Dữ liệu khởi tạo (seed): tài khoản Admin đầu tiên, và toàn bộ khoá `system_settings` với giá trị mặc định ở mục 6.2.

---

## 8. Danh sách API

```
Công khai:
  GET  /api/public/config

Xác thực:
  POST /api/auth/register                       # chặn nếu ALLOW_REGISTRATION = false
  POST /api/auth/login
  GET  /api/auth/me

User — Gmail:
  GET    /api/gmail-accounts                    # Gmail của chính mình
  POST   /api/gmail-accounts/app-password       # { email, appPassword } — test IMAP trước khi lưu
  GET    /api/oauth2/authorize-url              # lấy URL ủy quyền Google (JWT bắt buộc)
  GET    /api/oauth2/callback                   # Google gọi về — không cần JWT, kiểm tra bằng state
  DELETE /api/gmail-accounts/{id}               # xoá liên kết (+ revoke token nếu OAuth2)

User — Mail (mọi request đều fetch trực tiếp từ Gmail, không qua DB):
  GET  /api/mails/folders?gmailAccountId=                     # danh sách folder + số mail chưa đọc nếu lấy được
  GET  /api/mails?gmailAccountId=&folder=INBOX&page=0&size=20 # danh sách mail (fetch header)
  GET  /api/mails/{gmailAccountId}/{folder}/{uid}             # chi tiết một mail
  GET  /api/mails/{gmailAccountId}/{folder}/{uid}/attachments/{index}/download
  POST /api/mails/send                                        # multipart, chặn nếu ALLOW_SEND_MAIL = false

Admin — chỉ role ADMIN:
  POST   /api/admin/users                       # Admin tạo tài khoản hộ — dùng được cả khi đăng ký công khai đang đóng
  GET    /api/admin/users
  GET    /api/admin/users/{id}
  PUT    /api/admin/users/{id}/lock
  PUT    /api/admin/users/{id}/unlock
  PUT    /api/admin/users/{id}/role
  PUT    /api/admin/users/{id}/reset-password
  DELETE /api/admin/users/{id}
  GET    /api/admin/settings
  PUT    /api/admin/settings
  GET    /api/admin/stats
  GET    /api/admin/logs
```

Định dạng lỗi thống nhất: `{ timestamp, status, error, message, code }` — ví dụ `code = REGISTRATION_DISABLED`, `FEATURE_DISABLED`, `GMAIL_NEEDS_REAUTH`, để Client hiển thị thông báo đúng ý thay vì chỉ hiện mã HTTP.

---

## 9. Màn hình Swing thuần

| # | Màn hình | Thành phần chính | Ghi chú |
|---|---|---|---|
| 1 | **Login** (`LoginFrame`) | username, password, nút Đăng nhập / Đăng ký | Nút Đăng ký ẩn nếu `registrationEnabled=false`. Báo lỗi rõ khi tài khoản bị khoá / hệ thống bảo trì |
| 2 | **Register** (`RegisterFrame`) | username, password, nhập lại password, họ tên, email | Chỉ mở được khi đăng ký đang bật |
| 3 | **Main — Hộp thư** (`MainFrame`) | `JSplitPane`: trái là danh sách Gmail + folder, giữa là `JTable` mail, phải là chi tiết mail | Mail chưa đọc in đậm. Nút: Soạn mail, Làm mới (fetch lại), Quản lý Gmail, Đăng xuất. Có thanh trạng thái báo "Đang tải mail..." |
| 4 | **Chi tiết mail** (`MailDetailPanel`) | thông tin người gửi / nhận, thời gian, `JEditorPane` cho HTML hoặc `JTextArea` cho text, danh sách đính kèm dạng nút / list | Bấm đính kèm → tải về + mở file (mục 4.3) |
| 5 | **Soạn mail** (`ComposeDialog`) | From (chọn Gmail đã liên kết), To, Cc, Bcc, Subject, Body, đính kèm bằng `JFileChooser`, nút Gửi | Disable nếu Admin tắt gửi mail |
| 6 | **Quản lý Gmail** (`GmailAccountDialog`) | `JTable` các Gmail đã liên kết (email, cách liên kết, trạng thái), nút Thêm / Xoá | Khi Thêm: chọn **OAuth2** hoặc **App Password** (chỉ hiện cách đang được Admin bật). OAuth2 mở trình duyệt; App Password hiện form nhập |
| 7 | **Admin** (`AdminFrame` / tab trong MainFrame) | `JTabbedPane`: Người dùng / Cấu hình hệ thống / Thống kê / Nhật ký | Chỉ user có role ADMIN mới thấy và gọi được |

---

## 10. Lộ trình thực hiện (gợi ý)

| Giai đoạn | Nội dung |
|---|---|
| 1 | Dựng 2 project, MySQL schema + seed Admin & settings, HTTPS keystore, Auth (register / login / JWT) + kiểm tra cờ đăng ký, Login / Register trên Swing thuần |
| 2 | Liên kết Gmail bằng **App Password** trước (đơn giản hơn): thêm / xoá Gmail, fetch danh sách mail + chi tiết trực tiếp bằng IMAP, hiển thị lên Swing bằng `SwingWorker` |
| 3 | Gửi mail SMTP + đính kèm, tải và mở file đính kèm, chuyển đổi qua lại giữa nhiều Gmail và các folder |
| 4 | Bổ sung liên kết **OAuth2** (Google Cloud, authorize URL, callback, refresh token, XOAUTH2) — user chọn được cả hai cách |
| 5 | Module Admin hoàn chỉnh: quản lý user, bật / tắt tính năng & cấu hình, thống kê, audit log |
| 6 | Kiểm thử, xử lý lỗi (mất mạng, token hết hạn, Gmail sai mật khẩu), hoàn thiện báo cáo: sơ đồ kiến trúc, giải thích 2 lớp TLS, MIME / Base64, OAuth2 |

Thứ tự trên cố ý làm App Password trước OAuth2 để sớm có bản chạy được; thiết kế đã chừa sẵn `auth_type` và `MailConnectionFactory` nên thêm OAuth2 ở giai đoạn 4 không phải sửa lại phần fetch / send.

---

## 11. Giới hạn & hướng phát triển (ghi rõ trong báo cáo)

- Fetch trực tiếp mỗi lần nên tốc độ phụ thuộc Gmail và mạng; chưa có thông báo mail mới theo thời gian thực (có thể phát triển bằng IMAP IDLE hoặc Gmail API Push Notification).
- Chưa có tìm kiếm nâng cao / lọc mail offline vì không lưu mail tại hệ thống.
- Chứng chỉ HTTPS là self-signed cho môi trường học tập; triển khai thật cần chứng chỉ từ CA và tên miền thật, đặc biệt là redirect URI công khai cho OAuth2.
- OAuth2 với scope Gmail đầy đủ có thể yêu cầu Google xác minh ứng dụng (app verification) nếu dùng ngoài phạm vi test users của Google Cloud Project. Với đồ án: khai báo các tài khoản demo là test users.

---

## 12. Chờ duyệt

Bản v2 này đã áp dụng đúng 4 quyết định đã chốt và bổ sung module Admin theo yêu cầu. Sau khi duyệt, bước tiếp theo là tạo skeleton code chạy được cho cả 2 project (Backend: Spring Boot + MySQL + HTTPS + Auth; Client: Swing thuần + Login / MainFrame gọi API) theo đúng cấu trúc thư mục ở mục 2.

*Thay đổi so với v1: bỏ cache mail & scheduler & bảng mail_cache / attachments; bỏ FlatLaf → Swing thuần; App Password từ "cách duy nhất" thành một trong hai lựa chọn ngang hàng với OAuth2; Admin từ 3 API cơ bản thành module đầy đủ gồm quản lý user, bật / tắt tính năng, cấu hình hệ thống, thống kê và nhật ký.*
