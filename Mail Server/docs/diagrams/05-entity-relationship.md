# Sơ Đồ Thực Thể - Quan Hệ Cơ Sở Dữ Liệu (Entity-Relationship Diagram)

## 1. Mô Tả Kiến Trúc Cơ Sở Dữ Liệu MySQL 8.0

Cơ sở dữ liệu của **Mail Server** được thiết kế tinh gọn gồm 4 bảng, tuân thủ nguyên tắc **KHÔNG lưu nội dung thư điện tử (No Mail Caching)** trong hệ thống:
1. **`users`:** Lưu thông tin tài khoản đăng nhập của người dùng vào hệ thống `MailManager`. Mật khẩu được mã hóa một chiều bằng giải thuật **BCrypt**. Quản lý phân quyền RBAC (`ADMIN`, `USER`) và trạng thái kích hoạt (`enabled`).
2. **`gmail_accounts`:** Lưu thông tin liên kết tài khoản Gmail của từng người dùng (quan hệ 1-N với `users`). Trường `encrypted_secret` lưu App Password (16 ký tự) hoặc OAuth2 Refresh Token được **mã hóa đối xứng AES-256-GCM**, tuyệt đối không lưu dữ liệu thô.
3. **`system_settings`:** Lưu các cờ động hệ thống phục vụ quản trị runtime (như `MAINTENANCE_MODE`, `ALLOW_REGISTRATION`), cho phép thay đổi cấu hình mà không cần khởi động lại Server.
4. **`audit_logs`:** Ghi vết kiểm toán bảo mật (Security Audit Logging) các hành vi quan trọng: đăng nhập, đổi mật khẩu, liên kết/hủy liên kết Gmail, thu hồi token, kèm địa chỉ IP client và thời gian chính xác.

---

## 2. Sơ Đồ Thực Thể - Quan Hệ (Mermaid)

```mermaid
erDiagram
    USERS ||--o{ GMAIL_ACCOUNTS : "sở hữu (1-N)"
    USERS ||--o{ AUDIT_LOGS : "thực hiện (1-N)"

    USERS {
        bigint id PK "Tự tăng (ID duy nhất)"
        varchar username UK "Tên đăng nhập (3-50 ký tự)"
        varchar password_hash "Mật khẩu băm BCrypt"
        varchar full_name "Họ và tên người dùng"
        varchar email "Email liên hệ"
        varchar role "Vai trò: ADMIN | USER"
        boolean enabled "Trạng thái kích hoạt tài khoản"
        boolean must_change_password "Cờ bắt buộc đổi mật khẩu"
        timestamp created_at "Thời điểm tạo tài khoản"
        timestamp updated_at "Thời điểm cập nhật"
    }

    GMAIL_ACCOUNTS {
        bigint id PK "Tự tăng (ID tài khoản liên kết)"
        bigint user_id FK "Khóa ngoại tham chiếu users.id"
        varchar email "Địa chỉ Gmail liên kết"
        varchar auth_type "Loại xác thực: APP_PASSWORD | OAUTH2"
        text encrypted_secret "Mã hóa AES-256-GCM (App Pass / Refresh Token)"
        varchar status "Trạng thái: OK | NEEDS_REAUTH | DISABLED"
        timestamp created_at "Thời điểm liên kết"
        timestamp updated_at "Thời điểm cập nhật"
    }

    SYSTEM_SETTINGS {
        varchar setting_key PK "Khóa cấu hình: MAINTENANCE_MODE, ALLOW_REGISTER..."
        varchar setting_value "Giá trị cấu hình (true/false, text)"
        varchar description "Mô tả mục đích cờ động"
        timestamp updated_at "Thời điểm cập nhật"
    }

    AUDIT_LOGS {
        bigint id PK "Tự tăng (ID bản ghi kiểm toán)"
        bigint user_id FK "Khóa ngoại tham chiếu users.id (nullable)"
        varchar action "Hành động: LOGIN_SUCCESS, UNLINK_GMAIL, REVOKE..."
        text detail "Chi tiết nhật ký hành động"
        varchar ip_address "Địa chỉ IP thực hiện thao tác"
        timestamp created_at "Thời điểm ghi nhận"
    }
```

