package com.mailapp.mail.folder;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Phân giải thư mục Gmail theo chuẩn RFC 6154 (Special-Use Attributes).
 * Hỗ trợ nhận diện các thư mục đặc biệt (\Sent, \Trash, \Drafts, \Junk, \All, \Flagged)
 * bất kể ngôn ngữ giao diện của tài khoản Gmail (tiếng Anh, tiếng Việt, v.v.).
 * Cung cấp cơ chế fallback theo danh sách tên phổ biến nếu server không trả thuộc tính.
 *
 * @author Lê Thanh Cường (Module M07)
 */
@Component
public class GmailFolderResolver {

    private static final Logger log = LoggerFactory.getLogger(GmailFolderResolver.class);

    public enum SpecialFolder {
        INBOX("\\Inbox"),
        SENT("\\Sent"),
        DRAFTS("\\Drafts"),
        TRASH("\\Trash"),
        SPAM("\\Junk"),
        STARRED("\\Flagged"),
        ALL_MAIL("\\All");

        private final String rfcAttribute;

        SpecialFolder(String rfcAttribute) {
            this.rfcAttribute = rfcAttribute;
        }

        public String getRfcAttribute() {
            return rfcAttribute;
        }
    }

    private static final Map<SpecialFolder, List<String>> FALLBACK_NAMES = new EnumMap<>(SpecialFolder.class);

    static {
        FALLBACK_NAMES.put(SpecialFolder.INBOX, List.of("INBOX", "Inbox", "Hộp thư đến"));
        FALLBACK_NAMES.put(SpecialFolder.SENT, List.of("[Gmail]/Sent Mail", "[Gmail]/Thư đã gửi", "Sent", "Sent Mail", "Sent Messages"));
        FALLBACK_NAMES.put(SpecialFolder.DRAFTS, List.of("[Gmail]/Drafts", "[Gmail]/Thư nháp", "Drafts"));
        FALLBACK_NAMES.put(SpecialFolder.TRASH, List.of("[Gmail]/Trash", "[Gmail]/Thùng rác", "[Gmail]/Bin", "Trash", "Deleted Messages"));
        FALLBACK_NAMES.put(SpecialFolder.SPAM, List.of("[Gmail]/Spam", "[Gmail]/Thư rác", "Spam", "Junk"));
        FALLBACK_NAMES.put(SpecialFolder.STARRED, List.of("[Gmail]/Starred", "[Gmail]/Có gắn dấu sao", "Starred"));
        FALLBACK_NAMES.put(SpecialFolder.ALL_MAIL, List.of("[Gmail]/All Mail", "[Gmail]/Tất cả thư", "All Mail", "Archive"));
    }

    /**
     * Phân giải và tìm Folder tương ứng trên Gmail Store.
     *
     * @param store Store IMAP đã kết nối
     * @param target thư mục đặc biệt cần tìm
     * @return Folder tương ứng
     * @throws MessagingException khi không tìm thấy hoặc xảy ra lỗi kết nối
     */
    public Folder resolve(Store store, SpecialFolder target) throws MessagingException {
        Objects.requireNonNull(store, "Store không được null");
        Objects.requireNonNull(target, "SpecialFolder không được null");

        // 1. Inbox luôn chuẩn theo giao thức IMAP
        if (target == SpecialFolder.INBOX) {
            return store.getFolder("INBOX");
        }

        // 2. Thử phân giải theo thuộc tính RFC 6154
        try {
            Folder defaultFolder = store.getDefaultFolder();
            Folder[] allFolders = defaultFolder.list("*");
            for (Folder folder : allFolders) {
                if (hasAttribute(folder, target.getRfcAttribute())) {
                    log.debug("Phân giải thư mục {} theo RFC 6154 thành công: {}", target, folder.getFullName());
                    return folder;
                }
            }
        } catch (Exception e) {
            log.debug("Không thể duyệt thuộc tính RFC 6154, chuyển sang cơ chế fallback: {}", e.getMessage());
        }

        // 3. Fallback: Thử tìm theo danh sách tên phổ biến (đa ngôn ngữ)
        List<String> candidates = FALLBACK_NAMES.getOrDefault(target, Collections.emptyList());
        for (String candidate : candidates) {
            try {
                Folder folder = store.getFolder(candidate);
                if (folder != null && folder.exists()) {
                    log.debug("Phân giải thư mục {} theo fallback name thành công: {}", target, candidate);
                    return folder;
                }
            } catch (Exception ignored) {}
        }

        throw new MessagingException("Không thể phân giải thư mục Gmail: " + target + ". Đã thử RFC 6154 và các tên thay thế.");
    }

    private boolean hasAttribute(Folder folder, String expectedAttribute) {
        try {
            java.lang.reflect.Method method = folder.getClass().getMethod("getAttributes");
            String[] attributes = (String[]) method.invoke(folder);
            if (attributes != null) {
                for (String attr : attributes) {
                    if (attr != null && attr.equalsIgnoreCase(expectedAttribute)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
