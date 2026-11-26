# [BE] Hỗ trợ xoá liên kết OAuth2

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M06] Liên kết Gmail bằng OAuth2 |
| Owner | TV3 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 05/11/2026 → 06/11/2026 |
| Ước lượng | 2h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Hỗ trợ xoá liên kết OAuth2` thuộc **[M06] Liên kết Gmail bằng OAuth2** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-G3 DELETE /api/gmail-accounts/{id}`

- **Path:** `/api/gmail-accounts/{id}`
- **Method:** DELETE
- **Role:** AUTHENTICATED | **Category:** Gmail Account

**Mô tả:** Xoá liên kết Gmail; OAuth2 thì revoke token trước; log UNLINK_GMAIL.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

DELETE /api/gmail-accounts/{id}
- JWT required; tìm `gmailAccountRepository.findByIdAndUserId(id, userId)` → không thấy → 404 NOT_FOUND
- Nếu `authType=OAUTH2`: gọi `OAuth2Service.revoke(refreshToken)` của TV3 — bọc try/catch, revoke lỗi vẫn xoá (log warn)
- Delete record (KHÔNG xoá mail trên Gmail — Gmail giữ nguyên)
- Log `UNLINK_GMAIL` `{email, authType}`
- Response 204 No Content
- Test: xoá Gmail của mình → 204; xoá id không tồn tại → 404; xoá của user khác → 404 (không lộ info)

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** DELETE toàn bộ liên kết như AM-4315, tìm theo id và userId, 404 NOT_FOUND cả foreign/missing, 204 No Content khi thành công. Revoke mọi hộp thư có OAuth2 bất kể authType ưu tiên, decrypt refresh token phía server, xóa cache; lỗi revoke audit REVOKE_TOKEN_FAILED, vẫn xóa local; UNLINK_GMAIL có authType, không xóa nội dung thư.
- **Khác biệt:** thêm DELETE /api/gmail-accounts/{id}/methods/OAUTH2 chỉ gỡ OAuth2 và giữ App Password nếu có, gỡ method cuối cần confirmUnlink=true (409 CONFIRM_UNLINK_REQUIRED nếu thiếu); endpoint theo ID cũng trả 404 ACCOUNT_NOT_FOUND cho foreign/missing.
- **Mã nguồn:** `controller/GmailAccountController.java#deleteGmailAccount` và removeLoginMethod/revokeBestEffort, `oauth2/OAuth2ServiceImpl.java#revokeToken`.
- **Test source:** `controller/GmailAccountControllerTest.java` (delete own/foreign/missing/revoke failure), `oauth2/OAuth2MockWebServerTest.java#testRevokeTokenSuccess`, `service/GmailMultiAuthIntegrationTest.java#removeMethodsLifecycleAndLastMethodGuard`. Revoke thật trên Google chưa kiểm chứng.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

- **Bổ sung vòng G2 (sau F):** Invalidation cache chỉ theo grant tương ứng; V9 ngăn ghi lại method đã gỡ. Không suy thành khóa đồng thời remote revoke.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailAccountControllerTest: 15/15 PASS; OAuth2MockWebServerTest: 21/21 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Xóa toàn bộ liên kết trả 204, revoke token trước khi xóa best-effort; có thêm gỡ từng phương thức.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Unlink/revoke không phải link mới; ALLOW_*_LINK không cấm gỡ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (phạm vi local đã kiểm chứng). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4318 (H refs trong hàng AM-4318, OBSERVED): H060 owner DELETE OAuth id 4 204; H061 list rỗng. Tổng revoke fixture 2 do agent chính báo, gồm unlink và cascade theo chuỗi nghiệp vụ. Retest bản cuối: [F081](../backend-final-observations.md) (OBSERVED): USER khác DELETE OAuth link id3 404 NOT_FOUND.; [F082](../backend-final-observations.md) (OBSERVED): Owner DELETE OAuth link id3 204 body 0 byte.; [F083](../backend-final-observations.md) (OBSERVED): Owner list 200 còn đúng id2 APP_PASSWORD ACTIVE, OAuth id3 biến mất. Đây là mailbox khác được giữ, không phải gỡ method khỏi cùng mailbox.; [F084](../backend-final-observations.md) (OBSERVED): GET admin logs lọc owner/action 200 đúng 1 UNLINK_GMAIL actor id4, detail authType=OAUTH2, hasOAuth=true, hasAppPassword=false; không ghi token/credential. F018 missing ID404, F081 foreign404, F082 own204, F083 giữ mailbox khác; H103–106 App Password unlink/relink giữ mail count. Prime xác nhận provider manual aggregate revokes=2 cho unlink/cascade.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G041–044, G090: revoke bị provider fixture từ chối nhưng unlink 204 và App Password link còn, hai revoke fault được đếm độc lập. Phần còn lại hiện tại: Giữ PASS local; revoke Google thật riêng. Tách riêng human sign-off, live, contract và kết quả tự động. **Vòng G2:** Invalidation cache chỉ theo grant tương ứng; V9 ngăn ghi lại method đã gỡ. Không suy thành khóa đồng thời remote revoke. Không đổi checkbox.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M06] Liên kết Gmail bằng OAuth2**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
