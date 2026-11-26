# [BE] Xử lý lỗi thống nhất

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M01] Hạ tầng Backend & Bảo mật nền tảng |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 27/10/2026 → 28/10/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Xử lý lỗi thống nhất` thuộc **[M01] Hạ tầng Backend & Bảo mật nền tảng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `@RestControllerAdvice` `GlobalExceptionHandler`
- `ErrorDto`: `{timestamp, status, error, message, code, path}`
- `ErrorCode` enum: `VALIDATION_ERROR, UNAUTHORIZED, ACCOUNT_DISABLED, FORBIDDEN, NOT_FOUND, REGISTRATION_DISABLED, FEATURE_DISABLED, GMAIL_NOT_OWNED, GMAIL_DUPLICATE, GMAIL_LIMIT_REACHED, GMAIL_AUTH_FAILED, GMAIL_NEEDS_REAUTH, GMAIL_CONNECTION_ERROR, PAYLOAD_TOO_LARGE, INTERNAL_ERROR`
- Map: `MethodArgumentNotValidException`→400 VALIDATION_ERROR, `AccessDeniedException`→403 FORBIDDEN, `NoSuchElementException`/`EntityNotFoundException`→404 NOT_FOUND, generic `Exception`→500 INTERNAL_ERROR (log stacktrace)
- Tất cả error response đều đúng schema — Client parse `code` để quyết định UI

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** GlobalExceptionHandler @RestControllerAdvice và ErrorResponse gồm timestamp,status,error,message,code,errorCode,path; filter security/must-change/maintenance cũng có cùng trường. DTO/malformed JSON/missing parameter/part/type mismatch→400 VALIDATION_ERROR; AccessDenied→403 FORBIDDEN; NoSuchElement/EntityNotFound→404 NOT_FOUND; upload quá lớn→413 PAYLOAD_TOO_LARGE; method→405, media→415; duplicate constraint→409; lỗi không xác định→500.
- **Khác biệt:** thêm ErrorCode enum; lỗi generic 500 INTERNAL_ERROR và router 404 NOT_FOUND; full unlink 404 NOT_FOUND, login 403 ACCOUNT_DISABLED, lỗi password 400 VALIDATION_ERROR theo §4. Endpoint mở rộng vẫn có mã nghiệp vụ riêng; giữ alias trường errorCode. Diagnostic generic ghi class/stack frames, không raw exception message. Callback OAuth là HTML 200 có chủ đích, không dùng schema JSON lỗi. Container-level lỗi ngoài handler cần xác minh riêng.
- **Mã nguồn:** `exception/GlobalExceptionHandler.java`, `exception/ErrorResponse.java`, `exception/ErrorCode.java`, `security/SecurityConfig.java`, `security/JwtAuthenticationFilter.java`, `security/MustChangePasswordFilter.java`.
- **Test source:** `controller/AuthControllerIntegrationTest.java` (testMalformedJsonReturnsStructured400, globalExceptionMappingsReturnStructuredSafeErrors kiểm tra 403/404/413/500 và không lộ chi tiết nội bộ, wrongMethodAndContentTypeReturnStructuredErrors kiểm tra 405/415/400 thiếu tham số), các login error tests; `security/JwtAuthenticationFilterTest.java`, `controller/AdminIntegrationTest.java`. Lỗi tại container ngoài MVC cần xác minh trên Postman.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.


- **Bằng chứng cập nhật:** Lỗi thực tế duplicate index users.username trên MySQL đã sửa thành USERNAME_TAKEN; GlobalExceptionHandlerTest và hai race thật đạt.

- **Bổ sung vòng G2 (sau F):** GlobalExceptionHandlerTest 3/3 kiểm HTTP409 GMAIL_ACCOUNT_CHANGED không raw exception; đây là regression mới do V9, không nghiệm thu lại manual F.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AuthControllerIntegrationTest: 14/14 PASS; GlobalExceptionHandlerTest: 3/3 PASS; JwtAuthenticationFilterTest: 9/9 PASS; MySqlConcurrentAcceptanceTest: 2/2 PASS; OAuth2ControllerTest: 4/4 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). ErrorCode enum và mã chuẩn UNAUTHORIZED/ACCOUNT_DISABLED/NOT_FOUND/INTERNAL_ERROR; schema alias errorCode, callback HTML theo spec.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Error handler không thực hiện mutation/ownership/feature; caller chịu trách nhiệm.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Error handler không thực hiện mutation/ownership/feature; caller chịu trách nhiệm.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Error handler không thực hiện mutation/ownership/feature; caller chịu trách nhiệm.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (manual local agent; không human/live). Lịch sử trước F (không phải trạng thái hiện tại): Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4343 (H refs trong hàng AM-4343, PARTIAL): H003/H004 401; H011/H037/H038 400; H007/H034/H042/H078 403; H023/H033/H101/H102/H118 404; H010/H082/H123 409, H111 410, H138 413, H132 503; code/errorCode/status/path và thông báo. H048/H094 500 ngoài kỳ vọng. Retest bản cuối: [F061](../backend-final-observations.md) (PARTIAL): F004/F015 406 NOT_ACCEPTABLE thay H048/H094 500; F021/F030 404 USER_NOT_FOUND, F024/F027–029 400, F039/045 409, F047 404, F061 413 JSON. H003–004 401, H111 410, H132 503.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) G003 415 JSON; G076/077 phát hiện 401 /error thay 429 khi Accept binary; đã sửa GlobalExceptionHandler. J021 429 JSON TOO_MANY_ATTEMPTS + Retry-After=874 và path đúng, J022 owner khác 200; G055/G059 network 502 đúng. Bổ sung vòng 61: [backend-supplemental-observations.md](../backend-supplemental-observations.md) T005/T010: timeout 504 JSON đúng path/code/errorCode. P001–003: public config 200, fault bảng settings cô lập sau TTL trả 500 INTERNAL_ERROR đúng bảy field và message đã lọc, restore 200 cùng config. Phần còn lại hiện tại: Còn auth failure mapping 422 và privacy log ngoài bốn canary synthetic đã kiểm. Generic 500, 404, 502/504, 415/429 đã quan sát. Bổ sung A/B và regression cuối: [backend-supplemental-observations.md](../backend-supplemental-observations.md) — B008 500 INTERNAL_ERROR khi audit insert fail, B019 404 chuẩn, B020/B029 bốn canary log subject/body/path/SQL đều 0. Chỉ phạm vi log fixture mới; còn auth failure 422 và privacy các nhánh khác. Giữ OPEN; các nhánh manual/live/human còn thiếu không được suy từ regression. Vòng C: [BE](../backend-supplemental-observations.md): C026/C028/C029 trả 503 AUDIT_UNAVAILABLE trước mutation, C039 401 unknown unauth path, C040 8 nhóm aggregate privacy đều 0. Không thay mapping auth failure 422 thật. Vòng D: D001–003/D011: 401/403/200 role guard và 503 AUDIT_UNAVAILABLE fail-closed; D012 không có thư. D045 canary scope giới hạn; chưa auth failure 422 thật. Vòng E ([báo cáo bổ sung](../backend-supplemental-observations.md)): E001–004 events ADMIN: 401/403/200 no-store/400 invalid outcome; E042 aggregate 8 nhóm đều 0; canary subject/body/filename và lỗi SQL được dùng trong E, không có chứng cứ mới cho mọi nhóm credential/path/recipient hoặc Gmail live. Còn provider auth 422 thật. **Kết luận vòng F:** [F201–F226](../backend-supplemental-observations.md): F204/F205: provider từ chối xác thực mailbox App Password hiện có, GET folders qua Postman/Newman và POST multipart qua HTTPS trả 422 GMAIL_AUTH_FAILED, ErrorDto đủ field, no-store; F222: refresh OAuth loopback thực bị invalid_grant, 422 GMAIL_NEEDS_REAUTH, F223 refreshRejected=1, F224 NEEDS_REAUTH. F213–217: provider đã giao đúng 1 thư rồi lỗi ACK, HTTP 502 GMAIL_CONNECTION_ERROR với cảnh báo chưa xác định kết quả, không retry; đúng 1 MAIL_OPERATION_UNCERTAIN, không SEND_MAIL success giả, socket về 0. F226: sáu marker synthetic trong log/outbox/audit đều 0. Kết hợp evidence trước F về validation/RBAC/404/406/409/410/413/415/429/500/502/503/504, đủ nghĩa vụ manual local §4 AM-4343; không phải Google live, không chứng minh mọi secret hay human sign-off. **Vòng G2:** GlobalExceptionHandlerTest 3/3 kiểm HTTP409 GMAIL_ACCOUNT_CHANGED không raw exception; đây là regression mới do V9, không nghiệm thu lại manual F. Không đổi checkbox.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M01] Hạ tầng Backend & Bảo mật nền tảng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
