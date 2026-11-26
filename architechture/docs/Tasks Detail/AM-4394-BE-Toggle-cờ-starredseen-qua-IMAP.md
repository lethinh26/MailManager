# [BE] Toggle cờ starred/seen qua IMAP

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M17] Gắn sao & đánh dấu đã/chưa đọc (flags) |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 06/11/2026 → 08/11/2026 |
| Ước lượng | 3h |
| Priority | 3 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Toggle cờ starred/seen qua IMAP` thuộc **[M17] Gắn sao & đánh dấu đã/chưa đọc (flags)** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M7 PUT /api/mails/{gmailAccountId}/{folder}/{uid}/flags`

- **Path:** `/api/mails/{gmailAccountId}/{folder}/{uid}/flags`
- **Method:** PUT
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Toggle cờ mail {starred, seen} → IMAP setFlags \Flagged / \Seen. Trả {uid, starred, seen}. Cung cấp luôn mark-unread.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

PUT /api/mails/{gmailAccountId}/{folder}/{uid}/flags
- Body `{starred?: boolean, seen?: boolean}` — ít nhất 1 field
- Open folder READ_WRITE; `getMessageByUID`
- `starred` → `Flags.Flag.FLAGGED`; `seen` → `Flags.Flag.SEEN`
- `message.setFlag(flag, value)`; đóng folder commit
- Log `MAIL_FLAG_CHANGE` `{gmailId, uid, starred, seen}`
- Response 200 `{uid, starred, seen}` — trạng thái mới sau khi set

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** PUT flags nhận `seen`, `flagged` (alias đầu vào `starred`), ít nhất một trường khác null; false để bỏ sao/đánh dấu chưa đọc. Response 200 `{uid,seen,flagged,starred,answered,draft}` giữ tương thích Swing và contract gốc (`starred=flagged`); uidValidity tùy chọn, 409 nếu lệch. Audit MAIL_FLAG_CHANGE với gmailId, uid, starred, seen, userId, IP.
- **Code:** `Mail Server/src/main/java/com/mailapp/controller/MailController.java#updateFlags`, `dto/mail/MailDtos.java#UpdateFlagsRequest`, `mail/service/MailServiceImpl.java#updateFlags`. Ownership/bảo trì, READ_WRITE, xác nhận cờ yêu cầu, không báo thành công giả, invalidate folder cache; audit không log tiêu đề/nội dung/secret.
- **Test có trong repo:** `MailServiceGreenMailIntegrationTest#testSendSearchPaginateFlagsAndDeleteLifecycle` kiểm cờ và verify audit action/IP; `MailMessageBuilderTest#acceptsLegacyStarredButKeepsFlaggedWireContract` kiểm JSON alias. Chưa xác nhận kết quả lần này; cần manual Postman/Swing với cả true/false, đọc lại Gmail Web và audit DB. `MailControllerCompatibilityTest#flagsAcceptStarredAndReturnBothNamesAndUid` kiểm wire.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Đối chiếu bổ sung:** Rà soát N/A: không có N/A, toggle tường minh yêu cầu ownership, bảo trì, audit MAIL_FLAG_CHANGE; giữ alias starred/flagged và UIDVALIDITY. Cần manual trên Gmail test riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 6 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailControllerCompatibilityTest: 12/12 PASS; MailMessageBuilderTest: 5/5 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailAuditRecoveryIntegrationTest: 13/13 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE; không toggle star/seen riêng. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). Đối chiếu action nghiệp vụ ở §4.1 và test server; UI không audit trùng. Vòng C: [BE](../backend-supplemental-observations.md): C005–006 flag200/read-back đúng; C028–030 reject intent 503 và cờ không đổi; partial STORE regression ghi uncertain, không audit thành công giả. Vòng D: D009 flag200, D016 đúng một MAIL_FLAG_CHANGE sau drain; D046 read-back seen/flagged=true, cùng UID. Không thay manual UI. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): V8 recovery dùng chung cho mail audit; E chỉ gửi mail, flag manual D/C kế thừa; không suy thành ca flag mới.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4394 (H refs trong hàng AM-4394, OBSERVED): H024 PUT flags 200 seen=false, flagged/starred=true; H025 GET preview giữ đúng cờ; H099 search attachment vẫn flagged=true; H123 stale UIDVALIDITY 409, H124 trạng thái gốc giữ nguyên; H126 body rỗng 400; H142 đúng 1 audit actor/UID/starred/seen cho thao tác owner. H127 có audit cả true/false từ fixture UI, chỉ chứng minh dòng audit chứ không thay quan sát UI. Retest bản cuối: [F074](../backend-final-observations.md) (OBSERVED): PUT flags nhãn star/unread trả 400 VALIDATION_ERROR, dữ liệu yêu cầu không hợp lệ. Prime xác nhận lệnh đầu thiếu body; đây là setup sai cho positive case, chỉ ghi nhận empty-body rejection, không tính là toggle thành công hay lỗi nghiệp vụ.; [F075](../backend-final-observations.md) (OBSERVED): PUT flags với body hợp lệ và UIDVALIDITY 200 UID6, seen=false, flagged=true, starred=true.; [F076](../backend-final-observations.md) (OBSERVED): GET preview UID6 200 flags seen=false/flagged=true; không mở lại làm seen=true.; [F077](../backend-final-observations.md) (OBSERVED): PUT flags 200 UID6 seen=true, flagged=false, starred=false.; [F078](../backend-final-observations.md) (OBSERVED): GET preview UID6 200 seen=true/flagged=false, trạng thái persist đúng thao tác.; [F079](../backend-final-observations.md) (OBSERVED): USER khác PUT flags UID6 với UIDVALIDITY 404 GMAIL_NOT_OWNED.; [F080](../backend-final-observations.md) (OBSERVED): GET admin logs owner/action 200 total2: MAIL_FLAG_CHANGE UID6 starred=true/seen=false rồi starred=false/seen=true, hai timestamp và actor đúng; không có success audit cho F074/F079 trong kết quả. H123–126 stale UID/validation/unchanged.. Phần còn lại: Scope local cả hai hướng flag, alias, read-back, ownership, validation, audit đủ; Gmail thật/Gmail Web riêng, human sign-off. Không dùng HTTP read-back thay manual UI AM-4395. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng C: [BE](../backend-supplemental-observations.md): C005–006 flag200/read-back đúng; C028–030 reject intent 503 và cờ không đổi; partial STORE regression ghi uncertain, không audit thành công giả. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): Vòng F regression journal: 13/13; giữ đúng first outcome, không duplicate/replay provider, conflict fail closed. Không lặp flag đã đủ; không đổi trạng thái manual local. Không thay trạng thái checkbox.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M17] Gắn sao & đánh dấu đã/chưa đọc (flags)**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
