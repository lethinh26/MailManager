package com.mailapp.controller;

import com.mailapp.dto.mail.MailDtos.*;
import com.mailapp.exception.AppException;
import com.mailapp.mail.service.FilenameSanitizer;
import com.mailapp.mail.service.MailService;
import com.mailapp.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;

/**
 * REST Controller Hộp thư thật (M08, M10, M12, M16, M17).
 * Xem hợp đồng chi tiết tại docs/api-mail-contract.md.
 */
@RestController
@RequestMapping("/api/mails")
@Tag(name = "Mail Operations", description = "Đọc thư, tìm kiếm, tải đính kèm, gửi thư, xóa thư và cập nhật cờ IMAP/SMTP")
public class MailController {

    private final MailService mailService;

    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    @GetMapping("/folders")
    @Operation(summary = "Danh sách thư mục của tài khoản Gmail")
    public ResponseEntity<List<MailFolderDto>> listFolders(
            @RequestParam(required = false) Long gmailId,
            @RequestParam(required = false) Long gmailAccountId,
            @AuthenticationPrincipal User currentUser) {
        Long effectiveId = gmailId != null ? gmailId : gmailAccountId;
        return ResponseEntity.ok(mailService.listFolders(currentUser, effectiveId));
    }

    @GetMapping
    @Operation(summary = "Liệt kê và tìm kiếm thư theo phân trang UID (mới nhất trước)")
    public ResponseEntity<MailListResponse> listMails(
            @RequestParam(required = false) Long gmailId,
            @RequestParam(required = false) Long gmailAccountId,
            @RequestParam(defaultValue = "INBOX") String folder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) Boolean hasAttachment,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String since,
            @AuthenticationPrincipal User currentUser) {
        Long effectiveId = gmailId != null ? gmailId : gmailAccountId;
        return ResponseEntity.ok(mailService.listMails(
                currentUser, effectiveId, folder, page, size, q, unread, hasAttachment, from, since));
    }

    @GetMapping("/{gmailId}/{folder}/{uid}")
    @Operation(summary = "Đọc chi tiết 1 thư (HTML đã làm sạch, chặn ảnh từ xa mặc định, markSeen=false để xem trước)")
    public ResponseEntity<MailDetailResponse> getMailDetail(
            @PathVariable Long gmailId,
            @PathVariable String folder,
            @PathVariable long uid,
            @RequestParam(defaultValue = "false") boolean loadRemote,
            @RequestParam(required = false) Long uidValidity,
            @RequestParam(defaultValue = "true") boolean markSeen,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(markSeen
                ? mailService.getMailDetail(currentUser, gmailId, folder, uid, loadRemote, uidValidity)
                : mailService.getMailDetail(currentUser, gmailId, folder, uid, loadRemote, uidValidity, false));
    }

    @GetMapping("/{gmailId}/{folder}/{uid}/attachments/{index}/download")
    @Operation(summary = "Tải tệp đính kèm dạng stream (không nạp toàn bộ vào RAM)")
    public void downloadAttachment(
            @PathVariable Long gmailId,
            @PathVariable String folder,
            @PathVariable long uid,
            @PathVariable int index,
            @RequestParam(required = false) Long uidValidity,
            @AuthenticationPrincipal User currentUser,
            HttpServletResponse response) throws Exception {
        // Spring dùng virtual thread; giữ vòng đời file tạm trong cùng request.
        try (MailService.AttachmentDownloadHandle handle = mailService.prepareAttachmentDownload(
                currentUser, gmailId, folder, uid, index, uidValidity)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, FilenameSanitizer.buildContentDisposition(handle.sanitizedFilename()));
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setContentType(handle.contentType());
            response.setContentLengthLong(handle.estimatedSize());
            handle.streamer().streamTo(response.getOutputStream());
        }
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Gửi thư mới kèm tệp đính kèm qua SMTP")
    public ResponseEntity<SendMailResponse> sendMail(
            @RequestPart(value = "payload", required = false) SendMailRequest payload,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "files[]", required = false) List<MultipartFile> bracketFiles,
            @RequestParam MultiValueMap<String, String> fields,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {
        if (payload == null) {
            payload = legacySendPayload(fields);
        } else if (fields.keySet().stream().anyMatch(LEGACY_SEND_FIELDS::contains)) {
            throw invalidForm("Không trộn payload JSON với các trường gửi thư dạng form.");
        }
        List<MultipartFile> allAttachments = new ArrayList<>();
        if (attachments != null) allAttachments.addAll(attachments);
        if (files != null) allAttachments.addAll(files);
        if (bracketFiles != null) allAttachments.addAll(bracketFiles);
        return ResponseEntity.ok(mailService.sendMail(currentUser, payload, allAttachments, request.getRemoteAddr()));
    }

    private static final Set<String> LEGACY_SEND_FIELDS = Set.of(
            "gmailId", "gmailAccountId", "to", "cc", "bcc", "subject", "body", "isHtml", "inReplyTo", "references");

    private static SendMailRequest legacySendPayload(MultiValueMap<String, String> fields) {
        String modernId = singleFormField(fields, "gmailId");
        String oldId = singleFormField(fields, "gmailAccountId");
        Long gmailId;
        try {
            gmailId = modernId == null ? (oldId == null ? null : Long.valueOf(oldId)) : Long.valueOf(modernId);
            if (modernId != null && oldId != null && !gmailId.equals(Long.valueOf(oldId))) {
                throw invalidForm("ID hộp thư không nhất quán.");
            }
        } catch (NumberFormatException ex) {
            throw invalidForm("ID hộp thư không hợp lệ.");
        }
        if (gmailId == null) throw invalidForm("Thiếu payload hoặc gmailAccountId.");
        String html = singleFormField(fields, "isHtml");
        if (html != null && !"true".equalsIgnoreCase(html) && !"false".equalsIgnoreCase(html)) {
            throw invalidForm("isHtml phải là true hoặc false.");
        }
        boolean isHtml = Boolean.parseBoolean(html);
        String body = singleFormField(fields, "body");
        return new SendMailRequest(gmailId, formRecipients(fields, "to"), formRecipients(fields, "cc"),
                formRecipients(fields, "bcc"), singleFormField(fields, "subject"),
                isHtml ? null : body, isHtml ? body : null,
                singleFormField(fields, "inReplyTo"), singleFormField(fields, "references"));
    }

    private static String singleFormField(MultiValueMap<String, String> fields, String key) {
        List<String> values = fields.get(key);
        if (values == null || values.isEmpty()) return null;
        if (values.size() != 1) throw invalidForm("Trường " + key + " chỉ được truyền một lần.");
        return values.getFirst();
    }

    private static List<String> formRecipients(MultiValueMap<String, String> fields, String key) {
        List<String> values = fields.get(key);
        if (values == null || values.isEmpty()) return List.of();
        List<String> recipients = new ArrayList<>();
        for (String value : values) {
            // Never strip CR/LF before the common MailHeaderAndRateGuard sees it.
            if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "HEADER_INJECTION_DETECTED",
                        "Địa chỉ email chứa ký tự điều khiển.");
            }
            if (value.isBlank()) continue;
            for (String address : value.split("[,;]", -1)) recipients.add(address.trim());
        }
        return recipients;
    }

    private static AppException invalidForm(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @DeleteMapping("/{gmailId}/{folder}/{uid}")
    @Operation(summary = "Xóa thư (mặc định chuyển Thùng rác; permanent=true chỉ hợp lệ khi đang ở Thùng rác)")
    public ResponseEntity<Map<String, Object>> deleteMail(
            @PathVariable Long gmailId,
            @PathVariable String folder,
            @PathVariable long uid,
            @RequestParam(defaultValue = "false") boolean permanent,
            @RequestParam(required = false) Long uidValidity,
            @RequestHeader(value = "Prefer", required = false) String prefer,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {
        Map<String, Object> result = mailService.deleteMail(
                currentUser, gmailId, folder, uid, permanent, uidValidity, request.getRemoteAddr());
        if (prefer != null && java.util.Arrays.stream(prefer.split(","))
                .map(String::trim).anyMatch("return=representation"::equalsIgnoreCase)) {
            return ResponseEntity.ok().header("Preference-Applied", "return=representation").body(result);
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{gmailId}/{folder}/{uid}/flags")
    @Operation(summary = "Cập nhật cờ Seen / Flagged của thư (M17)")
    public ResponseEntity<UpdateFlagsResponse> updateFlags(
            @PathVariable Long gmailId,
            @PathVariable String folder,
            @PathVariable long uid,
            @RequestBody UpdateFlagsRequest body,
            @RequestParam(required = false) Long uidValidity,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {
        return ResponseEntity.ok(UpdateFlagsResponse.from(uid, mailService.updateFlags(
                currentUser, gmailId, folder, uid, body, uidValidity, request.getRemoteAddr())));
    }
}

