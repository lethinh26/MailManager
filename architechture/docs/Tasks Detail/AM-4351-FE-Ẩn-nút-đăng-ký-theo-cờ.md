# [FE] Ẩn nút đăng ký theo cờ

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M03] Client nền tảng & Màn hình vào hệ thống |
| Owner | TV1 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 02/11/2026 → 03/11/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Ẩn nút đăng ký theo cờ` thuộc **[M03] Client nền tảng & Màn hình vào hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `RegisterFrame extends JFrame`: username, password, confirm password, fullName, email
- Validation client: password==confirm, email regex, username không trống
- POST `/api/auth/register` → 201: JOptionPane "Đăng ký thành công" → quay LoginFrame
- 409 username trùng → highlight field username + message
- 403 REGISTRATION_DISABLED → "Đăng ký đã bị tắt" + disable form
- Chỉ hiện nút Đăng ký từ LoginFrame nếu `registrationEnabled=true`

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** LoginFrame không hiển thị đăng ký trước khi tải public config; refresh định kỳ, banner bảo trì và ẩn link theo registrationEnabled; RegisterFrame khóa form khi backend tắt.
- **Test liên quan:** UiAccessibilityAndThemeTest, AuthRegisterAndChangePasswordTest, AcceptanceRegressionTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** Cập nhật trực tiếp cả RegisterFrame đang mở theo public config, xóa mật khẩu khi không được phép; SwingFeatureAcceptanceTest.

- **Bổ sung G5:** G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AuthRegisterAndChangePasswordTest: 12/12 PASS; AcceptanceRegressionTest: 12/12 PASS; ClientLiveServerE2ETest: 16/16 PASS; SwingFeatureAcceptanceTest: 16/16 PASS; UiAccessibilityAndThemeTest: 2/2 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: cờ đăng ký không Gmail. A giữ riêng việc ẩn/disable UI; §4 chép register nên vẫn phải chứng minh REGISTER khi submit thực ở 4350, không tính N/A cho hành vi submit.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). ALLOW_REGISTRATION. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ: cờ đăng ký không Gmail. A giữ riêng việc ẩn/disable UI; §4 chép register nên vẫn phải chứng minh REGISTER khi submit thực ở 4350, không tính N/A cho hành vi submit.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4351 (M06, PARTIAL): Login ban đầu hiện Đăng ký ngay; prime xác nhận registration=false/OAuth=true, Main.main fresh cho thấy **không có Đăng ký ngay**, Google button vẫn hiện; ảnh M06-registration-hidden. Bổ sung: [swing-supplemental-observations.md](../swing-supplemental-observations.md) S03/S04 trên Swing thật: form đang mở tự disable 5 field và Submit, banner/nút đăng ký/OAuth đúng flags; restore enable và Back. Phần còn lại hiện tại: Còn clear password khi khác rỗng và stale submit 403, cũng như các nhánh register chưa quan sát ở AM-4350 do credential gate. Tách riêng human sign-off, live, contract và kết quả tự động. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): E regression: public config null/error fail-closed, register/OAuth UI ẩn khi chưa biết flag hoặc maintenance, clear password/disable form khi mất config, worker kiểm session/dispose trước submit/callback. Client 60/60 gồm E2E16 và SwingFeatureAcceptanceTest13; không manual auth. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4351-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4351-m). **G5:** G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M03] Client nền tảng & Màn hình vào hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
