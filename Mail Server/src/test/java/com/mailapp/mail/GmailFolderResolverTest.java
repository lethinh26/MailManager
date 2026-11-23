package com.mailapp.mail;

import com.mailapp.mail.folder.GmailFolderResolver;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailFolderResolverTest {

    @Mock
    private Store store;

    @Mock
    private Folder defaultFolder;

    @Mock
    private Folder inboxFolder;

    @Mock
    private TestImapFolder sentFolder;

    @Mock
    private Folder trashFolder;

    abstract static class TestImapFolder extends Folder {
        protected TestImapFolder(Store store) {
            super(store);
        }
        public abstract String[] getAttributes();
    }

    private GmailFolderResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GmailFolderResolver();
    }

    @Test
    @DisplayName("Phân giải INBOX trực tiếp qua store.getFolder('INBOX')")
    void testResolveInbox() throws MessagingException {
        when(store.getFolder("INBOX")).thenReturn(inboxFolder);

        Folder result = resolver.resolve(store, GmailFolderResolver.SpecialFolder.INBOX);

        assertNotNull(result);
        assertEquals(inboxFolder, result);
    }

    @Test
    @DisplayName("Phân giải SENT thành công theo RFC 6154 attribute \\Sent")
    void testResolveSentByRfcAttribute() throws MessagingException {
        when(store.getDefaultFolder()).thenReturn(defaultFolder);
        when(defaultFolder.list("*")).thenReturn(new Folder[]{sentFolder});
        when(sentFolder.getAttributes()).thenReturn(new String[]{"\\Sent", "\\HasNoChildren"});

        Folder result = resolver.resolve(store, GmailFolderResolver.SpecialFolder.SENT);

        assertNotNull(result);
        assertEquals(sentFolder, result);
    }

    @Test
    @DisplayName("Fallback theo tên tiếng Việt '[Gmail]/Thùng rác' khi không có thuộc tính RFC")
    void testResolveTrashFallbackVietnamese() throws MessagingException {
        when(store.getDefaultFolder()).thenReturn(defaultFolder);
        when(defaultFolder.list("*")).thenReturn(new Folder[]{}); // Không có attribute

        when(store.getFolder("[Gmail]/Trash")).thenReturn(null);
        when(store.getFolder("[Gmail]/Thùng rác")).thenReturn(trashFolder);
        when(trashFolder.exists()).thenReturn(true);

        Folder result = resolver.resolve(store, GmailFolderResolver.SpecialFolder.TRASH);

        assertNotNull(result);
        assertEquals(trashFolder, result);
    }

    @Test
    @DisplayName("Ném MessagingException khi không thể tìm thấy thư mục qua cả RFC lẫn fallback")
    void testResolveNotFoundThrowsException() throws MessagingException {
        when(store.getDefaultFolder()).thenReturn(defaultFolder);
        when(defaultFolder.list("*")).thenReturn(new Folder[]{});

        assertThrows(MessagingException.class, () ->
            resolver.resolve(store, GmailFolderResolver.SpecialFolder.SPAM)
        );
    }
}
