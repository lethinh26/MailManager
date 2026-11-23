package com.mailapp.mail.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mailapp.exception.AppException;
import com.mailapp.exception.RateLimitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bảo vệ gửi thư (M10): chống tiêm header CRLF, kiểm tra định dạng email, giới hạn người nhận,
 * và rate limit gửi thư theo từng người dùng.
 */
@Component
public class MailHeaderAndRateGuard {

    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@<>\"',;:\\r\\n]+@[^\\s@<>\"',;:\\r\\n]+\\.[^\\s@<>\"',;:\\r\\n]+$");
    public static final int MAX_RECIPIENTS = 50;

    private record SendCounter(int count, Instant windowStart) {}

    private final int maxSendsPerWindow;
    private final int windowMinutes;
    private final int maxDownloadsPerWindow;
    private final Cache<Long, SendCounter> sendCounters;
    private final Cache<Long, SendCounter> downloadCounters;

    @Autowired
    public MailHeaderAndRateGuard(
            @Value("${mail.gmail.send.rate-limit.max-sends:30}") int maxSendsPerWindow,
            @Value("${mail.gmail.send.rate-limit.window-minutes:15}") int windowMinutes,
            @Value("${mail.gmail.download.rate-limit.max-downloads:120}") int maxDownloadsPerWindow) {
        this.maxSendsPerWindow = maxSendsPerWindow;
        this.windowMinutes = windowMinutes;
        this.maxDownloadsPerWindow = maxDownloadsPerWindow;
        this.sendCounters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(windowMinutes))
                .maximumSize(10_000)
                .build();
        this.downloadCounters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(windowMinutes))
                .maximumSize(10_000)
                .build();
    }

    public MailHeaderAndRateGuard(int maxSendsPerWindow, int windowMinutes) {
        this(maxSendsPerWindow, windowMinutes, 120);
    }

    public void checkHeaderInjection(String fieldName, String value) {
        if (value == null) return;
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "HEADER_INJECTION_DETECTED",
                    "Trường '" + fieldName + "' chứa ký tự xuống dòng không hợp lệ (nghi ngờ tiêm header).");
        }
    }

    public void validateRecipients(List<String> to, List<String> cc, List<String> bcc) {
        int total = (to != null ? to.size() : 0)
                + (cc != null ? cc.size() : 0)
                + (bcc != null ? bcc.size() : 0);
        if (to == null || to.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Vui lòng nhập ít nhất một địa chỉ người nhận (To).");
        }
        if (total > MAX_RECIPIENTS) {
            throw new AppException(HttpStatus.BAD_REQUEST, "TOO_MANY_RECIPIENTS",
                    "Tổng số người nhận vượt quá giới hạn cho phép (" + MAX_RECIPIENTS + ").");
        }
        validateAddressList("to", to);
        validateAddressList("cc", cc);
        validateAddressList("bcc", bcc);
    }

    private void validateAddressList(String field, List<String> addresses) {
        if (addresses == null) return;
        for (String addr : addresses) {
            if (addr == null || addr.isBlank()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Địa chỉ email trong trường '" + field + "' không được để trống.");
            }
            checkHeaderInjection(field, addr);
            String trimmed = addr.trim();
            if (!SIMPLE_EMAIL.matcher(trimmed).matches()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL",
                        "Địa chỉ email không hợp lệ trong '" + field + "': " + trimmed);
            }
        }
    }

    public synchronized void checkAndRecordSendRate(Long userId) {
        if (userId == null) return;
        Instant now = Instant.now();
        SendCounter current = sendCounters.getIfPresent(userId);
        if (current == null || now.isAfter(current.windowStart().plus(Duration.ofMinutes(windowMinutes)))) {
            sendCounters.put(userId, new SendCounter(1, now));
            return;
        }
        if (current.count() >= maxSendsPerWindow) {
            long retryAfter = Math.max(1L,
                    Duration.between(now, current.windowStart().plus(Duration.ofMinutes(windowMinutes))).getSeconds());
            throw new RateLimitException("Bạn đã gửi quá nhiều thư trong thời gian ngắn. Vui lòng thử lại sau.", retryAfter);
        }
        sendCounters.put(userId, new SendCounter(current.count() + 1, current.windowStart()));
    }

    public synchronized void checkAndRecordDownloadRate(Long userId) {
        if (userId == null) return;
        Instant now = Instant.now();
        SendCounter current = downloadCounters.getIfPresent(userId);
        if (current == null || now.isAfter(current.windowStart().plus(Duration.ofMinutes(windowMinutes)))) {
            downloadCounters.put(userId, new SendCounter(1, now));
            return;
        }
        if (current.count() >= maxDownloadsPerWindow) {
            long retryAfter = Math.max(1L,
                    Duration.between(now, current.windowStart().plus(Duration.ofMinutes(windowMinutes))).getSeconds());
            throw new RateLimitException("Bạn đã tải tệp đính kèm quá nhiều lần trong thời gian ngắn. Vui lòng thử lại sau.", retryAfter);
        }
        downloadCounters.put(userId, new SendCounter(current.count() + 1, current.windowStart()));
    }
}

