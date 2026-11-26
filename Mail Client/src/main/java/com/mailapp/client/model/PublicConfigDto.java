package com.mailapp.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicConfigDto(
        boolean registrationEnabled,
        boolean oauth2LinkEnabled,
        boolean appPasswordLinkEnabled,
        boolean maintenanceMode,
        boolean sendMailEnabled,
        int maxGmailPerUser,
        int maxAttachmentSizeMb,
        int mailPageSize
) {
    public PublicConfigDto(boolean registrationEnabled, boolean oauth2LinkEnabled,
                           boolean appPasswordLinkEnabled, boolean maintenanceMode,
                           boolean sendMailEnabled, int maxGmailPerUser, int maxAttachmentSizeMb) {
        this(registrationEnabled, oauth2LinkEnabled, appPasswordLinkEnabled, maintenanceMode,
                sendMailEnabled, maxGmailPerUser, maxAttachmentSizeMb, 20);
    }

    public int effectiveMailPageSize() {
        return mailPageSize >= 10 && mailPageSize <= 100 ? mailPageSize : 20;
    }
}

