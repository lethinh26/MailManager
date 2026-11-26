# [FE] ApiClient HTTPS + JWT

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M03] Client nền tảng & Màn hình vào hệ thống |
| Owner | TV1 |
| Platform | UserWeb |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 29/10/2026 → 30/10/2026 |
| Ước lượng | 2h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[FE] ApiClient HTTPS + JWT` thuộc **[M03] Client nền tảng & Màn hình vào hệ thống** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `ApiClient` dùng Java HttpClient qua HTTPS, kiểm tra chứng chỉ máy chủ; chế độ bỏ qua xác minh chỉ được bật tường minh khi dev, có cảnh báo. HTTP loopback dành cho kiểm thử cục bộ.
- Gửi/nhận JSON DTO, đính kèm `Authorization: Bearer <token>` cho API cần xác thực; hỗ trợ GET/POST/PUT/PATCH/DELETE, multipart và stream.
- Parse lỗi JSON thành `ApiException` có HTTP status, code/message; xử lý 401 hết phiên và Retry-After, không ghi mật khẩu/JWT vào log.
- Xác minh `/api/auth/me` với token hợp lệ trước khi vào MainFrame; token thiếu/sai/hết hạn phải bị backend từ chối, Client không tạo phiên giả.
- Kiểm thử cert hợp lệ/sai, Authorization header và contract API; kiểm thử kết nối tới HTTPS server thực.
- Phần triển khai JwtProvider/JwtFilter/SecurityConfig bị sao chép nhầm vào task FE trước đây thuộc AM-4341; yêu cầu BE vẫn giữ tại task đó.

### 4.1. Đối chiếu triển khai hiện tại (26/11/2026)

- **Code và hành vi:** ApiClient: HTTPS xác thực CA/cert, chỉ cho HTTP loopback trong kiểm thử; JSON DTO, Bearer JWT và xử lý code/errorCode/Retry-After. requestLogin + verifyLogin gọi /me trước khi công bố token; 401 xử lý hết phiên.
- **Test liên quan:** TlsSecurityTest, ApiClientTest, AcceptanceRegressionTest#loginVerifiesAuthoritativeUserBeforePublishingToken, #publicPageSizeAndHttpsOnlyRules; ClientLiveServerE2ETest.
- **Nghiệm thu:** kiểm tra kết quả chạy mới trong test matrix; test tự động không thay thế thao tác manual hoặc kết nối Google thật.
- **Bổ sung vòng triển khai cuối:** ApiClient.newUnauthenticatedClient() giữ transport TLS nhưng không giữ JWT của phiên trước; worker cũ không nhận token mới.

- **Bổ sung vòng G2 (sau F):** ApiClient mỗi login dùng instance riêng theo code đã có; Gmail Manager bổ sung guard generation/disposed cho worker/callback. Không chứng minh manual expiry/network.

- **Bổ sung G3:** Manager/Compose 401 gọi session expiry; clear session và access token trước modal. Không thay native expiry/recovery.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 2 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 1 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AcceptanceRegressionTest: 12/12 PASS; ApiClientTest: 5/5 PASS; ClientLiveServerE2ETest: 16/16 PASS; TlsSecurityTest: 4/4 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, swing-acceptance-observed.md, swing-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Ownership của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ ở transport: không tự phân quyền Gmail, BE kiểm từng request. F giữ ở generic transport chỉ nếu yêu cầu maintenance/JwtFilter gốc đã được AM-4341 chứng minh; không miễn handling 403/503. A giữ: không tự ghi audit transport, đăng nhập và mutation do endpoint.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Feature của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ ở transport: không tự phân quyền Gmail, BE kiểm từng request. F giữ ở generic transport chỉ nếu yêu cầu maintenance/JwtFilter gốc đã được AM-4341 chứng minh; không miễn handling 403/503. A giữ: không tự ghi audit transport, đăng nhập và mutation do endpoint.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Phạm vi Audit của task đã rà lại ở bảng N/A swing-acceptance-cases.md: O giữ ở transport: không tự phân quyền Gmail, BE kiểm từng request. F giữ ở generic transport chỉ nếu yêu cầu maintenance/JwtFilter gốc đã được AM-4341 chứng minh; không miễn handling 403/503. A giữ: không tự ghi audit transport, đăng nhập và mutation do endpoint.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE) — OPEN. Quan sát thực tế: [swing-acceptance-observed.md](../swing-acceptance-observed.md) AM-4347 (M02, PARTIAL): Click "Kiểm tra kết nối máy chủ" → nhãn "[OK] Kết nối TLS máy chủ thành công (14 ms)", localhost:52324. Sau đó real login và mailbox HTTPS. Bổ sung: [swing-supplemental-observations.md](../swing-supplemental-observations.md) G001 TLS 1.3 trust/hostname thành công; S01 Swing connection check OK (73 ms); L04 HTTPS 8443 với cert fixture tin cậy. Bổ sung vòng 61: [swing-supplemental-observations.md](../swing-supplemental-observations.md) R2/R3: Client thật từ chối cert khác, mất server/timeout rõ lỗi, restore cert/HTTPS thành công. Public probe không có auth endpoint; S01/E2E kết nối JAR thật. Phần còn lại tại mốc trước F: Còn token hết hạn/session recovery và xử lý mất kết nối khi đã đăng nhập trực tiếp; không retry credential bị chặn. Public TLS lỗi và recovery đã kiểm. Tách riêng human sign-off, live, contract và kết quả tự động. Vòng E ([báo cáo bổ sung](../swing-supplemental-observations.md)): Client security/ApiClient regression và E2E đã chạy; E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. **Vòng F:** [Swing vòng F (automated)](../swing-supplemental-observations.md): ApiClient export CSV guard theo generation/role/disposal; recheck khi nhận response và trước publish, temp cleanup, không ghi đè. AdminModuleClientTest 5/5, Client E2E16/16; không thay manual expiry/network. Không thay trạng thái checkbox. **Vòng G2:** ApiClient mỗi login dùng instance riêng theo code đã có; Gmail Manager bổ sung guard generation/disposed cho worker/callback. Không chứng minh manual expiry/network. Không đổi checkbox. **Vòng G3:** Manager/Compose 401 gọi session expiry; clear session và access token trước modal. Không thay native expiry/recovery. Không đổi checkbox. **G4:** Giữ evidence trước; chưa có chứng cứ mới để đóng tiêu chí. [AM-4347-M ma trận nghĩa vụ](../acceptance-obligation-matrix.md#am-4347-m).

## 6. Phụ thuộc & tài liệu

- Module cha: **[M03] Client nền tảng & Màn hình vào hệ thống**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
