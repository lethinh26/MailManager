package com.mailapp.client.util;

import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.UserDto;

public class SessionStore {

    private static volatile SessionStore instance;

    private String accessToken;
    private UserDto currentUser;
    private PublicConfigDto publicConfig;

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
        this.accessToken = null;
        this.currentUser = null;
    }
}
