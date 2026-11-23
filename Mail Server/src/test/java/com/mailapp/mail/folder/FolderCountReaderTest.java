package com.mailapp.mail.folder;

import jakarta.mail.Folder;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.protocol.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FolderCountReaderTest {

    @Test
    @DisplayName("IMAP: MESSAGES và UNSEEN được lấy trong một lệnh STATUS duy nhất")
    void readsBothCountsWithSingleStatusCommand() throws Exception {
        IMAPFolder folder = mock(IMAPFolder.class);
        Status status = mock(Status.class);
        status.total = 7181;
        status.unseen = 6334;
        when(folder.getFullName()).thenReturn("INBOX");
        when(folder.doCommand(any())).thenReturn(status);

        FolderCountReader.FolderCounts counts = FolderCountReader.read(folder);

        assertEquals(7181, counts.total());
        assertEquals(6334, counts.unread());
        verify(folder, times(1)).doCommand(any());
        verify(folder, never()).getMessageCount();
        verify(folder, never()).getUnreadMessageCount();
    }

    @Test
    @DisplayName("Fallback cho Folder không phải IMAP vẫn dùng API chuẩn")
    void fallsBackToStandardFolderApi() throws Exception {
        Folder folder = mock(Folder.class);
        when(folder.getMessageCount()).thenReturn(10);
        when(folder.getUnreadMessageCount()).thenReturn(3);

        FolderCountReader.FolderCounts counts = FolderCountReader.read(folder);

        assertEquals(10, counts.total());
        assertEquals(3, counts.unread());
    }
}
