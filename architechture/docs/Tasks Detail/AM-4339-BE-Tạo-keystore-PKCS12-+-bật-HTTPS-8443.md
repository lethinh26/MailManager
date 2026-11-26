# [BE] Tạo keystore PKCS12 + bật HTTPS 8443

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M01] Hạ tầng Backend & Bảo mật nền tảng |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 26/10/2026 → 26/10/2026 |
| Ước lượng | 1h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Tạo keystore PKCS12 + bật HTTPS 8443` thuộc **[M01] Hạ tầng Backend & Bảo mật nền tảng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `keytool -genkeypair -alias mailapp -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650`
- Đặt `keystore.p12` trong `src/main/resources/`
- application.yml: `server.port=8443`, `server.ssl.key-store=classpath:keystore.p12`, `key-store-password=${MAILAPP_KEYSTORE_PASS}`, `key-store-type=PKCS12`, `key-alias=mailapp`
- Self-signed cert → Client Swing phải trust-all hoặc import cert — M03 phải config `HttpClient` bỏ qua verify khi dev (production thì CA thật)
- Test: `curl -k https://localhost:8443/actuator/health` → `{"status":"UP"}`

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** cấu hình SSL bật mặc định, 8443, PKCS12 alias mailapp, MAILAPP_KEYSTORE_PATH mặc định classpath:keystore.p12, MAILAPP_KEYSTORE_PASS hoặc KEYSTORE_PASSWORD; script scripts/gen-keystore.ps1 sinh keystore. Client ApiClient mặc định kiểm chứng chứng chỉ; trust-all chỉ khi bật -Dmailclient.insecure=true dành riêng dev.
- **Khác biệt:** external keystore path cấu hình được, không cần commit private key/password. Production dùng chứng chỉ được tin cậy; không mặc định trust-all.
- **Mã nguồn:** `src/main/resources/application.properties`, `scripts/gen-keystore.ps1`, `security/SecurityStartupValidator.java`; `Mail Client/src/main/java/com/mailapp/client/net/ApiClient.java`.
- **Test source:** `security/NonDevProfileTest.java` và `security/EndpointSecurityAuditTest.java` chỉ kiểm tra profile/quyền, không chứng minh TLS handshake vì test tắt SSL. Cần chạy HTTPS health, kiểm tra alias/cert và trust Client trên máy thật; không sinh keystore hoặc truy cập secret trong lần rà soát.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** EndpointSecurityAuditTest: 3/3 PASS; NonDevProfileTest: 3/3 PASS; ClientLiveServerE2ETest: 16/16 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). TLS/keystore infrastructure; quyền, flag, audit tại endpoint/caller.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). TLS/keystore infrastructure; quyền, flag, audit tại endpoint/caller.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). TLS/keystore infrastructure; quyền, flag, audit tại endpoint/caller.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (phạm vi local đã kiểm chứng). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4339 (H refs trong hàng AM-4339, PARTIAL): Các request HTTPS tới fixture có phản hồi thực; H029/H030 Postman HTTP và binary qua HTTPS. Retest bản cuối: [F002](../backend-final-observations.md) (OBSERVED): Kết nối HTTPS dùng default trust tới cùng fixture thất bại trước HTTP: certificateAccepted=false, DEPTH_ZERO_SELF_SIGNED_CERT; F001 dùng cert fixture thành công 200 UP.. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) L04: cổng 8443 trống, PID đúng fixture, PKCS12/SAN cert mới và curl --cacert xác minh trust/hostname thành công. Phần còn lại hiện tại: Giữ PASS local; không suy chứng chỉ deployment thật đã kiểm. Tách riêng human sign-off, live, contract và kết quả tự động.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M01] Hạ tầng Backend & Bảo mật nền tảng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
