package com.mailapp.mail.service;

import com.mailapp.dto.mail.MailDtos.*;
import com.mailapp.exception.AppException;
import com.mailapp.exception.MailConnectionException;
import com.mailapp.exception.MailNetworkException;
import com.mailapp.mail.ImapConnection;
import com.mailapp.mail.MailConnectionFactory;
import com.mailapp.mail.SmtpConnection;
import com.mailapp.mail.folder.GmailFolderResolver;
import com.mailapp.mail.folder.GmailFolderResolver.SpecialFolder;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.search.*;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Triển khai đầy đủ nghiệp vụ Hộp thư thật (M08, M10, M12, M16, M17).
 * Luôn sử dụng MailConnectionFactory, đóng kết nối trong try-with-resources / finally,
 * không lưu nội dung thư vào DB, chống IDOR, chống XSS, chống Path Traversal và CRLF injection.
 */
@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_TEXT_CHARS = HtmlSanitizerService.MAX_BODY_CHARS;
    private static final int STREAM_BUFFER_SIZE = 65536; // 64 KB buffer tối ưu tốc độ stream đính kèm lớn

    private final GmailAccountRepository gmailAccountRepository;
    private final MailConnectionFactory mailConnectionFactory;
    private final GmailFolderResolver folderResolver;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final HtmlSanitizerService htmlSanitizer;
    private final MailHeaderAndRateGuard headerAndRateGuard;

    /**
     * Bộ nhớ đệm tạm thời TRONG RAM (Caffeine, TTL cực ngắn — tuyệt đối KHÔNG lưu thư vào DB)
     * giúp mở lại thư mục / nội dung thư vừa xem dưới 5ms và giảm tải kết nối tới Gmail.
     */
    private final com.github.benmanes.caffeine.cache.Cache<String, List<MailFolderDto>> folderRamCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(200)
                    .expireAfterWrite(java.time.Duration.ofSeconds(25))
                    .build();

    private final com.github.benmanes.caffeine.cache.Cache<String, MailDetailResponse> detailRamCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(java.time.Duration.ofSeconds(60))
                    .build();

    public MailServiceImpl(GmailAccountRepository gmailAccountRepository,
                           MailConnectionFactory mailConnectionFactory,
                           GmailFolderResolver folderResolver,
                           SystemSettingService systemSettingService,
                           AuditLogService auditLogService,
                           HtmlSanitizerService htmlSanitizer,
                           MailHeaderAndRateGuard headerAndRateGuard) {
        this.gmailAccountRepository = gmailAccountRepository;
        this.mailConnectionFactory = mailConnectionFactory;
        this.folderResolver = folderResolver;
        this.systemSettingService = systemSettingService;
        this.auditLogService = auditLogService;
        this.htmlSanitizer = htmlSanitizer;
        this.headerAndRateGuard = headerAndRateGuard;
    }

    // =========================================================================
    // 1. Guard chung: Maintenance Mode + Anti-IDOR + NEEDS_REAUTH
    // =========================================================================

    private GmailAccount loadAndValidateAccount(User currentUser, Long gmailId) {
        if (currentUser == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa xác thực người dùng.");
        }
        if (systemSettingService.isMaintenanceMode() && currentUser.getRole() != Role.ADMIN) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE_MODE",
                    "Hệ thống đang trong chế độ bảo trì. Vui lòng thử lại sau.");
        }
        if (gmailId == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Thiếu tham số gmailId.");
        }
        GmailAccount account = gmailAccountRepository.findById(gmailId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản Gmail với ID: " + gmailId));

        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Bạn không có quyền truy cập tài khoản Gmail này.");
        }

        if (account.getStatus() == AccountStatus.NEEDS_REAUTH && !account.canFallbackToAppPassword()) {
            throw new AppException(HttpStatus.CONFLICT, "GMAIL_NEEDS_REAUTH",
                    "Tài khoản Gmail (" + MailCredentials.maskEmail(account.getEmail())
                            + ") cần cấp lại quyền truy cập. Vui lòng bấm 'Cấp lại quyền'.");
        }
        return account;
    }

    // =========================================================================
    // 2. Danh sách thư mục (GET /api/mails/folders)
    // =========================================================================

    @Override
    public List<MailFolderDto> listFolders(User currentUser, Long gmailId) {
        GmailAccount account = loadAndValidateAccount(currentUser, gmailId);
        String cacheKey = currentUser.getId() + ":" + gmailId;
        List<MailFolderDto> cachedFolders = folderRamCache.getIfPresent(cacheKey);
        if (cachedFolders != null) {
            return cachedFolders;
        }

        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            Store store = imap.getStore();
            List<MailFolderDto> result = new ArrayList<>();
            Set<String> seenImapNames = new LinkedHashSet<>();

            // Một lệnh LIST("*") duy nhất cho toàn bộ hộp thư: tái dùng cho cả thư mục
            // đặc biệt lẫn custom. Trước đây mỗi resolve() tự LIST riêng (6-7 RTT).
            Folder[] allFolders;
            try {
                allFolders = store.getDefaultFolder().list("*");
            } catch (Exception e) {
                log.debug("Không thể liệt kê thư mục: {}", e.getMessage());
                allFolders = new Folder[0];
            }

            // 1. Phân giải các thư mục đặc biệt theo thứ tự chuẩn UI
            List<SpecialFolder> orderedSpecials = List.of(
                    SpecialFolder.INBOX,
                    SpecialFolder.STARRED,
                    SpecialFolder.SENT,
                    SpecialFolder.DRAFTS,
                    SpecialFolder.SPAM,
                    SpecialFolder.TRASH,
                    SpecialFolder.ALL_MAIL
            );

            for (SpecialFolder sf : orderedSpecials) {
                try {
                    Folder f = folderResolver.resolve(store, allFolders, sf);
                    if (f != null && f.exists() && (f.getType() & Folder.HOLDS_MESSAGES) != 0) {
                        String fullName = f.getFullName();
                        if (seenImapNames.add(fullName.toLowerCase(Locale.ROOT))) {
                            int total = safeMessageCount(f);
                            int unread = safeUnreadCount(f);
                            result.add(new MailFolderDto(
                                    canonicalName(sf),
                                    fullName,
                                    vietnameseDisplayName(sf),
                                    canonicalType(sf),
                                    unread,
                                    total
                            ));
                        }
                    }
                } catch (Exception ignored) {
                    // Thư mục đặc biệt không tồn tại trên server (VD: GreenMail chỉ có INBOX)
                }
            }

            // 2. Bổ sung các thư mục tùy chỉnh (CUSTOM) từ cùng kết quả LIST ở trên
            try {
                for (Folder f : allFolders) {
                    if (f == null || (f.getType() & Folder.HOLDS_MESSAGES) == 0) continue;
                    String fullName = f.getFullName();
                    if (fullName == null || fullName.equalsIgnoreCase("[Gmail]")) continue;
                    if (seenImapNames.add(fullName.toLowerCase(Locale.ROOT))) {
                        String urlSafeName = fullName.contains("/")
                                ? "b64:" + Base64.getUrlEncoder().withoutPadding().encodeToString(fullName.getBytes(StandardCharsets.UTF_8))
                                : fullName;
                        result.add(new MailFolderDto(
                                urlSafeName,
                                fullName,
                                f.getName(),
                                "CUSTOM",
                                safeUnreadCount(f),
                                safeMessageCount(f)
                        ));
                    }
                }
            } catch (Exception e) {
                log.debug("Không thể duyệt thêm thư mục CUSTOM: {}", e.getMessage());
            }

            folderRamCache.put(cacheKey, List.copyOf(result));
            return result;
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể tải danh sách thư mục Gmail: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 3. Liệt kê & Tìm kiếm thư (GET /api/mails) — FetchProfile, không tải body
    // =========================================================================

    @Override
    public MailListResponse listMails(User currentUser,
                                      Long gmailId,
                                      String folderName,
                                      int page,
                                      int size,
                                      String q,
                                      Boolean unread,
                                      Boolean hasAttachment,
                                      String from,
                                      String since) {
        GmailAccount account = loadAndValidateAccount(currentUser, gmailId);
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            Folder folder = resolveFolderByName(imap.getStore(), folderName);
            if (folder == null || !folder.exists()) {
                throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND",
                        "Không tìm thấy thư mục: " + folderName);
            }

            folder.open(Folder.READ_ONLY);
            try {
                UIDFolder uidFolder = requireUidFolder(folder);
                long uidValidity = uidFolder.getUIDValidity();

                Message[] matched = fetchMatchingMessages(folder, q, unread, from, since);
                int totalCount = matched.length;

                // Sắp xếp mới nhất trước (IMAP trả thứ tự cũ -> mới theo message number)
                List<Message> newestFirst = new ArrayList<>(totalCount);
                for (int i = totalCount - 1; i >= 0; i--) {
                    newestFirst.add(matched[i]);
                }

                // Nếu cần lọc hasAttachment, fetch CONTENT_INFO + FLAGS + ENVELOPE theo lô nhỏ hoặc trang
                boolean filterAttachment = Boolean.TRUE.equals(hasAttachment);

                List<MailSummaryDto> items = new ArrayList<>();
                long finalTotal;
                boolean hasMore;

                if (!filterAttachment) {
                    int startIdx = safePage * safeSize;
                    if (startIdx >= newestFirst.size()) {
                        return new MailListResponse(Collections.emptyList(), safePage, safeSize, totalCount, false, uidValidity);
                    }
                    int endIdx = Math.min(startIdx + safeSize, newestFirst.size());
                    Message[] pageSlice = newestFirst.subList(startIdx, endIdx).toArray(new Message[0]);

                    fetchEnvelopeProfile(folder, pageSlice);
                    for (Message msg : pageSlice) {
                        items.add(toSummaryDto(msg, uidFolder, uidValidity));
                    }
                    finalTotal = totalCount;
                    hasMore = endIdx < totalCount;
                } else {
                    // Lọc theo đính kèm dựa trên CONTENT_INFO (không tải thân thư)
                    // Nếu số lượng lớn, chỉ duyệt tối đa 300 thư mới nhất để bảo đảm tốc độ
                    int scanLimit = Math.min(newestFirst.size(), 300);
                    Message[] toScan = newestFirst.subList(0, scanLimit).toArray(new Message[0]);
                    fetchEnvelopeProfile(folder, toScan);

                    List<MailSummaryDto> withAttach = new ArrayList<>();
                    for (Message msg : toScan) {
                        MailSummaryDto dto = toSummaryDto(msg, uidFolder, uidValidity);
                        if (dto.hasAttachment()) {
                            withAttach.add(dto);
                        }
                    }
                    finalTotal = withAttach.size();
                    int startIdx = safePage * safeSize;
                    if (startIdx < withAttach.size()) {
                        int endIdx = Math.min(startIdx + safeSize, withAttach.size());
                        items = withAttach.subList(startIdx, endIdx);
                        hasMore = endIdx < withAttach.size();
                    } else {
                        hasMore = false;
                    }
                }

                return new MailListResponse(items, safePage, safeSize, finalTotal, hasMore, uidValidity);
            } finally {
                safeCloseFolder(folder, false);
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể tải danh sách thư: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 4. Đọc chi tiết thư (GET /api/mails/{gmailId}/{folder}/{uid})
    // =========================================================================

    @Override
    public MailDetailResponse getMailDetail(User currentUser,
                                            Long gmailId,
                                            String folderName,
                                            long uid,
                                            boolean loadRemote,
                                            Long expectedUidValidity) {
        GmailAccount account = loadAndValidateAccount(currentUser, gmailId);
        String detailKey = currentUser.getId() + ":" + gmailId + ":" + folderName + ":" + uid + ":" + loadRemote;
        MailDetailResponse cached = detailRamCache.getIfPresent(detailKey);
        if (cached != null && (expectedUidValidity == null || expectedUidValidity.equals(cached.uidValidity()))) {
            return cached;
        }

        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            Folder folder = resolveFolderByName(imap.getStore(), folderName);
            if (folder == null || !folder.exists()) {
                throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND",
                        "Không tìm thấy thư mục: " + folderName);
            }

            boolean writable = openFolderReadWriteOrFallback(folder);
            try {
                UIDFolder uidFolder = requireUidFolder(folder);
                long uidValidity = uidFolder.getUIDValidity();
                verifyUidValidity(expectedUidValidity, uidValidity);

                Message msg = uidFolder.getMessageByUID(uid);
                if (msg == null) {
                    throw new AppException(HttpStatus.NOT_FOUND, "MAIL_NOT_FOUND",
                            "Không tìm thấy thư với UID " + uid + " trong thư mục " + folderName);
                }

                ParsedMailContent parsed = parseMessageContent(msg);

                // Làm sạch HTML bằng jsoup Safelist
                HtmlSanitizerService.SanitizeResult sanitized = htmlSanitizer.sanitize(parsed.htmlBody, loadRemote);

                String textBody = parsed.textBody;
                boolean truncated = sanitized.truncated();
                if (textBody != null && textBody.length() > MAX_TEXT_CHARS) {
                    textBody = textBody.substring(0, MAX_TEXT_CHARS);
                    truncated = true;
                }
                if ((textBody == null || textBody.isBlank()) && sanitized.html() != null) {
                    String fallbackPlain = htmlSanitizer.htmlToPlainText(sanitized.html());
                    if (fallbackPlain.length() > MAX_TEXT_CHARS) {
                        fallbackPlain = fallbackPlain.substring(0, MAX_TEXT_CHARS);
                        truncated = true;
                    }
                    textBody = fallbackPlain;
                }

                // Đánh dấu \Seen theo thiết kế
                if (writable && !msg.isSet(Flags.Flag.SEEN)) {
                    try {
                        msg.setFlag(Flags.Flag.SEEN, true);
                    } catch (Exception ignored) {}
                }

                List<MailAttachmentDto> attachmentDtos = new ArrayList<>();
                for (int i = 0; i < parsed.attachments.size(); i++) {
                    AttachmentMeta meta = parsed.attachments.get(i);
                    attachmentDtos.add(new MailAttachmentDto(
                            i,
                            meta.sanitizedFilename,
                            meta.contentType,
                            meta.size,
                            meta.inline,
                            meta.contentId
                    ));
                }

                MailDetailResponse detailResponse = new MailDetailResponse(
                        uid,
                        uidValidity,
                        extractFirstAddress(msg.getFrom()),
                        extractAddresses(msg.getRecipients(Message.RecipientType.TO)),
                        extractAddresses(msg.getRecipients(Message.RecipientType.CC)),
                        extractAddresses(msg.getRecipients(Message.RecipientType.BCC)),
                        extractAddresses(msg.getReplyTo()),
                        formatDate(msg),
                        getSingleHeader(msg, "Message-ID"),
                        getSingleHeader(msg, "In-Reply-To"),
                        getSingleHeader(msg, "References"),
                        decodeSubject(msg),
                        textBody != null ? textBody : "",
                        sanitized.html(),
                        truncated,
                        sanitized.hasBlockedRemoteImages(),
                        attachmentDtos,
                        extractFlags(msg)
                );
                detailRamCache.put(detailKey, detailResponse);
                return detailResponse;
            } finally {
                safeCloseFolder(folder, false);
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể đọc nội dung thư: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 5. Tải tệp đính kèm dạng stream (không nạp cả file vào RAM)
    // =========================================================================

    @Override
    public AttachmentDownloadHandle prepareAttachmentDownload(User currentUser,
                                                              Long gmailId,
                                                              String folderName,
                                                              long uid,
                                                              int attachmentIndex,
                                                              Long expectedUidValidity) {
        if (attachmentIndex < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_INDEX",
                    "Chỉ số tệp đính kèm không hợp lệ: " + attachmentIndex);
        }
        GmailAccount account = loadAndValidateAccount(currentUser, gmailId);
        headerAndRateGuard.checkAndRecordDownloadRate(currentUser.getId());

        // Bước 1: Kiểm tra tồn tại và lấy metadata nhanh (BODYSTRUCTURE) để xác thực trước khi trả response headers
        int maxMb = systemSettingService.getMaxAttachmentSizeMb();
        long maxBytes = maxMb > 0 ? (long) maxMb * 1024L * 1024L : Long.MAX_VALUE;
        AttachmentMeta targetMeta;
        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            Folder folder = resolveFolderByName(imap.getStore(), folderName);
            if (folder == null || !folder.exists()) {
                throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND", "Không tìm thấy thư mục: " + folderName);
            }
            folder.open(Folder.READ_ONLY);
            try {
                UIDFolder uidFolder = requireUidFolder(folder);
                verifyUidValidity(expectedUidValidity, uidFolder.getUIDValidity());

                Message msg = uidFolder.getMessageByUID(uid);
                if (msg == null) {
                    throw new AppException(HttpStatus.NOT_FOUND, "MAIL_NOT_FOUND", "Không tìm thấy thư với UID: " + uid);
                }
                List<Part> parts = collectAttachmentParts(msg);
                if (attachmentIndex >= parts.size()) {
                    throw new AppException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND",
                            "Không tìm thấy tệp đính kèm ở vị trí #" + attachmentIndex);
                }
                targetMeta = buildAttachmentMeta(parts.get(attachmentIndex), attachmentIndex);
                if (targetMeta.size > maxBytes) {
                    throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                            "Tệp đính kèm vượt quá kích thước tải tối đa cho phép (" + maxMb + " MB).");
                }
            } finally {
                safeCloseFolder(folder, false);
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể chuẩn bị tải tệp đính kèm: " + e.getMessage(), e);
        }

        // Bước 2: Trả streamer thực sự mở kết nối, đọc từng khối 64KB từ MIME InputStream thẳng ra OutputStream,
        // kiểm soát ngưỡng MAX_ATTACHMENT_SIZE_MB ngay trong luồng và luôn đóng Folder + Store trong finally.
        String safeContentType = FilenameSanitizer.safeDownloadContentType(targetMeta.contentType);
        AttachmentStreamer streamer = outputStream -> {
            try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
                Folder folder = resolveFolderByName(imap.getStore(), folderName);
                if (folder == null || !folder.exists()) {
                    throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND", "Thư mục không còn tồn tại: " + folderName);
                }
                folder.open(Folder.READ_ONLY);
                try {
                    UIDFolder uidFolder = requireUidFolder(folder);
                    verifyUidValidity(expectedUidValidity, uidFolder.getUIDValidity());
                    Message msg = uidFolder.getMessageByUID(uid);
                    if (msg == null) {
                        throw new AppException(HttpStatus.NOT_FOUND, "MAIL_NOT_FOUND", "Thư không còn tồn tại.");
                    }
                    List<Part> parts = collectAttachmentParts(msg);
                    if (attachmentIndex >= parts.size()) {
                        throw new AppException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "Tệp đính kèm không tồn tại.");
                    }
                    Part targetPart = parts.get(attachmentIndex);
                    try (InputStream is = targetPart.getInputStream()) {
                        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                        long streamedBytes = 0L;
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            streamedBytes += read;
                            if (streamedBytes > maxBytes) {
                                throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                                        "Luồng tải tệp đính kèm vượt quá giới hạn MAX_ATTACHMENT_SIZE_MB (" + maxMb + " MB).");
                            }
                            outputStream.write(buffer, 0, read);
                        }
                        outputStream.flush();
                    }
                } finally {
                    safeCloseFolder(folder, false);
                }
            }
        };

        return new AttachmentDownloadHandle(
                targetMeta.sanitizedFilename,
                safeContentType,
                targetMeta.size,
                streamer
        );
    }

    // =========================================================================
    // 6. Gửi thư SMTP (POST /api/mails/send)
    // =========================================================================

    @Override
    public SendMailResponse sendMail(User currentUser,
                                     SendMailRequest request,
                                     List<MultipartFile> attachments,
                                     String clientIp) {
        if (request == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Thiếu nội dung gửi thư.");
        }
        GmailAccount account = loadAndValidateAccount(currentUser, request.gmailId());

        if (!systemSettingService.isSendMailEnabled()) {
            throw new AppException(HttpStatus.FORBIDDEN, "FEATURE_DISABLED",
                    "Tính năng gửi thư hiện đang bị tạm tắt bởi quản trị viên.");
        }

        // Chống tiêm header CRLF và kiểm tra người nhận
        headerAndRateGuard.validateRecipients(request.to(), request.cc(), request.bcc());
        headerAndRateGuard.checkHeaderInjection("subject", request.subject());
        headerAndRateGuard.checkHeaderInjection("inReplyTo", request.inReplyTo());
        headerAndRateGuard.checkHeaderInjection("references", request.references());

        // Kiểm tra tổng dung lượng đính kèm theo MAX_ATTACHMENT_SIZE_MB
        int maxMb = systemSettingService.getMaxAttachmentSizeMb();
        long maxBytes = maxMb > 0 ? (long) maxMb * 1024L * 1024L : Long.MAX_VALUE;
        long totalAttachBytes = 0L;
        if (attachments != null) {
            for (MultipartFile file : attachments) {
                if (file != null && !file.isEmpty()) {
                    totalAttachBytes += file.getSize();
                }
            }
        }
        if (totalAttachBytes > maxBytes) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "Tổng dung lượng tệp đính kèm vượt quá giới hạn cho phép (" + maxMb + " MB).");
        }

        // Kiểm tra rate limit gửi thư theo user
        headerAndRateGuard.checkAndRecordSendRate(currentUser.getId());

        try (SmtpConnection smtp = mailConnectionFactory.openSmtp(account)) {
            MimeMessage mime = smtp.createMimeMessage();
            String displayName = account.getDisplayName() != null && !account.getDisplayName().isBlank()
                    ? account.getDisplayName() : account.getEmail();
            mime.setFrom(new InternetAddress(account.getEmail(), displayName, StandardCharsets.UTF_8.name()));

            addRecipients(mime, Message.RecipientType.TO, request.to());
            addRecipients(mime, Message.RecipientType.CC, request.cc());
            addRecipients(mime, Message.RecipientType.BCC, request.bcc());

            String subject = request.subject() != null ? request.subject() : "";
            mime.setSubject(subject, StandardCharsets.UTF_8.name());
            mime.setSentDate(new Date());

            if (request.inReplyTo() != null && !request.inReplyTo().isBlank()) {
                mime.setHeader("In-Reply-To", request.inReplyTo().trim());
            }
            if (request.references() != null && !request.references().isBlank()) {
                mime.setHeader("References", request.references().trim());
            }

            boolean hasHtml = request.htmlBody() != null && !request.htmlBody().isBlank();
            boolean hasFiles = attachments != null && attachments.stream().anyMatch(f -> f != null && !f.isEmpty());

            String textContent = request.textBody() != null ? request.textBody() : "";
            String htmlContent = hasHtml ? htmlSanitizer.sanitize(request.htmlBody(), true).html() : null;

            if (!hasFiles && !hasHtml) {
                mime.setText(textContent, StandardCharsets.UTF_8.name());
            } else if (!hasFiles) {
                MimeMultipart alt = new MimeMultipart("alternative");
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(textContent.isBlank() ? htmlSanitizer.htmlToPlainText(htmlContent) : textContent,
                        StandardCharsets.UTF_8.name());
                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(htmlContent, "text/html; charset=UTF-8");
                alt.addBodyPart(textPart);
                alt.addBodyPart(htmlPart);
                mime.setContent(alt);
            } else {
                MimeMultipart mixed = new MimeMultipart("mixed");

                MimeBodyPart bodyContainer = new MimeBodyPart();
                if (hasHtml) {
                    MimeMultipart alt = new MimeMultipart("alternative");
                    MimeBodyPart textPart = new MimeBodyPart();
                    textPart.setText(textContent.isBlank() ? htmlSanitizer.htmlToPlainText(htmlContent) : textContent,
                            StandardCharsets.UTF_8.name());
                    MimeBodyPart htmlPart = new MimeBodyPart();
                    htmlPart.setContent(htmlContent, "text/html; charset=UTF-8");
                    alt.addBodyPart(textPart);
                    alt.addBodyPart(htmlPart);
                    bodyContainer.setContent(alt);
                } else {
                    bodyContainer.setText(textContent, StandardCharsets.UTF_8.name());
                }
                mixed.addBodyPart(bodyContainer);

                for (MultipartFile file : attachments) {
                    if (file == null || file.isEmpty()) continue;
                    MimeBodyPart attachPart = new MimeBodyPart();
                    String safeName = FilenameSanitizer.sanitize(file.getOriginalFilename());
                    String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
                    attachPart.setDataHandler(new DataHandler(new ByteArrayDataSource(file.getBytes(), contentType)));
                    attachPart.setFileName(MimeUtility.encodeText(safeName, StandardCharsets.UTF_8.name(), null));
                    attachPart.setDisposition(Part.ATTACHMENT);
                    mixed.addBodyPart(attachPart);
                }
                mime.setContent(mixed);
            }

            mime.saveChanges();
            String generatedMessageId = mime.getMessageID();
            smtp.sendMessage(mime);

            int recipientCount = request.to().size()
                    + (request.cc() != null ? request.cc().size() : 0)
                    + (request.bcc() != null ? request.bcc().size() : 0);
            int attachCount = attachments != null ? (int) attachments.stream().filter(f -> f != null && !f.isEmpty()).count() : 0;

            // Audit log tuyệt đối KHÔNG ghi nội dung hay tiêu đề thư
            auditLogService.log(currentUser.getId(), "SEND_MAIL",
                    "Gửi thư từ " + MailCredentials.maskEmail(account.getEmail())
                            + " tới " + recipientCount + " người nhận, đính kèm: " + attachCount + " tệp",
                    clientIp);

            return new SendMailResponse(generatedMessageId, "SENT");
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Gửi thư thất bại qua SMTP: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // 7. Xóa thư (DELETE /api/mails/{gmailId}/{folder}/{uid}?permanent=false)
    // =========================================================================

    @Override
    public Map<String, Object> deleteMail(User currentUser,
                                          Long gmailId,
                                          String folderName,
                                          long uid,
                                          boolean permanent,
                                          Long expectedUidValidity,
                                          String clientIp) {
        GmailAccount account = loadAndValidateAccount(currentUser, gmailId);

        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            Store store = imap.getStore();
            Folder source = resolveFolderByName(store, folderName);
            if (source == null || !source.exists()) {
                throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND", "Không tìm thấy thư mục: " + folderName);
            }

            boolean isTrashFolder = isTrash(store, source, folderName);
            if (permanent && !isTrashFolder) {
                throw new AppException(HttpStatus.BAD_REQUEST, "PERMANENT_DELETE_REQUIRES_TRASH",
                        "Chỉ cho phép xóa vĩnh viễn khi thư đang nằm trong Thùng rác.");
            }
            if (!permanent && isTrashFolder) {
                throw new AppException(HttpStatus.BAD_REQUEST, "PERMANENT_DELETE_REQUIRED_IN_TRASH",
                        "Thư đã nằm trong Thùng rác. Cần truyền permanent=true để xóa vĩnh viễn.");
            }

            source.open(Folder.READ_WRITE);
            try {
                UIDFolder uidFolder = requireUidFolder(source);
                verifyUidValidity(expectedUidValidity, uidFolder.getUIDValidity());

                Message msg = uidFolder.getMessageByUID(uid);
                if (msg == null) {
                    throw new AppException(HttpStatus.NOT_FOUND, "MAIL_NOT_FOUND", "Không tìm thấy thư với UID: " + uid);
                }

                if (!permanent) {
                    // Đúng hành vi Gmail: phân giải thư mục \Trash, COPY sang Thùng rác rồi đánh dấu \Deleted + expunge ở thư mục gốc
                    Folder trash = resolveOrCreateTrashFolder(store);
                    source.copyMessages(new Message[]{msg}, trash);
                    msg.setFlag(Flags.Flag.DELETED, true);
                    source.expunge();
                } else {
                    // Xóa vĩnh viễn trong Thùng rác (chỉ vào đây khi permanent=true && isTrashFolder=true)
                    msg.setFlag(Flags.Flag.DELETED, true);
                    source.expunge();
                }

                auditLogService.log(currentUser.getId(), "DELETE_MAIL",
                        String.format("Xóa thư (permanent=%s) gmail=%s, folder=%s, uid=%d",
                                permanent, MailCredentials.maskEmail(account.getEmail()), folderName, uid),
                        clientIp);

                invalidateAccountMailCaches(currentUser.getId(), gmailId, folderName, uid);

                String resultMsg = permanent
                        ? "Đã xóa vĩnh viễn thư khỏi Thùng rác."
                        : "Đã chuyển thư vào Thùng rác.";
                return Map.of("status", "DELETED", "permanent", permanent, "message", resultMsg);
            } finally {
                safeCloseFolder(source, true);
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể xóa thư: " + e.getMessage(), e);
        }
    }

    private void invalidateAccountMailCaches(Long userId, Long gmailId, String folderName, Long uid) {
        folderRamCache.invalidate(userId + ":" + gmailId);
        if (folderName != null && uid != null) {
            detailRamCache.invalidate(userId + ":" + gmailId + ":" + folderName + ":" + uid + ":false");
            detailRamCache.invalidate(userId + ":" + gmailId + ":" + folderName + ":" + uid + ":true");
        }
    }

    // =========================================================================
    // 8. Cập nhật cờ thư (PUT /api/mails/{gmailId}/{folder}/{uid}/flags — M17)
    // =========================================================================

    @Override
    public MailFlagsDto updateFlags(User currentUser,
                                    Long gmailId,
                                    String folderName,
                                    long uid,
                                    UpdateFlagsRequest request,
                                    Long expectedUidValidity) {
        GmailAccount account = loadAndValidateAccount(currentUser, gmailId);
        if (request == null || (request.seen() == null && request.flagged() == null)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Vui lòng truyền ít nhất một cờ cần cập nhật (seen hoặc flagged).");
        }

        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            Folder folder = resolveFolderByName(imap.getStore(), folderName);
            if (folder == null || !folder.exists()) {
                throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND", "Không tìm thấy thư mục: " + folderName);
            }

            folder.open(Folder.READ_WRITE);
            try {
                UIDFolder uidFolder = requireUidFolder(folder);
                verifyUidValidity(expectedUidValidity, uidFolder.getUIDValidity());

                Message msg = uidFolder.getMessageByUID(uid);
                if (msg == null) {
                    throw new AppException(HttpStatus.NOT_FOUND, "MAIL_NOT_FOUND", "Không tìm thấy thư với UID: " + uid);
                }

                if (request.seen() != null) {
                    msg.setFlag(Flags.Flag.SEEN, request.seen());
                }
                if (request.flagged() != null) {
                    msg.setFlag(Flags.Flag.FLAGGED, request.flagged());
                }

                invalidateAccountMailCaches(currentUser.getId(), gmailId, folderName, uid);
                return extractFlags(msg);
            } finally {
                safeCloseFolder(folder, false);
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể cập nhật cờ thư: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Helpers: Folder resolution, Search (X-GM-RAW + SearchTerm), MIME Parsing
    // =========================================================================

    private Folder resolveFolderByName(Store store, String folderParam) throws MessagingException {
        if (folderParam == null || folderParam.isBlank() || "INBOX".equalsIgnoreCase(folderParam)) {
            return folderResolver.resolve(store, SpecialFolder.INBOX);
        }
        String trimmed = folderParam.trim();
        if (trimmed.startsWith("b64:")) {
            String decoded = new String(Base64.getUrlDecoder().decode(trimmed.substring(4)), StandardCharsets.UTF_8);
            return store.getFolder(decoded);
        }

        SpecialFolder mapped = switch (trimmed.toUpperCase(Locale.ROOT)) {
            case "SENT" -> SpecialFolder.SENT;
            case "DRAFTS" -> SpecialFolder.DRAFTS;
            case "TRASH" -> SpecialFolder.TRASH;
            case "SPAM", "JUNK" -> SpecialFolder.SPAM;
            case "STARRED" -> SpecialFolder.STARRED;
            case "ALL", "ALL_MAIL" -> SpecialFolder.ALL_MAIL;
            default -> null;
        };

        if (mapped != null) {
            try {
                return folderResolver.resolve(store, mapped);
            } catch (MessagingException e) {
                // Fallback: kiểm tra trực tiếp tên thư mục (VD: "Trash" trên GreenMail)
                Folder direct = store.getFolder(trimmed);
                if (direct != null && direct.exists()) {
                    return direct;
                }
                throw e;
            }
        }
        return store.getFolder(trimmed);
    }

    private Folder resolveOrCreateTrashFolder(Store store) throws MessagingException {
        try {
            Folder resolved = folderResolver.resolve(store, SpecialFolder.TRASH);
            if (resolved != null && resolved.exists()) {
                return resolved;
            }
        } catch (MessagingException ignored) {}

        Folder fallback = store.getFolder("Trash");
        if (!fallback.exists()) {
            fallback.create(Folder.HOLDS_MESSAGES);
        }
        return fallback;
    }

    private boolean isTrash(Store store, Folder source, String folderParam) {
        if ("TRASH".equalsIgnoreCase(folderParam)) return true;
        String full = source.getFullName() != null ? source.getFullName().toLowerCase(Locale.ROOT) : "";
        if (full.equals("trash") || full.endsWith("/trash") || full.endsWith("/thùng rác") || full.endsWith("/bin")) {
            return true;
        }
        try {
            Folder resolvedTrash = folderResolver.resolve(store, SpecialFolder.TRASH);
            return resolvedTrash != null && resolvedTrash.getFullName().equalsIgnoreCase(source.getFullName());
        } catch (Exception ignored) {
            return false;
        }
    }

    private Message[] fetchMatchingMessages(Folder folder,
                                            String q,
                                            Boolean unread,
                                            String from,
                                            String since) throws MessagingException {
        boolean hasFilter = (q != null && !q.isBlank())
                || unread != null
                || (from != null && !from.isBlank())
                || (since != null && !since.isBlank());

        if (!hasFilter) {
            return folder.getMessages();
        }

        // Thử X-GM-RAW trên Gmail nếu có từ khóa q hoặc lọc nâng cao
        if (isGmailStore(folder.getStore()) && (q != null && !q.isBlank())) {
            StringBuilder rawQuery = new StringBuilder(q.trim());
            if (Boolean.TRUE.equals(unread)) rawQuery.append(" is:unread");
            else if (Boolean.FALSE.equals(unread)) rawQuery.append(" is:read");
            if (from != null && !from.isBlank()) rawQuery.append(" from:").append(from.trim());
            if (since != null && !since.isBlank()) rawQuery.append(" after:").append(since.trim());

            try {
                // Sử dụng Angus Mail GmailRawSearchTerm nếu có trên classpath (reflection an toàn)
                Class<?> rawTermClass = Class.forName("org.eclipse.angus.mail.gimap.GmailRawSearchTerm");
                SearchTerm gmRaw = (SearchTerm) rawTermClass.getConstructor(String.class).newInstance(rawQuery.toString());
                return folder.search(gmRaw);
            } catch (Exception e) {
                log.debug("X-GM-RAW không khả dụng, chuyển sang SearchTerm chuẩn: {}", e.getMessage());
            }
        }

        // Fallback SearchTerm chuẩn (chạy ổn định với mọi IMAP server kể cả GreenMail)
        List<SearchTerm> terms = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String kw = q.trim();
            terms.add(new OrTerm(new SearchTerm[]{
                    new SubjectTerm(kw),
                    new FromStringTerm(kw),
                    new BodyTerm(kw)
            }));
        }
        if (unread != null) {
            terms.add(new FlagTerm(new Flags(Flags.Flag.SEEN), !unread));
        }
        if (from != null && !from.isBlank()) {
            terms.add(new FromStringTerm(from.trim()));
        }
        if (since != null && !since.isBlank()) {
            try {
                LocalDate localDate = LocalDate.parse(since.trim());
                Date sinceDate = Date.from(localDate.atStartOfDay(ZoneOffset.UTC).toInstant());
                terms.add(new ReceivedDateTerm(ComparisonTerm.GE, sinceDate));
            } catch (Exception e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Định dạng tham số 'since' không hợp lệ (cần yyyy-MM-dd): " + since);
            }
        }

        if (terms.isEmpty()) {
            return folder.getMessages();
        }
        SearchTerm combined = terms.size() == 1 ? terms.get(0) : new AndTerm(terms.toArray(new SearchTerm[0]));
        try {
            return folder.search(combined);
        } catch (MessagingException e) {
            // Một số máy chủ IMAP (như GreenMail) không hỗ trợ SEARCH với CHARSET UTF-8 -> fallback lọc theo ENVELOPE/FLAGS
            log.debug("IMAP SEARCH bị máy chủ từ chối ({}), chuyển sang lọc cục bộ theo ENVELOPE/FLAGS.", e.getMessage());
            return filterMessagesByEnvelope(folder, q, unread, from, since);
        }
    }

    private Message[] filterMessagesByEnvelope(Folder folder,
                                               String q,
                                               Boolean unread,
                                               String from,
                                               String since) throws MessagingException {
        Message[] all = folder.getMessages();
        fetchEnvelopeProfile(folder, all);

        String qLower = (q != null && !q.isBlank()) ? q.trim().toLowerCase(Locale.ROOT) : null;
        String fromLower = (from != null && !from.isBlank()) ? from.trim().toLowerCase(Locale.ROOT) : null;
        Date sinceDate = null;
        if (since != null && !since.isBlank()) {
            sinceDate = Date.from(LocalDate.parse(since.trim()).atStartOfDay(ZoneOffset.UTC).toInstant());
        }

        List<Message> matched = new ArrayList<>();
        for (Message m : all) {
            if (unread != null && m.isSet(Flags.Flag.SEEN) == unread) {
                continue;
            }
            if (fromLower != null) {
                MailAddressDto sender = extractFirstAddress(m.getFrom());
                String combinedFrom = (sender.name() + " " + sender.address()).toLowerCase(Locale.ROOT);
                if (!combinedFrom.contains(fromLower)) continue;
            }
            if (sinceDate != null) {
                Date d = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
                if (d != null && d.before(sinceDate)) continue;
            }
            if (qLower != null) {
                String subj = decodeSubject(m).toLowerCase(Locale.ROOT);
                MailAddressDto sender = extractFirstAddress(m.getFrom());
                String combinedFrom = (sender.name() + " " + sender.address()).toLowerCase(Locale.ROOT);
                if (!subj.contains(qLower) && !combinedFrom.contains(qLower)) continue;
            }
            matched.add(m);
        }
        return matched.toArray(new Message[0]);
    }

    private boolean isGmailStore(Store store) {
        if (store == null || store.getURLName() == null || store.getURLName().getHost() == null) {
            return false;
        }
        return store.getURLName().getHost().toLowerCase(Locale.ROOT).contains("gmail.com");
    }

    private void fetchEnvelopeProfile(Folder folder, Message[] messages) throws MessagingException {
        if (messages == null || messages.length == 0) return;
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.FLAGS);
        fp.add(FetchProfile.Item.CONTENT_INFO);
        fp.add(UIDFolder.FetchProfileItem.UID);
        folder.fetch(messages, fp);
    }

    private MailSummaryDto toSummaryDto(Message msg, UIDFolder uidFolder, long uidValidity) throws MessagingException {
        long uid = uidFolder.getUID(msg);
        String contentType = safeContentType(msg);
        int attachCount = estimateAttachmentCountCheaply(msg, contentType);
        boolean hasAttach = attachCount > 0 || contentType.toLowerCase(Locale.ROOT).startsWith("multipart/mixed");

        return new MailSummaryDto(
                uid,
                uidValidity,
                extractFirstAddress(msg.getFrom()),
                extractAddresses(msg.getRecipients(Message.RecipientType.TO)),
                decodeSubject(msg),
                "", // Không tải thân thư khi liệt kê (chỉ snippet khi rẻ)
                formatDate(msg),
                extractFlags(msg),
                Math.max(0, msg.getSize()),
                hasAttach,
                Math.max(attachCount, hasAttach ? 1 : 0)
        );
    }

    private int estimateAttachmentCountCheaply(Message msg, String contentType) {
        if (contentType == null) return 0;
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("multipart/mixed") && !lower.startsWith("multipart/related")) {
            return 0;
        }
        // Kiểm tra nhẹ nếu cấu trúc đã có sẵn từ CONTENT_INFO / BODYSTRUCTURE
        try {
            if (msg.isMimeType("multipart/mixed")) {
                return 1; // Chỉ báo hiệu có khả năng đính kèm mà không tải toàn bộ thân thư
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // =========================================================================
    // MIME Recursive Parsing (hỗ trợ multipart lồng, charset lạ, RFC 2047/2231)
    // =========================================================================

    private static class ParsedMailContent {
        String textBody = "";
        String htmlBody = "";
        final List<AttachmentMeta> attachments = new ArrayList<>();
    }

    private record AttachmentMeta(
            String sanitizedFilename,
            String contentType,
            long size,
            boolean inline,
            String contentId
    ) {}

    private ParsedMailContent parseMessageContent(Message msg) throws Exception {
        ParsedMailContent parsed = new ParsedMailContent();
        traversePart(msg, parsed, 0);
        return parsed;
    }

    private void traversePart(Part part, ParsedMailContent parsed, int depth) throws Exception {
        if (part == null || depth > 12) return;

        String disposition = safeDisposition(part);
        String rawFilename = safeFilename(part);
        String contentType = safeContentType(part);
        String lowerCt = contentType.toLowerCase(Locale.ROOT);

        boolean isExplicitAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition);
        boolean isInlineAsset = Part.INLINE.equalsIgnoreCase(disposition)
                && !lowerCt.startsWith("text/plain")
                && !lowerCt.startsWith("text/html")
                && !lowerCt.startsWith("multipart/");

        if (lowerCt.startsWith("multipart/")) {
            Object content = part.getContent();
            if (content instanceof Multipart mp) {
                for (int i = 0; i < mp.getCount(); i++) {
                    traversePart(mp.getBodyPart(i), parsed, depth + 1);
                }
            }
            return;
        }

        if (isExplicitAttachment || isInlineAsset || (rawFilename != null && !lowerCt.startsWith("text/plain") && !lowerCt.startsWith("text/html"))) {
            parsed.attachments.add(buildAttachmentMeta(part, parsed.attachments.size()));
            return;
        }

        if (lowerCt.startsWith("text/plain")) {
            String text = readPartAsStringSafe(part);
            if (!text.isEmpty()) {
                parsed.textBody = parsed.textBody.isEmpty() ? text : parsed.textBody + "\n" + text;
            }
        } else if (lowerCt.startsWith("text/html")) {
            String html = readPartAsStringSafe(part);
            if (!html.isEmpty()) {
                parsed.htmlBody = parsed.htmlBody.isEmpty() ? html : parsed.htmlBody + "\n" + html;
            }
        } else if (lowerCt.startsWith("message/rfc822")) {
            Object nested = part.getContent();
            if (nested instanceof Part nestedPart) {
                traversePart(nestedPart, parsed, depth + 1);
            }
        } else {
            // Nội dung nhị phân không gắn disposition vẫn coi là đính kèm
            parsed.attachments.add(buildAttachmentMeta(part, parsed.attachments.size()));
        }
    }

    private List<Part> collectAttachmentParts(Message msg) throws Exception {
        List<Part> result = new ArrayList<>();
        collectAttachmentPartsRecursive(msg, result, 0);
        return result;
    }

    private void collectAttachmentPartsRecursive(Part part, List<Part> result, int depth) throws Exception {
        if (part == null || depth > 12) return;
        String contentType = safeContentType(part);
        String lowerCt = contentType.toLowerCase(Locale.ROOT);
        String disposition = safeDisposition(part);
        String rawFilename = safeFilename(part);

        if (lowerCt.startsWith("multipart/")) {
            Object content = part.getContent();
            if (content instanceof Multipart mp) {
                for (int i = 0; i < mp.getCount(); i++) {
                    collectAttachmentPartsRecursive(mp.getBodyPart(i), result, depth + 1);
                }
            }
            return;
        }

        boolean isExplicitAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition);
        boolean isInlineAsset = Part.INLINE.equalsIgnoreCase(disposition)
                && !lowerCt.startsWith("text/plain")
                && !lowerCt.startsWith("text/html");

        if (isExplicitAttachment || isInlineAsset
                || (rawFilename != null && !lowerCt.startsWith("text/plain") && !lowerCt.startsWith("text/html"))
                || (!lowerCt.startsWith("text/plain") && !lowerCt.startsWith("text/html") && !lowerCt.startsWith("message/rfc822"))) {
            result.add(part);
        }
    }

    private AttachmentMeta buildAttachmentMeta(Part part, int index) {
        String rawFilename = safeFilename(part);
        String contentType = safeContentType(part);
        String cleanContentType = contentType.split(";")[0].trim();
        if (rawFilename == null || rawFilename.isBlank()) {
            String ext = guessExtension(cleanContentType);
            rawFilename = "attachment-" + (index + 1) + ext;
        }
        String sanitized = FilenameSanitizer.sanitize(rawFilename);
        String disposition = safeDisposition(part);
        String contentId = null;
        try {
            String[] cids = part.getHeader("Content-ID");
            if (cids != null && cids.length > 0 && cids[0] != null) {
                contentId = cids[0].trim().replaceAll("^<|>$", "");
            }
        } catch (Exception ignored) {}

        boolean inline = Part.INLINE.equalsIgnoreCase(disposition) || contentId != null;
        long size = 0;
        try {
            long rawSize = Math.max(0, part.getSize());
            String[] encHeaders = part.getHeader("Content-Transfer-Encoding");
            boolean isBase64 = encHeaders != null && encHeaders.length > 0
                    && encHeaders[0] != null && encHeaders[0].trim().equalsIgnoreCase("base64");
            // MIME Base64 chuẩn: mỗi dòng 76 ký tự + CRLF (78 byte) mã hóa đúng 57 byte nhị phân thật
            size = isBase64 ? (rawSize * 57L) / 78L : rawSize;
        } catch (Exception ignored) {}

        return new AttachmentMeta(sanitized, cleanContentType, size, inline, contentId);
    }

    private String readPartAsStringSafe(Part part) {
        try {
            Object content = part.getContent();
            if (content instanceof String s) {
                return s;
            }
        } catch (UnsupportedEncodingException uee) {
            // Fallback cho charset lạ (VD: unknown-8bit, x-windows-...)
        } catch (Exception ignored) {}

        try (InputStream is = part.getInputStream()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int maxRead = MAX_TEXT_CHARS * 2;
            int r;
            while ((r = is.read(buf)) != -1 && total < maxRead) {
                baos.write(buf, 0, r);
                total += r;
            }
            Charset cs = extractCharsetOrUtf8(safeContentType(part));
            return baos.toString(cs);
        } catch (Exception e) {
            return "";
        }
    }

    private Charset extractCharsetOrUtf8(String contentType) {
        try {
            ContentType ct = new ContentType(contentType);
            String cs = ct.getParameter("charset");
            if (cs != null && !cs.isBlank() && Charset.isSupported(cs.trim())) {
                return Charset.forName(cs.trim());
            }
        } catch (Exception ignored) {}
        return StandardCharsets.UTF_8;
    }

    // =========================================================================
    // Minor Helpers
    // =========================================================================

    private UIDFolder requireUidFolder(Folder folder) {
        if (folder instanceof UIDFolder uf) {
            return uf;
        }
        throw new MailNetworkException("Thư mục IMAP không hỗ trợ chuẩn UIDFolder.");
    }

    private void verifyUidValidity(Long expected, long actual) {
        if (expected != null && expected != actual) {
            throw new AppException(HttpStatus.CONFLICT, "UID_VALIDITY_CHANGED",
                    "Thư mục đã thay đổi chỉ mục UID (UIDVALIDITY đổi từ " + expected + " sang " + actual + "). Vui lòng làm mới danh sách.");
        }
    }

    private boolean openFolderReadWriteOrFallback(Folder folder) throws MessagingException {
        try {
            folder.open(Folder.READ_WRITE);
            return true;
        } catch (Exception e) {
            folder.open(Folder.READ_ONLY);
            return false;
        }
    }

    private void safeCloseFolder(Folder folder, boolean expunge) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(expunge);
            } catch (Exception ignored) {}
        }
    }

    private int safeMessageCount(Folder f) {
        try { return Math.max(0, f.getMessageCount()); } catch (Exception e) { return 0; }
    }

    private int safeUnreadCount(Folder f) {
        try { return Math.max(0, f.getUnreadMessageCount()); } catch (Exception e) { return 0; }
    }

    private String safeContentType(Part part) {
        try {
            String ct = part.getContentType();
            return ct != null ? ct : "application/octet-stream";
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private String safeDisposition(Part part) {
        try { return part.getDisposition(); } catch (Exception e) { return null; }
    }

    private String safeFilename(Part part) {
        try { return part.getFileName(); } catch (Exception e) { return null; }
    }

    private String decodeSubject(Message msg) {
        try {
            String subj = msg.getSubject();
            if (subj == null) return "(Không có tiêu đề)";
            return MimeUtility.decodeText(subj);
        } catch (Exception e) {
            return "(Không có tiêu đề)";
        }
    }

    private String formatDate(Message msg) {
        try {
            Date d = msg.getReceivedDate() != null ? msg.getReceivedDate() : msg.getSentDate();
            return d != null ? d.toInstant().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getSingleHeader(Message msg, String name) {
        try {
            String[] vals = msg.getHeader(name);
            return (vals != null && vals.length > 0) ? vals[0] : null;
        } catch (Exception e) {
            return null;
        }
    }

    private MailAddressDto extractFirstAddress(Address[] addresses) {
        List<MailAddressDto> list = extractAddresses(addresses);
        return list.isEmpty() ? new MailAddressDto("", "") : list.get(0);
    }

    private List<MailAddressDto> extractAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return Collections.emptyList();
        List<MailAddressDto> list = new ArrayList<>(addresses.length);
        for (Address a : addresses) {
            if (a instanceof InternetAddress ia) {
                String personal = ia.getPersonal();
                if (personal != null) {
                    try { personal = MimeUtility.decodeText(personal); } catch (Exception ignored) {}
                }
                list.add(new MailAddressDto(personal != null ? personal : "", ia.getAddress() != null ? ia.getAddress() : ""));
            } else if (a != null) {
                list.add(new MailAddressDto("", a.toString()));
            }
        }
        return list;
    }

    private MailFlagsDto extractFlags(Message msg) {
        try {
            Flags flags = msg.getFlags();
            if (flags == null) return new MailFlagsDto(false, false, false, false);
            return new MailFlagsDto(
                    flags.contains(Flags.Flag.SEEN),
                    flags.contains(Flags.Flag.FLAGGED),
                    flags.contains(Flags.Flag.ANSWERED),
                    flags.contains(Flags.Flag.DRAFT)
            );
        } catch (Exception e) {
            return new MailFlagsDto(false, false, false, false);
        }
    }

    private void addRecipients(MimeMessage mime, Message.RecipientType type, List<String> addresses) throws MessagingException {
        if (addresses == null || addresses.isEmpty()) return;
        for (String addr : addresses) {
            mime.addRecipient(type, new InternetAddress(addr.trim()));
        }
    }

    private String canonicalName(SpecialFolder sf) {
        return switch (sf) {
            case INBOX -> "INBOX";
            case SENT -> "SENT";
            case DRAFTS -> "DRAFTS";
            case TRASH -> "TRASH";
            case SPAM -> "SPAM";
            case STARRED -> "STARRED";
            case ALL_MAIL -> "ALL";
        };
    }

    private String canonicalType(SpecialFolder sf) {
        return canonicalName(sf);
    }

    private String vietnameseDisplayName(SpecialFolder sf) {
        return switch (sf) {
            case INBOX -> "Hộp thư đến";
            case SENT -> "Thư đã gửi";
            case DRAFTS -> "Thư nháp";
            case TRASH -> "Thùng rác";
            case SPAM -> "Thư rác";
            case STARRED -> "Có gắn dấu sao";
            case ALL_MAIL -> "Tất cả thư";
        };
    }

    private String guessExtension(String contentType) {
        if (contentType == null) return ".bin";
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "application/pdf" -> ".pdf";
            case "text/plain" -> ".txt";
            default -> ".bin";
        };
    }
}
