# Quan sát Swing bổ sung — 26/11/2026

Phiên G dùng Swing thật trên Client classes hiện tại, HTTPS/MySQL/GreenMail fixture mới và config/download root riêng. Chỉ thao tác công khai trước xác thực; không đi vòng, không thử lại thao tác credential đã bị chặn. Không mở mail cá nhân, không dùng token injection hoặc clipboard secret.

| Ref | Task | Quan sát |
|---|---|---|
| S01 | AM-4346, AM-4347, AM-4349 | LoginFrame 940×650, đổi light/dark; connection check hiện `[OK]` TLS kết nối máy chủ thành công (73 ms). G001 xác nhận TLS 1.3 tin cậy ở HTTP. Không chứng minh login success/forced change/mất session. |
| S02 | AM-4350, AM-4351 | RegisterFrame 580×680: username synthetic, fullName 256 ký tự và email sai; Submit báo giới hạn tên 1–255. Sửa tên 255 ký tự, Submit báo email không hợp lệ. Password để trống, không gửi request credential, không suy thành validation password/409/403 submit đã nghiệm thu. |
| S03 | AM-4351 | Khi form đăng ký đang mở, G007–008 tắt registration/OAuth và bật maintenance. Login hiện banner, nút Đăng ký và OAuth ẩn; năm field và nút Submit của RegisterFrame disable, có giải thích. Quan sát sau public-config poll, không chỉ đọc response. |
| S04 | AM-4351 | G013–014 restore flags: field/nút enable lại, banner biến mất, nút Đăng ký/OAuth hiện lại; Back đóng RegisterFrame. Chưa chứng minh clear password đang có dữ liệu hoặc stale submit 403. |

Ảnh cửa sổ fixture đã lưu `Mail Client/build/acceptance/ui-evidence-G/`: `G-public-login-dark.png`, `G-register-disabled.png`, `G-login-maintenance.png`, `G-register-restored.png`. Bằng chứng chính là state/control/text quan sát trong operator console; không tuyên bố đã kiểm tra từng pixel của ảnh. Phiên Swing đã đóng, fixture đã dọn.

Các ca M01–M31 cũ giữ nguyên; S01–S04 không phải 4 task PASS. Không thay cho thao tác hậu xác thực trên MainFrame, AdminFrame, GmailManagerDialog, Compose, attachment download/open/cache, delete/undo, race và phục hồi UI. Những sửa layout/file chooser trước đây đã có regression tự động, nhưng trực tiếp trên UI hậu đăng nhập vẫn bị chặn. S03/S04 đã giải quyết nhánh form đang mở disable/re-enable; AM-4351 vẫn OPEN do chưa kiểm clearing password khác rỗng và stale submit. Hướng dẫn phần còn thiếu xem `remaining-acceptance-guide.md`, không retry qua agent thao tác credential bị gate.

## R2/R3 — Swing public startup, kích thước tối thiểu và lỗi kết nối

`Mail Client/scripts/PublicSwingProbe.java` chạy Main.main/EDT thật với config/home riêng và HTTPS loopback chỉ phục vụ GET `/api/public/config`. Hai chứng chỉ synthetic độc lập; không có account, endpoint auth, token hoặc private manifest. Lệnh operator chỉ điều khiển cửa sổ/control public, không phải native keyboard/mouse hay human sign-off. Thử R đầu tiên lỗi helper khi đọc JLabel null, đã sửa helper và dọn phiên; không tính là lỗi ứng dụng.

| Ref | Quan sát trực tiếp |
|---|---|
| R2 startup | Splash OPEN 388 ms, CLOSE 2203 ms, LoginFrame OPEN 2272 ms; Windows L&F, DPI scale 1.25, frame 940×650. Khi startup với file cert malformed, Splash đóng, JDialog lỗi khởi động active, không có LoginFrame/request mới. |
| R2 min-size | 440×580: nút ping 183×28 nhưng visible 183×13; status visible 0×0. Đây là lỗi UI thực. |
| R2 TLS/offline | Cert đúng OK 5 ms; server dừng: lỗi `null`; đổi cert server sang cert khác cùng port: TLS bị từ chối, request count không tăng, raw PKIX text rộng 766 px trong label 362 px. Restore cert gốc: OK 43 ms. |
| R2 eye | Password để trống trong cả ba state mask true/false/true. Không nhập credential, không chứng minh thao tác với password có dữ liệu. |
| R3 startup/min-size | Sau sửa LoginFrame: Splash OPEN 465 ms, CLOSE 2459 ms, LoginFrame OPEN 2473 ms; DPI 1.25, frame active. 440×580, scroll tới nút ping: visible đầy đủ 183×28, status 301×16; scroll về username: visible 255×34. |
| R3 faults | Cert đúng OK 9 ms; server dừng báo Không kết nối được máy chủ; cert khác bị từ chối, HTTP count không tăng, báo Chứng chỉ TLS không hợp lệ. Server delay 7 s: nút ping disable, trạng thái Đang kiểm tra kết nối; sau timeout báo Kết nối quá thời gian chờ và nút enable lại. Restore cert/response: OK 5 ms. Các status được quan sát đủ vùng hiển thị. |
| R3 focus | Username đã focus trên frame active, lệnh Swing postActionEvent đưa focus tới password đang trống/masked; không có request auth. Không phải phím Enter vật lý. |

Fix: LoginFrame dùng viewport cuộn dọc thay card bị cắt khi cửa sổ nhỏ; focus cuộn tới control, kết quả ping cuộn tới status. Chặn ping trùng trong lúc chạy, không cập nhật frame đã dispose, thông báo lỗi an toàn thay raw exception/null, phân biệt TLS và HTTP loopback. Không thay đổi TLS trust/JWT/auth. Regression `UiLayoutRegressionTest#minimumLoginSizeKeepsBottomControlsReachable` và `ClientSecurityAndResilienceTest#publicConnectionCheckDoesNotOverlapOrExposeRawErrors` đã chạy pass.

Evidence `Mail Client/build/acceptance/public-ui-probe-R2/observations.txt` và `public-ui-probe-R3/observations.txt`, kèm render từng cửa sổ PNG trong thư mục tương ứng. R2: 7 request public/0 rejected; R3: 6 public/0 rejected; cả hai exit 0 và TEMP CLEANUP OK. Không claim đã review từng pixel; state/bounds/text là evidence chính. HTTPS public probe không phải JAR BE; kết nối HTTPS tới JAR đã có S01 và E2E. Giữ AM-4346/4347/4349 manual OPEN do các nhánh auth/session/native input và human sign-off còn thiếu; không lặp thao tác credential bị chặn.

## Regression tự động bổ sung — pagination, race, draft, attachment, admin

Không có phiên manual Swing authenticated mới; không thử lại login/credential bị chặn và không thay bằng token injection. Các thay đổi source và test dưới đây độc lập với các quan sát M/S/R đã lưu.

| Task | Sửa lỗi nghiệp vụ/race | Phạm vi chứng cứ |
|---|---|---|
| AM-4353 | GmailManagerDialog latest refresh wins, dispose-on-close; response cũ không ghi đè list mới. | Regression `gmailManagerIgnoresLateAccountListAfterNewerRefresh`. |
| AM-4364, AM-4366 | Next/Load More reset page khi search/filter khác query đã gửi; không append hai query khác nhau; callback stale bị bỏ qua. | SwingFeatureAcceptanceTest paging case mở rộng; E2E tự động. |
| AM-4367–4370 | Giữ recipient chip và text chưa commit trong draft; comma/semicolon/Enter commit; DnD complete/accept/reject; không sửa attachment khi đang send; không ghi config cũ hoặc callback send của session/dialog đã hết hiệu lực. | `composePreservesPendingAndCommittedRecipientDrafts`, suite tự động; native chooser/DnD và dynamic flag manual còn thiếu. |
| AM-4372, AM-4373, AM-4376 | Reset paging user/log theo filter, clamp page log, clear stale results khi lỗi, disable self lock/demotion/delete, tạo thư mục CSV chooser và không cập nhật dialog đã dispose. | `adminPagingRestartsWhenUserOrLogCriteriaChanges`, AdminModuleClientTest và E2E; không thay manual Admin. |
| AM-4395 | Flag mutation giữ session generation; 409 UIDVALIDITY_CHANGED hoặc outcome không rõ thì reload, không tự retry. | B012–013 read-back ở BE, Client regression tự động; không suy thành native keyboard hoặc stale mutation manual. |
| AM-4399, AM-4400 | Download/auto-open theo current attachment detail, mailbox/session generation; completion cũ không bật viewer ngoài ý muốn; giữ file tải thành công/cache của cùng phiên. | Regression tự động và E2E; cache counter/viewer OS/permission/native chooser trực tiếp còn thiếu. |

Client full cuối: 20 suite, 108/108 PASS, 0 SKIP/FAIL/ERROR; SwingFeatureAcceptanceTest 8/8 (tăng 3), ClientLiveServerE2ETest 16/16. Một lần compile đầu phát hiện thiếu local SessionStore variable trong update flags, đã sửa; compileTestJava và full acceptance sau sửa thành công. Đây là automated evidence, không phải mới thêm thao tác M01–M31 hoặc human sign-off. Giữ 1 manual local PASS/30 OPEN của FE.

## Vòng C — nghiệp vụ gửi không rõ kết quả, config và cache

Compose freeze field/from/recipient/chip/format/file edit lúc send, giữ draft khi fail, cảnh báo kết quả chưa xác định và yêu cầu xác nhận riêng trước gửi lại. ApiException không coi AUDIT_UNAVAILABLE 503 là mutation uncertain. MainFrame bỏ stale public-config callback theo session generation, fail-closed send khi config unavailable; SessionStore kiểm size/mtime/fileKey và NOFOLLOW_LINKS khi cache hit, loại file bị sửa/thay/xóa. Metadata không phải hash nội dung.

FE 5 suite targeted 31/31, 2 test mới; lũy kế 110/110 (suite không chạy lại giữ evidence trước). Không phải manual. C-UI: `Mail Client/scripts/LocalComposeProbe.java` khởi động ComposeDialog thật với zero account, không login/session/token/network endpoint, config/download root riêng. Quan sát window 760x620, ô To/Cc/Bcc, subject/body, HTML, attachment (giới hạn 1 MB), Hủy/Gửi trạng thái ban đầu. Native mouse click ô To thực hiện nhưng không xác nhận focus; probe dừng trước type/Enter/chooser/DnD/send, fixture root removed=true. Evidence `Mail Client/build/acceptance/ui-compose-C/observation.txt`. Không tính thao tác nhập liệu hoặc manual PASS. Chưa quan sát lại authenticated UI vì không có phiên hợp lệ độc lập; không retry credential gate. [Residual 30 manual FE](open-criteria-current.md).

## Vòng D2 — Compose độc lập

D2: Compose standalone không account/session, 760×620, scale 1.25; hit-test/pointer đúng nhưng không nhận AWT MOUSE_PRESSED, focus vẫn JComboBox. Dừng trước typing/chooser/DnD/send, exit 2 và root đã dọn; chưa chứng minh native input hoặc authenticated UI. Evidence `Mail Client/build/acceptance/ui-compose-D2/observation.txt`. Lần launch đầu lỗi quoting Java và lần không tty kết thúc stdin, không tính quan sát. Không đổi production Client, không tạo fake account/session, không giả EDT event là native. Client 47/47 tự động chạy mới; manual FE vẫn 30 OPEN.

## Vòng E1 — native desktop không xác nhận cửa sổ fixture

E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS. Windows user interactive/session1, input desktop accessible và cùng thread desktop; không coi headful là khả năng native control. Lệnh chẩn đoán Win32 đầu lỗi cú pháp PowerShell literal uint, chạy lại đúng kiểu [uint32] cho hai kiểm tra false. Không click thử trên cửa sổ không thuộc fixture. Exit0 chỉ là cleanup, không manual PASS. Evidence `Mail Client/build/acceptance/compose-E1/native-desktop-check.txt` và observation.txt.

E regression: public config null/error fail-closed, register/OAuth UI ẩn khi chưa biết flag hoặc maintenance, clear password/disable form khi mất config, worker kiểm session/dispose trước submit/callback. Client 60/60 gồm E2E16 và SwingFeatureAcceptanceTest13; không manual auth.

E regression: recipient chips scroll riêng, giữ vùng input; commit recipient hợp lệ nguyên nhóm, tối đa 50. Compose giữ generation phiên, kiểm lại sau chooser/confirm; attachment regular/readable NOFOLLOW_LINKS, dedup/size; thiếu status SENT hoặc messageId không rỗng không được báo thành công, giữ draft/unknown và không auto resend. Không coi metadata check là TOCTOU-proof. E1: standalone Compose không account/session, 760×620 scale1.25, To608×24, send disabled. Windows input desktop cùng session; Swing hit-test/pointer đúng nhưng Win32 foreground/window-under-cursor không thuộc PID fixture. Dừng trước mouse press/typing; đóng cửa sổ, root removed. Không tính manual PASS.

Không có fake authenticated session; 30 FE manual vẫn OPEN.

## Vòng F — regression Compose và audit CSV, không thêm manual Swing

Compose safeLinkHref chỉ http/https, host hợp lệ, không userInfo, escape href; kiểm dialog còn hiện hành sau modal và không sending. AcceptanceRegressionTest 10/10. Chưa native recipient/draft/reply/forward. Compose chọn file regular/readable NOFOLLOW_LINKS, normalize/dedupe, metadata stamp size/mtime/fileKey, kiểm trước submit và sau refresh config; DnD chỉ nhận file hợp lệ khi dialog hiện hành và không gửi. AcceptanceRegressionTest 10/10. Không hash/lock toàn nội dung hoặc loại trừ mọi TOCTOU; chưa native chooser/DnD.

CSV export snapshot filter trước chooser; single-flight, reject existing file thay vì overwrite, chỉ text/csv, temp file và move không replace sau guard session/generation/role/disposed; xóa temp, không publish stale response. AdminModuleClientTest5/5: existing file không request/không đổi, response hết guard không tạo target/part. Không chứng minh native chooser/viewer, role-loss manual; không thu hồi file đã export hợp lệ trước logout.

Client fresh 50/50: AcceptanceRegressionTest10, AdminModuleClientTest5, SwingFeatureAcceptanceTest13, MailApiClientNewEndpointsTest6, ClientLiveServerE2ETest16 (E13–15 tự động headful). Lũy kế116/116. Backend F213–217 xác minh hậu quả gửi chưa rõ thật trên GreenMail, không phải GUI send observation. Không chạy lại probe C/D2/E1 vì chưa xác nhận desktop/focus/event phù hợp; giữ 30 manual OPEN, 1/31 local PASS kế thừa. Không fake session, không retry nhập credential bị chặn. Attachment metadata không hash/lock; CSV no-overwrite là lựa chọn bảo toàn file, user phải chọn tên khác; không tuyên bố mọi race filesystem hoặc thu hồi file export trước logout.

## Vòng G2 — sau vòng F (kết quả hiện hành)

Reply-To/reply-all loại self alias/Bcc/dedup, thread header sạch, HTML-only quote text không fetch; attachment snapshot/remove theo file/stamp; Gmail manager generation/disposal trước request/callback, chỉ báo đủ khi cả IMAP/SMTP true, 409 refresh metadata không retry mutation; MainFrame/Compose gate NEEDS_REAUTH đồng nhất BE. Không có native Swing mới, không suy EDT/E2E thành manual.

Client 65/65 PASS (E2E16/16). Không probe lại C/D2/E1, chưa xác nhận foreground/focus/native event. 30 FE manual vẫn OPEN.

## Vòng G3 — kết quả hiện hành

G3: delete batch bảo toàn acknowledged count, dừng trước yêu cầu tiếp khi generation đổi hoặc response lỗi/uncertain; cleanup đối chiếu UID/UIDVALIDITY và attachment index, kiểm phiên/lựa chọn trước GET/download/DELETE và sau tải; modal close yêu cầu dừng và đợi kết quả, tránh auto/manual overlap; flag callback cũ và cache/open sau confirm bị chặn. Gmail Manager single-flight, vô hiệu hóa nút khi chưa tải dữ liệu hiện hành, recheck DTO sau confirm, wizard không submit sau close và refresh metadata sau thao tác; sửa thông báo 404, 401 từ Manager/Compose chuyển về login. Không native/manual mới; không suy EDT hay E2E thành native.

Client 66/66 PASS, E2E16/16. Không probe lại native; 30 FE manual OPEN.

## Vòng G4 — kết quả hiện hành

G4: Enter chỉ mở detail khi một row; detail delete/cleanup lấy đúng UID đang hiển thị và cùng UIDVALIDITY/lựa chọn, không biến toolbar thành batch. Context/keyboard batch vẫn explicit. Auto-clean dùng cùng requiresReauthBeforeMail cho dual-auth. AdminFrame read workers kiểm generation/disposed; mutation kiểm ADMIN/session trước request, sau confirm và response; single-flight user mutation, recheck target, không publish/copy temp password ở session cũ, clear field khi đóng, không close/cancel create khi pending. Settings guard trước/sau confirm. MailboxRegressionTest 11/11 (detail target regression), SwingFeatureAcceptanceTest 14/14 (late user rows sau dispose); không fake session, không thao tác clipboard/credential trực tiếp. Backend vẫn là authority; guard không thu hồi request đã gửi.

Client 46/46 fresh, E2E16/16. Không có bằng chứng desktop đổi nên không probe native lại; 30 FE manual OPEN.

## G5 — 26/11/2026

G5: FeatureAdmission ticket chỉ hợp lệ cho epoch cờ đang bật. RegisterFrame kiểm sau GET public config và trước dispatch; off/on vô hiệu submit cũ, clear password/confirm khi off, lỗi cũ không ghi đè trạng thái hồi phục. Không hồi sinh dữ liệu submit cũ; cần thao tác mới. SwingFeatureAcceptanceTest 16/16, kiểm ticket và form tự động; native journey còn thiếu.

G5: GmailManagerDialog dùng FeatureAdmission ticket, kiểm sau GET và trước link dispatch; off/on không hồi sinh submit cũ, config null fail-closed, lỗi cũ không ghi đè trạng thái mới; re-enable theo config mới. Giữ clear password, session/wizard guards; không có liên kết Gmail live hay native input mới. SwingFeatureAcceptanceTest 16/16 tự động.

G5: ComposeDialog kiểm FeatureAdmission trước/sau xác nhận, sau GET config và ngay trước dispatch. Off/on trong preflight vô hiệu request cũ, giữ draft editable, không đánh dấu uncertain khi chưa dispatch, không auto-retry; cần click mới. Test latch composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft xác nhận 0 dispatch; SwingFeatureAcceptanceTest 16/16. Không thay kết quả request đã gửi; BE vẫn là chốt, native journey còn thiếu.

Evidence G5: Mail Client/build/acceptance/client-round-G5-suite-summary.json (4 suite; 12 AcceptanceRegressionTest, 16 ClientLiveServerE2ETest, 5 GmailMultiAuthClientTest, 16 SwingFeatureAcceptanceTest; 49/49, 0 skip/fail/error); Mail Server/build/acceptance/mysql-metadata-round-G5.tsv (MySQL native 9.5.0, utf8mb4_unicode_ci, V1–V9, 9 settings, 1 admin, 5 bảng). Runner -SkipBuild -DatabaseEngine MySQL -ClientTests với 4 suite trên, không -RunPostman; BUILD SUCCESSFUL, fixture đã dọn. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A. Newman G4 140/653, OAuth loopback G4 và BE XML G4 chỉ kế thừa.

Hai test mới: featureAdmissionInvalidatesOldTicketsAcrossDisableAndRecovery, composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft. Không thao tác native mới; 1/31 generic manual PASS, 30 OPEN giữ nguyên.

## G6 — 26/11/2026

G6: MainFrame và MailboxViewModel dùng FeatureAdmission cho OAuth2 link: kiểm ticket/session trước và sau GET config, trước start, sau authorize response và trước Desktop.browse. Off/on không hồi sinh click cũ; config null/disabled fail-closed. Chỉ một poll dialog/worker; close/logout/cancel vô hiệu session generation, không poll tiếp hoặc nhận success trễ, success cần accountId hợp lệ/đúng reauth target. Nếu browser không mở được thì báo lỗi an toàn, không hiển thị raw authorize URL. Sau khi browser đã mở chỉ dừng chờ local, không giả remote consent bị hủy; không retry mutation. Hai test mới oauthPreflightRejectsInvalidatedClickAndUnavailableConfig và oauthPollStopsBeforeDispatchAndDiscardsLateSuccessAfterSessionChange kiểm preflight/late response bằng dữ liệu synthetic, không tạo session/token. MailboxRegressionTest 13/13; native/live vẫn OPEN.

Evidence G6: Mail Client/build/acceptance/client-round-G6-suite-summary.json; 3 suite: GmailMultiAuthClientTest 5/5, MailboxRegressionTest 13/13, SwingFeatureAcceptanceTest 16/16, tổng 34/34, 0 skip/fail/error. gradlew.bat test --tests *MailboxRegressionTest --tests *SwingFeatureAcceptanceTest --tests *GmailMultiAuthClientTest --max-workers=1 --no-daemon --console=plain --offline, BUILD SUCCESSFUL. Hai test mới; không cộng 32 test rerun vào lũy kế. E2E/MySQL G5, Newman/OAuth loopback/BE G4 kế thừa; không tạo fixture server mới. JAR SHA256 giữ G4: 3BBF603D265748D73CFE3FE8D4CB9B123F0FA65F0330FF1D268CC27B8C406B8A.

Không thao tác native mới, 1/31 generic manual PASS, 30 OPEN giữ nguyên.
