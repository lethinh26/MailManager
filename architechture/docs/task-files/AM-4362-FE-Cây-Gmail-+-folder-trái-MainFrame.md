# [FE] Cây Gmail + folder trái MainFrame

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M09] Màn hình Hộp thư & Chi tiết mail |
| Owner | TV4 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 04/11/2026 → 05/11/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Cây Gmail + folder trái MainFrame` thuộc **[M09] Màn hình Hộp thư & Chi tiết mail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `MainFrame extends JFrame`: layout BorderLayout — menu bar trên, sidebar trái (cây Gmail + folder), content phải (CardLayout: MailPanel / AdminPanel / Empty)
- Menu: File → "Quản lý Gmail" (M05 dialog), "Soạn mail" (M11), separator, "Đăng xuất"
- User menu: tên user, đổi mật khẩu, đăng xuất
- Sidebar: `JTree` — node root = user, child = mỗi Gmail (icon khác APP_PASSWORD vs OAUTH2, status), sub-child = folders
- Status bar dưới: trạng thái mạng, user, "Loading..." khi SwingWorker chạy
- Khu Admin chỉ thêm vào CardLayout nếu `SessionStore.userInfo.role == "ADMIN"` (TV6 implement sau, TV1 để placeholder)

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M09] Màn hình Hộp thư & Chi tiết mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
