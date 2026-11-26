package com.mailapp.mail.service;

import com.mailapp.dto.mail.MailDtos.MailListResponse;
import com.mailapp.dto.mail.MailDtos.SendMailRequest;
import com.mailapp.exception.AppException;
import com.mailapp.exception.MailNetworkException;
import com.mailapp.mail.ImapConnection;
import com.mailapp.mail.MailConnectionFactory;
import com.mailapp.mail.SmtpConnection;
import com.mailapp.mail.folder.GmailFolderResolver;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.SearchTerm;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.angus.mail.imap.protocol.IMAPProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MailServiceFailureRegressionTest {
    private final User user = new User(1L, "user", "user@example.test", Role.USER);
    private final GmailAccount account = new GmailAccount();
    private final MailConnectionFactory factory = mock(MailConnectionFactory.class);
    private final SystemSettingService settings = mock(SystemSettingService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final Store store = mock(Store.class);
    private final Session session = Session.getInstance(new Properties());
    private MailService service;

    @BeforeEach
    void setup() {
        account.setId(10L);
        account.setUser(user);
        account.setEmail("sender@example.test");
        account.setStatus(AccountStatus.ACTIVE);
        GmailAccountRepository repository = mock(GmailAccountRepository.class);
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(account));
        when(settings.isSendMailEnabled()).thenReturn(true);
        when(settings.getMaxAttachmentSizeMb()).thenReturn(1);
        when(settings.getMailPageSize()).thenReturn(20);
        when(factory.openImap(account)).thenAnswer(invocation -> new ImapConnection(store, "***"));
        service = new MailServiceImpl(repository, factory, new GmailFolderResolver(),
                settings, audit, new HtmlSanitizerService(), new MailHeaderAndRateGuard(30, 15));
    }

    @Test
    void failedListIsNeverCachedAsAnEmptyMailbox() throws Exception {
        Folder root = mock(Folder.class);
        when(store.getDefaultFolder()).thenReturn(root);
        when(root.list("*")).thenThrow(new MessagingException("connection lost"));
        java.util.List<Boolean> released = new java.util.ArrayList<>();
        when(factory.openImap(account)).thenAnswer(invocation ->
                new ImapConnection(store, "***", lease -> released.add(lease.isBroken())));
        for (int i = 0; i < 2; i++) {
            MailNetworkException failure = assertThrows(MailNetworkException.class,
                    () -> service.listFolders(user, 10L));
            assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatus());
        }
        verify(root, times(2)).list("*");
        assertEquals(List.of(true, true), released, "A failed protocol operation must discard the lease");
    }

    @Test
    void folderCloseFailureDiscardsLeaseEvenAfterSuccessfulRead() throws Exception {
        IMAPFolder folder = mock(IMAPFolder.class);
        when(store.getFolder("INBOX")).thenReturn(folder);
        when(folder.exists()).thenReturn(true);
        when(folder.isOpen()).thenReturn(true);
        when(folder.getUIDValidity()).thenReturn(9L);
        when(folder.getMessages()).thenReturn(new Message[0]);
        doThrow(new MessagingException("fixture close failure")).when(folder).close(false);
        java.util.List<Boolean> released = new java.util.ArrayList<>();
        when(factory.openImap(account)).thenAnswer(invocation ->
                new ImapConnection(store, "***", lease -> released.add(lease.isBroken())));
        assertEquals(0, service.listMails(user, 10L, "INBOX", 0, 20, null, null, null, null, null).total());
        assertEquals(List.of(true), released);
    }

    @Test
    void foreignAndMissingMailboxesAreIndistinguishableWithoutImapAccess() {
        User other = new User(2L, "other", "other@example.test", Role.USER);
        AppException foreign = assertThrows(AppException.class, () -> service.listFolders(other, 10L));
        AppException missing = assertThrows(AppException.class, () -> service.listFolders(other, 999L));
        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatus());
        assertEquals("GMAIL_NOT_OWNED", foreign.getCode());
        assertEquals(foreign.getStatus(), missing.getStatus());
        assertEquals(foreign.getCode(), missing.getCode());
        assertEquals(foreign.getMessage(), missing.getMessage());
        verifyNoInteractions(factory);
    }

    @Test
    void explicitAppPasswordRemainsUsableWhenOnlyOAuthNeedsReauth() throws Exception {
        account.setAuthType(AuthType.APP_PASSWORD);
        account.setStatus(AccountStatus.NEEDS_REAUTH);
        account.setAppPasswordEncrypted("synthetic-app-cipher");
        account.setRefreshTokenEncrypted("synthetic-oauth-cipher");
        Folder root = mock(Folder.class);
        when(store.getDefaultFolder()).thenReturn(root);
        when(root.list("*")).thenReturn(new Folder[0]);
        assertTrue(service.listFolders(user, 10L).isEmpty());
        verify(factory).openImap(account);
        assertEquals(AccountStatus.NEEDS_REAUTH, account.getStatus());
        account.setRefreshTokenEncrypted(null);
        AppException blocked = assertThrows(AppException.class, () -> service.listFolders(user, 10L));
        assertEquals("GMAIL_NEEDS_REAUTH", blocked.getCode());
        verify(factory, times(1)).openImap(account);
    }

    @Test
    void attachmentSearchIncludesOldestMessageBeyondFirst300AndFalseFilter() throws Exception {
        IMAPFolder folder = mock(IMAPFolder.class);
        when(store.getFolder("INBOX")).thenReturn(folder);
        when(folder.exists()).thenReturn(true);
        when(folder.getUIDValidity()).thenReturn(1L);
        when(folder.getUID(any(Message.class))).thenAnswer(call -> (long) ((Message) call.getArgument(0)).getMessageNumber());
        Message[] messages = new Message[301];
        for (int i = 0; i < messages.length; i++) {
            MimeMessage message = new NumberedMessage(session, i + 1);
            message.setSubject("message " + (i + 1));
            message.setText("body");
            if (i == 0) {
                MimeMultipart mixed = new MimeMultipart("mixed");
                MimeBodyPart text = new MimeBodyPart();
                text.setText("body");
                mixed.addBodyPart(text);
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.setText("attachment");
                attachment.setFileName("report.txt");
                attachment.setDisposition(Part.ATTACHMENT);
                mixed.addBodyPart(attachment);
                message.setContent(mixed);
            }
            message.saveChanges();
            messages[i] = message;
        }
        when(folder.getMessages()).thenReturn(messages);
        MailListResponse attached = service.listMails(user, 10L, "INBOX", 0, 0, null, null, true, null, null);
        assertEquals(20, attached.size());
        assertEquals(1, attached.total());
        assertEquals(1L, attached.items().getFirst().uid());
        assertEquals(1, attached.items().getFirst().attachmentCount());
        MailListResponse noAttachments = service.listMails(user, 10L, "INBOX", 14, 20, null, null, false, null, null);
        assertEquals(300, noAttachments.total());
        assertEquals(20, noAttachments.items().size());
        assertFalse(noAttachments.hasMore());
        assertTrue(noAttachments.items().stream().noneMatch(item -> item.hasAttachment()));
    }

    @Test
    void dateDescendingPaginationUsesUidTieBreakAndSearchFallbackWithoutFetchingBodies() throws Exception {
        IMAPFolder folder = mock(IMAPFolder.class);
        when(store.getFolder("INBOX")).thenReturn(folder);
        when(folder.exists()).thenReturn(true);
        when(folder.getUIDValidity()).thenReturn(1L);
        // The newest dates deliberately have lower sequence numbers. Two dates
        // tie; UID desc breaks the tie even if SEARCH returns an arbitrary order.
        Message first = metadataMessage(1, 3000L, null);
        Message second = metadataMessage(2, null, 3000L);
        Message third = metadataMessage(3, 2000L, 9000L);
        Message fourth = metadataMessage(4, null, null);
        Message fifth = metadataMessage(5, 1000L, null);
        Message[] all = {first, second, third, fourth, fifth};
        when(folder.getUID(any(Message.class))).thenAnswer(call ->
                (long) ((Message) call.getArgument(0)).getMessageNumber());
        when(folder.getMessages()).thenReturn(all);
        when(folder.search(any(SearchTerm.class))).thenReturn(new Message[]{fifth, first, second, fourth, third});

        for (String q : new String[]{null, "subject"}) {
            MailListResponse p0 = service.listMails(user, 10L, "INBOX", 0, 2, q, null, null, null, null);
            MailListResponse p1 = service.listMails(user, 10L, "INBOX", 1, 2, q, null, null, null, null);
            MailListResponse p2 = service.listMails(user, 10L, "INBOX", 2, 2, q, null, null, null, null);
            assertEquals(List.of(2L, 1L), p0.items().stream().map(item -> item.uid()).toList());
            assertEquals(List.of(3L, 5L), p1.items().stream().map(item -> item.uid()).toList());
            assertEquals(List.of(4L), p2.items().stream().map(item -> item.uid()).toList());
            assertEquals("1970-01-01T00:00:03Z", p0.items().getFirst().date());
            assertEquals("1970-01-01T00:00:02Z", p1.items().getFirst().date());
            assertNull(p2.items().getFirst().date());
            assertEquals(5, p0.total());
            assertTrue(p0.hasMore());
            assertFalse(p2.hasMore());
        }
        when(folder.search(any(SearchTerm.class))).thenThrow(new MessagingException("Unsupported SEARCH"));
        MailListResponse fallback = service.listMails(user, 10L, "INBOX", 0, 2,
                "subject", null, false, null, null);
        assertEquals(List.of(2L, 1L), fallback.items().stream().map(item -> item.uid()).toList());
        assertEquals(5, fallback.total());
        for (Message m : all) {
            verify(m, never()).getContent();
            verify(m, never()).getInputStream();
        }
        // Metadata-only ordering and envelope SEARCH fallback never fetch
        // CONTENT_INFO for all candidates unless the attachment filter needs it.
        verify(folder, atLeastOnce()).fetch(any(Message[].class), argThat(profile ->
                profile.contains(FetchProfile.Item.ENVELOPE)
                        && profile.contains(UIDFolder.FetchProfileItem.UID)
                        && !profile.contains(FetchProfile.Item.CONTENT_INFO)));
    }

    private Message metadataMessage(int number, Long receivedMs, Long sentMs) throws Exception {
        Message message = mock(Message.class);
        when(message.getMessageNumber()).thenReturn(number);
        when(message.getReceivedDate()).thenReturn(receivedMs == null ? null : new Date(receivedMs));
        when(message.getSentDate()).thenReturn(sentMs == null ? null : new Date(sentMs));
        when(message.getSubject()).thenReturn("subject");
        when(message.getContentType()).thenReturn("text/plain");
        when(message.getFlags()).thenReturn(new Flags());
        return message;
    }

    @Test
    void attachmentSizeMetadataDistinguishesEstimatesAndUnknownWithoutReadingAttachmentBodies() throws Exception {
        IMAPFolder folder = mock(IMAPFolder.class);
        when(store.getFolder("INBOX")).thenReturn(folder);
        when(folder.exists()).thenReturn(true);
        when(folder.getMode()).thenReturn(Folder.READ_ONLY);
        when(folder.getUIDValidity()).thenReturn(9L);
        Message message = mock(Message.class);
        when(folder.getMessageByUID(7L)).thenReturn(message);
        when(message.getContentType()).thenReturn("multipart/mixed");
        when(message.getFlags()).thenReturn(new Flags());
        MimeMultipart mixed = new MimeMultipart("mixed");
        when(message.getContent()).thenReturn(mixed);
        for (int encodedSize : new int[]{28, 32, -1}) {
            MimeBodyPart attachment = mock(MimeBodyPart.class);
            when(attachment.getContentType()).thenReturn("text/plain");
            when(attachment.getDisposition()).thenReturn(Part.ATTACHMENT);
            when(attachment.getFileName()).thenReturn("fixture.txt");
            when(attachment.getSize()).thenReturn(encodedSize);
            when(attachment.getHeader("Content-Transfer-Encoding")).thenReturn(new String[]{"base64"});
            mixed.addBodyPart(attachment);
        }

        var attachments = service.getMailDetail(user, 10L, "INBOX", 7L, false, 9L, false).attachments();
        assertEquals(List.of(0, 1, 2), attachments.stream().map(a -> a.index()).toList());
        assertEquals(List.of("fixture.txt", "fixture.txt", "fixture.txt"),
                attachments.stream().map(a -> a.filename()).toList());
        assertEquals(List.of(21L, 24L, 0L), attachments.stream().map(a -> a.sizeBytes()).toList());
        assertEquals(List.of("ESTIMATED", "ESTIMATED", "UNKNOWN"),
                attachments.stream().map(a -> a.sizeAccuracy().name()).toList());
        for (int i = 0; i < mixed.getCount(); i++) {
            BodyPart attachment = mixed.getBodyPart(i);
            verify(attachment, never()).getContent();
            verify(attachment, never()).getInputStream();
        }
    }

    @Test
    void defaultServiceNeverExpungesNonUidplusEvenOnLoopback() throws Exception {
        IMAPFolder folder = mock(IMAPFolder.class);
        Folder root = mock(Folder.class);
        when(store.getDefaultFolder()).thenReturn(root);
        when(root.list("*")).thenReturn(new Folder[0]);
        when(store.getFolder("INBOX")).thenReturn(folder);
        when(store.getURLName()).thenReturn(new URLName("imaps", "127.0.0.1", 993, null, null, null));
        when(folder.exists()).thenReturn(true);
        when(folder.getMode()).thenReturn(Folder.READ_WRITE);
        when(folder.getUIDValidity()).thenReturn(9L);
        Message target = mock(Message.class);
        when(folder.getMessageByUID(7L)).thenReturn(target);
        MailNetworkException ex = assertThrows(MailNetworkException.class,
                () -> service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1"));
        assertEquals("GMAIL_CONNECTION_ERROR", ex.getErrorCode());
        verifyNoInteractions(target);
        verify(folder, never()).copyMessages(any(Message[].class), any(Folder.class));
        verify(folder, never()).expunge();
        verifyNoInteractions(audit);
    }

    private record DeleteFixture(IMAPStore store, IMAPFolder source, Folder trash, Message target,
                                 ImapConnection connection) {}

    private DeleteFixture deleteFixture(boolean move, boolean uidPlus) throws Exception {
        IMAPStore capableStore = mock(IMAPStore.class);
        IMAPFolder source = mock(IMAPFolder.class);
        Folder root = mock(Folder.class);
        Folder trash = mock(Folder.class);
        Message target = mock(Message.class);
        when(capableStore.getDefaultFolder()).thenReturn(root);
        when(root.list("*")).thenReturn(new Folder[0]);
        when(capableStore.getFolder("INBOX")).thenReturn(source);
        when(capableStore.getFolder("[Gmail]/Trash")).thenReturn(trash);
        when(capableStore.hasCapability("MOVE")).thenReturn(move);
        when(capableStore.hasCapability("UIDPLUS")).thenReturn(uidPlus);
        when(source.exists()).thenReturn(true);
        when(source.getFullName()).thenReturn("INBOX");
        when(source.getMode()).thenReturn(Folder.READ_WRITE);
        when(source.isOpen()).thenReturn(true);
        when(source.getUIDValidity()).thenReturn(9L);
        when(source.getMessageByUID(7L)).thenReturn(target);
        when(trash.exists()).thenReturn(true);
        when(trash.getFullName()).thenReturn("[Gmail]/Trash");
        ImapConnection connection = spy(new ImapConnection(capableStore, "***"));
        when(factory.openImap(account)).thenReturn(connection);
        return new DeleteFixture(capableStore, source, trash, target, connection);
    }

    @Test
    void uidMoveUsesSingleServerCommandWithoutCopyOrExpungeAndAuditsOnce() throws Exception {
        DeleteFixture f = deleteFixture(true, false);
        IMAPProtocol protocol = mock(IMAPProtocol.class);
        when(f.source().doCommand(any(IMAPFolder.ProtocolCommand.class))).thenAnswer(call ->
                call.getArgument(0, IMAPFolder.ProtocolCommand.class).doCommand(protocol));
        assertEquals("DELETED", service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1").get("status"));
        verify(protocol).simpleCommand(eq("UID MOVE"), any());
        verify(f.source(), never()).copyMessages(any(), any());
        verify(f.source(), never()).expunge(any(Message[].class));
        verify(f.source(), never()).expunge();
        verify(f.target(), never()).setFlag(any(), anyBoolean());
        verify(audit).prepareMailMutation(eq(1L), eq("MAIL_DELETE"), contains("uid=7"), eq("127.0.0.1"));
        verify(audit).finishMailMutation(isNull(), eq(true), isNull());
        verify(f.source()).close(false);
    }

    @Test
    void lostMoveReplyDoesNotRetryCopyOrAuditAndRequiresFreshUidLookup() throws Exception {
        DeleteFixture f = deleteFixture(true, true);
        when(f.source().doCommand(any(IMAPFolder.ProtocolCommand.class)))
                .thenThrow(new MessagingException("reply lost after MOVE"));
        MailNetworkException failure = assertThrows(MailNetworkException.class,
                () -> service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1"));
        assertTrue(failure.getMessage().contains("Không xác định"));
        assertTrue(f.connection().isBroken());
        verify(f.source(), times(1)).doCommand(any(IMAPFolder.ProtocolCommand.class));
        verify(f.source(), never()).copyMessages(any(), any());
        verify(f.source(), never()).expunge(any(Message[].class));
        verify(audit).prepareMailMutation(eq(1L), eq("MAIL_DELETE"), contains("uid=7"), eq("127.0.0.1"));
        verify(audit).finishMailMutation(isNull(), eq(false), isNull());
        // Model a new connection after the server completed MOVE, but its
        // response was lost. Refreshing/reissuing the now stale UID is harmless.
        when(factory.openImap(account)).thenReturn(new ImapConnection(f.store(), "***"));
        when(f.source().getMessageByUID(7L)).thenReturn(null);
        AppException missing = assertThrows(AppException.class,
                () -> service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1"));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());
        verify(f.source(), times(1)).doCommand(any(IMAPFolder.ProtocolCommand.class));
        verifyNoMoreInteractions(audit);
    }

    @Test
    void copyFailureNeverMarksSourceDeletedOrBlindlyRepeatsCopy() throws Exception {
        DeleteFixture f = deleteFixture(false, true);
        doThrow(new MessagingException("COPY reply lost")).when(f.source()).copyMessages(any(), eq(f.trash()));
        MailNetworkException failure = assertThrows(MailNetworkException.class,
                () -> service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1"));
        assertTrue(failure.getMessage().contains("Không xác định"));
        assertTrue(f.connection().isBroken());
        verify(f.source(), times(1)).copyMessages(any(), eq(f.trash()));
        verify(f.target(), never()).setFlag(any(), anyBoolean());
        verify(f.source(), never()).expunge(any(Message[].class));
        verify(f.source(), never()).expunge();
        verify(audit).prepareMailMutation(eq(1L), eq("MAIL_DELETE"), contains("uid=7"), eq("127.0.0.1"));
        verify(audit).finishMailMutation(isNull(), eq(false), isNull());
    }

    @Test
    void uidExpungeFailureRestoresOnlyTargetFlagAndReturnsUncertainOutcome() throws Exception {
        DeleteFixture f = deleteFixture(false, true);
        doThrow(new MessagingException("expunge interrupted")).when(f.source()).expunge(any(Message[].class));
        MailNetworkException failure = assertThrows(MailNetworkException.class,
                () -> service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1"));
        assertTrue(failure.getMessage().contains("Không xác định"));
        assertTrue(f.connection().isBroken());
        var order = inOrder(f.source(), f.target());
        order.verify(f.source()).copyMessages(any(), eq(f.trash()));
        order.verify(f.target()).setFlag(Flags.Flag.DELETED, true);
        order.verify(f.source()).expunge(argThat(messages -> messages.length == 1 && messages[0] == f.target()));
        order.verify(f.target()).setFlag(Flags.Flag.DELETED, false);
        verify(f.source(), never()).expunge();
        verify(audit).prepareMailMutation(eq(1L), eq("MAIL_DELETE"), contains("uid=7"), eq("127.0.0.1"));
        verify(audit).finishMailMutation(isNull(), eq(false), isNull());
    }

    @Test
    void lostDeletedFlagReplyRestoresOnlyTargetWithoutExpungingOrAuditing() throws Exception {
        DeleteFixture f = deleteFixture(false, true);
        doThrow(new MessagingException("STORE reply lost"))
                .when(f.target()).setFlag(Flags.Flag.DELETED, true);
        MailNetworkException failure = assertThrows(MailNetworkException.class,
                () -> service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1"));
        assertTrue(failure.getMessage().contains("Không xác định"));
        assertTrue(f.connection().isBroken());
        var order = inOrder(f.source(), f.target());
        order.verify(f.source()).copyMessages(any(), eq(f.trash()));
        order.verify(f.target()).setFlag(Flags.Flag.DELETED, true);
        order.verify(f.target()).setFlag(Flags.Flag.DELETED, false);
        verify(f.source(), never()).expunge(any(Message[].class));
        verify(f.source(), never()).expunge();
        verify(audit).prepareMailMutation(eq(1L), eq("MAIL_DELETE"), contains("uid=7"), eq("127.0.0.1"));
        verify(audit).finishMailMutation(isNull(), eq(false), isNull());
    }

    @Test
    void smtpPerFileLimitAllowsSeveralFilesAndAuditOmitsSensitiveHeaders() throws Exception {
        SmtpConnection smtp = mock(SmtpConnection.class);
        when(factory.openSmtp(account)).thenReturn(smtp);
        when(smtp.createMimeMessage()).thenReturn(new MimeMessage(session));
        SendMailRequest request = new SendMailRequest(10L, List.of("secret@example.test"),
                List.of("secret-cc@example.test"), List.of("secret-bcc@example.test"),
                "private subject", "private body", null, null, null);
        MultipartFile first = new MockMultipartFile("files", "a.bin", "application/octet-stream", new byte[700_000]);
        MultipartFile second = new MockMultipartFile("files", "b.bin", "application/octet-stream", new byte[700_000]);
        assertEquals("SENT", service.sendMail(user, request, List.of(first, second), "127.0.0.1").status());
        verify(smtp).sendMessage(any());
        verify(audit).prepareMailMutation(eq(1L), eq("SEND_MAIL"), argThat(detail -> detail.contains("attachmentCount=2")
                && detail.contains("recipientCount=3") && detail.contains("toCount=1")
                && detail.contains("ccCount=1") && detail.contains("bccCount=1")
                && !detail.contains("secret@example.test") && !detail.contains("secret-cc@example.test")
                && !detail.contains("secret-bcc@example.test")
                && !detail.contains("private subject") && !detail.contains("private body")), eq("127.0.0.1"));
        verify(audit).finishMailMutation(isNull(), eq(true), isNull());
        MultipartFile oversized = new MockMultipartFile("files", "too-large.bin", "application/octet-stream",
                new byte[1_048_577]);
        AppException failure = assertThrows(AppException.class,
                () -> service.sendMail(user, request, List.of(oversized), "127.0.0.1"));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure.getStatus());
        assertEquals("PAYLOAD_TOO_LARGE", failure.getCode());
        verify(factory, times(1)).openSmtp(account);
    }

    @Test
    void rejectedSmtpRecipientIsBadRequestWithoutAddressLeakOrSuccessAudit() throws Exception {
        SmtpConnection smtp = mock(SmtpConnection.class);
        when(factory.openSmtp(account)).thenReturn(smtp);
        when(smtp.createMimeMessage()).thenReturn(new MimeMessage(session));
        doThrow(new SendFailedException("rejected address: secret@example.test", null, null, null,
                new Address[]{new InternetAddress("secret@example.test")})).when(smtp).sendMessage(any());
        SendMailRequest request = new SendMailRequest(10L, List.of("secret@example.test"), null, null,
                "subject", "body", null, null, null);
        AppException failure = assertThrows(AppException.class,
                () -> service.sendMail(user, request, List.of(), "127.0.0.1"));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        assertEquals("INVALID_EMAIL", failure.getCode());
        assertFalse(failure.getMessage().contains("secret@example.test"));
        verify(audit).prepareMailMutation(eq(1L), eq("SEND_MAIL"), contains("recipientCount=1"), eq("127.0.0.1"));
        verify(audit).finishMailMutation(isNull(), eq(false), isNull());
    }

    @Test
    void partialSmtpDeliveryIsUncertainEvenWithInvalidRecipients() throws Exception {
        SmtpConnection smtp = mock(SmtpConnection.class);
        when(factory.openSmtp(account)).thenReturn(smtp);
        when(smtp.createMimeMessage()).thenReturn(new MimeMessage(session));
        when(audit.prepareMailMutation(eq(1L), eq("SEND_MAIL"), anyString(), eq("127.0.0.1")))
                .thenReturn("fixture-event");
        doThrow(new SendFailedException("private@example.test", null,
                new Address[]{new InternetAddress("accepted@example.test")}, null,
                new Address[]{new InternetAddress("private@example.test")})).when(smtp).sendMessage(any());
        var request = new SendMailRequest(10L, List.of("accepted@example.test", "private@example.test"),
                null, null, "subject", "body", null, null, null);
        var failure = assertThrows(MailNetworkException.class,
                () -> service.sendMail(user, request, List.of(), "127.0.0.1"));
        assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatus());
        assertFalse(failure.getMessage().contains("private@example.test"));
        verify(smtp, times(1)).sendMessage(any());
        verify(smtp).close();
        verify(audit).finishMailMutation("fixture-event", false, null);
    }

    @Test
    void failedAuditIntentPreventsSmtpMutationAndClosesTransport() throws Exception {
        SmtpConnection smtp = mock(SmtpConnection.class);
        when(factory.openSmtp(account)).thenReturn(smtp);
        when(smtp.createMimeMessage()).thenReturn(new MimeMessage(session));
        when(audit.prepareMailMutation(eq(1L), eq("SEND_MAIL"), anyString(), eq("127.0.0.1")))
                .thenThrow(new AppException(HttpStatus.SERVICE_UNAVAILABLE, "AUDIT_UNAVAILABLE", "fixture"));
        SendMailRequest request = new SendMailRequest(10L, List.of("recipient@example.test"), null, null,
                "subject", "body", null, null, null);
        AppException failure = assertThrows(AppException.class,
                () -> service.sendMail(user, request, List.of(), "127.0.0.1"));
        assertEquals("AUDIT_UNAVAILABLE", failure.getCode());
        verify(smtp, never()).sendMessage(any());
        verify(smtp).close();
        verify(audit, never()).finishMailMutation(any(), anyBoolean(), any());
    }

    @Test
    void failedAuditIntentPreventsMoveAndExpunge() throws Exception {
        DeleteFixture f = deleteFixture(true, true);
        when(audit.prepareMailMutation(eq(1L), eq("MAIL_DELETE"), anyString(), eq("127.0.0.1")))
                .thenThrow(new AppException(HttpStatus.SERVICE_UNAVAILABLE, "AUDIT_UNAVAILABLE", "fixture"));
        AppException failure = assertThrows(AppException.class,
                () -> service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1"));
        assertEquals("AUDIT_UNAVAILABLE", failure.getCode());
        verify(f.source(), never()).doCommand(any(IMAPFolder.ProtocolCommand.class));
        verify(f.target(), never()).setFlag(any(), anyBoolean());
        verify(f.source()).close(false);
        verify(audit, never()).finishMailMutation(any(), anyBoolean(), any());
    }

    @Test
    void failedAuditIntentAlsoPreventsCreatingMissingTrashFolder() throws Exception {
        DeleteFixture f = deleteFixture(true, true);
        Folder fallback = mock(Folder.class);
        when(f.trash().exists()).thenReturn(false);
        when(f.store().getFolder("Trash")).thenReturn(fallback);
        when(audit.prepareMailMutation(eq(1L), eq("MAIL_DELETE"), anyString(), eq("127.0.0.1")))
                .thenThrow(new AppException(HttpStatus.SERVICE_UNAVAILABLE, "AUDIT_UNAVAILABLE", "fixture"));
        AppException failure = assertThrows(AppException.class,
                () -> service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1"));
        assertEquals("AUDIT_UNAVAILABLE", failure.getCode());
        verify(fallback, never()).create(anyInt());
        verify(f.source(), never()).doCommand(any(IMAPFolder.ProtocolCommand.class));
        verify(f.source(), never()).copyMessages(any(), any());
        verify(f.target(), never()).setFlag(any(), anyBoolean());
        verify(audit, never()).finishMailMutation(any(), anyBoolean(), any());
    }

    @Test
    void lostTrashCreationReplyIsUncertainWithoutMoveOrRetry() throws Exception {
        DeleteFixture f = deleteFixture(true, true);
        Folder fallback = mock(Folder.class);
        when(f.trash().exists()).thenReturn(false);
        when(f.store().getFolder("Trash")).thenReturn(fallback);
        when(audit.prepareMailMutation(eq(1L), eq("MAIL_DELETE"), anyString(), eq("127.0.0.1")))
                .thenReturn("fixture-event");
        when(fallback.create(Folder.HOLDS_MESSAGES)).thenThrow(new MessagingException("CREATE reply lost"));
        assertThrows(MailNetworkException.class,
                () -> service.deleteMail(user, 10L, "INBOX", 7L, false, 9L, "127.0.0.1"));
        var order = inOrder(audit, fallback);
        order.verify(audit).prepareMailMutation(eq(1L), eq("MAIL_DELETE"), anyString(), eq("127.0.0.1"));
        order.verify(fallback).create(Folder.HOLDS_MESSAGES);
        order.verify(audit).finishMailMutation("fixture-event", false, null);
        verify(fallback, times(1)).create(anyInt());
        verify(f.source(), never()).doCommand(any(IMAPFolder.ProtocolCommand.class));
        verify(f.source(), never()).copyMessages(any(), any());
        assertTrue(f.connection().isBroken());
    }

    @Test
    void partialFlagMutationIsUncertainAndDoesNotRetryOrReportSuccess() throws Exception {
        DeleteFixture f = deleteFixture(true, true);
        when(audit.prepareMailMutation(eq(1L), eq("MAIL_FLAG_CHANGE"), contains("uidValidity=9"), eq("127.0.0.1")))
                .thenReturn("fixture-event");
        doThrow(new MessagingException("second STORE reply lost")).when(f.target()).setFlag(Flags.Flag.FLAGGED, true);
        MailNetworkException failure = assertThrows(MailNetworkException.class, () ->
                service.updateFlags(user, 10L, "INBOX", 7L,
                        new com.mailapp.dto.mail.MailDtos.UpdateFlagsRequest(true, true), 9L, "127.0.0.1"));
        assertTrue(failure.getMessage().contains("Không xác định"));
        var order = inOrder(audit, f.target());
        order.verify(audit).prepareMailMutation(eq(1L), eq("MAIL_FLAG_CHANGE"), contains("uid=7"), eq("127.0.0.1"));
        order.verify(f.target()).setFlag(Flags.Flag.SEEN, true);
        order.verify(f.target()).setFlag(Flags.Flag.FLAGGED, true);
        order.verify(audit).finishMailMutation("fixture-event", false, null);
        verify(audit, never()).finishMailMutation(any(), eq(true), any());
        assertTrue(f.connection().isBroken());
        verify(f.source()).close(false);
    }

    @Test
    void readOnlyFolderCannotPretendToMarkMessageSeen() throws Exception {
        IMAPFolder folder = mock(IMAPFolder.class);
        when(store.getFolder("INBOX")).thenReturn(folder);
        when(folder.exists()).thenReturn(true);
        when(folder.getMode()).thenReturn(Folder.READ_ONLY);
        assertThrows(MailNetworkException.class,
                () -> service.getMailDetail(user, 10L, "INBOX", 1L, false, null));
    }

    private static final class NumberedMessage extends MimeMessage {
        NumberedMessage(Session session, int number) {
            super(session);
            setMessageNumber(number);
        }
    }
}
