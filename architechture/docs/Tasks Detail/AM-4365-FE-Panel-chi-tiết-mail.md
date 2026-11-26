# [FE] Panel chi tiết mail

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M09] Màn hình Hộp thư & Chi tiết mail |
| Owner | TV4 |
| Platform | UserWeb |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 06/11/2026 → 07/11/2026 |
| Ước lượng | 2.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Panel chi tiết mail` thuộc **[M09] Màn hình Hộp thư & Chi tiết mail** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `MailDetailPanel extends JPanel` (hoặc JDialog)
- Header: From, To, Cc, Subject, Date (format `dd/MM/yyyy HH:mm`)
- Body: `JEditorPane` contentType="text/html" render `bodyHtml`, fallback `JTextArea` cho `bodyText` (plain)
- Attachments list: `JList<String>` "filename (size)" — click mở → gọi EP-M4 (do TV5 xử lý tiếp)
- Nút "Gắn sao" toggle (EP-M7), "Xoá" (EP-M6 với confirm), "Đánh dấu chưa đọc" (EP-M7 seen=false)
- Đóng → trigger refresh danh sách hộp thư

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** MainFrame detail CardLayout: from/to/cc/subject/date, HTML sạch/plain toggle, chặn ảnh xa mặc định, attachment chips có tên/size, star/unread/delete, cập nhật flags danh sách sau load; bảo toàn thiết kế 3 cột thay dialog riêng.
- **Test liên quan:** MailApiClientNewEndpointsTest, MailboxRegressionTest, MailSecurityAndSanitizerTest, MailServiceGreenMailIntegrationTest; cần xem render Swing thực tế.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** Kiểm UID/UIDVALIDITY trả về đúng thư đã chọn, không render tiêu đề/người gửi như HTML; khi đổi selection xóa detail cũ.
- **Xóa thư từ detail:** cùng đường EP-M6 với AM-4393; xác nhận permanent=true chỉ trong Trash. Khi I/O/HTTP 5xx (trừ maintenance) có thể đã mutation nhưng chưa nhận response, cảnh báo kết quả chưa xác định, không tự retry, tải lại folder và yêu cầu kiểm tra source/Trash. E2E 14 chỉ kiểm xóa thành công, chưa chứng minh nhánh uncertain.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.


- **Bằng chứng cập nhật:** Khi kết quả DELETE không xác định, dừng auto-clean và reload folder/Trash; E14 kiểm luồng xóa thành công, failure regression kiểm service.

- **Bổ sung G3:** Cleanup đối chiếu UID/UIDVALIDITY trả về và attachment index trước download/mutation; manual detail/render/chooser vẫn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailSecurityAndSanitizerTest: 3/3 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; AttachmentCleanupServiceTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailApiClientNewEndpointsTest: 6/6 PASS; MailboxRegressionTest: 13/13 PASS; UiLayoutRegressionTest: 6/6 PASS; AttachmentSizeAccuracyTest: 3/3 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE; không toggle detail/delete/star riêng. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Nút detail star/unread/delete qua E14 tạo MAIL_FLAG_CHANGE/MAIL_DELETE (đúng hai lần chuyển/xóa). Không còn N/A vì có mutation.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4365 (M16, DEFECT/PARTIAL): Chọn fixture 05: subject/sender/To/date 26/11/2026 13:39, body plain "Nội dung fixture 5"; Chi tiết (+) hiện From/To/Mã thư, 3 chips (fixture.txt 20B, fixture.txt 23B, png 68B) trong tree. **Ảnh M16-detail chỉ thấy 1 chip do footer FlowLayout clip hàng sau**; các chip khác vẫn Invoke được bằng accessibility (không tương đương nhìn/click vật lý). Self-send HTML mở với mode HTML sạch. Phần còn lại: Sửa scroll strip ở nguồn, chưa build/reverify. Chưa thử ảnh xa/HTML sanitizer thực, Cc đầy đủ, mode toggle và resize nhỏ. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng C: [Swing](../swing-supplemental-observations.md): Detail body PEEK trước explicit Seen, FE callback/lease regression; không suy thành manual layout, remote image/sanitizer. **Vòng G3:** Cleanup đối chiếu UID/UIDVALIDITY trả về và attachment index trước download/mutation; manual detail/render/chooser vẫn thiếu. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4365-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4365-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M09] Màn hình Hộp thư & Chi tiết mail**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
