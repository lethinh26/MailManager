# Biểu Đồ Tuần Tự: Đọc Thư & Làm Sạch HTML An Toàn (M08)

Biểu đồ mô tả luồng liệt kê hộp thư tối ưu bằng `FetchProfile` (không tải nội dung thân thư), đọc chi tiết MIME, kiểm tra `UIDVALIDITY`, làm sạch HTML chống XSS/tracking pixel và nguyên tắc **Server không bao giờ lưu nội dung email vào CSDL**.

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng (Swing Client)
    participant Client as Mail Client (SwingWorker)
    participant Server as Mail Server (HTTPS :8443)
    participant DB as MySQL (Metadata Only)
    participant Gmail as Gmail IMAPS (:993)

    User->>Client: Chọn tài khoản Gmail & Thư mục (vd: INBOX)
    Client->>Server: GET /api/mails?gmailId=1&folder=INBOX&page=0&size=30 (Bearer JWT)
    Server->>DB: Kiểm tra quyền sở hữu (Anti-IDOR) & giải mã credential AES-256-GCM
    Server->>Gmail: IMAPS FETCH (ENVELOPE, FLAGS, CONTENT_INFO — không tải body)
    Gmail-->>Server: Danh sách tiêu đề, cờ, uidValidity
    Server-->>Client: 200 OK {items[], total, hasMore, uidValidity}
    Client-->>User: Hiển thị danh sách thư (Avatar Java2D, đậm nếu chưa đọc)

    User->>Client: Click mở một thư (uid)
    Client->>Server: GET /api/mails/1/INBOX/{uid}?loadRemote=false&uidValidity=...
    Server->>Gmail: Kiểm tra UIDVALIDITY & đọc MIME multipart (giới hạn 512KB)
    alt UIDVALIDITY đã thay đổi
        Server-->>Client: 409 Conflict {code: "UID_VALIDITY_CHANGED"}
        Client->>Client: Tự động gọi lại GET /api/mails để đồng bộ
    else Hợp lệ
        Server->>Server: HtmlSanitizerService (Jsoup Safelist, chặn script/iframe/ảnh từ xa, giữ cid:)
        Server->>Gmail: Đặt cờ \Seen (đã đọc)
        Server-->>Client: 200 OK {textBody, htmlBody sạch, hasBlockedRemoteImages, attachments[]}
        Note over Server,DB: Server TUYỆT ĐỐI KHÔNG lưu nội dung thư vào DB
        Client-->>User: Render nội dung + banner "Ảnh từ xa bị chặn" + chip đính kèm
    end
```

