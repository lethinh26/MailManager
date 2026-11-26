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
>   "errorCode": "FORBIDDEN",
>   "path": "/api/mails/folders"
> }
> ```

---

## Quy ước chung cho toàn bộ Mail API

1. **Chống IDOR (Ownership Check):** Mail API tra cứu bằng `findByIdAndUserId(id,userId)` trước khi mở kết nối hoặc cập nhật dữ liệu. Cùng trả `404 GMAIL_NOT_OWNED` với thông báo chung "Không tìm thấy tài khoản Gmail." nếu ID không tồn tại hoặc thuộc người khác (không tiết lộ sự tồn tại của hộp thư). `403 FORBIDDEN` vẫn dùng cho phân quyền chức năng/role, không dùng để phân biệt ID hộp thư. Endpoint chẩn đoán kết nối `POST /api/gmail-accounts/{id}/test` giữ quyền ADMIN kiểm tra tài khoản khác đã có trong thiết kế; USER chỉ kiểm tra hộp thư của mình.
2. **Bảo trì (`MAINTENANCE_MODE`):** Nếu hệ thống đang bảo trì và người dùng không phải `ADMIN` $\rightarrow$ `503 MAINTENANCE_MODE`.
3. **Trạng thái `NEEDS_REAUTH` & Fallback tự động:**
   - Khi hộp thư chỉ có OAuth2 (không có App Password dự phòng) và trạng thái là `NEEDS_REAUTH` (hoặc Google trả `invalid_grant`/từ chối xác thực OAuth2) $\rightarrow$ trả `422 GMAIL_NEEDS_REAUTH` để Client hiển thị nút **"Cấp lại quyền"**.
   - Khi hộp thư có **cả Google OAuth2 (chính) và App Password (dự phòng)** mà OAuth2 ném `ReauthRequiredException`: `MailConnectionFactory` tự động dùng App Password qua IMAPS/SMTP cho thao tác đó đúng 1 lần (KHÔNG retry lỗi xác thực nhiều lần), đặt `fallbackActive=true`, ghi audit `FALLBACK_TO_APP_PASSWORD` (không ghi bí mật) và **giữ nguyên** trạng thái `NEEDS_REAUTH` của OAuth2 để người dùng biết cần cấp lại quyền.
4. **Chuẩn hóa khóa hộp thư (`email_key`):** Dùng chung giữa Server và Client (`EmailNormalizer.normalizeKey`): hạ chữ thường, bỏ khoảng trắng; với `gmail.com` / `googlemail.com` bỏ dấu chấm `.` và phần `+tag` ở phần local, quy đổi `googlemail.com` $\rightarrow$ `gmail.com`; tên miền khác (Workspace/công ty) chỉ hạ chữ thường và bỏ khoảng trắng. Cột `email` gốc vẫn giữ để hiển thị.
5. **Kiểm tra `uidValidity`:** Khi Client gửi tham số tùy chọn `uidValidity` mà máy chủ IMAP đã thay đổi `UIDVALIDITY` của thư mục $\rightarrow$ `409 UID_VALIDITY_CHANGED` để Client tải lại danh sách thư mục.
6. **Tham số `{folder}` an toàn trên URL:** Hỗ trợ tên chuẩn hóa không chứa dấu `/` (`INBOX`, `SENT`, `DRAFTS`, `TRASH`, `SPAM`, `STARRED`, `ALL` — tự động phân giải qua `GmailFolderResolver`), tên thư mục tùy chỉnh không có `/`, hoặc dạng `b64:<url-safe-base64>` cho thư mục lồng nhau chứa dấu `/`.
7. **Không lưu nội dung thư vào DB:** Server chỉ làm proxy qua `MailConnectionFactory` (IMAPS 993 / SMTP 587 — kể cả OAuth2 dùng `SASL XOAUTH2`, không chuyển sang Gmail API). Wrapper kết nối đã xác thực được dùng trong try-with-resources; IMAP có thể trả kết nối còn khỏe vào pool. Tải đính kèm dùng file tạm trong thư mục temp của hệ điều hành, giới hạn theo cấu hình và dọn trong vòng đời request (xem mục 5).
8. **Lỗi kết nối chung:** `422 GMAIL_AUTH_FAILED` cho App Password sai, `422 GMAIL_NEEDS_REAUTH` cho OAuth2 cần cấp lại quyền, `502 GMAIL_CONNECTION_ERROR` cho lỗi mạng/giao thức và `504 GMAIL_CONNECTION_ERROR` khi factory xác định timeout. `401 UNAUTHORIZED` nếu chưa xác thực; các cờ tính năng có thể trả `403 FEATURE_DISABLED`.

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
Liên kết hoặc bổ sung phương thức App Password cho hộp thư Gmail (Google cấp App Password 16 ký tự; DTO chấp nhận 8–64 ký tự). Server loại bỏ khoảng trắng, thử đăng nhập IMAPS qua `MailConnectionFactory.testImap(...)` **trước** khi mã hóa AES-256-GCM và lưu theo `(user_id, email_key)` trong transaction.
- Mặc định `intent=CREATE`: hộp thư chưa có trả `201 Created`, `outcome=CREATED`; hộp thư đã có trả `409 GMAIL_DUPLICATE` trước probe và được kiểm tra lại trong transaction.
- Chỉ khi gửi `intent=UPSERT` tường minh: hộp thư đã có OAuth2 được thêm App Password (`200 OK`, `outcome=METHOD_ADDED`), giữ ID, phương thức OAuth2 và lựa chọn ưu tiên hiện tại; App Password đã có được cập nhật (`200 OK`, `outcome=METHOD_UPDATED`).

**Request Body:**
```json
{
  "email": "user@gmail.com",
  "appPassword": "<APP_PASSWORD_16_KY_TU>",
  "displayName": "Hộp thư kiểm thử",
  "intent": "CREATE"
}
```
**Response (`201 Created` hoặc `200 OK`):** Trả `GmailAccountResponse` kèm `outcome` (`CREATED` | `METHOD_ADDED` | `METHOD_UPDATED`) và `message` tiếng Việt thân thiện (che email `u***@gmail.com`).

**Mã lỗi riêng:**
- `400 VALIDATION_ERROR`: Body không hợp lệ (email tối đa 255 ký tự, displayName tối đa 255 ký tự, App Password 8–64 ký tự).
- `403 FEATURE_DISABLED`: Cờ `ALLOW_APP_PASSWORD_LINK=false`.
- `409 GMAIL_DUPLICATE`: CREATE mặc định gặp hộp thư đã liên kết; UPSERT phải được người dùng chọn rõ ràng.
- `409 GMAIL_LIMIT_REACHED`: Đạt giới hạn `MAX_GMAIL_PER_USER` (chỉ áp dụng khi tạo hộp thư mới).
- `422 GMAIL_AUTH_FAILED`: Sai email hoặc App Password.
- `422 IMAP_DISABLED`: Gmail chưa bật IMAP (kèm hướng dẫn bật).
- `422 TWO_STEP_VERIFICATION_REQUIRED`: Tài khoản chưa bật Xác minh 2 bước / yêu cầu App Password.
- `502 GMAIL_CONNECTION_ERROR`: Lỗi kết nối trong probe liên kết (khác mapping timeout 504 của Mail API). Probe IMAPS mở kết nối riêng, giới hạn từng socket tối đa 10 giây và không retry xác thực.

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
Đổi phương thức đăng nhập chính (`preferredMethod`: `OAUTH2` hoặc `APP_PASSWORD`) cho hộp thư đã có phương thức đó. Chống IDOR (`404 ACCOUNT_NOT_FOUND`).

**Request Body:**
```json
{
  "preferredMethod": "APP_PASSWORD"
}
```
**Response `200 OK`:** Trả `GmailAccountResponse` với `preferredMethod` và `activeMethod` mới. Nếu phương thức chọn chưa được liên kết $\rightarrow$ `400 METHOD_NOT_LINKED`.

### 1.5. `DELETE /api/gmail-accounts/{id}/methods/{method}` *(Endpoint mở rộng ngoài 27)*
Gỡ một phương thức đăng nhập (`method` = `OAUTH2` hoặc `APP_PASSWORD`) khỏi hộp thư. Chống IDOR (`404 ACCOUNT_NOT_FOUND`).
- Gỡ `OAUTH2`: thu hồi token trên Google (best-effort), xóa `refresh_token_encrypted`, tự chuyển phương thức chính sang `APP_PASSWORD`.
- Gỡ `APP_PASSWORD`: xóa `app_password_encrypted`, tự chuyển phương thức chính sang `OAUTH2`.
- Gỡ **phương thức cuối cùng**: nếu thiếu `?confirmUnlink=true` $\rightarrow$ `409 CONFIRM_UNLINK_REQUIRED`; nếu có `?confirmUnlink=true` $\rightarrow$ xóa luôn liên kết hộp thư và trả `{ "unlinked": true, "removedMethod": "...", "message": "..." }`.

### 1.6. `DELETE /api/gmail-accounts/{id}` (EP-G3)
Hủy liên kết toàn bộ hộp thư thuộc người gọi: 204 không body; ID không tồn tại hoặc thuộc người khác cùng 404 NOT_FOUND. Nếu có OAuth2 thì revoke best-effort, kể cả App Password là phương thức chính; không xóa thư trên Gmail. Lỗi revoke được audit riêng, không ngăn xóa liên kết local.

---

## 2. Danh sách Thư mục (`GET /api/mails/folders?gmailId={id}`)

Chấp nhận `gmailId` hoặc tên tương thích `gmailAccountId` (ưu tiên `gmailId` nếu cả hai có mặt). Phân giải bằng `GmailFolderResolver` (RFC 6154 + fallback đa ngôn ngữ), dùng chung một LIST và trả tên hiển thị tiếng Việt thân thiện. Đọc số thư tổng/chưa đọc qua STATUS hoặc API thư mục chuẩn. Chỉ cache kết quả khi đọc thành công; nếu LIST/STATUS thất bại thì trả lỗi kết nối, không biến lỗi thành hộp thư rỗng hay số 0 giả.

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
- `gmailId` hoặc `gmailAccountId` (bắt buộc có một): ID tài khoản Gmail.
- `folder` (mặc định `INBOX`): Tên thư mục hoặc mã chuẩn (`INBOX`, `SENT`, `TRASH`, ...).
- `page` (mặc định `0`): Trang (0-based).
- `size` (bỏ trống hoặc <= 0: dùng `MAIL_PAGE_SIZE` hiện tại, mặc định hệ thống `20`; tối đa `100`): Số thư mỗi trang.
- `q` (tùy chọn): Từ khóa tìm kiếm (thử `X-GM-RAW` nếu Gmail và provider hỗ trợ; fallback tìm theo tiêu đề/người gửi bằng `SearchTerm` hoặc ENVELOPE).
- `unread` (tùy chọn `true/false`): Lọc thư chưa đọc.
- `hasAttachment` (tùy chọn `true/false`): `true` chỉ thư có đính kèm, `false` chỉ thư không có đính kèm; lọc trước phân trang trên toàn bộ kết quả, không giới hạn 300 thư mới nhất.
- `from` (tùy chọn): Lọc theo người gửi.
- `since` (tùy chọn, `yyyy-MM-dd`): Lọc thư từ ngày trở đi; sai định dạng trả `400 VALIDATION_ERROR`.

Sắp xếp **date giảm dần** trên toàn bộ tập kết quả trước khi phân trang: receivedDate (IMAP INTERNALDATE), fallback sentDate nếu thiếu; ngày null đứng cuối, trùng ngày phân xử UID giảm dần. Batch 500 candidate bằng FetchProfile ENVELOPE/UID, chỉ fetch FLAGS/CONTENT_INFO của trang (hoặc batch 100 mọi candidate khi lọc attachment); fallback search chỉ dùng ENVELOPE/FLAGS/UID. Không fetch leaf body hay tải file để liệt kê; snippet rỗng. Không dựa vào message number hay giả định server hỗ trợ SORT. Chi phí O(n) metadata và O(n log n) sắp xếp, hiệu năng Gmail lớn chưa xác minh; offset pagination có thể dịch trang nếu hộp thư thay đổi giữa requests. `total`/`hasMore` tính sau lọc.

**Tương thích contract gốc AM-4321:** response đồng thời có `content` (cùng mảng với `items`), `totalElements` (bằng `total`), `totalPages` (ceil(total/size), 0 nếu rỗng) và mỗi summary có `isRead` bằng `flags.seen`. Ví dụ dưới đây rút gọn, chỉ liệt kê shape chính; các trường alias vẫn được trả.

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
- `loadRemote` (mặc định `false`): Nếu `false`, chặn ảnh/tài nguyên từ xa dùng `http(s)://` trong `htmlBody`, vẫn giữ ảnh nhúng `cid:`.
- `markSeen` (mặc định `true`): Đọc thư và xác nhận Seen; false dùng preview không đổi Seen.
- `uidValidity` (tùy chọn): Nếu khác `UIDVALIDITY` hiện tại của thư mục $\rightarrow$ `409 UID_VALIDITY_CHANGED`.

Mặc định `markSeen=true`: mở `READ_WRITE`, kiểm tra UID và tự động đánh dấu cờ `\Seen`; nếu không thể ghi/xác nhận cờ thì trả lỗi kết nối, không báo đọc thành công giả. `markSeen=false` mở `READ_ONLY` + PEEK, không thay đổi Seen (preview/cleanup); kiểm tra UIDVALIDITY như mặc định. Không cache chi tiết có thể cũ sau thao tác từ một client khác. Xử lý multipart và thư chuyển tiếp `message/rfc822` lồng nhau, charset lạ, tên file RFC 2047/2231, thư chỉ có HTML, thư rỗng, thư quá lớn (giới hạn 512 × 1024 ký tự cho mỗi dạng body, `truncated=true` khi bị cắt). Làm sạch HTML bằng `jsoup Safelist` (loại bỏ `script`, `iframe`, `form`, `on*`, `style` nguy hiểm). Với HTML-only, cung cấp thêm text fallback.

`attachments` dùng index từ 0, duyệt MIME theo thứ tự cây; các part khai báo attachment/có filename (kể cả text), tài nguyên inline và part nhị phân được tính theo cùng quy tắc khi liệt kê, đọc và tải. `sizeAccuracy` là `ESTIMATED` khi kích thước chỉ suy từ metadata MIME (kể cả 0), `UNKNOWN` khi provider không cung cấp kích thước (giữ số 0 cho client cũ), và `EXACT` dành cho trường hợp có độ dài đã xác minh. IMAP BODYSTRUCTURE không cho biết chính xác padding Base64 hoặc mọi biến đổi encoding, vì vậy không đọc nội dung tất cả attachment chỉ để tính size. Client nên hiển thị dấu xấp xỉ hoặc không rõ tương ứng. Download đã đo số byte giải mã thực tế và trả Content-Length chính xác; dùng byte/hash tải về để kiểm tra toàn vẹn.

Response đồng thời có alias `bodyText`/`bodyHtml` bằng `textBody`/`htmlBody`, và mỗi attachment có `fileName`/`sizeBytes` bằng `filename`/`size`, để tương thích AM-4322/4397. Ví dụ dưới chỉ hiển thị shape chính.

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
      "sizeAccuracy": "ESTIMATED",
      "inline": false,
      "contentId": null
    }
  ],
  "flags": { "seen": true, "flagged": true, "answered": false, "draft": false }
}
```

---

## 5. Tải Tệp Đính Kèm (`GET /api/mails/{gmailId}/{folder}/{uid}/attachments/{index}/download`)

Tham số tùy chọn `uidValidity` và index từ 0 tương ứng metadata ở mục 4. Mở folder `READ_ONLY`, đọc `Part.getInputStream()` (đã giải mã MIME) theo khối 64 KiB vào file tạm giới hạn dung lượng. Chỉ trả HTTP 200 sau khi kiểm tra đủ số byte đã giải mã, nhờ đó trả `413` trước khi ghi header nếu vượt ngưỡng; `Content-Length` là số byte thực tế, không dùng ước lượng MIME. Stream từ file tạm ra response, không nạp toàn bộ tệp vào RAM. File tạm được dọn khi stream/đóng handle hoặc khi có lỗi; sự cố dừng đột ngột tiến trình có thể để lại file trong thư mục temp hệ điều hành.

Với attachment nằm bên trong thư chuyển tiếp `message/rfc822`, tải MIME gốc theo luồng vào file tạm riêng tối đa 128 MiB rồi parse bằng shared file stream; kiểm tra cùng index/đường dẫn MIME/filename/content type/content-ID trước khi lấy phần đính kèm. Điều này tránh lỗi BODY section lồng nhau ở một số IMAP server, không áp dụng cho danh sách thư hay tải attachment thông thường; file MIME tạm cũng được dọn sau xử lý.

Header `Content-Disposition` chuẩn RFC 5987 với tên tệp đã làm sạch chống Path Traversal. Các loại file thực thi trên trình duyệt (`text/html`, `image/svg+xml`, `application/javascript`) được ép `Content-Type: application/octet-stream` và trả `X-Content-Type-Options: nosniff`. Có rate limit tải theo người dùng (`429`, `Retry-After`).

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

`payload.gmailAccountId` được chấp nhận làm alias của `gmailId`. Ngoài contract JSON chính, cùng endpoint chấp nhận multipart form gốc: các trường text `gmailAccountId` (hoặc `gmailId`), `to`, `cc`, `bcc`, `subject`, `body`, `isHtml` (true/false; mặc định false), và tùy chọn `inReplyTo`/`references`. Địa chỉ có thể truyền nhiều field cùng tên hoặc phân cách dấu phẩy/chấm phẩy. File part `files` hoặc `files[]` là alias của `attachments`; tất cả file parts được tính chung. Khi dùng JSON payload không trộn các trường text của form gốc (400 VALIDATION_ERROR). Hai dạng đi qua cùng nghiệp vụ/validation; `body` được map vào textBody hoặc htmlBody theo isHtml.

`MailMessageBuilder` tạo text UTF-8 hoặc multipart/alternative (text + HTML sạch), bọc multipart/mixed nếu có file; dữ liệu file được đọc qua stream, kể cả file rỗng có tên.

**Kiểm tra bảo mật:**
- Cờ `ALLOW_SEND_MAIL` (`403 FEATURE_DISABLED`).
- Mỗi tệp đính kèm $\le$ `MAX_ATTACHMENT_SIZE_MB` (`413 PAYLOAD_TOO_LARGE`) cho cả JSON multipart và form gốc. Giới hạn HTTP multipart toàn request 60 MiB (gồm cả overhead) và từng part 50 MiB; service cũng chặn tổng file trên 60 MiB khi được gọi trực tiếp. Swing vẫn kiểm tra tổng dung lượng theo cấu hình (AM-4368); máy chủ Gmail có thể áp giới hạn riêng thấp hơn.
- Tiêu đề tối đa 200 Unicode code point; tổng byte UTF-8 của `textBody` và `htmlBody` tối đa 1 MiB (`400 VALIDATION_ERROR`). HTML gửi đi không bị cắt bởi giới hạn hiển thị 512 Ki ký tự của mục 4.
- Ít nhất một địa chỉ To; kiểm tra địa chỉ To/CC/BCC (`400 INVALID_EMAIL` khi sai địa chỉ, kể cả SMTP từ chối địa chỉ người nhận; không trả nguyên văn lỗi SMTP chứa địa chỉ).
- Chống tiêm header CRLF (`\r`, `\n`) trong `subject`, `to`, `cc`, `bcc`, `inReplyTo`, `references` (`400 HEADER_INJECTION_DETECTED`).
- Giới hạn tổng số người nhận $\le 50$ (`400 TOO_MANY_RECIPIENTS`).
- Rate limit theo người dùng (`429 TOO_MANY_ATTEMPTS` kèm `Retry-After`).
- Ghi Audit Log `SEND_MAIL` sau khi SMTP thành công (gmailId, email gửi đã che, recipientCount, attachmentCount, toCount, ccCount, bccCount, IP). Các count phân biệt nhóm người nhận mà không lưu địa chỉ, tiêu đề, nội dung, fingerprint hay hash của chúng.

**Response `200 OK`:**
```json
{
  "messageId": "<generated-id@mailapp>",
  "status": "SENT",
  "message": "Sent",
  "sentAt": "2026-11-26T05:00:00Z"
}
```

---

## 7. Xóa Thư (`DELETE /api/mails/{gmailId}/{folder}/{uid}`)

*(EP-M6 — trả 204 theo task gốc AM-4391/4392; giữ xác nhận xóa vĩnh viễn tường minh. Client cần JSON như contract trước gửi `Prefer: return=representation`.)*

**Query Parameters:**
- `permanent` (mặc định `false`):
  - `false`: Chuyển thư vào Thùng rác (`GmailFolderResolver.SpecialFolder.TRASH`); nếu đã ở Thùng rác trả `400 PERMANENT_DELETE_REQUIRED_IN_TRASH`.
  - `true`: Xóa vĩnh viễn (`\Deleted` + UID EXPUNGE) — **CHỈ cho phép khi thư đang nằm trong Thùng rác** (nếu gọi `permanent=true` ngoài Thùng rác $\rightarrow$ `400 PERMANENT_DELETE_REQUIRES_TRASH`).
- `uidValidity` (tùy chọn): Kiểm tra đồng bộ `UIDVALIDITY`.

Xác định Thùng rác theo thư mục thực tế do resolver trả về, không chỉ theo hậu tố tên. Nếu server hỗ trợ `MOVE`, gửi một lệnh `UID MOVE` cho đúng UID trong folder đã chọn. Nếu không có MOVE, chuyển bằng COPY rồi đặt Deleted và UID EXPUNGE; xóa vĩnh viễn luôn cần UIDPLUS. Server không có capability cần thiết bị từ chối trước COPY/Deleted (`502 GMAIL_CONNECTION_ERROR`); fallback expunge thường chỉ có ở constructor package-private cho GreenMail fixture, kiểm tra không có thư khác mang cờ Deleted. Đóng folder không expunge toàn bộ. COPY và expunge không phải transaction nguyên tử; khi mất phản hồi từ thao tác mutation, service trả `502 GMAIL_CONNECTION_ERROR` với thông báo kết quả chưa xác định, loại kết nối lỗi khỏi pool, bỏ cache folder, không tự retry hoặc đổi MOVE thành COPY. Client phải tải lại cả nguồn và Trash để kiểm tra trước khi người dùng quyết định thao tác tiếp.

Ghi Audit Log `MAIL_DELETE` (một bản ghi duy nhất, không double-write với tên lịch sử DELETE_MAIL) sau khi MOVE hoặc expunge thành công; detail chứa gmailId, folder, uid, permanent và IP từ request; không ghi subject/body. Không ghi audit thành công khi kết quả chưa xác định.

**Response mặc định `204 No Content`** (không có body). Khi gửi header `Prefer: return=representation`, trả `200 OK` với `Preference-Applied: return=representation`:
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
Ít nhất một trong hai trường khác `null` (nếu không trả `400 VALIDATION_ERROR`). `starred` là alias đầu vào của `flagged`; response giữ `flagged` và bổ sung `uid`/`starred` tương thích AM-4394. Đặt `seen=false` để đánh dấu chưa đọc, `flagged=false` để bỏ sao. Hỗ trợ `uidValidity` tùy chọn; mở `READ_WRITE` và xác nhận trạng thái mới. Ghi Audit Log `MAIL_FLAG_CHANGE` gồm gmailId, uid, starred, seen, IP, không ghi subject/body.

**Response `200 OK`:**
```json
{
  "uid": 1052,
  "seen": true,
  "flagged": false,
  "starred": false,
  "answered": false,
  "draft": false
}
```

---

## 9. Bằng chứng kiểm thử và phạm vi xác minh

**Bổ sung contract byte/error ngày 26/11/2026:** `MailMessageBuilder` đặt `Content-Transfer-Encoding: base64` trên mọi MIME attachment (kể cả file rỗng) để giữ nguyên byte nguồn qua SMTP. Metadata IMAP `sizeAccuracy=ESTIMATED` có thể khác kích thước tải; đối chiếu byte decode, Content-Length và SHA-256. Các lỗi trước khi commit binary response trả JSON `ErrorResponse` với status/code thực tế ngay cả khi `Accept: application/octet-stream` (413 hoặc 429 + Retry-After); không chuyển sang `/error`/401 do negotiation. Không thay đổi RBAC, ownership hoặc giới hạn production. Sau khi response binary đã commit, lỗi stream không thể đổi status; Client phải kiểm tra download hoàn tất và xử lý file tạm.

G050–052 phát hiện PDF/TXT thay byte; J009–011 xác nhận PDF 584, PNG 68, TXT 35 byte và SHA-256 đúng nguồn; J021–022 xác nhận 429 JSON đúng path, Retry-After và owner khác không bị ảnh hưởng. G054–063 xác nhận lỗi IMAP/SMTP trước connect và phục hồi listener fixture, không retry mutation chưa rõ kết quả. Xem `architechture/docs/backend-supplemental-observations.md`. Không coi local evidence là Gmail live hay phê duyệt khác biệt contract.

Các lớp sau là bằng chứng **mã test đã có**, không phải xác nhận lần chạy hiện tại đã pass:

- `MailControllerCompatibilityTest` (standalone MockMvc): schema hiện tại và alias gốc, JSON multipart/form fields legacy, mapping validation/413, cờ, Content-Length/Content-Disposition/nosniff và cleanup. Không mở kết nối Gmail.

- `MailServiceGreenMailIntegrationTest`: luồng IMAPS/SMTP cục bộ, đọc MIME lồng/UTF-8, lọc, gửi, tải SHA-256/kích thước decode, 413, cờ và audit, xóa mềm/vĩnh viễn.
- `MailServiceFailureRegressionTest`: LIST lỗi không cache rỗng, lọc ngoài 300 thư, bộ lọc false, SMTP từ chối địa chỉ, folder read-only và ID ngoại lai/không tồn tại cùng 404 trước IMAP; `MailMessageBuilderTest`: MIME round-trip text/HTML/PDF/PNG/text rỗng, tên UTF-8 và alias flags.
- `DefaultMailConnectionFactoryGreenMailTest`, `MailConnectionFaultAndRetryTest`, `XOAuth2IntegrationTest`, `MailFallbackConnectionTest`: factory, phân loại lỗi, token provider và fallback; test cục bộ không chứng minh TLS/XOAUTH2 tới Google thật.
- Hai suite `RealGmailIntegrationTest` và `RealGmailXOAuth2IntegrationTest` đều yêu cầu `MAILAPP_REAL_GMAIL_DEDICATED=TEST-ACCOUNT`, đúng một auth mode opt-in và credential của Gmail test riêng. Cả hai kiểm IMAPS/SMTP, gửi thư tự gửi riêng biệt, đối chiếu byte/hash và dọn đúng UID thư test (UIDPLUS), không expunge toàn mailbox. App Password: `MAILAPP_REAL_GMAIL=1`, `GMAIL_TEST_EMAIL`, `GMAIL_TEST_APP_PASSWORD`; runner nhập tương tác. XOAUTH2: `MAILAPP_REAL_GMAIL_XOAUTH2=1`, `GMAIL_TEST_EMAIL`, `GMAIL_TEST_OAUTH2_ACCESS_TOKEN` ngắn hạn có mail scope, cấp qua môi trường tiến trình test cô lập. `RealGmailMimeFixturesTest` cần opt-in giữ thư mẫu riêng. Không ghi credential thật vào repo hoặc log; xem `docs/dev-setup.md`.

Kết quả suite do người phụ trách kiểm thử cập nhật riêng. Quan sát HTTP từng request trên fixture local xem `architechture/docs/backend-manual-observations.md`: đã có CREATE duplicate 409/UPSERT 200, tải binary, gửi multipart, thao tác cờ và Trash; ghi nhận riêng ca âm tính và các fix cần chạy lại. Quan sát agent không đồng nghĩa người nghiệm thu ký xác nhận hoặc Gmail/Google thật. Việc chuẩn bị Gmail test, 2FA/cấp credential, gửi đủ bộ fixture và nghiệm thu live vẫn còn.

### Bổ sung về kết quả mutation và audit storage

Nếu SMTP đã xác nhận giao thư hoặc IMAP đã xác nhận mutation, lỗi lưu audit độc lập không được chuyển thành lỗi gửi/xóa giả. Backend giữ kết quả nghiệp vụ đã xác nhận và ghi `AUDIT_WRITE_FAILED` đã lọc; log audit success chỉ sau commit. Không cam kết row audit tồn tại trong lúc DB lỗi, A/B là mốc trước V7, chưa có durable outbox; không có atomic commit MySQL–Gmail. A010–011 trước sửa trả 502 dù có đúng một thư; B010–018 trên JAR cuối xác nhận send200, flags200 và move204 với read-back đúng. B021–029 restore audit, thao tác mới ghi lại bình thường.

Nếu response của mutation IMAP không rõ (network, timeout hoặc partial STORE), Client reload mailbox/detail, không tự retry mutation, không áp trạng thái cũ. Backend invalidate folder cache ngay cả khi STORE lỗi giữa chừng. Khi UIDVALIDITY đổi, bỏ kết quả cũ và reload; download/auto-open chỉ cập nhật detail/session hiện hành. Contract raw To/subject và permanent delete implicit vẫn chưa được chủ dự án phê duyệt thay đổi.


## Audit durability V7 và lỗi mutation

SEND_MAIL, MAIL_DELETE, MAIL_FLAG_CHANGE: intent PREPARED trước SMTP send/IMAP mutation; nếu không commit được intent, HTTP 503 `AUDIT_UNAVAILABLE` (chưa thao tác thư). Kết quả xác nhận 200/204 giữ nguyên nếu audit storage lỗi. CONFIRMED drain đúng action; UNCERTAIN drain MAIL_OPERATION_UNCERTAIN (không phải audit success). Ghi audit và xóa outbox cùng transaction/row lock; replay mỗi 10 giây mặc định chỉ audit, không retry gửi/xóa/flags. Partial SMTP có validSent hoặc lỗi sau submit trả lỗi outcome chưa rõ; Client cảnh báo và xác nhận riêng trước resend, không tự retry. PEEK trước explicit Seen trong detail. Không ghi raw To/subject/body/attachment filename/credential. Crash trước durable outcome để lại PREPARED, cảnh báo unresolved, không tự suy diễn thành success. V7 thêm bảng riêng, không đổi schema lịch sử; không phục hồi audit đã mất trước V7. C001–041 native fixture đã kiểm chứng; Gmail live và contract literal AM-4323/4391 vẫn OPEN.

### Vòng D: bounded pending và replay fairness

Reserve slot trước durable intent; `mail.audit.local-capacity` mặc định 128/process, hợp lệ 1–4096. Hết slot trả 503 `AUDIT_UNAVAILABLE` trước remote mutation, không xóa acknowledgment cũ để nhận thao tác mới. Slot release khi outcome durable; PREPARED sau crash không tự suy success. CONFIRMED/UNCERTAIN durable replay theo keyset thời gian + ID với mốc trên mỗi sweep, tối đa 32 mỗi lượt; deferred local xoay vòng, lỗi DB unavailable ngừng batch. Không có persistent per-event backoff, không retry SMTP/IMAP. Tạo Trash fallback cũng sau PREPARED, lost CREATE reply là UNCERTAIN, không copy/move/retry. Admin status aggregate xem api-admin-contract.md; D001–027 native evidence, 43/43 BE targeted. Contract raw recipient/subject, implicit delete vẫn OPEN.

### Vòng E: V8 durable receipt và retry schedule (thay mô tả DB-only ở vòng D)

PREPARED vẫn phải durable trước mutation và reserve slot. Khi remote outcome được quan sát, receipt AES-GCM ghi local file, force và atomic rename nếu filesystem hỗ trợ, rồi cập nhật DB. Nếu DB outcome lỗi, giữ receipt cho recovery; startup chỉ restore receipt hợp lệ khớp intent hoặc event đã được audit, không suy diễn success từ PREPARED. Audit insert/delete outbox transactional; unique mailEventId và original mailActorId tương quan event, không retry SMTP/IMAP. Retry delivery lưu delivery_attempts/next_attempt_at trong DB V8; exponential backoff tối đa 300s khi DB ghi được lịch. DB unavailable vẫn bounded theo capacity/sweep. Crash trước durable receipt, journal/DB cùng hỏng hoặc mất dữ liệu không bảo đảm outcome; giữ unresolved và cảnh báo. E023–041 xác minh receipt + lịch retry qua process restart và đúng 2 audit/2 thư duy nhất. Không atomic MySQL–Gmail, không log raw To/subject, không implicit/global delete. Contract literal AM-4323/4391 vẫn OPEN.

## Vòng F — provider errors và receipt conflict safety

Wire contract không đổi. F204/F205: provider deny existing mailbox App Password -> HTTP422 GMAIL_AUTH_FAILED; F222: local OAuth refresh invalid_grant -> 422 GMAIL_NEEDS_REAUTH, persisted NEEDS_REAUTH. F213: SMTP fixture giao đúng một thư rồi trả lỗi -> 502 GMAIL_CONNECTION_ERROR, message chưa xác định kết quả, client phải giữ draft và kiểm trước khi gửi lại; không auto retry, audit MAIL_OPERATION_UNCERTAIN thay success. Không suy từ local sang Gmail live/raw lifecycle.

MailAuditRecoveryIntegrationTest 13/13 (3 test mới): journal fail closed khi write/remove lỗi, kiểm lock/root và cả hai sibling receipt/pending, inventory không đầy đủ không được tin; giữ conflict/corrupt, không tự xóa. Recovered receipt phải validate lại trước finalize; unreserved finish không biến PREPARED thành success, observation đầu tiên không bị ghi đè; replay chỉ audit, không SMTP/IMAP mutation. Runtime fault test H2 giữ PREPARED khi DB reject, ghi đúng một audit từ observation còn RAM khi DB hồi phục, không duplicate và chỉ mở admission khi receipt xử lý xong. Không suy thành bảo đảm mọi OS crash/disk fault hay atomic MySQL–Gmail.

Khi journal lỗi, admission mutation mới fail closed; replay observation đã biết chỉ ghi audit khi đủ evidence, không tự biến PREPARED thành CONFIRMED. Không xóa receipt mâu thuẫn để giả healthy, không atomic MySQL–Gmail. Runtime Docker volume chưa được chạy riêng. AM-4323 raw To/subject và AM-4391 literal implicit/global deletion vẫn chờ phê duyệt variant an toàn; không đổi wire hay nới privacy/UID guard.

## Vòng G2 — sau vòng F (kết quả hiện hành)

V9 native V8→V9 preservation: owner, dual methods, trạng thái và ciphertext synthetic giữ nguyên, revision=0; fresh V1–V9. JPA optimistic version chống stale write/delete; 409 GMAIL_ACCOUNT_CHANGED an toàn. OAuth cache ràng buộc grant/owner, publish sau save; reject scope giảm/invalid_grant chính xác, tránh refresh lặp, persist rotation; fallback chỉ active sau connect, failure không audit thành công, scoped SMTP authenticator, resource close khi save conflict. Local raw lifecycle và factory regression PASS, không chứng minh Gmail live, mọi race hoặc atomic revoke/DB. Audit journal V8 kế thừa không sửa/replay mutation.

Khi concurrent update/delete/relink hoặc probe dùng snapshot cũ bị optimistic conflict, lỗi JSON 409 GMAIL_ACCOUNT_CHANGED (code/errorCode) yêu cầu tải lại metadata; không tự retry mutation. revision nội bộ DB, không thêm credential/field vào DTO. NEEDS_REAUTH không chặn preferred APP_PASSWORD nếu còn cả hai method; App Password đơn lẻ NEEDS_REAUTH vẫn chặn. FallbackActive phản ánh kết nối fallback đã mở thành công, không bảo đảm lần sau thành công. Scope giảm hoặc invalid_grant → NEEDS_REAUTH; lỗi provider khác 502, không tự đánh dấu grant invalid. Các contract SMTP audit và delete an toàn không đổi.

## Vòng G3 — kết quả hiện hành

G3: MailboxRevisionChainIntegrationTest 2/2 trên H2/JPA thật (OSIV=false) và provider loopback: rotate refresh token commit, snapshot detached nhận đúng revision từ chính save; stale grant/cache bị từ chối trước provider; invalid_grant→NEEDS_REAUTH→fallback thực qua GreenMail có hai revision liên tiếp, đúng một audit mỗi action (mock audit), Store đóng; method bị gỡ thì snapshot cũ không mở raw/wrapper. Factory kiểm owner/id/revision trước credential/cache và sau open, đóng resource/lease nếu conflict. Không thay V9/schema, không retry mutation. Guard không bảo đảm thu hồi kết nối đã cấp hoặc atomic MySQL–Gmail.

409 GMAIL_ACCOUNT_CHANGED có thể xảy ra với snapshot cũ trước khi mở provider/cache hoặc sau open; tải lại metadata, không retry mutation. revision vẫn nội bộ, không thay DTO/schema. Client cleanup kiểm UID/UIDVALIDITY/index và dừng khi stale. AM-4323: contract literal audit To/subject không thể đạt mà vẫn cấm raw To/subject/body; giữ masked sender/count/metadata, không hash thay thế không được duyệt. AM-4340: V9 và schema additive giữ nguyên, bootstrap ngẫu nhiên BCrypt không plaintext console; literal fixed credential/schema cần quyết định. AM-4391: giữ explicit permanent/UIDVALIDITY/UID-targeted deletion, không implicit permanent hay global expunge. Ba phương án an toàn chưa được phê duyệt, giữ OPEN. AM-4361/4396/4397: source harness và local 7 MIME/DTO có evidence; live thật cả hai mode, 2FA/consent/remote TLS, marker/hash/cleanup và privacy vẫn thiếu điều kiện cấp quyền. Không tạo tài khoản/đăng nhập hoặc chạy live.

## Vòng G4 — kết quả hiện hành

G4: MailServiceImpl folderRamCache thêm revision cùng owner/mailboxId; invalidation tất cả revision của đúng owner/mailbox sau mutation. FolderCacheTest 2/2, test mới phân biệt hai revision và cache hit từng revision; TTL vẫn hữu hạn, không đảm bảo read/mutation nguyên tử. MailServiceFailureRegressionTest 22/22 và MailServiceGreenMailIntegrationTest 10/10 chạy lại vì service thay đổi. Không đổi V9, bootstrap, audit journal, OAuth refresh/fallback hay credential/permission path.

G4 R01–R12: observer CLI tương tác từng lệnh trên factory thật từ JAR G4, GreenMail mới loopback; account/password/AES synthetic ngẫu nhiên chỉ trong process, không app login/DB. Raw Store/Transport chưa connect khi cấp; caller connect/close, INBOX READ_ONLY total=2/unread=2, Folder close không đóng Store; FolderNotFoundException và caller abort trước send đều đóng scope. Final storeOpen=false/transportOpen=false, fixtureMessages=2, quit exit0, root removed. Không SMTP DATA fault mới, không suy isConnected thành đo mọi socket OS; không HTTP/Postman/native Swing/Gmail live mới.

Ba quyết định D chưa được duyệt: AM-4323 SEND_MAIL metadata-only (EP-M5 của thiết kế gốc cũng yêu cầu metadata; §4 literal To/subject mâu thuẫn privacy), giữ masked sender/count, không raw/hash To/subject/body; AM-4340 bootstrap random/BCrypt, không console plaintext, V1–V9 additive bảo toàn data; AM-4391 explicit permanent=true trong Trash/UIDVALIDITY/UID-targeted, không implicit permanent/global expunge. Không dùng approval để giảm invariant; chỉ chủ dự án xác nhận contract thay thế, không tự sửa §4. AM-4361/4396/4397 vẫn cần live cho manual/contract/privacy riêng.

Không đổi HTTP DTO/response schema hay permanent-delete contract hiện hành.

## G5 — 26/11/2026

G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu.

Ticket là guard phía Client, không khóa atomic với request; không thu hồi request đã dispatch. Không thêm HTTP contract, không đổi Backend/bootstrap/DB schema hay live preparation. Không lặp thao tác credential bị gate, không giả session/token, không yêu cầu người khác làm thay.

Ba contract chưa duyệt: AM-4323-C.03 metadata-only (masked sender và counts; không raw To/subject) cần quyết định D, C.04 live vẫn L. AM-4340-C.03 cần D cho bootstrap ngẫu nhiên/BCrypt/force-change, không plaintext console, schema additive V1–V9; local MySQL/seed đã có evidence, có thể đóng riêng contract sau duyệt rõ scope. AM-4391-C.02 cần D cho permanent=true rõ ràng + UIDVALIDITY/UID-targeted MOVE/EXPUNGE, default 204 (Prefer 200 tùy chọn), không global expunge; C.03 live vẫn L. Không tự sửa §4 hoặc coi code hiện có là duyệt.

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

## G6 — 26/11/2026

PASS manual local theo §4/EP-M1 và chuẩn local đã dùng cho AM-4321/4322: H015/H084/H125, F056, G002/G054–057, T004–008, F204/F222 và raw G4 R02–R05/R09. Count/owner/error/connect/close có observation; cache/revision có regression. 02 C2-TV4 và 03 M08 demo Gmail web/count/special-use/TLS vẫn chưa đạt; giữ 4320-M.05 S/L tại AM-4361-M.02 và AM-4397-M.04. Không claim nghiệm thu Gmail thật.

PASS manual local multipart đúng hành vi đang triển khai: H027/H040/H042/H120/H136–140, F013/F014/F064–065/F069–070, G045–063/G079–080, J001–005/J009–011, T009–013, F205/F213–217, C/D/E, G4 R06–R08/R10. Không tính setup H036 hoặc hash lỗi trước fix. Có direct HTTP từng request (không đổi nhãn thành Postman), delivery/read-back/bytes local và no retry khi uncertain. Giữ 4323-M.05 S/L trong 4323-C.04: Gmail thật cả hai auth mode, delivery/read-back/attachment, TV5. Contract 4323-C.03 D về audit metadata-only vẫn OPEN, không phê duyệt raw To/subject.

Ba contract D chưa duyệt: AM-4323-C.03 dùng audit metadata-only (masked sender/count, không raw To/subject/body), C.04 vẫn L; duyệt D chỉ giải quyết phần contract chưa đủ live. AM-4340-C.03 bootstrap ngẫu nhiên/BCrypt/force-change, không plaintext console, schema additive V1–V9; local MySQL/seed đã có evidence, owner duyệt rõ thì có thể đóng criterion contract và task. AM-4391-C.02 permanent=true rõ ràng, UIDVALIDITY, UID-targeted MOVE/EXPUNGE, default 204 (Prefer 200 tùy chọn), không global expunge; C.03 vẫn L. Giữ an toàn, không tự duyệt/đổi §4.

Không thay Backend/JAR, bootstrap, schema, API contract, permission, JWT/RBAC/PKCE/TLS/UIDVALIDITY/audit/recovery. Guard Client không thu hồi request đã dispatch hoặc consent remote. Không retry hành vi credential bị chặn, không đổi công cụ, worker, fake session/token, private state hoặc yêu cầu người khác làm thay. Native desktop vẫn thiếu điều kiện xác nhận; không chạy lại C/D2/E1. Không có live/human sign-off mới.

## Phê duyệt contract — 26/11/2026 (sau G6)

78 task / 468 tiêu chí: 298 PASS, 116 N/A, 54 OPEN; 30 task đóng, 48 task còn OPEN. 298/352 tiêu chí áp dụng = 84,659%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 5 contract, 3 privacy/live. 171 ID: 81 K/M kế thừa + 3 A phê duyệt = 84 nghĩa vụ đã thỏa; 83 trực tiếp thiếu + 4 shared S = 87 còn thiếu.

Chủ dự án phê duyệt 26/11/2026, sau G6: AM-4323-C.03 A — SEND_MAIL metadata-only (masked sender/counts, không raw/hash To/subject/body), C.04 vẫn L cả hai mode Gmail, delivery/read-back/attachments, shared 4323-M.05. AM-4340-C.03 A — bootstrap từ ADMIN_INITIAL_PASSWORD/cấu hình, BCrypt và forced-change, không log plaintext, không ghi đè Admin, Flyway V1–V9 additive; với evidence MySQL/seed kế thừa, contract và task đã PASS. AM-4391-C.02 A — explicit permanent=true trong Trash, UIDVALIDITY, targeted UID MOVE/UIDPLUS, default 204, Prefer 200 tùy chọn, fail-closed; C.03 vẫn L Gmail Trash/All Mail thật. Không có D còn chờ; phê duyệt không thay bằng chứng live, human/manual, test hay triển khai.

Không chạy test mới, không thay source/JAR, giữ JUnit 410/415 (5 live SKIP), Newman 653/653 kế thừa và E2E 16/16 kế thừa. Mọi mô tả D/chờ phê duyệt ở các mốc trước là lịch sử, không phải residual hiện hành.
