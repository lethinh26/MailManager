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
        int maxAttachmentSizeMb
) {}

