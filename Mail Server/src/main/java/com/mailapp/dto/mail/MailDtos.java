package com.mailapp.dto.mail;

import java.util.List;

/**
 * Các DTO chuẩn cho API Hộp thư thật (M08, M10, M12, M16, M17).
 * Xem hợp đồng chi tiết tại docs/api-mail-contract.md.
 */
public final class MailDtos {

    private MailDtos() {}

    public record MailAddressDto(String name, String address) {}

    public record MailFlagsDto(boolean seen, boolean flagged, boolean answered, boolean draft) {}

    public record MailFolderDto(
            String name,
            String imapName,
            String displayName,
            String type,
            int unread,
            int total
    ) {}

    public record MailSummaryDto(
            long uid,
            long uidValidity,
            MailAddressDto from,
            List<MailAddressDto> to,
            String subject,
            String snippet,
            String date,
            MailFlagsDto flags,
            long size,
            boolean hasAttachment,
            int attachmentCount
    ) {}

    public record MailListResponse(
            List<MailSummaryDto> items,
            int page,
            int size,
            long total,
            boolean hasMore,
            long uidValidity
    ) {}

    public record MailAttachmentDto(
            int index,
            String filename,
            String contentType,
            long size,
            boolean inline,
            String contentId
    ) {}

    public record MailDetailResponse(
            long uid,
            long uidValidity,
            MailAddressDto from,
            List<MailAddressDto> to,
            List<MailAddressDto> cc,
            List<MailAddressDto> bcc,
            List<MailAddressDto> replyTo,
            String date,
            String messageId,
            String inReplyTo,
            String references,
            String subject,
            String textBody,
            String htmlBody,
            boolean truncated,
            boolean hasBlockedRemoteImages,
            List<MailAttachmentDto> attachments,
            MailFlagsDto flags
    ) {}

    public record SendMailRequest(
            Long gmailId,
            List<String> to,
            List<String> cc,
            List<String> bcc,
            String subject,
            String textBody,
            String htmlBody,
            String inReplyTo,
            String references
    ) {}

    public record SendMailResponse(
            String messageId,
            String status
    ) {}

    public record UpdateFlagsRequest(
            Boolean seen,
            Boolean flagged
    ) {}
}

