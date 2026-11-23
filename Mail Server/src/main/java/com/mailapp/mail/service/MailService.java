package com.mailapp.mail.service;

import com.mailapp.dto.mail.MailDtos.*;
import com.mailapp.model.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Giao diện nghiệp vụ Hộp thư thật (M08, M10, M12, M16, M17).
 * Mọi thao tác đều đi qua MailConnectionFactory, KHÔNG lưu nội dung thư vào DB.
 */
public interface MailService {

    List<MailFolderDto> listFolders(User currentUser, Long gmailId);

    MailListResponse listMails(User currentUser,
                               Long gmailId,
                               String folder,
                               int page,
                               int size,
                               String q,
                               Boolean unread,
                               Boolean hasAttachment,
                               String from,
                               String since);

    MailDetailResponse getMailDetail(User currentUser,
                                     Long gmailId,
                                     String folder,
                                     long uid,
                                     boolean loadRemote,
                                     Long expectedUidValidity);

    record AttachmentDownloadHandle(
            String sanitizedFilename,
            String contentType,
            long estimatedSize,
            AttachmentStreamer streamer
    ) {}

    @FunctionalInterface
    interface AttachmentStreamer {
        void streamTo(OutputStream out) throws Exception;
    }

    AttachmentDownloadHandle prepareAttachmentDownload(User currentUser,
                                                       Long gmailId,
                                                       String folder,
                                                       long uid,
                                                       int attachmentIndex,
                                                       Long expectedUidValidity);

    SendMailResponse sendMail(User currentUser,
                              SendMailRequest request,
                              List<MultipartFile> attachments,
                              String clientIp);

    Map<String, Object> deleteMail(User currentUser,
                                   Long gmailId,
                                   String folder,
                                   long uid,
                                   boolean permanent,
                                   Long expectedUidValidity,
                                   String clientIp);

    MailFlagsDto updateFlags(User currentUser,
                             Long gmailId,
                             String folder,
                             long uid,
                             UpdateFlagsRequest request,
                             Long expectedUidValidity);
}

