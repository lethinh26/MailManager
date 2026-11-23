package com.mailapp.client.net;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mailapp.client.model.*;
import com.mailapp.client.model.AdminClientDtos.*;
import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.util.ClientConfig;

import javax.net.ssl.*;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;

/**
 * ApiClient kết nối Mail Server qua HTTPS bằng java.net.http.HttpClient.
 * Hỗ trợ gắn JWT tự động, xử lý lỗi thống nhất (đọc cả 'code' và 'errorCode'),
 * đọc header 'Retry-After' khi bị rate limit (429).
 *
 * Mặc định CHỈ tin chứng chỉ SSL nạp từ file .cer (cấu hình ~/.mailclient/config.properties hoặc ./certs/mailapp.cer).
 * Chế độ tin mọi chứng chỉ CHỈ khi chạy với cờ JVM: -Dmailclient.insecure=true.
 */
public class ApiClient {

    private static final String DEFAULT_BASE_URL = "https://localhost:8443";
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile String accessToken;

    public ApiClient() {
        this(ClientConfig.getInstance().getServerUrl(), (File) null);
    }

    public ApiClient(String baseUrl) {
        this(baseUrl, (File) null);
    }

    public ApiClient(String baseUrl, File certFile) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = createObjectMapper();
        this.httpClient = createHttpClient(certFile, null);
    }

    public ApiClient(String baseUrl, SSLContext sslContext) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = createObjectMapper();
        this.httpClient = createHttpClient(null, sslContext);
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private HttpClient createHttpClient(File explicitCertFile, SSLContext explicitSslContext) {
        try {
            String lowerUrl = this.baseUrl.toLowerCase(Locale.ROOT);
            boolean isPlainLoopbackHttp = explicitSslContext == null
                    && explicitCertFile == null
                    && (lowerUrl.startsWith("http://localhost") || lowerUrl.startsWith("http://127.0.0.1"));
            if (isPlainLoopbackHttp) {
                return HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
            }

            SSLContext sslContext = explicitSslContext;
            if (sslContext == null) {
                sslContext = buildSslContext(explicitCertFile);
            }

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Không thể khởi tạo HttpClient với SSLContext: " + e.getMessage(), e);
        }
    }

    private SSLContext buildSslContext(File explicitCertFile) throws Exception {
        boolean insecure = Boolean.getBoolean("mailclient.insecure");

        if (insecure) {
            // Chế độ insecure tin mọi chứng chỉ CHỈ khi có cờ -Dmailclient.insecure=true
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            return sc;
        }

        File certFile = explicitCertFile != null ? explicitCertFile : ClientConfig.getInstance().findCertFile();
        if (certFile == null || !certFile.exists()) {
            throw new IllegalStateException(
                    "KHÔNG TÌM THẤY CHỨNG CHỈ SSL (.cer) CỦA SERVER!\n" +
                    "Mặc định Mail Client chỉ tin cậy chứng chỉ nạp từ file .cer để bảo vệ kết nối HTTPS.\n" +
                    "Hướng dẫn lấy chứng chỉ từ Server:\n" +
                    "  1. Xuất chứng chỉ công khai từ keystore Server:\n" +
                    "     powershell -ExecutionPolicy Bypass -File scripts/gen-keystore.ps1\n" +
                    "     (hoặc: keytool -exportcert -keystore server.p12 -alias mailserver -file certs/mailapp.cer)\n" +
                    "  2. Đặt file vào ./certs/mailapp.cer hoặc cấu hình cert.path trong ~/.mailclient/config.properties\n" +
                    "  3. (Dành riêng cho dev test nhanh) Chạy với tham số JVM: -Dmailclient.insecure=true\n"
            );
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert;
        try (InputStream is = new FileInputStream(certFile)) {
            cert = (X509Certificate) cf.generateCertificate(is);
        }

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("mailapp-server", cert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, tmf.getTrustManagers(), new SecureRandom());
        return sc;
    }

    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void clearAccessToken() {
        this.accessToken = null;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // =========================================================================
    // API Endpoints
    // =========================================================================

    /**
     * Kiểm tra kết nối tới Server (ping) và trả về độ trễ (latency ms).
     */
    public long pingServer() throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/public/config"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 400) {
            return System.currentTimeMillis() - start;
        }
        throw new ApiException(response.statusCode(), "PING_FAILED", "Máy chủ phản hồi HTTP " + response.statusCode());
    }

    /**
     * [EP-P1] Lấy cấu hình công khai của hệ thống: registrationEnabled, maintenanceMode, v.v.
     */
    public PublicConfigDto getPublicConfig() throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder("/api/public/config")
                .GET()
                .build();
        return execute(request, PublicConfigDto.class);
    }

    /**
     * [EP-A2] Đăng nhập hệ thống, nhận Access Token JWT.
     */
    public LoginResponse login(String username, String password) throws IOException, InterruptedException {
        LoginRequest body = new LoginRequest(username, password);
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = newRequestBuilder("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        LoginResponse response = execute(request, LoginResponse.class);
        if (response != null && response.accessToken() != null) {
            this.accessToken = response.accessToken();
        }
        return response;
    }

    public record GoogleLoginStartDto(String authorizeUrl, String pollToken) {}
    public record GoogleLoginPollDto(String status, LoginResponse loginResponse, String code, String message) {}

    /**
     * Khởi tạo phiên Đăng nhập trực tiếp bằng Google OAuth2 (PKCE S256).
     */
    public GoogleLoginStartDto startGoogleOAuthLogin() throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder("/api/auth/oauth2/google/start")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return execute(request, GoogleLoginStartDto.class);
    }

    /**
     * Kiểm tra kết quả phiên Đăng nhập Google OAuth2 bằng pollToken (PENDING / SUCCESS / FAILED).
     */
    public GoogleLoginPollDto pollGoogleOAuthLogin(String pollToken) throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder("/api/auth/oauth2/google/poll?pollToken=" + urlEnc(pollToken))
                .GET()
                .build();
        GoogleLoginPollDto dto = execute(request, GoogleLoginPollDto.class);
        if (dto != null && "SUCCESS".equalsIgnoreCase(dto.status())
                && dto.loginResponse() != null && dto.loginResponse().accessToken() != null) {
            this.accessToken = dto.loginResponse().accessToken();
        }
        return dto;
    }

    /**
     * [EP-A1] Đăng ký tài khoản mới.
     */
    public UserDto register(RegisterRequest registerRequest) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(registerRequest);

        HttpRequest request = newRequestBuilder("/api/auth/register")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return execute(request, UserDto.class);
    }

    /**
     * [EP-A3] Lấy thông tin tài khoản hiện tại.
     */
    public UserDto getCurrentUser() throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder("/api/auth/me")
                .GET()
                .build();
        return execute(request, UserDto.class);
    }

    /**
     * [EP-A4] Đổi mật khẩu tài khoản và cấp Access Token JWT mới.
     */
    public ChangePasswordResponse changePassword(String currentPassword, String newPassword)
            throws IOException, InterruptedException {
        ChangePasswordRequest body = new ChangePasswordRequest(currentPassword, newPassword);
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = newRequestBuilder("/api/auth/change-password")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        ChangePasswordResponse response = execute(request, ChangePasswordResponse.class);
        if (response != null && response.accessToken() != null) {
            this.accessToken = response.accessToken();
        }
        return response;
    }

    /**
     * [EP-G1] Lấy danh sách tài khoản Gmail đã liên kết.
     */
    public List<GmailAccountDto> getGmailAccounts() throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder("/api/gmail-accounts")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(response);

        if (response.body() == null || response.body().isBlank()) {
            return Collections.emptyList();
        }
        return objectMapper.readValue(response.body(), new TypeReference<List<GmailAccountDto>>() {});
    }

    /**
     * [EP-O1] Lấy URL uỷ quyền Google OAuth2.
     */
    public String getOAuth2AuthorizeUrl() throws IOException, InterruptedException {
        return getOAuth2AuthorizeUrl(null);
    }

    /**
     * [EP-O1] Lấy URL uỷ quyền Google OAuth2 (tùy chọn accountId khi thêm OAuth2 hoặc cấp lại quyền cho hộp thư đã có).
     */
    public String getOAuth2AuthorizeUrl(Long existingAccountId) throws IOException, InterruptedException {
        String path = existingAccountId != null
                ? "/api/oauth2/authorize-url?accountId=" + existingAccountId
                : "/api/oauth2/authorize-url";
        HttpRequest request = newRequestBuilder(path)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(response);

        JsonNode root = objectMapper.readTree(response.body());
        if (root.hasNonNull("authorizeUrl")) {
            return root.get("authorizeUrl").asText();
        }
        throw new ApiException(response.statusCode(), "PARSE_ERROR", "Không tìm thấy URL uỷ quyền trong phản hồi máy chủ.");
    }

    /**
     * [Mở rộng ngoài 27] Đổi phương thức đăng nhập chính (OAUTH2 hoặc APP_PASSWORD) cho hộp thư Gmail.
     */
    public GmailAccountDto setPreferredGmailMethod(Long id, String preferredMethod)
            throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(Map.of("preferredMethod", preferredMethod));
        HttpRequest request = newRequestBuilder("/api/gmail-accounts/" + id + "/preferred-method")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(request, GmailAccountDto.class);
    }

    /**
     * [Mở rộng ngoài 27] Gỡ một phương thức đăng nhập (OAUTH2 hoặc APP_PASSWORD) khỏi hộp thư Gmail.
     * Nếu gỡ phương thức cuối cùng phải truyền confirmUnlink=true để xóa luôn liên kết.
     */
    public Map<String, Object> removeGmailMethod(Long id, String method, boolean confirmUnlink)
            throws IOException, InterruptedException {
        String path = "/api/gmail-accounts/" + id + "/methods/" + urlEnc(method)
                + (confirmUnlink ? "?confirmUnlink=true" : "");
        HttpRequest request = newRequestBuilder(path)
                .DELETE()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(response);
        return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    /**
     * [EP-G2] Liên kết tài khoản Gmail bằng App Password 16 ký tự.
     */
    public GmailAccountDto linkGmailWithAppPassword(String email, String appPassword, String displayName)
            throws IOException, InterruptedException {
        Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("email", email);
        payload.put("appPassword", appPassword);
        if (displayName != null && !displayName.isBlank()) {
            payload.put("displayName", displayName);
        }
        String json = objectMapper.writeValueAsString(payload);
        HttpRequest request = newRequestBuilder("/api/gmail-accounts/app-password")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return execute(request, GmailAccountDto.class);
    }

    /**
     * [EP-G3] Hủy liên kết tài khoản Gmail theo ID.
     */
    public void deleteGmailAccount(Long id) throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder("/api/gmail-accounts/" + id)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(response);
    }

    /**
     * Gửi yêu cầu POST không body tới đường dẫn tương đối (phục vụ kiểm thử phân quyền/hành động đơn giản).
     */
    public String postRaw(String relativePath) throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder(relativePath)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(response);
        return response.body();
    }

    /**
     * Kiểm tra thử kết nối Gmail (IMAP/SMTP) qua endpoint chính thức POST /api/gmail-accounts/{id}/test.
     */
    public Map<String, Object> testGmailConnection(Long id) throws IOException, InterruptedException {
        HttpRequest request = newMailRequestBuilder("/api/gmail-accounts/" + id + "/test")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(response);

        return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Tải ảnh đại diện Google của tài khoản Gmail qua Mail Server (GET /api/gmail-accounts/{id}/avatar).
     * Trả về mảng byte ảnh nếu có (HTTP 200), hoặc null nếu không có ảnh (204/404).
     */
    public byte[] getGmailAccountAvatar(Long id) throws IOException, InterruptedException {
        HttpRequest request = newRequestBuilder("/api/gmail-accounts/" + id + "/avatar")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
            return response.body();
        }
        return null;
    }

    /**
     * Đổi tên hiển thị của tài khoản Gmail (PATCH /api/gmail-accounts/{id}).
     */
    public GmailAccountDto patchGmailDisplayName(Long id, String displayName) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(Map.of("displayName", displayName));
        HttpRequest request = newRequestBuilder("/api/gmail-accounts/" + id)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(request, GmailAccountDto.class);
    }

    /**
     * [M08] Lấy danh sách thư mục của tài khoản Gmail.
     */
    public List<MailFolderDto> getMailFolders(Long gmailId) throws IOException, InterruptedException {
        HttpRequest request = newMailRequestBuilder("/api/mails/folders?gmailId=" + gmailId)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(response);
        if (response.body() == null || response.body().isBlank()) return Collections.emptyList();
        return objectMapper.readValue(response.body(), new TypeReference<List<MailFolderDto>>() {});
    }

    /**
     * [M08] Liệt kê & tìm kiếm thư theo phân trang UID.
     */
    public MailListResponse getMails(Long gmailId, String folder, int page, int size,
                                     String q, Boolean unread, Boolean hasAttachment,
                                     String from, String since) throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder("/api/mails?gmailId=").append(gmailId)
                .append("&folder=").append(urlEnc(folder != null ? folder : "INBOX"))
                .append("&page=").append(page)
                .append("&size=").append(size);
        if (q != null && !q.isBlank()) sb.append("&q=").append(urlEnc(q.trim()));
        if (unread != null) sb.append("&unread=").append(unread);
        if (hasAttachment != null) sb.append("&hasAttachment=").append(hasAttachment);
        if (from != null && !from.isBlank()) sb.append("&from=").append(urlEnc(from.trim()));
        if (since != null && !since.isBlank()) sb.append("&since=").append(urlEnc(since.trim()));

        HttpRequest request = newMailRequestBuilder(sb.toString()).GET().build();
        return execute(request, MailListResponse.class);
    }

    /**
     * [M08] Đọc chi tiết 1 thư (mặc định chặn ảnh từ xa).
     */
    public MailDetailResponse getMailDetail(Long gmailId, String folder, long uid,
                                            boolean loadRemote, Long uidValidity) throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/mails/")
                .append(gmailId).append("/")
                .append(urlEnc(folder)).append("/")
                .append(uid)
                .append("?loadRemote=").append(loadRemote);
        if (uidValidity != null) path.append("&uidValidity=").append(uidValidity);

        HttpRequest request = newMailRequestBuilder(path.toString()).GET().build();
        return execute(request, MailDetailResponse.class);
    }

    public record DownloadResult(File file, long bytesWritten, String sha256Hex) {}

    /**
     * [M12] Tải tệp đính kèm dạng stream thẳng xuống tệp cục bộ và tính SHA-256.
     */
    public DownloadResult downloadAttachmentToFile(Long gmailId, String folder, long uid,
                                                   int attachmentIndex, Long uidValidity,
                                                   File targetFile) throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/mails/")
                .append(gmailId).append("/")
                .append(urlEnc(folder)).append("/")
                .append(uid).append("/attachments/").append(attachmentIndex).append("/download");
        if (uidValidity != null) path.append("?uidValidity=").append(uidValidity);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(120))
                .GET();
        if (accessToken != null && !accessToken.isBlank()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errBody = "";
            try (InputStream is = response.body()) {
                if (is != null) errBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            handleErrorBody(response.statusCode(), errBody, response.headers());
        }

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("Không khởi tạo được SHA-256", e);
        }

        long totalBytes = 0L;
        try (InputStream is = response.body();
             OutputStream os = new BufferedOutputStream(new FileOutputStream(targetFile))) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                os.write(buf, 0, r);
                sha.update(buf, 0, r);
                totalBytes += r;
            }
            os.flush();
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : sha.digest()) {
            hex.append(String.format("%02x", b));
        }
        return new DownloadResult(targetFile, totalBytes, hex.toString());
    }

    /**
     * [M10] Gửi thư kèm tệp đính kèm qua multipart/form-data.
     */
    public SendMailResponse sendMailMultipart(SendMailPayload payload, List<File> attachmentFiles)
            throws IOException, InterruptedException {
        String boundary = "----MailManagerBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String crlf = "\r\n";

        // Part 1: JSON payload
        String payloadJson = objectMapper.writeValueAsString(payload);
        baos.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"payload\"" + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: application/json; charset=UTF-8" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(payloadJson.getBytes(StandardCharsets.UTF_8));
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));

        // Part 2..N: Attachments
        if (attachmentFiles != null) {
            for (File f : attachmentFiles) {
                if (f == null || !f.exists() || !f.isFile()) continue;
                String safeName = f.getName().replace("\"", "_");
                String mimeType = Files.probeContentType(f.toPath());
                if (mimeType == null || mimeType.isBlank()) mimeType = "application/octet-stream";

                baos.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
                baos.write(("Content-Disposition: form-data; name=\"attachments\"; filename=\"" + safeName + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
                baos.write(("Content-Type: " + mimeType + crlf + crlf).getBytes(StandardCharsets.UTF_8));
                baos.write(Files.readAllBytes(f.toPath()));
                baos.write(crlf.getBytes(StandardCharsets.UTF_8));
            }
        }
        baos.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/mails/send"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()));
        if (accessToken != null && !accessToken.isBlank()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        return execute(builder.build(), SendMailResponse.class);
    }

    /**
     * [M16] Xóa thư (mặc định chuyển vào Thùng rác).
     */
    public void deleteMail(Long gmailId, String folder, long uid, boolean permanent, Long uidValidity)
            throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/mails/")
                .append(gmailId).append("/")
                .append(urlEnc(folder)).append("/")
                .append(uid)
                .append("?permanent=").append(permanent);
        if (uidValidity != null) path.append("&uidValidity=").append(uidValidity);

        HttpRequest request = newMailRequestBuilder(path.toString()).DELETE().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(response);
    }

    /**
     * [M17] Cập nhật cờ Seen / Flagged.
     */
    public MailFlagsDto updateMailFlags(Long gmailId, String folder, long uid,
                                        Boolean seen, Boolean flagged, Long uidValidity)
            throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/mails/")
                .append(gmailId).append("/")
                .append(urlEnc(folder)).append("/")
                .append(uid).append("/flags");
        if (uidValidity != null) path.append("?uidValidity=").append(uidValidity);

        Map<String, Object> body = new LinkedHashMap<>();
        if (seen != null) body.put("seen", seen);
        if (flagged != null) body.put("flagged", flagged);
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = newMailRequestBuilder(path.toString())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(request, MailFlagsDto.class);
    }

    // =========================================================================
    // [ADMIN MODULE] EP-AD1..AD8, EP-S1/S2, EP-ST1/ST2
    // =========================================================================

    public AdminPageResponse<AdminUserSummary> adminListUsers(String q, String role, Boolean enabled,
                                                              int page, int size, String sortBy, String sortDir)
            throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/admin/users?page=").append(page)
                .append("&size=").append(size);
        if (q != null && !q.isBlank()) path.append("&q=").append(urlEnc(q.trim()));
        if (role != null && !role.isBlank()) path.append("&role=").append(urlEnc(role.trim()));
        if (enabled != null) path.append("&enabled=").append(enabled);
        if (sortBy != null && !sortBy.isBlank()) path.append("&sortBy=").append(urlEnc(sortBy));
        if (sortDir != null && !sortDir.isBlank()) path.append("&sortDir=").append(urlEnc(sortDir));

        HttpRequest req = newRequestBuilder(path.toString()).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(resp);
        return objectMapper.readValue(resp.body(), new TypeReference<>() {});
    }

    public AdminCreateUserResponse adminCreateUser(AdminCreateUserRequest requestDto)
            throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(requestDto);
        HttpRequest req = newRequestBuilder("/api/admin/users")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(req, AdminCreateUserResponse.class);
    }

    public AdminUserDetail adminGetUserDetail(Long userId) throws IOException, InterruptedException {
        HttpRequest req = newRequestBuilder("/api/admin/users/" + userId).GET().build();
        return execute(req, AdminUserDetail.class);
    }

    public AdminUserSummary adminLockUser(Long userId) throws IOException, InterruptedException {
        HttpRequest req = newRequestBuilder("/api/admin/users/" + userId + "/lock")
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        return execute(req, AdminUserSummary.class);
    }

    public AdminUserSummary adminUnlockUser(Long userId) throws IOException, InterruptedException {
        HttpRequest req = newRequestBuilder("/api/admin/users/" + userId + "/unlock")
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        return execute(req, AdminUserSummary.class);
    }

    public AdminUserSummary adminUpdateUserRole(Long userId, String role) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(Map.of("role", role));
        HttpRequest req = newRequestBuilder("/api/admin/users/" + userId + "/role")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(req, AdminUserSummary.class);
    }

    public AdminResetPasswordResponse adminResetPassword(Long userId) throws IOException, InterruptedException {
        HttpRequest req = newRequestBuilder("/api/admin/users/" + userId + "/reset-password")
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        return execute(req, AdminResetPasswordResponse.class);
    }

    public void adminDeleteUser(Long userId) throws IOException, InterruptedException {
        HttpRequest req = newRequestBuilder("/api/admin/users/" + userId).DELETE().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(resp);
    }

    public List<AdminSettingItem> adminGetSettings() throws IOException, InterruptedException {
        HttpRequest req = newRequestBuilder("/api/admin/settings").GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(resp);
        return objectMapper.readValue(resp.body(), new TypeReference<>() {});
    }

    public List<AdminSettingItem> adminUpdateSettings(Map<String, String> settings)
            throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(Map.of("settings", settings));
        HttpRequest req = newRequestBuilder("/api/admin/settings")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(resp);
        return objectMapper.readValue(resp.body(), new TypeReference<>() {});
    }

    public AdminStatsResponse adminGetStats() throws IOException, InterruptedException {
        HttpRequest req = newRequestBuilder("/api/admin/stats").GET().build();
        return execute(req, AdminStatsResponse.class);
    }

    public AdminPageResponse<AdminAuditLogItem> adminSearchLogs(Long userId, String username, String action,
                                                                String from, String to, int page, int size)
            throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/admin/logs?page=").append(page)
                .append("&size=").append(size);
        if (userId != null) path.append("&userId=").append(userId);
        if (username != null && !username.isBlank()) path.append("&username=").append(urlEnc(username.trim()));
        if (action != null && !action.isBlank()) path.append("&action=").append(urlEnc(action.trim()));
        if (from != null && !from.isBlank()) path.append("&from=").append(urlEnc(from.trim()));
        if (to != null && !to.isBlank()) path.append("&to=").append(urlEnc(to.trim()));

        HttpRequest req = newRequestBuilder(path.toString()).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(resp);
        return objectMapper.readValue(resp.body(), new TypeReference<>() {});
    }

    public long adminExportLogsCsvToFile(Long userId, String username, String action,
                                         String from, String to, File targetFile)
            throws IOException, InterruptedException {
        StringBuilder path = new StringBuilder("/api/admin/logs/export?");
        if (userId != null) path.append("userId=").append(userId).append("&");
        if (username != null && !username.isBlank()) path.append("username=").append(urlEnc(username.trim())).append("&");
        if (action != null && !action.isBlank()) path.append("action=").append(urlEnc(action.trim())).append("&");
        if (from != null && !from.isBlank()) path.append("from=").append(urlEnc(from.trim())).append("&");
        if (to != null && !to.isBlank()) path.append("to=").append(urlEnc(to.trim())).append("&");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "text/csv")
                .GET();
        if (accessToken != null && !accessToken.isBlank()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        HttpResponse<byte[]> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            handleErrorBody(resp.statusCode(), new String(resp.body(), StandardCharsets.UTF_8), resp.headers());
        }
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        Files.write(targetFile.toPath(), resp.body());
        return resp.body().length;
    }

    private String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private void handleErrorBody(int status, String body, java.net.http.HttpHeaders headers) {
        long retryAfter = 0;
        if (status == 429 && headers != null) {
            retryAfter = headers.firstValue("Retry-After").map(v -> {
                try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return 60L; }
            }).orElse(60L);
        }
        String code = "HTTP_" + status;
        String message = "Lỗi máy chủ (HTTP " + status + ")";
        try {
            if (body != null && !body.isBlank()) {
                ApiError err = objectMapper.readValue(body, ApiError.class);
                if (err.getEffectiveCode() != null && !err.getEffectiveCode().isBlank()) code = err.getEffectiveCode();
                if (err.message() != null && !err.message().isBlank()) message = err.message();
            }
        } catch (Exception ignored) {}
        throw new ApiException(status, code, message, (int) retryAfter);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private HttpRequest.Builder newRequestBuilder(String path) {
        return newRequestBuilder(path, Duration.ofSeconds(15));
    }

    private HttpRequest.Builder newMailRequestBuilder(String path) {
        // Các tác vụ IMAP/SMTP qua mạng WAN sang Gmail cần thời gian chờ rộng hơn (45s) để không ngắt sớm trước Server (35s)
        return newRequestBuilder(path, Duration.ofSeconds(45));
    }

    private HttpRequest.Builder newRequestBuilder(String path, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Accept", "application/json");

        if (accessToken != null && !accessToken.isBlank()) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return builder;
    }

    private <T> T execute(HttpRequest request, Class<T> clazz) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        handleErrorIfNeeded(response);

        if (clazz == Void.class || response.body() == null || response.body().isBlank()) {
            return null;
        }
        return objectMapper.readValue(response.body(), clazz);
    }

    private void handleErrorIfNeeded(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }

        String code = "";
        String message = "Yêu cầu thất bại (HTTP " + status + ")";
        int retryAfter = 0;

        if (status == 429) {
            retryAfter = response.headers().firstValue("Retry-After")
                    .map(s -> {
                        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 60; }
                    })
                    .orElse(60);
        }

        String body = response.body();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(body);

                // YÊU CẦU: Đọc cả hai trường "code" và "errorCode", ưu tiên trường nào có giá trị
                if (root.hasNonNull("code")) {
                    code = root.get("code").asText();
                } else if (root.hasNonNull("errorCode")) {
                    code = root.get("errorCode").asText();
                }

                if (root.hasNonNull("message")) {
                    message = root.get("message").asText();
                } else if (root.hasNonNull("error")) {
                    message = root.get("error").asText();
                }
            } catch (Exception ignored) {
                message = body;
            }
        }

        throw new ApiException(status, code, message, (int) retryAfter);
    }
}
