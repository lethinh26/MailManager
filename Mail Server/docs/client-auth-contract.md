# Hợp đồng tích hợp API Auth, Gmail và OAuth2 cho Client

> **Dành cho:** Lê Phú Thịnh (TV1 — M03: `ApiClient` và `LoginFrame`)  
> **Endpoint:** `POST /api/auth/login` (Public — không cần gửi Authorization header)  
> **Base URL:** `https://localhost:8443`  

> **Trạng thái đối chiếu:** Contract dưới đây mô tả mã nguồn hiện tại, gồm các mở rộng tương thích của đặc tả gốc. Các test được dẫn tên để kiểm chứng; chưa xác nhận kết quả chạy trong lần cập nhật tài liệu này.

---

## 1. Cấu trúc Request & Response

### Request Body (`application/json`):
```json
{
  "username": "admin",
  "password": "<DISPOSABLE_ADMIN_PASSWORD>"
}
```

### Ràng buộc kiểm tra phía Client (trước khi gọi API):
- `username`: tên đăng nhập hoặc email (từ 3 đến 255 ký tự, không để trống); tìm username không phân biệt hoa thường, sau đó tìm email. Username khi đăng ký tối đa 64 ký tự.
- `password`: không được để trống, tối đa 72 byte UTF-8 (BCrypt).

---

### Response Thành công (`200 OK`):
```json
{
  "accessToken": "<JWT_ACCESS_TOKEN_HS256>",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "username": "admin",
    "fullName": "Quản trị viên",
    "email": "admin@mailapp.com",
    "role": "ADMIN",
    "mustChangePassword": false,
    "createdAt": "2026-11-22T18:10:00"
  }
}
```

#### Xử lý phía Client khi thành công:
1. Lưu `accessToken` vào `SessionStore` theo cơ chế phiên của Client; không ghi token vào log hoặc lưu mật khẩu thô.
2. Lưu thông tin `user` để phân quyền UI:
   - Nếu `user.role == "ADMIN"` $\rightarrow$ Hiển thị tab Quản trị (AdminFrame).
   - Nếu `user.mustChangePassword == true` $\rightarrow$ Mở dialog bắt buộc đổi mật khẩu.
3. Chuyển sang màn hình chính `MainFrame`.
4. Mọi request sau này, `ApiClient` gắn header:
   `Authorization: Bearer <accessToken>`

---

## 2. Xử lý các mã lỗi & Giao diện người dùng

Hệ thống trả về format lỗi chuẩn thống nhất:
```json
{
  "timestamp": "2026-11-21T17:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Sai tên đăng nhập hoặc mật khẩu.",
  "code": "UNAUTHORIZED",
  "errorCode": "UNAUTHORIZED",
  "path": "/api/auth/login"
}
```

| HTTP Status | Mã lỗi (`code`) | Nguyên nhân | Hành vi Client (`LoginFrame` / `ApiClient`) |
|:---:|---|---|---|
| **400** | `VALIDATION_ERROR` | Dữ liệu không hợp lệ (username quá ngắn, password rỗng) | Hiển thị dòng chữ đỏ báo lỗi tương ứng dưới ô input. |
| **401** | `UNAUTHORIZED` | Sai username hoặc sai password | Báo lỗi popup hoặc label đỏ: *"Sai tên đăng nhập hoặc mật khẩu."* Không phân biệt do sai tên hay sai pass. |
| **403** | `ACCOUNT_DISABLED` | Mật khẩu đúng nhưng tài khoản bị Admin khóa (`enabled=false`) | Báo thông báo: *"Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."* |
| **429** | `TOO_MANY_ATTEMPTS` | Đã đạt ngưỡng `LOGIN_MAX_FAILED_ATTEMPTS` (mặc định 5, cấu hình 3–20) trong cửa sổ 15 phút mặc định, theo cặp username/email và IP | **Đọc header `Retry-After: <seconds>`**: Disable nút Đăng nhập và hiển thị đồng hồ đếm ngược: *"Bạn đã thử sai quá số lần quy định. Vui lòng thử lại sau X giây."* |
| **403** | `MAINTENANCE_MODE` | Hệ thống đang bảo trì, chỉ Admin mới được đăng nhập | Hiển thị hộp thoại: *"Hệ thống đang bảo trì để nâng cấp. Vui lòng quay lại sau."* |

Các request nghiệp vụ dùng JWT USER đã phát hành cũng bị chặn 503 khi bảo trì; API công khai và ADMIN vẫn truy cập được. Token thiếu/sai/hết hạn hoặc user bị khóa trả 401 `UNAUTHORIZED` ở endpoint bảo vệ. Audit đăng nhập thành công dùng `LOGIN`, thất bại dùng `LOGIN_FAILED` (được giữ khi nghiệp vụ từ chối). Tìm audit bằng alias `LOGIN_SUCCESS` vẫn trả cả lịch sử và sự kiện mới, không ghi trùng. Login USER khi bảo trì trả 403, request JWT USER hiện có trả 503.

JWT mới có `sub=username`, `uid` dạng số và `identityVersion=2`; token đã cấp trước đây có `sub` là ID số vẫn được chấp nhận khi chữ ký, hạn dùng và ID hợp lệ. Token mới phải có username khớp tài khoản hiện tại; quyền và trạng thái tài khoản luôn lấy từ DB. Client tiếp tục coi JWT là chuỗi opaque.

---

## 3. Cách đọc Header `Retry-After` trên Java Swing

Khi nhận mã phản hồi HTTP 429:

```java
HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

if (response.statusCode() == 429) {
    Optional<String> retryAfterHeader = response.headers().firstValue("Retry-After");
    long secondsToWait = retryAfterHeader.map(Long::parseLong).orElse(60L);

    // Cập nhật UI Swing
    SwingUtilities.invokeLater(() -> {
        loginButton.setEnabled(false);
        startCountdownTimer(secondsToWait);
    });
}
```

---

## 4. Hợp đồng luồng liên kết Gmail bằng OAuth2 (Module M06 & EP-G1, EP-G3)

### Quy trình phía Client Swing:
1. **Lấy URL ủy quyền (EP-O1)**:
   - Gửi `GET /api/oauth2/authorize-url` kèm header `Authorization: Bearer <accessToken>`.
   - Không truyền intent thì mặc định `CREATE` (liên kết mới). Client đa phương thức gửi `?intent=UPSERT` để chủ động cho phép thêm/cập nhật phương thức. `?accountId=<id>` khi cấp lại quyền hoặc bổ sung phương thức cho hộp thư cụ thể mặc định UPSERT nếu không truyền intent; kết hợp accountId với CREATE trả 400. ID không thuộc user hoặc không tồn tại trả `404 ACCOUNT_NOT_FOUND`. Kiểm tra `ALLOW_OAUTH2_LINK` (403 `FEATURE_DISABLED`); CREATE kiểm tra quota lúc khởi tạo, UPSERT kiểm tra quota tạo mới khi đã xác định hộp thư ở callback. Không áp dụng quota tạo mới khi bổ sung phương thức vào hộp thư hiện có.
   - Nhận phản hồi 200, `Cache-Control: no-store`: `{ "authorizeUrl": "https://accounts.google.com/o/oauth2/v2/auth?...", "pollToken": "<opaque-token>" }`. State ngẫu nhiên, PKCE S256, TTL liên kết 5 phút, scope Gmail + openid/email/profile; không trả client secret hay refresh token.
2. **Mở trình duyệt ngoài**:
   ```java
   if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
       Desktop.getDesktop().browse(new URI(response.authorizeUrl()));
   }
   ```
3. **Người dùng đăng nhập và cấp quyền trên trình duyệt**:
   - Google chuyển hướng về máy chủ: `https://localhost:8443/api/oauth2/callback`.
   - Máy chủ hiển thị trang HTML thông báo thành công hoặc lỗi (người dùng tự đóng tab trình duyệt).
4. **Client poll đúng phiên liên kết**:
   - Sau khi mở trình duyệt, Client hiển thị dialog *"Đang chờ hoàn tất liên kết trên trình duyệt..."*.
   - Gọi `GET /api/oauth2/link/poll?pollToken=<opaque-token>` kèm Bearer JWT. Trả `202` khi `PENDING`, `200` khi `SUCCESS` hoặc `FAILED`; mọi phản hồi poll thành công có `Cache-Control: no-store`.
   - Body: `{status, accountId, outcome, code, message}`; thành công có ID hộp thư và `outcome=CREATED|METHOD_ADDED|METHOD_UPDATED`, lỗi có `code,message`, các trường không áp dụng có thể null. Token thuộc user khác trả 404 `LINK_SESSION_NOT_FOUND`; phiên hết hạn/đã tiêu thụ trả 410 `LINK_SESSION_EXPIRED`. Kết quả cuối chỉ đọc một lần; giữ pollToken trong RAM, không log.
   - Khi `SUCCESS`, tải lại EP-G1. Khi `FAILED`, hiển thị thông báo lỗi; không suy luận kết quả từ thay đổi danh sách vì cập nhật phương thức có thể giữ nguyên ID và authType. Nếu chọn nhầm email Google hoặc hộp thư đích đã bị gỡ, callback từ chối `ACCOUNT_MISMATCH`.

Callback công khai trả HTML 200 (kể cả kết quả nghiệp vụ thất bại); browser hoặc HTTP client phải chấp nhận `text/html`. Accept không tương thích trả 406 NOT_ACCEPTABLE dạng ErrorDto JSON trước khi xử lý callback theo mã nguồn đã sửa (còn cần xác minh JAR cuối; snapshot cũ quan sát 500 với Accept JSON). State được tiêu thụ một lần trước khi đổi code. Kiểm tra scope Gmail, Google UserInfo (`email_verified`) và Gmail REST `GET /gmail/v1/users/me/profile` bằng cùng access token; địa chỉ `emailAddress` phải khớp email đã xác thực (không phân biệt hoa thường). Lỗi profile trả `GOOGLE_PROFILE_ERROR`, khác địa chỉ trả `ACCOUNT_MISMATCH`, không lưu liên kết. Kiểm tra trạng thái user/feature flag/bảo trì trước lưu credential đã mã hóa. CREATE gặp hộp thư đã có hoàn tất poll `FAILED`, code `GMAIL_DUPLICATE`; chỉ UPSERT chủ động thêm/cập nhật phương thức và giữ credential phương thức còn lại. Lỗi/hủy cấp quyền cũng hoàn tất poll; giữ PKCE và kiểm tra hộp thư đích.

Đăng nhập Google trực tiếp là luồng riêng: `POST /api/auth/oauth2/google/start` (200 `{authorizeUrl,pollToken}`), `GET /api/auth/oauth2/google/poll?pollToken=...` (public, 202 PENDING/200 SUCCESS hoặc FAILED, `{status,loginResponse,code,message}`; 410 `LOGIN_SESSION_EXPIRED` khi hết hạn/đã đọc). Email Google đã xác thực dùng để tìm/hợp nhất user hiện có hoặc tạo USER nếu được phép; quota liên kết không làm chặn đăng nhập đã xác thực. Không nhập chung token poll đăng nhập và token poll liên kết.

### Endpoint Quản lý Gmail Accounts (Đa phương thức trên cùng 1 hộp thư):
- **Chuẩn hóa `email_key` dùng chung (Server & Client):** Hạ chữ thường, bỏ khoảng trắng; với `gmail.com` / `googlemail.com` bỏ dấu chấm `.` và `+tag` ở phần local, đổi `googlemail.com` $\rightarrow$ `gmail.com`; tên miền khác chỉ hạ chữ thường. Client dùng `EmailNormalizer.normalizeKey(...)` kiểm tra trước ngay trên form Wizard App Password trước khi gọi mạng.
- **EP-G1: `GET /api/gmail-accounts`** (Bearer JWT):
  Trả danh sách mỗi hộp thư 1 dòng kèm `emailKey`, `preferredMethod`, `activeMethod`, `fallbackActive`, `methods: { hasOAuth, hasAppPassword }`, `status`. Tuyệt đối không trả token hay mật khẩu.
- **EP-G2: `POST /api/gmail-accounts/app-password`** (Bearer JWT):
  Body `{email,appPassword,displayName?,intent?}`: email hợp lệ tối đa 255 ký tự, appPassword 8–64 ký tự (loại khoảng trắng trước thử IMAPS), displayName tối đa 255. Mặc định `CREATE`; hộp thư trùng `(user_id,email_key)` trả 409 `GMAIL_DUPLICATE` trước probe, đồng thời kiểm tra lại trong transaction. Client gửi `intent: "UPSERT"` khi chủ động muốn thêm/cập nhật phương thức. Thử IMAPS thật trước lưu AES-GCM. Tạo mới trả 201 `CREATED`, UPSERT thêm/cập nhật phương thức trả 200 `METHOD_ADDED/METHOD_UPDATED`, giữ ID và bí mật phương thức còn lại. Lỗi: 400 `VALIDATION_ERROR`, 403 `FEATURE_DISABLED`, 409 `GMAIL_DUPLICATE|GMAIL_LIMIT_REACHED`, 422 `GMAIL_AUTH_FAILED|IMAP_DISABLED|TWO_STEP_VERIFICATION_REQUIRED`, 502 `GMAIL_CONNECTION_ERROR`.
- **Mở rộng ngoài 27 — `PUT /api/gmail-accounts/{id}/preferred-method`** (Bearer JWT):
  Đổi phương thức chính (`OAUTH2` | `APP_PASSWORD`).
- **Mở rộng ngoài 27 — `DELETE /api/gmail-accounts/{id}/methods/{OAUTH2|APP_PASSWORD}`** (Bearer JWT):
  Gỡ một phương thức (gỡ `OAUTH2` thu hồi token best-effort; gỡ `APP_PASSWORD` xóa bí mật). Gỡ phương thức cuối cùng bắt buộc truyền `?confirmUnlink=true` (thiếu trả `409 CONFIRM_UNLINK_REQUIRED`).
- **EP-G3: `DELETE /api/gmail-accounts/{id}`** (Bearer JWT):
  Hủy toàn bộ liên kết hộp thư (thu hồi token Google best-effort nếu có OAuth2 kể cả App Password đang là phương thức chính). Trả 204 No Content; cả ID không tồn tại và ID của user khác trả 404 `NOT_FOUND`. Không xóa thư trên Gmail. Audit `UNLINK_GMAIL`; lỗi revoke ghi `REVOKE_TOKEN_FAILED` và vẫn xóa liên kết. Các endpoint theo ID (preferred-method, gỡ từng method, đổi displayName, avatar, test kết nối) dùng cùng lookup theo owner và lỗi 404 `ACCOUNT_NOT_FOUND`.
- **Trang HTML Callback OAuth2 (EP-O2):** Luôn hiển thị rõ email Google trả về (che bớt dạng `c***@gmail.com`) để phát hiện chọn nhầm tài khoản; khi thêm OAuth2 cho hộp thư đã có App Password hiển thị *"Đã thêm phương thức Google OAuth2"* (không dùng chữ *"lỗi"*).

---

## 5. Hợp đồng EP-P1: Lấy Cấu hình Hệ thống An toàn (`GET /api/public/config`)

- **Xác thực:** Công khai (Public — không cần Bearer token).
- **Mục đích:** Client gọi ngay khi khởi động ứng dụng để bật/tắt các tính năng trên giao diện Swing mà không cần hardcode.

### Response `200 OK`:
```json
{
  "registrationEnabled": false,
  "oauth2LinkEnabled": true,
  "appPasswordLinkEnabled": true,
  "maintenanceMode": false,
  "sendMailEnabled": true,
  "maxGmailPerUser": 5,
  "maxAttachmentSizeMb": 25,
  "mailPageSize": 20
}
```

---

## 6. Hợp đồng EP-A1: Đăng ký Tài khoản Mới (`POST /api/auth/register`)

- **Xác thực:** Công khai (Public).
- **Ràng buộc:**
  - Chỉ cho phép khi cấu hình `ALLOW_REGISTRATION=true` (hoặc `registrationEnabled=true`, mặc định true); body hợp lệ khi flag false trả `403 REGISTRATION_DISABLED`. Body thiếu/JSON sai có thể bị framework từ chối 400 trước khi vào nghiệp vụ, không dùng ca đó để xác minh flag.
  - Khi hệ thống đang bảo trì (`MAINTENANCE_MODE=true`) $\rightarrow$ trả về `503 MAINTENANCE_MODE`.
  - Vai trò người dùng mới tạo **luôn luôn là `USER`** (bất kể client gửi gì lên cũng bị bỏ qua để chống đặc quyền leo thang).
  - Rate limit theo IP cho lần đăng ký trùng username/email, dùng ngưỡng `LOGIN_MAX_FAILED_ATTEMPTS` và cửa sổ mặc định 15 phút; thành công xóa bộ đếm.

### Request Body:
```json
{
  "username": "cuongle",
  "password": "<DISPOSABLE_NEW_PASSWORD>",
  "fullName": "Lê Thanh Cường",
  "email": "cuong@mailapp.com"
}
```

### Response Thành công `201 Created`:
```json
{
  "id": 5,
  "username": "cuongle",
  "fullName": "Lê Thanh Cường",
  "email": "cuong@mailapp.com",
  "role": "USER",
  "mustChangePassword": false,
  "createdAt": "2026-11-22T18:10:00"
}
```

### Các mã lỗi:
- `400 VALIDATION_ERROR`: Dữ liệu không hợp lệ (username 3–64 ký tự ASCII chữ/số/gạch dưới, email hợp lệ tối đa 255 ký tự, fullName tối đa 255, password tối thiểu 8 ký tự, tối đa 72 byte UTF-8, có chữ và số).
- `403 REGISTRATION_DISABLED`: Chức năng đăng ký đang bị tắt bởi Quản trị viên.
- `409 USERNAME_TAKEN`: Tên đăng nhập đã tồn tại trong hệ thống.
- `409 EMAIL_TAKEN`: Email đã tồn tại, không phân biệt hoa thường.
- `503 MAINTENANCE_MODE`: Đang bảo trì.
- `429 TOO_MANY_ATTEMPTS`: Thao tác quá nhanh từ cùng một địa chỉ IP.

---

## 7. Hợp đồng EP-A3: Lấy Thông tin Người dùng Hiện tại (`GET /api/auth/me`)

- **Xác thực:** Yêu cầu Bearer JWT (`Authorization: Bearer <accessToken>`).
- **Mục đích:** Client làm mới thông tin tài khoản đang đăng nhập hoặc kiểm tra token còn hiệu lực.
- **Bảo mật:** Tuyệt đối không trả password hash trong response.

### Response Thành công `200 OK`:
```json
{
  "id": 1,
  "username": "admin",
  "fullName": "Quản trị viên",
  "email": "admin@mailapp.com",
  "role": "ADMIN",
  "mustChangePassword": true,
  "createdAt": "2026-11-22T18:10:00"
}
```

---

## 8. Hợp đồng EP-A4: Đổi Mật khẩu Cá nhân (`POST /api/auth/change-password`)

- **Xác thực:** Yêu cầu Bearer JWT (`Authorization: Bearer <accessToken>`).
- **Chính sách mật khẩu mới:**
  - Tối thiểu 8 ký tự, tối đa 72 byte UTF-8 (BCrypt).
  - Phải chứa ít nhất 1 chữ cái và 1 chữ số.
  - **Bắt buộc phải khác mật khẩu hiện tại.**
- **Hiệu lực cờ `must_change_password`:**
  - Khi đổi mật khẩu thành công, cờ `must_change_password` tự động chuyển về `false`.
  - Sinh và trả về JWT Token mới để Client tiếp tục sử dụng mà không cần bắt người dùng đăng nhập lại.
  - Ghi Audit Log với hành động `CHANGE_PASSWORD`.
  - Rate limit khi sai mật khẩu hiện tại: ngưỡng cấu hình chung theo User ID, cửa sổ mặc định 15 phút.

### Request Body:
```json
{
  "currentPassword": "<DISPOSABLE_CURRENT_PASSWORD>",
  "newPassword": "<DISPOSABLE_NEW_PASSWORD>"
}
```

### Response Thành công `200 OK`:
```json
{
  "message": "Đổi mật khẩu thành công.",
  "accessToken": "<NEW_JWT_ACCESS_TOKEN_HS256>",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "username": "admin",
    "fullName": "Quản trị viên",
    "email": "admin@mailapp.com",
    "role": "ADMIN",
    "mustChangePassword": false,
    "createdAt": "2026-11-22T18:10:00"
  }
}
```

### Mã lỗi:
- `400 VALIDATION_ERROR`: Mật khẩu hiện tại không chính xác, mới trùng cũ, yếu hoặc vượt giới hạn BCrypt.
- `401 UNAUTHORIZED`, `429 TOO_MANY_ATTEMPTS` (Retry-After), `503 MAINTENANCE_MODE` với USER khi bảo trì.

---

## 9. Bộ lọc Cưỡng Chế Đổi Mật Khẩu (`MustChangePasswordFilter`)

Khi tài khoản người dùng có `mustChangePassword == true` (điển hình là tài khoản `ADMIN` vừa được seed ban đầu):
- Người dùng **chỉ được phép truy cập** các endpoint sau:
  - `POST /api/auth/change-password` (để hoàn tất đổi mật khẩu)
  - `GET /api/auth/me` (để lấy thông tin cá nhân)
  - Các đường dẫn cho phép trong forced-change filter (`/api/auth/login`, `/api/auth/register`, `/api/public/**`, `/actuator/**`, tài liệu API); filter bảo mật chính vẫn chỉ cho phép actuator health/info công khai. Không coi toàn bộ `/api/auth/*` là public.
- Nếu người dùng cố gắng gọi bất kỳ endpoint nghiệp vụ nào khác (ví dụ: `GET /api/gmail-accounts`, `GET /api/oauth2/authorize-url`):
  - Hệ thống lập tức chặn lại và trả về:
    **HTTP 403 Forbidden**
    ```json
    {
      "timestamp": "2026-11-22T10:00:00",
      "status": 403,
      "error": "Forbidden",
      "message": "Bạn phải đổi mật khẩu trước khi tiếp tục sử dụng hệ thống.",
      "code": "PASSWORD_CHANGE_REQUIRED",
      "errorCode": "PASSWORD_CHANGE_REQUIRED",
      "path": "/api/gmail-accounts"
    }
    ```
- **Hành vi bắt buộc phía Client Swing:**
  Khi nhận mã lỗi `PASSWORD_CHANGE_REQUIRED` (hoặc sau khi đăng nhập nhận cờ `mustChangePassword=true`), Client lập tức hiển thị Modal Dialog bắt buộc người dùng nhập mật khẩu mới và gọi `POST /api/auth/change-password`. Sau khi đổi thành công, cập nhật `accessToken` mới và tiếp tục các thao tác khác.

---

## 10. Hợp đồng API Hộp Thư Thật & Liên Kết App Password

Toàn bộ đặc tả chi tiết cho các API hộp thư thật (`GET /api/mails/folders`, `GET /api/mails`, `GET /api/mails/{gmailId}/{folder}/{uid}`, `GET .../attachments/{index}/download`, `POST /api/mails/send`, `DELETE /api/mails/...`, `PUT .../flags`), liên kết App Password (`POST /api/gmail-accounts/app-password`) và đổi tên hiển thị (`PATCH /api/gmail-accounts/{id}`) được quy định đầy đủ tại:
- [`docs/api-mail-contract.md`](api-mail-contract.md)

Các lỗi JSON do handler/filter ứng dụng tạo đều có `code`, `errorCode`, `status`, `error`, `message`, `timestamp`, `path` (URI không kèm query). Mã lỗi nội bộ là `INTERNAL_ERROR` và lỗi tài nguyên không tồn tại ở router là `NOT_FOUND`; enum ErrorCode chứa mã lỗi chuẩn. JWT ký **HS256**; token v2 có `sub=username`, `uid` dạng số, `identityVersion=2`, `username`, `role`, `jti`; token legacy numeric-sub vẫn được chấp nhận nếu ID/chữ ký/hạn dùng hợp lệ. Thời hạn mặc định 24h. Filter đọc role/enabled từ DB ở mỗi request; token cũ có thể dùng lại sau mở khóa nếu còn hạn, denylist mặc định tắt.

## 11. Bằng chứng mã nguồn và kiểm thử

Các file tương đối với `Mail Server/src/main/java/com/mailapp/`: `controller/AuthController.java`, `service/impl/AuthServiceImpl.java`, `dto/UserDto.java`, `security/LoginRateLimiter.java`, `security/JwtAuthenticationFilter.java`, `security/SecurityConfig.java`, `controller/GmailAccountController.java`, `service/GmailAccountLinkService.java`, `oauth2/OAuth2ServiceImpl.java`, `oauth2/OAuth2StateStore.java`, `controller/OAuth2Controller.java`, `controller/PublicHealthController.java`, `exception/GlobalExceptionHandler.java`.

Test source tương đối với `Mail Server/src/test/java/com/mailapp/`: `controller/AuthRegisterAndChangePasswordTest.java` (đăng ký, đăng nhập email, audit và rate limit, me/đổi mật khẩu/config); `controller/AuthControllerIntegrationTest.java` (mã lỗi 400/403/404/413/500, 405/415, malformed JSON, header); `security/JwtAuthenticationFilterTest.java#expiredCorrectlySignedJwtCannotAccessMe` (JWT đúng chữ ký nhưng hết hạn trên /me); `controller/AdminIntegrationTest.java` (JWT trong bảo trì, principal cũ không thể ghi đè reset/khóa/hạ quyền); `controller/GmailAccountControllerTest.java`, `service/GmailMultiAuthIntegrationTest.java` (ownership, multi-auth, quota đồng thời, kiểm tra bắt buộc đổi mật khẩu trong transaction); `oauth2/OAuth2ServiceTest.java`, `oauth2/OAuth2MockWebServerTest.java` (PKCE/state/poll, scope, callback đúng đích, flag, revoke, từ chối link khi cần đổi mật khẩu). Luồng Google login vẫn được cấp JWT nếu bắt buộc đổi mật khẩu, nhưng không tự liên kết Gmail trước khi đổi mật khẩu. Kết quả suite và quan sát HTTPS/Postman từng request được ghi riêng; xem `architechture/docs/backend-manual-observations.md`. Chưa coi tất cả ca đã đạt; OAuth consent Google thật và Gmail thật vẫn cần chứng cứ riêng.

### Privacy audit và session callback

LOGIN_FAILED khi identity không tồn tại chỉ ghi lý do cố định `UNKNOWN_USER`, không lưu tên đăng nhập chưa xác thực vào detail; mã lỗi 401 và response chung không đổi. Runtime log không ghi JTI hoặc raw submitted login identity. Hai suite AuthRegisterAndChangePasswordTest/AuthServiceTest reverify 21/21 sau điều chỉnh assertion privacy. Các log trước đó trong DB không được sửa/xóa bởi thay đổi này. Client phải bỏ response cũ khi session generation đã thay đổi, nhất là send, update flags, download và callback của dialog đã dispose.




## Session generation và kết quả gửi chưa xác định (vòng C)

Public config callback chỉ áp dụng với generation hiện hành; khi không lấy được config, disable send fail-closed. Không tự retry mutation thư mất response. AUDIT_UNAVAILABLE 503 là lỗi prepare trước khi thao tác thư; outcome sau SMTP submit có thể chưa rõ, giữ draft và cảnh báo riêng. Không thay quy trình login/JWT hoặc quyền xác thực. Cache file kiểm attributes và NOFOLLOW_LINKS, vẫn gắn owner/session generation.

## Vòng E — fail-closed public configuration và session generation

Nếu public config null/lỗi, LoginFrame ẩn register/OAuth; RegisterFrame khóa form và clear password; khi maintenance=true cũng không hiện các nút public register/OAuth, password login vẫn theo contract Backend. Worker kiểm disposed/flag và generation phù hợp trước submit/callback. Compose giữ generation lúc mở, kiểm lại sau modal chooser/confirmation; không gửi khi account không hợp lệ/config unavailable/phiên đã đổi. Thiếu xác nhận status SENT hoặc messageId hợp lệ được xử lý unknown, giữ draft và không auto resend. Không bypass RBAC/JWT. Regression Client60/60; native E1 dừng trước input, không suy manual.

## Vòng G3 — kết quả hiện hành

Manager và Compose chuyển HTTP401 về MainFrame; clear session/token trước modal login hết hạn. Các worker kiểm generation/disposed, không tự gửi lại mutation; manual session expiry vẫn OPEN.

## Vòng G4 — kết quả hiện hành

G4: Enter chỉ mở detail khi một row; detail delete/cleanup lấy đúng UID đang hiển thị và cùng UIDVALIDITY/lựa chọn, không biến toolbar thành batch. Context/keyboard batch vẫn explicit. Auto-clean dùng cùng requiresReauthBeforeMail cho dual-auth. AdminFrame read workers kiểm generation/disposed; mutation kiểm ADMIN/session trước request, sau confirm và response; single-flight user mutation, recheck target, không publish/copy temp password ở session cũ, clear field khi đóng, không close/cancel create khi pending. Settings guard trước/sau confirm. MailboxRegressionTest 11/11 (detail target regression), SwingFeatureAcceptanceTest 14/14 (late user rows sau dispose); không fake session, không thao tác clipboard/credential trực tiếp. Backend vẫn là authority; guard không thu hồi request đã gửi.

Không thay H/JWT gate hoặc thao tác credential; native expiry/forced-change còn OPEN.

## G5 — 26/11/2026

G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu.

G5: GmailManagerDialog dùng FeatureAdmission ticket, kiểm sau GET và trước link dispatch; off/on không hồi sinh submit cũ, config null fail-closed, lỗi cũ không ghi đè trạng thái mới; re-enable theo config mới. Giữ clear password, session/wizard guards; không có liên kết Gmail live hay native input mới. SwingFeatureAcceptanceTest 16/16 tự động.

Ticket là guard phía Client, không khóa atomic với request; không thu hồi request đã dispatch. Không thêm HTTP contract, không đổi Backend/bootstrap/DB schema hay live preparation. Không lặp thao tác credential bị gate, không giả session/token, không yêu cầu người khác làm thay.

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

## G6 — 26/11/2026

G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

Không thay Backend/JAR, bootstrap, schema, API contract, permission, JWT/RBAC/PKCE/TLS/UIDVALIDITY/audit/recovery. Guard Client không thu hồi request đã dispatch hoặc consent remote. Không retry hành vi credential bị chặn, không đổi công cụ, worker, fake session/token, private state hoặc yêu cầu người khác làm thay. Native desktop vẫn thiếu điều kiện xác nhận; không chạy lại C/D2/E1. Không có live/human sign-off mới.

Evidence G6: Mail Client/build/acceptance/client-round-G6-suite-summary.json; 3 suite: GmailMultiAuthClientTest 5/5, MailboxRegressionTest 13/13, SwingFeatureAcceptanceTest 16/16, tổng 34/34, 0 skip/fail/error. gradlew.bat test --tests *MailboxRegressionTest --tests *SwingFeatureAcceptanceTest --tests *GmailMultiAuthClientTest --max-workers=1 --no-daemon --console=plain --offline, BUILD SUCCESSFUL. Hai test mới; không cộng 32 test rerun vào lũy kế. E2E/MySQL G5, Newman/OAuth loopback/BE G4 kế thừa; không tạo fixture server mới. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A.
