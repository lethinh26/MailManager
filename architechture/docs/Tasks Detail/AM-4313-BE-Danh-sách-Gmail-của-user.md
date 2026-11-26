# [BE] Danh sách Gmail của user

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M04] Liên kết Gmail bằng App Password |
| Owner | TV2 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 03/11/2026 → 04/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Danh sách Gmail của user` thuộc **[M04] Liên kết Gmail bằng App Password** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-G1 GET /api/gmail-accounts`

- **Path:** `/api/gmail-accounts`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Gmail Account

**Mô tả:** Danh sách Gmail của chính user; không trả credential.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/gmail-accounts
- JWT required; filter `gmailAccountRepository.findByUserId(currentUser.id)`
- Response 200 array: `[{id, email, displayName, authType, status, createdAt}]` — KHÔNG trả `appPasswordEncrypted`, `refreshTokenEncrypted`
- Test: user A link 2 Gmail → thấy 2; user B (khác) → thấy 0

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GET /api/gmail-accounts 200 lấy findByUserId từ principal; mỗi hộp thư một dòng. DTO giữ id/email/displayName/authType/status/createdAt, mở rộng emailKey, preferredMethod, activeMethod, fallbackActive, methods.hasOAuth/hasAppPassword, tokenExpiresAt, lastVerifiedAt; outcome/message nullable. Không trả credential/token. authType là phương thức ưu tiên.
- **Khác biệt:** hỗ trợ nhiều phương thức trên một hộp thư thay vì coi từng authType là tài khoản riêng; cùng địa chỉ Gmail có thể liên kết độc lập bởi hai user. Không ghi audit cho đọc metadata vì §4 không yêu cầu.
- **Mã nguồn:** `controller/GmailAccountController.java#getMyGmailAccounts`, `dto/GmailAccountResponse.java`, `repository/GmailAccountRepository.java`.
- **Test source:** `controller/GmailAccountControllerTest.java#testGetMyGmailAccounts`, `service/GmailMultiAuthIntegrationTest.java` (idorBlockedAndTwoUsersCanLinkSameGmail, getMyGmailAccountsReturnsMetadataWithoutSecrets).

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailAccountControllerTest: 15/15 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). EP-G1 chỉ list metadata theo owner, không có toggle list/audit read riêng.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). EP-G1 chỉ list metadata theo owner, không có toggle list/audit read riêng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4313 (H refs trong hàng AM-4313, PARTIAL): H013–014 danh sách own App Password id 2, owner khác rỗng; H053 OAuth id 4 chỉ trong danh sách owner; H061/H104 rỗng sau unlink, H105 id 6 khi tạo lại; H144 owner độc lập có id 1. Retest bản cuối: [F008](../backend-final-observations.md) (OBSERVED): GET gmail-accounts owner 200, chính xác 2 link: id2 APP_PASSWORD ACTIVE, id3 OAUTH2 ACTIVE; metadata methods, không có trường password/refresh token thô trong summary.; [F009](../backend-final-observations.md) (OBSERVED): User ui_manual khác 200 [] trong cùng phiên, F008 owner có đúng 2 mailbox khác nhau; đạt đúng Test 2/0 của §4. H013–014/H144 đã có đối chiếu owner trước đó.. Phần còn lại: Scope GET list local đầy đủ; Google/Gmail thật và kiểm secrets toàn bộ response/log là phạm vi khác, human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M04] Liên kết Gmail bằng App Password**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
