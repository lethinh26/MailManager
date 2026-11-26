# [FE] Main.java + UIManager system L&F + entry mở LoginFrame

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M03] Client nền tảng & Màn hình vào hệ thống |
| Owner | TV1 |
| Platform | UserWeb |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 29/10/2026 |
| Ước lượng | 0.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Main.java + UIManager system L&F + entry mở LoginFrame` thuộc **[M03] Client nền tảng & Màn hình vào hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `LoginFrame extends JFrame`: form username + `JPasswordField`, nút "Đăng nhập", link "Chưa có tài khoản? Đăng ký" (ẩn nếu `publicConfig.registrationEnabled=false`), link "Quên mật khẩu" (placeholder)
- Bấm Đăng nhập → `SwingWorker` gọi `ApiClient.post("/api/auth/login")` → success: lưu token vào SessionStore, gọi `/api/auth/me`, nếu `mustChangePassword` → `ChangePasswordDialog`, else → `MainFrame`
- Error: `ApiException` → `JOptionPane` hiện `message`; ACCOUNT_DISABLED → text riêng "Tài khoản bị khoá"
- Disable nút Đăng nhập trong lúc gọi API + show "Đang đăng nhập..."
- Bảo trì: nếu `publicConfig.maintenanceMode=true` → hiện banner "Hệ thống đang bảo trì"

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** Main.main: system L&F, Theme.installUiDefaults, SwingUtilities.invokeLater → SplashWindow → LoginFrame. Giữ Splash/DPI/theme hiện có; không bỏ luồng entry.
- **Đối chiếu §4 gốc:** nội dung §4 của task entry trùng AM-4349 (đăng nhập). Không bỏ các yêu cầu username/password, loading, lỗi/khóa tài khoản, maintenance, /me và đổi mật khẩu bắt buộc; đối chiếu AM-4349, không chỉ test entry. UI hiện dùng lỗi inline thay JOptionPane theo thiết kế mới. E2E 13 tạo LoginFrame trực tiếp, không đi qua Main.main/SplashWindow; entry vật lý vẫn cần M01/M04 tại `architechture/docs/swing-acceptance-cases.md`.
- **Test liên quan:** UiLayoutRegressionTest, UiAccessibilityAndThemeTest; cần kiểm tra khởi động trực quan.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** ClientLiveServerE2ETest: 16/16 PASS; UiAccessibilityAndThemeTest: 2/2 PASS; UiLayoutRegressionTest: 6/6 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ O: startup/login không chạm Gmail; vẫn có LOGIN/LOGIN_FAILED và maintenance/registration của §4 copy.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE, ALLOW_REGISTRATION. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Backend ghi LOGIN/LOGIN_FAILED; Client đi qua API và test liên quan.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4346 (M01, PARTIAL): Khởi chạy Main.main Java 21 thực, LoginFrame active 940×650, L&F Windows; ảnh M01-login. Khởi động lại process về LoginFrame không mang session. Bổ sung: [swing-supplemental-observations.md](../swing-supplemental-observations.md) S01: LoginFrame 940×650 trên Client hiện tại; đổi light/dark và state quan sát được. Bổ sung vòng 61: [swing-supplemental-observations.md](../swing-supplemental-observations.md) R2/R3: Main.main/Splash thật, Windows L&F DPI 125%; malformed cert startup chặn LoginFrame. Sửa min-size clipping; 440×580 cuộn tới ping/status và username đủ bounds. Phần còn lại hiện tại: Còn native keyboard/DPI khác và các nhánh login copied §4 (AM-4349), human sign-off; startup/splash/min-size/cert public đã quan sát. Tách riêng human sign-off, live, contract và kết quả tự động. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E regression: public config null/error fail-closed, register/OAuth UI ẩn khi chưa biết flag hoặc maintenance, clear password/disable form khi mất config, worker kiểm session/dispose trước submit/callback. Client 60/60 gồm E2E16 và SwingFeatureAcceptanceTest13; không manual auth. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4346-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4346-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M03] Client nền tảng & Màn hình vào hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
