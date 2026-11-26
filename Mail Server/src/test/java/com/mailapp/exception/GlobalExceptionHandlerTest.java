package com.mailapp.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    private ErrorResponse conflict(String name, String path) {
        var request = new MockHttpServletRequest("POST", path);
        var sql = new SQLException("fixture rejected value must not leak", "23000", 1062);
        var cause = new ConstraintViolationException("fixture duplicate", sql, name);
        var response = new GlobalExceptionHandler().handleDataConflict(
                new DataIntegrityViolationException("fixture conflict", cause), request);
        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().message().contains("fixture rejected value"));
        return response.getBody();
    }

    @Test
    void legacyAndNewUsernameConstraintsReturnSameContract() {
        for (String name : new String[]{"users.username", "`users`.`username`",
                "'users.username'", "username", "uk_users_username_ci", "mailapp_db.users.username"}) {
            assertEquals("USERNAME_TAKEN", conflict(name, "/api/auth/register").code());
        }
        assertEquals("USERNAME_TAKEN", conflict("username", "/api/admin/users").code());
    }

    @Test
    void accountRevisionConflictUsesSafe409() {
        var response = new GlobalExceptionHandler().handleAccountRevisionConflict(
                new org.springframework.dao.OptimisticLockingFailureException("internal fixture details"),
                new MockHttpServletRequest("PATCH", "/api/gmail-accounts/1"));
        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("GMAIL_ACCOUNT_CHANGED", response.getBody().code());
        assertFalse(response.getBody().message().contains("internal fixture details"));
    }

    @Test
    void emailAndUnknownConstraintsRemainDistinctWithoutRawSqlMessage() {
        assertEquals("EMAIL_TAKEN", conflict("users.uk_users_email", "/api/auth/register").code());
        assertEquals("EMAIL_TAKEN", conflict("users.email", "/api/admin/users").code());
        assertEquals("CONFLICT", conflict("other.username", "/api/auth/register").code());
        assertEquals("CONFLICT", conflict("username", "/api/gmail-accounts").code());
        assertEquals("CONFLICT", conflict(null, "/api/auth/register").code());
    }
}
