# [FE] Lưu phiên đăng nhập

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M03] Client nền tảng & Màn hình vào hệ thống |
| Owner | TV1 |
| Platform | UserWeb |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 30/10/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Lưu phiên đăng nhập` thuộc **[M03] Client nền tảng & Màn hình vào hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `SessionStore` singleton: `token`, `userInfo {id,username,role,mustChangePassword}`, `publicConfig` (từ EP-P1)
- Lưu token in-memory (KHÔNG persist — user yêu cầu đăng nhập lại mỗi lần mở app là OK, hoặc persist vào file encrypted nếu spec yêu cầu)
- `logout()`: xoá token + userInfo, đóng MainFrame → mở LoginFrame
- `getAuthorizationHeader()` → `"Bearer " + token` hoặc null

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** SessionStore singleton lưu JWT, UserDto, PublicConfigDto trong RAM; getAuthorizationHeader, clearSession; MainFrame đóng worker và trở về LoginFrame khi logout/401. Cache file cách ly theo thế hệ phiên.
- **Test liên quan:** SessionStoreTest, OAuthFlowAndSessionTest, AcceptanceRegressionTest#uniqueDownloadsAndSessionCacheNeverOverwriteOrLeakAcrossSessions.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** Logout, hết phiên và hủy dialog bắt buộc đổi mật khẩu xóa session; client mới cách ly JWT. AcceptanceRegressionTest#retiredClientNeverInheritsNextUsersToken.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AcceptanceRegressionTest: 12/12 PASS; OAuthFlowAndSessionTest: 5/5 PASS; SessionStoreTest: 3/3 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: lưu phiên/cache scoped generation, không truy vấn Gmail tự thân; A giữ: logout RAM không có action audit riêng trong §4; F giữ: SessionStore chỉ lưu publicConfig, caller áp dụng flags, không tự policy.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Feature của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: lưu phiên/cache scoped generation, không truy vấn Gmail tự thân; A giữ: logout RAM không có action audit riêng trong §4; F giữ: SessionStore chỉ lưu publicConfig, caller áp dụng flags, không tự policy.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: lưu phiên/cache scoped generation, không truy vấn Gmail tự thân; A giữ: logout RAM không có action audit riêng trong §4; F giữ: SessionStore chỉ lưu publicConfig, caller áp dụng flags, không tự policy.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4348 (M03, PARTIAL): Profile→Đăng xuất No giữ MainFrame ui_manual(USER); Yes trở về LoginFrame trống. Login ui_other→chỉ card ui_other, INBOX 13 thư (12 seed + thư CC mới), không card ui_manual; process mới lại LoginFrame. Phần còn lại: Không khóa live session/401 hoặc race worker, không xác minh config/log toàn bộ; cache cross-session chưa test vật lý. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M03] Client nền tảng & Màn hình vào hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
