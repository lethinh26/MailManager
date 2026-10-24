# Java Mail App — Phân công công việc theo thành viên × tuần

> Dự án môn học: Ứng dụng Windows quản lý nhiều Gmail qua TLS
> Backend: Spring Boot + MySQL 8 | Client: Java Swing thuần | 3 tuần (26/10 → 15/11, không nghỉ cuối tuần)

---

## Phân công tổng quan

| TV | Phụ trách | Tổng giờ | W1 | W2 | W3 |
|---|---|---:|---:|---:|---:|
| TV1 | M01 nền tảng, M02 auth, M03 client base, M17 gắn sao | 30h | 26h | 4h | 0h |
| TV2 | M04 App Password, M05 dialog quản lý Gmail | 16h | 2h | 12h | 2h |
| TV3 | M06 OAuth2, M07 connection factory | 22h | 5h | 4h | 13h |
| TV4 | M08 IMAP fetch, M09 hộp thư + UI xoá/sao | 28h | 2h | 22h | 4h |
| TV5 | M10 SMTP, M11 soạn mail, M12 đính kèm | 29h | 2h | 14h | 13h |
| TV6 | M13/M14/M15 admin, M16 xoá mail | 34h | 10h | 12h | 12h |

## Timeline theo tuần

| Tuần | Ngày | Sprint | Mục tiêu |
|---|---|---|---|
| Tuần 1 | 26/10 – 01/11 | SPRINT 1 | Nền tảng: auth, infra, contracts chốt, admin backend khởi động |
| Tuần 2 | 02/11 – 08/11 | SPRINT 2 | App Password end-to-end: liên kết → fetch mail → gửi mail → admin API xong |
| Tuần 3 | 09/11 – 15/11 | SPRINT 3 | OAuth2 + xoá/sao + đính kèm + admin UI + test tổng |

---

## Backlog structure

### [US-01] Người dùng đăng ký, đăng nhập và vào được client
- **[M01] Hạ tầng Backend & Bảo mật nền tảng** — Owner: TV1 | Est: 10h | Sprint: SPRINT 1
  - [BE] AesCipher AES-256-GCM + load khoá từ env MAILAPP_AES_KEY | Owner: TV1 | Est: 1.5h | Week 1
  - [BE] Audit log service | Owner: TV1 | Est: 1h | Week 1
  - [BE] JWT provider + security filter | Owner: TV1 | Est: 2h | Week 1
  - [BE] Khởi tạo project Spring Boot | Owner: TV1 | Est: 1.5h | Week 1
  - [BE] Schema MySQL + seed Admin | Owner: TV1 | Est: 2h | Week 1
  - [BE] Service đọc cấu hình hệ thống | Owner: TV1 | Est: 1h | Week 1
  - [BE] Tạo keystore PKCS12 + bật HTTPS 8443 | Owner: TV1 | Est: 1h | Week 1
  - [BE] Xử lý lỗi thống nhất | Owner: TV1 | Est: 1h | Week 1
- **[M02] Xác thực tài khoản hệ thống** — Owner: TV1 | Est: 6h | Sprint: SPRINT 1
  - [BE] Lấy thông tin user hiện tại | Owner: TV1 | Est: 1h | Week 1
  - [BE] User tự đổi mật khẩu | Owner: TV1 | Est: 2h | Week 1
  - [BE] Đăng ký tài khoản mới | Owner: TV1 | Est: 1.5h | Week 1
  - [BE] Đăng nhập — trả JWT | Owner: TV1 | Est: 1.5h | Week 1
- **[M03] Client nền tảng & Màn hình vào hệ thống** — Owner: TV1 | Est: 8h | Sprint: SPRINT 1, SPRINT 2
  - [FE] ApiClient HTTPS + JWT | Owner: TV1 | Est: 2h | Week 1
  - [FE] Lưu phiên đăng nhập | Owner: TV1 | Est: 1h | Week 1
  - [FE] Main.java + UIManager system L&F + entry mở LoginFrame | Owner: TV1 | Est: 0.5h | Week 1
  - [FE] MainFrame khung rỗng | Owner: TV1 | Est: 1.5h | Week 1
  - [FE] Màn hình đăng ký | Owner: TV1 | Est: 1.5h | Week 1
  - [FE] Màn hình đăng nhập | Owner: TV1 | Est: 2h | Week 1
  - [FE] Ẩn nút đăng ký theo cờ | Owner: TV1 | Est: 1h | Week 2

### [US-02] Người dùng liên kết và quản lý tài khoản Gmail
- **[M04] Liên kết Gmail bằng App Password** — Owner: TV2 | Est: 8h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Chuẩn bị Gmail test + entity | Owner: TV2 | Est: 2h | Week 1
  - [BE] Danh sách Gmail của user | Owner: TV2 | Est: 1.5h | Week 2
  - [BE] Liên kết Gmail bằng App Password | Owner: TV2 | Est: 4h | Week 2
  - [BE] Xoá liên kết Gmail | Owner: TV2 | Est: 2.5h | Week 2
- **[M05] Màn hình Quản lý Gmail** — Owner: TV2 | Est: 6h | Sprint: SPRINT 2, SPRINT 3
  - [FE] Dialog quản lý Gmail | Owner: TV2 | Est: 1.5h | Week 2
  - [FE] Form thêm Gmail App Password | Owner: TV2 | Est: 2h | Week 2
  - [FE] Nút liên kết Google OAuth2 | Owner: TV2 | Est: 1.5h | Week 3
  - [FE] Nút xoá liên kết Gmail | Owner: TV2 | Est: 0.5h | Week 2
  - [FE] Ẩn nút thêm theo cờ | Owner: TV2 | Est: 0.5h | Week 2
- **[M06] Liên kết Gmail bằng OAuth2** — Owner: TV3 | Est: 14h | Sprint: SPRINT 2, SPRINT 3
  - [BE] Callback OAuth2 — đổi code lấy token | Owner: TV3 | Est: 6h | Week 3
  - [BE] Hỗ trợ xoá liên kết OAuth2 | Owner: TV3 | Est: 2h | Week 2
  - [BE] Hỗ trợ xoá user có Gmail OAuth2 | Owner: TV3 | Est: 2h | Week 2
  - [BE] Tạo URL uỷ quyền Google OAuth2 | Owner: TV3 | Est: 4h | Week 3
- **[M07] MailConnectionFactory — kết nối Gmail dùng chung** — Owner: TV3 | Est: 8h | Sprint: SPRINT 1, SPRINT 3
  - [BE] Interface MailConnectionFactory | Owner: TV3 | Est: 1h | Week 1
  - [BE] Kết nối bằng App Password | Owner: TV3 | Est: 2.5h | Week 1
  - [BE] Kết nối bằng OAuth2 XOAUTH2 | Owner: TV3 | Est: 3h | Week 3
  - [BE] Test factory kết nối thật | Owner: TV3 | Est: 1.5h | Week 1

### [US-03] Người dùng đọc và quản lý hộp thư
- **[M08] Nhận mail — IMAP Fetch** — Owner: TV4 | Est: 12h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Chi tiết mail + parse MIME | Owner: TV4 | Est: 4.5h | Week 2
  - [BE] Danh sách folder IMAP | Owner: TV4 | Est: 3h | Week 2
  - [BE] Danh sách mail phân trang + tìm kiếm | Owner: TV4 | Est: 4.5h | Week 2
  - [BE] MIME fixtures + Mail DTO | Owner: TV4 | Est: 2h | Week 1
- **[M09] Màn hình Hộp thư & Chi tiết mail** — Owner: TV4 | Est: 10h | Sprint: SPRINT 2
  - [FE] Bảng danh sách mail | Owner: TV4 | Est: 2h | Week 2
  - [FE] Cây Gmail + folder trái MainFrame | Owner: TV4 | Est: 2h | Week 2
  - [FE] Panel chi tiết mail | Owner: TV4 | Est: 2.5h | Week 2
  - [FE] Phân trang + tìm kiếm mail | Owner: TV4 | Est: 1.5h | Week 2
  - [FE] SwingWorker + trạng thái tải | Owner: TV4 | Est: 2h | Week 2
- **[M16] Xoá mail (Trash semantics)** — Owner: TV6 | Est: 7h | Sprint: SPRINT 2, SPRINT 3
  - [BE] Ghi audit log khi xoá mail | Owner: TV6 | Est: 2h | Week 3
  - [BE] Xoá mail qua IMAP | Owner: TV6 | Est: 4h | Week 2
  - [FE] Nút Xoá trong chi tiết + context menu | Owner: TV4 | Est: 2h | Week 3
- **[M17] Gắn sao & đánh dấu đã/chưa đọc (flags)** — Owner: TV1 | Est: 5h | Sprint: SPRINT 2, SPRINT 3
  - [BE] Toggle cờ starred/seen qua IMAP | Owner: TV1 | Est: 3h | Week 2
  - [FE] Toggle sao + đánh dấu chưa đọc | Owner: TV4 | Est: 2h | Week 3

### [US-04] Người dùng soạn và gửi mail
- **[M10] Gửi mail — SMTP** — Owner: TV5 | Est: 8h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Gửi mail SMTP multipart | Owner: TV5 | Est: 8h | Week 2
  - [BE] SendMailRequest DTO + builder | Owner: TV5 | Est: 2h | Week 1
- **[M11] Màn hình Soạn mail** — Owner: TV5 | Est: 6h | Sprint: SPRINT 2
  - [FE] Chọn file đính kèm | Owner: TV5 | Est: 1.5h | Week 2
  - [FE] Dialog soạn mail | Owner: TV5 | Est: 2h | Week 2
  - [FE] Disable Soạn khi bị tắt | Owner: TV5 | Est: 1h | Week 2
  - [FE] Gửi mail qua API | Owner: TV5 | Est: 1.5h | Week 2

### [US-05] Người dùng tải và mở file đính kèm
- **[M12] File đính kèm — Tải về & Mở file** — Owner: TV5 | Est: 10h | Sprint: SPRINT 3
  - [BE] Stream file đính kèm | Owner: TV5 | Est: 10h | Week 3
  - [FE] Tải file đính kèm về Downloads + chống trùng tên | Owner: TV5 | Est: 2h | Week 3
  - [FE] Tự mở file sau khi tải + nhớ file đã tải | Owner: TV5 | Est: 1h | Week 3

### [US-06] Quản trị viên quản lý và vận hành hệ thống
- **[M13] Admin — Quản lý người dùng** — Owner: TV6 | Est: 10h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Admin tạo tài khoản hộ | Owner: TV6 | Est: 1.5h | Week 1
  - [BE] Admin đặt lại mật khẩu user | Owner: TV6 | Est: 1h | Week 2
  - [BE] Chi tiết một user cho Admin | Owner: TV6 | Est: 1h | Week 1
  - [BE] Danh sách user cho Admin | Owner: TV6 | Est: 1.5h | Week 1
  - [BE] Khoá tài khoản user | Owner: TV6 | Est: 1h | Week 1
  - [BE] Mở khoá tài khoản user | Owner: TV6 | Est: 0.5h | Week 1
  - [BE] Xoá tài khoản user | Owner: TV6 | Est: 2h | Week 2
  - [BE] Đổi role user | Owner: TV6 | Est: 1.5h | Week 2
- **[M14] Admin — Cấu hình, Thống kê & Nhật ký** — Owner: TV6 | Est: 8h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Cập nhật cấu hình hệ thống | Owner: TV6 | Est: 3h | Week 1
  - [BE] Cờ cấu hình công khai | Owner: TV6 | Est: 1h | Week 1
  - [BE] Thống kê tổng quan | Owner: TV6 | Est: 1.5h | Week 2
  - [BE] Truy vấn audit log | Owner: TV6 | Est: 1.5h | Week 2
  - [BE] Đọc toàn bộ cấu hình hệ thống | Owner: TV6 | Est: 1h | Week 1
- **[M15] Màn hình Admin** — Owner: TV6 | Est: 8h | Sprint: SPRINT 3
  - [FE-Admin] Các nút thao tác user | Owner: TV6 | Est: 2.5h | Week 3
  - [FE-Admin] Khung AdminFrame 4 tab | Owner: TV6 | Est: 1h | Week 3
  - [FE-Admin] Tab cấu hình hệ thống | Owner: TV6 | Est: 2h | Week 3
  - [FE-Admin] Tab danh sách user | Owner: TV6 | Est: 2.5h | Week 3
  - [FE-Admin] Tab nhật ký | Owner: TV6 | Est: 1.5h | Week 3
  - [FE-Admin] Tab thống kê | Owner: TV6 | Est: 1h | Week 3
