package com.mailapp.service;

import com.mailapp.model.AuditLog;
import com.mailapp.model.User;
import com.mailapp.repository.AuditLogRepository;
import com.mailapp.repository.UserRepository;
import com.mailapp.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    private AuditLogServiceImpl auditLogService;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        auditLogService = new AuditLogServiceImpl(auditLogRepository, userRepository, transactionManager,
                mock(com.mailapp.service.impl.MailAuditRecovery.class));
    }

    @Test
    @DisplayName("Ghi audit log thành công với đầy đủ thông tin action, detail, ip và user")
    void testLogSuccess() {
        Long userId = 100L;
        User user = new User();
        user.setId(userId);
        user.setUsername("testuser");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        auditLogService.log(userId, "LINK_GMAIL", "Email: test@gmail.com", "192.168.1.100");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());

        AuditLog saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("LINK_GMAIL", saved.getAction());
        assertEquals("Email: test@gmail.com", saved.getDetail());
        assertEquals("192.168.1.100", saved.getIpAddress());
        assertNotNull(saved.getUser());
        assertEquals(userId, saved.getUser().getId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    @DisplayName("Ghi audit log cho khách (userId = null) không gây lỗi")
    void testLogGuestUser() {
        auditLogService.log(null, "LOGIN_FAILED", "Bad credentials", "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());

        AuditLog saved = captor.getValue();
        assertNotNull(saved);
        assertNull(saved.getUser());
        assertEquals("LOGIN_FAILED", saved.getAction());
        assertEquals("Bad credentials", saved.getDetail());
    }

    @Test
    void requestOverloadUsesEffectiveRemoteAddressNotSpoofedForwardedHeader() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "203.0.113.77");
        auditLogService.log(null, "LOGIN_FAILED", "Bad credentials", request);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());
        assertEquals("192.0.2.10", captor.getValue().getIpAddress());
    }

    @Test
    void standaloneAuditCommitFailureDoesNotTurnConfirmedExternalOperationIntoFailure() {
        doThrow(new org.springframework.transaction.TransactionSystemException("fixture commit failure"))
                .when(transactionManager).commit(any());
        assertDoesNotThrow(() -> auditLogService.log(null, "SEND_MAIL", "recipientCount=1", "127.0.0.1"));
        verify(auditLogRepository).saveAndFlush(any());
    }

    @Test
    void auditWriteFailureRollsBackAndPropagatesWithinBusinessTransaction() {
        when(auditLogRepository.saveAndFlush(any())).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("fixture insert rejected"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                    () -> auditLogService.log(null, "ADMIN_CHANGE_SETTING", "MAIL_PAGE_SIZE", "127.0.0.1"));
            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}

