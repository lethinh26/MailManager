# Java Mail App — Phân công công việc theo thành viên × tuần

---

## Phân công tổng quan

| TV | Phụ trách | Tổng giờ | W1 | W2 | W3 |
|:---|---|---:|---:|---:|---:|
| Lê Phú Thịnh | M01 nền tảng, M02 auth, M03 client base, M17 gắn sao | 30h | 26h | 4h | 0h |
| Cao Tất Thành | M04 App Password, M05 dialog quản lý Gmail | 16h | 2h | 12h | 2h |
| Lê Thanh Cường | M06 OAuth2, M07 connection factory | 22h | 5h | 4h | 13h |
| Trần Nguyễn Hữu Khánh | M08 IMAP fetch, M09 hộp thư + UI xoá/sao | 28h | 2h | 22h | 4h |
| Nguyễn Tá Thọ | M10 SMTP, M11 soạn mail, M12 đính kèm | 29h | 2h | 14h | 13h |
| Trần Minh Đức | M13/M14/M15 admin, M16 xoá mail | 34h | 10h | 12h | 12h |

## Timeline theo tuần

| Tuần | Ngày | Sprint | Mục tiêu |
|---|---|---|---|
| Tuần 1 | 26/10 – 01/11 | SPRINT 1 | Nền tảng: auth, infra, contracts chốt, admin backend khởi động |
| Tuần 2 | 02/11 – 08/11 | SPRINT 2 | App Password end-to-end: liên kết → fetch mail → gửi mail → admin API xong |
| Tuần 3 | 09/11 – 15/11 | SPRINT 3 | OAuth2 + xoá/sao + đính kèm + admin UI + test tổng |

---

## Backlog structure

### [US-01] Người dùng đăng ký, đăng nhập và vào được client
- **[M01] Hạ tầng Backend & Bảo mật nền tảng** — Owner: Lê Phú Thịnh | Est: 10h | Sprint: SPRINT 1
  - [BE] AesCipher AES-256-GCM + load khoá từ env MAILAPP_AES_KEY | Owner: Lê Phú Thịnh | Est: 1.5h | Week 1
  - [BE] Audit log service | Owner: Lê Phú Thịnh | Est: 1h | Week 1
  - [BE] JWT provider + security filter | Owner: Lê Phú Thịnh | Est: 2h | Week 1
  - [BE] Khởi tạo project Spring Boot | Owner: Lê Phú Thịnh | Est: 1.5h | Week 1
  - [BE] Schema MySQL + seed Admin | Owner: Lê Phú Thịnh | Est: 2h | Week 1
  - [BE] Service đọc cấu hình hệ thống | Owner: Lê Phú Thịnh | Est: 1h | Week 1
  - [BE] Tạo keystore PKCS12 + bật HTTPS 8443 | Owner: Lê Phú Thịnh | Est: 1h | Week 1
  - [BE] Xử lý lỗi thống nhất | Owner: Lê Phú Thịnh | Est: 1h | Week 1
- **[M02] Xác thực tài khoản hệ thống** — Owner: Lê Phú Thịnh | Est: 6h | Sprint: SPRINT 1
  - [BE] Lấy thông tin user hiện tại | Owner: Lê Phú Thịnh | Est: 1h | Week 1
  - [BE] User tự đổi mật khẩu | Owner: Lê Phú Thịnh | Est: 2h | Week 1
  - [BE] Đăng ký tài khoản mới | Owner: Lê Phú Thịnh | Est: 1.5h | Week 1
  - [BE] Đăng nhập — trả JWT | Owner: Lê Phú Thịnh | Est: 1.5h | Week 1
- **[M03] Client nền tảng & Màn hình vào hệ thống** — Owner: Lê Phú Thịnh | Est: 8h | Sprint: SPRINT 1, SPRINT 2
  - [FE] ApiClient HTTPS + JWT | Owner: Lê Phú Thịnh | Est: 2h | Week 1
  - [FE] Lưu phiên đăng nhập | Owner: Lê Phú Thịnh | Est: 1h | Week 1
  - [FE] Main.java + UIManager system L&F + entry mở LoginFrame | Owner: Lê Phú Thịnh | Est: 0.5h | Week 1
  - [FE] MainFrame khung rỗng | Owner: Lê Phú Thịnh | Est: 1.5h | Week 1
  - [FE] Màn hình đăng ký | Owner: Lê Phú Thịnh | Est: 1.5h | Week 1
  - [FE] Màn hình đăng nhập | Owner: Lê Phú Thịnh | Est: 2h | Week 1
  - [FE] Ẩn nút đăng ký theo cờ | Owner: Lê Phú Thịnh | Est: 1h | Week 2

### [US-02] Người dùng liên kết và quản lý tài khoản Gmail
- **[M04] Liên kết Gmail bằng App Password** — Owner: Cao Tất Thành | Est: 8h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Chuẩn bị Gmail test + entity | Owner: Cao Tất Thành | Est: 2h | Week 1
  - [BE] Danh sách Gmail của user | Owner: Cao Tất Thành | Est: 1.5h | Week 2
  - [BE] Liên kết Gmail bằng App Password | Owner: Cao Tất Thành | Est: 4h | Week 2
  - [BE] Xoá liên kết Gmail | Owner: Cao Tất Thành | Est: 2.5h | Week 2
- **[M05] Màn hình Quản lý Gmail** — Owner: Cao Tất Thành | Est: 6h | Sprint: SPRINT 2, SPRINT 3
  - [FE] Dialog quản lý Gmail | Owner: Cao Tất Thành | Est: 1.5h | Week 2
  - [FE] Form thêm Gmail App Password | Owner: Cao Tất Thành | Est: 2h | Week 2
  - [FE] Nút liên kết Google OAuth2 | Owner: Cao Tất Thành | Est: 1.5h | Week 3
  - [FE] Nút xoá liên kết Gmail | Owner: Cao Tất Thành | Est: 0.5h | Week 2
  - [FE] Ẩn nút thêm theo cờ | Owner: Cao Tất Thành | Est: 0.5h | Week 2
- **[M06] Liên kết Gmail bằng OAuth2** — Owner: Lê Thanh Cường | Est: 14h | Sprint: SPRINT 2, SPRINT 3
  - [BE] Callback OAuth2 — đổi code lấy token | Owner: Lê Thanh Cường | Est: 6h | Week 3
  - [BE] Hỗ trợ xoá liên kết OAuth2 | Owner: Lê Thanh Cường | Est: 2h | Week 2
  - [BE] Hỗ trợ xoá user có Gmail OAuth2 | Owner: Lê Thanh Cường | Est: 2h | Week 2
  - [BE] Tạo URL uỷ quyền Google OAuth2 | Owner: Lê Thanh Cường | Est: 4h | Week 3
- **[M07] MailConnectionFactory — kết nối Gmail dùng chung** — Owner: Lê Thanh Cường | Est: 8h | Sprint: SPRINT 1, SPRINT 3
  - [BE] Interface MailConnectionFactory | Owner: Lê Thanh Cường | Est: 1h | Week 1
  - [BE] Kết nối bằng App Password | Owner: Lê Thanh Cường | Est: 2.5h | Week 1
  - [BE] Kết nối bằng OAuth2 XOAUTH2 | Owner: Lê Thanh Cường | Est: 3h | Week 3
  - [BE] Test factory kết nối thật | Owner: Lê Thanh Cường | Est: 1.5h | Week 1

### [US-03] Người dùng đọc và quản lý hộp thư
- **[M08] Nhận mail — IMAP Fetch** — Owner: Trần Nguyễn Hữu Khánh | Est: 12h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Chi tiết mail + parse MIME | Owner: Trần Nguyễn Hữu Khánh | Est: 4.5h | Week 2
  - [BE] Danh sách folder IMAP | Owner: Trần Nguyễn Hữu Khánh | Est: 3h | Week 2
  - [BE] Danh sách mail phân trang + tìm kiếm | Owner: Trần Nguyễn Hữu Khánh | Est: 4.5h | Week 2
  - [BE] MIME fixtures + Mail DTO | Owner: Trần Nguyễn Hữu Khánh | Est: 2h | Week 1
- **[M09] Màn hình Hộp thư & Chi tiết mail** — Owner: Trần Nguyễn Hữu Khánh | Est: 10h | Sprint: SPRINT 2
  - [FE] Bảng danh sách mail | Owner: Trần Nguyễn Hữu Khánh | Est: 2h | Week 2
  - [FE] Cây Gmail + folder trái MainFrame | Owner: Trần Nguyễn Hữu Khánh | Est: 2h | Week 2
  - [FE] Panel chi tiết mail | Owner: Trần Nguyễn Hữu Khánh | Est: 2.5h | Week 2
  - [FE] Phân trang + tìm kiếm mail | Owner: Trần Nguyễn Hữu Khánh | Est: 1.5h | Week 2
  - [FE] SwingWorker + trạng thái tải | Owner: Trần Nguyễn Hữu Khánh | Est: 2h | Week 2
- **[M16] Xoá mail (Trash semantics)** — Owner: Trần Minh Đức | Est: 7h | Sprint: SPRINT 2, SPRINT 3
  - [BE] Ghi audit log khi xoá mail | Owner: Trần Minh Đức | Est: 2h | Week 3
  - [BE] Xoá mail qua IMAP | Owner: Trần Minh Đức | Est: 4h | Week 2
  - [FE] Nút Xoá trong chi tiết + context menu | Owner: Trần Nguyễn Hữu Khánh | Est: 2h | Week 3
- **[M17] Gắn sao & đánh dấu đã/chưa đọc (flags)** — Owner: Lê Phú Thịnh | Est: 5h | Sprint: SPRINT 2, SPRINT 3
  - [BE] Toggle cờ starred/seen qua IMAP | Owner: Lê Phú Thịnh | Est: 3h | Week 2
  - [FE] Toggle sao + đánh dấu chưa đọc | Owner: Trần Nguyễn Hữu Khánh | Est: 2h | Week 3

### [US-04] Người dùng soạn và gửi mail
- **[M10] Gửi mail — SMTP** — Owner: Nguyễn Tá Thọ | Est: 8h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Gửi mail SMTP multipart | Owner: Nguyễn Tá Thọ | Est: 8h | Week 2
  - [BE] SendMailRequest DTO + builder | Owner: Nguyễn Tá Thọ | Est: 2h | Week 1
- **[M11] Màn hình Soạn mail** — Owner: Nguyễn Tá Thọ | Est: 6h | Sprint: SPRINT 2
  - [FE] Chọn file đính kèm | Owner: Nguyễn Tá Thọ | Est: 1.5h | Week 2
  - [FE] Dialog soạn mail | Owner: Nguyễn Tá Thọ | Est: 2h | Week 2
  - [FE] Disable Soạn khi bị tắt | Owner: Nguyễn Tá Thọ | Est: 1h | Week 2
  - [FE] Gửi mail qua API | Owner: Nguyễn Tá Thọ | Est: 1.5h | Week 2

### [US-05] Người dùng tải và mở file đính kèm
- **[M12] File đính kèm — Tải về & Mở file** — Owner: Nguyễn Tá Thọ | Est: 10h | Sprint: SPRINT 3
  - [BE] Stream file đính kèm | Owner: Nguyễn Tá Thọ | Est: 10h | Week 3
  - [FE] Tải file đính kèm về Downloads + chống trùng tên | Owner: Nguyễn Tá Thọ | Est: 2h | Week 3
  - [FE] Tự mở file sau khi tải + nhớ file đã tải | Owner: Nguyễn Tá Thọ | Est: 1h | Week 3

### [US-06] Quản trị viên quản lý và vận hành hệ thống
- **[M13] Admin — Quản lý người dùng** — Owner: Trần Minh Đức | Est: 10h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Admin tạo tài khoản hộ | Owner: Trần Minh Đức | Est: 1.5h | Week 1
  - [BE] Admin đặt lại mật khẩu user | Owner: Trần Minh Đức | Est: 1h | Week 2
  - [BE] Chi tiết một user cho Admin | Owner: Trần Minh Đức | Est: 1h | Week 1
  - [BE] Danh sách user cho Admin | Owner: Trần Minh Đức | Est: 1.5h | Week 1
  - [BE] Khoá tài khoản user | Owner: Trần Minh Đức | Est: 1h | Week 1
  - [BE] Mở khoá tài khoản user | Owner: Trần Minh Đức | Est: 0.5h | Week 1
  - [BE] Xoá tài khoản user | Owner: Trần Minh Đức | Est: 2h | Week 2
  - [BE] Đổi role user | Owner: Trần Minh Đức | Est: 1.5h | Week 2
- **[M14] Admin — Cấu hình, Thống kê & Nhật ký** — Owner: Trần Minh Đức | Est: 8h | Sprint: SPRINT 1, SPRINT 2
  - [BE] Cập nhật cấu hình hệ thống | Owner: Trần Minh Đức | Est: 3h | Week 1
  - [BE] Cờ cấu hình công khai | Owner: Trần Minh Đức | Est: 1h | Week 1
  - [BE] Thống kê tổng quan | Owner: Trần Minh Đức | Est: 1.5h | Week 2
  - [BE] Truy vấn audit log | Owner: Trần Minh Đức | Est: 1.5h | Week 2
  - [BE] Đọc toàn bộ cấu hình hệ thống | Owner: Trần Minh Đức | Est: 1h | Week 1
- **[M15] Màn hình Admin** — Owner: Trần Minh Đức | Est: 8h | Sprint: SPRINT 3
  - [FE-Admin] Các nút thao tác user | Owner: Trần Minh Đức | Est: 2.5h | Week 3
  - [FE-Admin] Khung AdminFrame 4 tab | Owner: Trần Minh Đức | Est: 1h | Week 3
  - [FE-Admin] Tab cấu hình hệ thống | Owner: Trần Minh Đức | Est: 2h | Week 3
  - [FE-Admin] Tab danh sách user | Owner: Trần Minh Đức | Est: 2.5h | Week 3
  - [FE-Admin] Tab nhật ký | Owner: Trần Minh Đức | Est: 1.5h | Week 3
  - [FE-Admin] Tab thống kê | Owner: Trần Minh Đức | Est: 1h | Week 3
