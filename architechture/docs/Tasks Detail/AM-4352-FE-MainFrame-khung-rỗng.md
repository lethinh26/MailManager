# [FE] MainFrame khung rỗng

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M03] Client nền tảng & Màn hình vào hệ thống |
| Owner | TV1 |
| Platform | UserWeb |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 31/10/2026 → 01/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] MainFrame khung rỗng` thuộc **[M03] Client nền tảng & Màn hình vào hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `MainFrame extends JFrame`: layout BorderLayout — menu bar trên, sidebar trái (cây Gmail + folder), content phải (CardLayout: MailPanel / AdminPanel / Empty)
- Menu: File → "Quản lý Gmail" (M05 dialog), "Soạn mail" (M11), separator, "Đăng xuất"
- User menu: tên user, đổi mật khẩu, đăng xuất
- Sidebar: `JTree` — node root = user, child = mỗi Gmail (icon khác APP_PASSWORD vs OAUTH2, status), sub-child = folders
- Status bar dưới: trạng thái mạng, user, "Loading..." khi SwingWorker chạy
- Khu Admin chỉ thêm vào CardLayout nếu `SessionStore.userInfo.role == "ADMIN"` (TV6 implement sau, TV1 để placeholder)

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** MainFrame 3 cột, CardLayout cho trạng thái thư, thanh menu/toolbar, card Gmail + danh sách folder, vùng detail; Admin chỉ role ADMIN; status bar username/role và trạng thái kết nối/loading. Giữ layout cải tiến tương đương chức năng JTree/JTable, không tạo widget trùng lặp.
- **Test liên quan:** UiLayoutRegressionTest, SwingFeatureAcceptanceTest, MailboxRegressionTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** ClientLiveServerE2ETest: 16/16 PASS; MailboxRegressionTest: 13/13 PASS; SwingFeatureAcceptanceTest: 16/16 PASS; UiLayoutRegressionTest: 6/6 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Sidebar load account/folder theo phiên; E9/E12 kiểm outsider 404, E14 link/list/folder thật; quyền cuối ở BE. Không còn N/A vì MainFrame thực sự chạm Gmail.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). Role, ALLOW_SEND_MAIL và link flags. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O cần đối chiếu lại: MainFrame thực sự load Gmail/folders; nếu chỉ shell thì N/A độc lập, nhưng nguyên §4 sidebar phải kiểm owner qua 4362/E9/E12 và cross-account UI. A giữ riêng điều hướng/loading, các link/send mutation ở task riêng vẫn audit.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4352 (M07, PARTIAL): ui_manual login ban đầu empty-state "Chưa liên kết tài khoản Gmail", CTA, toolbar/menu/status USER, auto-clean TẮT; bấm CTA mở modal manager; sau link MainFrame có account/folder/list; logout qua menu profile. Ảnh M07-empty. Phần còn lại: Không resize 800x600/HiDPI/theme, không Admin shell. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4352-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4352-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M03] Client nền tảng & Màn hình vào hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
