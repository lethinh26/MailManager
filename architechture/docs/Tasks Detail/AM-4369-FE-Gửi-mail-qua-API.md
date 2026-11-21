# [FE] Gửi mail qua API

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M11] Màn hình Soạn mail |
| Owner | TV5 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 07/11/2026 → 08/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Gửi mail qua API` thuộc **[M11] Màn hình Soạn mail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `ComposeDialog extends JDialog` modal
- From: `JComboBox` danh sách Gmail user đã liên kết (EP-G1) — không cho sửa
- To/Cc/Bcc: `JTextField` — allow nhiều email phân cách `,` hoặc `;`, validate mỗi cái
- Subject `JTextField`, Body `JTextArea` wrap + `JCheckBox` "Gửi HTML" toggle
- Nút "Đính kèm" → `JFileChooser` multi-select → `JList` file đã chọn, mỗi row: filename + size + nút xoá
- Validate client trước khi gửi: tổng size ≤ `MAX_ATTACHMENT_SIZE_MB`, địa chỉ `to` bắt buộc
- Nút "Gửi" → ApiClient.postMultipart → `SwingWorker` + progress bar — sau khi success: JOptionPane "Đã gửi" + đóng dialog
- Nếu `publicConfig.sendMailEnabled=false` → disable nút Gửi + label "Tính năng bị tắt bởi Admin"

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M11] Màn hình Soạn mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
