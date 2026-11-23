# Biểu Đồ Tuần Tự: Soạn & Gửi Thư Multipart qua SMTP (M10)

Biểu đồ mô tả quy trình gửi thư kèm tệp đính kèm từ `ComposeDialog` qua `Mail Server` (có kiểm tra `ALLOW_SEND_MAIL`, chống CRLF Header Injection, giới hạn dung lượng `MAX_ATTACHMENT_SIZE_MB`, Rate Limit theo user) tới Gmail SMTP và ghi Audit Log không lưu nội dung thư.

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng (Swing Client)
    participant Compose as ComposeDialog (SwingWorker)
    participant Server as Mail Server (HTTPS :8443)
    participant Guard as MailHeaderAndRateGuard
    participant SMTP as Gmail SMTPS / STARTTLS (:587)
    participant Audit as AuditLog (MySQL)

    User->>Compose: Nhập To/Cc/Bcc, Tiêu đề, Nội dung, Kéo-thả tệp đính kèm
    Compose->>Compose: Kiểm tra định dạng email & tổng dung lượng <= MAX_ATTACHMENT_SIZE_MB
    User->>Compose: Bấm "Gửi thư"
    Compose->>Server: POST /api/mails/send (multipart/form-data: payload JSON + attachments[])

    Server->>Server: Kiểm tra ALLOW_SEND_MAIL & Anti-IDOR trên gmailId
    Server->>Guard: Kiểm tra chống CRLF Header Injection, giới hạn người nhận (<= 50) & Rate Limit (20 thư/giờ/user)
    alt Vi phạm Rate Limit
        Server-->>Compose: 429 Too Many Requests + Retry-After
    else Hợp lệ
        Server->>SMTP: Mở kết nối SMTP qua MailConnectionFactory (XOAUTH2 / App Password)
        Server->>SMTP: Dựng MimeMessage (UTF-8, multipart/alternative + attachments) & gửi đi
        SMTP-->>Server: Chấp nhận gửi (Message-ID)
        Server->>Audit: Ghi audit SEND_MAIL (gmailId, số người nhận, số tệp — KHÔNG ghi nội dung)
        Server-->>Compose: 200 OK {messageId, status: "SENT"}
        Compose-->>User: Thông báo gửi thành công & làm mới hộp thư
    end
```

