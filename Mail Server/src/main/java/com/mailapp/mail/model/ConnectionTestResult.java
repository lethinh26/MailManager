package com.mailapp.mail.model;

/**
 * Kết quả kiểm tra kết nối IMAP (dùng cho API EP-G2 khi liên kết Gmail).
 * Tuyệt đối không ném exception cho các lỗi dự kiến (sai pass, mạng lỗi, IMAP tắt, chưa bật 2FA).
 *
 * @author Lê Thanh Cường (Module M07)
 */
public record ConnectionTestResult(
    Status status,
    String message,
    long durationMs
) {
    public enum Status {
        OK,
        AUTH_FAILED,
        NETWORK_ERROR,
        IMAP_DISABLED,
        TWO_STEP_VERIFICATION_REQUIRED
    }

    public static ConnectionTestResult ok(long durationMs) {
        return new ConnectionTestResult(Status.OK, "Kết nối và xác thực IMAP thành công.", durationMs);
    }

    public static ConnectionTestResult authFailed(String message) {
        return new ConnectionTestResult(
            Status.AUTH_FAILED,
            message != null && !message.isBlank() ? message : "Xác thực thất bại. Vui lòng kiểm tra lại địa chỉ email hoặc App Password 16 ký tự.",
            0
        );
    }

    public static ConnectionTestResult networkError(String message) {
        return new ConnectionTestResult(
            Status.NETWORK_ERROR,
            message != null && !message.isBlank() ? message : "Không thể kết nối đến máy chủ Google Mail (hết thời gian chờ hoặc lỗi mạng).",
            0
        );
    }

    public static ConnectionTestResult imapDisabled(String message) {
        return new ConnectionTestResult(
            Status.IMAP_DISABLED,
            message != null && !message.isBlank()
                    ? message
                    : "Tài khoản Gmail chưa bật IMAP. Vui lòng mở Gmail Web -> Cài đặt -> Xem tất cả cài đặt -> Chuyển tiếp và POP/IMAP -> chọn Bật IMAP và Lưu thay đổi.",
            0
        );
    }

    public static ConnectionTestResult twoStepRequired(String message) {
        return new ConnectionTestResult(
            Status.TWO_STEP_VERIFICATION_REQUIRED,
            message != null && !message.isBlank()
                    ? message
                    : "Tài khoản Google yêu cầu bật Xác minh 2 bước (2-Step Verification) và tạo Mật khẩu ứng dụng (App Password 16 ký tự) tại myaccount.google.com/apppasswords.",
            0
        );
    }

    public boolean isSuccessful() {
        return status == Status.OK;
    }
}
