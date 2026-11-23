# Hướng Dẫn Bắt Gói Mạng Bằng Wireshark Chứng Minh Tính An Toàn Bảo Mật

Tài liệu hướng dẫn chi tiết cách sử dụng công cụ phân tích gói tin mạng **Wireshark** để kiểm chứng cơ chế bảo mật của **Mail Server**, chứng minh rằng toàn bộ dữ liệu trao đổi giữa Client - Server và Server - Gmail đều được mã hóa bằng TLS, ngăn chặn hoàn toàn việc nghe lén (Sniffing) và tấn công trung gian (Man-in-the-Middle).

---

## 1. Nguyên Lý Kiểm Chứng Bảo Mật

> [!IMPORTANT] Kết Quả Mong Đợi Trong Wireshark
> **Không thể đọc được nội dung gói tin ở dạng văn bản thuần (Plaintext)**: Toàn bộ username, password, JWT token, mã xác thực OAuth2 và nội dung email chỉ xuất hiện dưới dạng dữ liệu mã hóa nhị phân `Application Data (TLS)`. Việc **KHÔNG xem được nội dung** chính là bằng chứng xác thực và đanh thép nhất cho thấy hệ thống bảo mật đúng theo thiết kế đề tài.

Hệ thống hoạt động theo mô hình 2 lớp TLS:
1. **Lớp 1 (Client $\leftrightarrow$ Server):** HTTPS cổng `8443` (TLS 1.2 / TLS 1.3).
2. **Lớp 2 (Server $\leftrightarrow$ Gmail):**
   - **SMTP STARTTLS:** Cổng `587`, thương lượng nâng cấp từ Cleartext sang TLS.
   - **IMAPS:** Cổng `993`, TLS trực tiếp ngay sau TCP Handshake.

---

## 2. Chuẩn Bị & Cấu Hình Wireshark

1. **Khởi động Wireshark:**
   - Nếu chạy Client và Server trên cùng một máy: Chọn giao diện **Adapter for loopback traffic capture** (hoặc `Npcap Loopback Adapter`).
   - Nếu chạy qua mạng LAN thật (Server `192.168.1.10`, Client `192.168.1.15`): Chọn card mạng **Wi-Fi** hoặc **Ethernet** kết nối chung switch/router.
2. **Cấu hình Capture Filter (Bộ lọc bắt gói):**
   Để tránh các gói tin rác khác trên mạng, nhập bộ lọc bắt gói:
   ```
   tcp port 8443 or tcp port 993 or tcp port 587
   ```

---

## 3. Các Bộ Lọc Hiển Thị (Display Filters) & Phân Tích Gói Tin

### Trường Hợp 1: Kênh HTTPS Giữa Client Và Mail Server (Port 8443)
- **Bộ lọc hiển thị:**
  ```wireshark
  tcp.port == 8443 && tls
  ```
- **Hiện tượng quan sát được:**
  1. **TCP 3-Way Handshake:** Các gói tin `SYN` $\rightarrow$ `SYN, ACK` $\rightarrow$ `ACK` thiết lập kết nối TCP giữa PC Client và Server cổng 8443.
  2. **TLS Handshake:**
     - `Client Hello`: Client gửi danh sách mã hóa (Cipher Suites), phiên bản hỗ trợ (TLS 1.2, TLS 1.3), và ALPN (`http/1.1`).
     - `Server Hello`: Server đồng ý chọn Cipher Suite (ví dụ `TLS_AES_256_GCM_SHA384` hoặc `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`).
     - `Certificate`: Server gửi chứng chỉ số PKCS12 tự ký chứa SAN (`CN=MailApp Server, ip:192.168.1.10, dns:localhost`).
     - `Key Exchange` & `Change Cipher Spec`.
  3. **Application Data:**
     - Các gói tin tiếp theo chứa request đăng nhập `POST /api/auth/login`, JWT Bearer token và các lời gọi API.
     - **Minh chứng bảo mật:** Nhấp vào bất kỳ gói tin `Application Data` nào, phần payload chỉ là chuỗi hex nhị phân ngẫu nhiên. Wireshark **không thể phân giải được HTTP method hay JSON body**.

---

### Trường Hợp 2: Kênh Gửi Thư SMTP STARTTLS (Port 587)
- **Bộ lọc hiển thị:**
  ```wireshark
  tcp.port == 587
  ```
- **Hiện tượng quan sát được (Quá trình chuyển đổi từ Plaintext sang TLS):**
  1. **Giai đoạn Plaintext ban đầu:**
     - Server `smtp.gmail.com` gửi lời chào:
       ```
       220 smtp.gmail.com ESMTP ... - gsmtp
       ```
     - Mail Server gửi lệnh chào: `EHLO [192.168.1.10]`
     - Gmail phản hồi danh sách tính năng hỗ trợ, trong đó có dòng:
       ```
       250-STARTTLS
       ```
     - Mail Server gửi lệnh: `STARTTLS`
     - Gmail chấp thuận:
       ```
       220 2.0.0 Ready to start TLS
       ```
  2. **Giai đoạn Nâng cấp TLS:**
     - Ngay gói tin kế tiếp, Mail Server gửi `Client Hello (TLSv1.3 / TLSv1.2)`.
     - Quá trình TLS Handshake hoàn tất.
     - **Minh chứng bảo mật:** Toàn bộ lệnh gửi thư sau đó (lệnh `AUTH XOAUTH2`, chuỗi SASL access token, `MAIL FROM`, `RCPT TO`, và nội dung bức thư) được bọc 100% trong `Application Data` mã hóa TLS. Không thể đánh cắp được token hay nội dung thư.

---

### Trường Hợp 3: Kênh Nhận Thư IMAPS Trực Tiếp (Port 993)
- **Bộ lọc hiển thị:**
  ```wireshark
  tcp.port == 993
  ```
- **Hiện tượng quan sát được (Mã hóa ngay từ đầu):**
  1. Sau TCP Handshake 3 bước, Mail Server lập tức gửi gói tin `Client Hello (TLS)`.
  2. Không có bất kỳ dòng chữ Plaintext nào xuất hiện trên đường truyền (khác với SMTP port 587).
  3. Sau khi bắt tay TLS hoàn tất, mọi giao thức IMAP (`LOGIN`, `AUTHENTICATE XOAUTH2`, `FETCH`, `SELECT INBOX`) đều hoàn toàn nằm trong các frame `Application Data`.

---

## 4. Kiểm Tra Chuỗi Chứng Chỉ SSL Bằng Dòng Lệnh (OpenSSL)

Để chứng minh Mail Server kết nối trực tiếp tới máy chủ chính thống của Google và kiểm tra chuỗi chứng chỉ SSL đáng tin cậy của Google, chạy lệnh sau:

### Kiểm tra STARTTLS trên cổng 587 của Gmail:
```bash
openssl s_client -starttls smtp -connect smtp.gmail.com:587 -showcerts
```

**Bằng chứng thu được từ kết quả lệnh:**
1. **Chuỗi chứng chỉ (Certificate Chain):**
   - Certificate 0: `CN = smtp.gmail.com`
   - Certificate 1 (Trung gian): `CN = WR2, O = Google Trust Services LLC, C = US` (hoặc `GTS CA 1C3`)
   - Certificate 2 (Gốc): `CN = GTS Root R1, O = Google Trust Services LLC, C = US`
2. **Giao thức & Thuật toán mã hóa thương lượng:**
   - `Protocol: TLSv1.3`
   - `Cipher: TLS_AES_256_GCM_SHA384`
   - `Secure Renegotiation IS supported`

### Kiểm tra IMAPS trên cổng 993 của Gmail:
```bash
openssl s_client -connect imap.gmail.com:993 -showcerts
```

---

## 5. Kết Luận Kiểm Thử An Toàn Mạng

1. **Phòng vệ rò rỉ thông tin:** Trong toàn bộ chu kỳ sống của kết nối, từ lúc Client đăng nhập vào Mail Server cho đến khi Mail Server liên lạc với các máy chủ của Google, không có bất kỳ thông tin nhạy cảm nào (mật khẩu người dùng, App Password, Access Token, Refresh Token, nội dung email) bị lộ ở dạng văn bản thuần trên mạng.
2. **Đáp ứng tiêu chuẩn đề tài:** Thiết kế 2 lớp TLS phân tách độc lập bảo vệ tối đa tính bí mật (Confidentiality) và tính toàn vẹn (Integrity) theo đúng yêu cầu môn học Lập trình mạng PTIT.

