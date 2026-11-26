package com.mailapp.mail.service;

import com.mailapp.mail.ImapConnection;
import com.mailapp.mail.MailConnectionFactory;
import com.mailapp.mail.folder.GmailFolderResolver;
import com.mailapp.model.AccountStatus;
import com.mailapp.model.AuthType;
import com.mailapp.model.GmailAccount;
import com.mailapp.model.Role;
import com.mailapp.model.User;
import com.mailapp.repository.GmailAccountRepository;
import com.mailapp.service.AuditLogService;
import com.mailapp.service.SystemSettingService;
import jakarta.mail.Folder;
import jakarta.mail.Store;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderCacheTest {

    @Mock private GmailAccountRepository accountRepository;
    @Mock private MailConnectionFactory connectionFactory;
    @Mock private GmailFolderResolver folderResolver;
    @Mock private SystemSettingService systemSettingService;
    @Mock private AuditLogService auditLogService;
    @Mock private HtmlSanitizerService htmlSanitizer;
    @Mock private MailHeaderAndRateGuard headerAndRateGuard;
    @Mock private Store store;
    @Mock private Folder defaultFolder;

    @Test
    @DisplayName("Danh sách thư mục được cache: hai lần gọi liên tiếp chỉ mở IMAP một lần")
    void folderListIsServedFromCache() throws Exception {
        User user = new User(2L, "lethinh", "lethinh@example.com", Role.USER);
        GmailAccount account = new GmailAccount();
        account.setId(1L);
        account.setUser(user);
        account.setEmail("inbox@gmail.com");
        account.setAuthType(AuthType.APP_PASSWORD);
        account.setStatus(AccountStatus.ACTIVE);

        when(accountRepository.findByIdAndUserId(1L, 2L)).thenReturn(Optional.of(account));
        when(connectionFactory.openImap(account)).thenReturn(new ImapConnection(store, "i***@gmail.com"));
        when(store.getDefaultFolder()).thenReturn(defaultFolder);
        when(defaultFolder.list("*")).thenReturn(new Folder[0]);

        MailServiceImpl service = new MailServiceImpl(accountRepository, connectionFactory, folderResolver,
                systemSettingService, auditLogService, htmlSanitizer, headerAndRateGuard);

        service.listFolders(user, 1L);
        service.listFolders(user, 1L);

        verify(connectionFactory, times(1)).openImap(account);
        verify(defaultFolder, times(1)).list("*");
    }

    @Test
    void changedMailboxRevisionCannotReuseCountsFromOldLink() throws Exception {
        User user = new User(2L, "fixture", "fixture@example.test", Role.USER);
        GmailAccount account = new GmailAccount();
        account.setId(1L);
        account.setUser(user);
        account.setEmail("inbox@example.test");
        account.setAuthType(AuthType.APP_PASSWORD);
        account.setStatus(AccountStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(account, "revision", 0L);
        when(accountRepository.findByIdAndUserId(1L, 2L)).thenReturn(Optional.of(account));
        when(connectionFactory.openImap(account)).thenAnswer(call -> new ImapConnection(store, "fixture"));
        when(store.getDefaultFolder()).thenReturn(defaultFolder);
        when(defaultFolder.list("*")).thenReturn(new Folder[0]);
        MailServiceImpl service = new MailServiceImpl(accountRepository, connectionFactory, folderResolver,
                systemSettingService, auditLogService, htmlSanitizer, headerAndRateGuard);

        service.listFolders(user, 1L);
        service.listFolders(user, 1L);
        verify(connectionFactory, times(1)).openImap(account);
        org.springframework.test.util.ReflectionTestUtils.setField(account, "revision", 1L);
        service.listFolders(user, 1L);
        service.listFolders(user, 1L);
        verify(connectionFactory, times(2)).openImap(account);
        verify(defaultFolder, times(2)).list("*");
    }
}
