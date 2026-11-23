# Hợp đồng tích hợp API Đăng nhập (EP-A2) cho Client

> **Dành cho:** Lê Phú Thịnh (TV1 — M03: `ApiClient` và `LoginFrame`)  
> **Endpoint:** `POST /api/auth/login` (Public — không cần gửi Authorization header)  
> **Base URL:** `https://localhost:8443`  

---

## 1. Cấu trúc Request & Response

### Request Body (`application/json`):
```json
{
  "username": "admin",
  "password": "AdminPassword123!"
}
```

### Ràng buộc kiểm tra phía Client (trước khi gọi API):
- `username`: từ 3 đến 50 ký tự, không được để trống hoặc toàn khoảng trắng.
- `password`: không được để trống, tối đa 128 ký tự.

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
    "mustChangePassword": false
  }
}
```

#### Xử lý phía Client khi thành công:
1. Lưu `accessToken` vào `SessionStore` (Preferences hoặc bộ nhớ ram).
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
  "code": "INVALID_CREDENTIALS"
}
```

| HTTP Status | Mã lỗi (`code`) | Nguyên nhân | Hành vi Client (`LoginFrame` / `ApiClient`) |
|:---:|---|---|---|
| **400** | `VALIDATION_ERROR` | Dữ liệu không hợp lệ (username quá ngắn, password rỗng) | Hiển thị dòng chữ đỏ báo lỗi tương ứng dưới ô input. |
| **401** | `INVALID_CREDENTIALS` | Sai username hoặc sai password | Báo lỗi popup hoặc label đỏ: *"Sai tên đăng nhập hoặc mật khẩu."* Không phân biệt do sai tên hay sai pass. |
| **403** | `ACCOUNT_LOCKED` | Mật khẩu đúng nhưng tài khoản bị Admin khóa (`enabled=false`) | Báo thông báo: *"Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."* |
| **429** | `TOO_MANY_ATTEMPTS` | Nhập sai quá 5 lần / 15 phút (bị rate limit) | **Đọc header `Retry-After: <seconds>`**: Disable nút Đăng nhập và hiển thị đồng hồ đếm ngược: *"Bạn đã thử sai quá số lần quy định. Vui lòng thử lại sau X giây."* |
| **503** | `MAINTENANCE_MODE` | Hệ thống đang bảo trì, chỉ Admin mới được đăng nhập | Hiển thị hộp thoại: *"Hệ thống đang bảo trì để nâng cấp. Vui lòng quay lại sau."* |

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
   - Nhận phản hồi: `{ "authorizeUrl": "https://accounts.google.com/o/oauth2/v2/auth?..." }`.
2. **Mở trình duyệt ngoài**:
   ```java
   if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
       Desktop.getDesktop().browse(new URI(response.authorizeUrl()));
   }
   ```
3. **Người dùng đăng nhập và cấp quyền trên trình duyệt**:
   - Google chuyển hướng về máy chủ: `https://localhost:8443/api/oauth2/callback`.
   - Máy chủ hiển thị trang HTML thông báo thành công hoặc lỗi (người dùng tự đóng tab trình duyệt).
4. **Client poll danh sách tài khoản (EP-G1)**:
   - Sau khi mở trình duyệt, Client hiển thị dialog *"Đang chờ hoàn tất liên kết trên trình duyệt..."*.
   - Client định kỳ gọi `GET /api/gmail-accounts` mỗi 2 - 3 giây (tối đa 120 giây).
   - Khi danh sách tài khoản xuất hiện Gmail mới, đóng dialog và cập nhật danh sách hòm thư trên UI.

### Endpoint Quản lý Gmail Accounts (Đa phương thức trên cùng 1 hộp thư):
- **Chuẩn hóa `email_key` dùng chung (Server & Client):** Hạ chữ thường, bỏ khoảng trắng; với `gmail.com` / `googlemail.com` bỏ dấu chấm `.` và `+tag` ở phần local, đổi `googlemail.com` $\rightarrow$ `gmail.com`; tên miền khác chỉ hạ chữ thường. Client dùng `EmailNormalizer.normalizeKey(...)` kiểm tra trước ngay trên form Wizard App Password trước khi gọi mạng.
- **EP-G1: `GET /api/gmail-accounts`** (Bearer JWT):
  Trả danh sách mỗi hộp thư 1 dòng kèm `emailKey`, `preferredMethod`, `activeMethod`, `fallbackActive`, `methods: { hasOAuth, hasAppPassword }`, `status`. Tuyệt đối không trả token hay mật khẩu.
- **EP-G2: `POST /api/gmail-accounts/app-password`** (Bearer JWT):
  Upsert theo `(user_id, email_key)`. Nếu đã có Google OAuth2, giữ nguyên `id`, `displayName` và OAuth2 token; trả `outcome = METHOD_ADDED` kèm `message` tiếng Việt (không báo lỗi trùng).
- **Mở rộng ngoài 27 — `PUT /api/gmail-accounts/{id}/preferred-method`** (Bearer JWT):
  Đổi phương thức chính (`OAUTH2` | `APP_PASSWORD`).
- **Mở rộng ngoài 27 — `DELETE /api/gmail-accounts/{id}/methods/{OAUTH2|APP_PASSWORD}`** (Bearer JWT):
  Gỡ một phương thức (gỡ `OAUTH2` thu hồi token best-effort; gỡ `APP_PASSWORD` xóa bí mật). Gỡ phương thức cuối cùng bắt buộc truyền `?confirmUnlink=true` (thiếu trả `409 CONFIRM_UNLINK_REQUIRED`).
- **EP-G3: `DELETE /api/gmail-accounts/{id}`** (Bearer JWT):
  Hủy toàn bộ liên kết hộp thư (tự thu hồi token Google nếu có OAuth2). Trả về `200 OK`. Nếu không phải chủ sở hữu trả `403 FORBIDDEN`.
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
  "maxAttachmentSizeMb": 25
}
```

---

## 6. Hợp đồng EP-A1: Đăng ký Tài khoản Mới (`POST /api/auth/register`)

- **Xác thực:** Công khai (Public).
- **Ràng buộc:**
  - Chỉ cho phép khi cấu hình `ALLOW_REGISTRATION=true` (hoặc `registrationEnabled=true`). Mặc định `false` $\rightarrow$ trả về `403 REGISTRATION_DISABLED`.
  - Khi hệ thống đang bảo trì (`MAINTENANCE_MODE=true`) $\rightarrow$ trả về `503 MAINTENANCE_MODE`.
  - Vai trò người dùng mới tạo **luôn luôn là `USER`** (bất kể client gửi gì lên cũng bị bỏ qua để chống đặc quyền leo thang).
  - Rate limit theo IP: tối đa 5 lần / phút.

### Request Body:
```json
{
  "username": "cuongle",
  "password": "Password123!",
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
  "mustChangePassword": false
}
```

### Các mã lỗi:
- `400 VALIDATION_ERROR`: Dữ liệu không hợp lệ (username 3-50 ký tự, email đúng format, password >= 8 ký tự kèm chữ và số).
- `403 REGISTRATION_DISABLED`: Chức năng đăng ký đang bị tắt bởi Quản trị viên.
- `409 USERNAME_TAKEN`: Tên đăng nhập đã tồn tại trong hệ thống.
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
  "mustChangePassword": true
}
```

---

## 8. Hợp đồng EP-A4: Đổi Mật khẩu Cá nhân (`POST /api/auth/change-password`)

- **Xác thực:** Yêu cầu Bearer JWT (`Authorization: Bearer <accessToken>`).
- **Chính sách mật khẩu mới:**
  - Tối thiểu 8 ký tự, tối đa 128 ký tự.
  - Phải chứa ít nhất 1 chữ cái và 1 chữ số.
  - **Bắt buộc phải khác mật khẩu hiện tại.**
- **Hiệu lực cờ `must_change_password`:**
  - Khi đổi mật khẩu thành công, cờ `must_change_password` tự động chuyển về `false`.
  - Sinh và trả về JWT Token mới để Client tiếp tục sử dụng mà không cần bắt người dùng đăng nhập lại.
  - Ghi Audit Log với hành động `CHANGE_PASSWORD`.
  - Rate limit: 5 lần / phút theo User ID.

### Request Body:
```json
{
  "currentPassword": "AdminPassword123!",
  "newPassword": "NewAdminPassword456!"
}
```

### Response Thành công `200 OK`:
```json
{
  "message": "Đổi mật khẩu thành công.",
  "accessToken": "<NEW_JWT_ACCESS_TOKEN_HS256>",
  "tokenType": "Bearer",
  "expiresIn": 86400
}
```

### Mã lỗi:
- `400 INVALID_CREDENTIALS`: Mật khẩu hiện tại không chính xác.
- `400 PASSWORD_POLICY_VIOLATION`: Mật khẩu mới trùng mật khẩu cũ hoặc không đủ độ mạnh.

---

## 9. Bộ lọc Cưỡng Chế Đổi Mật Khẩu (`MustChangePasswordFilter`)

Khi tài khoản người dùng có `mustChangePassword == true` (điển hình là tài khoản `ADMIN` vừa được seed ban đầu):
- Người dùng **chỉ được phép truy cập** các endpoint sau:
  - `POST /api/auth/change-password` (để hoàn tất đổi mật khẩu)
  - `GET /api/auth/me` (để lấy thông tin cá nhân)
  - Các endpoint công khai (`/api/auth/login`, `/api/public/**`, `/actuator/**`)
- Nếu người dùng cố gắng gọi bất kỳ endpoint nghiệp vụ nào khác (ví dụ: `GET /api/gmail-accounts`, `GET /api/oauth2/authorize-url`):
  - Hệ thống lập tức chặn lại và trả về:
    **HTTP 403 Forbidden**
    ```json
    {
      "timestamp": "2026-11-22T10:00:00",
      "status": 403,
      "error": "Forbidden",
      "message": "Bạn phải đổi mật khẩu trước khi tiếp tục sử dụng hệ thống.",
      "code": "PASSWORD_CHANGE_REQUIRED"
    }
    ```
- **Hành vi bắt buộc phía Client Swing:**
  Khi nhận mã lỗi `PASSWORD_CHANGE_REQUIRED` (hoặc sau khi đăng nhập nhận cờ `mustChangePassword=true`), Client lập tức hiển thị Modal Dialog bắt buộc người dùng nhập mật khẩu mới và gọi `POST /api/auth/change-password`. Sau khi đổi thành công, cập nhật `accessToken` mới và tiếp tục các thao tác khác.

---

## 10. Hợp đồng API Hộp Thư Thật & Liên Kết App Password

Toàn bộ đặc tả chi tiết cho các API hộp thư thật (`GET /api/mails/folders`, `GET /api/mails`, `GET /api/mails/{gmailId}/{folder}/{uid}`, `GET .../attachments/{index}/download`, `POST /api/mails/send`, `DELETE /api/mails/...`, `PUT .../flags`), liên kết App Password (`POST /api/gmail-accounts/app-password`) và đổi tên hiển thị (`PATCH /api/gmail-accounts/{id}`) được quy định đầy đủ tại:
- [`docs/api-mail-contract.md`](file:///c:/Users/LE%20THANH%20CUONG/OneDrive/ptit/N3_HK1/T6_laptrinhMang/FINAL_PROJECT_MAIL/MailManager/Mail%20Server/docs/api-mail-contract.md)

Mọi mã lỗi JSON từ Server đều đồng thời trả hai trường `"code"` và `"errorCode"` (kèm `"status"`, `"error"`, `"message"`, `"timestamp"`) và thuật toán ký JWT được cố định là **`HS256`** (`{"alg":"HS256"}`).



