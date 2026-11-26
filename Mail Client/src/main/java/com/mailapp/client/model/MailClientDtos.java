package com.mailapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Các DTO phía Client tương ứng với docs/api-mail-contract.md.
 */
public final class MailClientDtos {

    private MailClientDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MailAddressDto(String name, String address) {
        public String displayText() {
            if (name != null && !name.isBlank() && address != null && !address.isBlank()) {
                return name + " <" + address + ">";
            }
            return (address != null && !address.isBlank()) ? address : (name != null ? name : "");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MailFlagsDto(boolean seen, boolean flagged, boolean answered, boolean draft) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MailFolderDto(
            String name,
            String imapName,
            String displayName,
            String type,
            int unread,
            int total
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MailListResponse(
            List<MailSummaryDto> items,
            int page,
            int size,
            long total,
            boolean hasMore,
            long uidValidity
    ) {}

    public enum AttachmentSizeAccuracy {
        EXACT, ESTIMATED, UNKNOWN;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static AttachmentSizeAccuracy fromWire(String value) {
            if (value == null) return null;
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ex) {
                return UNKNOWN;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MailAttachmentDto(
            int index,
            String filename,
            String contentType,
            long size,
            boolean inline,
            String contentId,
            AttachmentSizeAccuracy sizeAccuracy
    ) {
        public MailAttachmentDto {
            // Older servers did not distinguish a genuinely empty attachment
            // from unknown size (often encoded as zero). Never label that
            // ambiguous metadata exact.
            if (size < 0) sizeAccuracy = AttachmentSizeAccuracy.UNKNOWN;
            else if (sizeAccuracy == null) sizeAccuracy = size > 0
                    ? AttachmentSizeAccuracy.ESTIMATED : AttachmentSizeAccuracy.UNKNOWN;
        }

        public MailAttachmentDto(int index, String filename, String contentType,
                                 long size, boolean inline, String contentId) {
            this(index, filename, contentType, size, inline, contentId, null);
        }

        @JsonCreator
        public static MailAttachmentDto fromWire(
                @JsonProperty("index") int index,
                @JsonProperty("filename") String filename,
                @JsonProperty("fileName") String fileName,
                @JsonProperty("contentType") String contentType,
                @JsonProperty("size") Long size,
                @JsonProperty("sizeBytes") Long sizeBytes,
                @JsonProperty("inline") boolean inline,
                @JsonProperty("contentId") String contentId,
                @JsonProperty("sizeAccuracy") AttachmentSizeAccuracy sizeAccuracy) {
            long bytes = size != null ? size : sizeBytes != null ? sizeBytes : -1L;
            return new MailAttachmentDto(index, filename != null ? filename : fileName,
                    contentType, bytes, inline, contentId, sizeAccuracy);
        }

        @JsonProperty("fileName")
        public String fileName() { return filename; }

        @JsonProperty("sizeBytes")
        public long sizeBytes() { return size; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
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

    public record SendMailPayload(
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SendMailResponse(String messageId, String status) {}
}

