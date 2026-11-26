package com.mailapp.service.impl;

import com.mailapp.model.MailAuditOutbox;
import com.mailapp.security.AesCipher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * DB-independent receipts for already observed remote outcomes. The durable
 * DB PREPARED row remains the authority for whether an intent exists. Receipt
 * contents are authenticated with the existing application AES-GCM key.
 * This journal cannot call SMTP/IMAP or infer an outcome from an intent.
 *
 * One process owns a private directory. A complete forced .pending file is
 * also recoverable after a process dies before the atomic rename. Corrupt or
 * foreign files are never treated as success and never automatically deleted.
 */
@Component
public final class MailAuditReceiptJournal implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MailAuditReceiptJournal.class);
    private static final String FORMAT = "mail-audit-receipt-v1";
    private static final int MAX_BYTES = 8192;
    private static final int MAX_RECEIPTS = 4096;
    private final AesCipher cipher;
    private final Path root;
    private final FileChannel ownerChannel;
    private final FileLock ownerLock;
    private boolean invalidReceipts;
    // Runtime I/O/validation failures must also stop admitting new mutations.
    // Bounded by the reserved slots; a successful retry clears its own entry.
    private final Set<String> failedReceipts = new HashSet<>();

    public record Receipt(String id, String outcome, String detail) {
        public Receipt {
            if (id == null || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
                throw new IllegalArgumentException("Invalid audit receipt ID");
            if (!MailAuditOutbox.CONFIRMED.equals(outcome) && !MailAuditOutbox.UNCERTAIN.equals(outcome))
                throw new IllegalArgumentException("Invalid audit receipt outcome");
            if (detail != null && (detail.length() > 500 || !MailAuditOutbox.CONFIRMED.equals(outcome)))
                throw new IllegalArgumentException("Invalid audit receipt metadata");
        }
    }

    public MailAuditReceiptJournal(AesCipher cipher,
            @Value("${mail.audit.receipt-dir:./data/mail-audit-receipts}") String location) {
        this.cipher = cipher;
        // Tests may explicitly disable persistent receipts and exercise DB-only
        // behavior. A normal server must have a writable, owned directory.
        if (location == null || location.isBlank()) {
            root = null;
            ownerChannel = null;
            ownerLock = null;
            return;
        }
        FileChannel channel = null;
        try {
            root = Path.of(location).toAbsolutePath().normalize();
            if (root.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.createDirectories(root, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
                if (!Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS)
                        .equals(PosixFilePermissions.fromString("rwx------")))
                    throw new IOException("Audit directory must be private");
            } else {
                Files.createDirectories(root);
            }
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root))
                throw new IOException("Audit directory is not an owned real path");
            channel = FileChannel.open(root.resolve(".owner.lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("Audit directory already owned");
            ownerChannel = channel;
            ownerLock = lock;
        } catch (IOException | RuntimeException ex) {
            if (channel != null) try { channel.close(); } catch (IOException ignored) {}
            // Do not print file names, raw records or key information.
            throw new IllegalStateException("Cannot initialize private mail audit receipt directory ("
                    + ex.getClass().getSimpleName() + ")");
        }
    }

    public boolean enabled() { return root != null; }
    public synchronized boolean canAcceptNew() {
        return !enabled() || (!invalidReceipts && failedReceipts.isEmpty()
                && ownerLock.isValid() && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                && Files.isWritable(root));
    }

    /** Startup scan is bounded; invalid files remain for authorized investigation. */
    public synchronized Map<String, Receipt> recover() {
        if (!enabled()) return Map.of();
        Map<String, Receipt> result = new LinkedHashMap<>();
        Set<String> conflicts = new HashSet<>();
        int bad = 0, scanned = 0;
        boolean incomplete = false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(root)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".receipt") && !name.endsWith(".pending")) continue;
                if (++scanned > MAX_RECEIPTS * 2) {
                    bad++;
                    incomplete = true;
                    break;
                }
                try {
                    Receipt r = read(file);
                    Receipt previous = result.putIfAbsent(r.id(), r);
                    if (previous != null && !previous.equals(r)) conflicts.add(r.id());
                } catch (IOException | RuntimeException ex) {
                    bad++;
                    // A corrupt sibling must not be erased by replay of the
                    // other name, even when that other receipt is valid.
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) conflicts.add(name.substring(0, dot));
                }
            }
        } catch (IOException | RuntimeException ex) {
            bad++;
            incomplete = true;
        }
        for (String id : conflicts) result.remove(id);
        invalidReceipts = bad != 0 || !conflicts.isEmpty() || result.size() > MAX_RECEIPTS;
        if (invalidReceipts) log.error("MAIL_AUDIT_RECEIPT_INVALID count={} conflicts={}", bad, conflicts.size());
        // If enumeration was incomplete, a conflicting sibling could have
        // been outside the scan. Do not trust a partially scanned inventory.
        if (incomplete || result.size() > MAX_RECEIPTS) return Map.of();
        return result;
    }

    public synchronized void write(Receipt receipt) throws IOException {
        if (!enabled()) return;
        try {
            writeOwned(receipt);
            failedReceipts.remove(receipt.id());
        } catch (IOException | RuntimeException ex) {
            failedReceipts.add(receipt.id());
            throw ex;
        }
    }

    private void requireOwnedRoot() throws IOException {
        if (!ownerLock.isValid() || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !root.toRealPath().equals(root))
            throw new IOException("Audit journal ownership lost");
    }

    private void writeOwned(Receipt receipt) throws IOException {
        requireOwnedRoot();
        Path finalPath = root.resolve(receipt.id() + ".receipt");
        Path pendingPath = root.resolve(receipt.id() + ".pending");
        boolean finalExists = validateIfPresent(finalPath, receipt);
        boolean pendingExists = validateIfPresent(pendingPath, receipt);
        if (finalExists) return;
        if (!pendingExists) {
            String detail = receipt.detail() == null ? "-" : Base64.getEncoder()
                    .encodeToString(receipt.detail().getBytes(StandardCharsets.UTF_8));
            byte[] bytes = cipher.encrypt(FORMAT + "\n" + receipt.id() + "\n"
                    + receipt.outcome() + "\n" + detail).getBytes(StandardCharsets.US_ASCII);
            if (bytes.length > MAX_BYTES) throw new IOException("Receipt too large");
            try (FileChannel file = FileChannel.open(pendingPath, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) file.write(buffer);
                file.force(true);
            }
        }
        // A forced complete .pending file is valid even if rename is unsupported.
        try {
            Files.move(pendingPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            // No non-atomic fallback that could lose both names on failure.
        }
    }

    /** Do not conflate access errors, symlinks or corrupt files with absence. */
    private boolean validateIfPresent(Path file, Receipt expected) throws IOException {
        try {
            Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            return false;
        }
        if (!read(file).equals(expected)) throw new IOException("Conflicting audit receipt");
        return true;
    }

    private Receipt read(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a receipt");
        ByteBuffer buffer = ByteBuffer.allocate(MAX_BYTES + 1);
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            while (buffer.hasRemaining() && in.read(buffer) >= 0) {}
        }
        if (buffer.position() == 0 || buffer.position() > MAX_BYTES) throw new IOException("Receipt length");
        String encoded = new String(buffer.array(), 0, buffer.position(), StandardCharsets.US_ASCII);
        String[] fields = cipher.decrypt(encoded).split("\n", -1);
        if (fields.length != 4 || !FORMAT.equals(fields[0])
                || !(file.getFileName().toString().equals(fields[1] + ".receipt")
                  || file.getFileName().toString().equals(fields[1] + ".pending")))
            throw new IOException("Receipt format");
        String detail = "-".equals(fields[3]) ? null
                : new String(Base64.getDecoder().decode(fields[3]), StandardCharsets.UTF_8);
        return new Receipt(fields[1], fields[2], detail);
    }

    public synchronized void remove(Receipt receipt) throws IOException {
        if (!enabled()) return;
        try {
            requireOwnedRoot();
            Path finalPath = root.resolve(receipt.id() + ".receipt");
            Path pendingPath = root.resolve(receipt.id() + ".pending");
            // Verify BOTH names before deleting either, even after a DB commit.
            // A corrupt or conflicting sibling is evidence, not disposable data.
            boolean finalExists = validateIfPresent(finalPath, receipt);
            boolean pendingExists = validateIfPresent(pendingPath, receipt);
            if (finalExists) Files.delete(finalPath);
            if (pendingExists) Files.delete(pendingPath);
            failedReceipts.remove(receipt.id());
        } catch (IOException | RuntimeException ex) {
            failedReceipts.add(receipt.id());
            throw ex;
        }
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        if (ownerChannel != null) {
            try { ownerLock.release(); } catch (IOException ignored) {}
            try { ownerChannel.close(); } catch (IOException ignored) {}
        }
    }
}
