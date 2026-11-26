# Ma trận kiểm thử thực tế — 26/11/2026

Số liệu hợp nhất: Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới. Delta G6: +2 FE tests, +0 BE; không cộng suite rerun thành test mới.

## Lệnh đã chạy

- Mail Server: gradlew.bat test bootJar --max-workers=1 --no-daemon --console=plain.
- Mail Server/scripts/test-client-e2e.ps1 -Java21Home <JDK21> -SkipBuild -DatabaseEngine MySQL -VerifyDatabaseOnly (lượt kiểm schema độc lập).
- Mail Server/scripts/test-client-e2e.ps1 -Java21Home <JDK21> -SkipBuild -DatabaseEngine MySQL -RunPostman (lượt nghiệm thu đầy đủ, Gradle Client --rerun-tasks --max-workers=1).
- Hai phiên nghiệm thu từng request / Swing thật độc lập với collection tự động: `-SkipBuild -DatabaseEngine MySQL -ManualSession` (H), `-SkipBuild -DatabaseEngine MySQL -ManualSession -ManualPostman -ManualSeconds 600` (F trên JAR cuối). Cả hai đã dừng, dọn fixture và dữ liệu riêng.
- Không bật MAILAPP_REAL_GMAIL/MAILAPP_REAL_GMAIL_XOAUTH2, không truy cập Gmail thật hoặc DB hiện có.

## Tổng hợp

| Module | Suites | Tests | PASS | SKIP | FAIL | ERROR |
|---|---:|---:|---:|---:|---:|---:|
| Mail Server | 45 | 285 | 280 | 5 | 0 | 0 |
| Mail Client | 20 | 130 | 130 | 0 | 0 | 0 |
| Tổng | 65 | 415 | 410 | 5 | 0 | 0 |

Newman G4 kế thừa (6.2.1): 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. Đây là assertion HTTP độc lập, không cộng vào JUnit. Chỉ dùng sanitized postman-summary.json và oauth-summary.json.

## Suite và task liên quan

Task tham chiếu suite không đồng nghĩa mọi tiêu chí của task được test. Các mapping trước giữ nguyên và bổ sung test mới.

| Module | Suite | Tests | PASS | SKIP | FAIL / ERROR | Task tham chiếu |
|---|---|---:|---:|---:|---:|---|
| Mail Server | DotEnvAliasPrecedenceTest | 1 | 1 | 0 | 0 / 0 | AM-4341 |
| Mail Server | AdminAccountInitializerTest | 3 | 3 | 0 | 0 / 0 | AM-4340 |
| Mail Server | AdminIntegrationTest | 18 | 18 | 0 | 0 / 0 | AM-4310, AM-4311, AM-4312, AM-4317, AM-4319, AM-4325, AM-4326, AM-4327, AM-4328, AM-4329, AM-4330, AM-4331, AM-4332, AM-4333, AM-4334, AM-4335, AM-4336, AM-4337, AM-4341, AM-4343, AM-4344, AM-4345, AM-4370, AM-4371, AM-4372, AM-4373, AM-4374, AM-4375, AM-4376 |
| Mail Server | AuthControllerIntegrationTest | 14 | 14 | 0 | 0 / 0 | AM-4310, AM-4343, AM-4344 |
| Mail Server | AuthRegisterAndChangePasswordTest | 12 | 12 | 0 | 0 / 0 | AM-4309, AM-4310, AM-4311, AM-4312, AM-4331, AM-4333, AM-4335, AM-4336, AM-4341, AM-4344, AM-4345, AM-4350, AM-4351 |
| Mail Server | GmailAccountControllerTest | 15 | 15 | 0 | 0 / 0 | AM-4313, AM-4314, AM-4315, AM-4318, AM-4342, AM-4353, AM-4354, AM-4356, AM-4359, AM-4396 |
| Mail Server | MailControllerCompatibilityTest | 12 | 12 | 0 | 0 / 0 | AM-4321, AM-4322, AM-4323, AM-4324, AM-4391, AM-4394, AM-4397, AM-4398, AM-4343 |
| Mail Server | OAuth2ControllerTest | 4 | 4 | 0 | 0 / 0 | AM-4316, AM-4317, AM-4343, AM-4355 |
| Mail Server | GlobalExceptionHandlerTest | 3 | 3 | 0 | 0 / 0 | AM-4309, AM-4325, AM-4343 |
| Mail Server | AccessTokenProviderTest | 2 | 2 | 0 | 0 / 0 | AM-4360 |
| Mail Server | DefaultMailConnectionFactoryGreenMailTest | 10 | 10 | 0 | 0 / 0 | AM-4358, AM-4359, AM-4396, AM-4361, AM-4397 |
| Mail Server | GmailFolderResolverTest | 6 | 6 | 0 | 0 / 0 | AM-4320 |
| Mail Server | MailConnectionFactoryTest | 5 | 5 | 0 | 0 / 0 | AM-4358, AM-4359, AM-4360 |
| Mail Server | MailConnectionFaultAndRetryTest | 4 | 4 | 0 | 0 / 0 | AM-4358, AM-4359 |
| Mail Server | MailFallbackConnectionTest | 3 | 3 | 0 | 0 / 0 | AM-4358, AM-4360 |
| Mail Server | RealGmailIntegrationTest | 2 | 0 | 2 | 0 / 0 | AM-4361, AM-4391, AM-4396 |
| Mail Server | RealGmailXOAuth2IntegrationTest | 2 | 0 | 2 | 0 / 0 | AM-4360, AM-4361, AM-4391, AM-4396 |
| Mail Server | SecretMaskingTest | 6 | 6 | 0 | 0 / 0 | AM-4323, AM-4344, AM-4358, AM-4361 |
| Mail Server | XOAuth2IntegrationTest | 3 | 3 | 0 | 0 / 0 | AM-4360, AM-4361 |
| Mail Server | FolderCountReaderTest | 4 | 4 | 0 | 0 / 0 | AM-4320 |
| Mail Server | FolderCacheTest | 2 | 2 | 0 | 0 / 0 | AM-4320 |
| Mail Server | MailMessageBuilderTest | 5 | 5 | 0 | 0 / 0 | AM-4323, AM-4367, AM-4368, AM-4369, AM-4394, AM-4397, AM-4398 |
| Mail Server | MailPerformanceAndResilienceTest | 5 | 5 | 0 | 0 / 0 | AM-4321, AM-4324 |
| Mail Server | MailSecurityAndSanitizerTest | 3 | 3 | 0 | 0 / 0 | AM-4322, AM-4323, AM-4365 |
| Mail Server | MailServiceFailureRegressionTest | 22 | 22 | 0 | 0 / 0 | AM-4320, AM-4321, AM-4322, AM-4323, AM-4324, AM-4344, AM-4391, AM-4392, AM-4393, AM-4396 |
| Mail Server | MailServiceGreenMailIntegrationTest | 10 | 10 | 0 | 0 / 0 | AM-4320, AM-4321, AM-4322, AM-4323, AM-4324, AM-4364, AM-4365, AM-4369, AM-4370, AM-4391, AM-4392, AM-4393, AM-4394, AM-4395, AM-4397, AM-4398 |
| Mail Server | FlywayUserIdentityMigrationTest | 1 | 1 | 0 | 0 / 0 | AM-4309, AM-4325, AM-4340 |
| Mail Server | FlywayV3MigrationTest | 1 | 1 | 0 | 0 / 0 | AM-4340, AM-4396 |
| Mail Server | OAuth2MockWebServerTest | 21 | 21 | 0 | 0 / 0 | AM-4316, AM-4317, AM-4318, AM-4319, AM-4342, AM-4355, AM-4357 |
| Mail Server | OAuth2ServiceTest | 6 | 6 | 0 | 0 / 0 | AM-4316 |
| Mail Server | OAuth2StateStoreTest | 3 | 3 | 0 | 0 / 0 | AM-4316 |
| Mail Server | AesCipherTest | 7 | 7 | 0 | 0 / 0 | AM-4342 |
| Mail Server | EndpointSecurityAuditTest | 3 | 3 | 0 | 0 / 0 | AM-4311, AM-4338, AM-4339, AM-4340, AM-4341 |
| Mail Server | JwtAuthenticationFilterTest | 9 | 9 | 0 | 0 / 0 | AM-4311, AM-4341, AM-4343 |
| Mail Server | JwtProviderTest | 6 | 6 | 0 | 0 / 0 | AM-4341 |
| Mail Server | NonDevProfileTest | 3 | 3 | 0 | 0 / 0 | AM-4338, AM-4339 |
| Mail Server | SecurityStartupValidatorTest | 3 | 3 | 0 | 0 / 0 |  |
| Mail Server | AuditLogServiceTest | 5 | 5 | 0 | 0 / 0 | AM-4344, AM-4392 |
| Mail Server | MailAuditRecoveryIntegrationTest | 13 | 13 | 0 | 0 / 0 | AM-4323, AM-4344, AM-4391, AM-4392, AM-4394 |
| Mail Server | AuthServiceTest | 9 | 9 | 0 | 0 / 0 | AM-4310 |
| Mail Server | GmailMultiAuthIntegrationTest | 9 | 9 | 0 | 0 / 0 | AM-4313, AM-4314, AM-4315, AM-4317, AM-4318, AM-4353, AM-4354, AM-4355, AM-4356, AM-4357, AM-4359, AM-4396 |
| Mail Server | SystemSettingServiceTest | 4 | 4 | 0 | 0 / 0 | AM-4345 |
| Mail Server | EmailNormalizerTest | 3 | 3 | 0 | 0 / 0 | AM-4396 |
| Mail Server | RealGmailMimeFixturesTest | 1 | 0 | 1 | 0 / 0 | AM-4322, AM-4324, AM-4361, AM-4396, AM-4397 |
| Mail Server | MailboxRevisionChainIntegrationTest | 2 | 2 | 0 | 0 / 0 | AM-4317, AM-4320, AM-4323, AM-4358, AM-4359, AM-4360, AM-4361, AM-4396 |
| Mail Client | AcceptanceRegressionTest | 12 | 12 | 0 | 0 / 0 | AM-4347, AM-4348, AM-4349, AM-4351, AM-4355, AM-4364, AM-4366, AM-4367, AM-4368, AM-4369, AM-4399, AM-4400 |
| Mail Client | AdminModuleClientTest | 5 | 5 | 0 | 0 / 0 | AM-4371, AM-4372, AM-4373, AM-4374, AM-4375, AM-4376 |
| Mail Client | ApiClientTest | 5 | 5 | 0 | 0 / 0 | AM-4347, AM-4350 |
| Mail Client | AttachmentCleanupServiceTest | 10 | 10 | 0 | 0 / 0 | AM-4365, AM-4391, AM-4393, AM-4399 |
| Mail Client | AvatarThemeAndPrivacyTest | 4 | 4 | 0 | 0 / 0 |  |
| Mail Client | ClientLiveServerE2ETest | 16 | 16 | 0 | 0 / 0 | AM-4309, AM-4310, AM-4311, AM-4312, AM-4313, AM-4314, AM-4315, AM-4320, AM-4321, AM-4322, AM-4323, AM-4324, AM-4325, AM-4326, AM-4328, AM-4329, AM-4333, AM-4335, AM-4336, AM-4337, AM-4338, AM-4339, AM-4340, AM-4341, AM-4344, AM-4346, AM-4347, AM-4349, AM-4350, AM-4351, AM-4352, AM-4353, AM-4354, AM-4355, AM-4356, AM-4357, AM-4362, AM-4363, AM-4364, AM-4365, AM-4367, AM-4369, AM-4370, AM-4371, AM-4372, AM-4373, AM-4374, AM-4375, AM-4376, AM-4391, AM-4392, AM-4393, AM-4394, AM-4395, AM-4399 |
| Mail Client | ClientSecurityAndResilienceTest | 5 | 5 | 0 | 0 / 0 | AM-4347, AM-4349, AM-4400 |
| Mail Client | EmailNormalizerClientTest | 1 | 1 | 0 | 0 / 0 | AM-4354 |
| Mail Client | GmailMultiAuthClientTest | 5 | 5 | 0 | 0 / 0 | AM-4353, AM-4354, AM-4356 |
| Mail Client | MailApiClientNewEndpointsTest | 6 | 6 | 0 | 0 / 0 | AM-4353, AM-4356, AM-4362, AM-4363, AM-4364, AM-4365, AM-4367, AM-4368, AM-4369, AM-4393, AM-4395, AM-4399 |
| Mail Client | MailboxRegressionTest | 13 | 13 | 0 | 0 / 0 | AM-4355, AM-4357, AM-4352, AM-4362, AM-4363, AM-4364, AM-4365, AM-4366, AM-4395, AM-4393 |
| Mail Client | MySqlConcurrentAcceptanceTest | 2 | 2 | 0 | 0 / 0 | AM-4309, AM-4325, AM-4340, AM-4343 |
| Mail Client | OAuthFlowAndSessionTest | 5 | 5 | 0 | 0 / 0 | AM-4348, AM-4355, AM-4366 |
| Mail Client | PasswordValidatorTest | 7 | 7 | 0 | 0 / 0 | AM-4350 |
| Mail Client | SessionStoreTest | 3 | 3 | 0 | 0 / 0 | AM-4348 |
| Mail Client | SwingFeatureAcceptanceTest | 16 | 16 | 0 | 0 / 0 | AM-4354, AM-4350, AM-4351, AM-4352, AM-4353, AM-4357, AM-4364, AM-4366, AM-4367, AM-4370, AM-4371, AM-4372, AM-4374, AM-4395, AM-4368, AM-4369, AM-4373, AM-4376, AM-4399, AM-4400, AM-4375 |
| Mail Client | TlsSecurityTest | 4 | 4 | 0 | 0 / 0 | AM-4347 |
| Mail Client | UiAccessibilityAndThemeTest | 2 | 2 | 0 | 0 / 0 | AM-4346, AM-4349, AM-4351 |
| Mail Client | UiLayoutRegressionTest | 6 | 6 | 0 | 0 / 0 | AM-4346, AM-4352, AM-4362, AM-4363, AM-4365, AM-4368 |
| Mail Client | AttachmentSizeAccuracyTest | 3 | 3 | 0 | 0 / 0 | AM-4322, AM-4324, AM-4363, AM-4365, AM-4397, AM-4399 |

## Bằng chứng chính và giới hạn

- Backend 250/255 (full + chạy lại 2 suite sau sửa assertion privacy), Client 108/108, tổng 358 PASS / 363 JUnit, 5 SKIP, 0 FAIL, 0 ERROR. XML suite headers cuối, không lấy kết quả lịch sử, stdout/stderr hoặc secret.
- MySQL native 9.5.0, datadir cô lập, utf8mb4/utf8mb4_unicode_ci, Flyway V1–V6 thành công, 4 bảng nghiệp vụ, 9 settings, 1 admin; 1 admin hash đúng shape BCrypt, 0 linked App Password có cột encrypted không rỗng (chỉ aggregate; không chứng minh mọi giá trị mã hóa). Nguồn: mysql-metadata.tsv. MySQL native 9.5.0, datadir cô lập, utf8mb4/utf8mb4_unicode_ci, Flyway V1–V6 thành công, 4 bảng nghiệp vụ, 9 settings, 1 admin; 1 admin hash đúng shape BCrypt, 1 linked App Password có cột encrypted không rỗng (chỉ aggregate; không chứng minh mọi giá trị mã hóa). Nguồn: manual-mysql-metadata.tsv. Không suy rộng sang schema/volume triển khai thật; Flyway có thể cảnh báo phiên bản MySQL ngoài phạm vi hỗ trợ. Race MySQL/setting: xem MySqlConcurrentAcceptanceTest và ClientLiveServerE2ETest trong bảng; không suy từ aggregate thành mọi race đã quan sát thủ công.
- Newman 6.2.1: 140 request, 653 assertion, 0 lỗi/skip; OAuth loopback 4 exchange + 4 UserInfo + 4 Gmail profile + 2 revoke. HTTP qua disposable JAR/HTTPS, provider Google giả lập loopback; không Google thật.
- ClientLiveServerE2ETest: ClientLiveServerE2ETest: 16/16 PASS (E01–12 HTTPS/TLS/auth/config/GreenMail; E13–15 Swing headful tự động, E16 race settings); không thay manual.
- GreenMail/MIME/attachment/flags/Trash/UIDVALIDITY, targeted delete, retry và UI regressions: theo suite refs. AttachmentSizeAccuracyTest kiểm DTO alias và cách biểu diễn estimate/unknown; không tự chứng nhận UI đã retest trực tiếp.
- Live Google/Gmail chưa kiểm chứng; RealGmailIntegrationTest 2/2 SKIP; RealGmailXOAuth2IntegrationTest 2/2 SKIP; RealGmailMimeFixturesTest 1/1 SKIP. 7 MIME fixture opt-in không phải 7 thư thực đã gửi.
- Quan sát từng thao tác: `architechture/docs/backend-manual-observations.md` (150 H), `backend-final-observations.md` (95 F HTTP/TLS trên JAR cuối), `swing-acceptance-observed.md` (21 kịch bản trọng tâm có quan sát ít nhất một phần, 10 chưa chạy/bị chặn; 14 ảnh render fixture). 245 quan sát H/F là kết quả từng thao tác, không phải 245 assertion PASS. Ở mốc trước vòng G/J/L, 22 generic manual đã chuyển PASS local agent: AM-4313, AM-4315, AM-4318, AM-4321, AM-4322, AM-4324, AM-4326, AM-4327, AM-4329, AM-4333, AM-4334, AM-4335, AM-4336, AM-4337, AM-4338, AM-4339, AM-4341, AM-4345, AM-4348, AM-4391, AM-4392, AM-4394. Mốc lịch sử còn 56 manual OPEN, 6 contract OPEN và 3 privacy live OPEN; human sign-off/live riêng, không tuyên bố 100%. Regression UI cuối đã đạt, chưa có thao tác Swing hậu đăng nhập trực tiếp mới do cổng an toàn công cụ chặn nhập credential; quan sát public mới xem S01–S04 bên dưới.
- Không dùng cổng/chứng chỉ/volume production hay credential thật để nghiệm thu fixture.

## Bổ sung vòng 65 OPEN

- BE regression trọng điểm đã chạy, sau đó full `test bootJar` (mã thay đổi thật); Client/Newman chạy tuần tự lại trên JAR cuối. Backend 243/248, Client 103/103, tổng 346 PASS / 351 JUnit, 5 SKIP, 0 FAIL, 0 ERROR.
- G/J/L: [quan sát backend bổ sung](../../../architechture/docs/backend-supplemental-observations.md); S01–S04: [quan sát Swing public](../../../architechture/docs/swing-supplemental-observations.md). G 80 HTTP/TLS + 11 control + 1 aggregate, J 22 HTTP, không cộng vào JUnit/653 assertions.
- `MailServiceGreenMailIntegrationTest#outgoingAttachmentsPreserveExactBytesAcrossSmtpAndImap`: PDF/PNG/TXT/zero-byte; `MailControllerCompatibilityTest#binaryDownloadRateLimitReturnsJson429AndRetryAfter`: binary Accept/429 JSON. J009–011 nguồn/tải khớp hash, J021 429 đúng path/Retry-After, J022 owner khác 200.
- Legacy probe `-VerifyDatabaseOnly -VerifyLegacyUpgrade -VerifyConfiguredPort` exit 0; V2→V6, duplicate V5 fail trước DDL, row snapshot qua startup giữ nguyên, AES synthetic, trusted SAN/8443. Native MySQL 9.5.0; không dùng DB thật.
- Chốt bổ sung local AM-4325/4340/4342/4398; tổng 291 PASS, 116 N/A, 61 OPEN. Không đổi sáu contract/ba privacy live; Swing hậu đăng nhập chưa reverify trực tiếp do safety gate.

## Bổ sung vòng 61 OPEN

- R2/R3: startup, DPI 125%, min-size, cert sai/offline/timeout/recovery, sửa LoginFrame scroll và trạng thái ping. Hai regression mới pass; Client full `test-client-e2e.ps1 -SkipBuild -DatabaseEngine MySQL` exit 0, 105/105; E01–16 16/16.
- T001–T013: native MySQL/HTTPS/GreenMail socket stall, 504 và đóng 2/2 socket mỗi protocol, không mất mailbox hay gửi marker. P001–P003: public config 200→500 INTERNAL_ERROR→200 cùng values, fixture mới và restore table. T/P không dùng credential bị chặn, không Gmail live.
- BE source/JAR không đổi SHA256 B4C1F1624B01364A585A047C4083142CF771A2C5D4672A3DC44D393CC068F682; giữ BE 243/248 và Newman 140/653 từ lượt trước, không gọi là đã rerun vòng này. Tổng 348/353, 5 SKIP. 291 PASS/116 N/A/61 OPEN không đổi; đã thu hẹp nhánh còn thiếu nhưng chưa có đủ evidence đóng thêm criterion.

## Vòng audit / lease / race cuối

Backend 250/255 (full + chạy lại 2 suite sau sửa assertion privacy), Client 108/108, tổng 358 PASS / 363 JUnit, 5 SKIP, 0 FAIL, 0 ERROR. JAR SHA256 ABCC46A6683C88930AAF1F048C788D057BF6FFBC470EEF7DB81436E09BCCD0D9. A/B 16 + 37 quan sát (25 HTTP, 28 control); không cộng vào assertion. Backend full thất bại một assertion privacy cũ, chạy lại 2 suite PASS sau sửa test-only, giữ kết quả các suite không đổi. FE full + Newman trên MySQL riêng. Live vẫn 5 SKIP.

## Vòng C — durable mail audit và FE regression

Lũy kế Backend 258/263, Client 110/110, tổng 368 PASS / 373 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng C chạy lại BE 38/38 và FE 31/31 trên mã cuối; các suite khác và Newman 140/653 kế thừa, không phải full run mới. JAR SHA256 A500EADDDB58D1A182FBD2A3CCA9A16D423B2C155C7BD606609E82202CB853B1. BE: MailAuditRecoveryIntegrationTest 4/4, AuditLogServiceTest 5/5, MailServiceFailureRegressionTest 19/19, MailServiceGreenMailIntegrationTest 10/10. FE: SwingFeatureAcceptanceTest 9/9, SessionStoreTest 3/3, MailboxRegressionTest 8/8, ClientSecurityAndResilienceTest 5/5, MailApiClientNewEndpointsTest 6/6. Hai lệnh Gradle targeted tuần tự --max-workers=1 --no-daemon, BE bootJar. Evidence JSON: Mail Server/build/acceptance/audit-recovery-suite-summary.json, Mail Client/build/acceptance/audit-recovery-client-suite-summary.json. C001–041 18 HTTP +23 control, không cộng JUnit/Newman. Live 5 SKIP kế thừa.

**Regression schema V7-L:** V7-L: Native MySQL database-only, legacy V1/V2 seed synthetic → V6 làm tiền đề → V7. Snapshot 4 bảng trước/sau V7 khớp; outbox mới rỗng, 7 migration success; khởi động JAR vẫn giữ nguyên dữ liệu, 9 settings/1 admin/5 bảng (4 cũ + outbox). Exit 0, datadir fixture đã xóa. Lý do regression riêng: V7 vừa thêm và legacy harness phải nhận version 7; không phải nghiệm thu lại các task migration/AES/TLS cũ. Client/Newman không chạy. Evidence Mail Server/build/acceptance/legacy-upgrade-summary-V7.txt và mysql-metadata-V7-legacy.tsv.

## Vòng D — JAR cuối và audit vận hành

Lũy kế Backend 263/268, Client 110/110, tổng 373 PASS / 378 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng D chạy mới BE 43/43, Client 47/47 (gồm E2E 16/16) và Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới. SHA256 `6152F61629FF5082FE2438805262B40E7F6EA822E0664707CADE0921C6A9D8D8`. BE 4 suites: 21/10/5/7. Client 6 suites: E2E16, security5, mail API6, mailbox8, session3, Swing9; tất cả pass. Newman 140/653 mới, OAuth 4/4/4/2 rejected0, MySQL native V1–V7. D001–047: 36 HTTP + 11 control, AM-4309 manual local đóng, 60 OPEN. 5 live SKIP kế thừa. Evidence `audit-operations-suite-summary.json`, `client-integration-D-suite-summary.json`, `postman-summary-D.json`, `oauth-summary-D.json`, `mysql-metadata-D.tsv`, `manual-mysql-metadata-D.tsv`, `manual-http-observed-D.ndjson` trong build/acceptance module tương ứng. D2: Compose standalone không account/session, 760×620, scale 1.25; hit-test/pointer đúng nhưng không nhận AWT MOUSE_PRESSED, focus vẫn JComboBox. Dừng trước typing/chooser/DnD/send, exit 2 và root đã dọn; chưa chứng minh native input hoặc authenticated UI.

## Vòng E — V8, quyền actor dưới transaction và Client fail-closed

Lũy kế Backend 267/272, Client 114/114, tổng 381 PASS / 386 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng E xác minh BE 64/64 (46+18 sau sửa tương thích H2), Client 60/60 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới. JAR SHA256 B7DF41FF083C414AF6C9542EBDCD597D0E8F8F494CB84CDEDB6B8A73AFE60CF5. BE chọn 5 suite bằng Gradle --max-workers=1 --no-daemon: lần đầu 64 tests/46 PASS/18 Admin context failures do H2 không hỗ trợ ALTER nhiều ADD cùng lệnh; sửa V8 thành từng ALTER, chạy lại Admin18/18. Bốn suite đã PASS: MailServiceFailureRegressionTest21, MailServiceGreenMailIntegrationTest10, AuditLogServiceTest5, MailAuditRecoveryIntegrationTest10. Client 7 suite chọn: AcceptanceRegression9, ApiClient5, E2E16, SecurityAndResilience5, MailApiClientNewEndpoints6, SwingFeatureAcceptance13, UiLayoutRegression6 = 60 PASS. Không đếm hai lần các ca Admin đầu bị lỗi. BE/FE actual 124 ca riêng biệt đã xác minh; không full run 386. Newman 140/653 mới, OAuth 4/4/4/2 rejected0. Native MySQL V1–V8 và upgrade riêng V7→V8 giữ audit/PREPARED; E001–042 33 HTTP +9 control. Evidence: backend-round-E-suite-summary.json, client-round-E-suite-summary.json, postman-summary-E.json, oauth-summary-E.json, mysql-metadata-E.tsv, manual-mysql-metadata-E.tsv, manual-http-observed-E.ndjson, audit-upgrade-summary.txt. E1 native desktop stop trước input. Không thêm manual/live PASS; 60 OPEN giữ nguyên.

**Đóng gói V8:** Dockerfile tạo sẵn `/app/data/mail-audit-receipts` mode 0700 và owner mailapp; Compose mount named volume `mail_audit_receipts` đúng path. Giữ volume và AES key đồng bộ database khi recreate; không xóa volume khi còn unresolved. Chỉ kiểm cấu hình source/indent/mount; chưa build/run container hoặc thử ACL/volume trên Docker. Không đổi Java/JAR sau kiểm thử cuối.

## Vòng F — provider faults, receipt conflicts và Client export/attachment

Lũy kế Backend 270/275, Client 116/116, tổng 386 PASS / 391 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng F chạy mới BE 44/44, Client 50/50 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới.

- BE: gradlew.bat test --tests "*MailAuditRecoveryIntegrationTest" --tests "*MailServiceFailureRegressionTest" --tests "*MailServiceGreenMailIntegrationTest" bootJar --max-workers=1 --no-daemon --console=plain; 13 + 21 + 10 = 44/44.
- Client: test-client-e2e.ps1 -Java21Home <JDK21> -SkipBuild -DatabaseEngine MySQL -RunPostman -ClientTests chọn ClientLiveServerE2ETest, AcceptanceRegressionTest, AdminModuleClientTest, SwingFeatureAcceptanceTest, MailApiClientNewEndpointsTest; 16 + 10 + 5 + 13 + 6 = 50/50; chạy tuần tự, exit 0.
- Newman6.2.1: 140 request/653 assertion, 0 failed/pending; OAuth normal fixture 4/4/4/2, rejected=0, refreshRejected=0. MySQL native9.5.0, V1–V8 success, utf8mb4/utf8mb4_unicode_ci, 9 settings, 1 admin, 5 bảng. JAR SHA256 117BC6063255AFB84A8FDC7E613070D458F4F05AE4C8875A5C2E2C5C2C7CCBC0.
- Manual fixture riêng -SkipBuild -DatabaseEngine MySQL -ManualSession -ManualPostman -ManualFaults -ManualAuditFaults -ManualSeconds 900: F201–226, 13 application HTTP (F210 setup error) + 13 control/aggregate; AM-4343 local manual PASS duy nhất mới. Không cộng vào JUnit/Newman.
- Archive trong build/acceptance module tương ứng: backend-round-F-suite-summary.json, client-round-F-suite-summary.json, postman-summary-round-F.json, oauth-summary-round-F.json, mysql-metadata-round-F.tsv, manual-http-observed-round-F.ndjson, manual-mysql-metadata-round-F.tsv. XML headers/aggregate only. Hai fixture root và manual pointer đã dọn. 5 live SKIP kế thừa; không có full run, native Swing hay Gmail thật mới.

## Vòng G2 — sau vòng F (kết quả hiện hành)

Lũy kế Backend 277/282, Client 120/120, tổng 397 PASS / 402 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng G2 (sau F) chạy mới BE 132/134 (2 live SKIP do wildcard chọn suite XOAUTH2 live; không chạy Gmail thật), Client 65/65 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới.

| Module | Suite | Tests | PASS | SKIP | FAIL | ERROR |
|---|---|---:|---:|---:|---:|---:|
| Backend | AdminIntegrationTest | 18 | 18 | 0 | 0 | 0 |
| Backend | GmailAccountControllerTest | 15 | 15 | 0 | 0 | 0 |
| Backend | GlobalExceptionHandlerTest | 3 | 3 | 0 | 0 | 0 |
| Backend | AccessTokenProviderTest | 2 | 2 | 0 | 0 | 0 |
| Backend | DefaultMailConnectionFactoryGreenMailTest | 10 | 10 | 0 | 0 | 0 |
| Backend | MailConnectionFactoryTest | 5 | 5 | 0 | 0 | 0 |
| Backend | MailConnectionFaultAndRetryTest | 4 | 4 | 0 | 0 | 0 |
| Backend | MailFallbackConnectionTest | 3 | 3 | 0 | 0 | 0 |
| Backend | RealGmailXOAuth2IntegrationTest | 2 | 0 | 2 | 0 | 0 |
| Backend | MailServiceFailureRegressionTest | 22 | 22 | 0 | 0 | 0 |
| Backend | MailServiceGreenMailIntegrationTest | 10 | 10 | 0 | 0 | 0 |
| Backend | XOAuth2IntegrationTest | 3 | 3 | 0 | 0 | 0 |
| Backend | FlywayUserIdentityMigrationTest | 1 | 1 | 0 | 0 | 0 |
| Backend | OAuth2MockWebServerTest | 21 | 21 | 0 | 0 | 0 |
| Backend | OAuth2ServiceTest | 6 | 6 | 0 | 0 | 0 |
| Backend | GmailMultiAuthIntegrationTest | 9 | 9 | 0 | 0 | 0 |
| Client | AcceptanceRegressionTest | 12 | 12 | 0 | 0 | 0 |
| Client | AdminModuleClientTest | 5 | 5 | 0 | 0 | 0 |
| Client | ClientLiveServerE2ETest | 16 | 16 | 0 | 0 | 0 |
| Client | GmailMultiAuthClientTest | 5 | 5 | 0 | 0 | 0 |
| Client | MailApiClientNewEndpointsTest | 6 | 6 | 0 | 0 | 0 |
| Client | MailboxRegressionTest | 8 | 8 | 0 | 0 | 0 |
| Client | SwingFeatureAcceptanceTest | 13 | 13 | 0 | 0 | 0 |

Native V8→V9 chạy riêng bằng VerifyAccountRevisionUpgrade; full HTTP fixture MySQL chạy ClientTests chọn 7 suite và Newman. OAuth live suite bị chọn bởi wildcard *XOAuth2IntegrationTest nhưng cả 2 case SKIP, không có live request. Evidence: backend-round-G2-suite-summary.json, client-round-G2-suite-summary.json, postman-summary-round-G2.json, oauth-summary-round-G2.json, mysql-metadata-round-G2.tsv, account-revision-upgrade-summary-round-G2.txt (build/acceptance từng module). JAR SHA256 D5656D8FACAA0F4BF725237766471F1DFC8CDFD011567469568524715732D38D.

293 PASS / 116 N/A / 59 OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Không có tiêu chí mới đủ toàn bộ nghĩa vụ để đóng. Không lặp các thao tác đã đủ hoặc bị safety gate chặn.

## Vòng G3 — kết quả hiện hành

Lũy kế Backend 279/284, Client 124/124, tổng 403 PASS / 408 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng G3 chạy mới BE 100/100, Client 66/66 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; 5 live SKIP kế thừa, không chạy Gmail thật hoặc full suite mới.

| Module | Suite | Tests | PASS | SKIP | FAIL | ERROR |
|---|---|---:|---:|---:|---:|---:|
| Backend | GmailAccountControllerTest | 15 | 15 | 0 | 0 | 0 |
| Backend | DefaultMailConnectionFactoryGreenMailTest | 10 | 10 | 0 | 0 | 0 |
| Backend | MailboxRevisionChainIntegrationTest | 2 | 2 | 0 | 0 | 0 |
| Backend | MailConnectionFactoryTest | 5 | 5 | 0 | 0 | 0 |
| Backend | MailConnectionFaultAndRetryTest | 4 | 4 | 0 | 0 | 0 |
| Backend | MailFallbackConnectionTest | 3 | 3 | 0 | 0 | 0 |
| Backend | MailServiceFailureRegressionTest | 22 | 22 | 0 | 0 | 0 |
| Backend | XOAuth2IntegrationTest | 3 | 3 | 0 | 0 | 0 |
| Backend | OAuth2MockWebServerTest | 21 | 21 | 0 | 0 | 0 |
| Backend | OAuth2ServiceTest | 6 | 6 | 0 | 0 | 0 |
| Backend | GmailMultiAuthIntegrationTest | 9 | 9 | 0 | 0 | 0 |
| Client | AcceptanceRegressionTest | 12 | 12 | 0 | 0 | 0 |
| Client | AttachmentCleanupServiceTest | 10 | 10 | 0 | 0 | 0 |
| Client | ClientLiveServerE2ETest | 16 | 16 | 0 | 0 | 0 |
| Client | GmailMultiAuthClientTest | 5 | 5 | 0 | 0 | 0 |
| Client | MailboxRegressionTest | 10 | 10 | 0 | 0 | 0 |
| Client | SwingFeatureAcceptanceTest | 13 | 13 | 0 | 0 | 0 |

Evidence: build/acceptance/backend-round-G3-suite-summary.json, client-round-G3-suite-summary.json, postman-summary-round-G3.json, oauth-summary-round-G3.json, mysql-metadata-round-G3.tsv và jar-round-G3.sha256. JAR SHA256 02E672CD7CA5ED8B0A8987F9403BF28CB25EF290E941FA40E5E04B023A5EFC41.

293 PASS / 116 N/A / 59 OPEN, 26 task đóng đầy đủ, 52 task còn OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Không có tiêu chí mới đủ toàn bộ nghĩa vụ. Không lặp thao tác credential bị gate, C/D2/E1, AM-4309, AM-4343, E001, E042; không thay bootstrap.

## Vòng G4 — kết quả hiện hành

Lũy kế Backend 280/285, Client 126/126, tổng 406 PASS / 411 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng G4 chạy mới BE 34/34, Client 46/46 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; 5 live SKIP kế thừa, không chạy Gmail thật hoặc full suite mới.

| Module | Suite | Tests | PASS | SKIP | FAIL | ERROR |
|---|---|---:|---:|---:|---:|---:|
| Backend | FolderCacheTest | 2 | 2 | 0 | 0 | 0 |
| Backend | MailServiceFailureRegressionTest | 22 | 22 | 0 | 0 | 0 |
| Backend | MailServiceGreenMailIntegrationTest | 10 | 10 | 0 | 0 | 0 |
| Client | AdminModuleClientTest | 5 | 5 | 0 | 0 | 0 |
| Client | ClientLiveServerE2ETest | 16 | 16 | 0 | 0 | 0 |
| Client | MailboxRegressionTest | 11 | 11 | 0 | 0 | 0 |
| Client | SwingFeatureAcceptanceTest | 14 | 14 | 0 | 0 | 0 |

G4 R01–R12: observer CLI tương tác từng lệnh trên factory thật từ JAR G4, GreenMail mới loopback; account/password/AES synthetic ngẫu nhiên chỉ trong process, không app login/DB. Raw Store/Transport chưa connect khi cấp; caller connect/close, INBOX READ_ONLY total=2/unread=2, Folder close không đóng Store; FolderNotFoundException và caller abort trước send đều đóng scope. Final storeOpen=false/transportOpen=false, fixtureMessages=2, quit exit0, root removed. Không SMTP DATA fault mới, không suy isConnected thành đo mọi socket OS; không HTTP/Postman/native Swing/Gmail live mới.

G4: RealGmailIntegrationTest bổ sung raw standalone Store.connect/READ_ONLY/count/close và Transport connect/close trong nhánh App Password opt-in hiện hữu, dùng account/AES chỉ trong memory. RealGmailMimeFixturesTest bổ sung metadata sample ordinal, INBOX UID/UIDVALIDITY/attachment count sau verify exact bytes; không log raw content/secret, giữ mẫu cho manual, không tự xóa hoặc retry khi kết quả chưa rõ. Hai source đã compile, chưa chạy live; vẫn cần dedicated account/consent/permission và verify targeted cleanup/response-log. Không thay consent hoặc token flow.

Evidence module build/acceptance: backend-round-G4-suite-summary.json, client-round-G4-suite-summary.json, postman-summary-round-G4.json, oauth-summary-round-G4.json, mysql-metadata-round-G4.tsv, manual-raw-round-G4.tsv và jar-round-G4.sha256. JAR SHA256 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A.

293 PASS / 116 N/A / 59 OPEN; 26 task đóng, 52 còn OPEN. 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Raw caller-owned local đã quan sát, nhưng không có criterion đủ toàn bộ evidence mới. Ma trận 59 criterion, 171 hàng nghĩa vụ, 92 hàng thiếu prerequisite/evidence. Không đổi checkbox/spec, không tự phê duyệt hoặc thay loại evidence.

## G5 — Client targeted regression/E2E

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

| Suite chạy mới | PASS | SKIP/FAIL/ERROR |
|---|---:|---:|
| AcceptanceRegressionTest | 12/12 | 0/0/0 |
| ClientLiveServerE2ETest | 16/16 | 0/0/0 |
| GmailMultiAuthClientTest | 5/5 | 0/0/0 |
| SwingFeatureAcceptanceTest | 16/16 | 0/0/0 |

Ticket là guard phía Client, không khóa atomic với request; không thu hồi request đã dispatch. Không thêm HTTP contract, không đổi Backend/bootstrap/DB schema hay live preparation. Không lặp thao tác credential bị gate, không giả session/token, không yêu cầu người khác làm thay.

## G6 — Client targeted regression

Evidence G6: Mail Client/build/acceptance/client-round-G6-suite-summary.json; 3 suite: GmailMultiAuthClientTest 5/5, MailboxRegressionTest 13/13, SwingFeatureAcceptanceTest 16/16, tổng 34/34, 0 skip/fail/error. gradlew.bat test --tests *MailboxRegressionTest --tests *SwingFeatureAcceptanceTest --tests *GmailMultiAuthClientTest --max-workers=1 --no-daemon --console=plain --offline, BUILD SUCCESSFUL. Hai test mới; không cộng 32 test rerun vào lũy kế. E2E/MySQL G5, Newman/OAuth loopback/BE G4 kế thừa; không tạo fixture server mới. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A.

| Suite chạy mới | PASS | SKIP/FAIL/ERROR |
|---|---:|---:|
| GmailMultiAuthClientTest | 5/5 | 0/0/0 |
| MailboxRegressionTest | 13/13 | 0/0/0 |
| SwingFeatureAcceptanceTest | 16/16 | 0/0/0 |

G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

Không thay Backend/JAR, bootstrap, schema, API contract, permission, JWT/RBAC/PKCE/TLS/UIDVALIDITY/audit/recovery. Guard Client không thu hồi request đã dispatch hoặc consent remote. Không retry hành vi credential bị chặn, không đổi công cụ, worker, fake session/token, private state hoặc yêu cầu người khác làm thay. Native desktop vẫn thiếu điều kiện xác nhận; không chạy lại C/D2/E1. Không có live/human sign-off mới.

## Phê duyệt contract — 26/11/2026 (sau G6)

78 task / 468 tiêu chí: 298 PASS, 116 N/A, 54 OPEN; 30 task đóng, 48 task còn OPEN. 298/352 tiêu chí áp dụng = 84,659%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 5 contract, 3 privacy/live. 171 ID: 81 K/M kế thừa + 3 A phê duyệt = 84 nghĩa vụ đã thỏa; 83 trực tiếp thiếu + 4 shared S = 87 còn thiếu.

Chủ dự án phê duyệt 26/11/2026, sau G6: AM-4323-C.03 A — SEND_MAIL metadata-only (masked sender/counts, không raw/hash To/subject/body), C.04 vẫn L cả hai mode Gmail, delivery/read-back/attachments, shared 4323-M.05. AM-4340-C.03 A — bootstrap từ ADMIN_INITIAL_PASSWORD/cấu hình, BCrypt và forced-change, không log plaintext, không ghi đè Admin, Flyway V1–V9 additive; với evidence MySQL/seed kế thừa, contract và task đã PASS. AM-4391-C.02 A — explicit permanent=true trong Trash, UIDVALIDITY, targeted UID MOVE/UIDPLUS, default 204, Prefer 200 tùy chọn, fail-closed; C.03 vẫn L Gmail Trash/All Mail thật. Không có D còn chờ; phê duyệt không thay bằng chứng live, human/manual, test hay triển khai.

Không chạy test mới, không thay source/JAR, giữ JUnit 410/415 (5 live SKIP), Newman 653/653 kế thừa và E2E 16/16 kế thừa. Mọi mô tả D/chờ phê duyệt ở các mốc trước là lịch sử, không phải residual hiện hành.
