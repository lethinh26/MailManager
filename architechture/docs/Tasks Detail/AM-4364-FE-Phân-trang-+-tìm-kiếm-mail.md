# [FE] Phân trang + tìm kiếm mail

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M09] Màn hình Hộp thư & Chi tiết mail |
| Owner | TV4 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 05/11/2026 → 07/11/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Phân trang + tìm kiếm mail` thuộc **[M09] Màn hình Hộp thư & Chi tiết mail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `MailPanel extends JPanel` trong MainFrame
- `JTree` bên trái: root "Tài khoản" → node mỗi Gmail (icon) → folder children
- `JTable` mail ở giữa: columns From | Subject | Date | (icon 📎) — unread in đậm (`Font.BOLD` renderer)
- `DefaultTableModel` không editable; double-click row → mở `MailDetailPanel` (hoặc dialog)
- Phân trang: "Trang X/Y", nút Prev/Next, `JComboBox` page size (10/20/50)
- Nút "Làm mới" → reload EP-M2; `JTextField` search + nút "Tìm" → EP-M2 với `q=`
- Mọi API call trong `SwingWorker` + status bar "Đang tải..."
- Sau khi mở detail (EP-M3) → quay lại refresh table (mail chuyển read)

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** MainFrame loadMails: nút Trước/Sau + Tải thêm, Trang X/Y, size 10/20/50 hoặc mặc định động MAIL_PAGE_SIZE; q tìm kiếm, unread/attachment filter, reset page khi đổi size, UIDVALIDITY và stale-worker guard.
- **Test liên quan:** MailboxRegressionTest, MailApiClientNewEndpointsTest, AcceptanceRegressionTest#publicPageSizeAndHttpsOnlyRules, SwingFeatureAcceptanceTest, MailServiceGreenMailIntegrationTest, ClientLiveServerE2ETest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** SwingFeatureAcceptanceTest#mailboxPaginationAndDirectStarUseCurrentIdentityWithoutOpeningMail kiểm thao tác đổi size, Next/Previous và UID của từng trang.

- **Bổ sung G3:** Flag callback ABA không ghi lại list mới, reload metadata nếu cùng mailbox; không thay native paging/filter.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailServiceGreenMailIntegrationTest: 10/10 PASS; AcceptanceRegressionTest: 12/12 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailApiClientNewEndpointsTest: 6/6 PASS; MailboxRegressionTest: 13/13 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAIL_PAGE_SIZE, MAINTENANCE_MODE. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ: phân trang/search read-only.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4364 (M15, PARTIAL): Mặc định 20/60 Trang 1/3; chọn combo 10 → Trang 1/6 (59..50); Next → Trang 2/6 (49..40); Prev → 59..50, Prev disabled; 50 → Trang 1/2 (59..10); Tải thêm → 60 row (59..00), Trang 2/2, Next disabled. Attachment filter → 2, search marker self-send → đúng 1; sau xóa search → empty. Ảnh M15-paging. Phần còn lại: Không kiểm mọi q UTF-8/filter tổ hợp, uid trực tiếp từ UI (quan sát subject order, không kết luận UID snapshot), dynamic MAIL_PAGE_SIZE/override sau config, race. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — MainFrame/MailboxViewModel reset page khi search/filter khác query đã gửi, không trộn Next/Load More giữa hai query; regression Swing. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. **Vòng G3:** Flag callback ABA không ghi lại list mới, reload metadata nếu cùng mailbox; không thay native paging/filter. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4364-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4364-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M09] Màn hình Hộp thư & Chi tiết mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
