# Hướng dẫn cấu hình Google Cloud OAuth 2.0 Client (Module M06)

Tài liệu hướng dẫn từng bước thiết lập **Google Auth Platform** (giao diện Google Cloud Console mới) để lấy `GOOGLE_CLIENT_ID` và `GOOGLE_CLIENT_SECRET` cho chức năng liên kết Gmail bằng OAuth2 / XOAUTH2 trên MailManager.

---

## 1. Cấu hình trên Google Auth Platform (Giao diện mới)

Truy cập [Google Cloud Console](https://console.cloud.google.com/), chọn hoặc tạo mới một Project (ví dụ: `network-programming-mail-project`), sau đó mở menu **Google Auth Platform** (trước đây là *APIs & Services $\rightarrow$ OAuth consent screen*).

> [!IMPORTANT]
> **KHÔNG cần bật Gmail API (`gmail.googleapis.com`)**: Mail Server kết nối trực tiếp tới Gmail thông qua giao thức chuẩn **IMAPS (cổng 993)** và **SMTP STARTTLS (cổng 587)** sử dụng cơ chế xác thực **SASL XOAUTH2**, không gọi Gmail REST API.

### Bước 1: Branding (Thương hiệu ứng dụng)
1. Ở thanh điều hướng bên trái **Google Auth Platform**, chọn **Branding** (hoặc **Overview $\rightarrow$ Get started** nếu thiết lập lần đầu).
2. Điền thông tin hiển thị cho người dùng cuối:
   - **App name**: Tên hiển thị trên màn hình đăng nhập Google (ví dụ: `network_programming_mail_project` hoặc `Mail App PTIT`).
   - **User support email**: Chọn địa chỉ email hỗ trợ của bạn.
   - **Developer contact information**: Nhập địa chỉ email liên hệ của nhà phát triển.
3. Bấm **Save**.

> [!NOTE]
> Tên bạn đặt trong mục **Clients $\rightarrow$ Name** chỉ là tên nội bộ trong Console, **không hiển thị** cho người dùng cuối. Tên xuất hiện trên màn hình đăng nhập Google luôn lấy từ mục **Branding $\rightarrow$ App name**.

### Bước 2: Audience (Đối tượng sử dụng & Test users)
1. Ở cột bên trái, chọn **Audience**.
2. Chọn loại đối tượng: **External** (dành cho tài khoản `@gmail.com` cá nhân).
3. **Giữ nguyên trạng thái `Testing`** (không cần Publish/Verify khi chạy đồ án).
4. Cuộn xuống mục **Test users** $\rightarrow$ bấm **+ Add users** $\rightarrow$ thêm đầy đủ các địa chỉ Gmail sẽ dùng để kiểm thử $\rightarrow$ bấm **Save**.

> [!WARNING]
> **Hạn mức của chế độ Testing**:
> - Chỉ các tài khoản Gmail có tên trong danh sách **Test users** mới được phép đăng nhập (tài khoản ngoài danh sách sẽ bị chặn với lỗi `403 access_denied`).
> - **Refresh Token hết hạn sau 7 ngày**: Ở trạng thái **Testing**, Google tự động thu hồi Refresh Token sau 7 ngày kể từ lúc cấp. Khi token hết hạn, Mail Server sẽ tự động chuyển trạng thái tài khoản Gmail sang `NEEDS_REAUTH` để người dùng bấm **"Cấp lại quyền"** trên Mail Client.

### Bước 3: Data Access (Phạm vi quyền truy cập — Scopes)
1. Ở cột bên trái, chọn **Data Access** $\rightarrow$ bấm **Add or remove scopes**.
2. Tại ô **Manually add scopes**, thêm đủ 3 scope bắt buộc sau:
   ```text
   https://mail.google.com/
   openid
   email
   ```
   - `https://mail.google.com/`: **Bắt buộc** để xác thực IMAPS/SMTP bằng XOAUTH2.
   - `openid` và `email`: Xác định danh tính và địa chỉ email đã xác thực (`email_verified=true`).
3. Bấm **Add to table** $\rightarrow$ **Update** $\rightarrow$ **Save**.

### Bước 4: Clients (Tạo OAuth 2.0 Client ID)
1. Ở cột bên trái, chọn **Clients** $\rightarrow$ bấm **+ Create client**.
2. Điền thông tin:
   - **Application type**: Chọn **Web application**.
   - **Name**: `Mail Server Backend` (chỉ hiển thị nội bộ trong Console).
   - **Authorized JavaScript origins**: **Để trống** (luồng trao đổi token chạy phía Backend Java, không gọi từ trình duyệt SPA).
   - **Authorized redirect URIs**: Bấm **+ Add URI** và nhập **chính xác từng ký tự**:
     ```text
     https://localhost:8443/api/oauth2/callback
     ```
3. Bấm **Create**, lưu lại **Client ID** và **Client Secret**.

---

## 2. Nạp cấu hình vào Mail Server

Điền các giá trị vào file `Mail Server/.env` (file này nằm trong `.gitignore`, **tuyệt đối không commit** lên Git):

```env
GOOGLE_CLIENT_ID=<YOUR_GOOGLE_CLIENT_ID>
GOOGLE_CLIENT_SECRET=<YOUR_GOOGLE_CLIENT_SECRET>
GOOGLE_REDIRECT_URI=https://localhost:8443/api/oauth2/callback
```

### Áp dụng thay đổi `.env`
- **Khi chạy bằng Docker Compose**:
  > [!CAUTION]
  > Sau khi sửa file `.env`, **BẮT BUỘC** chạy lệnh:
  > ```powershell
  > cd "Mail Server"
  > docker compose up -d
  > ```
  > **KHÔNG dùng `docker compose restart`**, vì lệnh `restart` chỉ khởi động lại container cũ với bộ biến môi trường cũ trong RAM mà không đọc lại file `.env`.
- **Khi chạy trực tiếp từ IntelliJ IDEA**:
  Bấm **Stop** (`Ctrl+F2`) rồi **Run** (`Shift+F10`) hoặc **Rerun** (`Ctrl+F5`) để `MailServerApplication` nạp lại `.env`.

---

## 3. Ba màn hình người dùng gặp khi liên kết Gmail

Khi người dùng bấm **`+ Liên kết Gmail (Google)`** trên Mail Client, trình duyệt mặc định sẽ mở trang ủy quyền Google theo 3 bước:

### Màn hình 1: Cảnh báo `"Google chưa xác minh ứng dụng này"`
- Xuất hiện do ứng dụng đang ở chế độ **Testing** (chưa nộp kiểm duyệt Google).
- **Thao tác**: Bấm **Nâng cao (Advanced)** $\rightarrow$ chọn **Đi tới `<App name>` (không an toàn) / Go to `<App name>` (unsafe)** *(hoặc bấm nút **Tiếp tục / Continue** nếu trình duyệt hiển thị trực tiếp)*.

### Màn hình 2: Màn hình xin quyền truy cập (Granular Consent)
- Google hiển thị các ô đánh dấu quyền truy cập cho từng dịch vụ.
- **Thao tác bắt buộc**: Người dùng **phải giữ nguyên dấu TICK** ở ô quyền truy cập Gmail (*"Đọc, soạn, gửi và xóa vĩnh viễn tất cả email của bạn khỏi Gmail"*) rồi bấm **Tiếp tục (Continue)**.
- **Cơ chế bảo vệ phía Server**: Callback `GET /api/oauth2/callback` kiểm tra trường `scope` trong phản hồi token từ Google có chứa `https://mail.google.com/` hay không. Nếu người dùng bỏ tick quyền này, Server sẽ **không lưu tài khoản**, ghi Audit Log mã `LINK_GMAIL_DENIED_SCOPE`, và trả về trang thông báo tiếng Việt yêu cầu liên kết lại và giữ nguyên các ô quyền.

### Màn hình 3: Trình duyệt chuyển hướng về Callback (`net::ERR_CERT_AUTHORITY_INVALID`)
- Sau khi Google cấp mã ủy quyền, trình duyệt chuyển hướng về `https://localhost:8443/api/oauth2/callback?code=...&state=...`.
- Do Mail Server dùng chứng chỉ TLS tự ký (`keystore.p12`), trình duyệt hiển thị cảnh báo **"Kết nối của bạn không phải là riêng tư (`net::ERR_CERT_AUTHORITY_INVALID`)"**.
- **Thao tác**: Bấm **Nâng cao (Advanced)** $\rightarrow$ bấm **Tiếp tục truy cập localhost (không an toàn) / Proceed to localhost (unsafe)**.
- Ngay lập tức, trình duyệt hiển thị trang **"Liên kết thành công"** từ Mail Server, và hộp thoại chờ trên Mail Client tự động cập nhật danh sách tài khoản Gmail.

---

## 4. Cảnh báo bảo mật về chứng chỉ tự ký trên Windows

> [!CAUTION]
> **KHÔNG khuyến nghị** đưa chứng chỉ tự ký (`mailapp.cer`) vào kho chứng chỉ gốc tin cậy (**Trusted Root Certification Authorities**) của Windows. Nếu khóa riêng (`keystore.p12`) bị lộ, kẻ tấn công có thể lợi dụng chứng chỉ gốc đã tin cậy để giả mạo kết nối trên máy tính đó.
>
> Trong môi trường phát triển/demo, nên bấm **Advanced $\rightarrow$ Proceed** trên trình duyệt (Mail Client Java đã tự xác thực riêng bằng file `Mail Client/certs/mailapp.cer` cô lập trong bộ nhớ, không ảnh hưởng hệ điều hành).

Nếu vì mục đích demo tạm thời bạn đã cài chứng chỉ vào kho người dùng hiện tại (`certutil -user -addstore Root ...`), hãy **gỡ bỏ ngay sau khi demo** bằng lệnh PowerShell sau:

```powershell
# Kiểm tra chứng chỉ trong kho Root của người dùng hiện tại
certutil -user -store Root "MailApp Server"

# Gỡ bỏ chứng chỉ tự ký khỏi kho Root của người dùng hiện tại
certutil -user -delstore Root "MailApp Server"
```
