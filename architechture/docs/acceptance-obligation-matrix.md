# Ma trận nghĩa vụ và phụ thuộc — sau phê duyệt contract (26/11/2026)

**54 tiêu chí OPEN trên 48 task; 30/78 task đã đóng.** Giữ 59 cụm lịch sử/171 ID; 4 manual criterion PASS local G6 và 1 contract PASS sau phê duyệt. Nguồn: §4 hiện hành và §5 từng task; ba Task Detail đã sửa theo phê duyệt 26/11/2026. Các thiết kế 01/02/03 và evidence G6 được giữ lịch sử; các nghĩa vụ live TV2–TV5/M04–M12 vẫn bắt buộc. [Residual](open-criteria-current.md), [review](acceptance-review.md).

Lũy kế Backend 280/285, Client 130/130, tổng 410 PASS / 415 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G6 chạy mới Client 34/34 targeted (2 test mới); E2E 16/16 kế thừa G5, Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion kế thừa G4. Không Gmail thật, native manual, HTTP manual, E2E, Newman hoặc full suite mới.

## Quy tắc và prerequisite dùng chung

| Mã | Điều kiện / ý nghĩa |
|---|---|
| K | K — code/test, chưa thay manual |
| M | M — quan sát local đúng scope |
| H | H — thiếu phiên nghiệp vụ được cấp quyền độc lập |
| N | N — thiếu desktop native hợp lệ |
| L | L — thiếu Gmail test thật/consent/quyền |
| I | I — thiếu điều kiện cô lập/chứng minh hợp lệ |
| F | F — cần fault fixture được phép |
| D | D — thiếu quyết định contract |
| A | A — quyết định contract đã được chủ dự án phê duyệt rõ; không phải test/observation mới |
| S | S — tham chiếu nghĩa vụ dùng chung chưa đạt; vẫn giữ ID và điều kiện H/L gốc, không tính là evidence đã đạt hay N/A |

- K/M là evidence đã có cho nghĩa vụ cụ thể, **không** phải kết luận mọi nhánh triển khai hoàn hảo hoặc criterion manual đã PASS. M chỉ đúng phạm vi quan sát (loopback/CLI/HTTP). K là source/automated; kiểm chứng native/live vẫn phải riêng. K/M cùng hàng nghĩa là có cả hai loại evidence. A là điều kiện quyết định đã thỏa; evidence thực thi K/M vẫn được đánh giá riêng.
- H, N, L, I, F, D là điều kiện chưa thỏa; dấu `/` nối các prerequisite liên quan, không phải lựa chọn lách gate. Với native public không cần login, N vẫn cần window PID/foreground/hit-test/focus/event/observed response đúng fixture; native hậu login cần H độc lập. Không lặp probe C/D2/E1 khi điều kiện không đổi.
- H không có nghĩa yêu cầu người dùng/worker làm thay thao tác bị chặn; chỉ xét phiên độc lập được cấp quyền rõ ràng. Không fake session/token/credential, không truy cập private state, không đổi bootstrap, không suy atomic MySQL–Gmail. F chỉ fault trên fixture riêng nếu cần evidence; không tái thực hiện mutation đã thành công/chưa rõ outcome.
- L: dedicated Gmail test account, scope/consent và quyền thực hiện được xác nhận độc lập; chỉ thư synthetic marker/UID. D: chủ dự án phê duyệt rõ contract thay thế an toàn; không tự sửa yêu cầu.
- Điều kiện đóng mỗi criterion: AND của toàn bộ nghĩa vụ trực tiếp cần thiết bên dưới, loại evidence đúng §4/§5 và phạm vi bàn giao. S/H hoặc S/L chỉ dùng khi yêu cầu bàn giao đã được gắn nhầm thành điều kiện riêng của criterion manual local, có căn cứ cụ thể từ 02/03 và có criterion đích vẫn OPEN chịu trách nhiệm đầy đủ (bao gồm các điều kiện gốc tại hàng S được link ngược). Không coi S là đạt, không bỏ live, không tách hành vi trọng tâm ghi rõ trong §4. Một hành trình hợp lệ có thể cung cấp evidence cho nhiều hàng; không bắt buộc toàn bộ criterion liên quan PASS trước khi thu thập evidence độc lập. Human sign-off nếu yêu cầu vẫn riêng.

78 task / 468 tiêu chí: 298 PASS, 116 N/A, 54 OPEN; 30 task đóng, 48 task còn OPEN. 298/352 tiêu chí áp dụng = 84,659%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 5 contract, 3 privacy/live. Giữ 171 ID: 81 K/M có evidence, 3 A đã có quyết định chủ dự án (84 nghĩa vụ đã thỏa); 83 điều kiện trực tiếp thiếu và 4 tham chiếu S chưa đạt, tổng 87 hàng chưa thỏa.

**Phê duyệt đặc tả 26/11/2026 (sau G6):** Chủ dự án đã đồng ý giữ ba phương án an toàn và yêu cầu sửa Task Detail. 4323-C.03 A: audit metadata-only, không raw/hash To/subject/body; 4323-C.04 L vẫn thiếu Gmail thật hai auth mode, delivery/read-back/attachment (shared 4323-M.05). 4340-C.03 A: schema additive V1–V9 và bootstrap từ cấu hình, BCrypt/forced-change, không plaintext console, không ghi đè Admin; đủ evidence K/M/K cũ, đóng contract và task. 4391-C.02 A: explicit permanent=true trong Trash, UIDVALIDITY, UID MOVE/UIDPLUS targeted, default 204 (Prefer 200 tùy chọn), không global expunge; 4391-C.03 L vẫn thiếu Gmail Trash/All Mail thật. Không test/observation mới, không đổi source/JAR hoặc giảm guard; các mốc G6 trở về trước là lịch sử.

**Lịch sử G6:** Giữ nguyên 171 nhóm nghĩa vụ/ID. G6: 81 hàng K/M (tăng 1 từ 4311-M.02, kiểm shape của H005/H071/F026 đã khử nhạy cảm); 86 hàng điều kiện trực tiếp còn thiếu, 4 hàng S tham chiếu điều kiện chưa đạt ở criterion đích. Tổng 90/171 hàng vẫn chưa có evidence hoàn tất (86 trực tiếp + 4 tham chiếu). Không tính S là PASS/N/A; không tạo thêm manual/live observation. G5: 80 K/M, 91 thiếu; G6 đóng 4 criterion manual local đã có toàn bộ evidence đúng phạm vi, không đóng điều kiện live tương ứng.

## Manual Backend — 16 OPEN, 4 PASS local (giữ lịch sử ID)

<a id="am-4310-m"></a>

### AM-4310 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Login và JWT, lock/maintenance/rate-limit. [Task §4 nguyên bản](Tasks%20Detail/AM-4310-BE-%C4%90%C4%83ng-nh%E1%BA%ADp-%E2%80%94-tr%E1%BA%A3-JWT.md).
- **Source/đường thực thi:** AuthController, AuthServiceImpl.login, JwtProvider/JwtFilter, LoginRateLimiter.
- **Chưa đạt:** Còn login bằng email, sai mật khẩu, locked/maintenance và 429 trực tiếp; expiry ở 4311.
- **Blocker và điều kiện tối thiểu:** H — phiên chính thức được phép cho luồng auth; không chạy lại batch credential bị chặn.
- **Phụ thuộc:** Phiên auth độc lập → 4310 → dùng kết quả hợp lệ cho 4311; không tái tạo phiên bị chặn.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4310-M.01 | Login username thành công → JWT Bearer + /me đúng owner/role, expiresIn và DTO | H012, H003–005; AuthServiceTest | M |
| 4310-M.02 | Login email; sai credential → 401, LOGIN_FAILED đúng actor/reason không lộ input | AuthServiceImpl.login; AuthServiceTest, AuthRegisterAndChangePasswordTest; chưa quan sát trực tiếp các nhánh này | H |
| 4310-M.03 | Enabled=false → 403 ACCOUNT_DISABLED tại login (không chỉ JWT cũ bị từ chối) | H067 chỉ chứng minh session khóa 401; code/test AuthServiceImpl | H |
| 4310-M.04 | Maintenance USER 403, ADMIN được phép; rate limit 429 và Retry-After | AuthServiceImpl/LoginRateLimiter, automated; còn phiên login chính thức | H |

**Còn thiếu để đóng:** 4310-M.02 (H); 4310-M.03 (H); 4310-M.04 (H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4311-m"></a>

### AM-4311 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Thông tin /me và quyền theo session. [Task §4 nguyên bản](Tasks%20Detail/AM-4311-BE-L%E1%BA%A5y-th%C3%B4ng-tin-user-hi%E1%BB%87n-t%E1%BA%A1i.md).
- **Source/đường thực thi:** AuthController.getCurrentUser, JwtFilter, UserDto.
- **Chưa đạt:** Còn expiry và forced-change /me. G6 đối chiếu bản ghi scrubbed H005/H029/H069/H071/H133/F026/F032: đủ shape bảy trường, không field bí mật; 4311-M.02 đủ scope local. Không có /me forced-change=true hoặc expiry trực tiếp trong evidence đã kiểm.
- **Blocker và điều kiện tối thiểu:** H/I — phiên hợp lệ với lifecycle expiry/forced-change được cấp quyền, evidence metadata đã lọc.
- **Phụ thuộc:** Phiên hợp lệ + expiry/forced-change độc lập; không mint token. Có thể dùng kết quả 4310/4312/4331 nếu chính thao tác được phép.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4311-M.01 | Không Bearer/sai Bearer 401, Bearer hợp lệ 200 đúng identity/role | H003–005, H029, H071 | M |
| 4311-M.02 | DTO id/username/fullName/email/role/mustChangePassword/createdAt, không password/hash | G6 kiểm bản ghi JSON đã redact H005/H071/F026: GET /api/auth/me 200, đúng 7 key, createdAt parse được, mustChangePassword boolean, không extra key. Redactor giữ key (chỉ che value), không đọc private manifest/credential. H029/H069/H133/F032 cũng đúng shape. Không chứng minh flag=true hoặc JWT hết hạn. | M/K |
| 4311-M.03 | Bearer hết hạn 401 và forced-change /me đúng lifecycle | JwtFilter/JwtProvider, H078 gate; không mint/inject token | H |

**Còn thiếu để đóng:** 4311-M.03 (H). Cần kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4312-m"></a>

### AM-4312 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Đổi mật khẩu và session sau thay đổi. [Task §4 nguyên bản](Tasks%20Detail/AM-4312-BE-User-t%E1%BB%B1-%C4%91%E1%BB%95i-m%E1%BA%ADt-kh%E1%BA%A9u.md).
- **Source/đường thực thi:** AuthServiceImpl.changePassword, AuthController, BCrypt, JwtFilter.
- **Chưa đạt:** Còn đổi đúng/sai/trùng, mật khẩu cũ bị từ chối, phiên mới hợp lệ.
- **Blocker và điều kiện tối thiểu:** H — nghiệp vụ đổi mật khẩu chỉ trong phiên được cấp phép; thao tác agent đã bị chặn không được retry/chuyển giao.
- **Phụ thuộc:** Phiên được phép, target riêng, state trước/sau; liên quan 4331/4344, không retry credential.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4312-M.01 | Current password sai / new trùng / độ dài không hợp lệ → lỗi đúng code | AuthRegisterAndChangePasswordTest, AuthServiceTest; blocked credential batch | H |
| 4312-M.02 | Đổi hợp lệ → hash mới, mustChangePassword=false, CHANGE_PASSWORD, response 200 | Source transaction + tests; H078 chỉ là gate forced-change | H |
| 4312-M.03 | Password cũ không dùng được, phiên mới hợp lệ, mở lại chức năng | Auth/JWT tests; chưa manual được phép | H |

**Còn thiếu để đóng:** 4312-M.01 (H); 4312-M.02 (H); 4312-M.03 (H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4314-m"></a>

### AM-4314 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Link App Password, validation/quota/flags/audit. [Task §4 nguyên bản](Tasks%20Detail/AM-4314-BE-Li%C3%AAn-k%E1%BA%BFt-Gmail-b%E1%BA%B1ng-App-Password.md).
- **Source/đường thực thi:** GmailAccountController, GmailAccountLinkService, DefaultMailConnectionFactory.testImap, AesCipher.
- **Chưa đạt:** Còn request hợp lệ khi disabled, credential sai, quota/validation còn thiếu và Gmail thật.
- **Blocker và điều kiện tối thiểu:** H/L — môi trường chuyên test được cấp quyền; không nhập secret hay retry gate.
- **Phụ thuộc:** Phiên mailbox riêng + flag/quota; 4396/4359 cho provider thật; 4354 cho UI.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4314-M.01 | Email/request validation, flag off 403, quota 409 | Code/test validation, H116 flag read không phải POST link; actual request hợp lệ còn thiếu | H |
| 4314-M.02 | Duplicate 409, explicit update và create 201 đúng owner/id/status | H082–083/H105–106, V9 stale conflict regression | M |
| 4314-M.03 | Probe App Password sai → 422; DNS/network → 502; đóng tài nguyên | F204/F205 là mailbox đã link, T outage đã đủ cho wrapper; raw G4 R09 đóng khi folder lỗi; actual POST link sai/remote probe còn thiếu; Giữ shared 4359-M.03 (actual POST link/probe). | H/L |
| 4314-M.04 | 2FA App Password thật, Store.connect/probe, AES persistence, LINK_GMAIL và DTO an toàn | GmailMultiAuthIntegrationTest, schema G2; chỉ local encryption/probe, chưa Google thật; Giữ shared 4359-M.03 (dedicated 2FA/probe). | L |

**Còn thiếu để đóng:** 4314-M.01 (H); 4314-M.03 (H/L); 4314-M.04 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4316-m"></a>

### AM-4316 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** URL OAuth2/PKCE/state/consent. [Task §4 nguyên bản](Tasks%20Detail/AM-4316-BE-T%E1%BA%A1o-URL-u%E1%BB%B7-quy%E1%BB%81n-Google-OAuth2.md).
- **Source/đường thực thi:** OAuth2ServiceImpl authorize/start, OAuth2StateStore, MainFrame.initiateOAuth2Flow.
- **Chưa đạt:** Còn mở trình duyệt và consent Google thật từ Client.
- **Blocker và điều kiện tối thiểu:** L — dedicated Google OAuth client/consent/test account được duyệt. Local provider đã đủ cho shape/quota/TTL.
- **Phụ thuộc:** OAuth client/consent dedicated + browser; kết quả hợp lệ dùng chung 4317/4355.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4316-M.01 | JWT, flag 403, quota create 409/UPSERT owner đúng | H041, H046–047, G017–022 | M |
| 4316-M.02 | URL HTTPS mail scope/offline/state PKCE S256, TTL 5 phút, response no-store | G017–022/G064–065, F219; không in URL/poll secret | M |
| 4316-M.03 | Client mở browser mặc định tới Google và consent authorized | MainFrame OAuth flow; chưa native/live | N/L |

**Còn thiếu để đóng:** 4316-M.03 (N/L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4317-m"></a>

### AM-4317 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Callback đổi code, profile, token encryption, revoke. [Task §4 nguyên bản](Tasks%20Detail/AM-4317-BE-Callback-OAuth2-%E2%80%94-%C4%91%E1%BB%95i-code-l%E1%BA%A5y-token.md).
- **Source/đường thực thi:** OAuth2ServiceImpl callback/getFreshAccessToken, OAuth2StateStore, GmailAccountLinkService, AesCipher.
- **Chưa đạt:** Còn Google token/profile/consent/revoke thật và privacy/encryption live.
- **Blocker và điều kiện tối thiểu:** L — chuyên test, quyền OAuth rõ ràng và biên bản đã lọc, không in token.
- **Phụ thuộc:** 4316 consent/state hợp lệ → callback/profile/persist; provider riêng, 4360 refresh sau đó.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4317-M.01 | Callback no JWT/state lookup, expired/invalid/replay, HTML error; state one-time | F004–007, G023–040/G064–066 | M |
| 4317-M.02 | Provider code exchange, mail scope, profile identity, duplicate/explicit UPSERT, masked audit/response | H051–059, G023–040, F219–224; loopback đủ nhánh local | M |
| 4317-M.03 | Persist encrypted refresh grant, rotation commit/owner+revision, no stale cache/retry | G2 OAuth2MockWebServerTest, G3 MailboxRevisionChainIntegrationTest 2/2; nội bộ đã test | K |
| 4317-M.04 | Exchange/profile/consent/revoke Google thật, persistence/response/log privacy live | Dedicated provider test chưa có | L |

**Còn thiếu để đóng:** 4317-M.04 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4319-m"></a>

### AM-4319 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Xóa user có OAuth và revoke. [Task §4 nguyên bản](Tasks%20Detail/AM-4319-BE-H%E1%BB%97-tr%E1%BB%A3-xo%C3%A1-user-c%C3%B3-Gmail-OAuth2.md).
- **Source/đường thực thi:** AdminServiceImpl.deleteUser, UserRepository.lockActiveAdminIds, GmailAccountRepository, OAuth2Service revoke.
- **Chưa đạt:** Còn last-enabled-admin guard độc lập và Google revoke thật.
- **Blocker và điều kiện tối thiểu:** H/L — fixture quản trị có admin hy sinh được chuẩn bị hợp lệ và Google test riêng; không khóa/xóa bootstrap để ép ca.
- **Phụ thuộc:** Điều kiện last-admin như 4330/4332; revoke provider như 4317, không tác động bootstrap.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4319-M.01 | Self/missing/foreign-role guards, cascade user + mailbox, 204, audit đúng actor/count | F029–032/F085–095, H079–081, G081–091 | M |
| 4319-M.02 | Best-effort provider revoke thành công/thất bại, user khác nguyên vẹn | G081–091/F090–095; local only | M |
| 4319-M.03 | Transaction khóa active-admin set, kiểm lại actor, giữ invariant ít nhất một enabled ADMIN | AdminIntegrationTest, E008–022 actual overlap 3 admin; exact last-admin độc lập chưa đạt, không thay bootstrap | H/I |
| 4319-M.04 | Revoke Google thật trên dedicated target | Provider loopback không thay live | L |

**Còn thiếu để đóng:** 4319-M.03 (H/I); 4319-M.04 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4320-m"></a>

### AM-4320 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Danh sách folder IMAP/count/special use/errors. [Task §4 nguyên bản](Tasks%20Detail/AM-4320-BE-Danh-s%C3%A1ch-folder-IMAP.md).
- **Source/đường thực thi:** MailServiceImpl.loadAndValidateAccount/listFolders, FolderCountReader, GmailFolderResolver, DefaultMailConnectionFactory.
- **Đủ scope manual local:** §4 AM-4320/02 EP-M1 yêu cầu endpoint owner/count, raw Store lifecycle, auth/network mapping; H/F/G/T và G4 R02–R05/R09 đã quan sát đủ. 03 M08 tách list/detail/folder/MIME fixtures; AM-4321/4322 đã PASS manual local theo cùng chuẩn. Không đồng nhất manual endpoint với demo Gmail web cấp TV4.
- **Điều kiện live còn giữ:** L — 4320-M.05 là điều kiện tích hợp thật M08, link sang 4361-M.02 (remote TLS/special folders) và 4397-M.04 (demo EP-M1/count đối chiếu Gmail web). Hai criterion đích vẫn OPEN và phải đáp ứng chính xác điều kiện này, không bỏ hoặc suy từ fixture.
- **Phụ thuộc:** 4396 cung cấp dedicated mailbox; 4361/4397 tiếp tục live M08, 4362 native vẫn OPEN. Không lặp raw local/provider422/pool/outage F.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4320-M.01 | Owner GET 200/count, missing/foreign cùng 404 | H015/H084/H125, F056, G002 | M |
| 4320-M.02 | App Password và OAuth2 lỗi 422 đúng code, network 502/504, recovery không mất dữ liệu | F204/F222, G054–057, T004–008; không lặp | M |
| 4320-M.03 | Raw Store caller tạo/connect/LIST/open READ_ONLY/count/close Folder rồi close Store, lỗi folder đóng scope | G4 R02–R05/R09: 1 folder, INBOX total2/unread2, close thực | M |
| 4320-M.04 | Cache counts không dùng revision cũ, invalidation sau mutation; lease được trả/đóng | G4 FolderCacheTest; B002–006/B030–037, G3 revision guard | K |
| 4320-M.05 | Gmail special-use/count và remote TLS đúng môi trường demo của M08 | 01 §4.2, 02 A5/C2-TV4, 03 M08. Shared chưa đạt: [AM-4361-M](#am-4361-m) 4361-M.02 (TLS/special-use) và [AM-4397-M](#am-4397-m) 4397-M.04 (EP-M1 count/special-use đối chiếu Gmail web, đọc mẫu thực); không đóng live từ local. | S/L |

**Kết luận G6: PASS manual local.** 4320-M.01–04 đủ theo §4/§5 và chuẩn manual local đã áp dụng cho AM-4321/4322. 4320-M.05 vẫn chưa có evidence, giữ nguyên ở scope tích hợp live tại các criterion đích nêu trên; không claim TV4/Gmail web đã nghiệm thu. Không có manual run mới.

<a id="am-4323-m"></a>

### AM-4323 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** SMTP multipart, audit và lỗi gửi. [Task §4 nguyên bản](Tasks%20Detail/AM-4323-BE-G%E1%BB%ADi-mail-SMTP-multipart.md).
- **Source/đường thực thi:** MailController.send, MailServiceImpl.sendMail, DefaultMailConnectionFactory, SmtpConnection, MailAuditRecovery.
- **Đủ scope manual local:** §4/02 EP-M5: multipart, owner/flag, validation, MIME, SMTP giao thư/read-back, close và lỗi; H/F/G/J/T/C/D/E và G4 R06–R08/R10 đủ evidence local cho hành vi hiện hành. F013/F064–065 và J exact bytes; F205/F213–217 provider auth/uncertain. Không nhận 200 H036 setup sai hay hash lỗi G050–052 làm PASS.
- **Điều kiện live/contract còn giữ:** 4323-M.05 trùng điều kiện live của 4323-C.04 (TV5 giao thư hai auth mode, read-back/attachments thực tế); giữ contract OPEN D+L. Giống AM-4391 manual local PASS nhưng contract literal/live OPEN, không tự phê duyệt raw audit To/subject.
- **Phụ thuộc:** 4361/4397 cho dedicated provider; 4323-C.04 vẫn phải có delivery/read-back trên Gmail thật và evidence đúng công cụ/phạm vi được phép. Không retry mutation uncertain.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4323-M.01 | Ownership/flag/legacy-modern multipart, recipient/subject/body/file cap validation | H027/H040/H042/H120/H136–140, F069–070, G079–080 | M |
| 4323-M.02 | MIME UTF-8/To-Cc-Bcc/file bytes, SMTP acknowledgement và delivery thật local | F013/F064–065, G045–063, J001–005/J009–011; không ghi raw content | M |
| 4323-M.03 | Auth422, network502/504, post-delivery uncertain, không resend, one audit/event | F205/F213–217, T009–013, C/D/E recovery; đã đủ local | M |
| 4323-M.04 | Raw Transport caller create/connect/close; validation abort trước send đóng scope | G4 R06–R08/R10, fixture messages không đổi; không phải SMTP DATA fault | M |
| 4323-M.05 | Gmail SMTP thật, delivery/read-back/attachments, manual theo công cụ/phạm vi được duyệt | Shared chưa đạt: [AM-4323-C](#am-4323-c) 4323-C.04, 02 C2-TV5, 4361 provider và 4397 samples. Vẫn cần cả hai auth mode và delivery/read-back đúng scope; local không thay live. | S/L |

**Kết luận G6: PASS manual local.** 4323-M.01–04 đủ evidence nghiệp vụ local (các multipart H/F/G/J là HTTPS trực tiếp từng request, không gán là Postman automated). 4323-M.05 chưa đạt live, giữ tại 4323-C.04; contract D chưa duyệt. Không claim delivery Gmail thật/human sign-off, không có manual run mới.

<a id="am-4328-m"></a>

### AM-4328 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Khóa user, session bị khóa, last-admin. [Task §4 nguyên bản](Tasks%20Detail/AM-4328-BE-Kho%C3%A1-t%C3%A0i-kho%E1%BA%A3n-user.md).
- **Source/đường thực thi:** AdminServiceImpl.lockUser, UserRepository.lockActiveAdminIds, JwtFilter.
- **Chưa đạt:** Còn login user locked, last-enabled-admin guard độc lập/concurrency.
- **Blocker và điều kiện tối thiểu:** H — phiên Admin hợp lệ với dữ liệu hy sinh, không khóa bootstrap, chứng cứ cạnh tranh và role.
- **Phụ thuộc:** Admin fixture độc lập + invariant như 4330/4332; locked-login là nhánh H riêng.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4328-M.01 | Admin role/missing/self guard, lock target và JWT cũ fail, audit | H067/H069; F029–032, AdminIntegrationTest | M |
| 4328-M.02 | Locked login 403 tại login endpoint | AuthServiceImpl, H067 chỉ là JWT; không retry credential gate | H |
| 4328-M.03 | Last-enabled-admin lock invariant dưới transaction, exact last-admin độc lập | E008–022 overlap với 3 admin; self guard khác exact-two; không khóa bootstrap | H/I |

**Còn thiếu để đóng:** 4328-M.02 (H); 4328-M.03 (H/I). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4330-m"></a>

### AM-4330 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Đổi role và last-admin guard. [Task §4 nguyên bản](Tasks%20Detail/AM-4330-BE-%C4%90%E1%BB%95i-role-user.md).
- **Source/đường thực thi:** AdminServiceImpl.updateUserRole/requireCurrentAdmin/ensureNotLastActiveAdmin.
- **Chưa đạt:** Còn last-admin guard độc lập đúng điều kiện gốc; quyền sau đổi và overlap native đã có E008–022. Không đồng nhất race có bootstrap thứ ba hoặc self guard với race đúng 2 Admin cuối.
- **Blocker và điều kiện tối thiểu:** H/I — phiên nghiệm thu độc lập được cấp quyền và bố trí đúng last-admin; sole enabled ADMIN tự đổi bị self guard, actor khác bị từ chối. E chứng minh 403 cho actor mất quyền khi chờ khóa; không bỏ guard hoặc dùng fake principal/bootstrap target để ép LAST_ADMIN.
- **Phụ thuộc:** Điều kiện exact invariant độc lập, không chỉnh bootstrap hoặc fake principal; dùng chung 4319/4328/4332 nếu evidence đủ.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4330-M.01 | Role USER/ADMIN validation, self guard, audit, cập nhật quyền thực | H071, E008–022 actual role change/actor 403 | M |
| 4330-M.02 | Last-admin guard khi đúng hai admin cuối cạnh tranh, kết quả không còn 0 admin | E015 lock wait=1 nhưng bootstrap thứ ba; code khóa và recheck, AdminIntegrationTest; không làm giả fixture/bootstrap | H/I |

**Còn thiếu để đóng:** 4330-M.02 (H/I). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4331-m"></a>

### AM-4331 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Admin reset password/forced-change. [Task §4 nguyên bản](Tasks%20Detail/AM-4331-BE-Admin-%C4%91%E1%BA%B7t-l%E1%BA%A1i-m%E1%BA%ADt-kh%E1%BA%A9u-user.md).
- **Source/đường thực thi:** AdminServiceImpl.resetPassword, AuthServiceImpl.changePassword, JwtFilter.
- **Chưa đạt:** Còn old password bị từ chối, newPassword yếu/đúng, đổi bắt buộc và chức năng mở lại, audit.
- **Blocker và điều kiện tối thiểu:** H — phiên nghiệm thu auth được phép; không lặp gate/reset credential thay agent.
- **Phụ thuộc:** Phiên Admin/target được phép → forced-change/auth lifecycle → 4312/4344; không delegate reset.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4331-M.01 | Body newPassword hợp lệ/tạo temp random, BCrypt + forced-change, ADMIN_RESET_PASSWORD không secret | AdminIntegrationTest, compatibility code; chưa manual đủ | H |
| 4331-M.02 | Weak password 400, old bị từ chối/new login hợp lệ, forced-change gate và đổi thành công | Auth/Reset tests, H077–078 partial; credential gate | H |
| 4331-M.03 | Đúng response/audit, chức năng mở lại, không log password | Source DTO/audit; cần evidence metadata authorized | H |

**Còn thiếu để đóng:** 4331-M.01 (H); 4331-M.02 (H); 4331-M.03 (H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4332-m"></a>

### AM-4332 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Xóa user/cascade/revoke và last-admin. [Task §4 nguyên bản](Tasks%20Detail/AM-4332-BE-Xo%C3%A1-t%C3%A0i-kho%E1%BA%A3n-user.md).
- **Source/đường thực thi:** AdminServiceImpl.deleteUser, OAuth2Service revoke, V9 versioned delete.
- **Chưa đạt:** Còn last-admin độc lập và revoke Google thật.
- **Blocker và điều kiện tối thiểu:** H/L — fixture quản trị hợp lệ, target hy sinh không phải bootstrap, dedicated Google account.
- **Phụ thuộc:** Như 4330 exact invariant và 4319 revoke; target hy sinh riêng.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4332-M.01 | Owner/admin authorization, self/missing, cascade 204, user/session biến mất, audit | F085–095, H079–081, G081–091 | M |
| 4332-M.02 | Best-effort revoke fault không làm sai cascade, unrelated mailbox giữ nguyên | G081–091 | M |
| 4332-M.03 | Last-admin exact invariant transaction độc lập | E008–022 overlap với bootstrap thứ ba; V9 stale delete code/test; không sửa bootstrap | H/I |
| 4332-M.04 | Google revoke thật | Dedicated test target chưa có | L |

**Còn thiếu để đóng:** 4332-M.03 (H/I); 4332-M.04 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4344-m"></a>

### AM-4344 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Audit đúng action/actor/metadata và tính bền vững. [Task §4 nguyên bản](Tasks%20Detail/AM-4344-BE-Audit-log-service.md).
- **Source/đường thực thi:** AuditLogServiceImpl, MailAuditRecovery, MailAuditReceiptJournal, V7–V9.
- **Chưa đạt:** LOGIN_FAILED/ADMIN_RESET manual còn thiếu; privacy ngoài canary/human/live riêng. G5 xác nhận điều kiện an toàn PREPARED không receipt đã có D017–027 (restart, đối chiếu thư fixture cùng UID, không sinh success giả) và F guard hiện hành. Khi sự cố thật xảy ra vẫn cần đối chiếu được phép; không bảo đảm phục hồi outcome mất hoặc tự sửa outcome.
- **Blocker và điều kiện tối thiểu:** H — phiên nghiệp vụ được cấp quyền và evidence action/count. Không yêu cầu tạo thêm crash hoặc fault chỉ để kiểm lại 4344-M.04; không phục hồi lịch sử trước V7.
- **Phụ thuộc:** Nhánh LOGIN_FAILED/ADMIN_RESET hợp lệ từ 4310/4331 có thể cung cấp audit evidence. PREPARED chỉ đối chiếu được phép, không tự finalize/replay; D/E/F đã đủ phạm vi local của invariant này.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4344-M.01 | Action/actor/time/IP/metadata đúng; REGISTER/link/mail/admin/settings đã quan sát ở các luồng liên quan | H/F/G/J, D/E event counts và metadata | M |
| 4344-M.02 | LOGIN_FAILED và ADMIN_RESET_PASSWORD đúng action/actor, không password | AuthServiceImpl/AdminServiceImpl tests; cần manual auth/reset được phép | H |
| 4344-M.03 | Intent trước remote, CONFIRMED/UNCERTAIN, receipt/backoff, exact one audit, fail-closed | C/D/E native recovery, F journal tests 13/13; không lặp | M/K |
| 4344-M.04 | PREPARED không receipt: không tự finalize, đối chiếu remote; crash trước durable không suy atomic | D017–027: restart thực fixture, PREPARED1/localDeferred0, cùng UID/read-back đúng 1 thư, không thêm audit success; F MailAuditRecoveryIntegrationTest 13/13 giữ conflict/unreserved và replay chỉ audit. G5 đối chiếu §4 gốc, không yêu cầu tạo sự cố mới; điều kiện đối chiếu thực tế khi mất durable evidence vẫn giữ. | M/K |
| 4344-M.05 | Privacy response/log đủ phạm vi nghiệp vụ, không suy rộng aggregate canary sang mọi bí mật | E042/F226 đã đủ nhóm canary; live privacy riêng | K |

**Còn thiếu để đóng:** 4344-M.02 (H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4358-m"></a>

### AM-4358 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Factory và raw Store/Transport lifecycle/TLS. [Task §4 nguyên bản](Tasks%20Detail/AM-4358-BE-Interface-MailConnectionFactory.md).
- **Source/đường thực thi:** MailConnectionFactory interface, DefaultMailConnectionFactory, ImapConnection/SmtpConnection.
- **Đủ scope manual local:** 03 M07 phân biệt interface (Week 1, 1h), implementation từng auth mode và task Test factory kết nối thật; 02 C3 yêu cầu chốt interface sớm để TV4/TV5 dùng trước OAuth2. §4 khai báo raw APIs/cấu hình, G4 R02–R10 quan sát caller-owned connect/close, H/B/T/F quan sát actual wrapper/HTTP, config có code/test. Không lấy local test làm handshake Google.
- **Điều kiện live còn giữ:** 4358-M.04 thuộc test factory thật AM-4361-M.02/C.02; cả hostname/TLS/timeout và hai auth mode vẫn OPEN tại criterion đó. G6 không bỏ yêu cầu cấu hình §4 hoặc đổi contract.
- **Phụ thuộc:** R01–R12 đủ lifecycle interface local, 4359/4360 implementation và 4361 test thật theo 03 M07; không lặp raw/pool local.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4358-M.01 | Ba raw APIs trả caller-owned objects/session, cấu hình preferred auth, timeout 10/15/30, TLS strict remote | MailConnectionFactoryTest 5/5, G2 auth config; không ghi credential vào properties | K |
| 4358-M.02 | Raw Store và Transport chưa connect, caller connect/close, Folder READ_ONLY/close; lỗi đóng scope | G4 R02–R10, actual JAR; pool bật nhưng raw không pool | M |
| 4358-M.03 | Wrapper retry chỉ trước mutation, pool/broken/idle/timeout/recovery, stale guard | B/T/F/G3 evidence; MailConnectionFaultAndRetryTest, MailboxRevisionChainIntegrationTest | M/K |
| 4358-M.04 | Remote hostname/TLS/timeout và cả auth mode Gmail thật | Shared chưa đạt: [AM-4361-M](#am-4361-m) 4361-M.02 và [AM-4361-C](#am-4361-c) 4361-C.02, task Test factory kết nối thật riêng ở 03 M07. Không suy loopback là STARTTLS remote. | S/L |

**Kết luận G6: PASS manual local.** 4358-M.01–03 đủ cho scope interface: R02–R10 trực tiếp CLI trên JAR G4, H/B/T/F HTTP từng request và source/test config; CLI không gán nhãn Postman. 4358-M.04 vẫn là live gate của AM-4361, chưa đạt. Không có manual run mới.

<a id="am-4359-m"></a>

### AM-4359 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** App Password connect IMAP/SMTP và lỗi. [Task §4 nguyên bản](Tasks%20Detail/AM-4359-BE-K%E1%BA%BFt-n%E1%BB%91i-b%E1%BA%B1ng-App-Password.md).
- **Source/đường thực thi:** DefaultMailConnectionFactory.testImap/createImapStore/createSmtpTransport, MailCredentials, AesCipher.
- **Đủ scope manual local:** 03 M07 phân biệt implementation App Password với test factory thật. §4 factory auth/connect/close/error, không yêu cầu POST link sai password (EP-G2 thuộc AM-4314) hoặc tạo account 2FA (AM-4396). G4 R02–R10 raw connect AES synthetic thật; H/F/G/T có IMAPS/SMTP, provider deny 422, timeout/network, close/recovery. F204/F205 là lỗi factory trên link hiện hữu, không gọi là wrong-link POST.
- **Điều kiện khác còn giữ:** 4359-M.03 shared H/L: AM-4314-M.03/M.04 cho actual POST link/probe, AM-4396-M.02/M.03 cho 2FA/standalone, AM-4361-M.02/C.02 cho remote TLS/timeout. Tất cả vẫn OPEN; không retry credential gate.
- **Phụ thuộc:** 4396 dedicated account/2FA/standalone; 4314 actual wrong-link; 4361 remote test. Local factory không cần chạy lại.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4359-M.01 | App Password decrypt/host-scoped authenticator, local IMAPS/SMTP connect | G2 factory config/tests, G4 R02–R08 real AES in-memory | M/K |
| 4359-M.02 | Caller-owned close, exception mapping và no leaked connection | G4 R09/R10 raw scope; F204/F205, T004–013 wrapper network/auth evidence | M |
| 4359-M.03 | Actual link wrong App Password và dedicated 2FA remote probe/TLS/timeout | Shared chưa đạt: [AM-4314-M](#am-4314-m) 4314-M.03/M.04 (actual POST link), [AM-4396-M](#am-4396-m) 4396-M.02/M.03 (2FA/standalone), [AM-4361-M](#am-4361-m) 4361-M.02 (remote TLS/timeout). F deny existing mailbox không phải wrong link; không thay live bằng local. | S/H/L |

**Kết luận G6: PASS manual local.** 4359-M.01–02 đủ scope implementation App Password, có actual local connect/close/error/recovery, không chỉ automated. 4359-M.03 thuộc các task link/PREP/test thật, vẫn giữ nguyên gate tại các criterion đích. Không có manual run mới.

<a id="am-4360-m"></a>

### AM-4360 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** OAuth2 XOAUTH2 connect/refresh/fallback. [Task §4 nguyên bản](Tasks%20Detail/AM-4360-BE-K%E1%BA%BFt-n%E1%BB%91i-b%E1%BA%B1ng-OAuth2-XOAUTH2.md).
- **Source/đường thực thi:** OAuth2ServiceImpl, DefaultAccessTokenProvider, MailCredentials, DefaultMailConnectionFactory.
- **Chưa đạt:** Successful refresh, rotated/reduced scope, invalid_grant suppression, fallback fail/active và XOAUTH2 local đã có automated; F invalid_grant local quan sát trực tiếp đã có. Còn manual flow được phép và consent/refresh/fallback/XOAUTH2/remote wrong-scope recovery thật.
- **Blocker và điều kiện tối thiểu:** L — môi trường OAuth dedicated được duyệt, không dùng token hiện hữu.
- **Phụ thuộc:** 4316/4317 consent/persist; 4361 remote connection; không lặp rotation/fallback local.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4360-M.01 | Fresh grant refresh/rotation, persisted revision, reject scope/invalid_grant không retry | F219–224 local invalid grant; G2/G3 actual JPA/mock tests, không lặp | M/K |
| 4360-M.02 | Fallback success/failed state/audit, APP_PASSWORD preferred dual, local XOAUTH2 config | G2/G3 factory/JPA tests, F local fault; không suy handshake Gmail | K |
| 4360-M.03 | Remote consent, refresh/fallback thực và IMAPS/SMTP XOAUTH2, wrong-scope recovery | AM-4317 và AM-4361 provider; dedicated OAuth consent/live thiếu | L |

**Còn thiếu để đóng:** 4360-M.03 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4361-m"></a>

### AM-4361 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Kiểm factory kết nối thật với hai auth mode. [Task §4 nguyên bản](Tasks%20Detail/AM-4361-BE-Test-factory-k%E1%BA%BFt-n%E1%BB%91i-th%E1%BA%ADt.md).
- **Source/đường thực thi:** RealGmailIntegrationTest, RealGmailXOAuth2IntegrationTest, MailConnectionFactory.
- **Chưa đạt:** Còn App Password + XOAUTH2 IMAPS/SMTP, self-send/hash và targeted cleanup trên Gmail thật.
- **Blocker và điều kiện tối thiểu:** L — dedicated account/opt-in đầy đủ, 5 live test không được suy PASS từ rehearsal.
- **Phụ thuộc:** 4396 App Password setup + 4316/4317 OAuth consent độc lập; remote IMAPS/SMTP → self-send → read-back → explicit targeted cleanup. Privacy chứng minh riêng.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4361-M.01 | Factory API và cấu hình/timeout/close local, raw standalone App Password code | G2/G3 tests, G4 R02–R10; verifyRawAppPassword source đã compile | K/M |
| 4361-M.02 | Hai mode Gmail thật: auth + count/special folder, self-send và exact bytes/hash | 5 live skips kế thừa, không có live request. Phải thỏa đầy đủ remote hostname/TLS/timeout và special-use/count của shared 4320-M.05, 4358-M.04, 4359-M.03; không thay bằng raw local. | L |
| 4361-M.03 | Read-back marker/UID, explicit targeted cleanup, privacy evidence và manual | Existing live lifecycle code; no live evidence, AM-4397 retained fixtures separate | L |

**Còn thiếu để đóng:** 4361-M.02 (L); 4361-M.03 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4396-m"></a>

### AM-4396 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Chuẩn bị Gmail test và entity/link. [Task §4 nguyên bản](Tasks%20Detail/AM-4396-BE-Chu%E1%BA%A9n-b%E1%BB%8B-Gmail-test-%2B-entity.md).
- **Source/đường thực thi:** GmailAccount, GmailAccountRepository, GmailAccountResponse, LinkAppPasswordRequest, AesCipher.
- **Chưa đạt:** Entity/DTO và raw standalone local đã có; source live đã compile. Còn dedicated Gmail 2FA/App Password, standalone Store.connect thật, live owner/privacy.
- **Blocker và điều kiện tối thiểu:** L — dedicated account có 2FA/App Password/consent được duyệt; không dùng Gmail cá nhân hoặc đọc secret.
- **Phụ thuộc:** Dedicated account và consent → 2FA/App Password → standalone connect; prerequisite cho live 4359/4361/4397.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4396-M.01 | Entity/query/DTO/validation/schema và owner, encryption/unique | V1–V9, G2 preservation, G3 JPA, contract implementation đã có | K |
| 4396-M.02 | Dedicated Gmail dev/demo, 2FA/App Password và placeholder docs không secret | dev-setup/.env.example source, tài khoản/quyền chưa được cấp; Giữ shared 4359-M.03 dedicated test account/2FA. | L |
| 4396-M.03 | Standalone Store.connect/close trước dùng module, raw local observed; actual remote | G4 R02–R05/R09; verifyRawAppPassword source opt-in; Gmail thật thiếu; Giữ shared 4359-M.03 standalone remote connect/close. | L |
| 4396-M.04 | Live ownership/response/log privacy | Local tests không thay dedicated link/standalone evidence | L |

**Còn thiếu để đóng:** 4396-M.02 (L); 4396-M.03 (L); 4396-M.04 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4397-m"></a>

### AM-4397 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Bảy mẫu MIME, DTO, attachment và cleanup. [Task §4 nguyên bản](Tasks%20Detail/AM-4397-BE-MIME-fixtures-%2B-Mail-DTO.md).
- **Source/đường thực thi:** MailDtos, MailServiceImpl, MIME parser/HtmlSanitizerService, RealGmailMimeFixturesTest.
- **Chưa đạt:** 7 mẫu local và live harness compile có metadata UID/UIDVALIDITY; còn 7 mẫu trên Gmail thật, privacy/targeted cleanup.
- **Blocker và điều kiện tối thiểu:** L — dedicated mailbox/consent, chỉ thư synthetic marker riêng, không đọc/xóa thư khác; không gửi lại khi chưa rõ outcome.
- **Phụ thuộc:** 4396 dedicated mailbox → mẫu riêng → read-back byte/hash/UID/UIDVALIDITY → manual inspection → explicit cleanup; không auto delete hay resend.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4397-M.01 | DTO aliases, attachment index/type/size, read/stream/bytes UTF-8 | G/J observations, 7 MIME local rehearsal, G2 DTO evidence | K/M |
| 4397-M.02 | Ít nhất 5 mẫu theo §4, hiện 7 loại, gửi/nhận dedicated Gmail thật | RealGmailMimeFixturesTest code; 1 live SKIP | L |
| 4397-M.03 | Kiểm subject/body chỉ synthetic, attachment bytes/hash, ghi UID/UIDVALIDITY để cleanup từng thư | G4 source thêm metadata safe, không auto-delete samples cần manual | L |
| 4397-M.04 | Manual đọc/tải/cleanup đúng marker, privacy response/log live | Dedicated Gmail + authorized Postman/Swing, không tự retry mutation. Bao gồm EP-M1 special-use/count đối chiếu Gmail web của demo TV4 (shared 4320-M.05) trong đúng mailbox test; không coi tạo 7 mẫu hoặc đọc raw Store là đủ. | L/N |

**Còn thiếu để đóng:** 4397-M.02 (L); 4397-M.03 (L); 4397-M.04 (L/N). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

## 30 manual Frontend

<a id="am-4346-m"></a>

### AM-4346 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Entry/Main/L&F/LoginFrame. [Task §4 nguyên bản](Tasks%20Detail/AM-4346-FE-Main_java-%2B-UIManager-system-L%26F-%2B-entry-m%E1%BB%9F-LoginFrame.md).
- **Source/đường thực thi:** Main, LoginFrame.setupEvents/updateResponsiveLayout/fetchPublicConfig/executeLogin.
- **Chưa đạt:** Còn native keyboard/DPI khác và các nhánh login §4; public startup đã quan sát.
- **Blocker và điều kiện tối thiểu:** H/I — native input trong cửa sổ fixture được xác nhận focus; auth nghiệp vụ cần phiên được phép. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4346-M.01 | Khởi động Main/System L&F/LoginFrame, public config maintenance/register controls | M01/S public startup observations, source; không lặp | M |
| 4346-M.02 | Native keyboard Tab/Enter, resize/DPI, placeholder, loading/error | SwingFeatureAcceptanceTest automated, C/D2/E1 không có native input hợp lệ | N |
| 4346-M.03 | Login→/me→forced-change hoặc MainFrame đúng role, expiry | Auth flow, ClientLiveServerE2ETest; không retry gate | N/H |

**Còn thiếu để đóng:** 4346-M.02 (N); 4346-M.03 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4347-m"></a>

### AM-4347 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** ApiClient HTTPS/JWT/session recovery. [Task §4 nguyên bản](Tasks%20Detail/AM-4347-FE-ApiClient-HTTPS-%2B-JWT.md).
- **Source/đường thực thi:** ApiClient TLS/newRequestBuilder/verifyLogin/execute, SessionStore, MainFrame.handleSessionExpired.
- **Chưa đạt:** Còn token hết hạn, mất kết nối khi đã login và recovery thực tế.
- **Blocker và điều kiện tối thiểu:** H — phiên Swing xác thực hợp lệ; public TLS/recovery đã đủ. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4347-M.01 | HTTPS cert đúng/sai, không insecure mặc định, public recovery | Public TLS evidence và AcceptanceRegressionTest; đã quan sát local | M/K |
| 4347-M.02 | Authorization header, DTO JSON/error Retry-After, methods/multipart/stream | ClientLiveServerE2ETest 16/16, ApiClient tests, G3 401 callback | K |
| 4347-M.03 | Login verified /me, expiry 401, mất kết nối sau login và recovery thật | Native Swing + authorized session; no fake JWT | N/H |

**Còn thiếu để đóng:** 4347-M.03 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4349-m"></a>

### AM-4349 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Login UI và error/loading/forced-change. [Task §4 nguyên bản](Tasks%20Detail/AM-4349-FE-M%C3%A0n-h%C3%ACnh-%C4%91%C4%83ng-nh%E1%BA%ADp.md).
- **Source/đường thực thi:** LoginFrame.executeLogin/handleLoginError/startRateLimitCountdown/handleLoginSuccess.
- **Chưa đạt:** Còn 429 countdown, locked/maintenance, forced-change, show/hide có dữ liệu và native keyboard.
- **Blocker và điều kiện tối thiểu:** H — phiên login nghiệp vụ được cấp phép; không retry credential alias bị gate. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4349-M.01 | Public maintenance banner, register visibility, validation/loading controls | Public observations, SwingFeatureAcceptanceTest | M/K |
| 4349-M.02 | Login OK→/me→correct role/forced-change, wrong/locked/maintenance/429 | Auth service + E2E, remaining exact native scenarios | N/H |
| 4349-M.03 | Native keyboard, show/hide password có dữ liệu và error text | Existing controls; C/D2/E1 input blocked, không retry | N/H |

**Còn thiếu để đóng:** 4349-M.02 (N/H); 4349-M.03 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4350-m"></a>

### AM-4350 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Đăng ký UI/validation. [Task §4 nguyên bản](Tasks%20Detail/AM-4350-FE-M%C3%A0n-h%C3%ACnh-%C4%91%C4%83ng-k%C3%BD.md).
- **Source/đường thực thi:** RegisterFrame.executeRegister/handleServerError/setRegistrationAllowed.
- **Chưa đạt:** Còn duplicate email/username, giới hạn 72 byte/confirm, stale submit 403 và đăng ký hợp lệ.
- **Blocker và điều kiện tối thiểu:** H — buổi nghiệm thu đăng ký được phép; không lặp gate. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4350-M.01 | Client username/email/password/confirm/72byte validation, focus/highlight | AcceptanceRegressionTest, SwingFeatureAcceptanceTest | K |
| 4350-M.02 | POST register 201 → LoginFrame, 409 duplicate username/email, 403 flag stale | BE D028–047 đủ local API nhưng không chứng minh FE journey; G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu. | N/H |
| 4350-M.03 | Native field input/keyboard, server error và recovery | C/D2/E1 desktop chưa phù hợp | N |

**Còn thiếu để đóng:** 4350-M.02 (N/H); 4350-M.03 (N). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4351-m"></a>

### AM-4351 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Ẩn/disable nút/form đăng ký theo flag. [Task §4 nguyên bản](Tasks%20Detail/AM-4351-FE-%E1%BA%A8n-n%C3%BAt-%C4%91%C4%83ng-k%C3%BD-theo-c%E1%BB%9D.md).
- **Source/đường thực thi:** LoginFrame.applyPublicConfig, RegisterFrame.applyPublicConfig/setRegistrationAllowed.
- **Chưa đạt:** Còn form có dữ liệu password nonempty và stale submit 403.
- **Blocker và điều kiện tối thiểu:** H — phiên form đăng ký được phép; public flag show/hide đã đủ. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4351-M.01 | Public flag show/hide register action | S public observations, SwingFeatureAcceptanceTest | M |
| 4351-M.02 | Form đang mở khi off, disable và clear nonempty password/confirm | EDT automated test, chưa native input; G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu. | N/H |
| 4351-M.03 | Stale submit 403, re-enable/recovery đúng | BE flag denial, FE regression; cần UI journey; G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu. | N/H |

**Còn thiếu để đóng:** 4351-M.02 (N/H); 4351-M.03 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4352-m"></a>

### AM-4352 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** MainFrame empty-state/sidebar/toolbar. [Task §4 nguyên bản](Tasks%20Detail/AM-4352-FE-MainFrame-khung-r%E1%BB%97ng.md).
- **Source/đường thực thi:** MainFrame.initComponents/buildToolbar/buildLeftColumn/updateUserUI/openAdminFrame.
- **Chưa đạt:** Còn resize 800x600/HiDPI/theme và Admin shell thực tế.
- **Blocker và điều kiện tối thiểu:** H — phiên USER/ADMIN Swing hợp lệ; component test không thay shell sau login. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4352-M.01 | Shell, toolbar/menu, sidebar/folder/card/status, empty-state | MainFrame source; layout cards/JList thay literal JTable/JTree theo contract đã kế thừa | K |
| 4352-M.02 | USER/ADMIN đúng visibility và role-loss/disposal | ClientLiveServerE2ETest/SwingFeatureAcceptanceTest; native authorized shell thiếu | N/H |
| 4352-M.03 | Resize 800x600, theme/HiDPI/keyboard/loading | Layout tests không thay native; E1 blocker | N |

**Còn thiếu để đóng:** 4352-M.02 (N/H); 4352-M.03 (N). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4353-m"></a>

### AM-4353 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Quản lý Gmail modal/cards/actions. [Task §4 nguyên bản](Tasks%20Detail/AM-4353-FE-Dialog-qu%E1%BA%A3n-l%C3%BD-Gmail.md).
- **Source/đường thực thi:** GmailManagerDialog.refreshAccounts/renderAccounts/setPreferredMethod/testConnection/renameAccount.
- **Chưa đạt:** Manager 409 refresh, generation/disposal và partial diagnostic đã có regression; còn native rename/test/preferred/fallback/gỡ method/stale refresh.
- **Blocker và điều kiện tối thiểu:** H — phiên mailbox synthetic hợp lệ; automated latest-wins không thay manual. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4353-M.01 | Cards metadata auth/status/created/method, add/remove/rename/verify và fallback | G2/G3 source, GmailMultiAuthClientTest, latest-wins EDT; literal JTable thay cards | K |
| 4353-M.02 | Native open→load→rename/test/preferred/remove/reload; error, conflict409, stale result | G3 single-flight/generation guard; native observation thiếu | N/H |
| 4353-M.03 | Fallback/NEEDS_REAUTH và reauth action khi config cho phép | G2/G3 local tests; actual dual mailbox journey, Google thật riêng AM-4355; G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN. | N/H |

**Còn thiếu để đóng:** 4353-M.02 (N/H); 4353-M.03 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4354-m"></a>

### AM-4354 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Wizard App Password và link. [Task §4 nguyên bản](Tasks%20Detail/AM-4354-FE-Form-th%C3%AAm-Gmail-App-Password.md).
- **Source/đường thực thi:** GmailManagerDialog.openAppPasswordWizard, ApiClient.linkGmailWithAppPassword, GmailAccountController.
- **Chưa đạt:** Còn sai password, UPSERT/quota deny/new method và Gmail thật.
- **Blocker và điều kiện tối thiểu:** H/L — wizard trong phiên authorized + Gmail chuyên test riêng, không retry gate. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4354-M.01 | Wizard hướng dẫn 2FA, validation/precheck, password clear, feature flag/UPsert flow | GmailMultiAuthClientTest, G3 guard wizard/session; code | K |
| 4354-M.02 | Native valid/invalid field, quota/UPSERT/create vs update, error and close/recovery | Need authorized session, actual link credential not retried; G5: GmailManagerDialog dùng FeatureAdmission ticket, kiểm sau GET và trước link dispatch; off/on không hồi sinh submit cũ, config null fail-closed, lỗi cũ không ghi đè trạng thái mới; re-enable theo config mới. Giữ clear password, session/wizard guards; không có liên kết Gmail live hay native input mới. SwingFeatureAcceptanceTest 16/16 tự động. | N/H |
| 4354-M.03 | Dedicated Gmail 2FA/App Password actual link/probe | AM-4314/4396 live; not GreenMail | L |

**Còn thiếu để đóng:** 4354-M.02 (N/H); 4354-M.03 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4355-m"></a>

### AM-4355 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Nút Google OAuth2 và consent/poll. [Task §4 nguyên bản](Tasks%20Detail/AM-4355-FE-N%C3%BAt-li%C3%AAn-k%E1%BA%BFt-Google-OAuth2.md).
- **Source/đường thực thi:** GmailManagerDialog.startOAuth2, MainFrame.initiateOAuth2Flow, MailboxViewModel.pollForOAuthLink.
- **Chưa đạt:** Còn browser consent/cancel/reauth Google thực từ Swing.
- **Blocker và điều kiện tối thiểu:** H/L — dedicated account/consent authorized; loopback HTTP không thay Swing/live. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4355-M.01 | Authorize URL/PKCE, polling cancel/expiry/ownership and refresh metadata | Local HTTP F/G + MailboxRegressionTest, G2/G3 guards; G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN. | K/M |
| 4355-M.02 | Native browser consent/cancel/re-auth, stale callback/session transition | No validated desktop and authorized consent; G6 guard code/regression đã có, không thay native/consent. | N/L |

**Còn thiếu để đóng:** 4355-M.02 (N/L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4356-m"></a>

### AM-4356 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Xóa liên kết Gmail với xác nhận. [Task §4 nguyên bản](Tasks%20Detail/AM-4356-FE-N%C3%BAt-xo%C3%A1-li%C3%AAn-k%E1%BA%BFt-Gmail.md).
- **Source/đường thực thi:** GmailManagerDialog.unlinkAccount/handleRemoveMethod, ApiClient, GmailAccountController.
- **Chưa đạt:** Còn No/Yes và refresh, revoke/IDOR.
- **Blocker và điều kiện tối thiểu:** H/L — phiên mailbox fixture hợp lệ, chỉ xóa link hy sinh; Google thật riêng. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4356-M.01 | Confirm + exact selected account, single-flight, success refresh/409 conflict | G3 source; BE F081–084/G revoke fault; not native | K |
| 4356-M.02 | Native No no mutation/Yes removes intended link, other owner untouched, UI refresh | Native session fixture required | N/H |
| 4356-M.03 | Revoke Google actual where OAuth linked | AM-4318/4319 live still independent | L |

**Còn thiếu để đóng:** 4356-M.02 (N/H); 4356-M.03 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4357-m"></a>

### AM-4357 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Ẩn nút thêm/link theo config. [Task §4 nguyên bản](Tasks%20Detail/AM-4357-FE-%E1%BA%A8n-n%C3%BAt-th%C3%AAm-theo-c%E1%BB%9D.md).
- **Source/đường thực thi:** GmailManagerDialog.applyPublicConfig/updateWizardFlags, MainFrame.applyPublicConfig.
- **Chưa đạt:** Còn manager/wizard đang mở khi App Password off, reauth/Google hidden theo cờ.
- **Blocker và điều kiện tối thiểu:** H — phiên Swing hợp lệ và flags fixture; restore snapshot sau nghiệm thu. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4357-M.01 | Both link flags drive button/wizard disabled state, null config fail closed | SwingFeatureAcceptanceTest, G3 loading/operationPending guards | K |
| 4357-M.02 | Native transition manager and wizard open, flag off clears password and stale submit rejected | Need UI input and allowed flag fixture; public controls alone not enough; G5: GmailManagerDialog dùng FeatureAdmission ticket, kiểm sau GET và trước link dispatch; off/on không hồi sinh submit cũ, config null fail-closed, lỗi cũ không ghi đè trạng thái mới; re-enable theo config mới. Giữ clear password, session/wizard guards; không có liên kết Gmail live hay native input mới. SwingFeatureAcceptanceTest 16/16 tự động.; G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN. | N/H |

**Còn thiếu để đóng:** 4357-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4362-m"></a>

### AM-4362 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Cây mailbox/folder, count và chọn tài khoản. [Task §4 nguyên bản](Tasks%20Detail/AM-4362-FE-C%C3%A2y-Gmail-%2B-folder-tr%C3%A1i-MainFrame.md).
- **Source/đường thực thi:** MainFrame.renderAccountCards/selectGmailAccount/loadFolders, GmailAccountDto.requiresReauthBeforeMail.
- **Chưa đạt:** Còn hai mailbox cùng owner, fallback, delay/race/IDOR và hitbox DPI.
- **Blocker và điều kiện tối thiểu:** H — phiên mailbox fixture hợp lệ với hai mailbox và quyền rõ ràng. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4362-M.01 | Account/method/status, folder count, selection/owner, empty/loading | Source + E2E; cards/list equivalent contract inherited | K |
| 4362-M.02 | Two own mailboxes, fallback, wrong owner, delayed result cannot cross mailbox | G2/G3 guards, BE ownership local; native authorized journey missing | N/H |
| 4362-M.03 | Native DPI/hitbox/keyboard | Desktop condition not changed | N |

**Còn thiếu để đóng:** 4362-M.02 (N/H); 4362-M.03 (N). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4363-m"></a>

### AM-4363 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Bảng mail và tương tác row. [Task §4 nguyên bản](Tasks%20Detail/AM-4363-FE-B%E1%BA%A3ng-danh-s%C3%A1ch-mail.md).
- **Source/đường thực thi:** MainFrame.buildMiddleColumn/loadMailDetail, MailSummaryCellRenderer, MailboxViewModel.
- **Chưa đạt:** Còn double-click/Enter native, keyboard, subject dài/HiDPI.
- **Blocker và điều kiện tối thiểu:** H — phiên Swing và native input focus đúng; không suy từ EDT. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4363-M.01 | Row from/subject/date/attachment/unread, noneditable and flags | SwingFeatureAcceptanceTest/E2E; JList rather than JTable per existing implementation | K |
| 4363-M.02 | Enter/double click opens exactly intended detail, selection/multi-select behavior | G4 Enter single-only and detail target test; native still missing | N/H |
| 4363-M.03 | Long subject/HiDPI/navigation and mark-read refresh | Existing renderer, native missing | N |

**Còn thiếu để đóng:** 4363-M.02 (N/H); 4363-M.03 (N). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4364-m"></a>

### AM-4364 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Phân trang/tìm kiếm/filter. [Task §4 nguyên bản](Tasks%20Detail/AM-4364-FE-Ph%C3%A2n-trang-%2B-t%C3%ACm-ki%E1%BA%BFm-mail.md).
- **Source/đường thực thi:** MainFrame.loadMailsAtSize, MailboxViewModel.SearchCriteria/selectNewItems.
- **Chưa đạt:** Còn Next/Prev/Load More/search/filter, đổi config page size và race trực tiếp bản cuối.
- **Blocker và điều kiện tối thiểu:** H — phiên mailbox hợp lệ có >50 thư synthetic và fault delay độc lập. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4364-M.01 | Page 10/20/50, q/filter, deterministic order/dedup/UIDVALIDITY, latest-wins | F036–044 BE, MailboxRegressionTest/SwingFeatureAcceptanceTest | K/M |
| 4364-M.02 | Native Next/Prev/Load More, search/filter, config page-size changes and delayed/fail recovery | Need interactive authorized fixture; do not repeat BE paging | N/H |

**Còn thiếu để đóng:** 4364-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4365-m"></a>

### AM-4365 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Panel chi tiết/MIME/attachments. [Task §4 nguyên bản](Tasks%20Detail/AM-4365-FE-Panel-chi-ti%E1%BA%BFt-mail.md).
- **Source/đường thực thi:** MainFrame.displayMailDetail/renderCurrentBody/buildAttachmentChip, HtmlSanitizerService.
- **Chưa đạt:** Còn scroll chip bản sửa, Cc/toggle, HTML remote image/sanitizer và resize trực tiếp.
- **Blocker và điều kiện tối thiểu:** H — phiên thư synthetic hợp lệ, UI nhỏ/native input; không đọc mail thật. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4365-M.01 | Header From/To/Cc/Date/Subject, HTML/plain, remote blocked by default, attachment metadata | G/J BE MIME, SwingFeatureAcceptanceTest/AcceptanceRegressionTest; G3 identity guard | K |
| 4365-M.02 | Native toggle HTML/plain/remote image, scroll chip/header/resize and detail refresh | Need actual detail from fixture and desktop; no loading external image without consent | N/H |

**Còn thiếu để đóng:** 4365-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4366-m"></a>

### AM-4366 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** SwingWorker/loading/race/network. [Task §4 nguyên bản](Tasks%20Detail/AM-4366-FE-SwingWorker-%2B-tr%E1%BA%A1ng-th%C3%A1i-t%E1%BA%A3i.md).
- **Source/đường thực thi:** MainFrame.WorkerCoordinator, Login/Register/Gmail Manager/Compose/AdminFrame workers.
- **Chưa đạt:** Còn trực tiếp stale response, session/detail/dispose, delayed/error/recovery.
- **Blocker và điều kiện tối thiểu:** H/F — phiên hợp lệ và fault delay có kiểm soát; regression generation không thay quan sát. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4366-M.01 | Async loading/error/latest-wins, session/disposed guards, no stale mutation continuation | MailboxRegressionTest, G3 guards, G4 Admin window generation/single-flight; G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN. | K |
| 4366-M.02 | Native delayed response A→B, logout/expiry/dispose, network fail and recovery | EDT/E2E not manual, authorized desktop session missing; G6 OAuth2 worker session generation/close guard; không thay native transition. | N/H |

**Còn thiếu để đóng:** 4366-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4367-m"></a>

### AM-4367 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Dialog soạn thư/recipient validation/draft. [Task §4 nguyên bản](Tasks%20Detail/AM-4367-FE-Dialog-so%E1%BA%A1n-mail.md).
- **Source/đường thực thi:** ComposeDialog/EmailChipInput, ComposeValidation, MainFrame.openComposeDialog.
- **Chưa đạt:** Reply-To/self/Bcc/dedup/thread/HTML-only đã có automated. Còn native input comma/semicolon/Enter, invalid/limit recipient/body, draft No/Yes, reply/forward thật.
- **Blocker và điều kiện tối thiểu:** H/I — local component có thể kiểm phần không account, nhưng probe C dừng focus; luồng đầy đủ cần phiên Swing hợp lệ. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4367-M.01 | Modal From, To/Cc/Bcc delimiters/validation, subject/body/html, reply/thread, attachment cap | G2 reply-To/thread/HTML-only regression, G3 guards, AcceptanceRegressionTest | K |
| 4367-M.02 | Native commas/semicolons/Enter/chip removal, invalid/limit/body/keyboard, draft No/Yes | Desktop input not confirmed; public local Compose only earlier partial | N |
| 4367-M.03 | Reply/reply-all/forward intended thread and send journey, current config | Need authorized mailbox/Swing, AM-4369 related; G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu. | N/H |

**Còn thiếu để đóng:** 4367-M.02 (N); 4367-M.03 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4368-m"></a>

### AM-4368 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Chọn file đính kèm/size/DnD. [Task §4 nguyên bản](Tasks%20Detail/AM-4368-FE-Ch%E1%BB%8Dn-file-%C4%91%C3%ADnh-k%C3%A8m.md).
- **Source/đường thực thi:** ComposeDialog.chooseAttachments/addAttachmentFiles/drop/refreshAttachmentChips, ComposeValidation.AttachmentStamp.
- **Chưa đạt:** Attachment snapshot/remove theo file đã sửa; còn native chooser cancel/multi-select/DnD/reject, size và sửa file lúc gửi.
- **Blocker và điều kiện tối thiểu:** H/I — file synthetic dưới root fixture và native focus được xác nhận; không duyệt file cá nhân. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4368-M.01 | Multi-select/DnD, size cap, duplicate/remove and file stamp recheck before send | F/G2 code and regression; metadata check not full lock | K |
| 4368-M.02 | Native chooser cancel/multi-select/DnD, oversize, file changed, focus/DPI | E1 desktop condition not changed; synthetic files only | N |

**Còn thiếu để đóng:** 4368-M.02 (N). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4369-m"></a>

### AM-4369 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Gửi mail qua UI, loading và failure. [Task §4 nguyên bản](Tasks%20Detail/AM-4369-FE-G%E1%BB%ADi-mail-qua-API.md).
- **Source/đường thực thi:** ComposeDialog.performSend, ApiClient.sendMailMultipart, MailServiceImpl.sendMail.
- **Chưa đạt:** Còn submit thật, 503/uncertain/partial, giữ draft, xác nhận resend và callback stale trực tiếp.
- **Blocker và điều kiện tối thiểu:** H/F — phiên mailbox hợp lệ và fault fixture; không tự retry gửi mất phản hồi. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4369-M.01 | Validation/config precheck, freeze inputs, worker/progress, success ack closes | AcceptanceRegressionTest, SwingFeatureAcceptanceTest, G3 session guard; E2E | K |
| 4369-M.02 | SMTP local error/pre-mutation503/uncertain delivery, preserve draft, no automatic retry | F actual provider fault BE; G3 automated Composer; native outcome missing; G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu. | N/H |
| 4369-M.03 | Native send success, loss reply/confirmation resend, session/close/stale callback | Need authorized UI, never resend ambiguous delivery to manufacture evidence; G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu. | N/H |

**Còn thiếu để đóng:** 4369-M.02 (N/H); 4369-M.03 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4370-m"></a>

### AM-4370 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Disable soạn khi feature flag tắt. [Task §4 nguyên bản](Tasks%20Detail/AM-4370-FE-Disable-So%E1%BA%A1n-khi-b%E1%BB%8B-t%E1%BA%AFt.md).
- **Source/đường thực thi:** MainFrame.applyPublicConfig/sendEnabled, ComposeDialog.applyPublicConfig/performSend.
- **Chưa đạt:** Còn dynamic disable/re-enable MainFrame/dialog đang mở, config outage, stale submit.
- **Blocker và điều kiện tối thiểu:** H/F — phiên UI được phép, flag snapshot/restore, quan sát nút trực tiếp. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4370-M.01 | Flag off/config unavailable disable compose/send/reply; fresh config before mutation | SwingFeatureAcceptanceTest, G2/G3 code | K |
| 4370-M.02 | Native off/on while dialog open, outage/recovery and stale submit 403 | Authorized flags/desktop; no need repeat server flag denial; G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu. | N/H |

**Còn thiếu để đóng:** 4370-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4371-m"></a>

### AM-4371 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** AdminFrame 4 tab/role authorization. [Task §4 nguyên bản](Tasks%20Detail/AM-4371-FE-Admin-Khung-AdminFrame-4-tab.md).
- **Source/đường thực thi:** AdminFrame constructor/tabbedPane/handleAdminError, MainFrame.openAdminFrame/updateUserUI.
- **Chưa đạt:** Còn Admin login/shell 4 tab và role loss trực tiếp.
- **Blocker và điều kiện tối thiểu:** H — phiên Admin được cấp quyền; credential alias bị gate không được thử lại. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4371-M.01 | Admin-only shell, 4 tabs, role-gate/401/403; no Gmail content | AdminModuleClientTest/E2E, G4 current-window/role guard | K |
| 4371-M.02 | Native authorized Admin shell and role-loss closes window; USER cannot open | Need real authorized role session; no fake session | N/H |

**Còn thiếu để đóng:** 4371-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4372-m"></a>

### AM-4372 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Danh sách user/filter/paging Admin. [Task §4 nguyên bản](Tasks%20Detail/AM-4372-FE-Admin-Tab-danh-s%C3%A1ch-user.md).
- **Source/đường thực thi:** AdminFrame.loadUsers/applyUsersPage/updateSelectedUserButtons.
- **Chưa đạt:** Còn filter/role/status/search/paging và race trực tiếp.
- **Blocker và điều kiện tối thiểu:** H — phiên Admin hợp lệ, dữ liệu synthetic nhiều trang. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4372-M.01 | Search/role/enabled/sort/paging, latest request, clamp/empty/error | SwingFeatureAcceptanceTest, AdminModuleClientTest, G4 disposed callback | K |
| 4372-M.02 | Native filters and pagination, delay/race and metadata refresh | Authorized admin fixture and desktop | N/H |

**Còn thiếu để đóng:** 4372-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4373-m"></a>

### AM-4373 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Nút quản trị user/create/reset/lock/delete. [Task §4 nguyên bản](Tasks%20Detail/AM-4373-FE-Admin-C%C3%A1c-n%C3%BAt-thao-t%C3%A1c-user.md).
- **Source/đường thực thi:** AdminFrame.openCreateUserDialog/toggleLockSelectedUser/changeSelectedUserRole/resetSelectedUserPassword/deleteSelectedUser.
- **Chưa đạt:** Còn thao tác các dialog, self/last-admin, reset/temporary password an toàn.
- **Blocker và điều kiện tối thiểu:** H — fixture Admin hợp lệ và target hy sinh; không điều khiển credential đã chặn. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4373-M.01 | Create/lock/unlock/role/reset/delete validation and self/last-admin, confirmed target | AdminIntegrationTest/E2E; G4 exact selected DTO, single-flight, session/role recheck | K |
| 4373-M.02 | Temporary password only one-time current authorized dialog, no stale response or clipboard action after lost session | G4 source guard/clear field; not observing real temporary secret | K |
| 4373-M.03 | Native No/Yes, typed delete confirmation, reset/forced-change and role/self/last-admin exact behavior | Auth/credential and native blockers; no bootstrap change | N/H |

**Còn thiếu để đóng:** 4373-M.03 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4374-m"></a>

### AM-4374 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Tab settings 9 key, range, flag, save. [Task §4 nguyên bản](Tasks%20Detail/AM-4374-FE-Admin-Tab-c%E1%BA%A5u-h%C3%ACnh-h%E1%BB%87-th%E1%BB%91ng.md).
- **Source/đường thực thi:** AdminFrame.loadSettings/saveSettings, AdminViewModel, AdminServiceImpl.updateSettings.
- **Chưa đạt:** Còn UI settings, range/quota/legacy 0, confirm maintenance, metadata sau save.
- **Blocker và điều kiện tối thiểu:** H — phiên Admin hợp lệ, restore snapshot; server flag evidence không thay GUI. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4374-M.01 | Nine keys, ranges, at least one link method, legacy quota0, settings metadata/consumer | AdminIntegrationTest/ClientLiveServerE2ETest#16, SwingFeatureAcceptanceTest; G4 session guard after confirm | K |
| 4374-M.02 | Native maintenance No/Yes, invalid spinner, save/load and flags effective | Need authorized admin UI and restored fixture; no repeated BE tests merely counts | N/H |

**Còn thiếu để đóng:** 4374-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4375-m"></a>

### AM-4375 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Stats tab/KPI/chart/refresh. [Task §4 nguyên bản](Tasks%20Detail/AM-4375-FE-Admin-Tab-th%E1%BB%91ng-k%C3%AA.md).
- **Source/đường thực thi:** AdminFrame.loadStats/applyStats, AdminChartsPanel, AdminServiceImpl.getStats.
- **Chưa đạt:** Còn quan sát KPI/chart/refresh, empty/error và dữ liệu tương ứng.
- **Blocker và điều kiện tối thiểu:** H — phiên Admin hợp lệ, stats synthetic đối chiếu. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4375-M.01 | KPI/user/Gmail/registration/failed login, chart and current user count, read-only | AdminModuleClientTest, prior HTTP stats; G4 stale window guard | K |
| 4375-M.02 | Native refresh/empty/error and compare actual counters/chart | Authorized fixture data and desktop | N/H |

**Còn thiếu để đóng:** 4375-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4376-m"></a>

### AM-4376 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Audit tab filter/page/export. [Task §4 nguyên bản](Tasks%20Detail/AM-4376-FE-Admin-Tab-nh%E1%BA%ADt-k%C3%BD.md).
- **Source/đường thực thi:** AdminFrame.loadLogs/exportLogsCsv, ApiClient.adminExportLogsCsvToFile, AdminServiceImpl.exportLogsCsv.
- **Chưa đạt:** Còn filter/paging/clamp/error/export chooser trực tiếp.
- **Blocker và điều kiện tối thiểu:** H — phiên Admin được phép, CSV synthetic không secret và root download riêng. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4376-M.01 | Filter user/action/date, paging/clamp/error, CSV escaped/no-overwrite/generation guard | F export regression, SwingFeatureAcceptanceTest, AdminModuleClientTest; G4 window guard | K |
| 4376-M.02 | Native filter and chooser/cancel, exact exported file in isolated destination, no secret | Authorized admin UI, safe file root | N/H |

**Còn thiếu để đóng:** 4376-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4393-m"></a>

### AM-4393 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Xóa từ detail/context/multi-select/confirm. [Task §4 nguyên bản](Tasks%20Detail/AM-4393-FE-N%C3%BAt-Xo%C3%A1-trong-chi-ti%E1%BA%BFt-%2B-context-menu.md).
- **Source/đường thực thi:** MainFrame.deleteCurrentDetail/deleteSelectedMails/deleteMailTargets, MailboxViewModel, AttachmentCleanupService.
- **Chưa đạt:** Còn multi-select, Delete keyboard, permanent No, mất phản hồi/uncertain và 503 pre-mutation.
- **Blocker và điều kiện tối thiểu:** H/F — phiên mailbox synthetic, thư hy sinh và fault fixture, không retry mutation. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4393-M.01 | Detail exact UID versus context/keyboard batch, no accidental multi-delete | G4 detailMutationTarget, Enter only single; regression | K |
| 4393-M.02 | Confirm Trash vs permanent, No no request, targeted UID/UIDVALIDITY, refresh and error | G3 batch and cleanup guard; BE C/D/E mutation evidence | K |
| 4393-M.03 | Native multi-select/context/Delete, No/Yes, 503 and uncertain stop/refresh | Authorized fixture, no retry mutation; desktop blocker | N/H |

**Còn thiếu để đóng:** 4393-M.03 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4395-m"></a>

### AM-4395 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Toggle sao/seen và đồng bộ UI. [Task §4 nguyên bản](Tasks%20Detail/AM-4395-FE-Toggle-sao-%2B-%C4%91%C3%A1nh-d%E1%BA%A5u-ch%C6%B0a-%C4%91%E1%BB%8Dc.md).
- **Source/đường thực thi:** MainFrame.toggleStarForMail/updateMailFlags/markCurrentMailUnread.
- **Chưa đạt:** Còn keyboard/rapid toggle, stale UID/session, error/rollback/reload trực tiếp.
- **Blocker và điều kiện tối thiểu:** H/F — phiên mailbox synthetic có fault controlled, read-back; C backend không thay Swing. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4395-M.01 | Star direct row and detail icon, unread refresh, UIDVALIDITY and pending flag serialization | SwingFeatureAcceptanceTest, G3 ABA guard and worker code | K |
| 4395-M.02 | Native rapid toggle, keyboard, stale mailbox/error reload and read-back | Authorized desktop session; no duplicate mutation | N/H |

**Còn thiếu để đóng:** 4395-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4399-m"></a>

### AM-4399 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Tải attachment/unique name/path/size. [Task §4 nguyên bản](Tasks%20Detail/AM-4399-FE-T%E1%BA%A3i-file-%C4%91%C3%ADnh-k%C3%A8m-v%E1%BB%81-Downloads-%2B-ch%E1%BB%91ng-tr%C3%B9ng-t%C3%AAn.md).
- **Source/đường thực thi:** FileUtil.prepareAttachmentDirectory/saveUnique, MainFrame.downloadSingleAttachment, ApiClient download, SessionStore.
- **Chưa đạt:** Còn chooser/size/chip bản cuối, traversal/permission/network/part cleanup/default Downloads trực tiếp.
- **Blocker và điều kiện tối thiểu:** H/I — phiên mailbox synthetic, thư mục cô lập, không mở Downloads thật. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4399-M.01 | Default Downloads/MailApp/account, unique names, stream/bytes/hash, traversal/temporary cleanup | G/J exact bytes, F/G3 tests, isolated paths only | K/M |
| 4399-M.02 | Native chooser/default path, permission/network failure, chip/status and part cleanup | Need desktop and synthetic root, do not read real Downloads | N/H |

**Còn thiếu để đóng:** 4399-M.02 (N/H). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4400-m"></a>

### AM-4400 — Manual test pass (Postman cho BE, chạy Swing cho FE)

- **Yêu cầu gốc:** Auto-open/cache và cảnh báo executable. [Task §4 nguyên bản](Tasks%20Detail/AM-4400-FE-T%E1%BB%B1-m%E1%BB%9F-file-sau-khi-t%E1%BA%A3i-%2B-nh%E1%BB%9B-file-%C4%91%C3%A3-t%E1%BA%A3i.md).
- **Source/đường thực thi:** MainFrame.openDownloadedAttachment/downloadSingleAttachment, SessionStore.findDownloadedAttachment, ClientSafetyUtil.
- **Chưa đạt:** Còn viewer thật, request counter=0 cache hit, cache invalidation owner/file change, cancel executable/unsupported viewer.
- **Blocker và điều kiện tối thiểu:** H/I — phiên mailbox/file fixture và OS viewer vô hại; không chạy executable. E1: foreground và native hit-test Win32 không thuộc fixture. Chỉ tính manual khi window PID/focus và kết quả từng thao tác được xác nhận, phiên auth được cấp quyền độc lập.
- **Phụ thuộc:** N cho thao tác native; H cho nghiệp vụ hậu login/được bảo vệ nếu có. Bắt đầu từ state hiện hành → thao tác xác nhận → response/UI state → recovery/đối chiếu; không dùng EDT thay native.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4400-M.01 | Cache owner/session/UIDVALIDITY/index/file attributes, recheck after warning | F/G3 source/AcceptanceRegressionTest; no TOCTOU claim | K |
| 4400-M.02 | Native safe OS viewer, cache hit request counter0, changed file/owner invalidation | Authorized synthetic file, desktop missing | N/H |
| 4400-M.03 | Dangerous executable warning No, unsupported viewer/error path | Never execute dangerous file; native confirmation required | N |

**Còn thiếu để đóng:** 4400-M.02 (N/H); 4400-M.03 (N). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

## Contract — 5 OPEN, 1 PASS theo phê duyệt (giữ lịch sử ID)

<a id="am-4323-c"></a>

### AM-4323 — Contract đúng: method, path, request/response schema, error code khớp spec

- **Yêu cầu trước phê duyệt:** Audit SMTP literal To/subject so với metadata an toàn và hợp đồng gửi. [Task §4 hiện hành](Tasks%20Detail/AM-4323-BE-G%E1%BB%ADi-mail-SMTP-multipart.md).
- **Source/đường thực thi:** MailController, MailServiceImpl.sendMail, audit V8, §4 SMTP; 02 EP-M5 metadata-only.
- **Chưa đạt:** Chỉ còn Gmail live cả hai mode (C.04). Metadata-only đã được phê duyệt, C.03 A.
- **Blocker và điều kiện tối thiểu:** L — Gmail thật theo phạm vi TV5, giữ shared 4323-M.05, không ghi nội dung nhạy cảm.
- **Phụ thuộc:** Mailbox/SMTP provider được phép, 4361/4397 fixture; read-back trước kết luận outcome.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4323-C.01 | Legacy fields gmailAccountId/to/cc/bcc/subject/body/isHtml/files[] and modern payload, 200 message/sentAt, ownership/flag/validation/error codes | H027/H140/F013/F069–070 and tests; safe additive contract | K/M |
| 4323-C.02 | MimeMessage UTF-8, MIME files, actual local delivery/read-back, scoped Transport close | F/G/J, G4 raw R06–R10 | M/K |
| 4323-C.03 | Audit SEND_MAIL metadata-only thay literal To/subject | Chủ dự án phê duyệt 26/11/2026; §4 đã sửa, H093/F013–014 và regression kế thừa chứng minh masked sender/counts, không raw/hash | A |
| 4323-C.04 | Gmail true delivery where required by module | No live account/consent. Giữ đủ shared 4323-M.05: SMTP Gmail thật cả App Password/XOAUTH2 (02 C2-TV5), delivery/read-back/attachment và scope manual được duyệt, không thay bằng local. | L |

**Còn thiếu để đóng:** 4323-C.04 (L), gồm shared 4323-M.05. C.03 A đã đạt. **OPEN.**

<a id="am-4340-c"></a>

### AM-4340 — Contract đúng: method, path, request/response schema, error code khớp spec

- **Yêu cầu hiện hành:** Schema MySQL và seed Admin theo §4. [Task §4 đã phê duyệt](Tasks%20Detail/AM-4340-BE-Schema-MySQL-%2B-seed-Admin.md).
- **Source/đường thực thi:** V1__init.sql through V9, bootstrap initializer, SystemSettingService.
- **Đã đạt:** V1–V9 additive, native preservation, seed Admin BCrypt/forced-change từ cấu hình, không console plaintext; owner phê duyệt 26/11/2026.
- **Blocker:** Không còn trong phạm vi criterion; phê duyệt A và evidence MySQL/seed K/M/K kế thừa. Không suy thành kiểm DB production.
- **Phụ thuộc:** Evidence MySQL cô lập đã có; không chạy lại migration hay bootstrap.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4340-C.01 | DB charset/collation and users/gmail_accounts/system_settings/audit_logs, FK/unique/types; nine defaults | Native V1–V9 G3, V8→V9 G2 preserved synthetic rows; extra outbox additive | K/M |
| 4340-C.02 | Seed admin role/mustChangePassword, BCrypt, no plaintext console | Configured bootstrap (fixture supplies random password) and hash-shape evidence; do not change existing bootstrap | K |
| 4340-C.03 | Bootstrap từ cấu hình, BCrypt/forced-change, không plaintext console, schema additive thay literal fixed password/exact schema | Chủ dự án phê duyệt 26/11/2026; §4 đã sửa, K/M/K của C.01/C.02 đủ, không seed lại | A |

**PASS — 26/11/2026:** C.01 K/M, C.02 K, C.03 A; đóng criterion contract và task. Không tạo evidence manual/live mới.

<a id="am-4361-c"></a>

### AM-4361 — Contract đúng: method, path, request/response schema, error code khớp spec

- **Yêu cầu gốc:** Factory kết nối Gmail thật App Password/XOAUTH2. [Task §4 nguyên bản](Tasks%20Detail/AM-4361-BE-Test-factory-k%E1%BA%BFt-n%E1%BB%91i-th%E1%BA%ADt.md).
- **Source/đường thực thi:** RealGmailIntegrationTest, RealGmailXOAuth2IntegrationTest.
- **Chưa đạt:** Hai auth mode live vẫn thiếu, local 7 MIME không chứng minh handshake.
- **Blocker và điều kiện tối thiểu:** L — dedicated account được phép cả hai mode và opt-in live.
- **Phụ thuộc:** 4396 App Password setup + 4316/4317 OAuth consent độc lập; remote IMAPS/SMTP → self-send → read-back → explicit targeted cleanup. Privacy chứng minh riêng.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4361-C.01 | Factory interfaces/configuration and local auth/lease/close | G2/G3, G4 raw observation/standalone source | K/M |
| 4361-C.02 | Actual Gmail App Password and XOAUTH2 IMAPS/SMTP auth; synthetic self-send/download/hash/targeted cleanup | 2+2 live SKIP; not local simulation; Giữ đầy đủ remote hostname/TLS/timeout và cả hai auth mode tại 4358-M.04/4359-M.03. | L |

**Còn thiếu để đóng:** 4361-C.02 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4391-c"></a>

### AM-4391 — Contract đúng: method, path, request/response schema, error code khớp spec

- **Yêu cầu hiện hành:** Trash semantics và DELETE 204/xóa vĩnh viễn. [Task §4 đã phê duyệt](Tasks%20Detail/AM-4391-BE-Xo%C3%A1-mail-qua-IMAP.md).
- **Source/đường thực thi:** MailServiceImpl.deleteMail/moveByUid, MailController, GmailFolderResolver.
- **Chưa đạt:** Chỉ còn Gmail Trash/All Mail thật và read-back (C.03). Explicit permanent/UID-targeted đã được phê duyệt C.02 A.
- **Blocker và điều kiện tối thiểu:** L — nghiệm thu Gmail Trash/All Mail thật trên dedicated mailbox được phép; giữ guard.
- **Phụ thuộc:** Dedicated target và capability Gmail; explicit confirmation/UID-targeted bắt buộc.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4391-C.01 | Ownership/READ_WRITE/UID lookup, 404/422/502, audit, DELETE204/default and optional compatibility | C/D/E mutation evidence, existing targeted implementation | K/M |
| 4391-C.02 | Explicit permanent=true trong Trash và UIDVALIDITY/UID MOVE/UIDPLUS thay xóa ngầm/global expunge | Chủ dự án phê duyệt 26/11/2026; §4 đã sửa, F045–057/C/D/E kế thừa; giữ guard | A |
| 4391-C.03 | Actual Gmail Trash/All Mail semantics and read-back | Dedicated mailbox live not authorized | L |

**Còn thiếu để đóng:** 4391-C.03 (L). C.02 A đã đạt. **OPEN.**

<a id="am-4396-c"></a>

### AM-4396 — Contract đúng: method, path, request/response schema, error code khớp spec

- **Yêu cầu gốc:** Gmail test account/2FA/link/entity/Store.connect. [Task §4 nguyên bản](Tasks%20Detail/AM-4396-BE-Chu%E1%BA%A9n-b%E1%BB%8B-Gmail-test-%2B-entity.md).
- **Source/đường thực thi:** GmailAccount/GmailAccountRepository/GmailAccountResponse/LinkAppPasswordRequest.
- **Chưa đạt:** Entity/DTO/unique và fixture đã có; chưa Gmail test account/2FA/standalone connect thật.
- **Blocker và điều kiện tối thiểu:** L — dedicated account hợp lệ với quyền 2FA/App Password, không dùng Gmail cá nhân.
- **Phụ thuộc:** Dedicated account và consent → 2FA/App Password → standalone connect; prerequisite cho live 4359/4361/4397.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4396-C.01 | Entity and query names, validation, DTO, V9 schema/unique/owner | G2 preservation, G3 JPA, source | K |
| 4396-C.02 | Dedicated account/2FA/App Password and standalone Store.connect | G4 local raw and compile-only live source; account/consent absent | L |

**Còn thiếu để đóng:** 4396-C.02 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4397-c"></a>

### AM-4397 — Contract đúng: method, path, request/response schema, error code khớp spec

- **Yêu cầu gốc:** Bảy mẫu MIME, DTO và bộ fixture trên Gmail thật. [Task §4 nguyên bản](Tasks%20Detail/AM-4397-BE-MIME-fixtures-%2B-Mail-DTO.md).
- **Source/đường thực thi:** MailDtos, MIME parser, RealGmailMimeFixturesTest.
- **Chưa đạt:** Builder và rehearsal local đủ 7, live 1/1 SKIP; chưa đủ 7 thư trên dedicated Gmail.
- **Blocker và điều kiện tối thiểu:** L — authorized dedicated mailbox, marker/UID riêng, xác nhận byte và targeted cleanup.
- **Phụ thuộc:** 4396 dedicated mailbox → mẫu riêng → read-back byte/hash/UID/UIDVALIDITY → manual inspection → explicit cleanup; không auto delete hay resend.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4397-C.01 | DTO field aliases and exact decoded attachment bytes; seven MIME samples local | G/J and rehearsal local | K/M |
| 4397-C.02 | Prepare/deliver/read 5+ samples in actual Gmail test, UID metadata and targeted cleanup | G4 source records UID/UIDVALIDITY after verify, live 1 SKIP | L |

**Còn thiếu để đóng:** 4397-C.02 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

## 3 privacy/live

<a id="am-4361-p"></a>

### AM-4361 — Không lộ credential / token / app password / nội dung mail trong response hoặc log

- **Yêu cầu gốc:** Privacy response/log khi kết nối và gửi qua hai auth mode live. [Task §4 nguyên bản](Tasks%20Detail/AM-4361-BE-Test-factory-k%E1%BA%BFt-n%E1%BB%91i-th%E1%BA%ADt.md).
- **Source/đường thực thi:** RealGmailIntegrationTest.safeStage, MailCredentials, DefaultMailConnectionFactory.
- **Chưa đạt:** Chưa kiểm live response/log, credential masking/ownership; fixture/code không thay.
- **Blocker và điều kiện tối thiểu:** L — authorized live test, aggregate canary và metadata đã lọc, không in secret.
- **Phụ thuộc:** 4396 App Password setup + 4316/4317 OAuth consent độc lập; remote IMAPS/SMTP → self-send → read-back → explicit targeted cleanup. Privacy chứng minh riêng.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4361-P.01 | Local masked connection logging, no secret in DTO/properties, test source suppresses raw provider details | SecretMaskingTest, F226, G2/G3 | K |
| 4361-P.02 | Actual live both auth mode logs/response/ownership metadata, independent privacy observation | Dedicated provider and consent absent, do not access live credential or raw logs | L |

**Còn thiếu để đóng:** 4361-P.02 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4396-p"></a>

### AM-4396 — Không lộ credential / token / app password / nội dung mail trong response hoặc log

- **Yêu cầu gốc:** Privacy live link/entity và credential. [Task §4 nguyên bản](Tasks%20Detail/AM-4396-BE-Chu%E1%BA%A9n-b%E1%BB%8B-Gmail-test-%2B-entity.md).
- **Source/đường thực thi:** GmailAccountResponse, AesCipher, GmailAccountLinkService, live opt-in.
- **Chưa đạt:** Chưa kiểm response/log và quyền thực khi link Gmail dedicated.
- **Blocker và điều kiện tối thiểu:** L — phiên live được duyệt, chỉ aggregate/safe-stage, không đọc raw credential.
- **Phụ thuộc:** Dedicated account và consent → 2FA/App Password → standalone connect; prerequisite cho live 4359/4361/4397.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4396-P.01 | No encrypted/plain credentials in DTO, AES GCM/owner tested local, V9 safe state | G2/G3 source/tests | K |
| 4396-P.02 | Dedicated link/standalone live response/log and owner/privacy | Actual account/consent absent | L |

**Còn thiếu để đóng:** 4396-P.02 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**

<a id="am-4397-p"></a>

### AM-4397 — Không lộ credential / token / app password / nội dung mail trong response hoặc log

- **Yêu cầu gốc:** Privacy live MIME/attachment và cleanup. [Task §4 nguyên bản](Tasks%20Detail/AM-4397-BE-MIME-fixtures-%2B-Mail-DTO.md).
- **Source/đường thực thi:** MailDtos/MIME read/download, RealGmailMimeFixturesTest.safeStage.
- **Chưa đạt:** Chưa kiểm metadata/response/log của 7 mẫu live và cleanup đúng UID.
- **Blocker và điều kiện tối thiểu:** L — dedicated Gmail test, chỉ thư synthetic, aggregate privacy, không đọc thư ngoài marker.
- **Phụ thuộc:** 4396 dedicated mailbox → mẫu riêng → read-back byte/hash/UID/UIDVALIDITY → manual inspection → explicit cleanup; không auto delete hay resend.

| ID nghĩa vụ | Điều kiện cụ thể | Code/test/evidence hiện có hoặc còn thiếu | Phân loại |
|---|---|---|---|
| 4397-P.01 | Local fixture content only intended owner, exact bytes, masked logs/metadata | G/J/F canary, local rehearsal | K |
| 4397-P.02 | Live seven samples response/log privacy and cleanup only verified marker/UID, no unrelated mail | Dedicated account, authorized UI and filtered metadata needed | L |

**Còn thiếu để đóng:** 4397-P.02 (L). Cần tất cả hàng này có kết quả đúng yêu cầu, không thay bằng chứng khác loại. **OPEN.**
