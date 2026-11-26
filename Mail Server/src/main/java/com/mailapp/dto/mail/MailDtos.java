package com.mailapp.dto.mail;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
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
    ) {
        @JsonProperty("isRead")
        public boolean isRead() { return flags != null && flags.seen(); }
    }

    public record MailListResponse(
            List<MailSummaryDto> items,
            int page,
            int size,
            long total,
            boolean hasMore,
            long uidValidity
    ) {
        @JsonProperty("content")
        public List<MailSummaryDto> content() { return items; }

        @JsonProperty("totalElements")
        public long totalElements() { return total; }

        @JsonProperty("totalPages")
        public long totalPages() {
            return size <= 0 || total <= 0 ? 0 : 1 + (total - 1) / size;
        }
    }

    public enum AttachmentSizeAccuracy {
        EXACT, ESTIMATED, UNKNOWN
    }

    public record MailAttachmentDto(
            int index,
            String filename,
            String contentType,
            long size,
            boolean inline,
            String contentId,
            AttachmentSizeAccuracy sizeAccuracy
    ) {
        public MailAttachmentDto(int index, String filename, String contentType,
                                 long size, boolean inline, String contentId) {
            this(index, filename, contentType, size, inline, contentId,
                    size < 0 ? AttachmentSizeAccuracy.UNKNOWN : AttachmentSizeAccuracy.ESTIMATED);
        }

        @JsonProperty("fileName")
        public String fileName() { return filename; }

        @JsonProperty("sizeBytes")
        public long sizeBytes() { return size; }
    }

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
    ) {
        @JsonProperty("bodyText")
        public String bodyText() { return textBody; }

        @JsonProperty("bodyHtml")
        public String bodyHtml() { return htmlBody; }
    }

    public record SendMailRequest(
            @JsonAlias("gmailAccountId") Long gmailId,
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
            String status,
            String sentAt
    ) {
        public SendMailResponse(String messageId, String status) {
            this(messageId, status, Instant.now().toString());
        }

        @JsonProperty("message")
        public String message() { return "SENT".equals(status) ? "Sent" : status; }
    }

    public record UpdateFlagsRequest(
            Boolean seen,
            @JsonAlias("starred") Boolean flagged
    ) {}

    /** Both current and original flag response fields, without altering nested flags DTOs. */
    public record UpdateFlagsResponse(long uid, boolean seen, boolean flagged, boolean answered, boolean draft) {
        public static UpdateFlagsResponse from(long uid, MailFlagsDto flags) {
            return new UpdateFlagsResponse(uid, flags.seen(), flags.flagged(), flags.answered(), flags.draft());
        }

        @JsonProperty("starred")
        public boolean starred() { return flagged; }
    }
}

