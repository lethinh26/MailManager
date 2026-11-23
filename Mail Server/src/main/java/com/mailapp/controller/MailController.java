package com.mailapp.controller;

import com.mailapp.dto.mail.MailDtos.*;
import com.mailapp.mail.service.FilenameSanitizer;
import com.mailapp.mail.service.MailService;
import com.mailapp.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

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
            @RequestParam(defaultValue = "30") int size,
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
    @Operation(summary = "Đọc chi tiết 1 thư (HTML đã làm sạch, chặn ảnh từ xa mặc định, tự đánh dấu Seen)")
    public ResponseEntity<MailDetailResponse> getMailDetail(
            @PathVariable Long gmailId,
            @PathVariable String folder,
            @PathVariable long uid,
            @RequestParam(defaultValue = "false") boolean loadRemote,
            @RequestParam(required = false) Long uidValidity,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(mailService.getMailDetail(
                currentUser, gmailId, folder, uid, loadRemote, uidValidity));
    }

    @GetMapping("/{gmailId}/{folder}/{uid}/attachments/{index}/download")
    @Operation(summary = "Tải tệp đính kèm dạng stream (không nạp toàn bộ vào RAM)")
    public ResponseEntity<StreamingResponseBody> downloadAttachment(
            @PathVariable Long gmailId,
            @PathVariable String folder,
            @PathVariable long uid,
            @PathVariable int index,
            @RequestParam(required = false) Long uidValidity,
            @AuthenticationPrincipal User currentUser) {

        MailService.AttachmentDownloadHandle handle = mailService.prepareAttachmentDownload(
                currentUser, gmailId, folder, uid, index, uidValidity);

        StreamingResponseBody body = out -> {
            try {
                handle.streamer().streamTo(out);
            } catch (Exception e) {
                throw new RuntimeException("Lỗi khi stream tệp đính kèm: " + e.getMessage(), e);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, FilenameSanitizer.buildContentDisposition(handle.sanitizedFilename()))
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(handle.contentType()))
                .body(body);
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Gửi thư mới kèm tệp đính kèm qua SMTP")
    public ResponseEntity<SendMailResponse> sendMail(
            @RequestPart("payload") SendMailRequest payload,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {
        return ResponseEntity.ok(mailService.sendMail(currentUser, payload, attachments, request.getRemoteAddr()));
    }

    @DeleteMapping("/{gmailId}/{folder}/{uid}")
    @Operation(summary = "Xóa thư (mặc định chuyển Thùng rác; permanent=true chỉ hợp lệ khi đang ở Thùng rác)")
    public ResponseEntity<Map<String, Object>> deleteMail(
            @PathVariable Long gmailId,
            @PathVariable String folder,
            @PathVariable long uid,
            @RequestParam(defaultValue = "false") boolean permanent,
            @RequestParam(required = false) Long uidValidity,
            @AuthenticationPrincipal User currentUser,
            HttpServletRequest request) {
        return ResponseEntity.ok(mailService.deleteMail(
                currentUser, gmailId, folder, uid, permanent, uidValidity, request.getRemoteAddr()));
    }

    @PutMapping("/{gmailId}/{folder}/{uid}/flags")
    @Operation(summary = "Cập nhật cờ Seen / Flagged của thư (M17)")
    public ResponseEntity<MailFlagsDto> updateFlags(
            @PathVariable Long gmailId,
            @PathVariable String folder,
            @PathVariable long uid,
            @RequestBody UpdateFlagsRequest body,
            @RequestParam(required = false) Long uidValidity,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(mailService.updateFlags(
                currentUser, gmailId, folder, uid, body, uidValidity));
    }
}

