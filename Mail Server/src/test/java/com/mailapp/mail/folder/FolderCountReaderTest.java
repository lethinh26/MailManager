package com.mailapp.mail.folder;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.protocol.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void propagatesUnavailableCountsRatherThanReportingEmptyFolder() throws Exception {
        Folder folder = mock(Folder.class);
        when(folder.getMessageCount()).thenThrow(new MessagingException("connection lost"));
        assertThrows(MessagingException.class, () -> FolderCountReader.read(folder));
    }

    @Test
    @DisplayName("readAll giữ đúng thứ tự, gộp theo lô và cô lập lỗi trong từng lô")
    void readAllPreservesOrderAndIsolatesFailures() {
        List<String> names = List.of("INBOX", "A", "B", "C", "D", "E", "F");

        java.util.Map<String, FolderCountReader.FolderCounts> result = FolderCountReader.readAll(3, names, batch -> {
            // Lô chứa "D" cố tình lỗi: chỉ lô đó bị 0, các lô khác vẫn bình thường
            if (batch.contains("D")) {
                throw new IllegalStateException("boom");
            }
            java.util.Map<String, FolderCountReader.FolderCounts> out = new java.util.HashMap<>();
            for (String n : batch) {
                out.put(n, new FolderCountReader.FolderCounts(n.length(), n.length() * 2));
            }
            return out;
        });

        assertEquals(names.size(), result.size(), "Mọi folder đều phải có kết quả");
        assertEquals(5, result.get("INBOX").total());
        assertEquals(10, result.get("INBOX").unread());
        assertEquals(0, result.get("C").total(), "Lô lỗi trả 0 thay vì làm hỏng cả danh sách");
        assertEquals(0, result.get("D").total());
        assertEquals(1, result.get("F").total(), "Lô khác vẫn đọc bình thường");
        assertEquals(names, new java.util.ArrayList<>(result.keySet()), "Phải giữ nguyên thứ tự đầu vào");
    }
}
