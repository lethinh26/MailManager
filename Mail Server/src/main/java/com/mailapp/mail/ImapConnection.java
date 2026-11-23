package com.mailapp.mail;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Wrapper bao bọc jakarta.mail.Store cho kết nối IMAP.
 * Hỗ trợ chế độ Connection Pool (trả Store khỏe mạnh về pool thay vì đóng TCP/TLS mỗi lần)
 * và chế độ đóng trực tiếp. Luôn nuốt lỗi khi close() để không ảnh hưởng luồng chính.
 *
 * @author Lê Thanh Cường (Module M07)
 */
public class ImapConnection implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ImapConnection.class);

    private final Store store;
    private final String maskedEmail;
    private final Consumer<ImapConnection> releaseCallback;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile boolean broken = false;

    public ImapConnection(Store store, String maskedEmail) {
        this(store, maskedEmail, null);
    }

    public ImapConnection(Store store, String maskedEmail, Consumer<ImapConnection> releaseCallback) {
        this.store = Objects.requireNonNull(store, "Store không được null");
        this.maskedEmail = maskedEmail != null ? maskedEmail : "***";
        this.releaseCallback = releaseCallback;
    }

    public Store getStore() {
        return store;
    }

    public Folder folder(String folderName) throws MessagingException {
        return store.getFolder(folderName);
    }

    public boolean isConnected() {
        return !broken && store != null && store.isConnected();
    }

    /**
     * Đánh dấu kết nối đã gặp lỗi mạng để không trả lại vào pool.
     */
    public void markBroken() {
        this.broken = true;
    }

    public boolean isBroken() {
        return broken;
    }

    /**
     * Đóng vật lý Store IMAP (dùng nội bộ bởi pool khi hết hạn hoặc lỗi).
     */
    void forceCloseStore() {
        if (store != null) {
            try {
                if (store.isConnected()) {
                    store.close();
                }
                log.debug("Đã đóng vật lý kết nối IMAP cho: {}", maskedEmail);
            } catch (Exception e) {
                log.debug("Nuốt lỗi khi đóng IMAP Store cho {}: {}", maskedEmail, e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (releaseCallback != null) {
            try {
                releaseCallback.accept(this);
                return;
            } catch (Exception e) {
                log.debug("Lỗi trả IMAP Store về pool cho {}: {}", maskedEmail, e.getMessage());
            }
        }
        forceCloseStore();
    }
}
