# [BE] Service đọc cấu hình hệ thống

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

Hoàn thành `[BE] Service đọc cấu hình hệ thống` thuộc **[M01] Hạ tầng Backend & Bảo mật nền tảng** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 4. Việc cần làm chi tiết

- `SystemSettingService` interface + impl đọc `system_settings`
- API: `getBool(String key)`, `getInt(String key)`, `getString(String key)`, `getBoolOrDefault(key, def)`
- Cache in-memory `ConcurrentHashMap` + TTL 10s (hoặc invalidate khi EP-S2 PUT)
- Inject bởi mọi module kiểm tra feature flag
- Keys: ALLOW_REGISTRATION, ALLOW_OAUTH2_LINK, ALLOW_APP_PASSWORD_LINK, ALLOW_SEND_MAIL, MAINTENANCE_MODE, MAX_GMAIL_PER_USER, MAX_ATTACHMENT_SIZE_MB, MAIL_PAGE_SIZE, LOGIN_MAX_FAILED_ATTEMPTS

### 4.1. Đối chiếu triển khai hiện tại (bằng chứng mã nguồn)

> Quy ước đường dẫn: lớp Java backend tương đối với `Mail Server/src/main/java/com/mailapp/`, test tương đối với `Mail Server/src/test/java/com/mailapp/`; đường dẫn `src/...` và file cấu hình tính từ `Mail Server/`.

- **Thực hiện:** SystemSettingService typed getters cho 9 khóa, đọc SystemSettingRepository; cache Caffeine TTL 3 giây, invalidate sau commit khi sửa (tránh rollback lộ giá trị chưa commit); V4 seed thiếu. Các caller kiểm ALLOW_REGISTRATION/MAINTENANCE_MODE (auth/filter), ALLOW_OAUTH2_LINK/ALLOW_APP_PASSWORD_LINK/quota (OAuth/Gmail), ALLOW_SEND_MAIL/attachment limit/mail page size (mail), LOGIN_MAX_FAILED_ATTEMPTS động (LoginRateLimiter), public config.
- **Khác biệt:** giữ typed getters và bổ sung getBool/getInt/getString/getBoolOrDefault tương thích §4; cache kết quả DB (kể cả thiếu khóa), không cache fallback theo caller. Caffeine bounded TTL 3s, ngắn hơn 10s và invalidate khi admin PUT. MAIL_PAGE_SIZE mặc định 20 khi chưa cấu hình; quota 0 đã lưu vẫn không giới hạn; cập nhật quota mới bắt buộc dương theo AM-4335.
- **Mã nguồn:** `service/SystemSettingService.java`, `service/impl/SystemSettingServiceImpl.java`, `service/impl/AdminServiceImpl.java#updateSettings`, `security/LoginRateLimiter.java`, `controller/PublicHealthController.java`, `mail/service/MailServiceImpl.java`, `src/main/resources/db/migration/V4__seed_missing_settings.sql`.
- **Test source:** `service/SystemSettingServiceTest.java` (defaults/cache/update, genericAccessorsUseKnownDefaultsWithoutCachingCallerFallback), `controller/AdminIntegrationTest.java#testSettingsValidationCacheEvictionAndAudit` và simultaneousSettingUpdatesCannotDisableBothLinkMethods; `controller/AuthRegisterAndChangePasswordTest.java#rejectedLoginAuditPersistsAndRateLimitTracksSettings`. Còn kiểm tra config hiệu lực qua Postman, MySQL và Swing.

**Trạng thái kiểm chứng:** Kết quả lần chạy cuối tại §5 và test matrix; manual/Gmail thật còn theo crosswalk riêng.

## 5. Acceptance criteria

**Kết quả 26/11/2026:** 4 PASS (code/test thực tế và local manual được duyệt nếu có), 2 N/A, 0 OPEN. Chỉ [x] là PASS; N/A không tính đạt.

**Suite liên quan đã chạy:** AdminIntegrationTest: 18/18 PASS; AuthRegisterAndChangePasswordTest: 12/12 PASS; SystemSettingServiceTest: 4/4 PASS.

**Nghiệm thu bổ sung (tự động):** Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. UI/HTTP automated tách khỏi quan sát từng thao tác; xem test matrix.

Xem architechture/docs/acceptance-review.md, backend-manual-observations.md, backend-acceptance-cases.md và Mail Server/docs/testing/test-matrix.md.

- [x] Contract đúng: method, path, request/response schema, error code khớp spec — PASS (code/test thực tế). Đủ generic accessor và typed getter; Caffeine TTL 3s, invalidate sau commit; fallback riêng caller không làm bẩn cache.
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail — N/A (không áp dụng; không tính PASS/OPEN). Settings service đọc/cache flag, không Gmail/audit mutation riêng; PUT tại AM-4335 vẫn ghi audit.
- [x] Feature flag check nếu spec yêu cầu — PASS (code/test thực tế). 9 settings, cache invalidation. Đối chiếu §4.1 và suite/HTTP liên quan; BE vẫn là chốt bảo vệ.
- [ ] Audit log ghi đúng action — N/A (không áp dụng; không tính PASS/OPEN). Settings service đọc/cache flag, không Gmail/audit mutation riêng; PUT tại AM-4335 vẫn ghi audit.
- [x] Không lộ credential / token / app password / nội dung mail trong response hoặc log — PASS (code/test thực tế). Đối chiếu DTO/log và test an toàn liên quan; dữ liệu chỉ cho đúng người gọi. Không suy rộng sang Gmail thật.
- [x] Manual test pass (Postman cho BE, chạy Swing cho FE) — PASS (agent thao tác fixture local; chỉ phạm vi quan sát được duyệt). Quan sát thực tế: [backend-manual-observations.md](../backend-manual-observations.md) AM-4345 (H refs trong hàng AM-4345, OBSERVED): H037–038 validate settings; H039–044 public config cập nhật/restore và gate thực đổi theo setting; H115–116, H128–135 đổi/restore flags và maintenance tức thời qua public config và JWT gate; H137–139 cap thực tế. Retest bản cuối: [F060](../backend-final-observations.md) (OBSERVED): F060 settings cap1 200, F061 download thực bị 413, F062 restore25 200, F063 đúng binary 200. H039–044/H115–116/H128–139 flag/cache/maintenance đổi hiệu lực tức thời.. Phần còn lại: Scope behavior local settings/cache invalidation đủ, typed getter/TTL internals/race riêng; human sign-off. Không thay bằng assertion tự động; human sign-off và Gmail/Google thật (nếu áp dụng) vẫn tách riêng.

## 6. Phụ thuộc & tài liệu

- Module cha: **[M01] Hạ tầng Backend & Bảo mật nền tảng**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
