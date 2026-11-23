# Hợp Đồng API Hộp Thư Thật (Mail API Contract — M08, M10, M12, M16, M17, EP-G2)

> **Base URL:** `https://localhost:8443`  
> **Xác thực:** Tất cả endpoint dưới đây bắt buộc gửi `Authorization: Bearer <JWT_ACCESS_TOKEN>`.  
> **Format lỗi chuẩn thống nhất:** Mọi lỗi đều trả JSON có cả 2 trường `"code"` và `"errorCode"`:
> ```json
> {
>   "timestamp": "2026-11-22T16:30:00",
>   "status": 403,
>   "error": "Forbidden",
>   "message": "Mô tả lỗi tiếng Việt rõ ràng",
>   "code": "FORBIDDEN",
>   "errorCode": "FORBIDDEN"
> }
> ```

---

## Quy ước chung cho toàn bộ Mail API

1. **Chống IDOR (Ownership Check):** Mọi endpoint nhận `gmailId` đều xác minh tài khoản Gmail thuộc sở hữu của người dùng đang đăng nhập (`account.user.id == currentUser.id`). Nếu sai $\rightarrow$ `403 FORBIDDEN`.
2. **Bảo trì (`MAINTENANCE_MODE`):** Nếu hệ thống đang bảo trì và người dùng không phải `ADMIN` $\rightarrow$ `503 MAINTENANCE_MODE`.
3. **Trạng thái `NEEDS_REAUTH` & Fallback tự động:**
   - Khi hộp thư chỉ có OAuth2 (không có App Password dự phòng) và trạng thái là `NEEDS_REAUTH` (hoặc Google trả `invalid_grant`) $\rightarrow$ trả `409 GMAIL_NEEDS_REAUTH` để Client hiển thị nút **"Cấp lại quyền"**.
   - Khi hộp thư có **cả Google OAuth2 (chính) và App Password (dự phòng)** mà OAuth2 ném `ReauthRequiredException`: `MailConnectionFactory` tự động dùng App Password qua IMAPS/SMTP cho thao tác đó đúng 1 lần (KHÔNG retry lỗi xác thực nhiều lần), đặt `fallbackActive=true`, ghi audit `FALLBACK_TO_APP_PASSWORD` (không ghi bí mật) và **giữ nguyên** trạng thái `NEEDS_REAUTH` của OAuth2 để người dùng biết cần cấp lại quyền.
4. **Chuẩn hóa khóa hộp thư (`email_key`):** Dùng chung giữa Server và Client (`EmailNormalizer.normalizeKey`): hạ chữ thường, bỏ khoảng trắng; với `gmail.com` / `googlemail.com` bỏ dấu chấm `.` và phần `+tag` ở phần local, quy đổi `googlemail.com` $\rightarrow$ `gmail.com`; tên miền khác (Workspace/công ty) chỉ hạ chữ thường và bỏ khoảng trắng. Cột `email` gốc vẫn giữ để hiển thị.
5. **Kiểm tra `uidValidity`:** Khi Client gửi tham số tùy chọn `uidValidity` mà máy chủ IMAP đã thay đổi `UIDVALIDITY` của thư mục $\rightarrow$ `409 UID_VALIDITY_CHANGED` để Client tải lại danh sách thư mục.
6. **Tham số `{folder}` an toàn trên URL:** Hỗ trợ tên chuẩn hóa không chứa dấu `/` (`INBOX`, `SENT`, `DRAFTS`, `TRASH`, `SPAM`, `STARRED`, `ALL` — tự động phân giải qua `GmailFolderResolver`), tên thư mục tùy chỉnh không có `/`, hoặc dạng `b64:<url-safe-base64>` cho thư mục lồng nhau chứa dấu `/`.
7. **Không lưu nội dung thư vào DB:** Server chỉ làm proxy ngắn hạn qua `MailConnectionFactory` (IMAPS 993 / SMTP 587 — kể cả OAuth2 dùng `SASL XOAUTH2`, không chuyển sang Gmail API) và đóng kết nối trong `finally`.

---

## 1. Quản lý tài khoản Gmail (EP-G1, EP-G2, EP-G3, PATCH & Mở rộng đa phương thức ngoài 27)

### 1.1. `GET /api/gmail-accounts` (EP-G1)
Trả danh sách hộp thư Gmail của người dùng hiện tại (mỗi hộp thư **1 dòng**, tuyệt đối không bao giờ trả App Password hay Refresh Token).

**Response `200 OK`:**
```json
[
  {
    "id": 1,
    "email": "John.Doe@gmail.com",
    "emailKey": "johndoe@gmail.com",
    "displayName": "Gmail cá nhân",
    "authType": "OAUTH2",
    "preferredMethod": "OAUTH2",
    "activeMethod": "OAUTH2",
    "fallbackActive": false,
    "methods": {
      "hasOAuth": true,
      "hasAppPassword": true
    },
    "status": "ACTIVE",
    "createdAt": "2026-11-22T10:00:00",
    "tokenExpiresAt": "2026-11-22T11:00:00",
    "lastVerifiedAt": "2026-11-22T10:05:00"
  }
]
```

### 1.2. `POST /api/gmail-accounts/app-password` (EP-G2)
Liên kết hoặc bổ sung phương thức App Password 16 ký tự cho hộp thư Gmail. Server tự loại bỏ khoảng trắng trong App Password, thử đăng nhập IMAPS 993 thật qua `MailConnectionFactory.testImap(...)` **trước** khi mã hóa AES-256-GCM và upsert theo `(user_id, email_key)` trong transaction (có retry an toàn nếu xảy ra race condition).
- Chưa có hộp thư $\rightarrow$ tạo mới (`201 Created`, `outcome = "CREATED"`).
- Đã có hộp thư với Google OAuth2 $\rightarrow$ giữ nguyên `id`, `displayName`, `refreshTokenEncrypted` và `preferredMethod = OAUTH2`; thêm App Password làm dự phòng (`200 OK`, `outcome = "METHOD_ADDED"`). **Không bao giờ trả lỗi trùng.**
- Đã có App Password $\rightarrow$ cập nhật mật khẩu ứng dụng mới (`200 OK`, `outcome = "METHOD_UPDATED"`).

**Request Body:**
```json
{
  "email": "user@gmail.com",
  "appPassword": "<APP_PASSWORD_16_KY_TU>",
  "displayName": "Hộp thư công việc"
}
```
**Response (`201 Created` hoặc `200 OK`):** Trả `GmailAccountResponse` kèm `outcome` (`CREATED` | `METHOD_ADDED` | `METHOD_UPDATED`) và `message` tiếng Việt thân thiện (che email `u***@gmail.com`).

**Mã lỗi riêng:**
- `403 FEATURE_DISABLED`: Cờ `ALLOW_APP_PASSWORD_LINK=false`.
- `409 GMAIL_LIMIT_REACHED`: Đạt giới hạn `MAX_GMAIL_PER_USER` (chỉ áp dụng khi tạo hộp thư mới).
- `422 GMAIL_AUTH_FAILED`: Sai email hoặc App Password.
- `422 IMAP_DISABLED`: Gmail chưa bật IMAP (kèm hướng dẫn bật).
- `422 TWO_STEP_VERIFICATION_REQUIRED`: Tài khoản chưa bật Xác minh 2 bước / yêu cầu App Password.
- `502 GMAIL_CONNECTION_ERROR`: Lỗi mạng hoặc timeout tới Gmail.

### 1.3. `PATCH /api/gmail-accounts/{id}`
Đổi tên hiển thị (`displayName`) của tài khoản Gmail.

**Request Body:**
```json
{
  "displayName": "Hộp thư PTIT"
}
```
**Response `200 OK`:** Trả `GmailAccountResponse` đã cập nhật.

### 1.4. `PUT /api/gmail-accounts/{id}/preferred-method` *(Endpoint mở rộng ngoài 27)*
Đổi phương thức đăng nhập chính (`preferredMethod`: `OAUTH2` hoặc `APP_PASSWORD`) cho hộp thư đã có phương thức đó. Chống IDOR (`403 FORBIDDEN`).

**Request Body:**
```json
{
  "preferredMethod": "APP_PASSWORD"
}
```
**Response `200 OK`:** Trả `GmailAccountResponse` với `preferredMethod` và `activeMethod` mới. Nếu phương thức chọn chưa được liên kết $\rightarrow$ `400 METHOD_NOT_LINKED`.

### 1.5. `DELETE /api/gmail-accounts/{id}/methods/{method}` *(Endpoint mở rộng ngoài 27)*
Gỡ một phương thức đăng nhập (`method` = `OAUTH2` hoặc `APP_PASSWORD`) khỏi hộp thư. Chống IDOR (`403 FORBIDDEN`).
- Gỡ `OAUTH2`: thu hồi token trên Google (best-effort), xóa `refresh_token_encrypted`, tự chuyển phương thức chính sang `APP_PASSWORD`.
- Gỡ `APP_PASSWORD`: xóa `app_password_encrypted`, tự chuyển phương thức chính sang `OAUTH2`.
- Gỡ **phương thức cuối cùng**: nếu thiếu `?confirmUnlink=true` $\rightarrow$ `409 CONFIRM_UNLINK_REQUIRED`; nếu có `?confirmUnlink=true` $\rightarrow$ xóa luôn liên kết hộp thư và trả `{ "unlinked": true, "removedMethod": "...", "message": "..." }`.

---

## 2. Danh sách Thư mục (`GET /api/mails/folders?gmailId={id}`)

Phân giải bằng `GmailFolderResolver` (RFC 6154 + fallback đa ngôn ngữ), trả tên hiển thị tiếng Việt thân thiện.

**Response `200 OK`:**
```json
[
  {
    "name": "INBOX",
    "imapName": "INBOX",
    "displayName": "Hộp thư đến",
    "type": "INBOX",
    "unread": 3,
    "total": 42
  },
  {
    "name": "SENT",
    "imapName": "[Gmail]/Sent Mail",
    "displayName": "Thư đã gửi",
    "type": "SENT",
    "unread": 0,
    "total": 18
  }
]
```
Giá trị `type`: `INBOX`, `SENT`, `DRAFTS`, `TRASH`, `SPAM`, `STARRED`, `ALL`, `CUSTOM`.

---

## 3. Liệt kê & Tìm kiếm Thư (`GET /api/mails`)

**Query Parameters:**
- `gmailId` (bắt buộc): ID tài khoản Gmail.
- `folder` (mặc định `INBOX`): Tên thư mục hoặc mã chuẩn (`INBOX`, `SENT`, `TRASH`, ...).
- `page` (mặc định `0`): Trang (0-based).
- `size` (mặc định `30`, tối đa `100`): Số thư mỗi trang.
- `q` (tùy chọn): Từ khóa tìm kiếm (dùng `X-GM-RAW` khi là Gmail, fallback `SearchTerm` chuẩn).
- `unread` (tùy chọn `true/false`): Lọc thư chưa đọc.
- `hasAttachment` (tùy chọn `true/false`): Lọc thư có tệp đính kèm.
- `from` (tùy chọn): Lọc theo người gửi.
- `since` (tùy chọn, `yyyy-MM-dd`): Lọc thư từ ngày trở đi.

Sắp xếp **mới nhất trước**. Chỉ dùng `FetchProfile` (`ENVELOPE`, `FLAGS`, `CONTENT_INFO`, `UID`), **không** tải thân thư khi liệt kê.

**Response `200 OK`:**
```json
{
  "items": [
    {
      "uid": 1052,
      "uidValidity": 1,
      "from": { "name": "Phòng Đào Tạo", "address": "pdt@ptit.edu.vn" },
      "to": [{ "name": "Cường", "address": "user@gmail.com" }],
      "subject": "Thông báo lịch bảo vệ đồ án",
      "snippet": "",
      "date": "2026-11-22T09:15:00Z",
      "flags": { "seen": false, "flagged": true, "answered": false, "draft": false },
      "size": 24510,
      "hasAttachment": true,
      "attachmentCount": 1
    }
  ],
  "page": 0,
  "size": 30,
  "total": 42,
  "hasMore": true,
  "uidValidity": 1
}
```

---

## 4. Đọc Chi tiết Thư (`GET /api/mails/{gmailId}/{folder}/{uid}`)

**Query Parameters:**
- `loadRemote` (mặc định `false`): Nếu `false`, chặn mọi ảnh/tài nguyên ngoài `http(s)://` trong `htmlBody`, vẫn giữ ảnh nhúng `cid:`.
- `uidValidity` (tùy chọn): Nếu khác `UIDVALIDITY` hiện tại của thư mục $\rightarrow$ `409 UID_VALIDITY_CHANGED`.

Tự động đánh dấu cờ `\Seen`. Xử lý multipart lồng nhau, charset lạ, tên file RFC 2047/2231, thư chỉ có HTML, thư rỗng, thư quá lớn (giới hạn 512 KB kèm `truncated=true`). Làm sạch HTML bằng `jsoup Safelist` (loại bỏ `script`, `iframe`, `form`, `on*`, `style` nguy hiểm).

**Response `200 OK`:**
```json
{
  "uid": 1052,
  "uidValidity": 1,
  "from": { "name": "Phòng Đào Tạo", "address": "pdt@ptit.edu.vn" },
  "to": [{ "name": "Cường", "address": "user@gmail.com" }],
  "cc": [],
  "bcc": [],
  "replyTo": [],
  "date": "2026-11-22T09:15:00Z",
  "messageId": "<abc123@ptit.edu.vn>",
  "inReplyTo": null,
  "references": null,
  "subject": "Thông báo lịch bảo vệ đồ án",
  "textBody": "Nội dung văn bản thuần...",
  "htmlBody": "<p>Nội dung HTML đã làm sạch...</p>",
  "truncated": false,
  "hasBlockedRemoteImages": true,
  "attachments": [
    {
      "index": 0,
      "filename": "lich-bao-ve.pdf",
      "contentType": "application/pdf",
      "size": 18240,
      "inline": false,
      "contentId": null
    }
  ],
  "flags": { "seen": true, "flagged": true, "answered": false, "draft": false }
}
```

---

## 5. Tải Tệp Đính Kèm (`GET /api/mails/{gmailId}/{folder}/{uid}/attachments/{index}/download`)

Stream từng phần (8 KB/chunk) thẳng ra response, không nạp toàn bộ tệp vào RAM. Header `Content-Disposition` chuẩn RFC 5987 với tên tệp đã làm sạch chống Path Traversal. Các loại file thực thi trên trình duyệt (`text/html`, `image/svg+xml`, `application/javascript`) được ép `Content-Type: application/octet-stream` và `X-Content-Type-Options: nosniff`.

**Mã lỗi:** `400 INVALID_ATTACHMENT_INDEX`, `404 MAIL_NOT_FOUND`, `404 ATTACHMENT_NOT_FOUND`, `409 UID_VALIDITY_CHANGED`, `413 PAYLOAD_TOO_LARGE` (vượt `MAX_ATTACHMENT_SIZE_MB`).

---

## 6. Gửi Thư (`POST /api/mails/send`)

**Content-Type:** `multipart/form-data`
- Part `payload` (`application/json`):
  ```json
  {
    "gmailId": 1,
    "to": ["receiver@example.com"],
    "cc": [],
    "bcc": [],
    "subject": "Báo cáo đồ án",
    "textBody": "Xin chào...",
    "htmlBody": "<p>Xin chào...</p>",
    "inReplyTo": "<msg-id@example.com>",
    "references": "<msg-id@example.com>"
  }
  ```
- Part `attachments` (tùy chọn, nhiều file): các tệp đính kèm.

**Kiểm tra bảo mật:**
- Cờ `ALLOW_SEND_MAIL` (`403 FEATURE_DISABLED`).
- Tổng dung lượng đính kèm $\le$ `MAX_ATTACHMENT_SIZE_MB` (`413 PAYLOAD_TOO_LARGE`).
- Chống tiêm header CRLF (`\r`, `\n`) trong `subject`, `to`, `cc`, `bcc`, `inReplyTo`, `references` (`400 HEADER_INJECTION_DETECTED`).
- Giới hạn tổng số người nhận $\le 50$ (`400 TOO_MANY_RECIPIENTS`).
- Rate limit theo người dùng (`429 TOO_MANY_ATTEMPTS` kèm `Retry-After`).
- Ghi Audit Log `SEND_MAIL` (tuyệt đối không lưu nội dung hay tiêu đề thư).

**Response `200 OK`:**
```json
{
  "messageId": "<generated-id@mailapp>",
  "status": "SENT"
}
```

---

## 7. Xóa Thư (`DELETE /api/mails/{gmailId}/{folder}/{uid}`)

*(Endpoint mở rộng ngoài bảng 27 — ghi nhận trong mục Quyết định)*

**Query Parameters:**
- `permanent` (mặc định `false`):
  - `false`: Chuyển thư vào Thùng rác (`GmailFolderResolver.SpecialFolder.TRASH`).
  - `true`: Xóa vĩnh viễn (`\Deleted` + `expunge`) — **CHỈ cho phép khi thư đang nằm trong Thùng rác** (nếu gọi `permanent=true` ngoài Thùng rác $\rightarrow$ `400 PERMANENT_DELETE_REQUIRES_TRASH`).
- `uidValidity` (tùy chọn): Kiểm tra đồng bộ `UIDVALIDITY`.

Ghi Audit Log `DELETE_MAIL`.

**Response `200 OK`:**
```json
{
  "status": "DELETED",
  "permanent": false,
  "message": "Đã chuyển thư vào Thùng rác."
}
```

---

## 8. Cập nhật Cờ Thư (`PUT /api/mails/{gmailId}/{folder}/{uid}/flags` — M17)

**Request Body:**
```json
{
  "seen": true,
  "flagged": false
}
```
*(Trường nào khác `null` sẽ được cập nhật).*

**Response `200 OK`:**
```json
{
  "seen": true,
  "flagged": false,
  "answered": false,
  "draft": false
}
```

