# Kịch bản nghiệm thu Backend theo task gốc

Môi trường bắt buộc: Server bootJar và database riêng do `Mail Server/scripts/test-client-e2e.ps1` tạo, HTTPS loopback với certificate riêng, GreenMail và OAuth provider loopback; không dùng Postman workspace, MySQL hoặc Gmail hiện hữu. Bộ Newman có 140 request thật tới JAR, nhưng Google provider là fixture. Số request/assertion **đã chạy và kết quả** xem ma trận test, không suy ra từ danh sách dưới. Ký hiệu Nnn = request mang số nn trong collection; Onn = "OAuth fixture nn"; E12 = ClientLiveServerE2ETest#test12; E13–15 là các test Swing thật. Các suite BE chạy độc lập; cần đối chiếu XML, không đánh dấu live/human manual chỉ từ việc có test.


**Kết quả lần chạy cuối (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. MySQL native 9.5.0, datadir cô lập, utf8mb4/utf8mb4_unicode_ci, Flyway V1–V6 thành công, 4 bảng nghiệp vụ, 9 settings, 1 admin; 1 admin hash đúng shape BCrypt, 0 linked App Password có cột encrypted không rỗng (chỉ aggregate; không chứng minh mọi giá trị mã hóa). Nguồn: mysql-metadata.tsv. MySQL native 9.5.0, datadir cô lập, utf8mb4/utf8mb4_unicode_ci, Flyway V1–V6 thành công, 4 bảng nghiệp vụ, 9 settings, 1 admin; 1 admin hash đúng shape BCrypt, 1 linked App Password có cột encrypted không rỗng (chỉ aggregate; không chứng minh mọi giá trị mã hóa). Nguồn: manual-mysql-metadata.tsv. Không suy rộng sang schema/volume triển khai thật; Flyway có thể cảnh báo phiên bản MySQL ngoài phạm vi hỗ trợ. Kịch bản N/O/E bên dưới là automated evidence. Quan sát từng request: [H001–H150 trước sửa](backend-manual-observations.md) và [F001–F095 trên JAR cuối](backend-final-observations.md). 31/47 generic manual PASS local, 16 OPEN; human sign-off/Gmail thật riêng. Không suy ra manual từ assertion.

| Task | Kịch bản và kỳ vọng kiểm chứng | Phần cần xác nhận ngoài bằng chứng tự động |
|---|---|---|
| AM-4309 | N04–07, N36, N83; đăng ký 201 USER, trùng hoa/thường 409, validate 400, cờ tắt 403, audit REGISTER. Race MySQL chỉ một insert thắng. | Thao tác lại Postman bằng tay nếu yêu cầu ký nghiệm thu thủ công. |
| AM-4310 | N08–11, N18, N32–35, N43–48, N56–59, N84–85; login username/email, JWT, sai 401, khóa 403, bảo trì USER/ADMIN, giới hạn 429. | Human Postman; JWT hết hạn có test filter riêng. |
| AM-4311 | N02, N10–11, N44–45; /me trả đúng identity/role, không password, không JWT hoặc token sai 401. | Human Postman với token hết hạn và forced change. |
| AM-4312 | N14–18, N86; mật khẩu hiện tại sai/đổi trùng 400, đổi đúng 200, token mới, login mật khẩu cũ thất bại. | Human Postman. |
| AM-4313 | N19, O05, O20, O29–30; danh sách chỉ của actor, hai owner có hai ID khác, không credential. | Gmail test thật nếu cần chứng minh hai hộp thư Gmail độc lập. |
| AM-4314 | N22, N37, O13–14 và E12/E14; invalid/disabled/duplicate/quota, IMAP fixture sai/đúng, tạo trước rồi UPSERT giữ ID, audit. | IMAPS Gmail trực tiếp, tài khoản 2FA/App Password, timeout mạng/DNS thực tế; không coi GreenMail là Gmail. |
| AM-4315 | N20, O22, O32–35 và E12/E14; foreign/missing 404, owner 204, owner khác không bị ảnh hưởng, audit. | Gmail thật: xác minh unlink không xóa thư; revoke thật nếu OAuth. |
| AM-4316 | N41, N71, N73–75, O12/O16; URL/scope/PKCE/state, flag, quota, poll ownership. | Browser consent, state hết 5 phút với Google thật. |
| AM-4317 | N76–79, O02–10, O16–19, O26–31; public callback, PKCE, UserInfo + profile, duplicate legacy, UPSERT, replay bị chặn, audit. | Consent Google thật, thiếu scope và cancel, profile Gmail, refresh token thật. |
| AM-4318 | O22, O32–35; DELETE 204 đúng owner, provider nhận revoke, owner khác còn liên kết, audit. | Google revoke thật và xác minh token không dùng lại được. |
| AM-4319 | O37–38, N62–64, N95–98; admin cascade, provider revoke, target mất quyền, audit; last-admin guard có test BE. | Google revoke thật nhiều hộp thư, xác nhận trên Google; không xóa user hiện hữu. |
| AM-4320 | N21/O21/O25, E12/E14 và FolderCountReaderTest: foreign/missing, list folder/count/unread, mở và đóng đúng. | Gmail thật: folder Gmail special-use, total/unread, mất mạng. |
| AM-4321 | E12/E14, MailServiceGreenMailIntegrationTest: page/search, ngày/UID DESC, 1000+2 thư fixture, tie, filter attachment, không fetch body. | Gmail thật lớn, benchmark và so sánh khi hộp thư thay đổi; offset không bảo đảm snapshot. |
| AM-4322 | E12/E14, 5 MIME fixture: UID, plain/HTML/nested, metadata, markSeen và preview không seen, file index. | Gmail thật: render/charset/body mẫu, UID không tồn tại. |
| AM-4323 | O15, E12/E14 và MailControllerCompatibilityTest/MailServiceFailureRegressionTest: flag, multipart cũ/mới, SMTP fixture, subject/body/địa chỉ, từng file 413, audit. | Gmail thật gửi/nhận, xác nhận metadata audit thay to/subject để bảo vệ riêng tư. |
| AM-4324 | E12, MailServiceGreenMailIntegrationTest: stream đúng byte/hash, index, MIME lồng, tên trùng, 0 byte, kích thước giới hạn. | Postman tải binary và Gmail thật đối chiếu hash/file. |
| AM-4325 | N38–39, N51–54, N87; admin tạo dù registration tắt, 201, trùng 409, forced change, audit. | Human Postman. |
| AM-4326 | N12, N49, O24; ADMIN search/filter/page và target gmailCount, USER bị từ chối. | Human Postman, đổi các filter/sort trên dữ liệu test. |
| AM-4327 | N50, N81, O24; admin detail đúng target, 404, mailbox metadata không credential. | Human Postman với target có nhiều mailbox. |
| AM-4328 | N55–57, N62, N88, E15; khóa đúng target, JWT cũ từ chối, tự khóa bị chặn. | Human Postman; kiểm cạnh tranh khóa Admin cuối cùng theo BE test. |
| AM-4329 | N58–59, N89, E15; mở khóa, JWT lại dùng được, audit. | Human Postman. |
| AM-4330 | N60–66, N90; đổi role, JWT nhìn role DB, self guard. | Human Postman; last-admin cạnh tranh theo BE test. |
| AM-4331 | N67–72, N91; mật khẩu yếu từ chối, newPassword tường minh, cũ thất bại, bắt đổi, audit không secret. | Human Postman; luồng reset tự sinh an toàn có test BE. |
| AM-4332 | N64, N95–99, O37–38; xóa user target, cascade, JWT cũ mất quyền, revoke fixture, audit. | Human Postman, revoke Google thật (AM-4319). |
| AM-4333 | N01, N30–31, N46–48; công khai không JWT, đúng cờ mới, không nhạy cảm. | Human Postman, thử các trạng thái public config. |
| AM-4334 | N03, N24, N45; ADMIN nhận đủ 9 key, JWT/role gate, metadata đúng. | Human Postman. |
| AM-4335 | N25–31, N40–48, N92, N100 và E15; validation, atomic, cache, cờ và quota dương, restore. | Human Postman; cạnh tranh khóa setting cần đối chiếu suite và DB thực tế. |
| AM-4336 | N82, O38, E15; stats invariants, account giảm sau cascade, chỉ ADMIN. | Human Postman, đối chiếu số liệu DB test. |
| AM-4337 | N83–94, E15; lọc audit, action alias, pagination/CSV, không credential. | Human Postman: date/user/action filter trên nhiều trang. |
| AM-4338 | bootJar, actuator health của JAR, migration V1–V6 trong MySQL thật mới. | Port triển khai 8443 và config môi trường triển khai chưa dùng. |
| AM-4339 | keytool PKCS12 random SAN localhost, HTTPS JAR, TlsSecurityTest và E01; trust certificate riêng, không trust-all. | Chứng chỉ production/8443 thực tế chưa dùng. |
| AM-4340 | Metadata MySQL 9.5: charset/collation, 6 migrations, 4 bảng, 9 keys, admin; migration data preservation H2 và MySqlConcurrentAcceptanceTest. | MySQL version triển khai, schema legacy thật; seed ngẫu nhiên không dùng mật khẩu cứng/log plaintext. |
| AM-4341 | N02, N09–11, N44, N52, N56, N61/66; HS256/v2 username-sub/uid, expiry, legacy JWT, role/enabled và allowlist theo BE suite. | Human Postman; không mở wildcard auth làm lộ /me/change-password. |
| AM-4342 | AesCipherTest: round-trip, IV khác nhau, tag tamper và key; link thực tế lưu credential AES-GCM ở E12/O03. | Kiểm DB/log bằng tay chỉ trên fixture, không xuất key/credential. |
| AM-4343 | N02–05, N11, N25–29, N80–81; ErrorDto code/path/status, 400/401/403/404/409/429; mail error qua E12 và BE. | Manual local PASS vòng F: F204/F205/F222 422, F213 502, F226 canary0 + evidence cũ; human sign-off riêng. |
| AM-4344 | N83–94/N98, O31/35, E12/E14: audit đúng action/actor, không duplicate alias và không secret. | Human kiểm log DB fixture; không log body/to/subject. |
| AM-4345 | N30–31, N40–48, N100; cache invalidation sau commit, key validation, typed getters và fallback theo suite. | Human Postman; xem riêng AM-4335. |
| AM-4358 | DefaultMailConnectionFactoryGreenMailTest và MailConnectionFactoryTest: raw Store/Transport caller-owned chưa connect, Session và TLS/timeout, wrapper fallback. | Remote TLS Gmail thật, chứng chỉ đúng host. |
| AM-4359 | GreenMail và E12/E14: IMAP/SMTP App Password đúng/sai, raw adapter đóng, probe unpooled. | Gmail thật 2FA/App Password. |
| AM-4360 | XOAuth2IntegrationTest, AccessTokenProviderTest, MailFallbackConnectionTest: SASL/token/timeout, fallback, scoped authenticator; O03 chứng minh token exchange ở fixture. | XOAUTH2 handshake IMAPS/SMTP thật, refresh/revoke Google thật. |
| AM-4361 | RealGmailIntegrationTest và RealGmailXOAuth2IntegrationTest opt-in, không coi SKIP là PASS. | Account riêng, 2FA/App Password, access token scope mail còn hạn; chạy trực tiếp. |
| AM-4391 | E12/E14, MailServiceFailureRegressionTest: move Trash, permanent=true, sai UID/UIDVALIDITY, UID MOVE/UIDPLUS, mất reply không retry/audit sai. | Gmail Trash/All Mail thật; không global expunge; giữ xác nhận permanent. |
| AM-4392 | E12/E14, AuditLogServiceTest: đúng 2 MAIL_DELETE cho move + permanent; lỗi không log thành công, không subject/body. | Xác minh trên Gmail thật và kiểm audit Postman tay. |
| AM-4394 | E12/E14, BE GreenMail: star/seen, persisted flags, audit MAIL_FLAG_CHANGE. | Gmail thật, quan sát cờ trên Gmail. |
| AM-4396 | Entity/DTO, repository unique, V3/V6, E12/E14 link fixture. | Chủ dự án tạo Gmail test riêng, 2FA/App Password, kết nối standalone thật. |
| AM-4397 | MailServiceGreenMailIntegrationTest: 5 MIME mẫu, PDF/PNG/text, UTF-8 và tải đúng byte. | Gửi các mẫu vào Gmail test thật; dùng fixture không chứng minh Gmail đã nhận. |
| AM-4398 | MailMessageBuilderTest, E12: round-trip MimeMessage text/HTML/files, metadata/bytes. | Postman gửi multipart thật và Gmail test nhận file. |

Không dùng `expunge()` toàn mailbox, không vô hiệu hóa xác thực TLS, không in mật khẩu Admin. Mọi trường hợp lỗi/timeout xóa phải refresh và đối chiếu source/Trash trước khi thử lại. Người nghiệm thu chỉ dùng dữ liệu fixture, không cung cấp bí mật vào tài liệu.

## Cập nhật evidence vòng 65 OPEN

Xem [G/J/L](backend-supplemental-observations.md), [S01–S04](swing-supplemental-observations.md) và [hướng dẫn residual hiện tại](remaining-acceptance-guide.md). Kịch bản cũ là baseline lịch sử, dùng phần còn lại hiện tại trong §5 và acceptance-review để tránh lặp nhánh đã kiểm. Backend 250/255 (full + chạy lại 2 suite sau sửa assertion privacy), Client 108/108, tổng 358 PASS / 363 JUnit, 5 SKIP, 0 FAIL, 0 ERROR. 291 PASS/116 N/A/61 OPEN; không dùng tự động thay human/live hoặc phê duyệt contract.

Bổ sung vòng 61 OPEN: [R2/R3](swing-supplemental-observations.md), [T/P](backend-supplemental-observations.md), xem residual hiện tại trong §5 và guide. Client mới 105/105; BE/Newman giữ evidence trên JAR không đổi. Không lặp public startup/TLS/timeout và local socket fault/500 đã quan sát; không suy thành manual auth, Gmail live hoặc log privacy đầy đủ.

Vòng audit/lease/race: xem [A/B](backend-supplemental-observations.md), [regression Swing](swing-supplemental-observations.md) và mục 9 của [residual guide](remaining-acceptance-guide.md). Giữ 291/116/61; không thay manual/live/human bằng kết quả tự động.

## Vòng C: audit recovery mới

C001–041: reject INSERT audit, send/flag/move thành công và giữ CONFIRMED3; restart vẫn 3; restore drain đúng một event mỗi action, tick tiếp không trùng; reject DELETE outbox rollback INSERT audit; reject prepare không mutation; reject outcome lưu PREPARED, phục hồi khi DB hoạt động trong cùng process. Cuối queue0, SEND_MAIL3/MAIL_DELETE1/MAIL_FLAG_CHANGE1. MailAuditRecoveryIntegrationTest 4/4. Không phục hồi row mất từ trước V7, không atomic MySQL–Gmail; crash trước durable confirmation giữ PREPARED cần đối chiếu, không auto success.

Xem C001–041 trong [báo cáo BE](backend-supplemental-observations.md); [residual mới](open-criteria-current.md). V1–V7 native fixture, 503 AUDIT_UNAVAILABLE trước mutation, không auto resend/delete; không thay manual auth/live hoặc contract.

**Regression schema V7-L:** V7-L: Native MySQL database-only, legacy V1/V2 seed synthetic → V6 làm tiền đề → V7. Snapshot 4 bảng trước/sau V7 khớp; outbox mới rỗng, 7 migration success; khởi động JAR vẫn giữ nguyên dữ liệu, 9 settings/1 admin/5 bảng (4 cũ + outbox). Exit 0, datadir fixture đã xóa. Lý do regression riêng: V7 vừa thêm và legacy harness phải nhận version 7; không phải nghiệm thu lại các task migration/AES/TLS cũ. Client/Newman không chạy. Evidence Mail Server/build/acceptance/legacy-upgrade-summary-V7.txt và mysql-metadata-V7-legacy.tsv. Contract AM-4340 vẫn OPEN vì literal bootstrap/schema và môi trường triển khai chưa chấp thuận.

## Vòng D — kiểm chứng trực tiếp

H009–011/H098: 201 USER, duplicate 409, validation 400 và REGISTER. D028–039: public flag tắt, payload hợp lệ 403 REGISTRATION_DISABLED, user count 0; restore flag, 201 USER/mustChangePassword=false; username trùng và khác hoa/thường 409 USERNAME_TAKEN, email trùng khác hoa/thường 409 EMAIL_TAKEN. D042–043: đúng một user enabled, đúng một REGISTER cùng actor ID. D040–041/D044/D047: thêm cặp đăng ký trùng cho 201/409, đúng một user và một REGISTER; không xác nhận overlap thủ công, race dựa trên MySqlConcurrentAcceptanceTest 2/2 ở mốc trước. Đủ scope manual local Postman runtime từng request của §4 gốc; không phải human sign-off.

D001–003: status aggregate 401/403/200 ADMIN no-store. D005–016: capacity fixture 2, send/flag xác nhận, PREPARED2/localDeferred2; gửi thứ ba 503 AUDIT_UNAVAILABLE, read-back total0; restore drain đúng SEND_MAIL1/MAIL_FLAG_CHANGE1, queue/local0. D017–027: send mới 200, đúng một thư; restart thật khi outcome chưa durable, PREPARED1 vẫn tồn tại, localDeferred0, không thêm audit success, cùng UID/read-back đúng một thư. D045: 8 nhóm canary 0; D046 flags giữ đúng. H2 test chống starvation, giới hạn capacity, trạng thái stale, guard CREATE Trash và mất reply. Không khôi phục outcome RAM đã mất; không auto resolve/replay mail.

Lũy kế Backend 263/268, Client 110/110, tổng 373 PASS / 378 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng D chạy mới BE 43/43, Client 47/47 (gồm E2E 16/16) và Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới. [Residual hiện tại](open-criteria-current.md).

## Vòng E (kết quả ưu tiên hơn snapshot trên)

E008–022: hai tài khoản fixture được promote ADMIN, cả hai endpoint Admin 200; hai PUT demote chồng lấn (InnoDB lock-wait=1 E015) trả 200/403 vì kiểm tra actor dưới transaction lock. Actor mất role gọi Admin 403; restore cả hai USER, đúng 1 enabled ADMIN, bootstrap không bị sửa; 4 audit thành công, không audit giả. Đây là overlap native với 2 admin hy sinh và bootstrap vẫn tồn tại, không phải ca đúng 2 active Admin cuối.

E023–041: reject INSERT audit, gửi 200; CONFIRMED giữ attempts/nextAttemptAt qua restart. Reject UPDATE outcome, gửi 200; PREPARED và receipt bền vững, localDeferred1 vẫn khôi phục sau restart thật. Restore outcome: CONFIRMED2/localDeferred0; restore audit: queue0, đúng 2 SEND_MAIL cùng hai eventId và actorId, mỗi thư read-back đúng 1, không gửi lại. V8 không suy PREPARED thành success, không atomic MySQL–Gmail; nếu crash trước receipt durable hoặc cả hai kho mất dữ liệu thì vẫn phải đối chiếu. E042 aggregate 8 nhóm đều 0; canary subject/body/filename và lỗi SQL được dùng trong E, không có chứng cứ mới cho mọi nhóm credential/path/recipient hoặc Gmail live.

V8 native V7→V8 giữ historical audit và PREPARED; 64 BE ca mục tiêu và 653 Newman assertion xác minh. Không lặp AM-4309 hoặc các nhánh đã đủ chứng cứ. Chi tiết [E001–042](backend-supplemental-observations.md) và [residual](open-criteria-current.md).

## Vòng F — kết quả ưu tiên hơn snapshot trên

Vòng F: 293 PASS, 116 N/A, 59 OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live OPEN. 27/47 BE và 1/31 FE manual PASS local agent; human sign-off riêng. F204/F205: provider từ chối xác thực mailbox App Password hiện có, GET folders qua Postman/Newman và POST multipart qua HTTPS trả 422 GMAIL_AUTH_FAILED, ErrorDto đủ field, no-store; F222: refresh OAuth loopback thực bị invalid_grant, 422 GMAIL_NEEDS_REAUTH, F223 refreshRejected=1, F224 NEEDS_REAUTH. F213–217: provider đã giao đúng 1 thư rồi lỗi ACK, HTTP 502 GMAIL_CONNECTION_ERROR với cảnh báo chưa xác định kết quả, không retry; đúng 1 MAIL_OPERATION_UNCERTAIN, không SEND_MAIL success giả, socket về 0. F226: sáu marker synthetic trong log/outbox/audit đều 0. Kết hợp evidence trước F về validation/RBAC/404/406/409/410/413/415/429/500/502/503/504, đủ nghĩa vụ manual local §4 AM-4343; không phải Google live, không chứng minh mọi secret hay human sign-off. MailAuditRecoveryIntegrationTest 13/13 (3 test mới): journal fail closed khi write/remove lỗi, kiểm lock/root và cả hai sibling receipt/pending, inventory không đầy đủ không được tin; giữ conflict/corrupt, không tự xóa. Recovered receipt phải validate lại trước finalize; unreserved finish không biến PREPARED thành success, observation đầu tiên không bị ghi đè; replay chỉ audit, không SMTP/IMAP mutation. Runtime fault test H2 giữ PREPARED khi DB reject, ghi đúng một audit từ observation còn RAM khi DB hồi phục, không duplicate và chỉ mở admission khi receipt xử lý xong. Không suy thành bảo đảm mọi OS crash/disk fault hay atomic MySQL–Gmail.

AM-4320/4323/4358/4359/4360 bổ sung lỗi provider local, closure/recovery; raw lifecycle/live vẫn OPEN. AM-4314 không đồng nhất provider deny existing mailbox với POST link. AM-4344 còn LOGIN_FAILED/ADMIN_RESET trực tiếp. Xem [residual từng criterion](open-criteria-current.md), không lặp nhánh đã đủ. Lũy kế Backend 270/275, Client 116/116, tổng 386 PASS / 391 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng F chạy mới BE 44/44, Client 50/50 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới.

## Vòng G2 — sau vòng F (kết quả hiện hành)

V9 native V8→V9 preservation: owner, dual methods, trạng thái và ciphertext synthetic giữ nguyên, revision=0; fresh V1–V9. JPA optimistic version chống stale write/delete; 409 GMAIL_ACCOUNT_CHANGED an toàn. OAuth cache ràng buộc grant/owner, publish sau save; reject scope giảm/invalid_grant chính xác, tránh refresh lặp, persist rotation; fallback chỉ active sau connect, failure không audit thành công, scoped SMTP authenticator, resource close khi save conflict. Local raw lifecycle và factory regression PASS, không chứng minh Gmail live, mọi race hoặc atomic revoke/DB. Audit journal V8 kế thừa không sửa/replay mutation.

293 PASS / 116 N/A / 59 OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Không có tiêu chí mới đủ toàn bộ nghĩa vụ để đóng. Không lặp các thao tác đã đủ hoặc bị safety gate chặn.

## Vòng G3 — kết quả hiện hành

G3: MailboxRevisionChainIntegrationTest 2/2 trên H2/JPA thật (OSIV=false) và provider loopback: rotate refresh token commit, snapshot detached nhận đúng revision từ chính save; stale grant/cache bị từ chối trước provider; invalid_grant→NEEDS_REAUTH→fallback thực qua GreenMail có hai revision liên tiếp, đúng một audit mỗi action (mock audit), Store đóng; method bị gỡ thì snapshot cũ không mở raw/wrapper. Factory kiểm owner/id/revision trước credential/cache và sau open, đóng resource/lease nếu conflict. Không thay V9/schema, không retry mutation. Guard không bảo đảm thu hồi kết nối đã cấp hoặc atomic MySQL–Gmail.

293 PASS / 116 N/A / 59 OPEN, 26 task đóng đầy đủ, 52 task còn OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Không có tiêu chí mới đủ toàn bộ nghĩa vụ. Không lặp thao tác credential bị gate, C/D2/E1, AM-4309, AM-4343, E001, E042; không thay bootstrap.

## Vòng G4 — kết quả hiện hành

G4 R01–R12: observer CLI tương tác từng lệnh trên factory thật từ JAR G4, GreenMail mới loopback; account/password/AES synthetic ngẫu nhiên chỉ trong process, không app login/DB. Raw Store/Transport chưa connect khi cấp; caller connect/close, INBOX READ_ONLY total=2/unread=2, Folder close không đóng Store; FolderNotFoundException và caller abort trước send đều đóng scope. Final storeOpen=false/transportOpen=false, fixtureMessages=2, quit exit0, root removed. Không SMTP DATA fault mới, không suy isConnected thành đo mọi socket OS; không HTTP/Postman/native Swing/Gmail live mới.

G4: MailServiceImpl folderRamCache thêm revision cùng owner/mailboxId; invalidation tất cả revision của đúng owner/mailbox sau mutation. FolderCacheTest 2/2, test mới phân biệt hai revision và cache hit từng revision; TTL vẫn hữu hạn, không đảm bảo read/mutation nguyên tử. MailServiceFailureRegressionTest 22/22 và MailServiceGreenMailIntegrationTest 10/10 chạy lại vì service thay đổi. Không đổi V9, bootstrap, audit journal, OAuth refresh/fallback hay credential/permission path.

G4: RealGmailIntegrationTest bổ sung raw standalone Store.connect/READ_ONLY/count/close và Transport connect/close trong nhánh App Password opt-in hiện hữu, dùng account/AES chỉ trong memory. RealGmailMimeFixturesTest bổ sung metadata sample ordinal, INBOX UID/UIDVALIDITY/attachment count sau verify exact bytes; không log raw content/secret, giữ mẫu cho manual, không tự xóa hoặc retry khi kết quả chưa rõ. Hai source đã compile, chưa chạy live; vẫn cần dedicated account/consent/permission và verify targeted cleanup/response-log. Không thay consent hoặc token flow.

293 PASS / 116 N/A / 59 OPEN; 26 task đóng, 52 còn OPEN. 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Raw caller-owned local đã quan sát, nhưng không có criterion đủ toàn bộ evidence mới. Ma trận 59 criterion, 171 hàng nghĩa vụ, 92 hàng thiếu prerequisite/evidence. Không đổi checkbox/spec, không tự phê duyệt hoặc thay loại evidence. [Ma trận nghĩa vụ](acceptance-obligation-matrix.md).

## G5 — đối chiếu không chạy lại Backend

G5 xác nhận 4344-M.04 M/K theo D017–027 (restart, PREPARED1/localDeferred0, read-back cùng UID đúng một thư, không thêm audit success) và F MailAuditRecoveryIntegrationTest 13/13. Không auto-finalize/replay mutation; sự cố mất durable evidence vẫn cần đối chiếu nghiệp vụ được phép. Không yêu cầu tạo fault mới để chứng minh lại. AM-4344-M còn 4344-M.02: LOGIN_FAILED và ADMIN_RESET_PASSWORD manual (H).

Ba contract chưa duyệt: AM-4323-C.03 metadata-only (masked sender và counts; không raw To/subject) cần quyết định D, C.04 live vẫn L. AM-4340-C.03 cần D cho bootstrap ngẫu nhiên/BCrypt/force-change, không plaintext console, schema additive V1–V9; local MySQL/seed đã có evidence, có thể đóng riêng contract sau duyệt rõ scope. AM-4391-C.02 cần D cho permanent=true rõ ràng + UIDVALIDITY/UID-targeted MOVE/EXPUNGE, default 204 (Prefer 200 tùy chọn), không global expunge; C.03 live vẫn L. Không tự sửa §4 hoặc coi code hiện có là duyệt.

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

## G6 — scope manual local

78 task / 468 tiêu chí: 297 PASS, 116 N/A, 55 OPEN; 29 task đóng, 49 task còn OPEN. 297/352 tiêu chí áp dụng = 84,375%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Giữ 171 ID: 81 K/M có evidence, 86 điều kiện trực tiếp thiếu và 4 tham chiếu S chưa đạt; tổng 90 hàng chưa có đủ evidence.

G6 đóng đúng bốn checkbox Manual test pass local AM-4320, AM-4323, AM-4358, AM-4359 từ evidence quan sát đã có, đối chiếu §4 và phân công 02/03; không thêm manual run. AM-4320/4358/4359 đóng task, AM-4323 còn contract D/L. Đây là điều chỉnh phạm vi nghiệm thu, không thay yêu cầu gốc hay đánh dấu live PASS. Bốn hàng S giữ nguyên ID/điều kiện gốc và link đến criterion vẫn OPEN; không tính S là K/M hay N/A. Nếu yêu cầu sign-off đúng công cụ/human ngoài local agent thì vẫn phải xác minh riêng.

AM-4320: PASS manual local theo §4/EP-M1 và chuẩn local đã dùng cho AM-4321/4322: H015/H084/H125, F056, G002/G054–057, T004–008, F204/F222 và raw G4 R02–R05/R09. Count/owner/error/connect/close có observation; cache/revision có regression. 02 C2-TV4 và 03 M08 demo Gmail web/count/special-use/TLS vẫn chưa đạt; giữ 4320-M.05 S/L tại AM-4361-M.02 và AM-4397-M.04. Không claim nghiệm thu Gmail thật.

AM-4323: PASS manual local multipart đúng hành vi đang triển khai: H027/H040/H042/H120/H136–140, F013/F014/F064–065/F069–070, G045–063/G079–080, J001–005/J009–011, T009–013, F205/F213–217, C/D/E, G4 R06–R08/R10. Không tính setup H036 hoặc hash lỗi trước fix. Có direct HTTP từng request (không đổi nhãn thành Postman), delivery/read-back/bytes local và no retry khi uncertain. Giữ 4323-M.05 S/L trong 4323-C.04: Gmail thật cả hai auth mode, delivery/read-back/attachment, TV5. Contract 4323-C.03 D về audit metadata-only vẫn OPEN, không phê duyệt raw To/subject.

AM-4358: PASS manual local scope interface: 03 M07 tách Interface MailConnectionFactory, implementation App Password, XOAUTH2 và Test factory kết nối thật; 02 C3 chốt interface sớm. G4 R02–R10 actual raw Store/Transport caller-owned connect/close và lỗi scope; H/B/T/F wrapper HTTP/lease/timeout; config có code/test. CLI không gọi là Postman. 4358-M.04 S/L remote hostname/TLS/timeout và hai auth mode vẫn phải thỏa AM-4361-M.02/C.02.

AM-4359: PASS manual local scope implementation App Password theo 03 M07: G4 R02–R10 raw AES synthetic, IMAPS/SMTP caller-owned; H/F/G/T connect/close/error/recovery thực local. F204/F205 deny trên link đã có, không phải POST wrong App Password. 4359-M.03 S/H/L vẫn phải thỏa AM-4314-M.03/M.04 (POST link/probe), AM-4396-M.02/M.03 (dedicated 2FA/standalone) và AM-4361-M.02/C.02 (remote TLS/timeout). Không claim Gmail thật.

G6: 4311-M.02 chuyển M/K theo bản ghi JSON đã scrubbed H005/H071/F026: GET /api/auth/me 200, đúng 7 key id, username, fullName, email, role, mustChangePassword, createdAt; không extra key, createdAt parse được, mustChangePassword boolean false. H029/H069/H133/F032 cũng đúng shape. Redactor giữ key, chỉ che value. Không truy cập raw/private manifest, không thực hiện login mới. 4311-M.03 vẫn H: expiry401 và forced-change /me thực.

Ba contract D chưa duyệt: AM-4323-C.03 dùng audit metadata-only (masked sender/count, không raw To/subject/body), C.04 vẫn L; duyệt D chỉ giải quyết phần contract chưa đủ live. AM-4340-C.03 bootstrap ngẫu nhiên/BCrypt/force-change, không plaintext console, schema additive V1–V9; local MySQL/seed đã có evidence, owner duyệt rõ thì có thể đóng criterion contract và task. AM-4391-C.02 permanent=true rõ ràng, UIDVALIDITY, UID-targeted MOVE/EXPUNGE, default 204 (Prefer 200 tùy chọn), không global expunge; C.03 vẫn L. Giữ an toàn, không tự duyệt/đổi §4.

Evidence G6: Mail Client/build/acceptance/client-round-G6-suite-summary.json; 3 suite: GmailMultiAuthClientTest 5/5, MailboxRegressionTest 13/13, SwingFeatureAcceptanceTest 16/16, tổng 34/34, 0 skip/fail/error. gradlew.bat test --tests *MailboxRegressionTest --tests *SwingFeatureAcceptanceTest --tests *GmailMultiAuthClientTest --max-workers=1 --no-daemon --console=plain --offline, BUILD SUCCESSFUL. Hai test mới; không cộng 32 test rerun vào lũy kế. E2E/MySQL G5, Newman/OAuth loopback/BE G4 kế thừa; không tạo fixture server mới. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A.

## Phê duyệt contract — 26/11/2026 (sau G6)

78 task / 468 tiêu chí: 298 PASS, 116 N/A, 54 OPEN; 30 task đóng, 48 task còn OPEN. 298/352 tiêu chí áp dụng = 84,659%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 5 contract, 3 privacy/live. 171 ID: 81 K/M kế thừa + 3 A phê duyệt = 84 nghĩa vụ đã thỏa; 83 trực tiếp thiếu + 4 shared S = 87 còn thiếu.

Chủ dự án phê duyệt 26/11/2026, sau G6: AM-4323-C.03 A — SEND_MAIL metadata-only (masked sender/counts, không raw/hash To/subject/body), C.04 vẫn L cả hai mode Gmail, delivery/read-back/attachments, shared 4323-M.05. AM-4340-C.03 A — bootstrap từ ADMIN_INITIAL_PASSWORD/cấu hình, BCrypt và forced-change, không log plaintext, không ghi đè Admin, Flyway V1–V9 additive; với evidence MySQL/seed kế thừa, contract và task đã PASS. AM-4391-C.02 A — explicit permanent=true trong Trash, UIDVALIDITY, targeted UID MOVE/UIDPLUS, default 204, Prefer 200 tùy chọn, fail-closed; C.03 vẫn L Gmail Trash/All Mail thật. Không có D còn chờ; phê duyệt không thay bằng chứng live, human/manual, test hay triển khai.

Không chạy test mới, không thay source/JAR, giữ JUnit 410/415 (5 live SKIP), Newman 653/653 kế thừa và E2E 16/16 kế thừa. Mọi mô tả D/chờ phê duyệt ở các mốc trước là lịch sử, không phải residual hiện hành.
