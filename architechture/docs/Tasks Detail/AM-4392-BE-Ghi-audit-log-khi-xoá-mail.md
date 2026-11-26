# [BE] Ghi audit log khi xoá mail

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M16] Xoá mail (Trash semantics) |
| Owner | TV6 |
| Platform | API |
| Sprint | SPRINT 3 |
| Tuần | Week 3 |
| Kế hoạch | 09/11/2026 → 11/11/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Ghi audit log khi xoá mail` thuộc **[M16] Xoá mail (Trash semantics)** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

DELETE /api/mails/{gmailAccountId}/{folder}/{uid}
- Verify ownership; open folder READ_WRITE
- `getMessageByUID(uid)` → null: 404
- Trash semantics Gmail:
  - Nếu `folder` là `[Gmail]/Trash` → xoá thật: `message.setFlag(Flags.Flag.DELETED, true)` + `folder.expunge()`
  - Nếu folder khác → copy sang Trash trước: `Folder trash = store.getFolder("[Gmail]/Trash"); trash.open(READ_WRITE); folder.copyMessages(new Message[]{msg}, trash); msg.setFlag(DELETED,true); folder.expunge()`
- Log `MAIL_DELETE` `{gmailId, folder, uid}` — KHÔNG log subject/body
- Response 204
- Errors: 404 UID sai, 422 NEEDS_REAUTH, 502 CONNECTION_ERROR

### 4.1. Đối chiếu triển khai hiện tại và bằng chứng (26/11/2026)

Yêu cầu gốc ở trên được giữ nguyên. Đối chiếu mã nguồn/test; kết quả chạy mới nhất tại §5. Contract hiện hành tham chiếu `Mail Server/docs/api-mail-contract.md`.

- **Contract hiện tại:** action `MAIL_DELETE` đúng §4 (một bản ghi, không double-write DELETE_MAIL); audit sau khi thao tác thành công với gmailId, folder thực tế, uid, permanent, userId, IP. Không log subject/body/credential. DELETE trả 204 mặc định, 200 JSON nếu Prefer:return=representation, xóa vĩnh viễn phải có permanent=true (AM-4391); ownership/bảo trì trước kết nối.
- **Code:** `Mail Server/src/main/java/com/mailapp/mail/service/MailServiceImpl.java#deleteMail` (audit sau UID MOVE hoặc UID EXPUNGE), `controller/MailController.java#deleteMail` (remoteAddr), `service/impl/AuditLogServiceImpl.java#log`, `model/AuditLog.java`. Audit best-effort theo service hiện tại, không khẳng định commit audit DB nguyên tử với IMAP.
- **Test có trong repo:** `MailServiceGreenMailIntegrationTest#testSendSearchPaginateFlagsAndDeleteLifecycle` verify MAIL_DELETE hai lần với user/IP (mock audit), `AuditLogServiceTest#testLogSuccess` kiểm tra persistence call. Kết quả chạy tại §5; cần Postman + audit DB thật để đối chiếu detail và không có log thành công khi thao tác thất bại.

- **Đối chiếu bổ sung:** Cập nhật: audit duy nhất sau UID MOVE hoặc UID EXPUNGE thành công, không ghi success khi mất phản hồi; fault injection tests AM-4391. Rà N/A: §4 không có feature flag riêng cho audit xóa; ownership/bảo trì áp dụng ở MailService.deleteMail trước mutation. Không thể xác minh audit DB live với mock.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 5 PASS (code/test thực tế và local manual được duyệt nếu có), 1 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** MailServiceFailureRegressionTest: 22/22 PASS; MailServiceGreenMailIntegrationTest: 10/10 PASS; AuditLogServiceTest: 5/5 PASS; ClientLiveServerE2ETest: 16/16 PASS; MailAuditRecoveryIntegrationTest: 13/13 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). MAIL_DELETE chuẩn, không ghi subject/body, một bản ghi mỗi lần thành công; kiểm thử audit và E2E.
- [x] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — PASS (code/test thực tế). Phạm vi user/account được kiểm ở Backend và caller; Admin theo quyền quản trị. Xem §4.1.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Audit xóa thư không có toggle riêng; DELETE vẫn bảo trì/ownership/confirmation.
- [x] Audit log ghi đúng action — PASS (code/test thực tế). MAIL_DELETE chuẩn, test persisted audit và lọc log. Vòng C: [BE](../backend-supplemental-observations.md): C010–017 MAIL_DELETE tồn tại bền vững qua restart và drain đúng một; không thêm raw subject/body, không retry xóa. Vòng D: D001–003: status aggregate 401/403/200 ADMIN no-store. D005–016: capacity fixture 2, send/flag xác nhận, PREPARED2/localDeferred2; gửi thứ ba 503 AUDIT_UNAVAILABLE, read-back total0; restore drain đúng SEND_MAIL1/MAIL_FLAG_CHANGE1, queue/local0. D017–027: send mới 200, đúng một thư; restart thật khi outcome chưa durable, PREPARED1 vẫn tồn tại, localDeferred0, không thêm audit success, cùng UID/read-back đúng một thư. D045: 8 nhóm canary 0; D046 flags giữ đúng. H2 test chống starvation, giới hạn capacity, trạng thái stale, guard CREATE Trash và mất reply. Không khôi phục outcome RAM đã mất; không auto resolve/replay mail. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): V8 unique eventId, original actor và due retry; E023–041: reject INSERT audit, gửi 200; CONFIRMED giữ attempts/nextAttemptAt qua restart. Reject UPDATE outcome, gửi 200; PREPARED và receipt bền vững, localDeferred1 vẫn khôi phục sau restart thật. Restore outcome: CONFIRMED2/localDeferred0; restore audit: queue0, đúng 2 SEND_MAIL cùng hai eventId và actorId, mỗi thư read-back đúng 1, không gửi lại. V8 không suy PREPARED thành success, không atomic MySQL–Gmail; nếu crash trước receipt durable hoặc cả hai kho mất dữ liệu thì vẫn phải đối chiếu. E mới chứng minh SMTP audit, không coi là manual DELETE mới.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4392 (H refs trong hàng AM-4392, OBSERVED): H091 đúng 2 MAIL_DELETE (uid 61 permanent=false, uid 1 permanent=true), không audit thành công cho H088 bị từ chối; H092/H097 alias phân trang vẫn 2. Retest bản cuối: [F057](../backend-final-observations.md) (OBSERVED): Admin GET logs username/action 200 total2; đúng hai MAIL_DELETE của actor owner: source INBOX UID61 permanent=false và Trash UID1 permanent=true, IP loopback; detail không subject/body, không audit success cho các ca từ chối F045–048/F052. H091/092/097 alias/pagination.. Phần còn lại: Scope audit local đủ; kiểm Gmail thật và lost-reply behavior riêng, human sign-off riêng. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng. Vòng C: [BE](../backend-supplemental-observations.md): C010–017 MAIL_DELETE tồn tại bền vững qua restart và drain đúng một; không thêm raw subject/body, không retry xóa. **Vòng F:** [F201–F226](../backend-supplemental-observations.md): Vòng F regression journal: 13/13; giữ đúng first outcome, không duplicate/replay provider, conflict fail closed. Không lặp delete đã đủ; không đổi trạng thái manual local. Không thay trạng thái checkbox.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M16] Xoá mail (Trash semantics)**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
