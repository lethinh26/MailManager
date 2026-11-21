# [FE-Admin] Khung AdminFrame 4 tab

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M15] Màn hình Admin |
| Owner | TV6 |
| Platform | AdminWeb |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 09/11/2026 → 09/11/2026 |
| Ước lượng | 1h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[FE-Admin] Khung AdminFrame 4 tab` thuộc **[M15] Màn hình Admin** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `AdminFrame` (hoặc `AdminPanel` trong MainFrame CardLayout) — chỉ khởi tạo nếu `userInfo.role=ADMIN`
- `JTabbedPane` 4 tab:
  - **Người dùng**: search field + filter role/enabled + JTable + nút [Tạo mới, Khoá/Mở khoá, Đổi role, Đặt lại MK, Xoá] — mỗi action confirm dialog
  - **Cấu hình**: form load EP-S1 → `JCheckBox` cho boolean flags, `JSpinner`/`JTextField` cho int, nút "Lưu" → EP-S2. Công tắc `ALLOW_REGISTRATION` đặt đầu tab + label "Số user hiện tại: X"
  - **Thống kê**: panel read-only các con số từ EP-ST1, nút "Làm mới"
  - **Nhật ký**: filter (userId/action/date range) + JTable EP-ST2 phân trang
- Mọi call API → `SwingWorker`; error → JOptionPane với `code`/`message`

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M15] Màn hình Admin**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
