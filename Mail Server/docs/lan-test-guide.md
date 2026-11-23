# Hướng Dẫn Kiểm Thử Mail Server Trên Mạng LAN (LAN Test Guide)

Tài liệu hướng dẫn chi tiết từng bước kiểm thử kết nối mạng từ các máy **PC Client** tới **Mail Server** theo sơ đồ đề tài Lập trình mạng PTIT.

---

## 1. Sơ Đồ Mạng Đề Tài

```
                           [ ROUTER / SWITCH LAN ]
                             Subnet: 192.168.1.0/24
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        │                              │                              │
 ┌──────────────┐               ┌──────────────┐               ┌──────────────┐
 │ Mail Server  │               │ PC Client 1  │               │ PC Client 2  │
 │ 192.168.1.10 │               │ 192.168.1.15 │               │ 192.168.1.20 │
 │ (Port 8443)  │               └──────────────┘               └──────────────┘
 └──────────────┘
```

- **Mail Server**: Địa chỉ IP cấu hình `192.168.1.10` (hoặc IP Wi-Fi/Ethernet thực tế của máy Server, ví dụ `192.168.1.74`). Lắng nghe cổng **HTTPS 8443** trên mọi interface (`0.0.0.0`).
- **PC Clients**: Các máy trạm `192.168.1.15`, `192.168.1.20`,... chạy ứng dụng giao diện Java Swing.

---

## 2. Chuẩn Bị Trên Máy Server

### Bước 2.1: Sinh Keystore SSL PKCS12 và Xuất Chứng Chỉ Công Khai
Chạy script PowerShell để sinh chứng chỉ SSL tự ký có chứa IP LAN:
```powershell
cd "Mail Server"
.\scripts\gen-keystore.ps1 -AdditionalIps "192.168.1.74"
```
Kết quả sinh ra 2 tệp:
- `src/main/resources/keystore.p12`: Chứa Private Key & Certificate (Server dùng).
- `src/main/resources/mailapp.cer`: Chứng chỉ công khai (chia sẻ cho Client).

### Bước 2.2: Mở Cổng Tường Lửa (Windows Firewall)
> [!IMPORTANT]
> Script mở tường lửa yêu cầu quyền **Administrator**. Nhấp chuột phải vào PowerShell $\rightarrow$ chọn **Run as administrator**:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\open-firewall.ps1
```
Script sẽ tạo luật Inbound TCP 8443 cho phép các máy PC khác trong mạng LAN truy cập vào Server.

### Bước 2.3: Khởi Động Server Lắng Nghe Mọi Giao Diện
Khởi động server (sử dụng tệp `.env.local` nếu có):
```powershell
.\scripts\run-server.ps1
```
Kiểm tra log server khởi động, xuất hiện dòng:
```
Tomcat started on port 8443 (https) with context path '/'
```
Đặc biệt, thuộc tính `server.address=0.0.0.0` trong `application.properties` đảm bảo Tomcat lắng nghe trên tất cả card mạng của máy Server.

---

## 3. Các Bước Kiểm Thử Từ Máy PC Client

Thực hiện các lệnh sau tại cửa sổ dòng lệnh (PowerShell hoặc Command Prompt) của máy PC Client (`192.168.1.15`):

*(Thay thế `192.168.1.10` bằng IP thực tế của máy Server nếu khác)*

### Bước 3.1: Kiểm Tra Kết Nối Tầng Mạng (ICMP / Ping)
Kiểm tra xem hai máy có cùng lớp mạng và thông mạch với nhau không:
```powershell
ping 192.168.1.10
```
- **Thành công**: Nhận được `Reply from 192.168.1.10: bytes=32 time<5ms TTL=128`.
- **Nếu thất bại (`Request timed out`)**:
  - Kiểm tra xem máy Server có đang bật chế độ chặn ICMP trong Firewall không.
  - Kiểm tra 2 máy có cùng dải mạng subnet mask `255.255.255.0` không.

### Bước 3.2: Kiểm Tra Tầng Giao Vận (TCP Port 8443)
Sử dụng công cụ `Test-NetConnection` của PowerShell để kiểm tra cổng 8443:
```powershell
Test-NetConnection -ComputerName 192.168.1.10 -Port 8443
```
- **Thành công**: Dòng kết quả cuối cùng báo:
  ```
  TcpTestSucceeded : True
  ```
- **Nếu `TcpTestSucceeded : False`**:
  - Kiểm tra máy Server đã chạy script `open-firewall.ps1` chưa.
  - Kiểm tra Server đã khởi động và cổng 8443 đã ở trạng thái `LISTENING` chưa (`netstat -ano | findstr 8443`).

### Bước 3.3: Kiểm Tra Endpoint HTTP/HTTPS Công Khai (Curl)
Sử dụng lệnh `curl.exe` với cờ `-k` (bỏ qua kiểm tra CA bên thứ ba đối với chứng chỉ tự ký):

1. **Kiểm tra Actuator Health Check**:
```powershell
curl.exe -k -i https://192.168.1.10:8443/actuator/health
```
Kết quả mong đợi:
```http
HTTP/1.1 200 OK
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"status":"UP"}
```

2. **Kiểm tra Public Health Endpoint**:
```powershell
curl.exe -k -i https://192.168.1.10:8443/api/public/health
```
Kết quả mong đợi:
```http
HTTP/1.1 200 OK
Content-Type: application/json

{"status":"UP","components":{"db":{"status":"UP"}}}
```

---

## 4. Hướng Dẫn Cấu Hình Tin Cậy Chứng Chỉ SSL (Import Certificate)

Do Mail Server sử dụng chứng chỉ SSL tự ký (Self-Signed Certificate), khi ứng dụng Java Swing Client hoặc trình duyệt kết nối qua HTTPS sẽ gặp lỗi:
`sun.security.validator.ValidatorException: PKIX path building failed`.

Để khắc phục, Client có thể chọn 1 trong 2 cách sau:

### Cách 1: Import Chứng Chỉ `mailapp.cer` Vào Java Keystore (cacerts) của Client
Copy file `mailapp.cer` từ Server sang máy Client, mở PowerShell với quyền Administrator trên máy Client:
```powershell
# Đường dẫn tới tệp cacerts của máy ảo Java Client
$cacertsPath = "$env:JAVA_HOME\lib\security\cacerts"

# Lệnh import vào keystore (mật khẩu mặc định của cacerts trong Java là 'changeit')
keytool -importcert -alias mailapp -file .\mailapp.cer -keystore $cacertsPath -storepass changeit -noprompt
```
Sau khi import, mọi ứng dụng Java chạy trên máy Client sẽ tin cậy chứng chỉ HTTPS của Mail Server mà không cần can thiệp code.

### Cách 2: Cấu Hình SSLContext Tùy Biến Trong Swing Client (Khuyến nghị cho Đồ án)
Thay vì bắt từng máy cài đặt chứng chỉ hệ thống, trong mã nguồn `Mail Client`, cấu hình `HttpClient` hoặc `HttpsURLConnection` với `SSLContext` tin cậy trực tiếp file `mailapp.cer` đi kèm trong thư mục `resources` của Client:
```java
CertificateFactory cf = CertificateFactory.getInstance("X.509");
InputStream caInput = getClass().getResourceAsStream("/mailapp.cer");
Certificate ca = cf.generateCertificate(caInput);

KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
keyStore.load(null, null);
keyStore.setCertificateEntry("mailapp", ca);

TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
tmf.init(keyStore);

SSLContext sslContext = SSLContext.getInstance("TLS");
sslContext.init(null, tmf.getTrustManagers(), null);

HttpClient client = HttpClient.newBuilder()
    .sslContext(sslContext)
    .build();
```
Cách này giúp ứng dụng Swing Client đóng gói độc lập, cắm USB sang máy khác chạy được ngay mà không cần cấu hình Windows Certificate Manager.

