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

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** MainFrame sidebar card Gmail hiển thị email/avatar/phương thức và trạng thái, folder list theo account; chọn account tải folder, chọn folder tải thư, hủy worker cũ. Giữ card/list hiện đại thay JTree phân cấp nhưng chức năng điều hướng tương đương.
- **Test liên quan:** MailboxRegressionTest, MailApiClientNewEndpointsTest, UiLayoutRegressionTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** Generation cho danh sách account và user, ngăn kết quả cũ ghi đè; chặn tải mail lúc folders đang tải.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Bổ sung vòng G2 (sau F):** GmailAccountDto gate đồng nhất server NEEDS_REAUTH, không dựa vào fallbackActive cũ; MainFrame/Compose dùng gate mới; conflict409 refresh metadata. Chưa native multi-mailbox/DPI.

- **Bổ sung G3:** Delete/flag/cleanup dùng mailbox generation, từ chối response/selection cũ; native hai mailbox/DPI còn thiếu.

- **Bổ sung G4:** G4 sửa auto-clean gate dual-auth thống nhất requiresReauthBeforeMail; cache folder phân revision; native 2 mailbox/fallback vẫn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** ClientLiveServerE2ETest: 16/16 PASS; MailApiClientNewEndpointsTest: 6/6 PASS; MailboxRegressionTest: 13/13 PASS; UiLayoutRegressionTest: 6/6 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE, link/send CTA. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ: list account/folder đọc, không audit mutation riêng; thao tác menu dùng chung do 4353/4369.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4362 (M13, PARTIAL): Sidebar ui_manual có badge App Password, INBOX 60; sau gửi self count 61, xóa soft và click lại account → folder INBOX 60 + Thùng rác, chọn Trash đúng thư đã chuyển. Logout/login ui_other cho sidebar chỉ mailbox khác, INBOX 13. Phần còn lại: Không 2 mailbox cùng user, fallback status, race/chậm/IDOR request, actual hitbox DPI. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. **Vòng G2:** GmailAccountDto gate đồng nhất server NEEDS_REAUTH, không dựa vào fallbackActive cũ; MainFrame/Compose dùng gate mới; conflict409 refresh metadata. Chưa native multi-mailbox/DPI. Không đổi checkbox. **Vòng G3:** Delete/flag/cleanup dùng mailbox generation, từ chối response/selection cũ; native hai mailbox/DPI còn thiếu. Không đổi checkbox. **G4:** G4 sửa auto-clean gate dual-auth thống nhất requiresReauthBeforeMail; cache folder phân revision; native 2 mailbox/fallback vẫn thiếu. [AM-4362-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4362-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M09] Màn hình Hộp thư & Chi tiết mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
