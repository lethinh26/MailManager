# [BE] Xoá mail qua IMAP

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M16] Xoá mail (Trash semantics) |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 04/11/2026 → 07/11/2026 |
| Ước lượng | 4h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Xoá mail qua IMAP` thuộc **[M16] Xoá mail (Trash semantics)** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M6 DELETE /api/mails/{gmailAccountId}/{folder}/{uid}`

- **Path:** `/api/mails/{gmailAccountId}/{folder}/{uid}`
- **Method:** DELETE
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Xóa mail theo UID. Ngoài Trash → chuyển đúng thư vào Trash; trong Trash → chỉ xóa vĩnh viễn khi `permanent=true` đã được xác nhận. UID MOVE hoặc COPY + UID EXPUNGE theo capability, không global expunge; kiểm UIDVALIDITY. Ghi MAIL_DELETE sau thành công.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

> **Điều chỉnh đặc tả 26/11/2026 — chủ dự án đã phê duyệt:** Sau đối chiếu ba khác biệt contract, chủ dự án yêu cầu sửa Task Detail theo phương án hiện hành. §4 dưới đây là yêu cầu áp dụng; ghi chú/evidence G6 trở về trước là lịch sử. Phê duyệt không thay manual/live evidence hoặc giảm guard bảo mật.

DELETE /api/mails/{gmailAccountId}/{folder}/{uid}
- Verify ownership; open folder READ_WRITE
- `getMessageByUID(uid)` → null: 404
- Trash semantics Gmail:
  - Nếu folder là Trash thực tế: chỉ xóa vĩnh viễn khi có xác nhận rõ ràng `permanent=true`; thiếu tham số trả 400 PERMANENT_DELETE_REQUIRED_IN_TRASH. Chỉ xóa đúng UID với UID EXPUNGE/UIDPLUS, không `folder.expunge()` toàn folder.
  - Nếu folder khác: `permanent=true` trả 400 PERMANENT_DELETE_REQUIRES_TRASH; dùng UID MOVE tới Trash nếu có capability, nếu không dùng COPY rồi Deleted + UID EXPUNGE đúng thư khi có UIDPLUS. Xác định Trash theo folder resolver; thiếu capability an toàn thì fail closed trước mutation. Kiểm UIDVALIDITY, sai trả 409 UID_VALIDITY_CHANGED. Đóng folder không expunge toàn bộ. Mất phản hồi mutation: outcome chưa xác định, refresh nguồn/Trash, không retry hoặc fallback COPY mù; không ghi audit thành công giả.
- Log `MAIL_DELETE` `{gmailId, folder, uid}` — KHÔNG log subject/body
- Response 204 mặc định; `Prefer: return=representation` tùy chọn trả 200 với status/permanent/message.
- Errors: 404 UID sai/không thuộc owner, 400 thiếu hoặc sai phạm vi permanent, 409 UIDVALIDITY thay đổi, 422 NEEDS_REAUTH, 502/504 lỗi kết nối hoặc timeout; khi không xác định outcome phải thông báo rõ, không báo thành công.

> **Lịch sử contract:** §4 trước phê duyệt mô tả xóa ngầm trong Trash và COPY + Deleted + global `folder.expunge()`. Chủ dự án chấp thuận thay bằng explicit permanent, UIDVALIDITY và targeted UID MOVE/UIDPLUS, giữ 204 mặc định và an toàn dữ liệu. Gmail Trash/All Mail thật vẫn là yêu cầu nghiệm thu.

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu hiện hành là §4 đã được phê duyệt 26/11/2026; các đối chiếu trước phê duyệt bên dưới được giữ làm lịch sử. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** DELETE path giữ nguyên, `permanent=false` mặc định chuyển Trash, `true` chỉ hợp lệ khi thư đã trong Trash; gọi ngược trả 400 PERMANENT_DELETE_REQUIRES_TRASH hoặc PERMANENT_DELETE_REQUIRED_IN_TRASH. Response 204 mặc định; header `Prefer: return=representation` trả 200 `{status:"DELETED",permanent,message}`; UID sai 404, uidValidity sai 409, OAuth2 cần reauth 422, lỗi mạng 502/504. Action MAIL_DELETE (một bản ghi mỗi thao tác thành công).
- **Code:** `Mail Server/src/main/java/com/mailapp/controller/MailController.java#deleteMail`, `mail/service/MailServiceImpl.java#deleteMail`, `#isTrash`, `#resolveOrCreateTrashFolder`, `#assertOnlyTargetDeleted`; `mail/folder/GmailFolderResolver.java`. Guard ownership/bảo trì, READ_WRITE; xác định đúng Trash thực tế, UID MOVE nếu capability có, nếu không dùng COPY rồi Deleted + UID EXPUNGE đúng thư; service triển khai không có capability cần thiết fail closed (502, kể cả loopback) trước COPY/Deleted; chỉ constructor package-private GreenMail fixture có fallback kiểm tra không expunge thư khác; close(false). COPY/expunge không atomic, lỗi giữa chừng cần kiểm tra lại. Audit chỉ sau UID MOVE hoặc expunge thành công.
- **Test có trong repo:** `MailServiceGreenMailIntegrationTest#testSendSearchPaginateFlagsAndDeleteLifecycle`, `#testGmailLikeTrashStructureMoveAndPermanentDeleteGuard`, `#testSecurityGuards`; `RealGmailIntegrationTest#testRealGmailSmtpSendAttachmentVerifySha256AndTrash` gated (kiểm tra Gmail trực tiếp, không thay thế test API). Kết quả chạy tại §5; manual Gmail cần kiểm chứng Trash/All Mail và UID EXPUNGE không ảnh hưởng thư đã Deleted khác. `MailServiceFailureRegressionTest#defaultServiceNeverExpungesNonUidplusEvenOnLoopback` kiểm fail-closed; `MailControllerCompatibilityTest#deleteDefaultsToNoContentAndSupportsExplicitRepresentation` kiểm 204/200. Kết quả chạy tại §5.
- **E2E:** ClientLiveServerE2ETest#test12_ClientHttpsMailLifecycleAgainstDisposableGreenMail; fixture loopback, không sử dụng Gmail thật; xem kết quả suite tại §5.

- **Đối chiếu bổ sung:** Cập nhật: server có MOVE dùng đúng UID MOVE trên selected-folder protocol lock, encode mailbox theo UTF-8/modified UTF-7; thiếu MOVE dùng COPY + UID EXPUNGE khi UIDPLUS; xóa vĩnh viễn chỉ trong Trash với permanent=true và UIDPLUS, không có đường xóa ngầm legacy vì không thể phân biệt caller đã xác nhận. Mất phản hồi mutation trả 502 GMAIL_CONNECTION_ERROR kèm thông báo chưa xác định, markBroken và invalidate cache; không retry hoặc fallback COPY, không audit thành công. COPY/STORE/EXPUNGE không atomic; flag rollback best-effort đúng thư nếu chưa expunged, người dùng phải refresh nguồn/Trash. Test `uidMoveUsesSingleServerCommandWithoutCopyOrExpungeAndAuditsOnce`, `lostMoveReplyDoesNotRetryCopyOrAuditAndRequiresFreshUidLookup`, `copyFailureNeverMarksSourceDeletedOrBlindlyRepeatsCopy`, `uidExpungeFailureRestoresOnlyTargetFlagAndReturnsUncertainOutcome`; chưa chạy. Không có N/A; cần Gmail thật kiểm MOVE/Trash/All Mail, xác nhận explicit permanent là khác biệt safety còn lại.


- **Bằng chứng cập nhật:** Fault injection mất kết nối giữa mutation không retry; Client dừng auto-clean cả khi đổi mailbox, không báo xóa thành công, giữ file đã tải và reload folder.
## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 0 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailControllerCompatibilityTest: 12/12 PASS; RealGmailIntegrationTest: 0/2 PASS, 2 SKIP (2 SKIPPED); MailServiceFailureRegressionTest: 22/22 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; AttachmentCleanupServiceTest: 10/10 PASS; ClientLiveServerE2ETest: 16/16 PASS; RealGmailXOAuth2IntegrationTest: 0/2 PASS, 2 SKIP (2 SKIPPED); MailAuditRecoveryIntegrationTest: 13/13 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec — OPEN. **Phê duyệt 26/11/2026:** 4391-C.02 đã đạt theo §4 điều chỉnh; explicit permanent=true, UIDVALIDITY, UID MOVE/UIDPLUS targeted, fail-closed, 204 mặc định. Local F045–057/C/D/E và regression đã có. Chỉ còn 4391-C.03 L: Gmail Trash/All Mail thật và read-back đúng UID, không coi local là live. [Ma trận](../acceptance-obligation-matrix.md#am-4391-c). **Lịch sử G6 trở về trước:** 204 mặc định, explicit permanent=true chỉ trong Trash, UID MOVE/UIDPLUS targeted, fail-closed nếu thiếu capability; rollback flag best-effort khi mất phản hồi, không retry/xác nhận thành công giả. Cần chấp thuận khác biệt literal xóa ngầm và nghiệm thu Gmail Trash/All Mail. Retest local (không đóng literal): F045, F046, F047, F048, F049, F050, F051, F052, F053, F054, F055, F056. Vòng C: [BE](../backend-supplemental-observations.md): C007–009/C018 move 204 và read-back Trash đúng 1; C029 reject prepare 503 thư còn nguyên. UID-safe explicit permanent giữ nguyên; contract literal vẫn OPEN. Vòng D: Intent phải commit trước cả CREATE Trash fallback; test missing Trash/intent reject và lost CREATE reply giữ UNCERTAIN, không move/copy/retry. Contract explicit permanent/UID guard không đổi. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): V8 recovery chỉ audit, không replay DELETE/MOVE/SMTP/IMAP. Explicit permanent/UID safety giữ nguyên, literal contract OPEN. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4391-C ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4391-c).
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). MAINTENANCE_MODE; không toggle delete riêng, permanent confirmation bắt buộc. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). MAIL_DELETE chuẩn; E2E chuyển Trash và xóa vĩnh viễn đúng UID ghi đúng 2 log. Vòng C: [BE](../backend-supplemental-observations.md): C007–009/C018 move 204 và read-back Trash đúng 1; C029 reject prepare 503 thư còn nguyên. UID-safe explicit permanent giữ nguyên; contract literal vẫn OPEN.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4391 (H refs trong hàng AM-4391, OBSERVED): H085 DELETE INBOX UID 61 204; H086 source search rỗng; H087 Trash UID 1; H088 DELETE Trash thiếu permanent 400 PERMANENT_DELETE_REQUIRED_IN_TRASH; H089 explicit permanent 204; H090 Trash search rỗng. H106 Trash=0, INBOX vẫn 61. Retest bản cuối: [F045](../backend-final-observations.md) (OBSERVED): DELETE UID61 với UIDVALIDITY stale 409 UID_VALIDITY_CHANGED.; [F046](../backend-final-observations.md) (OBSERVED): DELETE permanent ngoài Trash 400 PERMANENT_DELETE_REQUIRES_TRASH.; [F047](../backend-final-observations.md) (OBSERVED): USER khác DELETE UID61 404 GMAIL_NOT_OWNED.; [F048](../backend-final-observations.md) (OBSERVED): DELETE UID9999999 404 MAIL_NOT_FOUND.; [F049](../backend-final-observations.md) (OBSERVED): Search source marker 200 total1 UID61 sau các ca từ chối, chưa bị xóa.; [F050](../backend-final-observations.md) (OBSERVED): DELETE đúng UID61 nguồn với UIDVALIDITY trả 204, body 0 byte.; [F051](../backend-final-observations.md) (OBSERVED): Search Trash trả đúng 1 marker với UID mới=1, date giống source.; [F052](../backend-final-observations.md) (OBSERVED): DELETE trong Trash không có permanent=true trả 400 PERMANENT_DELETE_REQUIRED_IN_TRASH.; [F053](../backend-final-observations.md) (OBSERVED): DELETE đúng Trash UID1 với UIDVALIDITY và permanent=true 204, body 0 byte.; [F054](../backend-final-observations.md) (OBSERVED): Search Trash marker 200 total0.; [F055](../backend-final-observations.md) (OBSERVED): Search INBOX marker 200 total0.; [F056](../backend-final-observations.md) (OBSERVED): Folders INBOX total60 unread59 (60 thư seed, một đã đọc F041), Trash total0 unread0; số lượng đối chứng không giảm. F057 đúng hai audit thành công; H085–090 có lifecycle tương tự trên fixture cũ.. Phần còn lại: Scope xóa local theo UID, 204 và confirmation guard đã quan sát; không chứng minh từng message seed byte-for-byte không đổi. Literal contract khác biệt xóa ngầm/global expunge vẫn OPEN; 422/502, post-mutation mất phản hồi và Gmail Trash/All Mail/UIDPLUS thật chưa xác minh; không dùng live destructive test trên mailbox thật của user. Human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng C: [BE](../backend-supplemental-observations.md): C007–009/C018 move 204 và read-back Trash đúng 1; C029 reject prepare 503 thư còn nguyên. UID-safe explicit permanent giữ nguyên; contract literal vẫn OPEN. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): Vòng F chỉ bổ sung journal safety chung cho mail mutations, 13/13 MailAuditRecoveryIntegrationTest; không lặp ca delete đã đủ và không thay targeted UID/permanent confirmation. Contract literal vẫn OPEN. Không thay trạng thái checkbox.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M16] Xoá mail (Trash semantics)**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
