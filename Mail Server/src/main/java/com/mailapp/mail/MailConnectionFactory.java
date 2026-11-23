package com.mailapp.mail;

import com.mailapp.mail.model.ConnectionTestResult;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.GmailAccount;

/**
 * Factory điều phối kết nối Gmail dùng chung cho toàn bộ dự án.
 * Cung cấp kết nối an toàn, đã xác thực sẵn cho cả IMAP và SMTP.
 * Mọi module khác (M04, M08, M10, M16) chỉ cần gọi factory này, không tự tạo Session.
 *
 * @author Lê Thanh Cường (Module M07)
 */
public interface MailConnectionFactory {

    /**
     * Mở kết nối IMAP qua MailCredentials.
     * Trả về ImapConnection (implements AutoCloseable) bọc Store đã connect sẵn.
     */
    ImapConnection openImap(MailCredentials credentials);

    /**
     * Mở kết nối IMAP qua thực thể GmailAccount (tự động giải mã secret hoặc lấy token OAuth2).
     */
    ImapConnection openImap(GmailAccount account);

    /**
     * Mở kết nối SMTP qua MailCredentials.
     * Trả về SmtpConnection (implements AutoCloseable) bọc Transport và Session đã connect sẵn.
     */
    SmtpConnection openSmtp(MailCredentials credentials);

    /**
     * Mở kết nối SMTP qua thực thể GmailAccount.
     */
    SmtpConnection openSmtp(GmailAccount account);

    /**
     * Kiểm tra thử kết nối IMAP bằng MailCredentials (dùng cho API liên kết EP-G2).
     * Tuyệt đối KHÔNG ném ngoại lệ cho các lỗi dự kiến (sai mật khẩu, lỗi mạng, IMAP tắt),
     * thay vào đó trả về ConnectionTestResult với mã trạng thái tương ứng.
     */
    ConnectionTestResult testImap(MailCredentials credentials);

    /**
     * Kiểm tra thử kết nối IMAP bằng email và App Password thô (tiện lợi cho M04 khi người dùng nhập form).
     */
    ConnectionTestResult testImap(String email, String rawAppPassword);

    /**
     * Kiểm tra thử kết nối IMAP từ thực thể GmailAccount.
     */
    ConnectionTestResult testImap(GmailAccount account);
}
