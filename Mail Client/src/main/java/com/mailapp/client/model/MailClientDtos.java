package com.mailapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MailAttachmentDto(
            int index,
            String filename,
            String contentType,
            long size,
            boolean inline,
            String contentId
    ) {}

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

