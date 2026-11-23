# Sơ Đồ Tuần Tự Đăng Nhập & Cấp Phát JWT (EP-A2: POST /api/auth/login)

## 1. Mô Tả Luồng Nghiệp Vụ & Cơ Chế Phòng Thủ

Luồng đăng nhập hệ thống được thiết kế theo các tiêu chuẩn bảo mật khắt khe:
1. **Phòng thủ Brute-force (Rate Limiting):** Giới hạn tối đa 5 lần đăng nhập thất bại từ cùng một địa chỉ IP trong khung thời gian 15 phút. Khi vi phạm, trả về ngay HTTP 429 kèm header `Retry-After`.
2. **Chống dò quét tài khoản (Timing Attack Protection):** Nếu `username` không tồn tại trong hệ thống, `AuthService` vẫn cố tình thực hiện một phép kiểm tra băm BCrypt với một chuỗi hash giả lập (`DUMMY_HASH`) để đảm bảo thời gian phản hồi giữa trường hợp tài khoản không tồn tại và trường hợp sai mật khẩu là tương đương nhau. Luôn trả về thông báo chung `INVALID_CREDENTIALS`.
3. **Bảo mật trạng thái tài khoản:** Trạng thái khóa (`enabled == false`) chỉ được phản hồi sau khi người dùng đã nhập đúng mật khẩu, tránh làm lộ thông tin tài khoản bị khóa cho kẻ tấn công.
4. **Cấp phát JWT an toàn:** JWT được ký bằng thuật toán HMAC SHA-256 với secret tối thiểu 256 bits, thời hạn 24 giờ. Header phản hồi chứa `Cache-Control: no-store` ngăn chặn cache token.

---

## 2. Sơ Đồ Tuần Tự (Mermaid)

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng (Client Swing)
    participant AuthCtrl as AuthController (EP-A2)
    participant AuthService as AuthService
    participant RateLimiter as RateLimitService
    participant UserRepo as UserRepository
    participant PwdEncoder as PasswordEncoder (BCrypt)
    participant JwtProv as JwtProvider
    participant AuditLog as AuditLogService

    User->>AuthCtrl: POST /api/auth/login {username, password}
    AuthCtrl->>AuthService: login(request, clientIp)
    AuthService->>RateLimiter: checkRateLimit(clientIp)
    alt Đã vượt quá 5 lần sai trong 15 phút
        RateLimiter-->>AuthService: Ném RateLimitException
        AuthService-->>AuthCtrl: 429 TOO_MANY_ATTEMPTS
        AuthCtrl-->>User: HTTP 429 (Kèm header Retry-After)
    else Trong ngưỡng cho phép
        RateLimiter-->>AuthService: OK
    end

    AuthService->>UserRepo: findByUsername(username)
    alt Không tìm thấy User
        UserRepo-->>AuthService: Optional.empty()
        AuthService->>PwdEncoder: matches(password, DUMMY_HASH) (Chống time-attack)
        AuthService->>RateLimiter: recordFailure(clientIp)
        AuthService-->>User: 401 INVALID_CREDENTIALS ("Sai tên đăng nhập hoặc mật khẩu")
    else Tìm thấy User
        UserRepo-->>AuthService: User entity
        AuthService->>PwdEncoder: matches(password, user.getPasswordHash())
        alt Mật khẩu KHÔNG khớp
            PwdEncoder-->>AuthService: false
            AuthService->>RateLimiter: recordFailure(clientIp)
            AuthService-->>User: 401 INVALID_CREDENTIALS ("Sai tên đăng nhập hoặc mật khẩu")
        else Mật khẩu KHỚP
            PwdEncoder-->>AuthService: true
            alt Tài khoản bị khóa (enabled == false)
                AuthService-->>User: 403 ACCOUNT_LOCKED ("Tài khoản đã bị vô hiệu hóa")
            else Tài khoản hợp lệ
                AuthService->>RateLimiter: recordSuccess(clientIp)
                AuthService->>JwtProv: generateToken(user) (HMAC SHA-256, hạn 24h)
                JwtProv-->>AuthService: accessToken (JWT)
                AuthService->>AuditLog: log(user.getId(), "LOGIN_SUCCESS", clientIp)
                AuthService-->>AuthCtrl: LoginResponse {accessToken, tokenType="Bearer", expiresIn=86400, user}
                AuthCtrl-->>User: HTTP 200 OK [Cache-Control: no-store]
            end
        end
    end
```

