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

- **`Server (dev)`**: Chạy `bootRun` cho Mail Server với profile `dev`. Tự động đọc keystore SSL, chạy cổng HTTPS 8443, tự sinh AES/JWT secret phiên làm việc nếu chưa cấu hình.
- **`Client`**: Chạy task `run` cho Mail Client (giao diện Swing, lớp khởi động `com.mailapp.client.Main`).

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
1. Mở file [auth.http](file:///Mail%20Server/docs/http/auth.http) tại đường dẫn `Mail Server/docs/http/auth.http`.
2. Ở góc trên bên phải khung soạn thảo HTTP, chọn môi trường: **`dev`** (được cấu hình trong `http-client.private.env.json` với `verifyHostCertificate: false` để chấp nhận chứng chỉ SSL tự ký).
3. Bấm nút mũi tên xanh bên cạnh request #1 (`POST {{baseUrl}}/api/auth/login` với `admin`) hoặc request #2 (`lethanhcuong`) để kiểm tra nhận JWT token.

### Bước 4: Chạy Mail Client
- Trong danh sách Run Configurations, chọn **`Client`**.
- Bấm nút **Run** (Shift+F10). Giao diện đồ họa người dùng (Swing) của Mail Client sẽ xuất hiện.

---

## 5. Dừng môi trường khi hoàn thành

Khi dừng phiên làm việc:
1. Bấm nút **Stop** (Ctrl+F2) trên tiến trình `Server (dev)` và `Client` trong IntelliJ.
2. Dừng container MySQL:
   ```powershell
   powershell -ExecutionPolicy Bypass -File "Mail Server\scripts\dev-down.ps1"
   ```
