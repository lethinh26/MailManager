package com.mailapp.client.util;

import com.mailapp.client.net.ApiException;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.Set;

/**
 * Tiện ích an toàn phía Client:
 * 1. Nhận diện đuôi tệp đính kèm nguy hiểm có khả năng thực thi mã trước khi mở bằng Desktop.open().
 * 2. Chuyển đổi ngoại lệ kỹ thuật sang thông báo tiếng Việt thân thiện kèm hướng dẫn khắc phục.
 */
public final class ClientSafetyUtil {

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "msp", "vbs", "vbe",
            "js", "jse", "jar", "ps1", "psm1", "psd1", "lnk", "scr",
            "hta", "pif", "reg", "sh", "dll", "cpl", "wsf", "wsh",
            "apk", "app", "vb", "vxd", "inf", "msc"
    );

    private ClientSafetyUtil() {}

    /**
     * Kiểm tra tên tệp có đuôi nguy hiểm (thực thi mã / script / shortcut) hay không.
     */
    public static boolean isDangerousExecutableExtension(String filename) {
        if (filename == null || filename.isBlank()) return false;
        String cleaned = filename.trim().toLowerCase(Locale.ROOT);
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot < 0 || lastDot == cleaned.length() - 1) {
            return false;
        }
        String ext = cleaned.substring(lastDot + 1);
        return DANGEROUS_EXTENSIONS.contains(ext);
    }

    /**
     * Chuyển đổi mọi ngoại lệ thành thông báo tiếng Việt rõ ràng kèm hướng dẫn xử lý,
     * tránh hiển thị thông báo kỹ thuật thô cho người dùng cuối.
     */
    public static String toFriendlyVietnameseError(Throwable ex) {
        if (ex == null) return "Đã xảy ra lỗi không xác định. Vui lòng thử lại.";
        Throwable root = ex;
        while (root.getCause() != null && !(root instanceof ApiException)) {
            root = root.getCause();
        }

        if (root instanceof ApiException apiEx) {
            if (apiEx.getMessage() != null && !apiEx.getMessage().isBlank()) {
                return apiEx.getMessage();
            }
            return switch (apiEx.getStatusCode()) {
                case 401 -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
                case 403 -> "Bạn không có quyền thực hiện thao tác này.";
                case 429 -> "Bạn thao tác quá nhanh. Vui lòng chờ " + apiEx.getRetryAfterSeconds() + " giây rồi thử lại.";
                case 502, 503, 504 -> "Máy chủ tạm thời quá tải hoặc đang khởi động lại. Vui lòng thử lại sau vài giây.";
                default -> "Máy chủ không thể xử lý yêu cầu. Vui lòng thử lại sau.";
            };
        }

        if (root instanceof SSLHandshakeException) {
            return "Không thể xác thực chứng chỉ bảo mật TLS của máy chủ. Hãy kiểm tra tệp chứng chỉ .cer trong mục Cài đặt.";
        }
        if (root instanceof UnknownHostException) {
            return "Không tìm thấy địa chỉ máy chủ (lỗi DNS). Hãy kiểm tra kết nối mạng và URL máy chủ trong Cài đặt.";
        }
        if (root instanceof ConnectException) {
            return "Không thể kết nối tới máy chủ (máy chủ có thể đang khởi động lại hoặc đã tắt). Hãy chờ vài giây rồi bấm Làm mới.";
        }
        if (root instanceof HttpTimeoutException || root instanceof HttpConnectTimeoutException || root instanceof SocketTimeoutException) {
            return "Kết nối tới máy chủ bị quá thời gian chờ. Hãy kiểm tra mạng và thử lại.";
        }
        if (root instanceof IOException ioEx) {
            String msg = ioEx.getMessage() != null ? ioEx.getMessage().toLowerCase(Locale.ROOT) : "";
            if (msg.contains("space") || msg.contains("disk full") || msg.contains("enospc")) {
                return "Ổ đĩa lưu trữ đã đầy. Hãy giải phóng dung lượng trống hoặc đổi thư mục tải về trong Cài đặt.";
            }
            if (msg.contains("access is denied") || msg.contains("permission denied")) {
                return "Không có quyền ghi vào thư mục tải về. Hãy chọn thư mục khác trong Cài đặt.";
            }
            return "Lỗi kết nối mạng hoặc đọc/ghi tệp. Hãy kiểm tra kết nối tới máy chủ và thử lại.";
        }
        return "Thao tác tạm thời chưa thực hiện được. Vui lòng thử lại sau giây lát.";
    }
}

