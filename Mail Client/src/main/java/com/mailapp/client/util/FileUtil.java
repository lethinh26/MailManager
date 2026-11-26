package com.mailapp.client.util;

import com.mailapp.client.net.ApiClient;
import com.mailapp.client.service.AttachmentCleanupService;
import java.io.IOException;
import java.nio.file.*;

/** Single-attachment downloads. A completed download never overwrites an existing user file. */
public final class FileUtil {
    private FileUtil() {}

    @FunctionalInterface
    public interface DownloadWriter {
        ApiClient.DownloadResult write(Path temporary) throws IOException, InterruptedException;
    }

    public static Path attachmentDirectory(Path downloads, String email) {
        return downloads.resolve("MailApp").resolve(AttachmentCleanupService.sanitizeFolderSegment(email));
    }

    /** JFileChooser silently falls back to the OS home/default folder for a missing path. */
    public static Path prepareAttachmentDirectory(Path downloads, String email) throws IOException {
        return Files.createDirectories(attachmentDirectory(downloads, email).toAbsolutePath().normalize());
    }

    public static ApiClient.DownloadResult download(ApiClient client, SessionStore.AttachmentKey key, Path requested)
            throws IOException, InterruptedException {
        return saveUnique(requested, temp -> client.downloadAttachmentToFile(key.gmailId(), key.folder(),
                key.uid(), key.index(), key.uidValidity(), temp.toFile()));
    }

    public static ApiClient.DownloadResult saveUnique(Path requested, DownloadWriter writer)
            throws IOException, InterruptedException {
        Path directory = requested.toAbsolutePath().normalize().getParent();
        Files.createDirectories(directory);
        String filename = AttachmentCleanupService.sanitizeFilename(requested.getFileName().toString());
        Path temporary = Files.createTempFile(directory, ".mailapp-", ".part");
        try {
            ApiClient.DownloadResult result = writer.write(temporary);
            if (result == null || Files.size(temporary) != result.bytesWritten()) {
                throw new IOException("Tệp tải xuống không đầy đủ.");
            }
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            while (true) {
                Path destination = AttachmentCleanupService.resolveUniqueFilePath(directory, filename);
                try {
                    // No REPLACE_EXISTING (and no ATOMIC_MOVE, whose replace semantics
                    // are provider-specific): never overwrite an existing path.
                    Files.move(temporary, destination);
                    return new ApiClient.DownloadResult(destination.toFile(), result.bytesWritten(), result.sha256Hex());
                } catch (FileAlreadyExistsException ignored) {
                    // Another download won the name. Pick the next free name.
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
