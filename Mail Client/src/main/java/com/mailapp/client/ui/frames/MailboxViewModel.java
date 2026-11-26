package com.mailapp.client.ui.frames;

import com.mailapp.client.net.ApiClient.OAuthLinkPollDto;
import com.mailapp.client.model.MailClientDtos.MailListResponse;
import com.mailapp.client.model.MailClientDtos.MailSummaryDto;
import com.mailapp.client.model.MailClientDtos.MailDetailResponse;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.util.FeatureAdmission;

import java.time.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.function.BooleanSupplier;

/**
 * View-Model thuần (không phụ thuộc Swing) chứa toàn bộ logic trạng thái hộp thư của MainFrame:
 * - (a) Hợp nhất trang phân trang (loại trùng UID & phát hiện đổi UIDVALIDITY)
 * - (b) Điều phối thế hệ (generation counter) & hủy tác vụ nền cũ khi đổi thư mục/thư, đóng cửa sổ, đăng xuất
 * - (c) Vòng lặp poll OAuth2 có thể hủy tức thì (không bị kẹt ở Thread.sleep)
 * - (d) Phân giải ngày đa định dạng (Instant Z, OffsetDateTime +07:00, LocalDateTime) sang múi giờ máy
 */
public class MailboxViewModel {

    // === (a) Phân trang & UIDVALIDITY ===

    /** The criteria of a paginated result must be identical across all of its pages. */
    public record SearchCriteria(String query, boolean unreadOnly, boolean hasAttachment) {
        public SearchCriteria {
            query = query == null ? "" : query.trim();
        }
    }

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
        if (shouldReloadFromFirstPage(currentUidValidity, resp, append)) {
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

        List<MailSummaryDto> added = selectNewItems(seenUids, resp);
        merged.addAll(added);

        return new PageMergeResult(false, resp.uidValidity(), merged, added.size());
    }

    /**
     * Chọn các thư trong trang chưa có trong {@code seenUids} và bổ sung UID của chúng vào tập.
     * Dùng cho đường append tăng dần: chỉ thêm phần mới vào model, không dựng lại toàn bộ danh sách.
     */
    public static List<MailSummaryDto> selectNewItems(Set<Long> seenUids, MailListResponse resp) {
        List<MailSummaryDto> added = new ArrayList<>();
        if (resp == null || resp.items() == null) {
            return added;
        }
        for (MailSummaryDto item : resp.items()) {
            if (item != null && seenUids.add(item.uid())) {
                added.add(item);
            }
        }
        return added;
    }

    /** True khi đang tải thêm trang nhưng UIDVALIDITY của hộp thư đã thay đổi. */
    public static boolean shouldReloadFromFirstPage(long currentUidValidity,
                                                    MailListResponse resp,
                                                    boolean append) {
        return append
                && currentUidValidity != 0
                && resp != null
                && resp.uidValidity() != 0
                && resp.uidValidity() != currentUidValidity;
    }

    public static boolean validMutationSelection(List<MailSummaryDto> selection, long uidValidity) {
        return uidValidity > 0 && selection != null && !selection.isEmpty()
                && selection.stream().allMatch(item -> item != null && item.uid() > 0 && item.uidValidity() == uidValidity);
    }

    /** Detail toolbar actions refer to the displayed message, never a batch selection. */
    public static List<MailSummaryDto> detailMutationTarget(MailDetailResponse detail,
            List<MailSummaryDto> selection, long uidValidity) {
        if (detail == null || detail.uidValidity() != uidValidity
                || !validMutationSelection(selection, uidValidity)) return List.of();
        return selection.stream().filter(item -> item.uid() == detail.uid()).limit(1).toList();
    }

    @FunctionalInterface
    public interface ConfirmedDelete {
        void delete(MailSummaryDto item) throws Exception;
    }

    /** Only acknowledged deletions count; never retry or continue after a failed response. */
    public record DeleteBatchResult(int acknowledged, int requested, boolean stopped, Exception failure) {}

    public static DeleteBatchResult deleteConfirmedBatch(List<MailSummaryDto> selection,
            java.util.function.BooleanSupplier stillCurrent, ConfirmedDelete action) {
        int acknowledged = 0;
        for (MailSummaryDto item : selection) {
            if (Thread.currentThread().isInterrupted() || !stillCurrent.getAsBoolean()) {
                return new DeleteBatchResult(acknowledged, selection.size(), true, null);
            }
            try {
                action.delete(item);
                acknowledged++;
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                return new DeleteBatchResult(acknowledged, selection.size(), false, ex);
            }
        }
        return new DeleteBatchResult(acknowledged, selection.size(), false, null);
    }

    public static boolean passesCleanupAge(String date, Instant cutoff) {
        if (cutoff == null) return true;
        if (date == null || date.isBlank()) return false;
        try {
            return !Instant.parse(date).isAfter(cutoff);
        } catch (DateTimeException e) {
            return false;
        }
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

    /** Check before and after preflight; an off/on transition must not revive a link click. */
    public static void requireOAuthLinkAdmission(FeatureAdmission admission, FeatureAdmission.Ticket ticket,
                                                 BooleanSupplier stillCurrent) throws InterruptedException {
        if (!stillCurrent.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("OAuth link no longer current");
        }
        if (!admission.isCurrent(ticket)) {
            throw new ApiException(400, "FORM_INVALIDATED",
                    "Cấu hình liên kết đã thay đổi. Vui lòng thực hiện liên kết lại.");
        }
    }

    public static void preflightOAuthLink(FeatureAdmission admission, FeatureAdmission.Ticket ticket,
                                          BooleanSupplier stillCurrent, Callable<PublicConfigDto> fetchConfig)
            throws Exception {
        requireOAuthLinkAdmission(admission, ticket, stillCurrent);
        PublicConfigDto latest = fetchConfig.call();
        requireOAuthLinkAdmission(admission, ticket, stillCurrent);
        if (latest == null) {
            throw new ApiException(503, "CONFIG_UNAVAILABLE", "Chưa tải được cấu hình liên kết Gmail.");
        }
        if (!latest.oauth2LinkEnabled()) {
            throw new ApiException(403, "FEATURE_DISABLED", "Tính năng liên kết Google OAuth2 đã bị tắt.");
        }
    }

    @FunctionalInterface
    public interface LinkResultFetcher {
        OAuthLinkPollDto fetch() throws Exception;
    }

    public static OAuthLinkPollDto pollForOAuthLink(LinkResultFetcher fetcher,
                                                   int maxAttempts, long intervalMs,
                                                   AtomicBoolean cancelled, IntConsumer onTick) throws Exception {
        return pollForOAuthLink(fetcher, maxAttempts, intervalMs, cancelled, () -> true, onTick);
    }

    public static OAuthLinkPollDto pollForOAuthLink(LinkResultFetcher fetcher,
                                                   int maxAttempts, long intervalMs,
                                                   AtomicBoolean cancelled, BooleanSupplier stillCurrent,
                                                   IntConsumer onTick) throws Exception {
        for (int i = 1; i <= maxAttempts; i++) {
            if (cancelled.get() || !stillCurrent.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return new OAuthLinkPollDto("CANCELLED", null, null, null);
            }
            if (intervalMs > 0) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new OAuthLinkPollDto("CANCELLED", null, null, null);
                }
            }
            if (cancelled.get() || !stillCurrent.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return new OAuthLinkPollDto("CANCELLED", null, null, null);
            }
            if (onTick != null) {
                onTick.accept(i);
            }
            if (cancelled.get() || !stillCurrent.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return new OAuthLinkPollDto("CANCELLED", null, null, null);
            }
            OAuthLinkPollDto updated = fetcher.fetch();
            if (cancelled.get() || !stillCurrent.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                return new OAuthLinkPollDto("CANCELLED", null, null, null);
            }
            if (updated != null && !"PENDING".equalsIgnoreCase(updated.status())) {
                return updated;
            }
        }
        return cancelled.get() || !stillCurrent.getAsBoolean() || Thread.currentThread().isInterrupted()
                ? new OAuthLinkPollDto("CANCELLED", null, null, null)
                : new OAuthLinkPollDto("FAILED", "TIMEOUT", "Hết thời gian chờ xác thực Google. Vui lòng thử lại.", null);
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

