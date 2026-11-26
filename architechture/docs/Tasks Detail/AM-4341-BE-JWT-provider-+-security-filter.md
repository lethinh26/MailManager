# [BE] JWT provider + security filter

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M01] Hạ tầng Backend & Bảo mật nền tảng |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 26/10/2026 → 27/10/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] JWT provider + security filter` thuộc **[M01] Hạ tầng Backend & Bảo mật nền tảng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `JwtProvider`: HS256, secret từ env `MAILAPP_JWT_SECRET` (≥32 byte), claims `{sub: username, uid, role}`, expiration 24h
- `generate(User)`, `validate(token) -> Claims` (catch ExpiredJwtException, MalformedJwtException, SignatureException)
- `JwtFilter extends OncePerRequestFilter`: parse `Authorization: Bearer`, verify, load `UserDetails`, set SecurityContext; nếu `enabled=false` hoặc bảo trì → reject
- `SecurityConfig`: `csrf.disable()`, `sessionManagement.stateless()`, public endpoints `/api/auth/*`, `/api/public/**`, `/api/oauth2/callback`, `/actuator/health`; `/api/admin/**` chỉ ADMIN; còn lại `authenticated()`
- `JwtAuthenticationEntryPoint` trả 401 đúng ErrorDto
- Test: gọi `/api/auth/me` với Bearer hợp lệ → 200; không token → 401; token fake → 401

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** JwtProvider HS256, secret ít nhất 32 byte từ MAILAPP_JWT_SECRET (alias JWT_SECRET), hạn 24h, parse/validate chữ ký/expiry; JwtAuthenticationFilter OncePerRequestFilter tải DB user/role/enabled, denylist tùy chọn. USER có JWT hợp lệ bị 503 MAINTENANCE_MODE ở endpoint bảo vệ; locked/missing/invalid token 401; ADMIN truy cập /api/admin/** và /api/dev/**; session stateless, CSRF tắt.
- **Khác biệt bảo mật:** JWT mới sub=username, uid số, identityVersion=2, username/role/jti; JWT legacy không version với numeric-sub hợp lệ và uid khớp (nếu có) vẫn được nhận; v2 kiểm tra username hiện tại từ DB, phân biệt username toàn chữ số với ID; từ chối version lạ hoặc identity mâu thuẫn; bắt buộc HS256, hạn dùng; chỉ allowlist login/register/Google login start-poll/public/callback/health/info/docs, không permitAll /api/auth/*; /me/change-password phải xác thực. MustChangePasswordFilter chặn 403 PASSWORD_CHANGE_REQUIRED. Entry point/denied handler JSON có path/code/errorCode. Dev secret ngẫu nhiên nếu thiếu, non-dev fail-fast. Denylist mặc định tắt.
- **Mã nguồn:** `security/JwtProvider.java`, `security/JwtAuthenticationFilter.java`, `security/SecurityConfig.java`, `security/MustChangePasswordFilter.java`, `src/main/resources/application.properties`.
- **Test source:** `security/JwtProviderTest.java`, `security/JwtAuthenticationFilterTest.java#expiredCorrectlySignedJwtCannotAccessMe` (JWT đúng chữ ký nhưng hết hạn), `security/EndpointSecurityAuditTest.java`, `controller/AdminIntegrationTest.java#maintenanceRejectsExistingUserJwtButNotAdminOrPublic`, `controller/AuthRegisterAndChangePasswordTest.java` (/me, bắt đổi mật khẩu), `DotEnvAliasPrecedenceTest.java` (legacy .env không ghi đè MAILAPP_*). HTTPS localhost được kiểm tra bằng Server JAR và Client E2E; triển khai/credential thật cần xác minh. JwtProviderTest#legacyNumericSubjectsAndNewNumericUsernamesCannotBeConfused kiểm tra legacy, uid mâu thuẫn, username số và version sai.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.


- **Bằng chứng cập nhật:** JWT v2 và regression legacy; bỏ UserDetailsServiceAutoConfiguration để không tạo/log mật khẩu user mặc định.
## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** DotEnvAliasPrecedenceTest: 1/1 PASS; AdminIntegrationTest: 18/18 PASS; AuthRegisterAndChangePasswordTest: 12/12 PASS; EndpointSecurityAuditTest: 3/3 PASS; JwtAuthenticationFilterTest: 9/9 PASS; JwtProviderTest: 6/6 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). JWT v2 sub=username, uid/role/expiry, legacy ID token vẫn đọc an toàn; giữ allowlist chặt bảo vệ /me và change-password, không tạo user Spring mặc định. Newman auth/role và JwtProviderTest/EndpointSecurityAuditTest đạt.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). JWT/filter thực thi auth/maintenance; Gmail owner và audit mutation ở service.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE, role/enabled. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). JWT/filter thực thi auth/maintenance; Gmail owner và audit mutation ở service.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4341 (H refs trong hàng AM-4341, OBSERVED): H003–005 JWT missing/invalid/valid, H012 expiresIn=86400; H034 ADMIN gate, H067/H069 lock/unlock session, H071 đổi role, H078 forced-change gate, H081 deleted session 401; H132 maintenance USER JWT 503, H133 ADMIN JWT 200. Phần còn lại: Scope Test của §4 đã quan sát; expiry/claim/legacy, allowlist đầy đủ và kiểm không ghi JWT vẫn là phạm vi kiểm chứng riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M01] Hạ tầng Backend & Bảo mật nền tảng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
