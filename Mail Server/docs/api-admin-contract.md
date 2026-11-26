# Hợp Đồng API Quản Trị Hệ Thống (Admin Module — EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2)

> **Base URL:** `https://localhost:8443`  
> **Xác thực bắt buộc:** `Authorization: Bearer <JWT>` với vai trò `ROLE_ADMIN`.  
> - Không có token / token hết hạn / tài khoản bị khóa → `401 UNAUTHORIZED`.  
> - Có token hợp lệ nhưng `role != ADMIN` → `403 FORBIDDEN`.  
> **Ranh giới bảo mật tuyệt đối:** Module Admin **KHÔNG** cung cấp bất kỳ API nào để đọc nội dung email, tải tệp đính kèm, hoặc xem mật khẩu / OAuth2 token của người dùng.

> **Đối chiếu:** Tài liệu mô tả mã nguồn hiện tại, kể cả các mở rộng so với đặc tả gốc. Mật khẩu tạm mới sinh trong response tạo/reset là ngoại lệ có chủ đích, không thể đọc lại mật khẩu cũ. Các test dẫn dưới đây là bằng chứng mã nguồn, chưa xác nhận chạy thành công trong lần cập nhật tài liệu này.

---

## 1. Quy ước lỗi chung & phân trang

Phản hồi lỗi JSON từ handler/filter ứng dụng chứa đồng thời `"code"`, `"errorCode"` và `"path"`:

```json
{
  "timestamp": "2026-11-22T18:10:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Không thể thực hiện thao tác lên Quản trị viên đang hoạt động cuối cùng của hệ thống.",
  "code": "LAST_ADMIN",
  "errorCode": "LAST_ADMIN",
  "path": "/api/admin/users/15/lock"
}
```

Các mã lỗi đặc thù nhóm Admin:
- `400 VALIDATION_ERROR`: Dữ liệu đầu vào sai định dạng, vượt ngưỡng cấu hình, hoặc cố tắt đồng thời cả `ALLOW_OAUTH2_LINK` và `ALLOW_APP_PASSWORD_LINK`.
- `400 SELF_ACTION_FORBIDDEN`: Admin cố tự khóa, tự đổi vai trò (kể cả gửi lại ADMIN), hoặc tự xóa chính mình.
- `404 USER_NOT_FOUND`: Không tìm thấy người dùng theo `id`.
- `409 USERNAME_TAKEN`: Trùng tên đăng nhập khi tạo người dùng mới.
- `409 EMAIL_TAKEN`: Email đã tồn tại (không phân biệt hoa thường).
- `400 LAST_ADMIN`: Cố khóa, hạ quyền, hoặc xóa tài khoản `ADMIN` đang hoạt động cuối cùng.

`400 LAST_ADMIN` theo đặc tả, bảo vệ Admin cuối cùng. Khi đang bảo trì, USER có JWT hợp lệ gọi endpoint bảo vệ nhận 503 `MAINTENANCE_MODE` trước kiểm tra quyền ADMIN. ADMIN bị bắt buộc đổi mật khẩu nhận 403 `PASSWORD_CHANGE_REQUIRED` khi gọi API nghiệp vụ.

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
Username 3–64 ký tự chữ ASCII, số, gạch dưới; không trùng không phân biệt hoa thường. Email hợp lệ tối đa 100 ký tự, chuẩn hóa chữ thường và duy nhất; họ tên tối đa 100 ký tự. Role mặc định USER, nếu truyền role phải là ADMIN/USER.
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
Nếu cung cấp password thì phải đáp ứng chính sách tối thiểu 8 ký tự có chữ và số, tối đa 72 byte UTF-8. Mật khẩu lưu BCrypt. `temporaryPassword=null` khi Admin đã cung cấp mật khẩu; không ghi mật khẩu vào audit hoặc log.
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
  Mỗi phần tử có `{id,username,fullName,email,role,enabled,mustChangePassword,gmailCount,createdAt,updatedAt}`. Repository dùng JPQL và Pageable (tương đương bộ lọc Specification trong thiết kế), đếm Gmail theo user.

### EP-AD3: `GET /api/admin/users/{id}`
Chi tiết 1 người dùng kèm `gmailCount` và danh sách tài khoản Gmail đã liên kết (chỉ metadata: `id`, `email`, `displayName`, `authType`, `status`, `createdAt`, `lastVerifiedAt` — **KHÔNG** chứa token hay App Password).
Trả 200 `AdminUserDetail` gồm các trường của summary và `gmailAccounts`; thiếu ID trả 404 `USER_NOT_FOUND`. `authType` là phương thức ưu tiên của hộp thư đa phương thức.

### EP-AD4: `PUT /api/admin/users/{id}/lock`
Khóa tài khoản (`enabled = false`).
- Chặn tự khóa (`400 SELF_ACTION_FORBIDDEN`) và khóa ADMIN cuối cùng (`400 LAST_ADMIN`).
- JWT hiện hành của user bị từ chối ở request kế tiếp trong thời gian khóa (vì `JwtAuthenticationFilter` kiểm tra `user.isEnabled()` trên từng request).
- Transaction khóa danh sách ID Admin hoạt động ở DB trước khi kiểm tra bảo vệ Admin cuối cùng. Response 200 `AdminUserSummary` (mở rộng của `{id,enabled:false}`).
- **Audit:** `ADMIN_LOCK_USER`.

### EP-AD5: `PUT /api/admin/users/{id}/unlock`
Mở khóa tài khoản (`enabled = true`). **Audit:** `ADMIN_UNLOCK_USER`.
Response 200 `AdminUserSummary`; 404 `USER_NOT_FOUND` nếu thiếu ID. JWT cũ còn hạn có thể được chấp nhận lại khi mở khóa; thao tác khóa không phải thu hồi token vĩnh viễn.

### EP-AD6: `PUT /api/admin/users/{id}/role`
Đổi vai trò (`{ "role": "ADMIN" | "USER" }`).
- Chặn mọi tự đổi vai trò (`400 SELF_ACTION_FORBIDDEN`) và hạ quyền ADMIN hoạt động cuối cùng (`400 LAST_ADMIN`). Dùng cùng cơ chế khóa DB như EP-AD4.
- Response 200 `AdminUserSummary`; vai trò thực tế được đọc từ DB trên request JWT kế tiếp.
- **Audit:** `ADMIN_CHANGE_ROLE` (ghi rõ vai trò cũ → mới).

### EP-AD7: `PUT /api/admin/users/{id}/reset-password`
Đặt lại mật khẩu bằng mật khẩu tạm sinh ngẫu nhiên an toàn (`SecureRandom`, 14 ký tự gồm chữ hoa, chữ thường, chữ số, ký tự đặc biệt), bật `mustChangePassword = true`.
Hỗ trợ body tùy chọn `{ "newPassword": "..." }` theo đặc tả. Nếu cung cấp, kiểm chính sách tối thiểu 8 ký tự có chữ và số, tối đa 72 byte UTF-8; không trả lại mật khẩu (temporaryPassword=null). Nếu không gửi body, server sinh mật khẩu tạm. Cả hai trường hợp bật mustChangePassword và trả Cache-Control: no-store.
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
Xóa người dùng và xóa cascade toàn bộ liên kết Gmail (với mọi hộp thư có refresh token OAuth2, kể cả `APP_PASSWORD` ưu tiên, gọi `OAuth2Service.revokeToken(...)` dạng best-effort trước khi xóa khỏi DB). Lỗi revoke ghi audit `REVOKE_TOKEN_FAILED` và vẫn xóa local; không xóa mail trên Gmail.
- Chặn tự xóa (`400 SELF_ACTION_FORBIDDEN`) và xóa ADMIN cuối cùng (`400 LAST_ADMIN`).
- **Response:** `204 No Content`.
- **Audit:** `ADMIN_DELETE_USER`.
  Detail ghi actor, target ID, username và gmailCount. Nhật ký cũ và settings được giữ, tham chiếu user/updatedBy bị gỡ để bảo toàn FK; không còn username của user đã xóa trong kết quả join nhật ký cũ.

---

## 3. Nhóm Cấu Hình Hệ Thống (`EP-S1`, `EP-S2`)

### EP-S1: `GET /api/admin/settings`
Trả 200 mảng đúng 9 khóa cấu hình được cho phép, sắp xếp tăng dần theo key; không trả khóa lạ/nhạy cảm. Mỗi item có `{key,value,type,minValue,maxValue,description,updatedByUsername,updatedBy,updatedAt}`, trong đó `updatedBy` là alias của `updatedByUsername`. Khi DB thiếu khóa, dùng mặc định.

| Khóa (`key`) | Kiểu | Khoảng hợp lệ | Mặc định |
| :--- | :---: | :---: | :---: |
| `ALLOW_REGISTRATION` | `BOOLEAN` | `true` / `false` | `true` |
| `ALLOW_OAUTH2_LINK` | `BOOLEAN` | `true` / `false` | `true` |
| `ALLOW_APP_PASSWORD_LINK` | `BOOLEAN` | `true` / `false` | `true` |
| `ALLOW_SEND_MAIL` | `BOOLEAN` | `true` / `false` | `true` |
| `MAINTENANCE_MODE` | `BOOLEAN` | `true` / `false` | `false` |
| `MAX_GMAIL_PER_USER` | `INTEGER` | `1..50` (chỉ giữ 0 nếu DB đang lưu 0) | `5` |
| `MAX_ATTACHMENT_SIZE_MB` | `INTEGER` | `1..50` | `25` |
| `MAIL_PAGE_SIZE` | `INTEGER` | `10..100` | `20` |
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
  - Khóa hai hàng cấu hình phương thức liên kết ở DB và tính giá trị hiệu lực từ dữ liệu đã khóa, tránh hai request đồng thời tắt cả hai.
  - Ghi `updated_by` là Admin đang thao tác, cập nhật timestamp, invalidate cache sau commit thành công (không công bố giá trị transaction rollback), ghi audit `ADMIN_CHANGE_SETTING` kèm giá trị cũ và mới cho từng khóa thay đổi.
  - Trả 200 danh sách mới cùng schema EP-S1. `MAX_GMAIL_PER_USER=0` đã tồn tại vẫn giữ nghĩa không giới hạn; có thể gửi lại 0 khi lưu các thiết lập khác nhưng không thể đặt quota dương thành 0 (400 `VALIDATION_ERROR`). EP-S1/S2 trả `minValue=0` chỉ khi giá trị lưu hiện tại là 0, còn lại là 1. `MAIL_PAGE_SIZE=20` mặc định khi chưa có cấu hình; không ghi đè giá trị đã lưu. `LOGIN_MAX_FAILED_ATTEMPTS` thực sự điều khiển rate limiter; `mailPageSize` xuất hiện trong public config và giá trị mặc định phân trang mail.

---

## 4. Nhóm Thống Kê & Nhật Ký (`EP-ST1`, `EP-ST2`)

### EP-ST1: `GET /api/admin/stats`
Trả 200. `totalAdmins` gồm cả ADMIN bị khóa; `gmailByOAuth2` và `gmailByAppPassword` đếm theo phương thức ưu tiên (`authType`), mỗi hộp thư đa phương thức chỉ được tính một lần. Registration tính theo ngày máy chủ (hôm nay và 7/30 ngày lịch gồm hôm nay). `failedLoginsLast24h` đếm action `LOGIN_FAILED`, không phải mọi lỗi OAuth2/rate limit; các ngày gửi mail không có log trả count=0.
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
- **Query params:** `userId`, `username` (contains, không phân biệt hoa thường), `action` (chuỗi con literal, không phân biệt hoa thường; hỗ trợ alias lịch sử LOGIN_SUCCESS/LOGIN, DELETE_MAIL/MAIL_DELETE, SETTING_CHANGE/ADMIN_CHANGE_SETTING và các sự kiện OAuth2 cũ), `from` (`yyyy-MM-dd` hoặc ISO-8601 local datetime), `to` (cùng định dạng), `page`, `size` (tối đa 100). Ngày không có giờ dùng đầu/cuối ngày tương ứng.
- **Response 200:** `PageResponse<AdminAuditLogItem>`, phần tử `{id,userId,username,action,detail,ipAddress,createdAt}`, sort createdAt giảm dần rồi id giảm dần. User đã xóa có thể có userId/username null.
- **`GET /api/admin/logs/export`**: Xuất tối đa 10.000 dòng CSV UTF-8 có BOM (`Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="audit-logs.csv"`). Hàm csvCell thay xuống dòng bằng khoảng trắng, thêm tiền tố `'` cho giá trị bắt đầu bằng `=`, `+`, `-`, `@` hoặc tab và escape dấu nháy kép để chống CSV Formula Injection.

## 5. Bằng chứng và phạm vi kiểm chứng

- Mã nguồn (tương đối với `Mail Server/src/main/java/com/mailapp/`): `controller/AdminController.java`, `service/impl/AdminServiceImpl.java`, `dto/admin/AdminDtos.java`, `repository/UserRepository.java`, `repository/SystemSettingRepository.java`, `repository/AuditLogRepository.java`, `service/impl/SystemSettingServiceImpl.java`, `security/JwtAuthenticationFilter.java`, `security/LoginRateLimiter.java`. Migration `src/main/resources/db/migration/V4__seed_missing_settings.sql` bổ sung khóa chưa có; `src/main/java/db/migration/V5__UserIdentityConstraints.java` mở rộng username và ràng buộc identity, không tự xóa/gộp user nếu DB cũ trùng.
- Test source: `src/test/java/com/mailapp/controller/AdminIntegrationTest.java` (RBAC, tạo trùng, list/detail không secret, bảo trì, khóa/mở khóa/role và cạnh tranh Admin cuối, reset/delete/revoke đa phương thức, principal cũ không ghi đè khóa/reset/hạ quyền, settings và cạnh tranh hai flag, public config, stats/log/CSV); `AuthRegisterAndChangePasswordTest.java` cùng package (audit LOGIN_FAILED, dynamic rate limit); `src/test/java/com/mailapp/migration/FlywayUserIdentityMigrationTest.java` (seed và unique identity); `src/test/java/com/mailapp/service/SystemSettingServiceTest.java` (default/cache/update). Các thao tác ghi User khóa và refresh hàng từ DB qua `repository/UserLockRepositoryImpl.java`; không merge principal cũ để đổi mật khẩu.
- Còn cần kết quả chạy bộ test, kiểm thử migration và khóa đồng thời trên MySQL thật, Postman cho toàn bộ endpoint và revoke Google thật. H2/mock không thay thế xác minh production. V5 cần xử lý thủ công dữ liệu identity trùng trước nâng cấp nếu phát hiện; migration cố ý không chọn tài khoản để gộp.

### Bổ sung kiểm chứng transaction/audit

Audit tham gia transaction DB nghiệp vụ: lỗi ghi/flush làm transaction rollback, không nuốt lỗi trong transaction đã rollback-only. B007–009 native MySQL cô lập reject INSERT audit, settings trả 500 INTERNAL_ERROR, MAIL_PAGE_SIZE và audit count giữ nguyên; B021–025 restore, update/reset 200 và thêm đúng hai audit rows. Log success chỉ sau commit. Với external revoke không cam kết rollback side effect đã xảy ra ở Google; không suy transaction DB thành phân tán atomic. Các kết quả migration V2–V6, E2E concurrent MySQL và Newman đã ghi riêng ở test matrix, không còn là test chưa chạy; revoke Google thật/human sign-off/last-admin độc lập còn OPEN.


## Phạm vi transaction audit sau V7

MailAuditRecovery chỉ cho external SEND_MAIL/MAIL_DELETE/MAIL_FLAG_CHANGE; không áp dụng cho mutation DB của Admin. Audit DB-only vẫn thuộc transaction nghiệp vụ, lỗi flush làm rollback. Các action audit mail đã xác nhận xuất hiện sau drain; trong outage có thể trễ, không ghi success giả, không retry thao tác thư. MAIL_OPERATION_UNCERTAIN là trạng thái chưa rõ, không phải thành công. Không cam kết atomic với Google revoke. Xem C001–041 và residual AM-4344.

## Audit outbox operational status (vòng D)

`GET /api/admin/mail-audit/status` yêu cầu ADMIN hợp lệ, response `200` JSON và `Cache-Control: no-store`; 401 khi thiếu phiên, 403 khi USER (D001–003). Trường `prepared`, `confirmed`, `uncertain`, `stalePrepared` (PREPARED quá 10 phút), `oldestPreparedAt`, `oldestReadyAt` (nullable), `localInFlight`, `localDeferred`, `localCapacity`. Ba count trạng thái từ DB; local counters theo process, có thể chồng lên DB count, không cộng để suy tổng. Không trả actor, ID sự kiện, nội dung, secret. Không có API resolve/mark-success/retry; PREPARED cũ không chứng minh gửi thành công hay thất bại.

## Vòng E — kiểm quyền trong transaction và inspection audit V8

Các mutation tạo/khóa/mở khóa/đổi role/reset/xóa user và update settings kiểm tra lại actor enabled ADMIN, không mustChangePassword, sau khi lấy khóa Admin. Actor bị mất quyền trong lúc chờ lock trả 403 FORBIDDEN; không ghi audit success cho mutation bị từ chối. E008–022 native overlap xác nhận 200/403 và 4 audit thành công, không thay guard self/last-admin.

`GET /api/admin/mail-audit/events?outcome=PREPARED|CONFIRMED|UNCERTAIN&page=0&size=20` chỉ ADMIN; outcome tùy chọn, invalid 400 VALIDATION_ERROR, size giới hạn 1..100, page không âm. Response 200 no-store gồm content, page, size, totalElements, totalPages; item eventId, actorId, action, outcome, occurredAt, stalePrepared, deliveryAttempts, nextAttemptAt (nullable). Không detail mail/credential/IP. Status aggregate bổ sung receiptRecoveryEnabled/receiptJournalAvailable, local counters không cộng vào DB counts. Audit log DTO bổ sung mailEventId/mailActorId tùy chọn khi có correlation; lịch sử cũ không đổi, CSV contract giữ nguyên. Không có endpoint resolve/mark-success/resend. E001–004/E029–039 thực tế đã xác minh.

## Vòng F — CSV Client và receipt error state

API server giữ nguyên. CSV export snapshot filter trước chooser; single-flight, reject existing file thay vì overwrite, chỉ text/csv, temp file và move không replace sau guard session/generation/role/disposed; xóa temp, không publish stale response. AdminModuleClientTest5/5: existing file không request/không đổi, response hết guard không tạo target/part. Không chứng minh native chooser/viewer, role-loss manual; không thu hồi file đã export hợp lệ trước logout. Legacy overload giữ tương thích; GUI truyền guard current-session; không coi file hợp lệ đã export trước logout là file phải thu hồi. Journal status/admission báo unavailable khi runtime receipt failure/conflict chưa được xử lý, không ghi audit giả. Audit inspection giữ ADMIN/no-store như E. Exact-two last-admin race chưa quan sát; không thay bootstrap hoặc suy từ E actor recheck.

## Vòng G2 — sau vòng F (kết quả hiện hành)

V9 mailbox revision chống stale write/delete khi quản trị và cập nhật liên kết cạnh tranh; optimistic conflict được map 409 GMAIL_ACCOUNT_CHANGED, cần reload trạng thái. AdminIntegrationTest 18/18. Không suy thành atomic revoke–DB hoặc bằng chứng exact-two last-admin; không đổi bootstrap.

## Vòng G3 — kết quả hiện hành

V9 không đổi. Stale mailbox owner/id/revision bị từ chối trước provider và khi lưu; không tự phê duyệt exact-two last-admin, không suy atomic revoke–DB.

## Vòng G4 — kết quả hiện hành

G4: Enter chỉ mở detail khi một row; detail delete/cleanup lấy đúng UID đang hiển thị và cùng UIDVALIDITY/lựa chọn, không biến toolbar thành batch. Context/keyboard batch vẫn explicit. Auto-clean dùng cùng requiresReauthBeforeMail cho dual-auth. AdminFrame read workers kiểm generation/disposed; mutation kiểm ADMIN/session trước request, sau confirm và response; single-flight user mutation, recheck target, không publish/copy temp password ở session cũ, clear field khi đóng, không close/cancel create khi pending. Settings guard trước/sau confirm. MailboxRegressionTest 11/11 (detail target regression), SwingFeatureAcceptanceTest 14/14 (late user rows sau dispose); không fake session, không thao tác clipboard/credential trực tiếp. Backend vẫn là authority; guard không thu hồi request đã gửi.

Không đổi server role/last-admin/transaction/bootstrap, không manual exact-two mới.

## G5 — 26/11/2026

G5 xác nhận 4344-M.04 M/K theo D017–027 (restart, PREPARED1/localDeferred0, read-back cùng UID đúng một thư, không thêm audit success) và F MailAuditRecoveryIntegrationTest 13/13. Không auto-finalize/replay mutation; sự cố mất durable evidence vẫn cần đối chiếu nghiệp vụ được phép. Không yêu cầu tạo fault mới để chứng minh lại. AM-4344-M còn 4344-M.02: LOGIN_FAILED và ADMIN_RESET_PASSWORD manual (H).

Ba contract chưa duyệt: AM-4323-C.03 metadata-only (masked sender và counts; không raw To/subject) cần quyết định D, C.04 live vẫn L. AM-4340-C.03 cần D cho bootstrap ngẫu nhiên/BCrypt/force-change, không plaintext console, schema additive V1–V9; local MySQL/seed đã có evidence, có thể đóng riêng contract sau duyệt rõ scope. AM-4391-C.02 cần D cho permanent=true rõ ràng + UIDVALIDITY/UID-targeted MOVE/EXPUNGE, default 204 (Prefer 200 tùy chọn), không global expunge; C.03 live vẫn L. Không tự sửa §4 hoặc coi code hiện có là duyệt.

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

## G6 — 26/11/2026

G6: 4311-M.02 chuyển M/K theo bản ghi JSON đã scrubbed H005/H071/F026: GET /api/auth/me 200, đúng 7 key id, username, fullName, email, role, mustChangePassword, createdAt; không extra key, createdAt parse được, mustChangePassword boolean false. H029/H069/H133/F032 cũng đúng shape. Redactor giữ key, chỉ che value. Không truy cập raw/private manifest, không thực hiện login mới. 4311-M.03 vẫn H: expiry401 và forced-change /me thực.

Ba contract D chưa duyệt: AM-4323-C.03 dùng audit metadata-only (masked sender/count, không raw To/subject/body), C.04 vẫn L; duyệt D chỉ giải quyết phần contract chưa đủ live. AM-4340-C.03 bootstrap ngẫu nhiên/BCrypt/force-change, không plaintext console, schema additive V1–V9; local MySQL/seed đã có evidence, owner duyệt rõ thì có thể đóng criterion contract và task. AM-4391-C.02 permanent=true rõ ràng, UIDVALIDITY, UID-targeted MOVE/EXPUNGE, default 204 (Prefer 200 tùy chọn), không global expunge; C.03 vẫn L. Giữ an toàn, không tự duyệt/đổi §4.

Không thay Backend/JAR, bootstrap, schema, API contract, permission, JWT/RBAC/PKCE/TLS/UIDVALIDITY/audit/recovery. Guard Client không thu hồi request đã dispatch hoặc consent remote. Không retry hành vi credential bị chặn, không đổi công cụ, worker, fake session/token, private state hoặc yêu cầu người khác làm thay. Native desktop vẫn thiếu điều kiện xác nhận; không chạy lại C/D2/E1. Không có live/human sign-off mới.

## Phê duyệt contract — 26/11/2026 (sau G6)

78 task / 468 tiêu chí: 298 PASS, 116 N/A, 54 OPEN; 30 task đóng, 48 task còn OPEN. 298/352 tiêu chí áp dụng = 84,659%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 5 contract, 3 privacy/live. 171 ID: 81 K/M kế thừa + 3 A phê duyệt = 84 nghĩa vụ đã thỏa; 83 trực tiếp thiếu + 4 shared S = 87 còn thiếu.

Chủ dự án phê duyệt 26/11/2026, sau G6: AM-4323-C.03 A — SEND_MAIL metadata-only (masked sender/counts, không raw/hash To/subject/body), C.04 vẫn L cả hai mode Gmail, delivery/read-back/attachments, shared 4323-M.05. AM-4340-C.03 A — bootstrap từ ADMIN_INITIAL_PASSWORD/cấu hình, BCrypt và forced-change, không log plaintext, không ghi đè Admin, Flyway V1–V9 additive; với evidence MySQL/seed kế thừa, contract và task đã PASS. AM-4391-C.02 A — explicit permanent=true trong Trash, UIDVALIDITY, targeted UID MOVE/UIDPLUS, default 204, Prefer 200 tùy chọn, fail-closed; C.03 vẫn L Gmail Trash/All Mail thật. Không có D còn chờ; phê duyệt không thay bằng chứng live, human/manual, test hay triển khai.

Không chạy test mới, không thay source/JAR, giữ JUnit 410/415 (5 live SKIP), Newman 653/653 kế thừa và E2E 16/16 kế thừa. Mọi mô tả D/chờ phê duyệt ở các mốc trước là lịch sử, không phải residual hiện hành.
