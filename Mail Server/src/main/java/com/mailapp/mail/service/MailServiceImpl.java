package com.mailapp.mail.service;

import com.mailapp.dto.mail.MailDtos.*;
import com.mailapp.exception.AppException;
import com.mailapp.exception.MailConnectionException;
import com.mailapp.exception.MailAuthException;
import com.mailapp.exception.MailNetworkException;
import com.mailapp.mail.ImapConnection;
import com.mailapp.mail.MailConnectionFactory;
import com.mailapp.mail.SmtpConnection;
import com.mailapp.mail.folder.FolderCountReader;
import com.mailapp.mail.folder.GmailFolderResolver;
import com.mailapp.mail.folder.GmailFolderResolver.MissingSpecialFolderException;
import com.mailapp.mail.folder.GmailFolderResolver.SpecialFolder;
import com.mailapp.mail.model.MailCredentials;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.search.*;
import jakarta.mail.util.SharedFileInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPMessage;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.angus.mail.imap.protocol.BASE64MailboxEncoder;
import org.eclipse.angus.mail.iap.Argument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final int DATE_METADATA_BATCH_SIZE = 500;
    private static final int MAX_SUBJECT_CHARS = 200;
    private static final int MAX_SEND_BODY_BYTES = 1024 * 1024;
    private static final long MAX_SEND_ATTACHMENTS_BYTES = 60L * 1024 * 1024;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_TEXT_CHARS = HtmlSanitizerService.MAX_BODY_CHARS;
    private static final int STREAM_BUFFER_SIZE = 65536; // 64 KB buffer tối ưu tốc độ stream đính kèm lớn
    private static final long MAX_EMBEDDED_MIME_BYTES = 128L * 1024 * 1024;

    private final GmailAccountRepository gmailAccountRepository;
    private final MailConnectionFactory mailConnectionFactory;
    private final GmailFolderResolver folderResolver;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;
    private final HtmlSanitizerService htmlSanitizer;
    private final MailHeaderAndRateGuard headerAndRateGuard;
    // Only the isolated GreenMail fixture constructor enables the non-UIDPLUS
    // fallback. A deployed service must never issue unscoped EXPUNGE.
    private final boolean allowLocalTestExpunge;

    /**
     * Bộ nhớ đệm tạm thời TRONG RAM cho số thư, không cache nội dung hay trạng thái
     * Seen vì có thể thay đổi từ một IMAP client khác.
     */
    /** Số thư trong folder đổi chậm; giữ cache đủ lâu để các lần refresh liên tiếp không đụng Gmail. */
    private static final int FOLDER_CACHE_TTL_SECONDS = 120;

    private record FolderCacheKey(Long ownerId, Long mailboxId, Long revision) {}
    private final com.github.benmanes.caffeine.cache.Cache<FolderCacheKey, List<MailFolderDto>> folderRamCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(200)
                    .expireAfterWrite(java.time.Duration.ofSeconds(FOLDER_CACHE_TTL_SECONDS))
                    .build();

    private void invalidateFolderCounts(Long ownerId, Long mailboxId) {
        folderRamCache.asMap().keySet().removeIf(key ->
                Objects.equals(ownerId, key.ownerId()) && Objects.equals(mailboxId, key.mailboxId()));
    }

    @Autowired
    public MailServiceImpl(GmailAccountRepository gmailAccountRepository,
                           MailConnectionFactory mailConnectionFactory,
                           GmailFolderResolver folderResolver,
                           SystemSettingService systemSettingService,
                           AuditLogService auditLogService,
                           HtmlSanitizerService htmlSanitizer,
                           MailHeaderAndRateGuard headerAndRateGuard) {
        this(gmailAccountRepository, mailConnectionFactory, folderResolver, systemSettingService,
                auditLogService, htmlSanitizer, headerAndRateGuard, false);
    }

    MailServiceImpl(GmailAccountRepository gmailAccountRepository,
                    MailConnectionFactory mailConnectionFactory,
                    GmailFolderResolver folderResolver,
                    SystemSettingService systemSettingService,
                    AuditLogService auditLogService,
                    HtmlSanitizerService htmlSanitizer,
                    MailHeaderAndRateGuard headerAndRateGuard,
                    boolean allowLocalTestExpunge) {
        this.gmailAccountRepository = gmailAccountRepository;
        this.mailConnectionFactory = mailConnectionFactory;
        this.folderResolver = folderResolver;
        this.systemSettingService = systemSettingService;
        this.auditLogService = auditLogService;
        this.htmlSanitizer = htmlSanitizer;
        this.headerAndRateGuard = headerAndRateGuard;
        this.allowLocalTestExpunge = allowLocalTestExpunge;
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
        GmailAccount account = gmailAccountRepository.findByIdAndUserId(gmailId, currentUser.getId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "GMAIL_NOT_OWNED",
                        "Không tìm thấy tài khoản Gmail."));

        boolean explicitAppPasswordWithOAuth = account.getPreferredMethod() == AuthType.APP_PASSWORD
                && account.hasOAuth() && account.hasAppPassword();
        if (account.getStatus() == AccountStatus.NEEDS_REAUTH
                && !account.canFallbackToAppPassword() && !explicitAppPasswordWithOAuth) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "GMAIL_NEEDS_REAUTH",
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
        // A relink or method change may invalidate the credentials while
        // retaining the same mailbox id. Never satisfy the new revision's
        // folder request from a cache populated under the old link.
        FolderCacheKey cacheKey = new FolderCacheKey(currentUser.getId(), gmailId, account.getRevision());
        List<MailFolderDto> cachedFolders = folderRamCache.getIfPresent(cacheKey);
        if (cachedFolders != null) {
            return cachedFolders;
        }

        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            try {
                Store store = imap.getStore();
                Set<String> seenImapNames = new LinkedHashSet<>();

                // Một lệnh LIST("*") duy nhất cho toàn bộ hộp thư: tái dùng cho cả thư mục
                // đặc biệt lẫn custom. Trước đây mỗi resolve() tự LIST riêng (6-7 RTT).
                Folder[] allFolders = store.getDefaultFolder().list("*");

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

                List<MailFolderDto> specialDtos = new ArrayList<>();
                List<Folder> specialFolders = new ArrayList<>();
                List<String[]> specialMeta = new ArrayList<>();
                for (SpecialFolder sf : orderedSpecials) {
                    try {
                        Folder f = folderResolver.resolve(store, allFolders, sf);
                        // Không gọi f.exists(): folder đến từ LIST("*") nên chắc chắn tồn tại, còn mỗi lệnh
                        // exists() tốn thêm ~400ms round-trip tới Gmail.
                        if (f != null && (f.getType() & Folder.HOLDS_MESSAGES) != 0) {
                            String fullName = f.getFullName();
                            if (seenImapNames.add(fullName.toLowerCase(Locale.ROOT))) {
                                specialFolders.add(f);
                                specialMeta.add(new String[]{
                                        canonicalName(sf), fullName, vietnameseDisplayName(sf), canonicalType(sf)});
                            }
                        }
                    } catch (MissingSpecialFolderException ignored) {
                        // Thư mục đặc biệt không tồn tại trên server (VD: GreenMail chỉ có INBOX)
                    }
                }

                // 2. Bổ sung các thư mục tùy chỉnh (CUSTOM) từ cùng kết quả LIST ở trên
                List<Folder> customFolders = new ArrayList<>();
                List<String[]> customMeta = new ArrayList<>();
                for (Folder f : allFolders) {
                        if (f == null || (f.getType() & Folder.HOLDS_MESSAGES) == 0) continue;
                        String fullName = f.getFullName();
                        if (fullName == null || fullName.equalsIgnoreCase("[Gmail]")) continue;
                        if (seenImapNames.add(fullName.toLowerCase(Locale.ROOT))) {
                            String urlSafeName = fullName.contains("/")
                                    ? "b64:" + Base64.getUrlEncoder().withoutPadding().encodeToString(fullName.getBytes(StandardCharsets.UTF_8))
                                    : fullName;
                            customFolders.add(f);
                            customMeta.add(new String[]{urlSafeName, fullName, f.getName(), "CUSTOM"});
                        }
                }

                // 3. Đọc STATUS và chỉ cache khi toàn bộ số thư đã được xác nhận.
                Map<Folder, FolderCountReader.FolderCounts> counts = new LinkedHashMap<>();
                for (Folder f : specialFolders) {
                    counts.put(f, FolderCountReader.read(f));
                }
                for (Folder f : customFolders) {
                    counts.put(f, FolderCountReader.read(f));
                }
                for (int i = 0; i < specialFolders.size(); i++) {
                    String[] meta = specialMeta.get(i);
                    FolderCountReader.FolderCounts c = counts.get(specialFolders.get(i));
                    specialDtos.add(new MailFolderDto(meta[0], meta[1], meta[2], meta[3], c.unread(), c.total()));
                }
                for (int i = 0; i < customFolders.size(); i++) {
                    String[] meta = customMeta.get(i);
                    FolderCountReader.FolderCounts c = counts.get(customFolders.get(i));
                    specialDtos.add(new MailFolderDto(meta[0], meta[1], meta[2], meta[3], c.unread(), c.total()));
                }

                List<MailFolderDto> result = specialDtos;
                folderRamCache.put(cacheKey, List.copyOf(result));
                return result;
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                imap.markBroken();
                throw e;
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể tải danh sách thư mục Gmail.", e);
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
        int configuredSize = size <= 0 ? systemSettingService.getMailPageSize() : size;
        int safeSize = configuredSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(configuredSize, MAX_PAGE_SIZE);
        long offset = (long) safePage * safeSize;

        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            try {
                Folder folder = resolveFolderByName(imap.getStore(), folderName);
                if (folder == null || !folder.exists()) {
                    throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND",
                            "Không tìm thấy thư mục: " + folderName);
                }

                folder.open(Folder.READ_ONLY);
                try {
                    UIDFolder uidFolder = requireUidFolder(folder);
                    long uidValidity = uidFolder.getUIDValidity();

                    List<MailSummaryDto> items = new ArrayList<>();
                    Message[] matched = fetchMatchingMessages(folder, q, unread, from, since);
                    // Sequence number/UID order is not guaranteed to equal the exposed
                    // received date (APPEND/COPY/import can introduce older dates).
                    // Sort globally before taking a page, fetching only envelope+UID
                    // metadata for candidates; BODYSTRUCTURE is fetched separately.
                    List<DatedMessage> ordered = orderByDate(folder, uidFolder, matched);
                    if (hasAttachment == null) {
                        if (offset < ordered.size()) {
                            int start = (int) offset;
                            List<DatedMessage> pageItems = ordered.subList(start, (int) Math.min(offset + safeSize, ordered.size()));
                            Message[] slice = pageItems.stream().map(DatedMessage::message).toArray(Message[]::new);
                            fetchEnvelopeProfile(folder, slice);
                            for (DatedMessage entry : pageItems) {
                                items.add(toSummaryDto(entry, uidValidity, collectAttachmentParts(entry.message()).size()));
                            }
                        }
                        return new MailListResponse(items, safePage, safeSize, ordered.size(),
                                offset + items.size() < ordered.size(), uidValidity);
                    }

                    // BODYSTRUCTURE cho từng lô: đếm đúng mọi thư khớp, kể cả trang sâu
                    // và bộ lọc false; chỉ giữ DTO của trang đang yêu cầu.
                    long total = 0;
                    for (int start = 0; start < ordered.size(); start += MAX_PAGE_SIZE) {
                        List<DatedMessage> batchItems = ordered.subList(start, Math.min(start + MAX_PAGE_SIZE, ordered.size()));
                        Message[] batch = batchItems.stream().map(DatedMessage::message).toArray(Message[]::new);
                        fetchEnvelopeProfile(folder, batch);
                        for (DatedMessage entry : batchItems) {
                            int count = collectAttachmentParts(entry.message()).size();
                            if ((count > 0) != hasAttachment) continue;
                            if (total >= offset && items.size() < safeSize) {
                                items.add(toSummaryDto(entry, uidValidity, count));
                            }
                            total++;
                        }
                    }
                    return new MailListResponse(items, safePage, safeSize, total,
                            offset + items.size() < total, uidValidity);
                } finally {
                    safeCloseFolder(imap, folder, false);
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                imap.markBroken();
                throw e;
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể tải danh sách thư.", e);
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
                                            Long expectedUidValidity,
                                            boolean markSeen) {
        GmailAccount account = loadAndValidateAccount(currentUser, gmailId);
        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            try {
                Folder folder = resolveFolderByName(imap.getStore(), folderName);
                if (folder == null || !folder.exists()) {
                    throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND",
                            "Không tìm thấy thư mục: " + folderName);
                }

                int mode = markSeen ? Folder.READ_WRITE : Folder.READ_ONLY;
                folder.open(mode);
                try {
                    if (folder.getMode() != mode) {
                        throw new MessagingException("IMAP folder did not open in the requested mode");
                    }
                    UIDFolder uidFolder = requireUidFolder(folder);
                    long uidValidity = uidFolder.getUIDValidity();
                    verifyUidValidity(expectedUidValidity, uidValidity);

                    Message msg = uidFolder.getMessageByUID(uid);
                    if (msg == null) {
                        throw new AppException(HttpStatus.NOT_FOUND, "MAIL_NOT_FOUND",
                                "Không tìm thấy thư với UID " + uid + " trong thư mục " + folderName);
                    }

                    // A failed detail read must not implicitly change Seen via BODY fetch.
                    if (msg instanceof IMAPMessage imapMessage) {
                        imapMessage.setPeek(true);
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

                    // Chỉ đọc tương tác mới đánh dấu Seen; preview không sửa cờ.
                    if (markSeen) {
                        if (!msg.isSet(Flags.Flag.SEEN)) {
                            try {
                                msg.setFlag(Flags.Flag.SEEN, true);
                            } finally {
                invalidateFolderCounts(currentUser.getId(), gmailId);
                            }
                        }
                        if (!msg.isSet(Flags.Flag.SEEN)) {
                            throw new MessagingException("IMAP did not confirm Seen");
                        }
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
                                meta.contentId,
                                meta.sizeAccuracy
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
                    return detailResponse;
                } finally {
                    safeCloseFolder(imap, folder, false);
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                imap.markBroken();
                throw e;
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể đọc nội dung thư.", e);
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

        // Kích thước BODYSTRUCTURE là số byte MIME đã encode hoặc có thể chưa biết.
        // Spool tạm theo từng khối, đo byte đã decode trước khi gửi HTTP 200/Content-Length.
        // Không giữ file trong RAM, không lưu thư vào DB; xóa file tạm trên mọi nhánh kết thúc.
        int maxMb = systemSettingService.getMaxAttachmentSizeMb();
        long maxBytes = maxMb > 0 ? (long) maxMb * 1024L * 1024L : Long.MAX_VALUE;
        Path spool = null;
        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            try {
                Folder folder = resolveFolderByName(imap.getStore(), folderName);
                if (folder == null || !folder.exists()) {
                    throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND", "Không tìm thấy thư mục: " + folderName);
                }
                folder.open(Folder.READ_ONLY);
                try {
                    if (folder.getMode() != Folder.READ_ONLY) throw new MessagingException("IMAP folder is not read-only");
                    UIDFolder uidFolder = requireUidFolder(folder);
                    verifyUidValidity(expectedUidValidity, uidFolder.getUIDValidity());

                    Message msg = uidFolder.getMessageByUID(uid);
                    if (msg == null) {
                        throw new AppException(HttpStatus.NOT_FOUND, "MAIL_NOT_FOUND", "Không tìm thấy thư với UID: " + uid);
                    }
                    List<AttachmentPart> parts = collectAttachmentParts(msg);
                    if (attachmentIndex >= parts.size()) {
                        throw new AppException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND",
                                "Không tìm thấy tệp đính kèm ở vị trí #" + attachmentIndex);
                    }
                    AttachmentPart selected = parts.get(attachmentIndex);
                    Part target = selected.part();
                    AttachmentMeta targetMeta = buildAttachmentMeta(target, attachmentIndex);
                    spool = Files.createTempFile("mailapp-download-", ".tmp");
                    // Some IMAP servers expose embedded RFC822 BODYSTRUCTURE but
                    // return empty or wrong BODY[section] for its descendants.
                    // Parse raw MIME only for embedded attachments.
                    long size = selected.embedded() && msg instanceof IMAPMessage imapMessage
                            ? copyEmbeddedAttachment(imapMessage, attachmentIndex, selected.mimePath(), targetMeta, spool, maxBytes, maxMb)
                            : copyDecodedAttachment(target, spool, maxBytes, maxMb);
                    Path ready = spool;
                    Runnable cleanup = () -> deleteDownloadSpool(ready);
                    AttachmentDownloadHandle handle = new AttachmentDownloadHandle(
                            targetMeta.sanitizedFilename,
                            FilenameSanitizer.safeDownloadContentType(targetMeta.contentType),
                            size,
                            out -> {
                                try (InputStream in = Files.newInputStream(ready)) {
                                    in.transferTo(out);
                                    out.flush();
                                } finally {
                                    cleanup.run();
                                }
                            },
                            cleanup);
                    spool = null; // handle sở hữu file kể từ đây
                    return handle;
                } finally {
                    safeCloseFolder(imap, folder, false);
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                imap.markBroken();
                throw e;
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể chuẩn bị tải tệp đính kèm.", e);
        } finally {
            if (spool != null) deleteDownloadSpool(spool);
        }
    }

    private void deleteDownloadSpool(Path spool) {
        try {
            Files.deleteIfExists(spool);
        } catch (IOException e) {
            log.warn("Không thể xóa file tải tạm thời.");
        }
    }

    private long copyDecodedAttachment(Part target, Path spool, long maxBytes, int maxMb) throws Exception {
        long size = 0;
        try (InputStream in = target.getInputStream(); OutputStream out = Files.newOutputStream(spool)) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (read > maxBytes - size) {
                    throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                            "Tệp đính kèm vượt quá kích thước tải tối đa cho phép (" + maxMb + " MB).");
                }
                out.write(buffer, 0, read);
                size += read;
            }
        }
        return size;
    }

    private long copyEmbeddedAttachment(IMAPMessage message, int index, String expectedPath, AttachmentMeta expected,
                                        Path destination, long maxBytes, int maxMb) throws Exception {
        Path rawMime = Files.createTempFile("mailapp-embedded-mime-", ".tmp");
        try {
            // Retrieve the original MIME without relying on nested-part section addressing.
            long copied = 0;
            try (InputStream in = message.getMimeStream(); OutputStream out = Files.newOutputStream(rawMime)) {
                byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (read > MAX_EMBEDDED_MIME_BYTES - copied) {
                        throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                                "Cấu trúc thư lồng vượt giới hạn xử lý MIME.");
                    }
                    out.write(buffer, 0, read);
                    copied += read;
                }
            }
            // Parse file-backed MIME ranges rather than buffering the whole message.
            try (SharedFileInputStream input = new SharedFileInputStream(rawMime.toFile())) {
                MimeMessage parsed = new MimeMessage(Session.getInstance(new Properties()), input);
                List<AttachmentPart> parts = collectAttachmentParts(parsed);
                if (index >= parts.size()) throw new MessagingException("Embedded MIME index mismatch");
                AttachmentPart selected = parts.get(index);
                AttachmentMeta actual = buildAttachmentMeta(selected.part(), index);
                if (!selected.embedded()
                        || !selected.mimePath().equals(expectedPath)
                        || !actual.sanitizedFilename().equals(expected.sanitizedFilename())
                        || !actual.contentType().equalsIgnoreCase(expected.contentType())
                        || !Objects.equals(actual.contentId(), expected.contentId())) {
                    throw new MessagingException("Embedded MIME metadata mismatch");
                }
                return copyDecodedAttachment(selected.part(), destination, maxBytes, maxMb);
            }
        } finally {
            deleteDownloadSpool(rawMime);
        }
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
        validateSendContent(request);

        // MAX_ATTACHMENT_SIZE_MB is a per-file setting; the HTTP request limit
        // separately bounds the aggregate. Also enforce an aggregate ceiling
        // for callers that invoke the service without multipart HTTP parsing.
        int maxMb = systemSettingService.getMaxAttachmentSizeMb();
        if (maxMb <= 0) maxMb = 25;
        long maxBytes = (long) maxMb * 1024L * 1024L;
        long totalAttachBytes = 0L;
        if (attachments != null) {
            for (MultipartFile file : attachments) {
                if (MailMessageBuilder.isAttachment(file)) {
                    long fileSize = file.getSize();
                    if (fileSize < 0 || fileSize > maxBytes) {
                        throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                                "Tệp đính kèm vượt quá giới hạn cho phép (" + maxMb + " MB mỗi tệp).");
                    }
                    if (fileSize > MAX_SEND_ATTACHMENTS_BYTES - totalAttachBytes) {
                        throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                                "Tổng dung lượng tệp đính kèm vượt quá giới hạn yêu cầu (60 MB).");
                    }
                    totalAttachBytes += fileSize;
                }
            }
        }

        // Kiểm tra rate limit gửi thư theo user
        headerAndRateGuard.checkAndRecordSendRate(currentUser.getId());

        boolean submissionStarted = false;
        try (SmtpConnection smtp = mailConnectionFactory.openSmtp(account)) {
            MimeMessage mime = smtp.createMimeMessage();
            MailMessageBuilder.build(mime, account, request, attachments, htmlSanitizer);
            String generatedMessageId = mime.getMessageID();

            int toCount = request.to().size();
            int ccCount = request.cc() != null ? request.cc().size() : 0;
            int bccCount = request.bcc() != null ? request.bcc().size() : 0;
            int recipientCount = toCount + ccCount + bccCount;
            int attachCount = attachments != null ? (int) attachments.stream().filter(MailMessageBuilder::isAttachment).count() : 0;

            // Prepare before the first irreversible operation; no recipient
            // address, subject, body, attachment name or credential in the journal.
            String event = auditLogService.prepareMailMutation(currentUser.getId(), "SEND_MAIL",
                    "Gửi thư từ " + MailCredentials.maskEmail(account.getEmail())
                            + ", gmailId=" + request.gmailId()
                            + ", recipientCount=" + recipientCount + ", attachmentCount=" + attachCount
                            + ", toCount=" + toCount + ", ccCount=" + ccCount + ", bccCount=" + bccCount,
                    clientIp);
            try {
                submissionStarted = true;
                smtp.sendMessage(mime);
            } catch (MessagingException | RuntimeException e) {
                auditLogService.finishMailMutation(event, false, null);
                throw e;
            } finally {
                // A partially accepted SMTP envelope can also change the mailbox.
        invalidateFolderCounts(currentUser.getId(), request.gmailId());
            }
            auditLogService.finishMailMutation(event, true, null);

            return new SendMailResponse(generatedMessageId, "SENT");
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (AddressException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Địa chỉ email không hợp lệ.");
        } catch (SendFailedException e) {
            if (e.getValidSentAddresses() != null && e.getValidSentAddresses().length > 0) {
                throw new MailNetworkException("SMTP đã giao một phần thư. Hãy kiểm tra trước khi gửi lại để tránh trùng lặp.", e);
            }
            if (e.getInvalidAddresses() != null && e.getInvalidAddresses().length > 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL",
                        "Máy chủ SMTP từ chối một hoặc nhiều địa chỉ người nhận.");
            }
            throw new MailNetworkException("Chưa xác định được kết quả gửi thư. Hãy kiểm tra trước khi gửi lại.", e);
        } catch (AuthenticationFailedException e) {
            throw new MailAuthException("Máy chủ SMTP không chấp nhận thông tin xác thực.", e);
        } catch (Exception e) {
            throw new MailNetworkException(submissionStarted
                    ? "Chưa xác định được kết quả gửi thư. Hãy kiểm tra trước khi gửi lại."
                    : "Không thể chuẩn bị gửi thư qua SMTP.", e);
        }
    }

    private void validateSendContent(SendMailRequest request) {
        String subject = request.subject();
        if (subject != null && subject.codePointCount(0, subject.length()) > MAX_SUBJECT_CHARS) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Tiêu đề thư tối đa 200 ký tự.");
        }
        long size = 0;
        for (String body : new String[]{request.textBody(), request.htmlBody()}) {
            if (body == null) continue;
            if (body.length() > MAX_SEND_BODY_BYTES) {
                throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Nội dung thư tối đa 1 MB.");
            }
            size += body.getBytes(StandardCharsets.UTF_8).length;
            if (size > MAX_SEND_BODY_BYTES) {
                throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Nội dung thư tối đa 1 MB.");
            }
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
            try {
                Store store = imap.getStore();
                Folder source = resolveFolderByName(store, folderName);
                if (source == null || !source.exists()) {
                    throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND", "Không tìm thấy thư mục: " + folderName);
                }

                boolean isTrashFolder = isTrash(store, source);
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
                    if (source.getMode() != Folder.READ_WRITE) throw new MessagingException("IMAP folder is read-only");
                    UIDFolder uidFolder = requireUidFolder(source);
                    long uidValidity = uidFolder.getUIDValidity();
                    verifyUidValidity(expectedUidValidity, uidValidity);

                    Message msg = uidFolder.getMessageByUID(uid);
                    if (msg == null) {
                        throw new AppException(HttpStatus.NOT_FOUND, "MAIL_NOT_FOUND", "Không tìm thấy thư với UID: " + uid);
                    }

                    // UID EXPUNGE targets only this message, not every message that
                    // another client might already have marked Deleted.
                    boolean targetedExpunge = source instanceof IMAPFolder && store instanceof IMAPStore imapStore
                            && imapStore.hasCapability("UIDPLUS");
                    boolean move = !permanent && source instanceof IMAPFolder && store instanceof IMAPStore imapStore
                            && imapStore.hasCapability("MOVE");
                    if (!move && !targetedExpunge) {
                        // Non-UIDPLUS servers cannot promise targeted expunge. This
                        // exception exists only in disposable GreenMail fixtures.
                        String host = store.getURLName() == null ? null : store.getURLName().getHost();
                        if (!allowLocalTestExpunge || (!"localhost".equalsIgnoreCase(host)
                                && !"127.0.0.1".equals(host) && !"::1".equals(host))) {
                            throw new MessagingException("Server does not support UID EXPUNGE");
                        }
                        assertOnlyTargetDeleted(source, msg);
                    }
                    boolean wasDeleted = !move && msg.isSet(Flags.Flag.DELETED);
                    String event = auditLogService.prepareMailMutation(currentUser.getId(), "MAIL_DELETE",
                            String.format("Xóa thư (permanent=%s) gmailId=%d, folder=%s, uid=%d, uidValidity=%d",
                                    permanent, gmailId, source.getFullName(), uid, uidValidity), clientIp);
                    boolean mutationAttempted = false;
                    try {
                        // Non-Gmail fallback may CREATE a folder. Keep it behind
                        // the same durable intent as COPY/MOVE/STORE/EXPUNGE.
                        if (!permanent) mutationAttempted = true;
                        Folder trash = permanent ? null : resolveOrCreateTrashFolder(store);
                        if (move) {
                            // MOVE is server-side; a lost reply is ambiguous, so do
                            // not fall back to COPY or retry the operation.
                            mutationAttempted = true;
                            moveByUid((IMAPFolder) source, uid, trash);
                        } else {
                            if (!permanent) {
                                mutationAttempted = true;
                                source.copyMessages(new Message[]{msg}, trash);
                            }
                            mutationAttempted = true;
                            try {
                                msg.setFlag(Flags.Flag.DELETED, true);
                                if (targetedExpunge) {
                                    ((IMAPFolder) source).expunge(new Message[]{msg});
                                } else {
                                    assertOnlyTargetDeleted(source, msg);
                                    source.expunge();
                                }
                            } catch (MessagingException | RuntimeException e) {
                                // STORE may have succeeded before its reply was lost.
                                // Best effort only: restore this flag if the source
                                // is still known to exist. Never expunge other mail.
                                if (!wasDeleted && !msg.isExpunged()) {
                                    try { msg.setFlag(Flags.Flag.DELETED, false); }
                                    catch (MessagingException | RuntimeException ignored) {}
                                }
                                throw e;
                            }
                        }
                    } catch (MessagingException | RuntimeException e) {
                        auditLogService.finishMailMutation(event, false, null);
                        if (mutationAttempted) {
                            imap.markBroken();
                            invalidateFolderCounts(currentUser.getId(), gmailId);
                            throw new MailNetworkException("Không xác định được kết quả xóa thư. Hãy tải lại thư mục nguồn và Thùng rác trước khi thao tác lại.", e);
                        }
                        throw e;
                    }

                    auditLogService.finishMailMutation(event, true, null);

                    invalidateFolderCounts(currentUser.getId(), gmailId);

                    String resultMsg = permanent
                            ? "Đã xóa vĩnh viễn thư khỏi Thùng rác."
                            : "Đã chuyển thư vào Thùng rác.";
                    return Map.of("status", "DELETED", "permanent", permanent, "message", resultMsg);
                } finally {
                    safeCloseFolder(imap, source, false);
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                imap.markBroken();
                throw e;
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể xóa thư.", e);
        }
    }

    private void assertOnlyTargetDeleted(Folder folder, Message target) throws MessagingException {
        UIDFolder uidFolder = requireUidFolder(folder);
        long targetUid = uidFolder.getUID(target);
        for (Message deleted : folder.search(new FlagTerm(new Flags(Flags.Flag.DELETED), true))) {
            if (uidFolder.getUID(deleted) != targetUid) {
                throw new MessagingException("Cannot expunge safely: unrelated messages are marked Deleted");
            }
        }
    }

    private void moveByUid(IMAPFolder source, long uid, Folder trash) throws MessagingException {
        // Angus moveMessages/moveUIDMessages both issue sequence-number MOVE;
        // the latter returns destination UIDs rather than issuing UID MOVE.
        // Execute the UID form under the selected-folder protocol lock.
        source.doCommand(protocol -> {
            Argument args = new Argument();
            args.writeAtom(Long.toString(uid));
            String destination = trash.getFullName();
            if (protocol.supportsUtf8()) {
                args.writeString(destination, StandardCharsets.UTF_8);
            } else {
                args.writeString(BASE64MailboxEncoder.encode(destination));
            }
            protocol.simpleCommand("UID MOVE", args);
            return null;
        });
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
                                    Long expectedUidValidity,
                                    String clientIp) {
        GmailAccount account = loadAndValidateAccount(currentUser, gmailId);
        if (request == null || (request.seen() == null && request.flagged() == null)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Vui lòng truyền ít nhất một cờ cần cập nhật (seen hoặc flagged).");
        }

        try (ImapConnection imap = mailConnectionFactory.openImap(account)) {
            try {
                Folder folder = resolveFolderByName(imap.getStore(), folderName);
                if (folder == null || !folder.exists()) {
                    throw new AppException(HttpStatus.NOT_FOUND, "FOLDER_NOT_FOUND", "Không tìm thấy thư mục: " + folderName);
                }

                folder.open(Folder.READ_WRITE);
                try {
                    if (folder.getMode() != Folder.READ_WRITE) throw new MessagingException("IMAP folder is read-only");
                    UIDFolder uidFolder = requireUidFolder(folder);
                    long uidValidity = uidFolder.getUIDValidity();
                    verifyUidValidity(expectedUidValidity, uidValidity);

                    Message msg = uidFolder.getMessageByUID(uid);
                    if (msg == null) {
                        throw new AppException(HttpStatus.NOT_FOUND, "MAIL_NOT_FOUND", "Không tìm thấy thư với UID: " + uid);
                    }

                    String auditIdentity = String.format("gmailId=%d, folder=%s, uid=%d, uidValidity=%d",
                            gmailId, folder.getFullName(), uid, uidValidity);
                    String event = auditLogService.prepareMailMutation(currentUser.getId(), "MAIL_FLAG_CHANGE",
                            auditIdentity + ", requestedStarred=" + request.flagged() + ", requestedSeen=" + request.seen(),
                            clientIp);
                    MailFlagsDto flags;
                    try {
                        if (request.seen() != null) {
                            msg.setFlag(Flags.Flag.SEEN, request.seen());
                        }
                        if (request.flagged() != null) {
                            msg.setFlag(Flags.Flag.FLAGGED, request.flagged());
                        }
                        flags = extractFlags(msg);
                        if ((request.seen() != null && request.seen() != flags.seen())
                                || (request.flagged() != null && request.flagged() != flags.flagged())) {
                            throw new MessagingException("IMAP did not confirm requested flags");
                        }
                    } catch (MessagingException | RuntimeException e) {
                        auditLogService.finishMailMutation(event, false, null);
                        imap.markBroken();
                        throw new MailNetworkException("Không xác định được kết quả cập nhật cờ thư. Hãy tải lại danh sách trước khi thao tác lại.", e);
                    } finally {
                        // Even if only the first STORE succeeded or its reply was lost,
                        // cached unread counts can no longer be trusted.
                invalidateFolderCounts(currentUser.getId(), gmailId);
                    }
                    auditLogService.finishMailMutation(event, true,
                            auditIdentity + ", starred=" + flags.flagged() + ", seen=" + flags.seen());
                    return flags;
                } finally {
                    safeCloseFolder(imap, folder, false);
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                imap.markBroken();
                throw e;
            }
        } catch (AppException | MailConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new MailNetworkException("Không thể cập nhật cờ thư.", e);
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
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(trimmed.substring(4)), StandardCharsets.UTF_8);
                return store.getFolder(decoded);
            } catch (IllegalArgumentException e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tên thư mục không hợp lệ.");
            }
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
            } catch (MissingSpecialFolderException e) {
                // Fallback: kiểm tra trực tiếp tên thư mục (VD: "Trash" trên GreenMail)
                Folder direct = store.getFolder(trimmed);
                if (direct != null && direct.exists()) {
                    return direct;
                }
                return null;
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
        } catch (MissingSpecialFolderException ignored) {}

        if (isGmailStore(store)) {
            throw new MessagingException("Gmail Trash folder is unavailable");
        }

        Folder fallback = store.getFolder("Trash");
        if (!fallback.exists()) {
            if (!fallback.create(Folder.HOLDS_MESSAGES)) {
                throw new MessagingException("Cannot create Trash folder");
            }
        }
        return fallback;
    }

    private boolean isTrash(Store store, Folder source) throws MessagingException {
        try {
            Folder resolvedTrash = folderResolver.resolve(store, SpecialFolder.TRASH);
            return resolvedTrash != null && resolvedTrash.getFullName().equalsIgnoreCase(source.getFullName());
        } catch (MissingSpecialFolderException ignored) {
            return false;
        }
    }

    private Message[] fetchMatchingMessages(Folder folder,
                                            String q,
                                            Boolean unread,
                                            String from,
                                            String since) throws MessagingException {
        Date sinceDate = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceDate = Date.from(LocalDate.parse(since.trim()).atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (java.time.format.DateTimeParseException e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Định dạng tham số 'since' không hợp lệ (cần yyyy-MM-dd).");
            }
        }
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
                log.debug("X-GM-RAW không khả dụng, chuyển sang SearchTerm chuẩn.");
            }
        }

        // Fallback SearchTerm chuẩn (chạy ổn định với mọi IMAP server kể cả GreenMail)
        List<SearchTerm> terms = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String kw = q.trim();
            terms.add(new OrTerm(new SearchTerm[]{
                    new SubjectTerm(kw),
                    new FromStringTerm(kw)
            }));
        }
        if (unread != null) {
            terms.add(new FlagTerm(new Flags(Flags.Flag.SEEN), !unread));
        }
        if (from != null && !from.isBlank()) {
            terms.add(new FromStringTerm(from.trim()));
        }
        if (sinceDate != null) terms.add(new ReceivedDateTerm(ComparisonTerm.GE, sinceDate));

        if (terms.isEmpty()) {
            return folder.getMessages();
        }
        SearchTerm combined = terms.size() == 1 ? terms.get(0) : new AndTerm(terms.toArray(new SearchTerm[0]));
        try {
            return folder.search(combined);
        } catch (MessagingException e) {
            // Một số máy chủ IMAP (như GreenMail) không hỗ trợ SEARCH với CHARSET UTF-8 -> fallback lọc theo ENVELOPE/FLAGS
            log.debug("IMAP SEARCH bị máy chủ từ chối, chuyển sang lọc cục bộ theo ENVELOPE/FLAGS.");
            return filterMessagesByEnvelope(folder, q, unread, from, since);
        }
    }

    private Message[] filterMessagesByEnvelope(Folder folder,
                                               String q,
                                               Boolean unread,
                                               String from,
                                               String since) throws MessagingException {
        Message[] all = folder.getMessages();
        // Fallback search needs sender/date/subject and flags, not MIME structure.
        for (int start = 0; start < all.length; start += DATE_METADATA_BATCH_SIZE) {
            Message[] batch = Arrays.copyOfRange(all, start, Math.min(start + DATE_METADATA_BATCH_SIZE, all.length));
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.FLAGS);
            fp.add(UIDFolder.FetchProfileItem.UID);
            folder.fetch(batch, fp);
        }

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
                Date d = messageDate(m);
                if (d == null || d.before(sinceDate)) continue;
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

    private record DatedMessage(Message message, long uid, Date date) {}

    private List<DatedMessage> orderByDate(Folder folder, UIDFolder uidFolder, Message[] matched)
            throws MessagingException {
        List<DatedMessage> ordered = new ArrayList<>(matched.length);
        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(UIDFolder.FetchProfileItem.UID);
        for (int start = 0; start < matched.length; start += DATE_METADATA_BATCH_SIZE) {
            Message[] batch = Arrays.copyOfRange(matched, start, Math.min(start + DATE_METADATA_BATCH_SIZE, matched.length));
            folder.fetch(batch, fp);
            for (Message msg : batch) {
                long uid = uidFolder.getUID(msg);
                if (uid <= 0) throw new MessagingException("IMAP message UID unavailable");
                ordered.add(new DatedMessage(msg, uid, messageDate(msg)));
            }
        }
        ordered.sort(Comparator.comparing(DatedMessage::date, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparingLong(DatedMessage::uid).reversed()));
        return ordered;
    }

    private MailSummaryDto toSummaryDto(DatedMessage entry, long uidValidity, int attachCount) throws MessagingException {
        Message msg = entry.message();

        return new MailSummaryDto(
                entry.uid(),
                uidValidity,
                extractFirstAddress(msg.getFrom()),
                extractAddresses(msg.getRecipients(Message.RecipientType.TO)),
                decodeSubject(msg),
                "", // Không tải thân thư khi liệt kê (chỉ snippet khi rẻ)
                entry.date() != null ? entry.date().toInstant().toString() : null,
                extractFlags(msg),
                Math.max(0, msg.getSize()),
                attachCount > 0,
                attachCount
        );
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
            String contentId,
            AttachmentSizeAccuracy sizeAccuracy
    ) {}

    private ParsedMailContent parseMessageContent(Message msg) throws Exception {
        ParsedMailContent parsed = new ParsedMailContent();
        walkMime(msg, 0, false, "root", (part, kind, embedded, mimePath) -> {
            switch (kind) {
                case ATTACHMENT -> parsed.attachments.add(buildAttachmentMeta(part, parsed.attachments.size()));
                case TEXT -> parsed.textBody = appendBody(parsed.textBody, part);
                case HTML -> parsed.htmlBody = appendBody(parsed.htmlBody, part);
            }
        });
        return parsed;
    }

    private record AttachmentPart(Part part, boolean embedded, String mimePath) {}

    private List<AttachmentPart> collectAttachmentParts(Message msg) throws Exception {
        List<AttachmentPart> result = new ArrayList<>();
        walkMime(msg, 0, false, "root", (part, kind, embedded, mimePath) -> {
            if (kind == MimeLeaf.ATTACHMENT) result.add(new AttachmentPart(part, embedded, mimePath));
        });
        return result;
    }

    private enum MimeLeaf { TEXT, HTML, ATTACHMENT }

    @FunctionalInterface
    private interface MimeVisitor {
        void visit(Part part, MimeLeaf kind, boolean embedded, String mimePath) throws Exception;
    }

    /**
     * Một quy tắc duy nhất cho danh sách, chi tiết và download. Với IMAP,
     * getContent() trên container dùng BODYSTRUCTURE; không đọc leaf body
     * khi chỉ đếm attachment hoặc định vị index.
     */
    private void walkMime(Part part, int depth, boolean embedded, String mimePath, MimeVisitor visitor) throws Exception {
        if (part == null) return;
        if (depth > 64) throw new MessagingException("MIME nesting exceeds limit");
        String type = part.getContentType();
        String lower = type == null ? "application/octet-stream" : type.toLowerCase(Locale.ROOT);
        String disposition = part.getDisposition();
        String filename = part.getFileName();
        boolean text = lower.startsWith("text/plain");
        boolean html = lower.startsWith("text/html");
        boolean multipart = lower.startsWith("multipart/");

        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (filename != null && !filename.isBlank())
                || (Part.INLINE.equalsIgnoreCase(disposition) && !text && !html && !multipart)) {
            visitor.visit(part, MimeLeaf.ATTACHMENT, embedded, mimePath);
        } else if (multipart) {
            Object content = part.getContent();
            if (!(content instanceof Multipart mp)) throw new MessagingException("Malformed MIME multipart");
            for (int i = 0; i < mp.getCount(); i++) {
                walkMime(mp.getBodyPart(i), depth + 1, embedded, mimePath + "/" + i, visitor);
            }
        } else if (lower.startsWith("message/rfc822")) {
            Object nested = part.getContent();
            if (!(nested instanceof Part nestedPart)) throw new MessagingException("Malformed embedded message");
            walkMime(nestedPart, depth + 1, true, mimePath + "/message", visitor);
        } else if (text) {
            visitor.visit(part, MimeLeaf.TEXT, embedded, mimePath);
        } else if (html) {
            visitor.visit(part, MimeLeaf.HTML, embedded, mimePath);
        } else {
            visitor.visit(part, MimeLeaf.ATTACHMENT, embedded, mimePath);
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
        AttachmentSizeAccuracy accuracy = AttachmentSizeAccuracy.UNKNOWN;
        try {
            long rawSize = part.getSize();
            if (rawSize >= 0) {
                String[] encHeaders = part.getHeader("Content-Transfer-Encoding");
                boolean isBase64 = encHeaders != null && encHeaders.length > 0
                        && encHeaders[0] != null && encHeaders[0].trim().equalsIgnoreCase("base64");
                // BODYSTRUCTURE gives encoded octets; line breaks and padding
                // prevent an exact decoded length without reading the content.
                long compactSize = rawSize - 2L * (rawSize / 78L);
                size = isBase64 ? compactSize * 3L / 4L : rawSize;
                accuracy = AttachmentSizeAccuracy.ESTIMATED;
            }
        } catch (Exception ignored) {}

        return new AttachmentMeta(sanitized, cleanContentType, size, inline, contentId, accuracy);
    }

    private String appendBody(String existing, Part part) throws Exception {
        int limit = MAX_TEXT_CHARS + 1; // +1 để nhận biết nội dung đã bị cắt
        if (existing.length() >= limit) return existing;
        StringBuilder body = new StringBuilder(existing);
        Charset charset = extractCharsetOrUtf8(part.getContentType());
        try (Reader reader = new InputStreamReader(part.getInputStream(), charset)) {
            char[] buffer = new char[4096];
            int read = reader.read(buffer, 0, Math.min(buffer.length, limit - body.length()));
            if (read < 0) return existing;
            if (!body.isEmpty()) body.append('\n');
            while (read >= 0 && body.length() < limit) {
                body.append(buffer, 0, Math.min(read, limit - body.length()));
                if (body.length() == limit) break;
                read = reader.read(buffer, 0, Math.min(buffer.length, limit - body.length()));
            }
        }
        return body.toString();
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

    private void safeCloseFolder(ImapConnection imap, Folder folder, boolean expunge) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(expunge);
            }
        } catch (Exception e) {
            imap.markBroken();
        }
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

    private Date messageDate(Message msg) throws MessagingException {
        Date received = msg.getReceivedDate();
        return received != null ? received : msg.getSentDate();
    }

    private String formatDate(Message msg) {
        try {
            Date d = messageDate(msg);
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

    private MailFlagsDto extractFlags(Message msg) throws MessagingException {
        Flags flags = msg.getFlags();
        if (flags == null) throw new MessagingException("IMAP flags unavailable");
        return new MailFlagsDto(
                flags.contains(Flags.Flag.SEEN),
                flags.contains(Flags.Flag.FLAGGED),
                flags.contains(Flags.Flag.ANSWERED),
                flags.contains(Flags.Flag.DRAFT)
        );
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
