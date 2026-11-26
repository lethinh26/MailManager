# [BE] Chuẩn bị Gmail test + entity

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M04] Liên kết Gmail bằng App Password |
| Owner | TV2 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 26/10/2026 → 29/10/2026 |
| Ước lượng | 2h |
| Priority | 1 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Chuẩn bị Gmail test + entity` thuộc **[M04] Liên kết Gmail bằng App Password** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `GmailAccount` entity + `GmailAccountRepository extends JpaRepository`
- Custom queries: `findByUserId(Long)`, `findByIdAndUserId(Long, Long)`, `countByUserId(Long)`, `existsByUserIdAndEmailIgnoreCase(Long, String)`
- DTO `GmailAccountResponse {id, email, displayName, authType, status, createdAt}`
- Validation schema cho `LinkAppPasswordRequest {email, appPassword}` (`@NotBlank`, `@Email`)


**Chuẩn bị Gmail test (PREP):**

- Tạo Gmail test account dùng cho dev/demo
- Bật 2FA + sinh App Password tại `myaccount.google.com/apppasswords`
- Ghi vào `.env.example` + doc `dev-setup.md` (KHÔNG commit password thật)
- Verify `javamail` `Store.connect("imap.gmail.com",993,email,appPassword)` bằng test standalone trước khi code module chính

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Entity/DTO hiện tại:** `Mail Server/src/main/java/com/mailapp/model/GmailAccount.java`, `repository/GmailAccountRepository.java` có findByUserId/findByIdAndUserId/countByUserId, chuẩn hóa email_key và existsByUserIdAndEmailKey thay IgnoreCase đơn giản; `dto/GmailAccountResponse.java` giữ các trường gốc và bổ sung preferredMethod/activeMethod/methods/fallbackActive/outcome, không trả secret. `dto/LinkAppPasswordRequest.java` validation NotBlank/Email/Size và toString che password. `controller/GmailAccountController.java#linkGmailWithAppPassword` check feature/limit, test IMAP trước AES encrypt/upsert; `service/GmailAccountLinkService.java` audit nghiệp vụ.
- **Test có trong repo:** `GmailAccountControllerTest`, `GmailMultiAuthIntegrationTest`, `EmailNormalizerTest`, `FlywayV3MigrationTest`, `DefaultMailConnectionFactoryGreenMailTest`, `RealGmailIntegrationTest`. Kết quả chạy tại §5.
- **Ownership:** `GmailAccountController.java#requireOwnedAccount` và `MailServiceImpl.java#loadAndValidateAccount` dùng findByIdAndUserId, Mail API trả cùng 404 GMAIL_NOT_OWNED cho ID không tồn tại hoặc thuộc người khác; endpoint quản lý tài khoản theo contract EP-G; ADMIN giữ quyền chẩn đoán kết nối đã có. Các test controller/multi-auth và `MailServiceFailureRegressionTest#foreignAndMissingMailboxesAreIndistinguishableWithoutImapAccess` đối chiếu ràng buộc này.
- **PREP còn cần xác minh thực tế:** không khẳng định đã tạo Gmail test/2FA/App Password hoặc chạy live Store.connect. Cơ chế hiện tại dùng `Mail Server/scripts/test-real-gmail.ps1` và `Mail Server/docs/mail-connection-factory.md` (biến GMAIL_TEST_EMAIL/GMAIL_TEST_APP_PASSWORD, prompt an toàn) thay ghi credential vào .env.example; hai biến test được mô tả ở contract, không cần lưu giá trị. Đã bổ sung dev-setup.md và placeholder trong .env.example, không chứa credential. Manual Postman/live cần tài khoản test riêng được chủ dự án cấp; không ghi password/token thật.

- **An toàn live test:** cần opt-in `MAILAPP_REAL_GMAIL=1` và Gmail test riêng. Script test-real-gmail.ps1 yêu cầu xác nhận TEST-ACCOUNT; chỉ expunge đúng UID thư test. Thiết lập theo `Mail Server/docs/dev-setup.md` và placeholder `.env.example`.

- **Đối chiếu bổ sung:** Rà soát N/A: feature/audit riêng của task entity/PREP không áp dụng; yêu cầu link ở AM-4314 vẫn bắt buộc flag, ownership và audit. Không có chứng cứ đã tạo Gmail test, bật 2FA, cấp App Password hoặc Store.connect thành công. Không truy cập DB/Gmail/credential trong lượt này; cần chủ dự án cấp test account và thực hiện opt-in hướng dẫn dev-setup.

- **Bổ sung vòng G2 (sau F):** V9 thêm revision nội bộ, stale write/delete bị từ chối, owner + dual methods bảo toàn qua native V8→V9; DTO không thêm credential. Không tạo Gmail thật hoặc standalone remote connect.

- **Bổ sung G3:** V9 preflight metadata owner/id/revision, detached update giữ own revision; không thay schema/credential DTO; Gmail test/standalone thật còn thiếu.

- **Bổ sung G4:** G4 R02–R05/R09 raw standalone local đạt; test source opt-in bổ sung raw connect/close remote, chỉ compile; account/2FA/live chưa có.

- **Bổ sung G6:** Giữ shared 4359-M.03: dedicated account, 2FA/App Password, standalone Store.connect/close/probe remote và privacy. Không suy từ raw local.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 1 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 3 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailAccountControllerTest: 15/15 PASS; DefaultMailConnectionFactoryGreenMailTest: 10/10 PASS; RealGmailIntegrationTest: 0/2 PASS, 2 SKIP (2 SKIPPED); MailServiceFailureRegressionTest: 22/22 PASS; FlywayV3MigrationTest: 1/1 PASS; GmailMultiAuthIntegrationTest: 9/9 PASS; EmailNormalizerTest: 3/3 PASS; RealGmailXOAuth2IntegrationTest: 0/2 PASS, 2 SKIP (2 SKIPPED); RealGmailMimeFixturesTest: 0/1 PASS, 1 SKIP (1 SKIPPED).

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec — OPEN. Entity/DTO/unique, link fixture; runner App Password opt-in, dedicated Gmail test account/2FA/App Password/standalone Store.connect chưa có chứng cứ live. **G4:** G4 R02–R05/R09 raw standalone local đạt; test source opt-in bổ sung raw connect/close remote, chỉ compile; account/2FA/live chưa có. [AM-4396-C ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4396-c).
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Chuẩn bị entity/tài khoản fixture, không flag/audit riêng; API link có nghĩa vụ ở AM-4314/4317.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Chuẩn bị entity/tài khoản fixture, không flag/audit riêng; API link có nghĩa vụ ở AM-4314/4317.
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log — OPEN. Dedicated live opt-in, masking/safe-stage, không đọc thư tùy ý; chỉ thư synthetic marker/UID, không in secret, không xuất raw To/subject/body. RealGmailIntegrationTest 2/2 SKIP; RealGmailXOAuth2IntegrationTest 2/2 SKIP; RealGmailMimeFixturesTest 1/1 SKIP. Chưa quan sát response/log và quyền trên Gmail test thật; fixture/code không thay thế kiểm chứng live. **G4:** G4 R02–R05/R09 raw standalone local đạt; test source opt-in bổ sung raw connect/close remote, chỉ compile; account/2FA/live chưa có. [AM-4396-P ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4396-p).
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4396 (H refs trong hàng AM-4396, BLOCKED LIVE): H105 create link id 6 ACTIVE và H106 mailbox fixture mở được; không phải tài khoản Gmail thật. Phần còn lại: Tạo mailbox test riêng, 2FA/App Password, liên kết thật, chứng minh auth/unique và không lộ credential. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — GmailAccountControllerTest selftest lỗi Inbox đọc/close không báo imapOk giả hoặc cập nhật lastVerified; rehearsal MIME synthetic. Chưa có Gmail chuyên test được cấp quyền và bằng chứng link thật. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): F201/F204/F209/F224: metadata linked fixture, provider deny/recovery và OAuth NEEDS_REAUTH tách biệt; không thực hiện tạo Gmail chuyên test/2FA/App Password/standalone Store.connect thật. Không thay trạng thái checkbox. **Vòng G2:** V9 thêm revision nội bộ, stale write/delete bị từ chối, owner + dual methods bảo toàn qua native V8→V9; DTO không thêm credential. Không tạo Gmail thật hoặc standalone remote connect. Không đổi checkbox. **Vòng G3:** V9 preflight metadata owner/id/revision, detached update giữ own revision; không thay schema/credential DTO; Gmail test/standalone thật còn thiếu. Không đổi checkbox. **G4:** G4 R02–R05/R09 raw standalone local đạt; test source opt-in bổ sung raw connect/close remote, chỉ compile; account/2FA/live chưa có. [AM-4396-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4396-m). **G6:** Giữ shared 4359-M.03: dedicated account, 2FA/App Password, standalone Store.connect/close/probe remote và privacy. Không suy từ raw local.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M04] Liên kết Gmail bằng App Password**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
