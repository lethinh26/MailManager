# Hợp Đồng API Quản Trị Hệ Thống (Admin Module — EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2)

> **Base URL:** `https://localhost:8443`  
> **Xác thực bắt buộc:** `Authorization: Bearer <JWT>` với vai trò `ROLE_ADMIN`.  
> - Không có token / token hết hạn / tài khoản bị khóa → `401 UNAUTHORIZED`.  
> - Có token hợp lệ nhưng `role != ADMIN` → `403 FORBIDDEN`.  
> **Ranh giới bảo mật tuyệt đối:** Module Admin **KHÔNG** cung cấp bất kỳ API nào để đọc nội dung email, tải tệp đính kèm, hoặc xem mật khẩu / OAuth2 token của người dùng.

---

## 1. Quy ước lỗi chung & phân trang

Mọi phản hồi lỗi JSON đều chứa đồng thời `"code"` và `"errorCode"`:

```json
{
  "timestamp": "2026-11-22T18:10:00",
  "status": 409,
  "error": "Conflict",
  "message": "Không thể thực hiện thao tác lên Quản trị viên đang hoạt động cuối cùng của hệ thống.",
  "code": "LAST_ADMIN",
  "errorCode": "LAST_ADMIN"
}
```

Các mã lỗi đặc thù nhóm Admin:
- `400 VALIDATION_ERROR`: Dữ liệu đầu vào sai định dạng, vượt ngưỡng cấu hình, hoặc cố tắt đồng thời cả `ALLOW_OAUTH2_LINK` và `ALLOW_APP_PASSWORD_LINK`.
- `400 SELF_ACTION_FORBIDDEN`: Admin cố tự khóa, tự hạ quyền, hoặc tự xóa chính mình.
- `404 USER_NOT_FOUND`: Không tìm thấy người dùng theo `id`.
- `409 USERNAME_TAKEN`: Trùng tên đăng nhập khi tạo người dùng mới.
- `409 LAST_ADMIN`: Cố khóa, hạ quyền, hoặc xóa tài khoản `ADMIN` đang hoạt động cuối cùng.

Định dạng phân trang thống nhất:
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

---

## 2. Nhóm Quản Lý Người Dùng (`EP-AD1` .. `EP-AD8`)

### EP-AD1: `POST /api/admin/users`
Tạo tài khoản thủ công (chạy được cả khi `ALLOW_REGISTRATION=false`). Luôn đặt `mustChangePassword = true`.
- **Request:**
```json
{
  "username": "new_member",
  "fullName": "Nguyễn Văn Mới",
  "email": "moi@ptit.edu.vn",
  "role": "USER",
  "password": null
}
```
*(Nếu `password` để trống/null, Server tự sinh mật khẩu tạm ngẫu nhiên an toàn bằng `SecureRandom` và trả **MỘT LẦN** trong `temporaryPassword` kèm header `Cache-Control: no-store`.)*
- **Response `201 Created`:**
```json
{
  "id": 15,
  "username": "new_member",
  "fullName": "Nguyễn Văn Mới",
  "email": "moi@ptit.edu.vn",
  "role": "USER",
  "enabled": true,
  "mustChangePassword": true,
  "gmailCount": 0,
  "temporaryPassword": "<ONE_TIME_GENERATED_PASSWORD>",
  "createdAt": "2026-11-22T18:10:00",
  "updatedAt": "2026-11-22T18:10:00"
}
```
- **Audit:** `ADMIN_CREATE_USER` (không ghi mật khẩu).

### EP-AD2: `GET /api/admin/users`
Liệt kê, tìm kiếm, lọc, sắp xếp và phân trang người dùng.
- **Query params:**
  - `q` hoặc `username` (tùy chọn): tìm không phân biệt hoa thường trên `username`, `fullName`, `email`.
  - `role`: `ADMIN` hoặc `USER`.
  - `enabled`: `true` hoặc `false`.
  - `page` (mặc định `0`), `size` (mặc định `20`, tối đa `100`).
  - `sortBy`: `id`, `username`, `role`, `enabled`, `createdAt` (mặc định `createdAt`).
  - `sortDir`: `asc` hoặc `desc` (mặc định `desc`).
- **Response `200 OK`:** `PageResponse<AdminUserSummary>`.

### EP-AD3: `GET /api/admin/users/{id}`
Chi tiết 1 người dùng kèm `gmailCount` và danh sách tài khoản Gmail đã liên kết (chỉ metadata: `id`, `email`, `displayName`, `authType`, `status`, `createdAt`, `lastVerifiedAt` — **KHÔNG** chứa token hay App Password).

### EP-AD4: `PUT /api/admin/users/{id}/lock`
Khóa tài khoản (`enabled = false`).
- Chặn tự khóa (`400 SELF_ACTION_FORBIDDEN`) và khóa ADMIN cuối cùng (`409 LAST_ADMIN`).
- JWT hiện hành của user bị vô hiệu ngay lập tức ở request kế tiếp (vì `JwtAuthenticationFilter` kiểm tra `user.isEnabled()` trên từng request).
- **Audit:** `ADMIN_LOCK_USER`.

### EP-AD5: `PUT /api/admin/users/{id}/unlock`
Mở khóa tài khoản (`enabled = true`). **Audit:** `ADMIN_UNLOCK_USER`.

### EP-AD6: `PUT /api/admin/users/{id}/role`
Đổi vai trò (`{ "role": "ADMIN" | "USER" }`).
- Chặn tự hạ quyền (`400 SELF_ACTION_FORBIDDEN`) và hạ quyền ADMIN cuối cùng (`409 LAST_ADMIN`).
- **Audit:** `ADMIN_CHANGE_ROLE` (ghi rõ vai trò cũ → mới).

### EP-AD7: `PUT /api/admin/users/{id}/reset-password`
Đặt lại mật khẩu bằng mật khẩu tạm sinh ngẫu nhiên an toàn (`SecureRandom`, 14 ký tự gồm chữ hoa, chữ thường, chữ số, ký tự đặc biệt), bật `mustChangePassword = true`.
- Trả mật khẩu tạm **MỘT LẦN DUY NHẤT** kèm header `Cache-Control: no-store`:
```json
{
  "userId": 15,
  "username": "new_member",
  "temporaryPassword": "<ONE_TIME_TEMP_PASSWORD>",
  "mustChangePassword": true,
  "message": "Đã đặt lại mật khẩu tạm thành công. Hãy sao chép và gửi cho người dùng; mật khẩu này chỉ hiển thị một lần."
}
```
- **Audit:** `ADMIN_RESET_PASSWORD` (không ghi mật khẩu).

### EP-AD8: `DELETE /api/admin/users/{id}`
Xóa người dùng và xóa cascade toàn bộ liên kết Gmail (với tài khoản `OAUTH2`, gọi `OAuth2Service.revokeToken(...)` dạng best-effort trước khi xóa khỏi DB).
- Chặn tự xóa (`400 SELF_ACTION_FORBIDDEN`) và xóa ADMIN cuối cùng (`409 LAST_ADMIN`).
- **Response:** `204 No Content`.
- **Audit:** `ADMIN_DELETE_USER`.

---

## 3. Nhóm Cấu Hình Hệ Thống (`EP-S1`, `EP-S2`)

### EP-S1: `GET /api/admin/settings`
Trả danh sách toàn bộ 9 khóa cấu hình hệ thống:

| Khóa (`key`) | Kiểu | Khoảng hợp lệ | Mặc định |
| :--- | :---: | :---: | :---: |
| `ALLOW_REGISTRATION` | `BOOLEAN` | `true` / `false` | `true` |
| `ALLOW_OAUTH2_LINK` | `BOOLEAN` | `true` / `false` | `true` |
| `ALLOW_APP_PASSWORD_LINK` | `BOOLEAN` | `true` / `false` | `true` |
| `ALLOW_SEND_MAIL` | `BOOLEAN` | `true` / `false` | `true` |
| `MAINTENANCE_MODE` | `BOOLEAN` | `true` / `false` | `false` |
| `MAX_GMAIL_PER_USER` | `INTEGER` | `0..50` (0 = không giới hạn) | `5` |
| `MAX_ATTACHMENT_SIZE_MB` | `INTEGER` | `1..50` | `25` |
| `MAIL_PAGE_SIZE` | `INTEGER` | `10..100` | `30` |
| `LOGIN_MAX_FAILED_ATTEMPTS` | `INTEGER` | `3..20` | `5` |

### EP-S2: `PUT /api/admin/settings`
- **Request:**
```json
{
  "settings": {
    "ALLOW_REGISTRATION": "false",
    "MAX_GMAIL_PER_USER": "10"
  }
}
```
- **Ràng buộc:**
  - Validate đúng kiểu và khoảng giá trị cho từng khóa; từ chối khóa lạ (`400 VALIDATION_ERROR`).
  - **Không cho tắt đồng thời** `ALLOW_OAUTH2_LINK=false` và `ALLOW_APP_PASSWORD_LINK=false` (`400 VALIDATION_ERROR`).
  - Ghi `updated_by` là Admin đang thao tác, làm mới bộ nhớ đệm tức thì, ghi audit `ADMIN_CHANGE_SETTING` kèm giá trị cũ và mới cho từng khóa thay đổi.

---

## 4. Nhóm Thống Kê & Nhật Ký (`EP-ST1`, `EP-ST2`)

### EP-ST1: `GET /api/admin/stats`
```json
{
  "totalUsers": 12,
  "activeUsers": 10,
  "lockedUsers": 2,
  "totalAdmins": 2,
  "totalGmailAccounts": 8,
  "gmailByOAuth2": 5,
  "gmailByAppPassword": 3,
  "gmailNeedsReauth": 1,
  "registrationsToday": 1,
  "registrationsLast7Days": 4,
  "registrationsLast30Days": 12,
  "failedLoginsLast24h": 6,
  "mailsSentLast7DaysByDay": [
    { "date": "2026-11-16", "count": 2 },
    { "date": "2026-11-17", "count": 0 }
  ]
}
```

### EP-ST2: `GET /api/admin/logs` & `GET /api/admin/logs/export`
- **Query params:** `userId`, `username`, `action`, `from` (`yyyy-MM-dd` hoặc ISO-8601), `to` (`yyyy-MM-dd` hoặc ISO-8601), `page`, `size` (tối đa 100).
- **`GET /api/admin/logs/export`**: Xuất CSV UTF-8 có BOM (`Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="audit-logs.csv"`). Mọi trường bắt đầu bằng `=`, `+`, `-`, `@`, `\t`, `\r` đều được vô hiệu hóa chống CSV Formula Injection bằng tiền tố `'`.

