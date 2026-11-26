package com.mailapp.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Gmail REST profile for the mailbox accessible by the granted token. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleGmailProfile(String emailAddress) {}
