package com.mailapp.mail;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Wrapper bao bọc jakarta.mail.Transport và Session cho kết nối SMTP.
 * Cung cấp tiện ích tạo MimeMessage và gửi thư cho Module M10.
 * Triển khai AutoCloseable để sử dụng trong try-with-resources.
 * Phương thức close() nuốt sạch ngoại lệ khi đóng.
 *
 * @author Lê Thanh Cường (Module M07)
 */
public class SmtpConnection implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SmtpConnection.class);

    private final Transport transport;
    private final Session session;
    private final String maskedEmail;

    public SmtpConnection(Transport transport, Session session, String maskedEmail) {
        this.transport = Objects.requireNonNull(transport, "Transport không được null");
        this.session = Objects.requireNonNull(session, "Session không được null");
        this.maskedEmail = maskedEmail != null ? maskedEmail : "***";
    }

    public Transport getTransport() {
        return transport;
    }

    public Session getSession() {
        return session;
    }

    /**
     * Khởi tạo đối tượng MimeMessage mới gắn với Session của kết nối này.
     */
    public MimeMessage createMimeMessage() {
        return new MimeMessage(session);
    }

    /**
     * Gửi MimeMessage qua kết nối SMTP đã mở.
     *
     * @param message thư cần gửi
     * @throws MessagingException khi xảy ra lỗi gửi thư qua SMTP
     */
    public void sendMessage(MimeMessage message) throws MessagingException {
        Objects.requireNonNull(message, "Message không được null");
        Address[] recipients = message.getAllRecipients();
        if (recipients == null || recipients.length == 0) {
            throw new MessagingException("Thư không có người nhận hợp lệ");
        }
        transport.sendMessage(message, recipients);
        log.info("Đã gửi thư SMTP thành công từ tài khoản: {}", maskedEmail);
    }

    /**
     * Kiểm tra kết nối SMTP còn hoạt động hay không.
     */
    public boolean isConnected() {
        return transport != null && transport.isConnected();
    }

    /**
     * Đóng kết nối SMTP Transport, tự động bắt và log lỗi nếu có, không ném exception.
     */
    @Override
    public void close() {
        if (transport != null && transport.isConnected()) {
            try {
                transport.close();
                log.debug("Đã đóng kết nối SMTP cho: {}", maskedEmail);
            } catch (Exception e) {
                log.debug("Nuốt lỗi khi đóng SMTP Transport cho {}: {}", maskedEmail, e.getMessage());
            }
        }
    }
}

