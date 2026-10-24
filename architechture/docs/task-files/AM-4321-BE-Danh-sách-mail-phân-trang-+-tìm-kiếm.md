# [BE] Danh sách mail phân trang + tìm kiếm

## 1. Thông tin chung

| Trường | Giá trị |
|---|---|
| Loại | Sub Task |
| Module cha | [M08] Nhận mail — IMAP Fetch |
| Owner | TV4 |
| Platform | API |
| Sprint | SPRINT 2 |
| Tuần | Week 2 |
| Kế hoạch | 03/11/2026 → 06/11/2026 |
| Ước lượng | 4.5h |
| Priority | 2 |

---

## 2. Mục tiêu

Hoàn thành `[BE] Danh sách mail phân trang + tìm kiếm` thuộc **[M08] Nhận mail — IMAP Fetch** theo đúng spec `01-thiet-ke-tong-the.md` + `02-danh-sach-endpoint-va-phan-cong.md`.

## 3. Endpoint liên quan

### `EP-M2 GET /api/mails`

- **Path:** `/api/mails`
- **Method:** GET
- **Role:** AUTHENTICATED | **Category:** Mail

**Mô tả:** Danh sách mail phân trang theo folder, FetchProfile header-only; q= tìm bằng IMAP search.

**Lỗi chính:** `—`

## 4. Việc cần làm chi tiết

GET /api/mails?gmailAccountId=&folder=&page=&size=&q=
- Verify ownership; mở IMAP, `store.getFolder(folder)` (URL-decode nếu có `[Gmail]/...`) → 404 nếu null
- `folder.open(READ_ONLY)`, total = `getMessageCount()`
- Tính range: page*size+1 đến min((page+1)*size, total) — Gmail UID order theo `getMessages(start,end)`
- `FetchProfile`: `Envelope`, `Flags`, `ContentInfo` — KHÔNG fetch body
- Nếu `q` present: `folder.search(new OrTerm(SubjectTerm, FromTerm))` hoặc `BodyTerm` tuỳ spec, sau đó paginate trên kết quả
- Map mỗi Message → `{uid (dùng UIDFolder.getUID), from, subject, date, isRead=flags.contains(Flag.SEEN), hasAttachment=parse ContentInfo}`
- Response 200: `{content:[...], page, size, totalElements:total, totalPages:ceil(total/size)}`
- Sắp xếp: mới nhất trước (sort by date desc) — Gmail IMAP thường trả tăng dần nên reverse

## 5. Acceptance criteria

- [ ] Contract đúng: method, path, request/response schema, error code khớp spec
- [ ] Ownership check (gmail_account.user_id == user) nếu đụng Gmail
- [ ] Feature flag check nếu spec yêu cầu
- [ ] Audit log ghi đúng action
- [ ] Không lộ credential / token / app password / nội dung mail trong response hoặc log
- [ ] Manual test pass (Postman cho BE, chạy Swing cho FE)

## 6. Phụ thuộc & tài liệu

- Module cha: **[M08] Nhận mail — IMAP Fetch**
- Backend chung: `JwtFilter`, `JwtProvider`, `AesCipher`, `AuditLogService`, `SystemSettingService` (M01/TV1)
- Nếu đụng Gmail: `MailConnectionFactory` (M07/TV3) — KHÔNG tự viết auth Gmail
- Settings flags (M14/TV6): `ALLOW_REGISTRATION`, `ALLOW_OAUTH2_LINK`, `ALLOW_APP_PASSWORD_LINK`, `ALLOW_SEND_MAIL`, `MAINTENANCE_MODE`, `MAX_GMAIL_PER_USER`, `MAX_ATTACHMENT_SIZE_MB`, `MAIL_PAGE_SIZE`, `LOGIN_MAX_FAILED_ATTEMPTS`

**Docs:**
- `docs/01-thiet-ke-tong-the.md` — kiến trúc, package, DB schema, security
- `docs/02-danh-sach-endpoint-va-phan-cong.md` — contract endpoint + phân công
- `docs/03-task-breakdown.md` — phân công TV × tuần
