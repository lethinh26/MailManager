# [FE] Màn hình đăng nhập

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
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Màn hình đăng nhập` thuộc **[M03] Client nền tảng & Màn hình vào hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `LoginFrame extends JFrame`: form username + `JPasswordField`, nút "Đăng nhập", link "Chưa có tài khoản? Đăng ký" (ẩn nếu `publicConfig.registrationEnabled=false`), link "Quên mật khẩu" (placeholder)
- Bấm Đăng nhập → `SwingWorker` gọi `ApiClient.post("/api/auth/login")` → success: lưu token vào SessionStore, gọi `/api/auth/me`, nếu `mustChangePassword` → `ChangePasswordDialog`, else → `MainFrame`
- Error: `ApiException` → `JOptionPane` hiện `message`; ACCOUNT_DISABLED → text riêng "Tài khoản bị khoá"
- Disable nút Đăng nhập trong lúc gọi API + show "Đang đăng nhập..."
- Bảo trì: nếu `publicConfig.maintenanceMode=true` → hiện banner "Hệ thống đang bảo trì"

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** LoginFrame username/password, SwingWorker, nút loading, /auth/me, đổi mật khẩu bắt buộc trước MainFrame, trạng thái lỗi/429 và placeholder Quên mật khẩu liên hệ Admin. Giữ hiển thị lỗi inline dễ đọc thay dialog; hỗ trợ Google OAuth login.
- **Test liên quan:** AcceptanceRegressionTest, UiAccessibilityAndThemeTest, ClientLiveServerE2ETest; dialog đổi mật khẩu và thao tác tay cần kiểm tra.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** Login kiểm tra /me trước công bố phiên, xử lý UNAUTHORIZED/ACCOUNT_DISABLED; audit LOGIN. ChangePasswordDialog chỉ nhận kết quả thuộc generation hiện tại.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AcceptanceRegressionTest: 12/12 PASS; ClientLiveServerE2ETest: 16/16 PASS; UiAccessibilityAndThemeTest: 2/2 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ: login/đổi password không đụng Gmail; role/auth guard vẫn cần.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE, LOGIN_MAX_FAILED_ATTEMPTS. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Backend ghi LOGIN/LOGIN_FAILED; Client đi qua API và test liên quan.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4349 (M04, PARTIAL): Login trống → "Vui lòng nhập tên đăng nhập"; Quên mật khẩu? → dialog liên hệ Quản trị viên. Nhập sai fixture password → "Đang đăng nhập..." nút disable, sau đó "Sai tên đăng nhập hoặc mật khẩu"; nhập đúng → MainFrame USER. Bổ sung: [swing-supplemental-observations.md](../swing-supplemental-observations.md) S01 connection check TLS OK và light/dark; S03 banner maintenance trên Login hiện khi flag bật, biến mất S04. Bổ sung vòng 61: [swing-supplemental-observations.md](../swing-supplemental-observations.md) R2 eye với password trống mask true/false/true; R3 focus username→password bằng Swing action (không phải native Enter); ping disable trong request chậm, lỗi/recovery hiển thị đủ sau fix. Phần còn lại hiện tại: Còn countdown 429/locked/maintenance login thực, forced password change, show/hide với password có dữ liệu, native keyboard và human sign-off. Không suy ping thành login loading. Tách riêng human sign-off, live, contract và kết quả tự động. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E regression: public config null/error fail-closed, register/OAuth UI ẩn khi chưa biết flag hoặc maintenance, clear password/disable form khi mất config, worker kiểm session/dispose trước submit/callback. Client 60/60 gồm E2E16 và SwingFeatureAcceptanceTest13; không manual auth. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4349-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4349-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M03] Client nền tảng & Màn hình vào hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
