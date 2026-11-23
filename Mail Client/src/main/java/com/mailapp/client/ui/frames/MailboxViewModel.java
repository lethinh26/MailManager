package com.mailapp.client.ui.frames;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.model.MailClientDtos.MailListResponse;
import com.mailapp.client.model.MailClientDtos.MailSummaryDto;

import java.time.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

/**
 * View-Model thuần (không phụ thuộc Swing) chứa toàn bộ logic trạng thái hộp thư của MainFrame:
 * - (a) Hợp nhất trang phân trang (loại trùng UID & phát hiện đổi UIDVALIDITY)
 * - (b) Điều phối thế hệ (generation counter) & hủy tác vụ nền cũ khi đổi thư mục/thư, đóng cửa sổ, đăng xuất
 * - (c) Vòng lặp poll OAuth2 có thể hủy tức thì (không bị kẹt ở Thread.sleep)
 * - (d) Phân giải ngày đa định dạng (Instant Z, OffsetDateTime +07:00, LocalDateTime) sang múi giờ máy
 */
public class MailboxViewModel {

    // === (a) Phân trang & UIDVALIDITY ===

    public record PageMergeResult(
            boolean uidValidityChanged,
            long uidValidity,
            List<MailSummaryDto> items,
            int addedCount
    ) {}

    public static PageMergeResult mergeMailPage(List<MailSummaryDto> existingItems,
                                                long currentUidValidity,
                                                MailListResponse resp,
                                                boolean append) {
        if (resp == null) {
            return new PageMergeResult(false, currentUidValidity,
                    existingItems != null ? List.copyOf(existingItems) : List.of(), 0);
        }
        // Nếu đang tải thêm trang mà máy chủ đổi UIDVALIDITY -> buộc tải lại từ trang 0
        if (append && currentUidValidity != 0 && resp.uidValidity() != 0 && resp.uidValidity() != currentUidValidity) {
            return new PageMergeResult(true, resp.uidValidity(), List.of(), 0);
        }

        List<MailSummaryDto> merged = new ArrayList<>();
        Set<Long> seenUids = new HashSet<>();
        if (append && existingItems != null) {
            for (MailSummaryDto item : existingItems) {
                if (item != null && seenUids.add(item.uid())) {
                    merged.add(item);
                }
            }
        }

        int added = 0;
        if (resp.items() != null) {
            for (MailSummaryDto item : resp.items()) {
                if (item != null && seenUids.add(item.uid())) {
                    merged.add(item);
                    added++;
                }
            }
        }

        return new PageMergeResult(false, resp.uidValidity(), merged, added);
    }

    // === (b) Bộ đếm thế hệ & hủy worker cũ ===

    public static class WorkerCoordinator {
        private final AtomicLong foldersGen = new AtomicLong(0);
        private final AtomicLong mailsGen = new AtomicLong(0);
        private final AtomicLong detailGen = new AtomicLong(0);

        private volatile Future<?> activeFoldersTask;
        private volatile Future<?> activeMailsTask;
        private volatile Future<?> activeDetailTask;

        public synchronized long beginFoldersLoad(Future<?> previousToReplace) {
            long gen = foldersGen.incrementAndGet();
            cancelQuietly(activeFoldersTask);
            activeFoldersTask = previousToReplace;
            return gen;
        }

        public synchronized void attachFoldersTask(Future<?> task) {
            activeFoldersTask = task;
        }

        public boolean isFoldersCurrent(long gen) {
            return gen == foldersGen.get();
        }

        public synchronized long beginMailsLoad() {
            long gen = mailsGen.incrementAndGet();
            cancelQuietly(activeMailsTask);
            return gen;
        }

        public synchronized void attachMailsTask(Future<?> task) {
            activeMailsTask = task;
        }

        public boolean isMailsCurrent(long gen) {
            return gen == mailsGen.get();
        }

        public synchronized long beginDetailLoad() {
            long gen = detailGen.incrementAndGet();
            cancelQuietly(activeDetailTask);
            return gen;
        }

        public synchronized void attachDetailTask(Future<?> task) {
            activeDetailTask = task;
        }

        public boolean isDetailCurrent(long gen) {
            return gen == detailGen.get();
        }

        /**
         * Gọi khi đóng cửa sổ hoặc đăng xuất: vô hiệu hóa mọi callback đang chạy và hủy toàn bộ worker nền.
         */
        public synchronized void cancelAll() {
            foldersGen.incrementAndGet();
            mailsGen.incrementAndGet();
            detailGen.incrementAndGet();
            cancelQuietly(activeFoldersTask);
            cancelQuietly(activeMailsTask);
            cancelQuietly(activeDetailTask);
            activeFoldersTask = null;
            activeMailsTask = null;
            activeDetailTask = null;
        }

        private static void cancelQuietly(Future<?> task) {
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
        }
    }

    // === (c) Poll OAuth2 hủy tức thì ===

    @FunctionalInterface
    public interface AccountFetcher {
        List<GmailAccountDto> fetch() throws Exception;
    }

    public enum OAuthPollStatus {
        LINKED,
        CANCELLED,
        TIMEOUT
    }

    public static OAuthPollStatus pollForOAuthAccount(AccountFetcher fetcher,
                                                      int initialCount,
                                                      Set<Long> initialActiveOAuthIds,
                                                      int maxAttempts,
                                                      long intervalMs,
                                                      AtomicBoolean cancelled,
                                                      IntConsumer onTick) throws Exception {
        Set<Long> baseline = initialActiveOAuthIds != null ? initialActiveOAuthIds : Set.of();
        for (int i = 1; i <= maxAttempts; i++) {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                return OAuthPollStatus.CANCELLED;
            }
            if (intervalMs > 0) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return OAuthPollStatus.CANCELLED;
                }
            }
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                return OAuthPollStatus.CANCELLED;
            }
            if (onTick != null) {
                onTick.accept(i);
            }
            List<GmailAccountDto> updated = fetcher.fetch();
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                return OAuthPollStatus.CANCELLED;
            }
            if (updated != null && (updated.size() > initialCount || hasNewActiveOAuth(updated, baseline))) {
                return OAuthPollStatus.LINKED;
            }
        }
        return cancelled.get() ? OAuthPollStatus.CANCELLED : OAuthPollStatus.TIMEOUT;
    }

    private static boolean hasNewActiveOAuth(List<GmailAccountDto> list, Set<Long> baselineActiveIds) {
        for (GmailAccountDto acc : list) {
            if (acc != null
                    && "OAUTH2".equalsIgnoreCase(acc.authType())
                    && "ACTIVE".equalsIgnoreCase(acc.status())
                    && !baselineActiveIds.contains(acc.id())) {
                return true;
            }
        }
        return false;
    }

    // === (d) Phân giải ngày đa định dạng sang múi giờ máy ===

    public static ZonedDateTime parseDateToZone(String isoDate, ZoneId targetZone) {
        if (isoDate == null || isoDate.isBlank()) {
            return null;
        }
        ZoneId zone = targetZone != null ? targetZone : ZoneId.systemDefault();
        String trimmed = isoDate.trim();
        try {
            return Instant.parse(trimmed).atZone(zone);
        } catch (Exception ignored) {}
        try {
            return OffsetDateTime.parse(trimmed).atZoneSameInstant(zone);
        } catch (Exception ignored) {}
        try {
            return LocalDateTime.parse(trimmed).atZone(zone);
        } catch (Exception ignored) {}
        return null;
    }
}

