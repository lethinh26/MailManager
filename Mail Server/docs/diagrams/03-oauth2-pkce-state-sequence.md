# Sơ Đồ Tuần Tự Liên Kết Đa Phương Thức Gmail (OAuth2 PKCE + App Password + Fallback)

## 1. Mô Tả Kiến Trúc Đa Phương Thức Trên Cùng Một Hộp Thư

Mỗi hộp thư Gmail được định danh duy nhất theo `(user_id, email_key)` trong bảng `gmail_accounts` (Flyway V3), cho phép lưu đồng thời cả **Google OAuth2** (`refresh_token_encrypted`, giao thức IMAPS/SMTP `SASL XOAUTH2`) và **App Password** (`app_password_encrypted`, giao thức IMAPS/SMTP chuẩn):
1. **Chuẩn hóa `email_key` dùng chung Server & Client:** Hạ chữ thường, cắt khoảng trắng; riêng `gmail.com` / `googlemail.com` bỏ dấu chấm `.`, bỏ `+tag` ở phần local và quy đổi `googlemail.com` $\rightarrow$ `gmail.com`.
2. **Upsert an toàn trong transaction (`GmailAccountLinkService`):** Cả OAuth2 Callback và `POST /api/gmail-accounts/app-password` đều tìm theo `(user_id, email_key)`, giữ nguyên `id`, `displayName` và phương thức còn lại; xử lý tranh chấp đồng thời (race condition) bằng ràng buộc `UNIQUE(user_id, email_key)` kết hợp retry an toàn 1 lần.
3. **Trang Callback HTML & Phản hồi API:** Hiển thị rõ email Google trả về (đã che `c***@gmail.com`) để phát hiện chọn nhầm tài khoản; trả `outcome` (`CREATED`, `METHOD_ADDED`, `METHOD_UPDATED`) và thông điệp tiếng Việt thân thiện (không dùng từ *"lỗi"* khi thêm phương thức thứ hai).
4. **Tự động Fallback (`DefaultMailConnectionFactory`):** Khi `preferredMethod = OAUTH2` gặp `ReauthRequiredException` mà hộp thư có App Password, Server tự dùng App Password cho thao tác đó, đặt `fallbackActive = true`, ghi audit `FALLBACK_TO_APP_PASSWORD` (không ghi bí mật), **không xóa `NEEDS_REAUTH`** của OAuth2 và không retry lỗi xác thực nhiều lần.

---

## 2. Sơ Đồ Tuần Tự (Mermaid)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant Client as Mail Client (Swing)
    participant GmailCtrl as GmailAccountController / OAuth2Controller
    participant LinkSvc as GmailAccountLinkService
    participant OAuthSvc as OAuth2Service
    participant StateStore as OAuth2StateStore (RAM)
    participant Browser as Trình duyệt Web
    participant GoogleAuth as Google OAuth2 Server
    participant DB as MySQL (UNIQUE user_id, email_key)
    participant MailFac as DefaultMailConnectionFactory (IMAPS/SMTP)

    rect rgb(235, 245, 255)
        note over User, DB: Luồng A — Liên kết / Thêm Google OAuth2 (PKCE S256 + Upsert theo email_key)
        User->>Client: Bấm "Đăng nhập Google" hoặc "Thêm phương thức đăng nhập"
        Client->>GmailCtrl: GET /api/oauth2/authorize-url (Bearer JWT)
        GmailCtrl->>OAuthSvc: getAuthorizeUrl(userId, accountId?)
        OAuthSvc->>StateStore: save(state, userId, codeVerifier, TTL=10m)
        GmailCtrl-->>Client: 200 OK { authorizeUrl }
        Client->>Browser: Mở Google Consent (scope https://mail.google.com/)
        User->>GoogleAuth: Chọn tài khoản Google & Cho phép
        GoogleAuth-->>Browser: 302 /api/oauth2/callback?code=...&state=...
        Browser->>GmailCtrl: GET /api/oauth2/callback
        GmailCtrl->>OAuthSvc: handleCallback(code, state)
        OAuthSvc->>GoogleAuth: POST /token (code + code_verifier) & GET /userinfo
        GoogleAuth-->>OAuthSvc: refresh_token + email + picture
        OAuthSvc->>LinkSvc: upsertOAuth2(user, email, encRefreshToken)
        LinkSvc->>DB: findByUserIdAndEmailKey(userId, normalizeKey(email))
        alt Chưa tồn tại hộp thư
            LinkSvc->>DB: INSERT gmail_accounts (authType=OAUTH2)
            LinkSvc-->>OAuthSvc: outcome = CREATED
        else Đã tồn tại (kể cả từ App Password)
            LinkSvc->>DB: UPDATE giữ nguyên id, displayName, appPasswordEncrypted; đặt OAUTH2 làm chính
            LinkSvc-->>OAuthSvc: outcome = METHOD_ADDED / METHOD_UPDATED
        end
        OAuthSvc-->>Browser: HTML hiển thị email che (c***@gmail.com) & "Đã thêm phương thức" (không báo lỗi)
    end

    rect rgb(240, 253, 244)
        note over User, DB: Luồng B — Thêm App Password dự phòng (Kiểm tra trước cục bộ + Upsert)
        User->>Client: Nhập email ở Wizard App Password
        Client->>Client: EmailNormalizer.normalizeKey(email) so với danh sách cục bộ
        Client-->>User: Báo ngay "Hộp thư này đã liên kết bằng Google. Bấm 'Thêm App Password'..."
        User->>Client: Bấm "Thêm App Password"
        Client->>GmailCtrl: POST /api/gmail-accounts/app-password
        GmailCtrl->>MailFac: testImap(email, appPassword) qua IMAPS 993
        GmailCtrl->>LinkSvc: upsertAppPassword(user, email, encAppPass)
        LinkSvc->>DB: Upsert theo (user_id, email_key), retry 1 lần nếu race UNIQUE
        GmailCtrl-->>Client: 200 OK { outcome: METHOD_ADDED, methods: {hasOAuth:true, hasAppPassword:true} }
    end

    rect rgb(255, 247, 237)
        note over Client, MailFac: Luồng C — Tự động Fallback OAuth2 -> App Password (giữ nguyên NEEDS_REAUTH)
        Client->>MailFac: Đọc/Gửi thư (preferredMethod = OAUTH2)
        MailFac->>OAuthSvc: getFreshAccessToken(account)
        OAuthSvc-->>MailFac: ném ReauthRequiredException (invalid_grant -> NEEDS_REAUTH)
        alt Hộp thư có App Password dự phòng
            MailFac->>DB: fallbackActive=true (GIỮ NGUYÊN status=NEEDS_REAUTH) + Audit FALLBACK_TO_APP_PASSWORD
            MailFac->>MailFac: Mở IMAPS/SMTP bằng App Password đúng 1 lần (không retry lỗi auth)
            MailFac-->>Client: Thao tác thành công + dải cam "Đang dùng App Password dự phòng"
        else Không có App Password
            MailFac-->>Client: 409 GMAIL_NEEDS_REAUTH
        end
    end
```
