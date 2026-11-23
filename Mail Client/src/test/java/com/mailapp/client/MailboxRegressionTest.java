package com.mailapp.client;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.ui.frames.MailboxViewModel;
import com.mailapp.client.ui.frames.MailboxViewModel.OAuthPollStatus;
import com.mailapp.client.ui.frames.MailboxViewModel.PageMergeResult;
import com.mailapp.client.ui.frames.MailboxViewModel.WorkerCoordinator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kiểm thử hồi quy thật cho 4 lỗi Client (MailboxRegressionTest)")
class MailboxRegressionTest {

    private static MailSummaryDto sampleMail(long uid, long uidValidity, String subject) {
        return new MailSummaryDto(
                uid,
                uidValidity,
                new MailAddressDto("Sender", "sender@example.com"),
                List.of(new MailAddressDto("Me", "me@example.com")),
                subject,
                "snippet",
                "2026-11-22T09:15:00Z",
                new MailFlagsDto(false, false, false, false),
                1024L,
                false,
                0
        );
    }

    @Test
    @DisplayName("(a) Hồi quy phân trang: Loại bỏ trùng UID khi có thư mới đến và phát hiện đổi UIDVALIDITY")
    void testPaginationDeduplicationAndUidValidityChange() {
        List<MailSummaryDto> page0 = List.of(
                sampleMail(103L, 5001L, "Mail 103"),
                sampleMail(102L, 5001L, "Mail 102"),
                sampleMail(101L, 5001L, "Mail 101")
        );

        // Trang 1 bị trùng UID 101 (do có thư mới 104 đến làm dịch offset)
        MailListResponse page1Resp = new MailListResponse(
                List.of(
                        sampleMail(101L, 5001L, "Mail 101 duplicate"),
                        sampleMail(100L, 5001L, "Mail 100"),
                        sampleMail(99L, 5001L, "Mail 99")
                ),
                1, 3, 5L, true, 5001L
        );

        PageMergeResult merged = MailboxViewModel.mergeMailPage(page0, 5001L, page1Resp, true);
        assertFalse(merged.uidValidityChanged(), "Cùng UIDVALIDITY không được đánh dấu đổi cấu trúc");
        assertEquals(5, merged.items().size(), "Phải loại bỏ UID 101 bị trùng, chỉ còn 5 thư duy nhất");
        assertEquals(2, merged.addedCount(), "Chỉ thêm mới 2 UID (100, 99)");
        assertEquals(List.of(103L, 102L, 101L, 100L, 99L),
                merged.items().stream().map(MailSummaryDto::uid).toList());

        // Trường hợp máy chủ đổi UIDVALIDITY (5001 -> 9999) giữa lúc bấm 'Tải thêm'
        MailListResponse changedResp = new MailListResponse(
                List.of(sampleMail(1L, 9999L, "Rebuilt mailbox mail")),
                1, 3, 2L, false, 9999L
        );
        PageMergeResult resetResult = MailboxViewModel.mergeMailPage(merged.items(), 5001L, changedResp, true);
        assertTrue(resetResult.uidValidityChanged(), "Phải phát hiện đổi UIDVALIDITY để tải lại từ trang 0");
        assertEquals(9999L, resetResult.uidValidity());
    }

    @Test
    @DisplayName("(b) Hồi quy bộ đếm thế hệ & hủy worker cũ khi đổi thư mục/thư liên tục, đóng cửa sổ, đăng xuất")
    void testWorkerGenerationCancelsStaleTasksAndOnLogoutOrClose() {
        WorkerCoordinator coord = new WorkerCoordinator();
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            // Đổi thư mục/thư liên tục: tác vụ 1 chạy chậm, tác vụ 2 đến ngay sau
            CountDownLatch task1Started = new CountDownLatch(1);
            CountDownLatch task1Interrupted = new CountDownLatch(1);
            long gen1 = coord.beginMailsLoad();
            Future<?> f1 = pool.submit(() -> {
                task1Started.countDown();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    task1Interrupted.countDown();
                }
            });
            coord.attachMailsTask(f1);
            assertTrue(task1Started.await(2, TimeUnit.SECONDS));

            long gen2 = coord.beginMailsLoad();
            assertFalse(coord.isMailsCurrent(gen1), "Callback của gen cũ phải bị vô hiệu hóa ngay");
            assertTrue(coord.isMailsCurrent(gen2), "Gen mới nhất phải đang hiện hành");
            assertTrue(f1.isCancelled(), "Worker cũ phải bị gọi cancel(true)");
            assertTrue(task1Interrupted.await(2, TimeUnit.SECONDS), "Luồng worker cũ phải bị ngắt tức thì");

            // Mở chi tiết thư liên tục -> hủy detail worker cũ
            long dGen1 = coord.beginDetailLoad();
            Future<?> d1 = pool.submit(() -> {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            });
            coord.attachDetailTask(d1);
            long dGen2 = coord.beginDetailLoad();
            assertFalse(coord.isDetailCurrent(dGen1));
            assertTrue(coord.isDetailCurrent(dGen2));
            assertTrue(d1.isCancelled());

            // Đóng cửa sổ / Đăng xuất -> cancelAll vô hiệu hóa mọi gen và hủy toàn bộ worker
            Future<?> d2 = pool.submit(() -> {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            });
            coord.attachDetailTask(d2);

            coord.cancelAll();
            assertFalse(coord.isMailsCurrent(gen2), "Sau cancelAll (đóng cửa sổ/đăng xuất), mọi gen thư phải hết hiệu lực");
            assertFalse(coord.isDetailCurrent(dGen2), "Sau cancelAll, mọi gen chi tiết thư phải hết hiệu lực");
            assertTrue(d2.isCancelled(), "Worker đang chạy phải bị hủy khi đóng cửa sổ / đăng xuất");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(e);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("(c) Hồi quy OAuth2 poll: Hủy tức thì trong lúc đang sleep (< 300ms, không gọi thêm API)")
    void testOAuth2PollCancelsImmediately() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger apiCalls = new AtomicInteger(0);
        CountDownLatch pollStarted = new CountDownLatch(1);
        AtomicReference<OAuthPollStatus> statusRef = new AtomicReference<>();

        Thread pollThread = new Thread(() -> {
            pollStarted.countDown();
            try {
                OAuthPollStatus st = MailboxViewModel.pollForOAuthAccount(
                        () -> {
                            apiCalls.incrementAndGet();
                            return List.<GmailAccountDto>of();
                        },
                        0,
                        Set.of(),
                        90,
                        5000L, // sleep 5 giây mỗi vòng — nếu không hủy tức thì sẽ kẹt 5 giây!
                        cancelled,
                        null
                );
                statusRef.set(st);
            } catch (Exception e) {
                fail(e);
            }
        });

        long startMs = System.currentTimeMillis();
        pollThread.start();
        assertTrue(pollStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(60); // Để luồng poll đi vào Thread.sleep(5000)

        // Người dùng bấm Hủy -> đặt cờ + interrupt luồng poll
        cancelled.set(true);
        pollThread.interrupt();
        pollThread.join(1000);
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertFalse(pollThread.isAlive(), "Luồng OAuth2 poll phải dừng ngay khi hủy!");
        assertEquals(OAuthPollStatus.CANCELLED, statusRef.get());
        assertEquals(0, apiCalls.get(), "Hủy trong lúc chờ không được gọi thêm API nào!");
        assertTrue(elapsedMs < 800, "Phải hủy tức thì dưới 800ms (thực tế: " + elapsedMs + "ms)");
    }

    @Test
    @DisplayName("(d) Hồi quy parse ngày: Instant (Z), OffsetDateTime (+07:00), LocalDateTime sang đúng múi giờ máy")
    void testMultiFormatDateParsingToTargetZone() {
        ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");

        ZonedDateTime fromInstant = MailboxViewModel.parseDateToZone("2026-11-22T09:15:00Z", vnZone);
        ZonedDateTime fromOffset = MailboxViewModel.parseDateToZone("2026-11-22T16:15:00+07:00", vnZone);
        ZonedDateTime fromLocal = MailboxViewModel.parseDateToZone("2026-11-22T16:15:00", vnZone);

        assertNotNull(fromInstant, "Phải parse được Instant ISO-8601 có hậu tố Z");
        assertNotNull(fromOffset, "Phải parse được OffsetDateTime có +07:00");
        assertNotNull(fromLocal, "Phải parse được LocalDateTime không múi giờ");

        assertEquals(Instant.parse("2026-11-22T09:15:00Z"), fromInstant.toInstant());
        assertEquals(fromInstant.toInstant(), fromOffset.toInstant(), "Instant Z và +07:00 tương đương phải cùng thời điểm");
        assertEquals(fromOffset.toInstant(), fromLocal.toInstant(), "LocalDateTime theo múi giờ máy phải khớp cùng thời điểm");
        assertEquals(16, fromInstant.getHour(), "09:15Z quy đổi sang Asia/Ho_Chi_Minh phải là 16:15");
        assertEquals(15, fromInstant.getMinute());
        assertEquals(vnZone, fromInstant.getZone());

        assertNull(MailboxViewModel.parseDateToZone(null, vnZone));
        assertNull(MailboxViewModel.parseDateToZone("not-a-date", vnZone));
    }
}
