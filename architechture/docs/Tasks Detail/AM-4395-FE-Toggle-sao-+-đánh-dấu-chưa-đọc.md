# [FE] Toggle sao + đánh dấu chưa đọc

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M17] Gắn sao & đánh dấu đã/chưa đọc (flags) |
| Owner | TV4 |
| Platform | UserWeb |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 11/11/2026 → 15/11/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Toggle sao + đánh dấu chưa đọc` thuộc **[M17] Gắn sao & đánh dấu đã/chưa đọc (flags)** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- Trong `MailPanel` JTable: column icon sao (⭐/☆) — click toggle → EP-M7 `{starred:!current}` → cập nhật icon
- Trong `MailDetailPanel`: nút "Đánh dấu chưa đọc" → EP-M7 `{seen:false}` → refresh
- Icon sao hiện ở JTable row + detail header

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** MainFrame icon sao vector trên mỗi dòng, hit vùng riêng không mở detail, menu Gắn/Bỏ sao, detail star và Đánh dấu chưa đọc, EP-M7 cập nhật flagged/seen, chống callback mailbox cũ. Server nhận alias starred và audit MAIL_FLAG_CHANGE.
- **Test liên quan:** MailApiClientNewEndpointsTest, MailboxRegressionTest, MailServiceGreenMailIntegrationTest; thao tác chuột Swing cần kiểm tra tay.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** SwingFeatureAcceptanceTest#mailboxPaginationAndDirectStarUseCurrentIdentityWithoutOpeningMail kiểm nhấn sao trực tiếp bằng MouseEvent, chỉ gửi flag, không mở detail.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Bổ sung G3:** Flag worker kiểm generation trước request, bỏ response cũ khi A→B→A; refresh nếu cần. Native rapid flag/reload còn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailServiceGreenMailIntegrationTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailApiClientNewEndpointsTest: 6/6 PASS; MailboxRegressionTest: 13/13 PASS; SwingFeatureAcceptanceTest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE; không toggle star/unread riêng. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4395 (M29, PARTIAL): Detail 05 Gắn sao → tooltip Bỏ gắn sao và status "Đã cập nhật trạng thái sao"; Đánh dấu chưa đọc → status tương ứng. Restart chọn list row 59 vùng sao scoped pointer: selection vẫn [], detail empty "Chọn một thư để xem nội dung", status sao updated; context menu Bỏ gắn sao → click → mở lại context thấy Gắn sao. Ảnh M29-list-star. Phần còn lại: Không verify seen trực tiếp qua API/audit hoặc stale UID race, actual keyboard. Không coi renderer alone là chứng minh flags persisted mọi trường hợp. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — Flag mutation giữ session generation, 409 UIDVALIDITY và outcome không rõ thì reload, không retry mutation. B012–013 backend đọc lại flags đúng; Swing regression riêng. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [Swing](../swing-supplemental-observations.md): C005–006/C028–030 backend flags success/fail-closed read-back; FE phân biệt 503 preflight và unknown mutation, không thay manual keyboard/rapid toggle. **Vòng G3:** Flag worker kiểm generation trước request, bỏ response cũ khi A→B→A; refresh nếu cần. Native rapid flag/reload còn thiếu. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4395-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4395-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M17] Gắn sao & đánh dấu đã/chưa đọc (flags)**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
