# [BE] AesCipher AES-256-GCM + load khoá từ env MAILAPP_AES_KEY

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M01] Hạ tầng Backend & Bảo mật nền tảng |
| Owner | TV1 |
| Platform | API |
| Sprint | SPRINT 1 |
| Tuần | Week 1 |
| Kế hoạch | 27/10/2026 → 27/10/2026 |
| Ước lượng | 1.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] AesCipher AES-256-GCM + load khoá từ env MAILAPP_AES_KEY` thuộc **[M01] Hạ tầng Backend & Bảo mật nền tảng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `AesCipher` class `util/`: AES-256-GCM, key 32 byte đọc từ env `MAILAPP_AES_KEY` (base64)
- `encrypt(String plaintext) -> String`: random 12-byte IV, gcm tag 128 bit, output base64(IV || ciphertext)
- `decrypt(String b64) -> String`
- KHÔNG dùng ECB, KHÔNG reuse IV. Log lỗi mà KHÔNG log plaintext
- Test: `encrypt("hello") -> x`, `decrypt(x) == "hello"`, gọi 2 lần `encrypt` cho kết quả khác nhau (IV khác)
- Dùng cho: App Password + OAuth2 refresh_token khi lưu DB

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** security/AesCipher, AES/GCM/NoPadding, key Base64 đúng 32 byte, IV ngẫu nhiên 12 byte, tag 128 bit, Base64(IV+ciphertext+tag), decrypt kiểm chiều dài tối thiểu IV+tag. Lưu App Password và OAuth refresh token mã hóa; không dùng ECB.
- **Khác biệt:** class ở security thay util; env MAILAPP_AES_KEY và alias AES_KEY; non-dev fail-fast khi thiếu/sai, dev có thể dùng key ngẫu nhiên tạm thời (không đọc lại ciphertext sau restart nếu không cấu hình key ổn định).
- **Mã nguồn:** `security/AesCipher.java`, `controller/GmailAccountController.java#linkGmailWithAppPassword`, `oauth2/OAuth2ServiceImpl.java`, `src/main/resources/application.properties`.
- **Test source:** `security/AesCipherTest.java` (roundtrip/IV khác nhau, key thiếu/sai, dữ liệu hỏng, dev), `oauth2/OAuth2MockWebServerTest.java`, `controller/GmailAccountControllerTest.java`. Kiểm tra môi trường key cấu hình thật không in key/plaintext.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 3 PASS (code/test thực tế và local manual được duyệt nếu có), 3 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** GmailAccountControllerTest: 15/15 PASS; OAuth2MockWebServerTest: 21/21 PASS; AesCipherTest: 7/7 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đối chiếu §4.1 và test liên quan; phạm vi code/tự động, không thay manual.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). AES-GCM utility không trực tiếp thực thi owner/feature/audit nghiệp vụ.
- [ ] Feature flag check nếu spec yêu cầu — N/A (không áp dụng; không tính PASS/OPEN). AES-GCM utility không trực tiếp thực thi owner/feature/audit nghiệp vụ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). AES-GCM utility không trực tiếp thực thi owner/feature/audit nghiệp vụ.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (phạm vi local đã kiểm chứng). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4342 (H refs trong hàng AM-4342, NOT OBSERVED): H053/H083/H105 DTO không có credential, chứng minh bề mặt HTTP giới hạn; không chứng minh AES-GCM storage. Bổ sung: [backend-supplemental-observations.md](../backend-supplemental-observations.md) L03: trực tiếp chạy AesCipher trong probe độc lập native MySQL, env key synthetic 32 byte, round-trip và IV khác, overhead IV12/tag16, tag sửa và sai key bị từ chối; ciphertext App/OAuth synthetic giữ đúng qua migration, giải mã khớp, chỉ xuất boolean/count. G040 DTO link không credential. Phần còn lại hiện tại: Đủ phép kiểm local của utility không có endpoint Postman riêng; evidence là operational probe, không tuyên bố human sign-off/credential Google thật hay xem raw log. Privacy live vẫn riêng. Tách riêng human sign-off, live, contract và kết quả tự động.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M01] Hạ tầng Backend & Bảo mật nền tảng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
