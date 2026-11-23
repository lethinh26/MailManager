package com.mailapp.mail.folder;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.protocol.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Đọc số thư tổng và số thư chưa đọc của một folder IMAP.
 *
 * <p>Jakarta Mail chỉ có {@code getMessageCount()} và {@code getUnreadMessageCount()},
 * mỗi method phát một lệnh STATUS riêng. Với Gmail (độ trễ cao) và một hộp thư có
 * nhiều label, điều đó nhân đôi số round-trip. Với IMAPFolder của Angus Mail ta dùng
 * {@code doCommand} để gửi một lệnh STATUS duy nhất với cả hai item.</p>
 */
public final class FolderCountReader {

    private static final Logger log = LoggerFactory.getLogger(FolderCountReader.class);

    private FolderCountReader() {}

    public record FolderCounts(int total, int unread) {}

    /**
     * @return số thư tổng / chưa đọc, trả 0 nếu máy chủ từ chối STATUS
     */
    public static FolderCounts read(Folder folder) {
        if (folder instanceof IMAPFolder imapFolder) {
            try {
                Status status = (Status) imapFolder.doCommand(protocol ->
                        protocol.status(imapFolder.getFullName(), new String[]{"MESSAGES", "UNSEEN"}));
                if (status != null) {
                    return new FolderCounts(Math.max(0, status.total), Math.max(0, (int) status.unseen));
                }
            } catch (Exception e) {
                log.debug("STATUS gộp thất bại cho {}, fallback sang API chuẩn: {}",
                        safeName(folder), e.getMessage());
            }
        }

        try {
            return new FolderCounts(
                    Math.max(0, folder.getMessageCount()),
                    Math.max(0, folder.getUnreadMessageCount()));
        } catch (MessagingException e) {
            log.debug("Không đọc được số thư của {}: {}", safeName(folder), e.getMessage());
            return new FolderCounts(0, 0);
        }
    }

    private static String safeName(Folder folder) {
        try {
            return folder.getFullName();
        } catch (Exception e) {
            return "<unknown>";
        }
    }
}
