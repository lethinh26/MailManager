# Hướng dẫn nghiệm thu phần còn mở — 26/11/2026

## 1. Phạm vi và cách dùng

Mốc đối chiếu ban đầu: [acceptance-review.md](acceptance-review.md) ghi **468 tiêu chí = 287 PASS + 116 N/A + 65 OPEN**. Tài liệu này bao phủ **56 manual OPEN (26 BE, 30 FE), 6 contract OPEN, 3 privacy live OPEN** trên 57 task khác nhau; không phải biên bản PASS, không chỉnh §4/§5. Mọi thay đổi sau mốc này phải đối chiếu lại từng checkbox hiện tại trước khi lập biên bản.

Nguồn từng task: §4 gốc, §4.1 triển khai, §5 residual trong [Tasks Detail](Tasks%20Detail/); [BE crosswalk](backend-acceptance-cases.md), [FE crosswalk](swing-acceptance-cases.md), [H001–H150](backend-manual-observations.md), [F001–F095](backend-final-observations.md), [M01–M31](swing-acceptance-observed.md). H và F là hai fixture độc lập, không tái sử dụng ID/UID của phiên cũ; M là thao tác Swing thật do agent điều khiển trên bản trước các fix layout/file chooser, không phải bằng chứng UI của bản đã sửa. Test matrix hiện ghi BE 241/246 (5 live SKIP), Client 103/103, 344/349 JUnit PASS, Newman 140 request/653 assertion; đây là evidence tự động riêng. Không cần chạy lại các nhánh đã quan sát đầy đủ chỉ để tăng số liệu.

**Phân luồng nghiệm thu:**

- **I — độc lập:** kiểm tra đọc/quan sát an toàn, schema aggregate, public config, metadata, kết quả đã được tạo bởi phiên test hợp lệ; không cần truy cập bí mật. Nếu cần phiên đã xác thực, chỉ làm trong phiên fixture hợp lệ đã được cấp quyền cho chính thao tác đó; không tự tìm hay tạo đường vòng.
- **H — người nghiệm thu:** người có thẩm quyền sử dụng trực tiếp giao diện chính thức Swing, Postman UI của riêng fixture hoặc trang Google chính thức khi được phép; ghi kết quả đã khử nhạy cảm. Đây là mô tả quy trình nghiệm thu nghiệp vụ cho chủ dự án, **không phải yêu cầu người dùng thực hiện hộ một tool call bị chặn**. Agent không thực hiện, chuyển giao, nhắc chạy lệnh, retry hoặc bypass các thao tác credential đã bị safety gate chặn (login/change-password batch và nhập alias credential UI). Không token injection, không trích token/cookie/key/secret từ tiến trình/file/clipboard, không dùng connector/agent khác để làm thay. Nếu chưa có phiên nghiệm thu hợp lệ độc lập, ghi BLOCKED, không tạo phiên bằng cách khác.
- **F — fault injection:** chỉ trên fixture riêng đã có cơ chế fault injection do chủ dự án kiểm soát và cho phép; chưa có cơ chế thì BLOCKED. Không làm hỏng mạng/máy dùng chung, không tự sửa code/test, không ép sự cố production.
- **L — live:** cần chính chủ duyệt tài khoản Gmail chuyên test, OAuth client/consent test, máy chủ test được phép; không thay bằng GreenMail. Không có tài khoản/ủy quyền thì BLOCKED LIVE.
- **D — quyết định đặc tả:** chủ dự án phê duyệt rõ khác biệt hợp đồng; không coi mitigation hoặc manual local PASS là sự chấp thuận.

Các bước ở dưới là **kế hoạch**, không phải kết quả đã thực hiện. H/F/M nêu trước mỗi task là evidence kế thừa; mục “còn làm” chỉ liệt kê khoảng trống. Nhóm H/F/L có thể dùng một phiên nghiệm thu hợp lệ cho nhiều task và tham chiếu cùng evidence nếu hành vi thực sự giống nhau, không lặp lại thao tác credential bị chặn.

## 2. Fixture và trình tự an toàn

1. Người phụ trách xác nhận revision/JAR/Client distribution cuối, cổng HTTPS loopback ngẫu nhiên (không 8443), certificate fixture được kiểm tra hostname/trust, MySQL datadir/schema mới, GreenMail IMAPS/SMTP và OAuth mock riêng, root config/download riêng. Tham chiếu [dev-setup.md](../../Mail%20Server/docs/dev-setup.md) phần manual session và [README HTTP](../../Mail%20Server/docs/http/README.acceptance.md) để biết cấu hình fixture; đây không phải lệnh yêu cầu chạy lại. Không dùng dev profile cá nhân, Postman workspace/global environment có secret, DB/keystore/Downloads thật hoặc tắt TLS. Nếu thiếu fixture an toàn, dừng. Không mở private manifest/state/log.
2. Chuẩn bị dữ liệu synthetic: A0 là admin bootstrap được bảo toàn; A1/A2 là admin hy sinh **nếu đã được chuẩn bị hợp lệ**; U1/U2 có mailbox riêng, U3 hy sinh cho thao tác quản trị; một mailbox hai phương thức và hai mailbox khác nhau cùng owner; các tài khoản có quyền/flag đúng theo bài kiểm. Dùng marker duy nhất cho mỗi thư, >50 thư để test trang, >25 audit rows và đủ user hai trang, thư đối chứng không được xóa. File vô hại: PDF nhỏ, PNG, text UTF-8, duplicate filename khác bytes, zero-byte, 1 MiB và 1 MiB+1; hash nguồn trước khi gửi. Không thu thập thông tin credential; không mở executable. Cấu hình auto-clean tắt, chỉ thao tác file dưới root fixture.
3. Thứ tự: I đọc schema/public config/evidence; H thao tác UI chưa kiểm ở bản cuối; thay đổi flags theo nhóm, ghi snapshot 9 key trước, phục hồi đúng snapshot sau từng ca; fault injection riêng; cascade/delete destructive cuối phiên; live là phiên riêng hoàn toàn. Không chạy nhiều operator mutation trên cùng fixture đồng thời. Người phụ trách dọn đúng fixture theo lifecycle hiện có, không tác động tài khoản/thư ngoài phạm vi.
4. **Điểm dừng:** mọi tool gate, sai cert, URL ngoài fixture, path ngoài root, thiếu quyền, account không đúng synthetic marker, timeout/mất phản hồi không xác định, dữ liệu thật hoặc pop-up credential ngoài phạm vi. Không retry mù. Chỉ ghi nhận lỗi đã thấy; thao tác mutation mất reply thì đối chiếu read-back hợp lệ trước khi quyết định, không gán PASS.

## 3. 26 manual Backend còn OPEN

Các endpoint dưới đây mô tả contract để người nghiệm thu biết hành vi cần quan sát, không phải lệnh thực thi. Với các nhánh credential đã bị gate chặn, không điều khiển Postman/terminal/browser bằng agent, không gửi secret trong biên bản. Nếu chủ dự án tự có một buổi kiểm thử hợp lệ sau này, có thể quan sát chính các luồng UI thông thường dưới đây; không chạy lại qua công cụ thay cho agent. Chứng cứ FE không tự đóng criterion BE Postman nếu thiếu phản hồi API được khử nhạy cảm từ phiên nghiệm thu được phép.

### AM-4309 — Manual đăng ký [H]

Đã có H009–011 201/duplicate username/validation và H098 REGISTER; H117 là thiếu body, không chứng minh disabled registration.

- Còn làm: trong UI đăng ký bình thường của fixture, khi form đã mở và admin chủ động tắt đăng ký, thử hoàn tất form có dữ liệu hợp lệ theo quy trình nghiệm thu của người sở hữu phiên; nếu UI chặn trước request, ghi FE pass tương ứng nhưng BE 403 chưa quan sát. Khi fixture cho phép đăng ký, kiểm email trùng và username khác hoa/thường của dữ liệu synthetic; không đụng tài khoản thật. Khôi phục flag sau ca. Không retry batch credential bị gate.
- Kỳ vọng API EP-A1: valid request khi flag false → 403 REGISTRATION_DISABLED; duplicate username/email → 409 code đúng contract; không thêm user/REGISTER success. Race cần fixture concurrency riêng, không lấy lần gửi tuần tự thay thế. Evidence chỉ code/count/audit action, không body có password.

### AM-4310 — Manual đăng nhập [H]

H012/H077 login 200 đã quan sát; H067 là session sau lock, không phải login bị lock.

- Còn làm trong buổi nghiệm thu hợp lệ do chính người dùng thao tác LoginFrame: đăng nhập bằng email synthetic; nhập sai để thấy lỗi chung, dùng một USER hy sinh để kiểm ngưỡng cấu hình và countdown, đợi thời gian giới hạn tự hết (không đổi IP/username để né). Với user đã bị khóa theo workflow Admin chính thức, quan sát từ chối; khi Maintenance bật, USER bị chặn còn ADMIN hợp lệ được vào. Không thao tác credential bootstrap hoặc vượt safety gate.
- Kỳ vọng EP-A2: 200 user/token metadata (không ghi token); 401 UNAUTHORIZED chung, 403 ACCOUNT_DISABLED, 429 TOO_MANY_ATTEMPTS + Retry-After, login USER bảo trì 403 MAINTENANCE_MODE theo contract login (API có phiên USER khác có thể 503), ADMIN 200. Kiểm audit LOGIN/LOGIN_FAILED đã khử nhạy cảm; không lặp H012. Nếu thiếu người nghiệm thu hợp lệ hoặc telemetry sanitized, BLOCKED. JWT expiry kiểm riêng 4311.

### AM-4311 — Manual /me [H, I]

H003–005/H029/H067/H069/H071/H081 đã đủ missing/invalid/valid JWT, role và locked/deleted session. Không tạo token giả.

- Còn làm: trong phiên hợp lệ do người nghiệm thu sử dụng bình thường, quan sát tài khoản mustChangePassword=true ở UI đổi mật khẩu bắt buộc; metadata /me hiện đúng role/id/flag. Để phiên test hết hạn theo TTL cấu hình hợp lệ và quan sát request refresh tự nhiên bị từ chối; không trích, sửa, tiêm, ký hoặc dùng lại token thủ công.
- Kỳ vọng /me 200 đúng DTO, không password/hash, phiên tự hết hạn 401 UNAUTHORIZED và client về login, không hiển thị mailbox cũ. Nếu không có phiên hết hạn tự nhiên trong thời gian fixture, ghi BLOCKED chứ không thay clock/token.

### AM-4312 — Manual đổi mật khẩu [H]

H078 chỉ có forced-change gate, chưa có thao tác EP-A4. Batch tiếp theo bị gate chặn, không thực hiện lại bằng tool.

- Còn làm: chủ dự án trong buổi GUI hợp lệ của tài khoản synthetic dùng menu Đổi mật khẩu/ChangePasswordDialog bình thường; quan sát validation mật khẩu hiện tại không đúng, mới trùng, yếu, rồi đổi hợp lệ theo chính sách ≥8 ký tự, ≤72 byte UTF-8, chữ+số. Sau khi đổi, xác minh flag forced-change hết, app có thể thao tác tiếp; nếu chính người nghiệm thu thực hiện logout/login thông thường thì kiểm hành vi cũ bị từ chối và mới được chấp nhận.
- Kỳ vọng EP-A4 400 VALIDATION_ERROR cho ca sai/trùng/yếu, 200 và thông báo thành công khi đúng, refresh session do app tự xử lý, CHANGE_PASSWORD audit không password. Chỉ ghi status, flag, audit; không ghi mật khẩu/token. Không có người nghiệm thu thì BLOCKED; không dùng agent để làm thay.

### AM-4314 — Manual App Password link [H, I, F, L]

H082/H083/H105/H106 đã có duplicate409/explicit UPSERT200/CREATE201/IMAP. H121 là GET list, không phải disabled link.

- Còn làm: dùng wizard quản lý Gmail chính thức trên mailbox synthetic được provision hợp lệ: kiểm validation email/format, quota tạo mới với đủ mailbox, và flag disabled khi wizard đang mở. Với authorized human test session, sai thông tin xác thực fixture được xử lý thành lỗi, không thêm link; nếu yêu cầu kiểm chính POST, chỉ dùng kết quả sanitized của chính buổi nghiệm thu đó. Không xin/nhập credential bằng agent.
- Kỳ vọng EP-G2 400 VALIDATION_ERROR, 403 FEATURE_DISABLED cho POST hợp lệ bị cờ chặn, 409 GMAIL_LIMIT_REACHED tạo mới, 422 GMAIL_AUTH_FAILED sai App Password; không link/audit thành công khi lỗi. I lọc LINK_GMAIL từ audit đã tồn tại và kiểm metadata DTO/aggregate encrypted count. F probe DNS/network do fixture có sẵn → 502 GMAIL_CONNECTION_ERROR (khác timeout của Mail API). L App Password Google thật/2FA và TLS chỉ khi được duyệt.

### AM-4316 — Manual authorize URL [I, H, F, L]

H041/H045–047/H108/F003 đã có flag403, URL/poll [present], pending và foreign404.

- Còn làm: trong phiên fixture hợp lệ, owner đã đạt quota chọn “Liên kết Google” ở chế độ CREATE; kiểm 409 GMAIL_LIMIT_REACHED không mở luồng mới. Nếu owner có phiên link mới hợp lệ, dùng UI bình thường, đợi TTL 5 phút; từ chỉ số khử nhạy cảm của provider xác nhận state/PKCE S256, scope Gmail và profile, offline/consent, không có secret. Không mở hoặc sao chép raw authorizeUrl/state/poll token, không chế callback thủ công.
- Kỳ vọng quota không tạo mailbox; session quá TTL không còn usable (poll 410 LINK_SESSION_EXPIRED hoặc lỗi state đúng contract). Nếu không có sẵn diagnostic an toàn thì TTL/PKCE vẫn BLOCKED. L browser consent đúng tài khoản test được phép, không browser profile cá nhân.

### AM-4317 — Manual callback [I, H, F, L]

H051–059 success/duplicate/UPSERT, H109–112 deny/poll consumed; F004–007 406→PENDING→HTML success→poll SUCCESS đã hoàn tất, không lặp.

- Còn làm: trong buổi nghiệm thu OAuth fixture có provider hỗ trợ ca expired/invalid state, success replay, scope thiếu, Gmail profile/email mismatch, chủ dự án chọn scenario được cấp sẵn; không sửa/inject token, code, state hoặc truy xuất callback bí mật. Quan sát browser kết thúc bằng HTML lỗi, poll FAILED với code đúng (ACCOUNT_MISMATCH/GOOGLE_PROFILE_ERROR hoặc scope/state tương ứng), không tạo link hoặc thay credential; success replay không sinh lần link/token exchange thứ hai.
- I kiểm audit OAUTH2_CALLBACK đúng actor, metadata linked/masked, counter và encrypted shape chỉ aggregate; không coi count là chứng minh nội dung ciphertext. F nếu provider không hỗ trợ các tình huống, ghi BLOCKED. L Google consent/token exchange/profile thật phải là phiên riêng được phê duyệt.

### AM-4319 — Manual admin xóa user có OAuth [I, F, L]

F085–095 đã có relink, target hai mailbox, 204, users 4→3, Gmail 3→1, OAuth 1→0, detail404/session401, audit 2 links, owner khác còn; provider 2 revoke tổng hợp. Không xóa lại target.

- Còn làm: dùng cùng đánh giá guard cuối-admin ở 4328/4330/4332, không tạo tình huống bằng cách khóa/xóa bootstrap. Nhánh best-effort revoke lỗi chỉ khi provider fixture riêng có fault được phép: admin hợp lệ thao tác xóa target synthetic có link OAuth, quan sát revoke failure count + user/link biến mất, admin và mailbox đối chứng giữ, đúng một ADMIN_DELETE_USER với count target, không secret. Nếu không có target/cơ chế đã provision, BLOCKED.
- Last-admin độc lập không thể đạt bằng caller là sole enabled admin vì self guard chặn trước; yêu cầu reviewer xem invariant và evidence concurrency riêng. L revoke Google thật chỉ trên dedicated account khi được duyệt, không hủy quyền tài khoản cá nhân.

### AM-4320 — Manual folder IMAP [I, F, L]

H015/H084/H106/H125, F056 đã có folder counts, owner/foreign deny và Trash.

- Còn làm: trên phiên hợp lệ đọc danh sách folder với ID synthetic không tồn tại đã kiểm tra (không ID của người khác) → 404 GMAIL_NOT_OWNED, không mở IMAP. Trong fixture fault đã provision sẵn mailbox auth lỗi hoặc kết nối timeout/network, chọn folder qua app/endpoint đọc; expected 422 GMAIL_AUTH_FAILED/GMAIL_NEEDS_REAUTH, 502 GMAIL_CONNECTION_ERROR hoặc 504 timeout, UI không dùng list cũ như success.
- Chỉ số an toàn connection/pool lease phải quay về baseline sau lỗi/thành công; không coi API 200 là bằng chứng close Store. Không fault hook/telemetry thì BLOCKED. L xác minh Gmail special-use folder mapping và total/unread trên mailbox test.

### AM-4323 — Manual SMTP multipart [H, I, F, L, D]

H027/H140/F013 send, H042/H120 flag403, H136 email400, H138 cap413, F069/070 subject/body400, F064/065 CC/hash, F014 group counts đã có. Không lặp.

- Còn làm: user có phiên hợp lệ mở Compose, dùng To/CC/BCC là ba mailbox fixture khác nhau, file PDF/PNG/text vô hại (hash nguồn), marker riêng; xác minh BCC mailbox nhận đúng một thư, người nhận To/CC không thấy địa chỉ BCC, file từng index/bytes/hash khớp. Với dữ liệu header có ký tự xuống dòng ở field text do bài test synthetic quy định, quan sát validation 400 hoặc client chặn an toàn, không sinh thư/audit success; không gửi ra ngoài fixture.
- F lỗi auth/network SMTP trên fixture riêng → 422/502 (timeout 504 khi xác định), không success giả, không tự gửi lại khi kết quả không xác định. I audit chỉ grouped counts/masked sender/actor; D literal raw To/subject còn OPEN (mục 5); L Gmail SMTP thật chưa có. Tham chiếu cùng evidence 4369/4398 nếu đúng từng file/recipient.

### AM-4325 — Manual admin tạo user [H, I]

H073–074 đã tạo 201 forced-change và metadata không có mật khẩu tạm.

- Còn làm: trong AdminFrame hợp lệ, khi registration=false, người nghiệm thu dùng “Tạo mới” với dữ liệu synthetic mới; quan sát user được tạo, role mặc định USER và forced-change. Thử dữ liệu trùng/validation bằng form quản trị, không tạo account thật. USER shell không có chức năng admin; nếu chỉ UI ẩn thì chưa chứng minh API 403, dùng evidence độc lập đã cấp quyền khi có. Phục hồi setting.
- Kỳ vọng EP-U create 201 dù registration tắt, duplicate409/invalid400/USER403; audit ADMIN_CREATE_USER đúng actor/role; mật khẩu tạm nếu hiển thị one-time trong UI không chụp/ghi. Không nhờ agent reset/đăng nhập admin để kiểm. Nếu không có phiên Admin human hợp lệ thì BLOCKED.

### AM-4328 — Manual khóa user [H, I, F]

H066–067/H148 và F021–027 đã có lock, JWT401, audit, missing404/USER403/self400.

- Còn làm: người nghiệm thu với user synthetic đang bị khóa trong workflow LoginFrame hợp lệ quan sát login bị từ chối 403 ACCOUNT_DISABLED; không lặp lock positive. Độc lập kiểm policy last enabled ADMIN như mục guard cuối nhóm này. Nếu đã có fault/concurrency fixture dành riêng, kiểm invariant admin enabled luôn ≥1 khi thao tác đồng thời, không dùng production/bootstrap.
- Kỳ vọng khóa không tạo false success và không mất admin cuối. Không coi self400 là branch last-admin độc lập; thiếu khả năng kiểm giữ OPEN cho branch đó.

### AM-4330 — Manual đổi role [H, I, F]

H070–072/H150 promote/restore/audit; F024/025/028 invalid400/USER403/self400.

- Còn làm: trong buổi UI hợp lệ, sau khi A1 synthetic được nâng quyền, làm mới phiên hiện tại qua UI → thấy admin tabs và thao tác đọc được; khi A0 hạ A1 về USER, refresh từ phiên A1 → admin chức năng bị chặn, không dùng cached role cũ. Kỳ vọng role cập nhật và admin endpoint 403 khi role USER; ghi evidence menu và status sanitized.
- Last-admin guard như mục chung 4328/4332; không ép tự demote, không thử vòng qua role bằng token. Nếu thiếu A1 đã provision/phiên hợp lệ, BLOCKED.

### AM-4331 — Manual reset password [H, I]

H075–078 reset generated temp200/no-store, forced-change, login temp200, mailbox403 đã có. Không lặp reset/login bằng agent.

- Còn làm: trong buổi Admin GUI hợp lệ, quan sát validation chế độ mật khẩu mới explicit nếu UI chính thức hỗ trợ (không sử dụng tool để ép request body); nếu UI chỉ generated temp, ghi nhánh explicit API chưa quan sát. Người sở hữu tài khoản synthetic thực hiện ChangePasswordDialog bình thường, xác nhận forced-change hết và mailbox dùng được; nếu tự logout/login, xác nhận mật khẩu cũ bị từ chối. Xem 4312, 4349/4373 để chia sẻ evidence đúng nhánh.
- Kỳ vọng weak/invalid explicit newPassword 400, valid reset 200 no-store, mustChangePassword=true trước đổi và false sau đổi, ADMIN_RESET_PASSWORD audit chỉ target id không secret. Không chụp dialog temp password, không xuất token. Gate/không có human session → BLOCKED.

### AM-4332 — Manual xóa user [I, F, L]

F029–032 self/missing/USER guard; F088–095 cascade và read-back đủ phần trọng tâm. Không lặp destructive positive. Chỉ còn invariant last-admin và revoke failure/live như 4319.

- **Guard chung 4319/4328/4330/4332:** rà code/spec bằng đọc nguồn và invariant metadata fixture (enabled ADMIN count). Sole enabled admin vừa là caller, tự lock/demote/delete bị self guard 400 trước; không có thao tác HTTP bình thường độc lập vào last-admin branch khi invariant đúng. Ghi phân biệt “self guard observed”, “last-admin branch not directly observed”. Nếu muốn chứng minh race, cần fixture concurrency được duyệt và A1/A2 hy sinh đã provision, giữ ít nhất A0 an toàn; dùng test evidence riêng, không cố tạo DB trạng thái bất hợp lệ, không sửa role/hash/token trực tiếp. Không có safe scenario thì owner quyết định phạm vi generic manual hoặc giữ OPEN, không đổi tiêu chí bằng suy diễn.
- Còn F revoke best-effort failure của 4319 và L revoke thật. Kỳ vọng lỗi provider không làm rơi link/user đối chứng hoặc tạo audit sai; không truy cập credential.

### AM-4340 — Manual schema/seed [I, D]

Metadata MySQL F đã xác nhận V1–V6, 4 bảng, 9 settings, admin BCrypt-shape1, linked encrypted count1; F089–095 cascade. Còn structural/upgrade cụ thể.

- Còn làm: trên metadata-only view của DB fixture cuối, đối chiếu danh sách column/type/nullability, charset/collation, PK/FK/on-delete, unique normalized username/email và unique(owner,email_key), indexes; kiểm seed **chỉ thiếu key**, không ghi đè giá trị đã có và admin bootstrap chỉ khi cần. Đối chiếu 9 key/default trong §4 với schema thực; chỉ báo key names/count/type, không truy xuất user password/ciphertext hoặc secret.
- Legacy upgrade: nếu có snapshot synthetic V1–V5 chuyên test được owner chuẩn bị, ghi trước/sau aggregate count, normalized uniqueness, custom settings giữ nguyên và migration history; không sử dụng DB thật, không chạy migration hoặc mở DB ngoài phạm vi trong lượt tài liệu này. H2/unit migration không thay MySQL legacy direct. D literal schema/seed vẫn cần chấp thuận mục 5; deploy thật chỉ xác nhận riêng, không đổi port thật để đạt checklist.

### AM-4342 — Manual AES-GCM [I, F]

H/F DTO không có credential và aggregate encrypted count chỉ chứng minh cột không rỗng, không xác nhận plaintext khác ciphertext.

- Còn làm: rà AesCipher và test hiện có: 32-byte key, GCM, random 12-byte IV/128-bit tag, không ECB/reuse, exception sanitized. Để có direct manual crypto evidence, chỉ dùng **demo/diagnostic được cấp sẵn** với chuỗi vô hại không phải credential, key test ngẫu nhiên riêng: hai lần mã hóa cùng dummy text cho output khác, decrypt khớp, tamper/wrong test key bị từ chối; ghi booleans/độ dài/loại thuật toán, không in key/output/decrypted text. Không có diagnostic noncredential được phép thì BLOCKED, không viết/chạy script đọc AES key thật.
- Kiểm aggregate linked credential shape và privacy report qua cơ chế đã khử dữ liệu nhạy cảm; không query ciphertext/plaintext/secret, không đọc raw log. Unit test evidence tách riêng.

### AM-4343 — Manual error handler [I, H, F]

H/F đã có 400/401/403/404/406/409/410/413/503 và fix 406. Chưa có 415/429/generic500/auth/network.

- Còn làm I: trên route fixture hợp lệ có body JSON, ca media type không hỗ trợ với body **không chứa credential** → 415 structured ErrorDto; đối chiếu timestamp/status/error/message/code/path và code alias, không stacktrace hay dữ liệu thô. 429 lấy từ buổi nghiệm thu legitimate rate-limit ở 4310 hoặc giới hạn read/download synthetic đã được duyệt; ghi Retry-After, không spam/né rate.
- F lỗi generic có kiểm soát (hook fixture được duyệt) → 500 INTERNAL_ERROR chung, không exception/raw input; lỗi auth/network từ 4320/4323. Không cố gây crash thật. Nếu hook không có, ghi BLOCKED. Privacy log chỉ từ báo cáo scrubbed, không đọc private log.

### AM-4344 — Manual audit service [I, H]

H091/093/098/119/127/142/148–150 và F014/057/080/084/094 đã đủ DELETE/SEND/REGISTER/settings/flags/admin lock/unlock/role/unlink/delete. Không thực hiện lại chỉ để lấy log.

- I: trên audit view của phiên ADMIN hợp lệ, lọc lần lượt LOGIN, LOGIN_FAILED, CHANGE_PASSWORD, ADMIN_CREATE_USER, ADMIN_RESET_PASSWORD, LINK_GMAIL, OAUTH2_CALLBACK đã được tạo từ các thao tác hợp lệ, kiểm action/actor/target/IP loopback/time và số record; nếu không có event thì ghi thiếu và chỉ chờ bằng chứng từ buổi human workflow tương ứng. Không tạo thêm credential action bằng agent. Không spoof X-Forwarded-For.
- Kỳ vọng detail không chứa mật khẩu, temp password, JWT, OAuth token, App Password, nội dung mail. Chỉ ghi booleans/action/count; không dump row chi tiết/CSV/raw log. Một audit action không chứng minh toàn bộ log; live privacy tách mục 6.

### AM-4358 — Manual factory chung [I, F, L]

H015/H016/H027–030 và F folder/send/download chứng minh API round-trip, chưa đo lifecycle raw.

- I: rà interface/session properties và connection factory hiện có đối chiếu IMAPS993/SMTP587 STARTTLS, hostname verification, timeouts socket (connect10s/read15s theo đặc tả nếu cấu hình mặc định), auth exception wrap, pool lease/release; bằng chứng cấu hình/source không gọi là handshake manual.
- F: fixture có sẵn cert sai/timeout/disconnect; người nghiệm thu mở folder/gửi marker qua workflow hợp lệ, ghi lỗi structured, connection count/lease baseline trước/sau, không auto retry auth hoặc uncertain mutation. Không hook thì BLOCKED. L kiểm handshake thật ở 4361; không dùng trust-all, không suy socket timeout thành deadline DNS/TLS tổng.

### AM-4359 — Manual App Password connection [H, F, L]

H013/H083/H105/H106/H027–030 local ACTIVE/update/round-trip đã có.

- Còn làm: trong buổi người dùng hợp lệ với mailbox synthetic đã provision, wizard/probe hiển thị lỗi auth khi thông tin không hợp lệ; không thêm link, không lộ password, phân biệt auth422 với network502/504 qua fixture fault được phép. Không agent nhập alias/password/đọc private manifest.
- L dùng chính luồng App Password trên tài khoản Gmail dedicated được phép, kiểm TLS hostname/IMAPS, SMTP STARTTLS, round trip marker và close/release bằng counters an toàn; xem 4361/4396. Chưa có account/diagnostic thì BLOCKED; không lấy local TLS làm Google handshake.

### AM-4360 — Manual XOAUTH2 connection [I, H, F, L]

H051–065/F004–007/F081–095 OAuth local link/revoke/cascade, counters loopback, không phải XOAUTH2 handshake Gmail thật.

- F: chỉ với provider/IMAP fixture đã có scenario hết hạn access token, invalid_grant, lỗi auth và mailbox có hai phương thức được provision hợp lệ: mở folder bình thường, quan sát refresh thành công hoặc 422 NEEDS_REAUTH nếu không fallback; fallback App Password chỉ khi policy cho phép, đúng mailbox, audit method switch, không duplicate link; khi cấp lại quyền hợp lệ, ACTIVE trở lại. Không đọc/chỉnh token hoặc inject credential để gây lỗi. Nếu scenario chưa có, BLOCKED.
- L consent Google chính thức, XOAUTH2 IMAPS/SMTP với tài khoản chuyên test và refresh/revoke qua workflow được duyệt. Chỉ ghi cơ chế/counter/masked status, không in secret; 4361/4396.

### AM-4361 — Manual factory kết nối Gmail thật [L]

5 live test SKIP, không có H/F handshake Google. Đây là live-only, không thể đóng bằng local.

- Chủ dự án tự quyết định cấp quyền tài khoản dedicated (mục 6). Trong phiên live chuyên test, người nghiệm thu kiểm hai phương thức App Password và OAuth2 **riêng biệt** bằng workflow chính thức, TLS hostname và IMAPS/SMTP auth, mở folder/count, gửi marker self-send text + file vô hại, tìm đúng marker, đọc/tải bytes/hash; dọn đúng marker/UID sau xác nhận. Không scan/xóa thư không thuộc test, không dùng account thật cá nhân.
- Ghi biên bản 2 mode, timing, status, SHA dummy, cleanup marker và privacy check; opt-in suite result riêng, không thay manual. Nếu thiếu consent/tài khoản/môi trường, BLOCKED LIVE; cả contract/privacy còn OPEN.

### AM-4396 — Manual chuẩn bị Gmail test/entity [L, I]

H105/106/F008/F035 entity DTO/owner/unique fixture; chưa có Gmail riêng thật.

- Owner provision Gmail chuyên dev/demo, 2FA/App Password theo giao diện Google chính thức và OAuth consent nếu cần; không ghi secret vào repo/chat/biên bản. Trong phiên test được phê duyệt, xác nhận standalone IMAPS Store.connect và link ACTIVE bằng app, CREATE duplicate của cùng owner bị409, metadata DTO chỉ identity/auth/status/time; xác nhận owner khác không nhìn thấy row của mình bằng session hợp lệ sẵn có. Không mở mailbox cá nhân.
- Ghi marker/account alias masked, status, unique/owner, TLS, aggregate encrypted shape nếu fixture hỗ trợ. Không có phép/credential an toàn → BLOCKED LIVE. Xem mục 6 contract/privacy.

### AM-4397 — Manual MIME fixtures/DTO [L]

H019–022/H100/F010–012/F063/F068 đã có fixture text/HTML sanitize/duplicate name/hash/zero-byte/limit. Chưa có 5+ thư Gmail thật.

- Sau khi owner duyệt dedicated Gmail, tạo đúng 7 mẫu vô hại theo opt-in thiết kế hiện có: plain, HTML-only, multipart/alternative, PDF, nhiều PNG/text, UTF-8 Việt, nested message/rfc822; marker riêng từng mẫu, hash nguồn cho file. Gửi vào mailbox test, dùng app tìm đúng marker, kiểm summary/detail alias (From/To/Cc/Bcc/date/body), metadata index/name/type/sizeAccuracy, file download hash từng part; HTML remote không tự tải, không hiển thị script.
- Giữ mẫu chỉ khi đã opt-in KEEP-FIXTURES và ghi inventory marker/UID/UIDVALIDITY, dọn đúng từng marker khi xong. Không đọc thư ngoài marker, không khẳng định metadata estimate là exact. RealGmailMimeFixturesTest đang SKIP; không bật nó trong lượt hiện tại. L thiếu account → BLOCKED.

### AM-4398 — Manual SendMailRequest/builder [H, I, L]

H027/H140/F013/F064–070 multipart legacy/modern, delivery/limits/zero-byte. Còn mẫu nhiều MIME và hash nguồn.

- Dùng cùng buổi gửi synthetic ở 4323/4369: chuẩn bị 1 PDF nhỏ, PNG và text UTF-8 có SHA nguồn, gửi plain và HTML với marker riêng qua form hiện hành; với Postman human nghiệm thu độc lập được phép, chọn contract multipart payload hoặc form gốc và xác minh không trộn hai kiểu. Không tạo lại nhánh legacy/modern success đã quan sát chỉ để tăng số.
- Kỳ vọng người nhận fixture có đúng To/Cc/Bcc, nội dung plain/HTML đúng, tên/type/index từng part và hash decoded = nguồn. I đọc builder/DTO contract và evidence standalone test (tách tự động). L Gmail thật cùng 4361/4397; local không thay SMTP Google.

## 4. 30 manual Swing còn OPEN

Các bước dưới đây dành cho **người nghiệm thu sử dụng cửa sổ Swing thật bằng chuột/bàn phím** trong phiên đã được cấp quyền, không phải thao tác qua agent/cầu credential, không tự mở browser/profile cá nhân. Nếu cần LoginFrame hoặc AdminFrame và chưa có phiên human hợp lệ, ghi BLOCKED, không retry hoặc chuyển qua tool khác. Mọi ảnh chỉ chụp cửa sổ fixture không chứa password/token/temp-password dialog; file chooser chỉ folder fixture, viewer chỉ file dummy. BE API success không thay bằng chứng render/refresh/UI.

### AM-4346 — Manual startup (M01) [H, I]

Đã mở Main.main thật, Windows L&F và LoginFrame/restart. Còn dùng bản Client cuối, quan sát splash nếu có, focus bàn phím/Tab/Enter không nhập secret, resize min-size 800×600 và DPI 100%/150% trên môi trường test nếu có, đổi theme chính thức; kiểm không clip, font/label đủ, lỗi cert startup hiển thị an toàn (cùng 4347). Ghi kích thước/DPI/cửa sổ, không screenshot desktop khác. Không có môi trường DPI thì ghi chưa xác minh, không thay OS cá nhân.

### AM-4347 — Manual HTTPS/ApiClient (M02) [H, F]

Đã có kiểm tra kết nối cert đúng và login/mail HTTPS; F002 BE trust rejection không phải FE. Trên fixture Client riêng có config cert không khớp được owner chuẩn bị, click “Kiểm tra kết nối máy chủ” → lỗi TLS rõ, không MainFrame/không chấp nhận trust-all; phục hồi config chuẩn. F gián đoạn server fixture riêng, UI báo kết nối lỗi và phục hồi khi service lại sẵn; phiên tự hết hạn như 4311 → 401 dẫn login, không stale data. Không tự dừng server chia sẻ hoặc tiêm token; không hook/phiên tự hết hạn → BLOCKED.

### AM-4349 — Manual LoginFrame (M04) [H]

M04 trống/sai/đúng, placeholder quên mật khẩu đã có. Người nghiệm thu hợp lệ kiểm Tab/focus/Enter, show-hide password chỉ quan sát trạng thái che (không chụp giá trị), countdown 429/Retry-After không thể submit trong giới hạn, maintenance banner và lỗi user locked; tài khoản forced-change mở modal không vào MainFrame cho đến đổi hợp lệ. Kỳ vọng thông báo đúng code, nút phục hồi theo policy, không lưu password. Dùng chung buổi H 4310/4312/4331; không agent nhập credential hay retry batch bị chặn.

### AM-4350 — Manual RegisterFrame (M05) [H]

M05 trống/confirm mismatch/201 đã có. Trong form chính thức, thử username trùng/case variant, email trùng/sai và biên độ 255/256 ký tự, password vượt 72 byte UTF-8 bằng dữ liệu synthetic do người nghiệm thu tự nhập. Kỳ vọng field highlight, message 409/400 tương ứng, form không tạo user sai. Khi form đang mở và registration bị tắt hợp lệ, xem 4351; expected disable/403 nếu request đã gửi. Không đưa password/response token vào evidence; nếu UI validation chặn thì ghi client-only.

### AM-4351 — Manual đăng ký theo flag (M06) [H]

M06 fresh LoginFrame đã ẩn đăng ký khi false. Còn mở RegisterFrame khi flag true; admin human hợp lệ đổi false, đợi poll UI → form disabled/note, password field được clear, stale submit không thành công; nếu request racing đã gửi, 403 REGISTRATION_DISABLED được xử lý, không tạo user. Restore true → nút/form hoạt động lại theo refresh/poll. Ghi thời điểm config đổi và UI chuyển trạng thái, không lấy public config BE thay quan sát form.

### AM-4352 — Manual MainFrame shell (M07) [H]

M07 empty CTA, toolbar USER/sidebar/status đã có. Còn resize MainFrame 800×600 và DPI khác/theme, chuyển card Empty/Mail/Manager bằng menu, kiểm không chồng/clip, status loading; trong phiên admin hợp lệ quan sát khu Admin hiển thị và trở lại mail card. USER không thấy admin controls đã quan sát; không lặp login USER bằng tool. Evidence screenshot riêng mỗi layout không chứa secret.

### AM-4353 — Manual Gmail manager (M08) [H, F]

M08 card/metadata/modal và button đã có. Còn chọn mailbox synthetic → Đổi tên, Cancel giữ tên rồi Save → card/sidebar cập nhật cùng ID; “Kiểm tra kết nối” → trạng thái/lastVerifiedAt cập nhật, failure rõ nếu fault riêng; mailbox hai auth method đã provision, chọn preferred, kiểm active/fallback badge với scenario sẵn; Gỡ một method giữ mailbox/credential còn lại, cảnh báo khi gỡ cuối. Không gỡ dữ liệu thật, không nhập secret qua agent. Nếu không có two-method/fault fixture, BLOCKED phần đó.

### AM-4354 — Manual App Password wizard (M09) [H, L]

M09 wizard ba bước/ACTIVE/INBOX60 đã có. Còn validation email/App Password format, quota denied, explicit thêm/cập nhật method với mailbox fixture đã có (không duplicate ID), sai credential hiển thị lỗi và không tạo link trong buổi human hợp lệ. Quan sát mask/disable loading, Cancel giữ dữ liệu cũ. Kỳ vọng error 400/409/422 phù hợp, label hướng dẫn 2FA. Không lấy App Password Google thật từ tài khoản cá nhân; L chỉ dedicated đã duyệt, không có thì BLOCKED LIVE.

### AM-4355 — Manual OAuth UI (M10) [H, L]

M10 chỉ nút OAuth, không chạy consent. Với phiên human hợp lệ và provider local có UI chính thức, người nghiệm thu chọn “Liên kết Google”, quan sát browser/pending, cancel/deny → UI FAILED/hủy poll, không link; success → quay app, card ACTIVE; OAuth mailbox NEEDS_REAUTH đã provision → chọn Cấp lại quyền, refresh card giữ đúng mailbox ID. Không mở raw callback/state/poll token hay browser profile cá nhân. Nếu chỉ mock HTTP không có browser flow, ghi BLOCKED UI; Google consent thật cần dedicated account riêng (L).

### AM-4356 — Manual unlink UI (M11) [H, L]

M11 chỉ thấy nút. Còn trên mailbox hy sinh có marker: chọn Xóa liên kết → No giữ card/folders, Yes → card biến mất và main refresh, mailbox đối chứng giữ, không xóa thư trên server; nếu có OAuth dedicated, revoke kiểm riêng 4318/4360. Khi gỡ method thay vì whole mailbox, xác minh method còn lại giữ đúng card (4353). Không thử IDOR bằng cách sửa request/UI; BE F đã có foreign guard. Ghi dialog No/Yes, count và read-back; thiếu human session → BLOCKED.

### AM-4357 — Manual flag link (M12) [H]

M12 ban đầu cả hai button hiện, chưa thấy UI sau đổi flag. Còn mở manager/wizard, Admin human đổi App Password false/OAuth true rồi đảo lại (không tắt cả hai), đợi poll; link/reauth tương ứng ẩn/disable/note, wizard đang mở dừng submit/clear secret, method đang liên kết vẫn có thể quản lý/gỡ. Restore snapshot → controls đúng. Kỳ vọng stale POST 403 nếu request đã gửi; nếu UI chặn trước POST, chỉ ghi FE. Không lấy H116 config thay render.

### AM-4362 — Manual sidebar (M13) [H, F]

M13 badge AppPassword/count/Trash và cross-user đã có. Còn dùng owner có hai mailbox synthetic đã provision với marker khác nhau, chọn folder qua chuột ở DPI khác; kiểm sidebar icon/auth/fallback/status, list/selected account khớp, nhanh chuyển A→B khi request A chậm (F đã được duyệt) → chỉ B update, không lẫn UID/mail; refresh count sau thao tác. Không tiêm request IDOR; F008–009/F040/F047 là chứng cứ BE ownership tách riêng.

### AM-4363 — Manual list table (M14) [H]

M14 sender/subject/date/attachment và unread font đã có. Còn chọn row bằng bàn phím, double-click/Enter đúng mail, thử sửa cell không được, long subject scroll/truncate với tooltip và DPI, không click nhầm row. Sau mở một unread fixture, đóng detail/làm mới thấy row font thường, filter unread loại khỏi list. Ghi row marker trước/sau, không cần lặp attachment filter đã quan sát.

### AM-4364 — Manual paging/search (M15) [H, F]

M15 đã kiểm 10/20/50, Prev/Next/Load more/boundary và marker search. Còn chọn “Mặc định”, admin đổi MAIL_PAGE_SIZE (ví dụ 20→10, range10–100) → refresh/poll page size mặc định đổi; override 50 vẫn 50. Tìm marker UTF-8, unread+attachment combination, clear và thay query phải reset trang1. Mở detail rồi “Làm mới”/quay list, read state/count cập nhật; không trùng marker khi mailbox ổn định, tie date giữ thứ tự. F chuyển folder/q nhanh lúc load, response cũ không ghi đè. Không yêu cầu snapshot offset khi mailbox mutate; restore setting.

### AM-4365 — Manual detail (M16) [H]

M16 phát hiện 3 chips nhưng clip sau chip đầu; bản cuối có sửa scroll, AttachmentSizeAccuracyTest tự động không phải retest UI. Còn mở đúng fixture có 3+ chip, resize hẹp và kéo horizontal scroll, từng chip hiện/nhấp được bằng chuột, không clip; metadata estimated hiển thị “≈”, unknown “Không rõ dung lượng”, exact zero chỉ khi thực sự biết, tải trả exact bytes (không coi estimate 24 là lỗi decoded22). Xem From/To/Cc/date format, switch plain/HTML, HTML-only/remote image loopback bị chặn, không script/remote fetch; mở detail/đóng refresh. Ghi ảnh trước/sau layout không có thư thật.

### AM-4366 — Manual SwingWorker/loading (M17) [H, F]

M17 đã thấy disable/loading login/register/folders/send. Còn fault fixture riêng có delay cho GET list/detail; lúc status loading kéo/resize cửa sổ, chọn folder B/đóng dialog, khi request A về không cập nhật sai/đóng rồi revive, không freeze EDT. Khi lỗi network đã provision, thấy message rõ, controls enabled lại, retry chỉ khi kết quả chắc chắn read-only; mutation uncertain không tự retry. Không tạo lỗi bằng kill server chia sẻ; thiếu hook thì BLOCKED.

### AM-4367 — Manual ComposeDialog (M18) [H]

M18 empty To validation, From combo, To/Cc/Bcc/HTML và gửi thành công đã có. Còn thử keyboard chip delimiter dấu phẩy/chấm phẩy/Enter/Backspace, invalid địa chỉ (chặn), >50 recipient, subject >200/body >1 MiB, From không editable, plain↔HTML formatting; đóng draft đã sửa → No giữ draft, Yes bỏ, không gửi. Kỳ vọng validation client trước network, gửi không thành công khi sai; server validation H/F tách riêng. Không dùng địa chỉ thật hoặc gửi bulk.

### AM-4368 — Manual chọn attachment (M19) [H]

M19 lỗi chooser root + clip, đã sửa nguồn/build test nhưng chưa nhìn bản cuối. Còn mở chooser lần đầu từ Compose khi configured dir chưa tồn tại → bắt đầu đúng root fixture hoặc báo lỗi rõ, không fallback personal; chọn multi-select PDF/PNG/text cùng lúc, kéo-thả chỉ file dummy, chip scroll đủ, × xóa cập nhật tổng. Admin cap 1 MiB: chọn hai file tổng vượt, UI chặn; thay kích thước/xóa file sau chọn → gửi bị chặn an toàn, không dùng bytes cũ. Restore cap. Không enumerate personal shell locations, cancel không tạo file ngoài fixture.

### AM-4369 — Manual gửi qua API (M20) [H, F, L]

M20 send self/CC/HTML và To+Bcc/20B/0B đã có; còn dùng cùng marker 4323/4398: người nhận BCC synthetic mở mailbox thấy thư, người To/CC không thấy BCC, download từng PDF/PNG/text/zero-byte từ **thư vừa gửi** đối chiếu SHA nguồn; Admin human Logs tab thấy đúng SEND_MAIL group count/actor, không raw subject/body. F thất bại auth/network có kiểm soát → dialog lỗi, không success giả; nếu không rõ outcome không auto resend, kiểm marker trước quyết định. L Gmail SMTP chỉ dedicated. Không lấy H/F API hash khác thư thay cho UI.

### AM-4370 — Manual disable gửi (M21) [H]

M21 trước đổi flag gửi được, chưa quan sát controls khi false. Còn để MainFrame/Compose đang mở, Admin human tắt ALLOW_SEND_MAIL → toolbar/menu/reply/shortcut và Send disable/note; nếu draft đang mở, giữ nội dung không gửi, stale request 403 xử lý rõ. Restore flag true → UI enable đúng, không tự gửi draft. Ghi ảnh trạng thái trước/sau và flag time; nếu chưa có phiên human hợp lệ, BLOCKED.

### AM-4371 — Manual AdminFrame (M22) [H]

M22 USER shell không admin; admin alias bị gate, chưa vào. Người sở hữu phiên Admin hợp lệ tự dùng UI chính thức, mở AdminFrame, đi qua 4 tab Người dùng/Cấu hình/Thống kê/Nhật ký, loading/error trạng thái rõ; đóng/reopen, resize, role loss của A1 hợp lệ như 4330 → admin card biến mất/không dùng lại quyền cũ. Không dùng UI automation credential alias hoặc token injection. Nếu chưa có người nghiệm thu Admin độc lập, BLOCKED.

### AM-4372 — Manual Users tab (M23) [H]

M23 chưa vào. Trong AdminFrame hợp lệ mở Người dùng, kiểm ≥2 trang fixture, search username/email synthetic, filter USER/ADMIN + enabled/locked, sort tên/ngày, chuyển Next/Prev, page reset khi đổi filter, chọn row đúng target và button enable theo role/state; không chọn row → mutation button disable, refresh giữ selection hợp lệ hoặc clear. Expected totals đúng filter, không trùng ID, metadata không password. F033/034 BE page không chứng minh UI.

### AM-4373 — Manual admin actions (M24) [H]

M24 chưa vào. Trong AdminFrame hợp lệ, chỉ dùng U3/A1 hy sinh, mở detail (mailbox count đúng), Tạo mới khi registration tắt, validation/duplicate; lock/unlock confirm No giữ/Yes đổi row, role change No/Yes, reset dialog policy/one-time temp password che kín không screenshot/copy, delete target confirm No/Yes và list refresh, self action bị chặn, admin bootstrap không xóa/khóa/hạ quyền. Các bước credential chỉ là workflow GUI bình thường của người có thẩm quyền; không yêu cầu agent hay người dùng chạy lệnh bị chặn.
- Expected mỗi success cập nhật row/status và đúng một audit actor/target; error 400/403/404/409 thông báo, không stale selection; không coi UI disable self là last-admin branch. Chia sẻ evidence BE 4325/4328/4330/4331/4332 chỉ nếu cùng thao tác có status sanitized; nếu gate/không có Admin operator, BLOCKED toàn tab.

### AM-4374 — Manual Settings tab (M25) [H]

M25 chưa thao tác tab. Trong AdminFrame hợp lệ mở Cấu hình, kiểm 9 key/labels/user count, 5 boolean và 4 số: MAX_GMAIL_PER_USER 1–50 (legacy 0 chỉ giữ nếu đã có, không đặt mới 0), MAX_ATTACHMENT_SIZE_MB 1–50, MAIL_PAGE_SIZE 10–100, LOGIN_MAX_FAILED_ATTEMPTS 3–20. Kiểm input ngoài range và hai link flags false bị chặn, Cancel maintenance confirm giữ snapshot; Save hợp lệ → loading/metadata/updatedAt/by và success, consumer UI nhận thay đổi (4351/4357/4364/4370). Legacy 0 chỉ kiểm khi fixture legacy đã provision, không sửa DB để ép ca. Restore đủ 9 key, ghi audit ADMIN_CHANGE_SETTING, không lấy API PUT H/F thay tab UI.

### AM-4375 — Manual Stats tab (M26) [H]

M26 chưa vào. Admin human mở Thống kê, ghi 4–5 KPI users/active/locked/admin/Gmail/auth type, registration periods/failed logins và biểu đồ mails/day 7 ngày; refresh sau một mutation synthetic **đã được phép** (tạo hoặc xóa target trong 4373) → delta đúng, admin count giữ; thử empty data/zero chart nếu fixture có. Kỳ vọng active+locked=total, breakdown consistent theo contract multi-auth, không suy breakdown luôn cộng bằng total khi mailbox hai method; ghi số/counter không dữ liệu người dùng. F089/093 stats BE không chứng minh render.

### AM-4376 — Manual Logs tab (M27) [H]

M27 chưa vào. Admin human vào Nhật ký, chọn userId/actor, action MAIL_DELETE/alias DELETE_MAIL, date range khớp hai trang fixture, Next/Prev, sort createdAt DESC; row detail masked, USER không có tab. Export CSV chỉ vào root fixture, xem bằng viewer text an toàn, header/row count/filter/quote đúng, không mở macro; Cancel không tạo file, export lỗi rõ nếu path fixture không ghi được. Không copy CSV raw vào chat; ghi hash/count/action/booleans. F071–073 CSV summary BE không chứng minh UI.

### AM-4393 — Manual delete UI (M28) [H, F]

M28 detail soft No/Yes, Trash permanent Yes và context No đã quan sát. Còn chọn 2–3 marker không liền nhau bằng Ctrl/Shift, right-click Xóa hoặc phím Delete → confirm count đúng, Cancel giữ tất cả; Yes chỉ các marker đã chọn vào Trash, thư đối chứng còn. Trong Trash prompt XÓA VĨNH VIỄN → No giữ nguyên; không xóa thư seed. F fault post-mutation nếu hook riêng được duyệt → “Kết quả xóa chưa xác định”, dừng lô, không tự retry, refresh source/Trash read-back trước quyết định. Không có hook → BLOCKED, không tạo mất mạng thật.

### AM-4395 — Manual flag UI (M29) [H, F]

M29 list star không mở detail, detail star/unread status và context unstar đã có. Còn một marker chưa đọc: click vùng sao bằng chuột, icon vàng trên list vẫn unread bold/selection không tự mở; mở detail, header/list đồng bộ star; click Đánh dấu chưa đọc → row bold, unread filter có lại marker; bỏ sao qua keyboard/context, refresh giữ kết quả, header/list cập nhật. Nếu session/API evidence hợp lệ sẵn có, đọc flags/audit MAIL_FLAG_CHANGE của cùng marker, không dùng F075–080 của fixture khác. F stale UIDVALIDITY/response chậm khi chuyển mailbox → không cập nhật nhầm row, báo reload. Thiếu hook/telemetry ghi phần chưa xác minh.

### AM-4399 — Manual tải attachment (M30) [H, F]

M30 duplicate filename/hash đúng nhưng chooser first-save fallback và clipping/size estimate đã phát hiện. Còn dùng **Client cuối** với root fixture mới chưa có MailApp/<alias>: click Tải xuống → folder được tạo trước chooser, Cancel không ghi; save 2–3 chip bằng chuột, cùng filename khác bytes → suffix (1)/(2), bản trước hash nguyên, size estimate/unknown không nhầm exact. Với tên file traversal synthetic, sanitize không viết ngoài root; path permission lỗi trong root fixture hoặc download lỗi (F hook đã có) → thông báo, không .part/reserved dư/cache sai. Default Downloads chỉ trong VM/user.home test đã chuẩn bị, không chạm Downloads thật; nếu chưa có, BLOCKED phần default.

### AM-4400 — Manual mở/cached file (M31) [H, I, F]

M31 status mở 2 lần, file count/hash và tải lại khi xóa cached file đã có; chưa thấy viewer và request count. Còn human mở .txt/.png/.pdf dummy từ UI, xác nhận viewer OS đúng file fixture (không chụp desktop ngoài fixture), mở lần hai → cùng path, không suffix, counter tải trong telemetry sanitized không tăng; xóa file fixture cached → mở lại đúng hash (đã có, chỉ cần nếu reverify bản cuối liên quan). Logout/login U2 bình thường trong buổi hợp lệ → không dùng cache U1; tên đuôi nguy hiểm chỉ text inert → cảnh báo, bấm No, **không chạy**. F Desktop unsupported nếu môi trường test đã provision → error/file vẫn lưu; không chỉnh OS thật để ép lỗi. Không counter thì ghi chưa chứng minh “no GET”.

## 5. Sáu contract OPEN — quyết định/điều kiện đóng riêng

Không có chấp thuận mặc định. Ba khác biệt an toàn cần owner ghi rõ chọn giữ, sửa đặc tả có version hoặc từ chối; nếu từ chối, mở việc thiết kế mới, không làm yếu bảo mật để đổi checkbox. Ba contract live còn cần evidence Google thật, không được “approve deviation” thay cho mục tiêu live.

### AM-4323 — Contract [D, L]

- Literal §4 yêu cầu audit SEND_MAIL detail có raw `to, subject, attachmentCount`. Hiện tại giữ sender masked, gmailId, recipientCount, attachmentCount, toCount/ccCount/bccCount; không raw địa chỉ nhận/chủ đề/body hoặc hash dễ dò. F014 xác minh count 3, nhóm 1/1/1, actor/time/IP. Hai dạng multipart tương thích và các validation đã có, không phải lỗi gửi cơ bản.
- Rủi ro nếu làm đúng literal: PII/subject nhạy cảm nằm trong log/audit export và retention; không ghi lại raw để demo. Đề xuất owner chấp thuận contract audit metadata-only bằng quyết định có version, nêu quyền xem/retention. Nếu cần đối soát, dùng message correlation ID an toàn và counts, không thêm content vào audit.
- Điều kiện đóng: quyết định chấp thuận rõ, spec/contract update được owner duyệt và verify audit privacy; các yêu cầu SMTP Gmail/BCC/fault còn ở manual/live. Chưa chấp thuận → contract OPEN.

### AM-4340 — Contract [D, I]

- Literal §4 đặt tên/cỡ cột và ENUM cụ thể (users.password, system_settings.key/value, field lengths, role/auth/status), fixed admin password và log console. Triển khai có users.password_hash, setting_key/setting_value, varchar/enums và migration V1–V6 tương thích dữ liệu, normalized identity/multi-auth; admin bootstrap random/BCrypt, must-change, không in mật khẩu cố định. Manual metadata mới chỉ shape/count.
- Rủi ro rename/narrow/đổi enum trên schema đã triển khai: mất dữ liệu/unique conflict, migration không reversible; fixed/admin password log dễ lộ. Đề xuất giữ migration tương thích, chỉ thêm an toàn khi có versioned migration và backup/upgrade plan; không đổi dữ liệu setting đang có hoặc in secret. Đối chiếu schema metadata/legacy ở 4340 manual.
- Điều kiện đóng: owner phê duyệt mapping tên/type và bootstrap policy, 9 key defaults, evidence upgrade không mất dữ liệu từ môi trường synthetic; nếu literal phải giữ, đề xuất migration tương thích được duyệt riêng, không triển khai tại đây. Contract OPEN đến quyết định.

### AM-4391 — Contract [D, L]

- Literal §4 delete Trash ngầm và folder.expunge(), dễ xóa thư khác đã đánh dấu Deleted. Hiện tại 204, owner/UIDVALIDITY check, explicit permanent=true chỉ Trash, resolver folder, UID MOVE/UIDPLUS targeted; COPY→Deleted→UID EXPUNGE fallback fail-closed, rollback flag best-effort, không global expunge/no blind retry khi mất reply. F045–057 xác minh marker duy nhất, guard400/409/404, 204, read-back và đúng hai audit; generic manual local đã PASS, không lặp.
- Đề xuất owner chấp thuận confirmation và targeted semantics, chấp nhận capability thiếu thì 502 fail-closed; ghi rõ caller phải reload source/Trash khi uncertain. Không bỏ guard để đáp ứng literal hoặc xóa thư ngoài marker. L kiểm Gmail Trash/All Mail thật chỉ dedicated, không production.
- Điều kiện đóng: quyết định rõ + contract version/FE wording thống nhất + evidence live đúng marker nếu yêu cầu; chưa chấp thuận → contract OPEN.

### AM-4361 — Contract [L]

Cần handshake thực **cả App Password và XOAUTH2** qua IMAPS/SMTP TLS, marker self-send/tải exact hash/targeted cleanup và kết quả live suite không SKIP/FAIL. GreenMail/OAuth mock, 5 skipped live tests, source code factory không đủ. Owner review remote hostname/trust/timeouts và kết quả được khử nhạy cảm, đồng thời giữ manual human evidence riêng.

### AM-4396 — Contract [L]

Cần dedicated Gmail test, 2FA/App Password thật được cấp phép, standalone Store.connect, link hợp lệ, unique/ownership DTO như §4; .env.example chỉ placeholder, không secret thật. Metadata fixture và F008/F035 không thay live. Xem manual 4396 và privacy dưới.

### AM-4397 — Contract [L]

Cần gửi/nhận ít nhất 5 mẫu theo §4; hiện đã có kế hoạch **7 mẫu** (plain/HTML-only/alternative/PDF/multi PNG+text/UTF-8/nested). Owner duyệt KEEP-FIXTURES và marker inventory; mỗi mẫu thực sự có trong Gmail dedicated, summary/detail/attachment metadata đúng, download bytes/hash đúng. RealGmailMimeFixturesTest SKIP không đủ, không lấy GreenMail làm Gmail thật.

## 6. Ba privacy live OPEN — tiêu chí quan sát an toàn

**Điều kiện chung L:** chính chủ quyết định cấp quyền và thực hiện nghiệm thu trên account Gmail chuyên test, OAuth test app/consent hợp lệ và môi trường test cô lập; không tài khoản cá nhân, không gửi password/token/App Password qua chat, Git, screenshot, command history, clipboard, Postman collection hay log. Không chạy lệnh/runner live trong lượt này; chỉ dùng quy trình đã được chủ dự án duyệt trong [dev-setup.md](../../Mail%20Server/docs/dev-setup.md). Không nhờ agent đi lấy credential hoặc mở private file. Người nghiệm thu chỉ cung cấp báo cáo boolean/count đã scrubbed, không cung cấp raw response/log để “chứng minh không lộ”.

### AM-4361 — Privacy live [L]

Khi hai auth mode thật được nghiệm thu, quan sát UI/DTO/ErrorDto và privacy report của chính test session: credential fields absent, không plaintext/token trong output, auth failure message chung/sanitized, chỉ synthetic marker/attachment hash, không mail ngoài marker. Ghi riêng mode AppPassword/XOAUTH2, outcome, sanitized field-count/secret-match boolean từ cơ chế do owner kiểm soát, logs review status; không đọc/chép raw log, không dùng kết quả skip hoặc source masking thay live. Không có evidence cả hai mode → OPEN.

### AM-4396 — Privacy live [L]

Trong link Gmail dedicated thật, màn hình secret được che, metadata GET list/admin detail không trả appPasswordEncrypted/refreshTokenEncrypted/token/password; storage evidence chỉ aggregate encrypted-shape và owner isolation, không truy vấn giá trị/hash/key. Thử thất bại auth theo quy trình người sở hữu tài khoản được phép, UI không echo secret; audit chỉ email metadata được phép/actor, không credential. Kiểm repo/example placeholder và báo cáo scrubbed từ live session. Không có consent/account → OPEN; không suy ciphertext không rỗng là proof AES đúng.

### AM-4397 — Privacy live [L]

Với 7 thư marker Gmail thật, chỉ owner hợp lệ xem body/attachment đúng nhu cầu, user khác không truy cập (chỉ bằng phiên đã cấp quyền), remote image/script bị chặn, audit/export không raw mail body/secret, attachment bytes chỉ trong folder fixture. Quan sát sample qua UI, ghi marker alias/type/bytes/SHA dummy, không chép raw mail hoặc Message-ID/cookie/token vào biên bản. Cleanup đúng marker, không expunge toàn mailbox. Nếu fixture giữ để human review, ghi explicit opt-in và danh sách marker masked; thiếu evidence live → OPEN.

## 7. Biên bản ghi kết quả và khóa trạng thái

Một dòng cho từng **nhánh còn thiếu**, không chỉ “task PASS”; dùng cùng reference nếu nhánh thật sự đã được quan sát từ cùng một thao tác. Ghi lại H/F/M đã kế thừa, không chạy lại nhánh đã chứng minh. Với local human acceptance, role và tên định danh người nghiệm thu theo quy trình nội bộ, không secret hay thông tin account cá nhân.

| Ref mới | Task / criterion / nhánh | Phiên/revision và scope | Thao tác thực đã làm | Kỳ vọng | Quan sát (đã khử nhạy cảm) | Evidence an toàn | Kết quả | Còn thiếu |
|---|---|---|---|---|---|---|---|---|
| R001 | AM-xxxx / Manual / tên nhánh | local/fixture; revision; thời điểm | hành động GUI hoặc phép đọc được phép | code, state, count | code/state/count thật, không body bí mật | ảnh window-only hoặc sanitized row/hash dummy | OBSERVED / PARTIAL / FAIL / BLOCKED | nhánh chưa xác minh |
| R002 | AM-xxxx / Contract | owner review | quyết định đặc tả | chấp thuận hoặc yêu cầu sửa có version | quyết định thực | mã biên bản/phiên bản | APPROVED / OPEN | các điều kiện triển khai/live còn thiếu |
| R003 | AM-xxxx / Privacy live | dedicated Gmail, mode | ca live được cấp phép | không secret/body trong output sai phạm vi | booleans/counters được scrub | safe aggregate | OBSERVED / BLOCKED LIVE | mode/nhánh còn thiếu |

- Nếu lỗi thật: ghi expected vs actual, revision, tái lập an toàn; chỉ reverify nhánh lỗi sau fix, không tự chuyển checkbox. Nếu setup sai (H117/F074), ghi lỗi setup, không tính là positive PASS.
- Nếu một ca bị safety gate chặn: ghi BLOCKED + tên khả năng bị chặn, **không** retry, chuyển tool/agent, request người khác chạy command hoặc dùng token injection; không hứa tự xử lý sau. Các nhánh độc lập không liên quan vẫn có thể kiểm.
- Manual PASS local do owner quyết định trên evidence hợp lệ cho scope đã thống nhất, không bao gồm human sign-off/live/contract nếu còn thiếu. 6 contract/3 privacy live cần quyết định/evidence riêng. Sau nghiệm thu, người phụ trách aggregate cập nhật §5/review/test matrix đúng số; tài liệu này không chỉnh chúng.

## 8. Kết quả bổ sung sau mốc 65 OPEN

**Mốc hiện tại: 291 PASS + 116 N/A + 61 OPEN (52 manual: 22 BE/30 FE; 6 contract; 3 privacy live).** Phần 1–7 là crosswalk lịch sử 65 OPEN; bảng dưới và §5/acceptance-review là residual hiện tại, có ưu tiên cao hơn nếu kế hoạch cũ liệt kê nhánh đã kiểm. Không cần thực hiện lại G/J/L/S/R2/R3/T/P. Bổ sung vòng này giữ nguyên 61 OPEN; xem báo cáo quan sát mới, không tính thêm PASS khi các nhánh khác chưa đủ.

Backend 250/255 (full + chạy lại 2 suite sau sửa assertion privacy), Client 108/108, tổng 358 PASS / 363 JUnit, 5 SKIP, 0 FAIL, 0 ERROR. Newman chạy lại 140 request/653 assertions trên JAR cuối, provider loopback không phải Google thật. Hai lỗi MIME byte và binary Accept 429 đã sửa và reverify.

| Task | Bằng chứng bổ sung | Phần còn thiếu hiện tại |
|---|---|---|
| AM-4316 | G017–022, G064–065: CREATE quota 409, UPSERT method hiện hữu 200, HTTPS/Gmail scope/offline/PKCE S256/state/poll/redirect shape đúng; state thực quá 5 phút, callback HTML lỗi và poll 410. | Chưa mở browser/Google consent thật từ Client; không lặp lại các nhánh local quota/TTL/shape/owner đã có evidence. |
| AM-4317 | G023–040, G064–066: invalid/expired state, replay, missing scope, profile mismatch đều fail; link không đổi; normal provider SUCCESS; audit OAUTH2_CALLBACK thành công và LINK_GMAIL_DENIED_SCOPE, DTO không credential. | Google token/profile/consent/revoke thật vẫn thiếu; provider loopback không chứng minh Google live. Kiểm privacy log runtime đã scrubbed và mã hóa lưu qua live link, không in secret. |
| AM-4318 | G041–044, G090: revoke bị provider fixture từ chối nhưng unlink 204 và App Password link còn, hai revoke fault được đếm độc lập. | Giữ PASS local; revoke Google thật riêng. |
| AM-4319 | G081–091: cascade OAuth target, provider reject revoke, delete 204, detail 404 và session cũ 401; unrelated owner nguyên vẹn; audit đúng. | Còn last-admin guard độc lập và Google revoke thật; không ép bằng cách xóa/khóa/hạ quyền admin bootstrap. |
| AM-4320 | G002/G054–057: missing account 404, IMAPS listener pause 502, resume giữ marker/UID/UIDVALIDITY. T004–008: 504 GMAIL_CONNECTION_ERROR thật; 2/2 socket timeout tự đóng, active=0; resume giữ 60 thư, UID/UIDVALIDITY. B002–006/B030–037: pool fixture hoạt động, 1 socket sau hai page; idle 13 s về 0; ngắt warm IMAPS trả 502 và 0 socket, restore 200/UIDVALIDITY giữ nguyên. Unit/GreenMail kiểm lease close, không chứng minh mọi lifecycle raw hoặc Gmail thật. | Còn auth failure 422, lifecycle raw Store và Gmail special-use/count thật. Local warm lease, idle release, 502/504, retry/recovery đã kiểm; không lặp. |
| AM-4323 | G045–063, G079–080, G092; J001–005/J009–011: BCC nhận độc lập, nhiều MIME đúng hash sau fix Base64, UTF-8 đúng, header injection/51 recipients 400, SMTP outage 502 rồi phục hồi đúng một marker sau kiểm vắng mặt; audit chỉ success và metadata. T009–013: SMTP timeout 504 JSON trước greeting/auth, hai socket tự đóng; sau restore search marker total0, không retry mutation. A010–011 tái hiện audit fail gây 502 dù đã giao đúng 1 thư. B010–011 sửa thành 200 SENT và đúng 1 thư; B020/B029 log counters, B026–028 phục hồi ghi audit. SMTP auth failure và Gmail thật/contract raw To/subject vẫn thiếu. | Còn SMTP auth failure và Gmail live/human Postman multipart nếu yêu cầu đúng công cụ. Audit storage fault đã kiểm; contract raw To/subject OPEN riêng. |
| AM-4324 | G050–052 phát hiện MIME byte sai; J009–011 xác nhận nguồn/tải 584/68/35 byte và SHA-256 khớp; J021–022 binary Accept nhận 429 JSON + Retry-After đúng path, owner khác 200. | Giữ PASS local; Gmail thật và thao tác UI sau login riêng. |
| AM-4325 (manual local PASS) | G009–012/G015–016: registration=false vẫn tạo USER 201 enabled/mustChangePassword, temporaryPassword chỉ ghi present/no-store; duplicate 409, invalid 400, USER 403, audit ADMIN_CREATE_USER, GET detail không trả credential. | Đủ residual local trước đó. Human sign-off riêng; không suy thành đã thử credential branch bị chặn hoặc mọi UI Admin. |
| AM-4332 | G081–091: cascade unlink với revoke failure thực được provider đếm; target 404, session cũ 401, owner khác còn mailbox, audit đúng. | Còn last-admin guard độc lập (không suy từ self guard); Google thật và human sign-off. Không thử xóa admin bootstrap để ép ca. |
| AM-4338 | L04: JAR mới chạy trên schema V6 đã nâng từ V2, startup giữ nguyên row snapshot; HTTPS owned 8443. | Giữ PASS local, chưa deploy. |
| AM-4339 | L04: cổng 8443 trống, PID đúng fixture, PKCS12/SAN cert mới và curl --cacert xác minh trust/hostname thành công. | Giữ PASS local; không suy chứng chỉ deployment thật đã kiểm. |
| AM-4340 (manual local PASS) | L01/L02/L04: native MySQL 9.5.0 từ V2 có dữ liệu lên V6, users 2→2, aliases 3→2 giữ hai auth methods và owner độc lập, audit 1→2, settings 7→9 giữ custom flags/quota; 4 insert sai bị reject; V5 duplicate email fail trước DDL, hai user nguyên vẹn; startup JAR không đổi row snapshot; port 8443 TLS tin cậy. | Đủ kiểm chứng operational local schema/migration/startup, không phải JUnit thay thao tác. Contract literal schema/seed vẫn OPEN riêng vì chưa có phê duyệt. Không kiểm DB triển khai hiện hữu. |
| AM-4342 (manual local PASS) | L03: trực tiếp chạy AesCipher trong probe độc lập native MySQL, env key synthetic 32 byte, round-trip và IV khác, overhead IV12/tag16, tag sửa và sai key bị từ chối; ciphertext App/OAuth synthetic giữ đúng qua migration, giải mã khớp, chỉ xuất boolean/count. G040 DTO link không credential. | Đủ phép kiểm local của utility không có endpoint Postman riêng; evidence là operational probe, không tuyên bố human sign-off/credential Google thật hay xem raw log. Privacy live vẫn riêng. |
| AM-4343 | G003 415 JSON; G076/077 phát hiện 401 /error thay 429 khi Accept binary; đã sửa GlobalExceptionHandler. J021 429 JSON TOO_MANY_ATTEMPTS + Retry-After=874 và path đúng, J022 owner khác 200; G055/G059 network 502 đúng. T005/T010: timeout 504 JSON đúng path/code/errorCode. P001–003: public config 200, fault bảng settings cô lập sau TTL trả 500 INTERNAL_ERROR đúng bảy field và message đã lọc, restore 200 cùng config. B008 500 INTERNAL_ERROR khi audit insert fail, B019 404 chuẩn, B020/B029 bốn canary log subject/body/path/SQL đều 0. Chỉ phạm vi log fixture mới; còn auth failure 422 và privacy các nhánh khác. | Còn auth failure mapping 422 và privacy log ngoài bốn canary synthetic đã kiểm. Generic 500, 404, 502/504, 415/429 đã quan sát. |
| AM-4344 | G004–006 audit LOGIN, CHANGE_PASSWORD, LINK_GMAIL từ bootstrap hợp lệ; G012/G039/G066/G089/G092 ADMIN_CREATE_USER, OAUTH2_CALLBACK, DENIED_SCOPE, ADMIN_DELETE_USER, SEND_MAIL đúng. G092 hai success, không ghi success cho validation/SMTP fail, không raw To/subject/body. A007–014/B007–029: audit reject; settings rollback 20, rows giữ 11; send/flag/delete xác nhận thành công dù không có audit row, 4 AUDIT_WRITE_FAILED; restore 2 setting changes + 1 send tạo rows14, 1 SEND_MAIL success. Không có outbox phục hồi audit đã mất; LOGIN_FAILED/ADMIN_RESET manual và privacy live còn thiếu. | Còn LOGIN_FAILED/ADMIN_RESET manual, privacy ngoài canary đã kiểm và human sign-off. Lỗi audit storage, rollback và recovery thao tác mới đã quan sát A/B; không có durable outbox để phục hồi row mất. |
| AM-4346 | S01: LoginFrame 940×650 trên Client hiện tại; đổi light/dark và state quan sát được. R2/R3: Main.main/Splash thật, Windows L&F DPI 125%; malformed cert startup chặn LoginFrame. Sửa min-size clipping; 440×580 cuộn tới ping/status và username đủ bounds. | Còn native keyboard/DPI khác và các nhánh login copied §4 (AM-4349), human sign-off; startup/splash/min-size/cert public đã quan sát. |
| AM-4347 | G001 TLS 1.3 trust/hostname thành công; S01 Swing connection check OK (73 ms); L04 HTTPS 8443 với cert fixture tin cậy. R2/R3: Client thật từ chối cert khác, mất server/timeout rõ lỗi, restore cert/HTTPS thành công. Public probe không có auth endpoint; S01/E2E kết nối JAR thật. | Còn token hết hạn/session recovery và xử lý mất kết nối khi đã đăng nhập trực tiếp; không retry credential bị chặn. Public TLS lỗi và recovery đã kiểm. |
| AM-4349 | S01 connection check TLS OK và light/dark; S03 banner maintenance trên Login hiện khi flag bật, biến mất S04. R2 eye với password trống mask true/false/true; R3 focus username→password bằng Swing action (không phải native Enter); ping disable trong request chậm, lỗi/recovery hiển thị đủ sau fix. | Còn countdown 429/locked/maintenance login thực, forced password change, show/hide với password có dữ liệu, native keyboard và human sign-off. Không suy ping thành login loading. |
| AM-4350 | S02: tên 256 ký tự bị reject, 255 ký tự qua validation tên, email sai báo lỗi; S03/S04 disable/re-enable form. | Còn duplicate username/email, password 72-byte/confirm, stale submit 403 và đăng ký hợp lệ; không retry credential bị chặn. |
| AM-4351 | S03/S04 trên Swing thật: form đang mở tự disable 5 field và Submit, banner/nút đăng ký/OAuth đúng flags; restore enable và Back. | Còn clear password khi khác rỗng và stale submit 403, cũng như các nhánh register chưa quan sát ở AM-4350 do credential gate. |
| AM-4358 | G054–063: IMAPS/SMTP listener fault trước connect trả 502, phục hồi đúng marker, không reset mailbox. T004–013: timeout 504 IMAPS/SMTP, fixture 2 s và 2 attempts; từng protocol accepted2/peerClosed2/fixtureClosed0/active0; recovery giữ mailbox, không phát sinh thư. B002–006/B030–037 và regression healthyLeaseReturnsOnceBrokenLeaseClosesAndIdleStoreExpires/poolCapacityAndAccountIsolation: release logical, idle eviction, broken discard, warm outage/recovery thật; SMTP socket 0 sau send. Chưa quan sát toàn bộ raw API lifecycle và remote Gmail TLS. | Còn raw Store/Transport lifecycle và remote hostname/TLS/timeout với Gmail thật; local successful pooled lease, unpooled SMTP close, idle/broken và recovery đã kiểm. |
| AM-4359 | G054–057: App Password mailbox fixture qua IMAPS, fault 502 và resume đúng UID/UIDVALIDITY. T004–013: App Password linked fixture, IMAPS/SMTP pre-auth timeout 504, retry và đóng socket, recovery giữ mailbox; không đọc hoặc nhập credential. B002–006/B030–037 App Password link synthetic: warm pool release/eviction và fault recovery; controller selftest read/close failure regression không báo kết nối tốt giả. Còn wrong credential manual và Gmail 2FA/remote thật. | Còn wrong App Password trực tiếp, raw/probe lifecycle, remote TLS/timeout và Gmail 2FA thật; không retry credential bị chặn. Local warm lease/close/recovery đã kiểm. |
| AM-4360 | G023–040, G064–066 và G090: OAuth loopback thiếu scope/profile mismatch/replay/expiry bị từ chối, recovery SUCCESS; không phải handshake XOAUTH2 tới Gmail. | Còn consent/token refresh/XOAUTH2 live, wrong scope/auth recovery thực được phép. |
| AM-4397 | J001–005/J009–011: local HTML/plain UTF-8 và PDF/PNG/TXT qua SMTP/IMAP đúng hash, metadata ESTIMATED tách decoded size; regression thêm zero-byte. DefaultMailConnectionFactoryGreenMailTest.allSevenDedicatedMimeSamplesRehearseThroughLocalSmtpAndImap chạy chính builder 7 mẫu dành cho live qua GreenMail thật; exact attachment bytes/metadata. Live suite RealGmailMimeFixturesTest vẫn SKIP; safeStage bổ sung canary log; cleanup UID thật chưa chạy. | Bộ 7 MIME trên Gmail dedicated thật, privacy live và cleanup theo UID chưa thực hiện. Builder chung đã rehearsal đầy đủ trên GreenMail, không dùng local fixture đóng live. |
| AM-4398 (manual local PASS) | G045–053 phát hiện file PDF/TXT biến byte qua SMTP. Sửa MailMessageBuilder Base64; J001–005/J009–011 thực gửi/nhận, HTML/plain UTF-8, tải PDF/PNG/TXT bằng Postman/Newman từng request, Content-Length/decoded bytes/SHA-256 đều khớp. Regression MIME standalone và SMTP/IMAP có thêm zero-byte. | Đủ local DTO/builder/MIME của §4; multipart gửi qua HTTPS trực tiếp (không gọi là Postman). Gmail SMTP thật vẫn riêng ở AM-4323/4361; human sign-off riêng. |
| AM-4399 | J009–011: byte/hash/Content-Length đúng trên final JAR, metadata ESTIMATED tách kích thước tải. | Còn trực tiếp Swing bản đã sửa chooser/size label/chip layout, traversal/permission/network/part cleanup/default Downloads; không thay bằng API J hay regression. |
| AM-4400 | J009–011 chứng minh attachment payload chính xác trên final JAR; không chứng minh cache Swing. | Còn viewer, request counter=0 khi cache hit, cache invalidation owner, cảnh báo/cancel executable và unsupported viewer, trực tiếp trên Client bản cuối. |

AM-4325/4340/4342/4398 không còn yêu cầu manual local độc lập phải làm lại. AM-4340 contract chưa phê duyệt. 4342 được nghiệm thu utility qua probe operational có dữ liệu thật trong fixture, không phải Postman/human/live. Tất cả task còn OPEN không nằm trong bảng trên giữ nguyên residual ở phần 3–6. Tập trung đúng những nhánh còn thiếu; không nhờ người khác chạy hộ thao tác agent bị safety gate chặn. Ba quyết định contract và điều kiện Gmail live giữ nguyên.

### Checklist residual hiện tại (đối chiếu toàn bộ 61 OPEN)

- **[AM-4309](Tasks%20Detail/AM-4309-BE-%C4%90%C4%83ng-k%C3%BD-t%C3%A0i-kho%E1%BA%A3n-m%E1%BB%9Bi.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4310](Tasks%20Detail/AM-4310-BE-%C4%90%C4%83ng-nh%E1%BA%ADp-%E2%80%94-tr%E1%BA%A3-JWT.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4311](Tasks%20Detail/AM-4311-BE-L%E1%BA%A5y-th%C3%B4ng-tin-user-hi%E1%BB%87n-t%E1%BA%A1i.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4312](Tasks%20Detail/AM-4312-BE-User-t%E1%BB%B1-%C4%91%E1%BB%95i-m%E1%BA%ADt-kh%E1%BA%A9u.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4314](Tasks%20Detail/AM-4314-BE-Li%C3%AAn-k%E1%BA%BFt-Gmail-b%E1%BA%B1ng-App-Password.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4316](Tasks%20Detail/AM-4316-BE-T%E1%BA%A1o-URL-u%E1%BB%B7-quy%E1%BB%81n-Google-OAuth2.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4317](Tasks%20Detail/AM-4317-BE-Callback-OAuth2-%E2%80%94-%C4%91%E1%BB%95i-code-l%E1%BA%A5y-token.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4319](Tasks%20Detail/AM-4319-BE-H%E1%BB%97-tr%E1%BB%A3-xo%C3%A1-user-c%C3%B3-Gmail-OAuth2.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4320](Tasks%20Detail/AM-4320-BE-Danh-s%C3%A1ch-folder-IMAP.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4323](Tasks%20Detail/AM-4323-BE-G%E1%BB%ADi-mail-SMTP-multipart.md)**: Contract đúng: method, path, request/response schema, error code khớp spec; Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4328](Tasks%20Detail/AM-4328-BE-Kho%C3%A1-t%C3%A0i-kho%E1%BA%A3n-user.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4330](Tasks%20Detail/AM-4330-BE-%C4%90%E1%BB%95i-role-user.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4331](Tasks%20Detail/AM-4331-BE-Admin-%C4%91%E1%BA%B7t-l%E1%BA%A1i-m%E1%BA%ADt-kh%E1%BA%A9u-user.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4332](Tasks%20Detail/AM-4332-BE-Xo%C3%A1-t%C3%A0i-kho%E1%BA%A3n-user.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4340](Tasks%20Detail/AM-4340-BE-Schema-MySQL-%2B-seed-Admin.md)**: Contract đúng: method, path, request/response schema, error code khớp spec. Chi tiết evidence và residual tại §5.
- **[AM-4343](Tasks%20Detail/AM-4343-BE-X%E1%BB%AD-l%C3%BD-l%E1%BB%97i-th%E1%BB%91ng-nh%E1%BA%A5t.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4344](Tasks%20Detail/AM-4344-BE-Audit-log-service.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4346](Tasks%20Detail/AM-4346-FE-Main_java-%2B-UIManager-system-L%26F-%2B-entry-m%E1%BB%9F-LoginFrame.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4347](Tasks%20Detail/AM-4347-FE-ApiClient-HTTPS-%2B-JWT.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4349](Tasks%20Detail/AM-4349-FE-M%C3%A0n-h%C3%ACnh-%C4%91%C4%83ng-nh%E1%BA%ADp.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4350](Tasks%20Detail/AM-4350-FE-M%C3%A0n-h%C3%ACnh-%C4%91%C4%83ng-k%C3%BD.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4351](Tasks%20Detail/AM-4351-FE-%E1%BA%A8n-n%C3%BAt-%C4%91%C4%83ng-k%C3%BD-theo-c%E1%BB%9D.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4352](Tasks%20Detail/AM-4352-FE-MainFrame-khung-r%E1%BB%97ng.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4353](Tasks%20Detail/AM-4353-FE-Dialog-qu%E1%BA%A3n-l%C3%BD-Gmail.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4354](Tasks%20Detail/AM-4354-FE-Form-th%C3%AAm-Gmail-App-Password.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4355](Tasks%20Detail/AM-4355-FE-N%C3%BAt-li%C3%AAn-k%E1%BA%BFt-Google-OAuth2.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4356](Tasks%20Detail/AM-4356-FE-N%C3%BAt-xo%C3%A1-li%C3%AAn-k%E1%BA%BFt-Gmail.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4357](Tasks%20Detail/AM-4357-FE-%E1%BA%A8n-n%C3%BAt-th%C3%AAm-theo-c%E1%BB%9D.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4358](Tasks%20Detail/AM-4358-BE-Interface-MailConnectionFactory.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4359](Tasks%20Detail/AM-4359-BE-K%E1%BA%BFt-n%E1%BB%91i-b%E1%BA%B1ng-App-Password.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4360](Tasks%20Detail/AM-4360-BE-K%E1%BA%BFt-n%E1%BB%91i-b%E1%BA%B1ng-OAuth2-XOAUTH2.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4361](Tasks%20Detail/AM-4361-BE-Test-factory-k%E1%BA%BFt-n%E1%BB%91i-th%E1%BA%ADt.md)**: Contract đúng: method, path, request/response schema, error code khớp spec; Không lộ credential / token / app password / nội dung mail trong response hoặc log; Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4362](Tasks%20Detail/AM-4362-FE-C%C3%A2y-Gmail-%2B-folder-tr%C3%A1i-MainFrame.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4363](Tasks%20Detail/AM-4363-FE-B%E1%BA%A3ng-danh-s%C3%A1ch-mail.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4364](Tasks%20Detail/AM-4364-FE-Ph%C3%A2n-trang-%2B-t%C3%ACm-ki%E1%BA%BFm-mail.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4365](Tasks%20Detail/AM-4365-FE-Panel-chi-ti%E1%BA%BFt-mail.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4366](Tasks%20Detail/AM-4366-FE-SwingWorker-%2B-tr%E1%BA%A1ng-th%C3%A1i-t%E1%BA%A3i.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4367](Tasks%20Detail/AM-4367-FE-Dialog-so%E1%BA%A1n-mail.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4368](Tasks%20Detail/AM-4368-FE-Ch%E1%BB%8Dn-file-%C4%91%C3%ADnh-k%C3%A8m.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4369](Tasks%20Detail/AM-4369-FE-G%E1%BB%ADi-mail-qua-API.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4370](Tasks%20Detail/AM-4370-FE-Disable-So%E1%BA%A1n-khi-b%E1%BB%8B-t%E1%BA%AFt.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4371](Tasks%20Detail/AM-4371-FE-Admin-Khung-AdminFrame-4-tab.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4372](Tasks%20Detail/AM-4372-FE-Admin-Tab-danh-s%C3%A1ch-user.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4373](Tasks%20Detail/AM-4373-FE-Admin-C%C3%A1c-n%C3%BAt-thao-t%C3%A1c-user.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4374](Tasks%20Detail/AM-4374-FE-Admin-Tab-c%E1%BA%A5u-h%C3%ACnh-h%E1%BB%87-th%E1%BB%91ng.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4375](Tasks%20Detail/AM-4375-FE-Admin-Tab-th%E1%BB%91ng-k%C3%AA.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4376](Tasks%20Detail/AM-4376-FE-Admin-Tab-nh%E1%BA%ADt-k%C3%BD.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4391](Tasks%20Detail/AM-4391-BE-Xo%C3%A1-mail-qua-IMAP.md)**: Contract đúng: method, path, request/response schema, error code khớp spec. Chi tiết evidence và residual tại §5.
- **[AM-4393](Tasks%20Detail/AM-4393-FE-N%C3%BAt-Xo%C3%A1-trong-chi-ti%E1%BA%BFt-%2B-context-menu.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4395](Tasks%20Detail/AM-4395-FE-Toggle-sao-%2B-%C4%91%C3%A1nh-d%E1%BA%A5u-ch%C6%B0a-%C4%91%E1%BB%8Dc.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4396](Tasks%20Detail/AM-4396-BE-Chu%E1%BA%A9n-b%E1%BB%8B-Gmail-test-%2B-entity.md)**: Contract đúng: method, path, request/response schema, error code khớp spec; Không lộ credential / token / app password / nội dung mail trong response hoặc log; Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4397](Tasks%20Detail/AM-4397-BE-MIME-fixtures-%2B-Mail-DTO.md)**: Contract đúng: method, path, request/response schema, error code khớp spec; Không lộ credential / token / app password / nội dung mail trong response hoặc log; Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4399](Tasks%20Detail/AM-4399-FE-T%E1%BA%A3i-file-%C4%91%C3%ADnh-k%C3%A8m-v%E1%BB%81-Downloads-%2B-ch%E1%BB%91ng-tr%C3%B9ng-t%C3%AAn.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.
- **[AM-4400](Tasks%20Detail/AM-4400-FE-T%E1%BB%B1-m%E1%BB%9F-file-sau-khi-t%E1%BA%A3i-%2B-nh%E1%BB%9B-file-%C4%91%C3%A3-t%E1%BA%A3i.md)**: Manual test pass (Postman cho BE, chạy Swing cho FE). Chi tiết evidence và residual tại §5.

## 9. Bổ sung A/B và regression cuối

Backend 250/255 (full + chạy lại 2 suite sau sửa assertion privacy), Client 108/108, tổng 358 PASS / 363 JUnit, 5 SKIP, 0 FAIL, 0 ERROR. Không cần làm lại các nhánh A/B đã reverify. Các phần trước và checklist 61 OPEN vẫn giữ làm residual; bảng sau cập nhật evidence, không tự đóng tiêu chí.

| Task | Evidence mới và giới hạn |
|---|---|
| AM-4310 | Bảo vệ privacy: LOGIN_FAILED với identity chưa tồn tại chỉ ghi UNKNOWN_USER, không lưu tên gửi vào. Hai suite AuthRegisterAndChangePasswordTest/AuthServiceTest đã reverify 21/21; không phải manual login mới. |
| AM-4317 | Thay đổi retry link chỉ áp dụng va chạm uk_user_gmail_key; lỗi audit/DB khác không được coi là race có thể retry. GmailMultiAuthIntegrationTest đã chạy; Google thật vẫn thiếu. |
| AM-4320 | B002–006/B030–037: pool fixture hoạt động, 1 socket sau hai page; idle 13 s về 0; ngắt warm IMAPS trả 502 và 0 socket, restore 200/UIDVALIDITY giữ nguyên. Unit/GreenMail kiểm lease close, không chứng minh mọi lifecycle raw hoặc Gmail thật. |
| AM-4323 | A010–011 tái hiện audit fail gây 502 dù đã giao đúng 1 thư. B010–011 sửa thành 200 SENT và đúng 1 thư; B020/B029 log counters, B026–028 phục hồi ghi audit. SMTP auth failure và Gmail thật/contract raw To/subject vẫn thiếu. |
| AM-4343 | B008 500 INTERNAL_ERROR khi audit insert fail, B019 404 chuẩn, B020/B029 bốn canary log subject/body/path/SQL đều 0. Chỉ phạm vi log fixture mới; còn auth failure 422 và privacy các nhánh khác. |
| AM-4344 | A007–014/B007–029: audit reject; settings rollback 20, rows giữ 11; send/flag/delete xác nhận thành công dù không có audit row, 4 AUDIT_WRITE_FAILED; restore 2 setting changes + 1 send tạo rows14, 1 SEND_MAIL success. Không có outbox phục hồi audit đã mất; LOGIN_FAILED/ADMIN_RESET manual và privacy live còn thiếu. |
| AM-4353 | GmailManagerDialog chỉ nhận kết quả refresh mới nhất và bỏ callback đã dispose; regression refresh response ngược thứ tự. |
| AM-4358 | B002–006/B030–037 và regression healthyLeaseReturnsOnceBrokenLeaseClosesAndIdleStoreExpires/poolCapacityAndAccountIsolation: release logical, idle eviction, broken discard, warm outage/recovery thật; SMTP socket 0 sau send. Chưa quan sát toàn bộ raw API lifecycle và remote Gmail TLS. |
| AM-4359 | B002–006/B030–037 App Password link synthetic: warm pool release/eviction và fault recovery; controller selftest read/close failure regression không báo kết nối tốt giả. Còn wrong credential manual và Gmail 2FA/remote thật. |
| AM-4360 | Lifecycle pool phân tách mailbox/method/secret fingerprint; persistence failure sau open đóng lease. Regression tự động không thay Google consent, token refresh hoặc XOAUTH2 live. |
| AM-4361 | Cùng bộ mẫu MIME live được rehearsal local SMTP/IMAPS, kiểm 7 mẫu và exact bytes qua DefaultMailConnectionFactoryGreenMailTest. Năm live test vẫn SKIP; không suy thành Gmail auth thật. |
| AM-4364 | MainFrame/MailboxViewModel reset page khi search/filter khác query đã gửi, không trộn Next/Load More giữa hai query; regression Swing. |
| AM-4366 | Callback stale theo mailbox/session/detail và dispose được bỏ qua; regression pagination/manager/compose, không phải thao tác delay thủ công. |
| AM-4367 | Compose giữ cả recipient chip đã commit và input chưa commit khi giữ draft; phím comma/semicolon/Enter được xử lý, regression giữ draft. |
| AM-4368 | Compose bảo vệ attachment edits trong lúc send, hoàn tất DnD accept/reject, filename literal; source và suite tự động, chưa reverify native chooser/DnD. |
| AM-4369 | Compose bỏ kết quả send thuộc dialog dispose/session cũ, không ghi public config cũ và không bắt đầu send khi session đổi; regression tự động. |
| AM-4370 | Compose không cập nhật từ public config cũ trong worker và bảo vệ session khi send; E2E tự động, không phải quan sát dynamic disable trực tiếp. |
| AM-4372 | AdminFrame reset page theo filter user/log đổi, disable self actions; regression adminPagingRestartsWhenUserOrLogCriteriaChanges. |
| AM-4373 | AdminFrame disable self lock/demotion/delete; không suy thành manual LAST_ADMIN/reset/dialog. |
| AM-4376 | AdminFrame reset/clamp page logs, xóa list stale khi lỗi, tạo thư mục chooser CSV đã cấu hình và bỏ callback export sau dispose; regression paging. |
| AM-4395 | Flag mutation giữ session generation, 409 UIDVALIDITY và outcome không rõ thì reload, không retry mutation. B012–013 backend đọc lại flags đúng; Swing regression riêng. |
| AM-4396 | GmailAccountControllerTest selftest lỗi Inbox đọc/close không báo imapOk giả hoặc cập nhật lastVerified; rehearsal MIME synthetic. Chưa có Gmail chuyên test được cấp quyền và bằng chứng link thật. |
| AM-4397 | DefaultMailConnectionFactoryGreenMailTest.allSevenDedicatedMimeSamplesRehearseThroughLocalSmtpAndImap chạy chính builder 7 mẫu dành cho live qua GreenMail thật; exact attachment bytes/metadata. Live suite RealGmailMimeFixturesTest vẫn SKIP; safeStage bổ sung canary log; cleanup UID thật chưa chạy. |
| AM-4399 | Download completion chỉ thay đổi UI của detail/mailbox generation hiện tại; cache cùng phiên vẫn giữ file thành công. Chưa thao tác native chooser/viewer. |
| AM-4400 | Auto-open chỉ chạy khi detail và session hiện tại khớp; stale completion không bật viewer ngoài ý muốn. Không thay manual cache-hit counter/OS viewer. |

Điều kiện hoàn tất: thao tác GUI nghiệp vụ trong phiên được cấp quyền hợp lệ, Gmail chuyên test/opt-in riêng cho 4361/4396/4397 và quyết định 4323/4340/4391 của chủ dự án. Không nhờ chạy hộ hoặc thử lại thao tác agent bị safety gate chặn. Audit thất bại đã được quan sát, không cam kết hồi phục row bị mất hay atomic commit giữa MySQL và Gmail.

## 10. Vòng C — audit recovery và residual cập nhật

Lũy kế Backend 258/263, Client 110/110, tổng 368 PASS / 373 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng C chạy lại BE 38/38 và FE 31/31 trên mã cuối; các suite khác và Newman 140/653 kế thừa, không phải full run mới. **291 PASS / 116 N/A / 61 OPEN**. [Crosswalk chính xác từng criterion](open-criteria-current.md) là danh sách mới nhất; §3–9 là lịch sử và kế hoạch còn hữu ích, bỏ qua nhánh đã chứng minh.

| Task | Evidence mới |
|---|---|
| AM-4320 | C012: IMAPS pooled 1, SMTP 0 trước restart; C005–009/C018/C028–030 đọc lại cùng mailbox/UID sau flag/move và fail-closed. Không suy thành raw Store/auth failure thật. |
| AM-4322 | Mã detail đọc body qua PEEK trước explicit Seen; tránh đổi Seen ngầm khi parse lỗi. Suite MailServiceFailureRegressionTest và MailServiceGreenMailIntegrationTest chạy lại; không thêm manual detail. |
| AM-4323 | C003–004/C010–017: send 200, một thư, durable CONFIRMED qua restart, đúng một SEND_MAIL. C019–024 lỗi drain không duplicate, C025–032 intent reject 503 không gửi, C033–038 outcome lỗi rồi phục hồi. Partial SMTP có validSent trả kết quả chưa rõ thay 400 giả, không tự resend. C040 privacy aggregate. Không ghi raw To/subject; contract OPEN riêng. |
| AM-4340 | V7 chỉ thêm bảng mail_audit_outbox và index; C startup native MySQL V1–V7 thành công, 4 bảng nghiệp vụ cũ + outbox, 9 settings/1 admin. Không seed/log password cố định; contract OPEN. |
| AM-4343 | C026/C028/C029 trả 503 AUDIT_UNAVAILABLE trước mutation, C039 401 unknown unauth path, C040 8 nhóm aggregate privacy đều 0. Không thay mapping auth failure 422 thật. |
| AM-4344 | C001–041: reject INSERT audit, send/flag/move thành công và giữ CONFIRMED3; restart vẫn 3; restore drain đúng một event mỗi action, tick tiếp không trùng; reject DELETE outbox rollback INSERT audit; reject prepare không mutation; reject outcome lưu PREPARED, phục hồi khi DB hoạt động trong cùng process. Cuối queue0, SEND_MAIL3/MAIL_DELETE1/MAIL_FLAG_CHANGE1. MailAuditRecoveryIntegrationTest 4/4. Không phục hồi row mất từ trước V7, không atomic MySQL–Gmail; crash trước durable confirmation giữ PREPARED cần đối chiếu, không auto success. |
| AM-4358 | C012 đo socket trước restart IMAPS1/SMTP0; lỗi audit/outbox không mở thêm mail lease qua replay. B evidence idle/broken/recovery kế thừa, không lặp. Raw Store/Transport và remote Gmail chưa quan sát. |
| AM-4359 | C operations local GreenMail, read-back sau fault; không kiểm sai App Password hoặc Gmail remote thật. |
| AM-4365 | Detail body PEEK trước explicit Seen, FE callback/lease regression; không suy thành manual layout, remote image/sanitizer. |
| AM-4366 | MainFrame config refresh bỏ callback session generation cũ; config unavailable disable send fail-closed. BE partial delivery/outcome mapping và FE 31/31 suite trọng điểm, không thay manual race. |
| AM-4367 | Compose khóa toàn bộ field/chip/format/file edit lúc gửi, giữ draft khi fail, unknown outcome cảnh báo và resend cần xác nhận riêng; SwingFeatureAcceptanceTest 9/9. C-UI standalone mở cửa sổ thật không account/session; dừng native input do focus không xác nhận, không chứng minh thao tác soạn thủ công. |
| AM-4368 | Compose khóa attachment edit trong lúc SMTP gửi; giữ DnD và chooser từ mốc trước. C-UI standalone chưa thao tác chooser/DnD do focus không xác nhận. |
| AM-4369 | Không tự resend khi SMTP outcome chưa rõ; bảo toàn draft, xác nhận riêng trước retry; 503 AUDIT_UNAVAILABLE chắc chắn chưa gửi, không đánh đồng với uncertain. C gửi thật/read-back + regression Swing, chưa gửi trực tiếp từ phiên Swing. |
| AM-4370 | Public config outage làm send disabled, refresh generation guard, giữ cảnh báo unknown khi flag đổi. Regression; chưa quan sát dynamic authenticated UI. |
| AM-4391 | C007–009/C018 move 204 và read-back Trash đúng 1; C029 reject prepare 503 thư còn nguyên. UID-safe explicit permanent giữ nguyên; contract literal vẫn OPEN. |
| AM-4392 | C010–017 MAIL_DELETE tồn tại bền vững qua restart và drain đúng một; không thêm raw subject/body, không retry xóa. |
| AM-4393 | 503 AUDIT_UNAVAILABLE là pre-mutation failure đã biết; FE không phân loại thành unknown. C029–030 không mutation, chưa manual UI. |
| AM-4394 | C005–006 flag200/read-back đúng; C028–030 reject intent 503 và cờ không đổi; partial STORE regression ghi uncertain, không audit thành công giả. |
| AM-4395 | C005–006/C028–030 backend flags success/fail-closed read-back; FE phân biệt 503 preflight và unknown mutation, không thay manual keyboard/rapid toggle. |
| AM-4399 | SessionStore kiểm size/mtime/fileKey và NOFOLLOW_LINKS để loại cache file thay/đổi/xóa; 3/3 regression; chưa native chooser, permission/disk fault manual. |
| AM-4400 | Cache đã tải bị sửa hoặc thay file được loại bỏ; scope generation hiện tại, NOFOLLOW_LINKS. Metadata check không phải cryptographic hash integrity. SessionStoreTest 3/3; chưa OS viewer/cache-hit counter manual. |

Không lặp C001–041, A/B, các bộ MIME/TLS/AES/migration trước khi có lý do regression. Khi intent PREPARED quá hạn, cảnh báo unresolved, không tự replay nghiệp vụ hoặc ghi success. CONFIRMED/UNCERTAIN replay chỉ audit, insert+delete cùng transaction. Một confirmation chỉ nằm trong RAM khi toàn DB lỗi có thể mất xác nhận nếu crash, nhưng intent còn bền vững; cần đối chiếu hợp lệ, không tự suy thành success. C-UI no mailbox/no auth chỉ thấy cửa sổ Compose và trạng thái ban đầu, native input dừng vì không có focus xác nhận. Ba quyết định literal và ba live task vẫn OPEN; điều kiện chi tiết ở crosswalk. Không yêu cầu người dùng/worker thực hiện hộ thao tác safety gate.

**Regression schema V7-L:** V7-L: Native MySQL database-only, legacy V1/V2 seed synthetic → V6 làm tiền đề → V7. Snapshot 4 bảng trước/sau V7 khớp; outbox mới rỗng, 7 migration success; khởi động JAR vẫn giữ nguyên dữ liệu, 9 settings/1 admin/5 bảng (4 cũ + outbox). Exit 0, datadir fixture đã xóa. Lý do regression riêng: V7 vừa thêm và legacy harness phải nhận version 7; không phải nghiệm thu lại các task migration/AES/TLS cũ. Client/Newman không chạy. Evidence Mail Server/build/acceptance/legacy-upgrade-summary-V7.txt và mysql-metadata-V7-legacy.tsv. Contract AM-4340 vẫn OPEN vì literal bootstrap/schema và môi trường triển khai chưa chấp thuận.

## 11. Vòng D — residual hiện tại (ưu tiên hơn các mục lịch sử)

**292 PASS + 116 N/A + 60 OPEN: 21 BE manual + 30 FE manual + 6 contract + 3 privacy live.** [Danh sách chính xác từng checkbox còn mở](open-criteria-current.md). Không lặp AM-4309 manual local, C/D, các nhánh đã đủ chứng cứ. H009–011/H098: 201 USER, duplicate 409, validation 400 và REGISTER. D028–039: public flag tắt, payload hợp lệ 403 REGISTRATION_DISABLED, user count 0; restore flag, 201 USER/mustChangePassword=false; username trùng và khác hoa/thường 409 USERNAME_TAKEN, email trùng khác hoa/thường 409 EMAIL_TAKEN. D042–043: đúng một user enabled, đúng một REGISTER cùng actor ID. D040–041/D044/D047: thêm cặp đăng ký trùng cho 201/409, đúng một user và một REGISTER; không xác nhận overlap thủ công, race dựa trên MySqlConcurrentAcceptanceTest 2/2 ở mốc trước. Đủ scope manual local Postman runtime từng request của §4 gốc; không phải human sign-off.

D001–003: status aggregate 401/403/200 ADMIN no-store. D005–016: capacity fixture 2, send/flag xác nhận, PREPARED2/localDeferred2; gửi thứ ba 503 AUDIT_UNAVAILABLE, read-back total0; restore drain đúng SEND_MAIL1/MAIL_FLAG_CHANGE1, queue/local0. D017–027: send mới 200, đúng một thư; restart thật khi outcome chưa durable, PREPARED1 vẫn tồn tại, localDeferred0, không thêm audit success, cùng UID/read-back đúng một thư. D045: 8 nhóm canary 0; D046 flags giữ đúng. H2 test chống starvation, giới hạn capacity, trạng thái stale, guard CREATE Trash và mất reply. Không khôi phục outcome RAM đã mất; không auto resolve/replay mail.

D2: Compose standalone không account/session, 760×620, scale 1.25; hit-test/pointer đúng nhưng không nhận AWT MOUSE_PRESSED, focus vẫn JComboBox. Dừng trước typing/chooser/DnD/send, exit 2 và root đã dọn; chưa chứng minh native input hoặc authenticated UI.

Lũy kế Backend 263/268, Client 110/110, tổng 373 PASS / 378 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng D chạy mới BE 43/43, Client 47/47 (gồm E2E 16/16) và Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới. Còn credential thao tác đã bị gate chặn, native Swing input không tới window, live dedicated account/consent, human sign-off và quyết định contract an toàn riêng. Không yêu cầu người dùng chạy thay thao tác bị từ chối; các điều kiện residual là tiêu chuẩn nghiệm thu hợp lệ độc lập.

## 12. Vòng E — residual hiện tại (ưu tiên hơn các mục lịch sử)

**292 PASS +116 N/A +60 OPEN: 21 BE manual, 30 FE manual, 6 contract, 3 privacy live.** Lũy kế Backend 267/272, Client 114/114, tổng 381 PASS / 386 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng E xác minh BE 64/64 (46+18 sau sửa tương thích H2), Client 60/60 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới. Không lặp AM-4309 hay quan sát C/D/E đã đủ. [Danh sách chính xác từng checkbox, yêu cầu gốc và phần thiếu](open-criteria-current.md).

E008–022: hai tài khoản fixture được promote ADMIN, cả hai endpoint Admin 200; hai PUT demote chồng lấn (InnoDB lock-wait=1 E015) trả 200/403 vì kiểm tra actor dưới transaction lock. Actor mất role gọi Admin 403; restore cả hai USER, đúng 1 enabled ADMIN, bootstrap không bị sửa; 4 audit thành công, không audit giả. Đây là overlap native với 2 admin hy sinh và bootstrap vẫn tồn tại, không phải ca đúng 2 active Admin cuối.

E023–041: reject INSERT audit, gửi 200; CONFIRMED giữ attempts/nextAttemptAt qua restart. Reject UPDATE outcome, gửi 200; PREPARED và receipt bền vững, localDeferred1 vẫn khôi phục sau restart thật. Restore outcome: CONFIRMED2/localDeferred0; restore audit: queue0, đúng 2 SEND_MAIL cùng hai eventId và actorId, mỗi thư read-back đúng 1, không gửi lại. V8 không suy PREPARED thành success, không atomic MySQL–Gmail; nếu crash trước receipt durable hoặc cả hai kho mất dữ liệu thì vẫn phải đối chiếu. E042 aggregate 8 nhóm đều 0; canary subject/body/filename và lỗi SQL được dùng trong E, không có chứng cứ mới cho mọi nhóm credential/path/recipient hoặc Gmail live.

E regression: public config null/error fail-closed, register/OAuth UI ẩn khi chưa biết flag hoặc maintenance, clear password/disable form khi mất config, worker kiểm session/dispose trước submit/callback. Client 60/60 gồm E2E16 và SwingFeatureAcceptanceTest13; không manual auth.

E regression: recipient chips scroll riêng, giữ vùng input; commit recipient hợp lệ nguyên nhóm, tối đa 50. Compose giữ generation phiên, kiểm lại sau chooser/confirm; attachment regular/readable NOFOLLOW_LINKS, dedup/size; thiếu status SENT hoặc messageId không rỗng không được báo thành công, giữ draft/unknown và không auto resend. Không coi metadata check là TOCTOU-proof. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS.

Điều kiện tối thiểu còn lại: các nghĩa vụ credential/expiry/forced change chỉ trong phiên được cấp quyền độc lập; không retry/đổi công cụ/giao cho người khác thao tác đã bị gate. Native Swing phải xác nhận window PID/focus và kết quả từng thao tác, không coi EDT/E2E là manual. Google/Gmail live cần tài khoản chuyên test và consent/quyền riêng; không dùng tài khoản cá nhân. Last-admin độc lập phải chứng minh đúng invariant/response mà không khóa/xóa/hạ bootstrap hoặc fake principal. Không tự sửa spec: AM-4323 cần quyết định chấp nhận metadata thay raw To/subject; AM-4340 cần quyết định schema/bootstrap an toàn thay literal fixed password/console; AM-4391 cần quyết định explicit permanent/targeted UID thay global/implicit expunge. AM-4361/4396/4397 còn live contract và privacy, bảy MIME local giữ nguyên. Không yêu cầu ai chạy hộ thao tác bị chặn. Không tự resolve PREPARED, không retry mail mutation đã success/unknown. Journal/backoff vận hành theo dev-setup.md.

**Đóng gói V8:** Dockerfile tạo sẵn `/app/data/mail-audit-receipts` mode 0700 và owner mailapp; Compose mount named volume `mail_audit_receipts` đúng path. Giữ volume và AES key đồng bộ database khi recreate; không xóa volume khi còn unresolved. Chỉ kiểm cấu hình source/indent/mount; chưa build/run container hoặc thử ACL/volume trên Docker. Không đổi Java/JAR sau kiểm thử cuối.

## 13. Vòng F — residual hiện tại (ưu tiên hơn các mục lịch sử)

Vòng F: 293 PASS, 116 N/A, 59 OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live OPEN. 27/47 BE và 1/31 FE manual PASS local agent; human sign-off riêng. Lũy kế Backend 270/275, Client 116/116, tổng 386 PASS / 391 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng F chạy mới BE 44/44, Client 50/50 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới. Danh sách authoritative của từng checkbox còn thiếu, §4 nguyên bản, evidence, phần chưa đạt và điều kiện tối thiểu: [open-criteria-current.md](open-criteria-current.md). Vòng F chỉ đóng AM-4343 manual local; AM-4309 đã đủ D, không lặp. Các số liệu/điều kiện thiếu tại mục 1–12 là lịch sử và không dùng để mở lại nhánh đã đủ.

- Backend: [F201–F226](backend-supplemental-observations.md). 422 GMAIL_AUTH_FAILED/GMAIL_NEEDS_REAUTH local, SMTP post-delivery 502 với đúng một thư và uncertain audit, privacy canary0 đã có. Không lặp các nhánh này, E001/E042 hoặc migration/TLS/MIME/attachment/recovery đã đủ. Raw Store/Transport caller-owned, actual link POST bị disabled/sai credential/quota, last-admin exact-two, LOGIN_FAILED/ADMIN_RESET, Gmail thật và các nhánh auth riêng vẫn thiếu theo từng mục index. Không sửa bootstrap để ép race; không retry bị chặn. MailAuditRecoveryIntegrationTest 13/13 (3 test mới): journal fail closed khi write/remove lỗi, kiểm lock/root và cả hai sibling receipt/pending, inventory không đầy đủ không được tin; giữ conflict/corrupt, không tự xóa. Recovered receipt phải validate lại trước finalize; unreserved finish không biến PREPARED thành success, observation đầu tiên không bị ghi đè; replay chỉ audit, không SMTP/IMAP mutation. Runtime fault test H2 giữ PREPARED khi DB reject, ghi đúng một audit từ observation còn RAM khi DB hồi phục, không duplicate và chỉ mở admission khi receipt xử lý xong. Không suy thành bảo đảm mọi OS crash/disk fault hay atomic MySQL–Gmail.
- Frontend: Compose chọn file regular/readable NOFOLLOW_LINKS, normalize/dedupe, metadata stamp size/mtime/fileKey, kiểm trước submit và sau refresh config; DnD chỉ nhận file hợp lệ khi dialog hiện hành và không gửi. AcceptanceRegressionTest 10/10. Không hash/lock toàn nội dung hoặc loại trừ mọi TOCTOU; chưa native chooser/DnD. CSV export snapshot filter trước chooser; single-flight, reject existing file thay vì overwrite, chỉ text/csv, temp file và move không replace sau guard session/generation/role/disposed; xóa temp, không publish stale response. AdminModuleClientTest5/5: existing file không request/không đổi, response hết guard không tạo target/part. Không chứng minh native chooser/viewer, role-loss manual; không thu hồi file đã export hợp lệ trước logout. E1/C/D2 không xác nhận được native input; không probe lại nếu chưa có thay đổi môi trường được xác nhận. Điều kiện là cửa sổ/focus/event/kết quả thật trên phiên hợp lệ độc lập. EDT/headful E2E không phải manual.
- Contract AM-4323: §4 literal SEND_MAIL có To/subject; hiện masked sender và counts, không raw/hash/body. Phương án an toàn cần phê duyệt là giữ metadata-only; ít raw forensic detail hơn nhưng giữ correlation/count và privacy. Không tự approve, không ghi raw.
- Contract AM-4340: §4 seed admin mật khẩu cố định/log console, schema 4 bảng; hiện random/env bootstrap, hash BCrypt, không plaintext log và additive V1–V8/5 bảng bảo toàn dữ liệu. Cần phê duyệt schema evolved/secure bootstrap; tác động là onboarding phải theo bootstrap an toàn thay vì password chung. Không dùng fixed password.
- Contract AM-4391: §4 implicit permanent Trash và global expunge; hiện explicit permanent=true có confirm, UIDVALIDITY, targeted UID MOVE/UIDPLUS, 204 default/Prefer200 tương thích. Cần phê duyệt; caller cũ phải truyền flag, bảo vệ thư khác. Không bỏ confirmation hoặc targeted deletion.
- AM-4361/4396/4397: mỗi task contract/privacy/manual OPEN riêng. Điều kiện tài khoản Gmail chuyên test/consent/môi trường được phép, cả APP_PASSWORD và XOAUTH2 nếu áp dụng, standalone Store/real handshake/send/read-back/hash/targeted cleanup; 7 MIME local không phải live. Chỉ evidence scrubbed, không secret/đọc thư cá nhân. 5 live SKIP kế thừa.

H/I/F/L/D là phân loại điều kiện, không yêu cầu người dùng thực hiện hộ thao tác bị chặn. Không retry/chuyển giao/tool switch/token injection/fake session. Khi thiếu phiên độc lập được cấp phép, giữ BLOCKED. Không tự duyệt contract hoặc đổi OPEN sang N/A. Không tuyên bố MySQL–Gmail atomic, tuyệt đối crash-proof hoặc 100%.

## 14. Vòng G2 — residual hiện hành (ưu tiên hơn các mục lịch sử)

Lũy kế Backend 277/282, Client 120/120, tổng 397 PASS / 402 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng G2 (sau F) chạy mới BE 132/134 (2 live SKIP do wildcard chọn suite XOAUTH2 live; không chạy Gmail thật), Client 65/65 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; các suite khác kế thừa, không phải full run mới.

293 PASS / 116 N/A / 59 OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Không có tiêu chí mới đủ toàn bộ nghĩa vụ để đóng. Không lặp các thao tác đã đủ hoặc bị safety gate chặn.

V9 native V8→V9 preservation: owner, dual methods, trạng thái và ciphertext synthetic giữ nguyên, revision=0; fresh V1–V9. JPA optimistic version chống stale write/delete; 409 GMAIL_ACCOUNT_CHANGED an toàn. OAuth cache ràng buộc grant/owner, publish sau save; reject scope giảm/invalid_grant chính xác, tránh refresh lặp, persist rotation; fallback chỉ active sau connect, failure không audit thành công, scoped SMTP authenticator, resource close khi save conflict. Local raw lifecycle và factory regression PASS, không chứng minh Gmail live, mọi race hoặc atomic revoke/DB. Audit journal V8 kế thừa không sửa/replay mutation.

Reply-To/reply-all loại self alias/Bcc/dedup, thread header sạch, HTML-only quote text không fetch; attachment snapshot/remove theo file/stamp; Gmail manager generation/disposal trước request/callback, chỉ báo đủ khi cả IMAP/SMTP true, 409 refresh metadata không retry mutation; MainFrame/Compose gate NEEDS_REAUTH đồng nhất BE. Không có native Swing mới, không suy EDT/E2E thành manual.

AM-4323: giữ metadata-only sender che/count, không raw/hash To/subject; AM-4340: giữ bootstrap ngẫu nhiên, không log plaintext, V1–V9 additive; AM-4391: giữ explicit permanent confirmation/UIDVALIDITY/targeted UID, không global expunge. Ba thay đổi literal cần phê duyệt riêng; không tự approve. AM-4361/4396/4397 còn dedicated Gmail live; 7 MIME local và các live test source không thay live evidence.

59 mục có yêu cầu gốc, evidence, phần thiếu và blocker/điều kiện tại [open-criteria-current.md](open-criteria-current.md). Không giao người khác làm hộ thao tác bị chặn. Last-admin exact-two vẫn thiếu; không đổi bootstrap hoặc fake principal. H/I/F/L/D là điều kiện độc lập, không phải yêu cầu bypass. Không tự resolve PREPARED, retry mail mutation hoặc suy MySQL–Gmail atomic.

## 15. Vòng G3 — ưu tiên hơn mục 14

Lũy kế Backend 279/284, Client 124/124, tổng 403 PASS / 408 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng G3 chạy mới BE 100/100, Client 66/66 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; 5 live SKIP kế thừa, không chạy Gmail thật hoặc full suite mới.

293 PASS / 116 N/A / 59 OPEN, 26 task đóng đầy đủ, 52 task còn OPEN; 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Không có tiêu chí mới đủ toàn bộ nghĩa vụ. Không lặp thao tác credential bị gate, C/D2/E1, AM-4309, AM-4343, E001, E042; không thay bootstrap.

G3: MailboxRevisionChainIntegrationTest 2/2 trên H2/JPA thật (OSIV=false) và provider loopback: rotate refresh token commit, snapshot detached nhận đúng revision từ chính save; stale grant/cache bị từ chối trước provider; invalid_grant→NEEDS_REAUTH→fallback thực qua GreenMail có hai revision liên tiếp, đúng một audit mỗi action (mock audit), Store đóng; method bị gỡ thì snapshot cũ không mở raw/wrapper. Factory kiểm owner/id/revision trước credential/cache và sau open, đóng resource/lease nếu conflict. Không thay V9/schema, không retry mutation. Guard không bảo đảm thu hồi kết nối đã cấp hoặc atomic MySQL–Gmail.

G3: delete batch bảo toàn acknowledged count, dừng trước yêu cầu tiếp khi generation đổi hoặc response lỗi/uncertain; cleanup đối chiếu UID/UIDVALIDITY và attachment index, kiểm phiên/lựa chọn trước GET/download/DELETE và sau tải; modal close yêu cầu dừng và đợi kết quả, tránh auto/manual overlap; flag callback cũ và cache/open sau confirm bị chặn. Gmail Manager single-flight, vô hiệu hóa nút khi chưa tải dữ liệu hiện hành, recheck DTO sau confirm, wizard không submit sau close và refresh metadata sau thao tác; sửa thông báo 404, 401 từ Manager/Compose chuyển về login. Không native/manual mới; không suy EDT hay E2E thành native.

AM-4323: contract literal audit To/subject không thể đạt mà vẫn cấm raw To/subject/body; giữ masked sender/count/metadata, không hash thay thế không được duyệt. AM-4340: V9 và schema additive giữ nguyên, bootstrap ngẫu nhiên BCrypt không plaintext console; literal fixed credential/schema cần quyết định. AM-4391: giữ explicit permanent/UIDVALIDITY/UID-targeted deletion, không implicit permanent hay global expunge. Ba phương án an toàn chưa được phê duyệt, giữ OPEN. AM-4361/4396/4397: source harness và local 7 MIME/DTO có evidence; live thật cả hai mode, 2FA/consent/remote TLS, marker/hash/cleanup và privacy vẫn thiếu điều kiện cấp quyền. Không tạo tài khoản/đăng nhập hoặc chạy live.

Đối chiếu đủ 59 mục, yêu cầu gốc, evidence, nghĩa vụ còn thiếu, loại blocker và điều kiện tối thiểu theo [open-criteria-current.md](open-criteria-current.md). Các điều kiện không phải yêu cầu người dùng thực hiện hộ thao tác đã bị chặn. Không tạo fake session, không đổi bootstrap, không suy atomic remote hoặc tự finalize PREPARED.

## 16. Vòng G4 — ưu tiên hơn mục 15

Lũy kế Backend 280/285, Client 126/126, tổng 406 PASS / 411 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. Vòng G4 chạy mới BE 34/34, Client 46/46 (gồm E2E 16/16), Newman 140 request/653 assertion trên JAR cuối; 5 live SKIP kế thừa, không chạy Gmail thật hoặc full suite mới.

293 PASS / 116 N/A / 59 OPEN; 26 task đóng, 52 còn OPEN. 20 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Raw caller-owned local đã quan sát, nhưng không có criterion đủ toàn bộ evidence mới. Ma trận 59 criterion, 171 hàng nghĩa vụ, 92 hàng thiếu prerequisite/evidence. Không đổi checkbox/spec, không tự phê duyệt hoặc thay loại evidence.

G4 R01–R12: observer CLI tương tác từng lệnh trên factory thật từ JAR G4, GreenMail mới loopback; account/password/AES synthetic ngẫu nhiên chỉ trong process, không app login/DB. Raw Store/Transport chưa connect khi cấp; caller connect/close, INBOX READ_ONLY total=2/unread=2, Folder close không đóng Store; FolderNotFoundException và caller abort trước send đều đóng scope. Final storeOpen=false/transportOpen=false, fixtureMessages=2, quit exit0, root removed. Không SMTP DATA fault mới, không suy isConnected thành đo mọi socket OS; không HTTP/Postman/native Swing/Gmail live mới.

G4: MailServiceImpl folderRamCache thêm revision cùng owner/mailboxId; invalidation tất cả revision của đúng owner/mailbox sau mutation. FolderCacheTest 2/2, test mới phân biệt hai revision và cache hit từng revision; TTL vẫn hữu hạn, không đảm bảo read/mutation nguyên tử. MailServiceFailureRegressionTest 22/22 và MailServiceGreenMailIntegrationTest 10/10 chạy lại vì service thay đổi. Không đổi V9, bootstrap, audit journal, OAuth refresh/fallback hay credential/permission path.

G4: Enter chỉ mở detail khi một row; detail delete/cleanup lấy đúng UID đang hiển thị và cùng UIDVALIDITY/lựa chọn, không biến toolbar thành batch. Context/keyboard batch vẫn explicit. Auto-clean dùng cùng requiresReauthBeforeMail cho dual-auth. AdminFrame read workers kiểm generation/disposed; mutation kiểm ADMIN/session trước request, sau confirm và response; single-flight user mutation, recheck target, không publish/copy temp password ở session cũ, clear field khi đóng, không close/cancel create khi pending. Settings guard trước/sau confirm. MailboxRegressionTest 11/11 (detail target regression), SwingFeatureAcceptanceTest 14/14 (late user rows sau dispose); không fake session, không thao tác clipboard/credential trực tiếp. Backend vẫn là authority; guard không thu hồi request đã gửi.

G4: RealGmailIntegrationTest bổ sung raw standalone Store.connect/READ_ONLY/count/close và Transport connect/close trong nhánh App Password opt-in hiện hữu, dùng account/AES chỉ trong memory. RealGmailMimeFixturesTest bổ sung metadata sample ordinal, INBOX UID/UIDVALIDITY/attachment count sau verify exact bytes; không log raw content/secret, giữ mẫu cho manual, không tự xóa hoặc retry khi kết quả chưa rõ. Hai source đã compile, chưa chạy live; vẫn cần dedicated account/consent/permission và verify targeted cleanup/response-log. Không thay consent hoặc token flow.

Ba quyết định D chưa được duyệt: AM-4323 SEND_MAIL metadata-only (EP-M5 của thiết kế gốc cũng yêu cầu metadata; §4 literal To/subject mâu thuẫn privacy), giữ masked sender/count, không raw/hash To/subject/body; AM-4340 bootstrap random/BCrypt, không console plaintext, V1–V9 additive bảo toàn data; AM-4391 explicit permanent=true trong Trash/UIDVALIDITY/UID-targeted, không implicit permanent/global expunge. Không dùng approval để giảm invariant; chỉ chủ dự án xác nhận contract thay thế, không tự sửa §4. AM-4361/4396/4397 vẫn cần live cho manual/contract/privacy riêng.

Chỉ xét các hàng H/N/L/I/F/D còn thiếu trong [ma trận nghĩa vụ](acceptance-obligation-matrix.md) và [residual hiện hành](open-criteria-current.md). H không yêu cầu ai thực hiện hộ thao tác đã bị chặn; chỉ xét điều kiện độc lập. Không lặp raw local G4, C/D/E/F, G2/G3 rotation/fallback, V9 upgrade hoặc C/D2/E1 native probe khi điều kiện chưa đổi.

## 17. G5 — trạng thái hiện hành (thay §16 về số liệu)

78 task / 468 tiêu chí: 293 PASS, 116 N/A, 59 OPEN; 26 task đóng, 52 task còn OPEN. Ma trận hiện có 171 nhóm: 80 có K/M trong scope, 91 còn điều kiện. Không đổi checkbox; G5 không đóng thêm toàn criterion.

Lũy kế Backend 280/285, Client 128/128, tổng 408 PASS / 413 JUnit, 5 live SKIP, 0 FAIL/ERROR trong evidence hợp nhất. G5 chạy mới Client 49/49 (gồm E2E 16/16); Backend/JAR không đổi, BE 34/34 và Newman 140 request/653 assertion là kết quả G4 kế thừa, không chạy lại. Không Gmail thật, native manual, HTTP manual hoặc full suite mới.

G5 xác nhận 4344-M.04 M/K theo D017–027 (restart, PREPARED1/localDeferred0, read-back cùng UID đúng một thư, không thêm audit success) và F MailAuditRecoveryIntegrationTest 13/13. Không auto-finalize/replay mutation; sự cố mất durable evidence vẫn cần đối chiếu nghiệp vụ được phép. Không yêu cầu tạo fault mới để chứng minh lại. AM-4344-M còn 4344-M.02: LOGIN_FAILED và ADMIN_RESET_PASSWORD manual (H).

G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu.

G5: GmailManagerDialog dùng FeatureAdmission ticket, kiểm sau GET và trước link dispatch; off/on không hồi sinh submit cũ, config null fail-closed, lỗi cũ không ghi đè trạng thái mới; re-enable theo config mới. Giữ clear password, session/wizard guards; không có liên kết Gmail live hay native input mới. SwingFeatureAcceptanceTest 16/16 tự động.

G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu.

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

Ticket là guard phía Client, không khóa atomic với request; không thu hồi request đã dispatch. Không thêm HTTP contract, không đổi Backend/bootstrap/DB schema hay live preparation. Không lặp thao tác credential bị gate, không giả session/token, không yêu cầu người khác làm thay.

Ba contract chưa duyệt: AM-4323-C.03 metadata-only (masked sender và counts; không raw To/subject) cần quyết định D, C.04 live vẫn L. AM-4340-C.03 cần D cho bootstrap ngẫu nhiên/BCrypt/force-change, không plaintext console, schema additive V1–V9; local MySQL/seed đã có evidence, có thể đóng riêng contract sau duyệt rõ scope. AM-4391-C.02 cần D cho permanent=true rõ ràng + UIDVALIDITY/UID-targeted MOVE/EXPUNGE, default 204 (Prefer 200 tùy chọn), không global expunge; C.03 live vẫn L. Không tự sửa §4 hoặc coi code hiện có là duyệt.

Điều kiện H/N/L/I/D còn lại theo từng hàng của ma trận và residual: phiên nghiệp vụ được cấp quyền đúng thao tác, native desktop thực sự thuộc fixture và nhận input, dedicated Gmail consent/môi trường live, exact-two-admin fixture hợp lệ không sửa bootstrap, và owner duyệt contract. Không có điều kiện mới trong G5. Không coi E2E là native/manual; không coi GreenMail là Gmail thật. Chưa có bằng chứng đóng thêm criterion.

Yêu cầu gốc, evidence, blocker và điều kiện tối thiểu của từng criterion vẫn tại [open-criteria-current.md](open-criteria-current.md), ID nghĩa vụ tại [acceptance-obligation-matrix.md](acceptance-obligation-matrix.md). Không tạo ma trận mới hoặc mở lại 79 nhóm G4 đã đủ.

## 18. G6 — trạng thái hiện hành (thay §17 về số liệu)

78 task / 468 tiêu chí: 297 PASS, 116 N/A, 55 OPEN; 29 task đóng, 49 task còn OPEN. 297/352 tiêu chí áp dụng = 84,375%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 6 contract, 3 privacy/live. Giữ 171 ID: 81 K/M có evidence, 86 điều kiện trực tiếp thiếu và 4 tham chiếu S chưa đạt; tổng 90 hàng chưa có đủ evidence.

G6 đóng đúng bốn checkbox Manual test pass local AM-4320, AM-4323, AM-4358, AM-4359 từ evidence quan sát đã có, đối chiếu §4 và phân công 02/03; không thêm manual run. AM-4320/4358/4359 đóng task, AM-4323 còn contract D/L. Đây là điều chỉnh phạm vi nghiệm thu, không thay yêu cầu gốc hay đánh dấu live PASS. Bốn hàng S giữ nguyên ID/điều kiện gốc và link đến criterion vẫn OPEN; không tính S là K/M hay N/A. Nếu yêu cầu sign-off đúng công cụ/human ngoài local agent thì vẫn phải xác minh riêng.

AM-4320: PASS manual local theo §4/EP-M1 và chuẩn local đã dùng cho AM-4321/4322: H015/H084/H125, F056, G002/G054–057, T004–008, F204/F222 và raw G4 R02–R05/R09. Count/owner/error/connect/close có observation; cache/revision có regression. 02 C2-TV4 và 03 M08 demo Gmail web/count/special-use/TLS vẫn chưa đạt; giữ 4320-M.05 S/L tại AM-4361-M.02 và AM-4397-M.04. Không claim nghiệm thu Gmail thật.

AM-4323: PASS manual local multipart đúng hành vi đang triển khai: H027/H040/H042/H120/H136–140, F013/F014/F064–065/F069–070, G045–063/G079–080, J001–005/J009–011, T009–013, F205/F213–217, C/D/E, G4 R06–R08/R10. Không tính setup H036 hoặc hash lỗi trước fix. Có direct HTTP từng request (không đổi nhãn thành Postman), delivery/read-back/bytes local và no retry khi uncertain. Giữ 4323-M.05 S/L trong 4323-C.04: Gmail thật cả hai auth mode, delivery/read-back/attachment, TV5. Contract 4323-C.03 D về audit metadata-only vẫn OPEN, không phê duyệt raw To/subject.

AM-4358: PASS manual local scope interface: 03 M07 tách Interface MailConnectionFactory, implementation App Password, XOAUTH2 và Test factory kết nối thật; 02 C3 chốt interface sớm. G4 R02–R10 actual raw Store/Transport caller-owned connect/close và lỗi scope; H/B/T/F wrapper HTTP/lease/timeout; config có code/test. CLI không gọi là Postman. 4358-M.04 S/L remote hostname/TLS/timeout và hai auth mode vẫn phải thỏa AM-4361-M.02/C.02.

AM-4359: PASS manual local scope implementation App Password theo 03 M07: G4 R02–R10 raw AES synthetic, IMAPS/SMTP caller-owned; H/F/G/T connect/close/error/recovery thực local. F204/F205 deny trên link đã có, không phải POST wrong App Password. 4359-M.03 S/H/L vẫn phải thỏa AM-4314-M.03/M.04 (POST link/probe), AM-4396-M.02/M.03 (dedicated 2FA/standalone) và AM-4361-M.02/C.02 (remote TLS/timeout). Không claim Gmail thật.

G6: 4311-M.02 chuyển M/K theo bản ghi JSON đã scrubbed H005/H071/F026: GET /api/auth/me 200, đúng 7 key id, username, fullName, email, role, mustChangePassword, createdAt; không extra key, createdAt parse được, mustChangePassword boolean false. H029/H069/H133/F032 cũng đúng shape. Redactor giữ key, chỉ che value. Không truy cập raw/private manifest, không thực hiện login mới. 4311-M.03 vẫn H: expiry401 và forced-change /me thực.

G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

Evidence G6: Mail Client/build/acceptance/client-round-G6-suite-summary.json; 3 suite: GmailMultiAuthClientTest 5/5, MailboxRegressionTest 13/13, SwingFeatureAcceptanceTest 16/16, tổng 34/34, 0 skip/fail/error. gradlew.bat test --tests *MailboxRegressionTest --tests *SwingFeatureAcceptanceTest --tests *GmailMultiAuthClientTest --max-workers=1 --no-daemon --console=plain --offline, BUILD SUCCESSFUL. Hai test mới; không cộng 32 test rerun vào lũy kế. E2E/MySQL G5, Newman/OAuth loopback/BE G4 kế thừa; không tạo fixture server mới. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A.

Không thay Backend/JAR, bootstrap, schema, API contract, permission, JWT/RBAC/PKCE/TLS/UIDVALIDITY/audit/recovery. Guard Client không thu hồi request đã dispatch hoặc consent remote. Không retry hành vi credential bị chặn, không đổi công cụ, worker, fake session/token, private state hoặc yêu cầu người khác làm thay. Native desktop vẫn thiếu điều kiện xác nhận; không chạy lại C/D2/E1. Không có live/human sign-off mới.

Ba contract D chưa duyệt: AM-4323-C.03 dùng audit metadata-only (masked sender/count, không raw To/subject/body), C.04 vẫn L; duyệt D chỉ giải quyết phần contract chưa đủ live. AM-4340-C.03 bootstrap ngẫu nhiên/BCrypt/force-change, không plaintext console, schema additive V1–V9; local MySQL/seed đã có evidence, owner duyệt rõ thì có thể đóng criterion contract và task. AM-4391-C.02 permanent=true rõ ràng, UIDVALIDITY, UID-targeted MOVE/EXPUNGE, default 204 (Prefer 200 tùy chọn), không global expunge; C.03 vẫn L. Giữ an toàn, không tự duyệt/đổi §4.

H/I/N/L/D còn thiếu theo từng hàng ma trận: phiên nghiệp vụ hợp lệ được cấp quyền độc lập, fixture exact-two-admin hợp lệ không sửa bootstrap, desktop native đúng PID/focus/input, dedicated Gmail consent/scope và kết nối thật, owner phê duyệt contract. Không coi overlap 3 admin là exact-two; không coi E2E là native/manual hay GreenMail là Gmail. Không phát hiện phần triển khai độc lập cần sửa thêm trong scope đã rà; không kết luận mọi lỗi đều hết. Không thể đạt 100% khi điều kiện này chưa thỏa.

Chi tiết 55 criterion và điều kiện tối thiểu tại [open-criteria-current.md](open-criteria-current.md), 171 ID và shared links tại [acceptance-obligation-matrix.md](acceptance-obligation-matrix.md). Không thay điều kiện live bằng local.

## 19. Phê duyệt contract — trạng thái hiện hành (thay §18 về số liệu và D)

78 task / 468 tiêu chí: 298 PASS, 116 N/A, 54 OPEN; 30 task đóng, 48 task còn OPEN. 298/352 tiêu chí áp dụng = 84,659%. BE manual local 31/47, FE manual local 1/31; còn 16 BE manual, 30 FE manual, 5 contract, 3 privacy/live. 171 ID: 81 K/M kế thừa + 3 A phê duyệt = 84 nghĩa vụ đã thỏa; 83 trực tiếp thiếu + 4 shared S = 87 còn thiếu.

Chủ dự án phê duyệt 26/11/2026, sau G6: AM-4323-C.03 A — SEND_MAIL metadata-only (masked sender/counts, không raw/hash To/subject/body), C.04 vẫn L cả hai mode Gmail, delivery/read-back/attachments, shared 4323-M.05. AM-4340-C.03 A — bootstrap từ ADMIN_INITIAL_PASSWORD/cấu hình, BCrypt và forced-change, không log plaintext, không ghi đè Admin, Flyway V1–V9 additive; với evidence MySQL/seed kế thừa, contract và task đã PASS. AM-4391-C.02 A — explicit permanent=true trong Trash, UIDVALIDITY, targeted UID MOVE/UIDPLUS, default 204, Prefer 200 tùy chọn, fail-closed; C.03 vẫn L Gmail Trash/All Mail thật. Không có D còn chờ; phê duyệt không thay bằng chứng live, human/manual, test hay triển khai.

Không chạy test mới, không thay source/JAR, giữ JUnit 410/415 (5 live SKIP), Newman 653/653 kế thừa và E2E 16/16 kế thừa. Mọi mô tả D/chờ phê duyệt ở các mốc trước là lịch sử, không phải residual hiện hành. Chỉ còn 54 criterion theo [open-criteria-current.md](open-criteria-current.md); bỏ AM-4340 khỏi công việc còn mở, không chạy lại schema/seed. AM-4323/4391 chỉ còn L. Các phần hướng dẫn cũ giữ làm lịch sử, không thực hiện lại nhánh đã đủ.
