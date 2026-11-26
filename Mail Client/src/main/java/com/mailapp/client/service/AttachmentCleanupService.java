package com.mailapp.client.service;

import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import com.mailapp.client.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Nghiệp vụ cốt lõi đề tài: "TỰ ĐỘNG tải tệp đính kèm rồi XÓA thư".
 *
 * Quy tắc an toàn tuyệt đối:
 * 1. Lưu vào <downloadBase>/MailManager/<sanitized-email>/<yyyy-MM-dd>/.
 * 2. Chống Path Traversal (../), làm sạch tên tệp, tự đổi tên chống ghi đè trùng tên.
 * 3. Tải ra tệp tạm (.part), xác minh số byte thực nhận/ghi (> 0, khớp HTTP Content-Length nếu có)
 *    + tính SHA-256; không so sánh với dung lượng ước tính từ IMAP,
 *    chuyển tên sang đích mà không ghi đè tệp có sẵn.
 * 4. CHỈ KHI MỌI TỆP ĐÍNH KÈM của thư tải & xác minh thành công mới gọi xóa thư.
 *    Nếu tải lỗi hoặc bị hủy giữa chừng -> TUYỆT ĐỐI KHÔNG XÓA thư!
 */
public class AttachmentCleanupService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public enum ItemStatus { SUCCESS, DRY_RUN, SKIPPED, FAILED, CANCELLED, DELETE_UNCERTAIN }

    public record DownloadedFileInfo(String filename, Path savedPath, long bytes, String sha256,
                                     AttachmentSizeAccuracy sizeAccuracy) {
        public DownloadedFileInfo(String filename, Path savedPath, long bytes, String sha256) {
            this(filename, savedPath, bytes, sha256, AttachmentSizeAccuracy.EXACT);
        }
    }

    public record MailCleanupResult(
            long uid,
            String subject,
            ItemStatus status,
            List<DownloadedFileInfo> downloadedFiles,
            boolean deleted,
            String message
    ) {}

    private final ApiClient apiClient;
    private int consecutiveErrors = 0;

    public AttachmentCleanupService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public int getConsecutiveErrors() {
        return consecutiveErrors;
    }

    public void resetConsecutiveErrors() {
        this.consecutiveErrors = 0;
    }

    /**
     * Thực thi luồng tải-rồi-xóa cho danh sách thư đã chọn (dùng được cho cả chạy thủ công lẫn Tự động dọn).
     */
    public List<MailCleanupResult> processMails(Long gmailId,
                                                String accountEmail,
                                                String folder,
                                                List<MailSummaryDto> targets,
                                                File downloadBaseDir,
                                                boolean permanentDelete,
                                                boolean dryRun,
                                                AtomicBoolean cancelled,
                                                Consumer<String> progressCallback) {
        return processMails(gmailId, accountEmail, folder, targets, downloadBaseDir,
                permanentDelete, dryRun, cancelled, progressCallback, () -> true);
    }

    private static boolean stopped(AtomicBoolean cancelled, BooleanSupplier stillCurrent) {
        return Thread.currentThread().isInterrupted()
                || (cancelled != null && cancelled.get()) || !stillCurrent.getAsBoolean();
    }

    public List<MailCleanupResult> processMails(Long gmailId,
                                                String accountEmail,
                                                String folder,
                                                List<MailSummaryDto> targets,
                                                File downloadBaseDir,
                                                boolean permanentDelete,
                                                boolean dryRun,
                                                AtomicBoolean cancelled,
                                                Consumer<String> progressCallback,
                                                BooleanSupplier stillCurrent) {
        List<MailCleanupResult> results = new ArrayList<>();
        if (targets == null || targets.isEmpty()) {
            return results;
        }

        Path dayDir = buildTargetDirectory(downloadBaseDir, accountEmail, LocalDate.now());
        if (!dryRun) {
            try {
                Files.createDirectories(dayDir);
            } catch (IOException e) {
                for (MailSummaryDto m : targets) {
                    results.add(new MailCleanupResult(m.uid(), m.subject(), ItemStatus.FAILED,
                            List.of(), false, "Không tạo được thư mục tải về: " + e.getMessage()));
                }
                consecutiveErrors++;
                return results;
            }
        }

        for (MailSummaryDto summary : targets) {
            if (stopped(cancelled, stillCurrent)) {
                results.add(new MailCleanupResult(summary.uid(), summary.subject(), ItemStatus.CANCELLED,
                        List.of(), false, "Đã hủy bởi người dùng (không xóa thư)."));
                continue;
            }
            if (summary.uid() <= 0 || summary.uidValidity() <= 0) {
                results.add(new MailCleanupResult(summary.uid(), summary.subject(), ItemStatus.FAILED,
                        List.of(), false, "Thiếu định danh UID/UIDVALIDITY hợp lệ; dừng lô, không xóa thư."));
                consecutiveErrors++;
                break;
            }

            if (progressCallback != null) {
                progressCallback.accept("Đang xử lý thư: " + summary.subject());
            }

            try {
                if (stopped(cancelled, stillCurrent)) {
                    results.add(new MailCleanupResult(summary.uid(), summary.subject(), ItemStatus.CANCELLED,
                            List.of(), false, "Phiên hoặc lựa chọn đã thay đổi; không xóa thư."));
                    break;
                }
                MailDetailResponse detail = apiClient.getMailDetail(
                        gmailId, folder, summary.uid(), false, summary.uidValidity(), false);

                if (detail == null || detail.uid() != summary.uid()
                        || detail.uidValidity() != summary.uidValidity()) {
                    consecutiveErrors++;
                    results.add(new MailCleanupResult(summary.uid(), summary.subject(), ItemStatus.FAILED,
                            List.of(), false, "Định danh thư trả về không khớp lựa chọn; dừng lô, không xóa thư."));
                    break;
                }
                if (stopped(cancelled, stillCurrent)) {
                    results.add(new MailCleanupResult(summary.uid(), summary.subject(), ItemStatus.CANCELLED,
                            List.of(), false, "Phiên hoặc lựa chọn đã thay đổi; không xóa thư."));
                    break;
                }

                List<MailAttachmentDto> realAttachments = detail.attachments() == null ? List.of()
                        : detail.attachments();
                var indices = new HashSet<Integer>();
                if (realAttachments.stream().anyMatch(a -> a == null || a.index() < 0 || !indices.add(a.index()))) {
                    consecutiveErrors++;
                    results.add(new MailCleanupResult(summary.uid(), summary.subject(), ItemStatus.FAILED,
                            List.of(), false, "Danh sách tệp không hợp lệ hoặc trùng chỉ số; dừng lô, không xóa thư."));
                    break;
                }

                if (realAttachments.stream().noneMatch(a -> !a.inline())) {
                    results.add(new MailCleanupResult(summary.uid(), detail.subject(), ItemStatus.SKIPPED,
                            List.of(), false, "Thư không có tệp đính kèm — bỏ qua, không xóa."));
                    continue;
                }

                if (dryRun) {
                    List<DownloadedFileInfo> preview = new ArrayList<>();
                    for (MailAttachmentDto a : realAttachments) {
                        preview.add(new DownloadedFileInfo(sanitizeFilename(a.filename()),
                                dayDir.resolve(sanitizeFilename(a.filename())), a.size(), "DRY_RUN", a.sizeAccuracy()));
                    }
                    results.add(new MailCleanupResult(summary.uid(), detail.subject(), ItemStatus.DRY_RUN,
                            preview, false,
                            "[Chạy thử] Sẽ tải " + realAttachments.size() + " tệp vào " + dayDir + " rồi xóa thư."));
                    consecutiveErrors = 0;
                    continue;
                }

                List<DownloadedFileInfo> savedFiles = new ArrayList<>();
                boolean allOk = true;
                String failureReason = null;

                for (MailAttachmentDto att : realAttachments) {
                    if (stopped(cancelled, stillCurrent)) {
                        allOk = false;
                        failureReason = "Đã hủy giữa chừng khi đang tải tệp — KHÔNG xóa thư.";
                        break;
                    }

                    String safeName = sanitizeFilename(att.filename());
                    try {
                        ApiClient.DownloadResult dl = FileUtil.saveUnique(dayDir.resolve(safeName), partPath -> {
                            if (stopped(cancelled, stillCurrent)) throw new InterruptedException("Đã hủy tải.");
                            ApiClient.DownloadResult result = apiClient.downloadAttachmentToFile(
                                    gmailId, folder, summary.uid(), att.index(), summary.uidValidity(), partPath.toFile());
                            if (Files.size(partPath) <= 0) {
                                throw new IOException("Tệp tải về rỗng; không xóa thư.");
                            }
                            if (stopped(cancelled, stillCurrent)) throw new InterruptedException("Đã hủy tải.");
                            return result;
                        });
                        Path finalPath = dl.file().toPath();
                        savedFiles.add(new DownloadedFileInfo(finalPath.getFileName().toString(), finalPath, dl.bytesWritten(), dl.sha256Hex()));
                    } catch (Exception ex) {
                        allOk = false;
                        failureReason = "Lỗi tải tệp '" + safeName + "': " + ex.getMessage() + " — KHÔNG xóa thư.";
                        if (ex instanceof ApiException apiEx && mustStopBatch(apiEx)) {
                            consecutiveErrors++;
                            results.add(new MailCleanupResult(summary.uid(), detail.subject(), ItemStatus.FAILED,
                                    List.copyOf(savedFiles), false, "Phiên hoặc định danh không còn hợp lệ; dừng lô, không xóa thư."));
                            return results;
                        }
                        break;
                    }
                }

                // CHỈ KHI MỌI TỆP LƯU THÀNH CÔNG VÀ KHÔNG BỊ HỦY mới xóa thư
                if (allOk && !stopped(cancelled, stillCurrent)) {
                    try {
                        apiClient.deleteMail(gmailId, folder, summary.uid(), permanentDelete, summary.uidValidity());
                    } catch (Exception ex) {
                        if (!ApiException.isDeleteOutcomeUncertain(ex)) throw ex;
                        if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                        consecutiveErrors++;
                        results.add(new MailCleanupResult(summary.uid(), detail.subject(), ItemStatus.DELETE_UNCERTAIN,
                                List.copyOf(savedFiles), false,
                                "Đã lưu tệp nhưng chưa xác nhận được kết quả xóa. Dừng lô; kiểm tra thư mục nguồn và Thùng rác trước khi thao tác lại."));
                        // A subsequent automatic run must not blindly retry an ambiguous COPY/MOVE.
                        break;
                    }
                    consecutiveErrors = 0;
                    results.add(new MailCleanupResult(
                            summary.uid(), detail.subject(), ItemStatus.SUCCESS, savedFiles, true,
                            "Đã tải & xác minh " + savedFiles.size() + " tệp, sau đó "
                                    + (permanentDelete ? "xóa vĩnh viễn" : "chuyển vào Thùng rác") + "."));
                } else {
                    ItemStatus st = stopped(cancelled, stillCurrent) ? ItemStatus.CANCELLED : ItemStatus.FAILED;
                    if (st == ItemStatus.FAILED) consecutiveErrors++;
                    results.add(new MailCleanupResult(summary.uid(), detail.subject(), st, savedFiles, false,
                            failureReason != null ? failureReason : "Phiên hoặc lựa chọn đã thay đổi; không xóa thư."));
                }

            } catch (ApiException apiEx) {
                consecutiveErrors++;
                results.add(new MailCleanupResult(summary.uid(), summary.subject(), ItemStatus.FAILED,
                        List.of(), false, "Lỗi API [" + apiEx.getCode() + "]: " + apiEx.getMessage() + " — không xác nhận xóa thành công."));
                if (mustStopBatch(apiEx)) {
                    // Dừng ngay toàn bộ lô khi mất quyền hoặc hết phiên
                    break;
                }
            } catch (Exception ex) {
                consecutiveErrors++;
                results.add(new MailCleanupResult(summary.uid(), summary.subject(), ItemStatus.FAILED,
                        List.of(), false, "Lỗi không xác định: " + ex.getMessage() + " — không xác nhận xóa thành công."));
            }
        }

        return results;
    }

    private static boolean mustStopBatch(ApiException ex) {
        return ex.getStatusCode() == 401 || ex.getStatusCode() == 403
                || "GMAIL_NEEDS_REAUTH".equalsIgnoreCase(ex.getCode())
                || "GMAIL_ACCOUNT_CHANGED".equalsIgnoreCase(ex.getCode())
                || "UID_VALIDITY_CHANGED".equalsIgnoreCase(ex.getCode());
    }

    public static Path buildTargetDirectory(File baseDownloadsDir, String email, LocalDate date) {
        String safeEmail = sanitizeFolderSegment(email);
        String dayStr = date.format(DATE_FMT);
        return baseDownloadsDir.toPath().resolve("MailManager").resolve(safeEmail).resolve(dayStr);
    }

    public static String sanitizeFolderSegment(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        String cleaned = raw.trim().replaceAll("[^a-zA-Z0-9._@-]", "_").replace("..", "_");
        return cleaned.isBlank() ? "unknown" : cleaned;
    }

    /**
     * Làm sạch tên tệp đính kèm phía Client (phòng thủ chiều sâu chống Path Traversal & ký tự điều khiển).
     */
    public static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) return "attachment.bin";
        String s = raw.replaceAll("[\\x00-\\x1F\\x7F]", "");
        int lastSlash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (lastSlash >= 0) s = s.substring(lastSlash + 1);
        s = s.replace("..", "_").replaceAll("[<>:\"/\\\\|?*]", "_").trim();
        while (s.startsWith(".") || s.startsWith(" ")) s = s.substring(1);
        while (s.endsWith(".") || s.endsWith(" ")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty() || s.matches("^[._\\s]+$")) return "attachment.bin";
        // Chặn tên thiết bị dành riêng của Windows (CON, PRN, AUX, NUL, COM1..9, LPT1..9)
        int firstDot = s.indexOf('.');
        String baseCheck = firstDot >= 0 ? s.substring(0, firstDot) : s;
        if (baseCheck.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")) {
            s = "file_" + s;
        }
        if (s.length() > 160) {
            int dot = s.lastIndexOf('.');
            if (dot > 0 && s.length() - dot <= 15) {
                s = s.substring(0, 160 - (s.length() - dot)) + s.substring(dot);
            } else {
                s = s.substring(0, 160);
            }
        }
        return s;
    }

    /**
     * Chống trùng tên tệp (report.pdf -> report (1).pdf) và bảo vệ chống vượt giới hạn đường dẫn Windows (MAX_PATH ~ 260).
     */
    public static Path resolveUniqueFilePath(Path directory, String safeFilename) {
        String name = safeFilename;
        int dirLen = directory.toAbsolutePath().toString().length();
        // Chừa biên an toàn cho phần hậu tố " (999)" và ".part" dưới ngưỡng 250 ký tự Windows
        int maxAllowedNameLen = Math.max(24, 240 - dirLen - 1);
        if (name.length() > maxAllowedNameLen) {
            int dot = name.lastIndexOf('.');
            if (dot > 0 && name.length() - dot <= 15 && maxAllowedNameLen > (name.length() - dot + 4)) {
                String ext = name.substring(dot);
                name = name.substring(0, maxAllowedNameLen - ext.length()) + ext;
            } else {
                name = name.substring(0, maxAllowedNameLen);
            }
        }

        Path candidate = directory.resolve(name);
        if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return candidate;
        }
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int counter = 1;
        while (true) {
            Path next = directory.resolve(base + " (" + counter + ")" + ext);
            if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
                return next;
            }
            counter++;
        }
    }
}

