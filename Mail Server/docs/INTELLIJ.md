# Hướng Dẫn Phát Triển Bằng IntelliJ IDEA

Tài liệu hướng dẫn thiết lập môi trường và chạy đồng thời **Mail Server** và **Mail Client** trong một cửa sổ IntelliJ IDEA.

---

## 1. Yêu cầu môi trường

- **JDK**: Phiên bản **Java 21 LTS** (Eclipse Temurin 21, Microsoft OpenJDK 21, hoặc Oracle OpenJDK 21).
  - Cấu hình trong IntelliJ: `File` $\rightarrow$ `Project Structure...` $\rightarrow$ `Project` $\rightarrow$ `SDK`: chọn JDK 21.
  - Cấu hình Gradle JVM: `Settings` (Ctrl+Alt+S) $\rightarrow$ `Build, Execution, Deployment` $\rightarrow$ `Build Tools` $\rightarrow$ `Gradle` $\rightarrow$ `Gradle JVM`: chọn JDK 21.
- **Docker Desktop**: Đang chạy (để cung cấp MySQL service cho môi trường dev).

---

## 2. Cách Link 2 Project Gradle trong 1 Cửa Sổ IDEA

Repo dự án gồm 2 Gradle project độc lập (`Mail Server` và `Mail Client`). Để làm việc tiện lợi trong cùng một cửa sổ IntelliJ:

1. Mở thư mục gốc `MailManager` bằng IntelliJ IDEA (`File` $\rightarrow$ `Open...` $\rightarrow$ chọn thư mục `MailManager`).
2. Mở thanh công cụ **Gradle Tool Window** ở cạnh phải màn hình (`View` $\rightarrow$ `Tool Windows` $\rightarrow$ `Gradle`).
3. Bấm vào biểu tượng **`+` (Link Gradle Project)**:
   - Điều hướng chọn file `Mail Server/build.gradle` $\rightarrow$ bấm **OK**.
4. Bấm tiếp biểu tượng **`+` (Link Gradle Project)** lần thứ hai:
   - Điều hướng chọn file `Mail Client/build.gradle` $\rightarrow$ bấm **OK**.
5. IntelliJ sẽ đồng bộ hóa cả hai project. Bạn sẽ thấy hai module `mail-server` và `mail-client` cùng xuất hiện trong cây thư mục Gradle.

---

## 3. Cấu hình chạy sẵn có (Shared Run Configurations)

Dự án đã định cấu hình sẵn trong thư mục `.run/` (tự động nhận diện khi mở project):

- **`MailServerApplication`**: Cấu hình khởi chạy trực tiếp lớp chính `com.mailapp.MailServerApplication` (dành cho nút ▶ Run và 🐞 Debug của IntelliJ). Tự động đặt `SPRING_PROFILES_ACTIVE=dev`, tự động nạp toàn bộ biến từ file `Mail Server/.env` (bao gồm `KEYSTORE_PASSWORD`, `JWT_SECRET`, `AES_KEY`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`), kết nối MySQL tại `localhost:3307`.
- **`Server (Docker MySQL)`**: Cấu hình chạy Server ngoài Docker kết nối tới cơ sở dữ liệu MySQL chạy trong Docker Compose (`localhost:3307`).
- **`Server (dev)`**: Chạy qua Gradle task `bootRun` với profile `dev`.
- **`Client`**: Chạy task `run` cho Mail Client ở chế độ bảo mật an toàn mặc định (xác thực chứng chỉ `.cer`).
- **`Client (Insecure Dev)`**: Chạy Mail Client với cờ `-Dmailclient.insecure=true` (bỏ qua xác thực TLS, hiển thị dải đỏ cảnh báo `"⚠️ KẾT NỐI KHÔNG AN TOÀN"`). Có thể chạy từ terminal: `./gradlew run -Pinsecure=true` hoặc `./gradlew run --args="--insecure"`.

---

## 4. Cơ chế hoạt động của Profile dev khi chạy ngoài Docker

1. **Keystore SSL & Mật khẩu**:
   - `MailServerApplication` tự động tìm và nạp `KEYSTORE_PASSWORD` từ file `.env` để mở file `classpath:keystore.p12` (cùng file chứng chỉ mà Docker sử dụng).
   - Nếu chưa có file keystore hoặc chưa có mật khẩu, hệ thống tự động sinh một keystore tự ký tạm thời trong thư mục temp của hệ điều hành với SAN `dns:localhost,ip:127.0.0.1` để bảo đảm HTTPS luôn hoạt động.
2. **Cơ sở dữ liệu (MySQL)**:
   - Profile `dev` khi chạy ngoài Docker tự động cấu hình kết nối tới MySQL tại `localhost:3307` (cổng được ánh xạ bởi Docker Compose: `0.0.0.0:3307 -> 3306`).
3. **Phát hiện xung đột cổng 8443**:
   - Trước khi khởi động Tomcat, Server tự động kiểm tra xem cổng 8443 có đang bị chiếm không (ví dụ: do container Docker `mailapp-server` đang chạy).
   - Nếu cổng bị chiếm, Server in thông báo hướng dẫn rõ ràng:
     ```text
     ================================================================================
     Cổng 8443 đang bị chiếm, chạy docker compose stop mail-server
     ================================================================================
     ```
     Sau đó dừng lại ngay lập tức thay vì bung stack trace lỗi dài khó hiểu.

---

## 4. Thứ tự thực hiện khi chạy ứng dụng

### Bước 1: Bật dịch vụ MySQL
Chạy script PowerShell từ terminal:
```powershell
cd "Mail Server"
powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1 -Background
```
*(Hoặc dùng lệnh Docker: `docker compose up -d mysql` trong thư mục `Mail Server/`).*

### Bước 2: Chạy Mail Server
- Trên thanh công cụ trên cùng của IntelliJ, chọn run configuration **`Server (dev)`**.
- Bấm nút **Run** (Shift+F10) hoặc **Debug** (Shift+F9).
- Chờ log xuất hiện dòng:
  ```text
  Tomcat started on port 8443 (https) with context path '/'
  ```

### Bước 3: Kiểm thử API bằng IntelliJ HTTP Client
1. Mở [auth.http](file:///Mail%20Server/docs/http/auth.http), [mail.http](file:///Mail%20Server/docs/http/mail.http) (hộp thư thật), hoặc [admin.http](file:///Mail%20Server/docs/http/admin.http) (EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2, xuất CSV).
2. Ở góc trên bên phải khung soạn thảo HTTP, chọn môi trường: **`dev`** (được cấu hình trong `http-client.private.env.json` với `verifyHostCertificate: false` để chấp nhận chứng chỉ SSL tự ký).
3. Bấm nút mũi tên xanh bên cạnh request #1 (`POST {{baseUrl}}/api/auth/login` với `admin`) hoặc request #2 (`lethanhcuong`) để kiểm tra nhận JWT token (HS256).

### Bước 4: Chạy Mail Client (Pure Swing — 3 cột)
- Trong danh sách Run Configurations, chọn **`Client`** (hoặc chạy `./gradlew run` trong thư mục `Mail Client`).
- Giao diện đồ họa 3 cột (Tài khoản & Thư mục | Danh sách thư & Tìm kiếm | Khung đọc thư + Tải đính kèm & Xóa) với bộ logo thương hiệu Java2D **Mail Manager** (hỗ trợ Sáng/Tối) sẽ xuất hiện.
- Sinh lại bộ icon PNG/ICO + banner README: `./gradlew run --args="--generate-icons"`.
- Xuất bộ 8 ảnh mockup có dấu nước `MÔ PHỎNG`: `./gradlew run --args="--screenshots"`.

---

## 5. Dừng môi trường khi hoàn thành

Khi dừng phiên làm việc:
1. Bấm nút **Stop** (Ctrl+F2) trên tiến trình `Server (dev)` và `Client` trong IntelliJ.
2. Dừng container MySQL:
   ```powershell
   powershell -ExecutionPolicy Bypass -File "Mail Server\scripts\dev-down.ps1"
   ```

