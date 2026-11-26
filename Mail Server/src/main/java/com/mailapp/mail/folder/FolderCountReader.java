package com.mailapp.mail.folder;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.protocol.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
     * @return số thư tổng / chưa đọc; không biến lỗi kết nối thành số 0 giả.
     */
    public static FolderCounts read(Folder folder) throws MessagingException {
        if (folder instanceof IMAPFolder imapFolder) {
            try {
                Status status = (Status) imapFolder.doCommand(protocol ->
                        protocol.status(imapFolder.getFullName(), new String[]{"MESSAGES", "UNSEEN"}));
                if (status != null && status.total >= 0 && status.unseen >= 0) {
                    return new FolderCounts(status.total, (int) status.unseen);
                }
            } catch (Exception e) {
                log.debug("STATUS gộp thất bại, fallback sang API chuẩn: {}",
                        e.getClass().getSimpleName());
            }
        }

        int total = folder.getMessageCount();
        int unread = folder.getUnreadMessageCount();
        if (total >= 0 && unread >= 0) return new FolderCounts(total, unread);
        boolean openedHere = !folder.isOpen();
        if (openedHere) folder.open(Folder.READ_ONLY);
        try {
            total = folder.getMessageCount();
            unread = folder.getUnreadMessageCount();
            if (total < 0 || unread < 0) {
                throw new MessagingException("IMAP folder counts unavailable");
            }
            return new FolderCounts(total, unread);
        } finally {
            if (openedHere && folder.isOpen()) folder.close(false);
        }
    }

    /**
     * Đọc số thư cho nhiều folder, chia thành các lô chạy song song.
     *
     * <p>Mỗi lệnh STATUS trên Gmail mất ~1 giây để máy chủ tính lại số thư, dù RTT chỉ vài chục ms.
     * Đọc 8 folder tuần tự vì thế tốn ~8 giây; chạy song song theo lô rút ngắn còn ~1-2 giây.</p>
     *
     * <p><b>Quan trọng:</b> mỗi lô nhận một connection riêng và một luồng riêng, vì {@code Store}
     * của Jakarta Mail không thread-safe. Bên gọi phải bảo đảm các lô dùng connection độc lập.
     * Lô lỗi trả 0 cho các folder trong lô đó thay vì làm hỏng cả danh sách.</p>
     *
     * @param batchSize số folder mỗi lô (tối thiểu 1)
     * @param names     danh sách folder cần đọc, giữ nguyên thứ tự khi trả về
     * @param reader    hàm đọc một lô trên luồng riêng của lô đó
     */
    public static <T> Map<T, FolderCounts> readAll(int batchSize,
                                                   List<T> names,
                                                   BatchReader<T> reader) {
        Map<T, FolderCounts> result = new LinkedHashMap<>();
        if (names == null || names.isEmpty()) {
            return result;
        }
        int size = Math.max(1, batchSize);
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < names.size(); i += size) {
            batches.add(names.subList(i, Math.min(i + size, names.size())));
        }

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Map<T, FolderCounts>>> futures = new ArrayList<>();
            for (List<T> batch : batches) {
                futures.add(pool.submit(() -> reader.read(batch)));
            }
            for (int i = 0; i < futures.size(); i++) {
                Map<T, FolderCounts> batchResult;
                try {
                    batchResult = futures.get(i).get();
                } catch (Exception e) {
                    log.debug("Đọc số thư theo lô thất bại, dùng 0: {}", e.getClass().getSimpleName());
                    batchResult = Map.of();
                }
                for (T name : batches.get(i)) {
                    result.put(name, batchResult.getOrDefault(name, new FolderCounts(0, 0)));
                }
            }
        }
        // Giữ đúng thứ tự đầu vào bất kể lô nào xong trước
        Map<T, FolderCounts> ordered = new LinkedHashMap<>();
        for (T name : names) {
            ordered.put(name, result.getOrDefault(name, new FolderCounts(0, 0)));
        }
        return ordered;
    }

    @FunctionalInterface
    public interface BatchReader<T> {
        Map<T, FolderCounts> read(List<T> batch) throws Exception;
    }
}
