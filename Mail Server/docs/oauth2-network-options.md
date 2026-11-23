# Tùy Chọn Mạng Cho Google OAuth2 Callback Trong Môi Trường Mạng LAN

Tài liệu này phân tích bài toán xác thực **Google OAuth2** khi ứng dụng **Mail Server** và **Mail Client** triển khai trong mô hình mạng LAN nội bộ (theo đề tài Lập trình mạng PTIT), đồng thời trình bày các phương án khả thi và hạn chế kỹ thuật từ phía Google.

---

## 1. Bài Toán Kỹ Thuật

Trong luồng **Google OAuth 2.0 Authorization Code**:
1. User trên `Mail Client` (Swing) nhấn nút "Đăng nhập Google".
2. Client gọi `GET /api/oauth2/authorize` để lấy Google OAuth URL kèm `state`.
3. Trình duyệt của người dùng mở trang Google Consent Screen:
   `https://accounts.google.com/o/oauth2/v2/auth?...&redirect_uri=<REDIRECT_URI>`
4. Sau khi người dùng đồng ý cấp quyền, Google Server chuyển hướng (HTTP 302) trình duyệt của người dùng quay về:
   `<REDIRECT_URI>?code=...&state=...`
5. Trình duyệt người dùng gửi HTTP GET tới `<REDIRECT_URI>`, trúng vào endpoint `GET /api/oauth2/callback` của `Mail Server`. Server trích xuất `code`, gọi trực tiếp tới `https://oauth2.googleapis.com/token` để đổi lấy Refresh Token và Access Token.

### Vấn Đề Khi Chạy Trên Mạng LAN Khác Máy
Theo sơ đồ mạng đề tài:
- **Mail Server**: IP `192.168.1.10`, cổng HTTPS `8443`.
- **PC Clients**: IP `192.168.1.15`, `192.168.1.20`, `192.168.1.25`.

Nếu cấu hình:
- `redirect_uri = https://localhost:8443/api/oauth2/callback`:
  - Khi người dùng ở PC `192.168.1.15` xác thực xong, trình duyệt trên máy PC đó sẽ cố mở `https://localhost:8443/...`. Lúc này trình duyệt gửi request tới chính máy `192.168.1.15` (nơi không hề có Mail Server nào đang lắng nghe) $\rightarrow$ **Kết nối thất bại (Connection Refused)**.
- `redirect_uri = https://192.168.1.10:8443/api/oauth2/callback`:
  - Google Cloud Console **chặn tuyệt đối** việc khai báo IP Private (theo tiêu chuẩn RFC 1918: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`) trong danh sách **"Authorized redirect URIs"**. Google chỉ chấp nhận:
    1. Tên miền công khai có TLD hợp lệ (Top-Level Domain) và có chứng chỉ HTTPS hợp lệ.
    2. Riêng `localhost` và `127.0.0.1` được Google hỗ trợ ngoại lệ cho môi trường phát triển cục bộ (Local Development).

> [!WARNING] Cảnh báo về dịch vụ Dynamic DNS / Wildcard DNS (như `nip.io`, `sslip.io`)
> Nhiều hướng dẫn cũ gợi ý dùng `https://192.168.1.10.nip.io:8443/...`. Tuy nhiên, chính sách Google Cloud OAuth hiện tại kiểm duyệt rất nghiêm ngặt: Google thường xuyên từ chối các domain wildcard miễn phí (`*.nip.io`) hoặc yêu cầu Domain Verification qua Google Search Console đối với OAuth App. Do đó, **không thể coi `nip.io` là giải pháp ổn định hay được cam kết hoạt động**.

---

## 2. Ba Phương Án Triển Khai

| Tiêu chí | Phương án A: Trình duyệt trên Server | Phương án B: Cloudflare Tunnel / ngrok | Phương án C: Cloud VPS với Domain riêng |
| :--- | :--- | :--- | :--- |
| **Bản chất** | Chạy Client & Browser ngay trên máy Server | Mở đường hầm HTTPS công khai từ Server | Triển khai Server lên máy chủ Internet |
| **Độ phức tạp** | Rất thấp (Không cần cấu hình mạng thêm) | Thấp - Trung bình (Cài thêm agent tunnel) | Trung bình - Cao (Chi phí VPS & Domain) |
| **Redirect URI** | `https://localhost:8443/api/oauth2/callback` | `https://<subdomain>.trycloudflare.com/api/oauth2/callback` | `https://mail.yourdomain.com/api/oauth2/callback` |
| **Google chấp nhận** | Có (Hỗ trợ mặc định) | Có (Là public HTTPS hostname) | Có (Domain chính thức) |
| **Tính đa máy LAN** | Không (Chỉ demo tại máy Server) | Có (Mọi PC LAN đều callback được) | Có (Toàn bộ Internet & LAN) |

---

### Phương Án A: Mở Trình Duyệt Trên Máy Server (Khuyến nghị cho Demo cục bộ)

- **Cơ chế**:
  - `Mail Server` lắng nghe `0.0.0.0:8443`.
  - Mở một instance của `Mail Client` (hoặc mở trình duyệt) trực tiếp trên máy Server (`192.168.1.10`).
  - `GOOGLE_REDIRECT_URI=https://localhost:8443/api/oauth2/callback`.
- **Ưu điểm**:
  - Không phụ thuộc mạng ngoài, không cần cài công cụ bên thứ ba.
  - Phù hợp nhất cho việc test nhanh tính năng liên kết tài khoản Google tại bàn kỹ thuật.
- **Cách áp dụng**:
  - Khi cần kết nối tài khoản Gmail mới qua OAuth2, mở trình duyệt trên máy Server để thực hiện xác thực một lần. Sau khi Server nhận được Refresh Token và lưu mã hóa vào DB, các PC Client (`.15`, `.20`, `.25`) có thể đăng nhập bình thường và gọi API gửi/nhận mail mà không cần mở lại trình duyệt.

---

### Phương Án B: Cloudflare Tunnel hoặc ngrok (Khuyến nghị cho Demo đồ án phòng lab)

- **Cơ chế**:
  - Chạy một tunnel client nhẹ (ví dụ `cloudflared` hoặc `ngrok`) trên máy Server `192.168.1.10` để trỏ vào cổng `8443`.
  - Lấy domain HTTPS công khai tạm thời do Cloudflare/ngrok cấp (ví dụ: `https://mail-ptit.trycloudflare.com`).
  - Thêm URL này vào Google Cloud Console:
    `Authorized redirect URIs`: `https://mail-ptit.trycloudflare.com/api/oauth2/callback`
  - Đặt biến môi trường trên Server:
    `GOOGLE_REDIRECT_URI=https://mail-ptit.trycloudflare.com/api/oauth2/callback`
- **Quy trình hoạt động**:
  1. PC Client `192.168.1.15` gọi API `/api/oauth2/authorize` tới `https://192.168.1.10:8443`.
  2. Server trả về Google Auth URL chứa `redirect_uri=https://mail-ptit.trycloudflare.com/...`.
  3. Trình duyệt trên PC `.15` mở Google Consent.
  4. Người dùng bấm đồng ý, Google chuyển hướng trình duyệt về `https://mail-ptit.trycloudflare.com/api/oauth2/callback?code=...`.
  5. Cloudflare Tunnel forward request này về cổng `8443` trên máy Server nội bộ. Server đổi token thành công!
- **Lệnh chạy nhanh với Cloudflare Quick Tunnel (Miễn phí, không cần tài khoản)**:
  ```bash
  cloudflared tunnel --url https://localhost:8443 --no-tls-verify
  ```

---

### Phương Án C: Triển khai Server lên Cloud VPS kèm Domain Public

- **Cơ chế**:
  - Đặt `Mail Server` lên VPS (DigitalOcean, AWS EC2, Linode,...) có IP tĩnh public.
  - Trỏ bản ghi DNS: `mail.yourdomain.com -> IP VPS`.
  - Cài đặt Let's Encrypt SSL hợp lệ.
  - `GOOGLE_REDIRECT_URI=https://mail.yourdomain.com/api/oauth2/callback`.
- **Ưu điểm**:
  - Chuyên nghiệp, đúng chuẩn môi trường Production.
  - Chứng chỉ SSL được trình duyệt và Java tin cậy mặc định, không cần import chứng chỉ tự ký.

---

## 3. Khuyến Nghị Cho Buổi Báo Cáo Đồ Án Nhóm PTIT

1. **Giai đoạn Test & Code Backend**: Dùng **Phương án A** (`localhost:8443`).
2. **Giai đoạn Tích hợp đa máy LAN tại phòng máy**:
   - Các API thông thường (Đăng nhập hệ thống, gửi nhận mail bằng App Password hoặc Token đã lưu) chạy trực tiếp qua IP LAN `https://192.168.1.10:8443`.
   - Đối với tính năng "Liên kết tài khoản Google mới qua OAuth2":
     - Nếu phòng thi có Internet: Dùng **Phương án B (Cloudflare Tunnel)** để trình diễn luồng OAuth2 hoàn hảo từ máy Client.
     - Nếu phòng thi mạng cô lập (không ra được Internet): Lưu ý rằng Google OAuth2 **bắt buộc** phải có Internet để nói chuyện với `accounts.google.com`. Khi đó, nhóm nên chuẩn bị sẵn tài khoản đã liên kết trước trong DB để demo tính năng đồng bộ và gửi nhận mail XOAUTH2.

