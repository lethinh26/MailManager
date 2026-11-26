# [FE] Màn hình đăng ký

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M03] Client nền tảng & Màn hình vào hệ thống |
| Owner | TV1 |
| Platform | UserWeb |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 30/10/2026 → 31/10/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Màn hình đăng ký` thuộc **[M03] Client nền tảng & Màn hình vào hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `RegisterFrame extends JFrame`: username, password, confirm password, fullName, email
- Validation client: password==confirm, email regex, username không trống
- POST `/api/auth/register` → 201: JOptionPane "Đăng ký thành công" → quay LoginFrame
- 409 username trùng → highlight field username + message
- 403 REGISTRATION_DISABLED → "Đăng ký đã bị tắt" + disable form
- Chỉ hiện nút Đăng ký từ LoginFrame nếu `registrationEnabled=true`

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** RegisterFrame kiểm tra username 3–64 (chữ/số/_), email/họ tên ≤255 (đồng bộ RegisterRequest BE), email đúng định dạng, họ tên không rỗng, mật khẩu tối thiểu 8 ký tự/chữ+số, tối đa 72 byte UTF-8 và xác nhận; POST register trong SwingWorker, 409 USERNAME_TAKEN highlight username, 409 EMAIL_TAKEN highlight email, REGISTRATION_DISABLED khóa form; callback về LoginFrame.
- **Test liên quan:** PasswordValidatorTest, ApiClientTest, AuthRegisterAndChangePasswordTest, ClientLiveServerE2ETest; cần thao tác form thủ công.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** Giới hạn BCrypt 72 byte UTF-8 ở cả FE/BE; preflight public config; disable khi đăng ký/bảo trì, xóa mật khẩu khi flag tắt; SwingFeatureAcceptanceTest#openRegisterFormRespondsToFlagChangesAndClearsPasswords.

- **Bổ sung G5:** G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AuthRegisterAndChangePasswordTest: 12/12 PASS; ApiClientTest: 5/5 PASS; ClientLiveServerE2ETest: 16/16 PASS; PasswordValidatorTest: 7/7 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ: đăng ký không đụng Gmail; REGISTER audit và registration flag không miễn.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_REGISTRATION. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4350 (M05, PARTIAL): Click đăng ký từ Login; submit trống → "Tên đăng nhập phải từ 3 đến 64 ký tự..."; password confirm khác → "[X] Mật khẩu xác nhận chưa khớp"/lỗi mismatch; sửa confirm → "Đang tạo tài khoản..." form disabled, modal "Đăng ký thành công tài khoản 'ui_manual_reg'!", OK prefill username Login. Ảnh M05-register-success. Bổ sung: [swing-supplemental-observations.md](../swing-supplemental-observations.md) S02: tên 256 ký tự bị reject, 255 ký tự qua validation tên, email sai báo lỗi; S03/S04 disable/re-enable form. Phần còn lại hiện tại: Còn duplicate username/email, password 72-byte/confirm, stale submit 403 và đăng ký hợp lệ; không retry credential bị chặn. Tách riêng human sign-off, live, contract và kết quả tự động. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E regression: public config null/error fail-closed, register/OAuth UI ẩn khi chưa biết flag hoặc maintenance, clear password/disable form khi mất config, worker kiểm session/dispose trước submit/callback. Client 60/60 gồm E2E16 và SwingFeatureAcceptanceTest13; không manual auth. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4350-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4350-m). **G5:** G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M03] Client nền tảng & Màn hình vào hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
