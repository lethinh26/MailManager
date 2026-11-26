# [BE] Xoá liên kết Gmail

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M04] Liên kết Gmail bằng App Password |
| Owner | TV2 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 05/11/2026 → 06/11/2026 |
| Ước lượng | 2.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Xoá liên kết Gmail` thuộc **[M04] Liên kết Gmail bằng App Password** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

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

- **Thực hiện:** DELETE /api/gmail-accounts/{id} tìm theo id + userId, ID không tồn tại và ID người khác cùng 404 NOT_FOUND. Revoke best-effort mọi liên kết có OAuth2, kể cả App Password ưu tiên; lỗi ghi REVOKE_TOKEN_FAILED rồi xóa local. UNLINK_GMAIL detail gồm email/authType/hasOAuth/hasAppPassword; không xóa mail Gmail.
- **Khác biệt:** trả 204 No Content và 404 NOT_FOUND cho cả ID thiếu và của user khác, đúng §4; không thay đổi revoke best-effort.
- **Mã nguồn:** `controller/GmailAccountController.java#deleteGmailAccount` và revokeBestEffort, `repository/GmailAccountRepository.java#findByIdAndUserId`, `oauth2/OAuth2ServiceImpl.java#revokeToken`.
- **Test source:** `controller/GmailAccountControllerTest.java` (testDeleteOwnGmailAccountSuccess, testUserCannotDeleteAnotherUsersGmail, testDeleteNonExistentAccount, testDeleteProceedsEvenWhenRevokeFails), `service/GmailMultiAuthIntegrationTest.java#idorBlockedAndTwoUsersCanLinkSameGmail`. Revoke Google thật chưa được xác minh.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Bổ sung vòng G2 (sau F):** V9 optimistic lock ngăn stale delete xóa mailbox đã relink. Test repository H2, không phải atomic revoke–DB hoặc Google live.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailAccountControllerTest: 15/15 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Xóa toàn bộ liên kết trả 204, missing/foreign trả 404 NOT_FOUND; revoke best-effort và audit.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Unlink không bị ALLOW_*_LINK chặn; ownership và UNLINK_GMAIL vẫn bắt buộc.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4315 (H refs trong hàng AM-4315, OBSERVED): H103 DELETE owner 204, H104 list rỗng, H105 relink 201 id mới, H106 INBOX 61/61 unread và Trash 0 sau relink, dữ liệu fixture còn. H060–061 OAuth unlink. Retest bản cuối: [F017](../backend-final-observations.md) (OBSERVED): User ui_other DELETE /api/gmail-accounts/2 của owner khác 404 NOT_FOUND, thông báo chung.; [F018](../backend-final-observations.md) (OBSERVED): Owner DELETE ID không tồn tại 9999999 404 NOT_FOUND cùng thông báo với foreign ID.; [F019](../backend-final-observations.md) (OBSERVED): Owner GET list 200 vẫn đủ id2 và id3 ACTIVE sau hai request từ chối. Kết hợp H060–061 owner OAuth unlink 204/list rỗng, H103–106 App Password unlink 204/relink và mail count giữ nguyên: đủ 204/404/404 đúng Test §4 trên fixture, không xóa thư thật.; [F084](../backend-final-observations.md) (OBSERVED): F081–084 xác minh thêm unlink OAuth own204/foreign404, list còn mailbox App Password và UNLINK_GMAIL audit; F017–019 missing/foreign denied và owner list không đổi, H103–106 App Password unlink/relink giữ mail count. Prime báo manual provider revokes=2 cho unlink/cascade.. Phần còn lại: Scope local §4 204/404/404 + audit đủ; còn revoke failure best-effort và Google thật; human sign-off. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. **Vòng G2:** V9 optimistic lock ngăn stale delete xóa mailbox đã relink. Test repository H2, không phải atomic revoke–DB hoặc Google live. Không đổi checkbox.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M04] Liên kết Gmail bằng App Password**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
