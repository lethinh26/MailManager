package com.mailapp.client.util;

import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.UserDto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SessionStore {

    private static volatile SessionStore instance;

    private String accessToken;
    private UserDto currentUser;
    private PublicConfigDto publicConfig;
    private long generation;
    private final Map<AttachmentKey, CachedAttachment> attachmentCache = new HashMap<>();
    private record CachedAttachment(Path path, long size, FileTime modified, Object fileKey) {}

    public record AttachmentKey(Long gmailId, String folder, long uidValidity, long uid, int index) {}

    private SessionStore() {}

    public static SessionStore getInstance() {
        if (instance == null) {
            synchronized (SessionStore.class) {
                if (instance == null) {
                    instance = new SessionStore();
                }
            }
        }
        return instance;
    }

    public synchronized void saveSession(String accessToken, UserDto user) {
        generation++;
        attachmentCache.clear();
        this.accessToken = accessToken;
        this.currentUser = user;
    }

    public synchronized void updateToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public synchronized void updateUser(UserDto user) {
        this.currentUser = user;
    }

    public synchronized void setPublicConfig(PublicConfigDto config) {
        this.publicConfig = config;
    }

    public synchronized PublicConfigDto getPublicConfig() {
        return publicConfig;
    }

    public synchronized String getAccessToken() {
        return accessToken;
    }

    public synchronized String getAuthorizationHeader() {
        return hasActiveSession() ? "Bearer " + accessToken : null;
    }

    public synchronized long getGeneration() {
        return generation;
    }

    public synchronized Path findDownloadedAttachment(AttachmentKey key) {
        CachedAttachment cached = attachmentCache.get(key);
        if (cached == null) return null;
        CachedAttachment actual = inspect(cached.path());
        if (actual == null || !cached.equals(actual)) {
            attachmentCache.remove(key);
            return null;
        }
        return cached.path();
    }

    public synchronized void rememberDownloadedAttachment(long expectedGeneration, AttachmentKey key, Path file) {
        if (generation == expectedGeneration && hasActiveSession()) {
            CachedAttachment cached = inspect(file);
            if (cached != null) attachmentCache.put(key, cached);
        }
    }

    private static CachedAttachment inspect(Path file) {
        try {
            Path path = file.toAbsolutePath().normalize();
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attrs.isRegularFile()
                    ? new CachedAttachment(path, attrs.size(), attrs.lastModifiedTime(), attrs.fileKey()) : null;
        } catch (IOException | SecurityException ex) {
            return null;
        }
    }

    public synchronized UserDto getCurrentUser() {
        return currentUser;
    }

    public synchronized boolean hasActiveSession() {
        return accessToken != null && !accessToken.isBlank();
    }

    public synchronized boolean isLoggedIn() {
        return hasActiveSession();
    }

    public synchronized void clearSession() {
        generation++;
        attachmentCache.clear();
        this.accessToken = null;
        this.currentUser = null;
        this.publicConfig = null;
    }
}
