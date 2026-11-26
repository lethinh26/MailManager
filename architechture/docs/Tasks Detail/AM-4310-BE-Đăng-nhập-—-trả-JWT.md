# [BE] Đăng nhập — trả JWT

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M02] Xác thực tài khoản hệ thống |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 28/10/2026 → 30/10/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Đăng nhập — trả JWT` thuộc **[M02] Xác thực tài khoản hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-A2 POST /api/auth/login`

- **Path:** `/api/auth/login`
- **Method:** POST
- **Role:** PUBLIC | **Category:** Authentication

**Mô tả:** Đăng nhập {username,password} -> JWT; kiểm tra enabled + MAINTENANCE_MODE; log LOGIN/LOGIN_FAILED.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

POST /api/auth/login
- Body: `{username, password}`
- Tìm user; sai → 401 `{code:"UNAUTHORIZED", message:"Sai tài khoản hoặc mật khẩu"}` + log `LOGIN_FAILED`
- `enabled=false` → 403 ACCOUNT_DISABLED
- `MAINTENANCE_MODE=true` và user.role != ADMIN → 403 với message bảo trì
- Đúng → `JwtProvider.generate()`, log `LOGIN`
- Response 200: `{accessToken, tokenType:"Bearer", expiresIn:86400, user:{id, username, fullName, email, role, mustChangePassword}}`
- Test: login OK → dùng token gọi /api/auth/me; user bị khoá → 403; maintenance mode → 403 với USER, OK với ADMIN

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** POST login 200 trả JWT HS256/Bearer, 86400 giây mặc định, UserDto có createdAt; hỗ trợ username hoặc email. BCrypt, dummy hash khi user không tồn tại, LOGIN_FAILED lưu được khi AppException từ chối; LOGIN khi thành công (filter LOGIN_SUCCESS cũ vẫn tìm được LOGIN mới). Rate limit cặp định danh/IP, ngưỡng LOGIN_MAX_FAILED_ATTEMPTS động, cửa sổ mặc định 15 phút, 429 + Retry-After. Login nhận email tới 255 ký tự; JWT mới sub=username, uid số, identityVersion=2; JWT numeric-sub cũ vẫn hợp lệ khi ID không mâu thuẫn.
- **Khác biệt:** 401 UNAUTHORIZED với thông báo "Sai tài khoản hoặc mật khẩu", 403 ACCOUNT_DISABLED (chỉ khi mật khẩu đúng), 403 MAINTENANCE_MODE cho USER, đúng §4; ADMIN vẫn đăng nhập được. JWT đã cấp vẫn bị 503 khi USER gọi endpoint bảo vệ. Password vượt 72 byte UTF-8 trả 401 chung, rate limit/audit mà không gọi BCrypt. Token thiếu/sai tại endpoint bảo vệ vẫn 401 UNAUTHORIZED. Không đồng nhất khóa tạm rate limit với enabled=false.
- **Mã nguồn:** `controller/AuthController.java#login`, `service/impl/AuthServiceImpl.java#login`, `security/LoginRateLimiter.java`, `security/JwtProvider.java`.
- **Test source:** `service/AuthServiceTest.java` (overlongUnicodePasswordIsRejectedBeforeBcryptOrLookup), `controller/AuthControllerIntegrationTest.java` (login success/401/403/429), `controller/AuthRegisterAndChangePasswordTest.java#rejectedLoginAuditPersistsAndRateLimitTracksSettings`, `controller/AdminIntegrationTest.java#maintenanceRejectsExistingUserJwtButNotAdminOrPublic`.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

- **Bổ sung G3:** 401 từ Gmail Manager/Compose được chuyển về MainFrame và xóa session/token trước modal thông báo; không có login manual mới.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AuthControllerIntegrationTest: 14/14 PASS; AuthRegisterAndChangePasswordTest: 12/12 PASS; AuthServiceTest: 9/9 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đúng UNAUTHORIZED, ACCOUNT_DISABLED, 403 bảo trì lúc login, JWT và audit LOGIN; JWT đã cấp vẫn bị chặn 503 bảo trì.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Login/password chỉ xác thực user, không truy cập Gmail.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE và LOGIN_MAX_FAILED_ATTEMPTS. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Ghi LOGIN/LOGIN_FAILED; lỗi login lưu audit; filter tương thích LOGIN_SUCCESS cũ.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4310 (H refs trong hàng AM-4310, PARTIAL): H012 login user vừa tạo 200, Bearer, expiresIn=86400; H077 login mật khẩu tạm 200; H067 session khóa 401. Phần còn lại: Login bằng email, sai mật khẩu, tài khoản khóa 403 ở endpoint login, rate limit 429, bảo trì USER/ADMIN và hết hạn JWT. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — Bảo vệ privacy: LOGIN_FAILED với identity chưa tồn tại chỉ ghi UNKNOWN_USER, không lưu tên gửi vào. Hai suite AuthRegisterAndChangePasswordTest/AuthServiceTest đã reverify 21/21; không phải manual login mới. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. **Vòng G3:** 401 từ Gmail Manager/Compose được chuyển về MainFrame và xóa session/token trước modal thông báo; không có login manual mới. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4310-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4310-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M02] Xác thực tài khoản hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
