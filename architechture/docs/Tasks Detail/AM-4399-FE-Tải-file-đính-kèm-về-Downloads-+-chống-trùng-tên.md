# [FE] Tải file đính kèm về Downloads + chống trùng tên

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M12] File đính kèm — Tải về & Mở file |
| Owner | TV5 |
| Platform | UserWeb |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 11/11/2026 → 12/11/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] Tải file đính kèm về Downloads + chống trùng tên` thuộc **[M12] File đính kèm — Tải về & Mở file** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `FileUtil.download(ApiClient, gmailId, folder, uid, index, fileName)`:
  - Gọi EP-M4 → `byte[]` hoặc `InputStream`
  - Thư mục `%USERPROFILE%\Downloads\MailApp\${gmailAccountEmail}\` (tạo nếu chưa có)
  - Chống trùng: nếu `filename.ext` tồn tại → `filename (1).ext`, `(2)`,...
  - Ghi file, lưu map `attachmentKey → localPath` vào `SessionStore.attachmentCache` (in-memory)
  - Trả `File` object
- `FileUtil.openOrDownload(...)`: nếu attachment đã trong cache + file tồn tại → `Desktop.getDesktop().open(file)` — không tải lại
- Caller: `MailDetailPanel` nút click attachment

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** FileUtil tải file tới Downloads/MailApp/<email> mặc định, sanitize tên, đổi tên không ghi đè, thử lại khi đích đã tồn tại `(1)`, file tạm, xác minh byte rồi move không REPLACE_EXISTING, kiểm tra byte; cả tải thủ công và mở đều không ghi đè.
- **Test liên quan:** AcceptanceRegressionTest#uniqueDownloadsAndSessionCacheNeverOverwriteOrLeakAcrossSessions, #failedDownloadRemovesTemporaryAndReservedFiles, AttachmentCleanupServiceTest, MailApiClientNewEndpointsTest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Bổ sung G3:** Cleanup reject identity/index mismatch và cancel giữa download; không xóa, part cleanup; AttachmentCleanupServiceTest 10/10. Native chooser/network/permission vẫn thiếu.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AcceptanceRegressionTest: 12/12 PASS; AttachmentCleanupServiceTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailApiClientNewEndpointsTest: 6/6 PASS; AttachmentSizeAccuracyTest: 3/3 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE, giới hạn tải; không toggle download riêng. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: Giữ: EP-M4 download read-only không yêu cầu audit mutation riêng; nếu cleanup rồi xóa, xóa vẫn MAIL_DELETE.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4399 (M30, DEFECT/PARTIAL): Tải xuống lần đầu mở chooser ngoài configured dir, Cancel ngay. Tạo **chỉ** folder fixture MailApp/ui_manual@test.local rồi thử lại: chooser đúng folder, Save → modal path fixture.txt, SHA prefix. Invoke chip trùng tên thứ hai (clipped nhưng accessibility) → Save → fixture (1).txt, khác hash. Filesystem: fixture.txt 20B SHA-256 `606D4086C65A116D6AFF0B8A18D04C4FD01FBF153C2DCA0FB70617A105E13948`; fixture (1).txt 22B `44DD125B9432E121BE6340EB6EE4E5829D44514F44557FC4E3186D6224070F14`; bản đầu không bị ghi đè. Ảnh M30-download-success. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) J009–011: byte/hash/Content-Length đúng trên final JAR, metadata ESTIMATED tách kích thước tải. Phần còn lại hiện tại: Còn trực tiếp Swing bản đã sửa chooser/size label/chip layout, traversal/permission/network/part cleanup/default Downloads; không thay bằng API J hay regression. Tách riêng human sign-off, live, contract và kết quả tự động. Bổ sung A/B và regression cuối: [swing-supplemental-observations.md](../swing-supplemental-observations.md) — Download completion chỉ thay đổi UI của detail/mailbox generation hiện tại; cache cùng phiên vẫn giữ file thành công. Chưa thao tác native chooser/viewer. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [Swing](../swing-supplemental-observations.md): SessionStore kiểm size/mtime/fileKey và NOFOLLOW_LINKS để loại cache file thay/đổi/xóa; 3/3 regression; chưa native chooser, permission/disk fault manual. **Vòng G3:** Cleanup reject identity/index mismatch và cancel giữa download; không xóa, part cleanup; AttachmentCleanupServiceTest 10/10. Native chooser/network/permission vẫn thiếu. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4399-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4399-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M12] File đính kèm — Tải về & Mở file**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
