package com.mailapp.client;

import com.mailapp.client.model.GmailAccountDto;
import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.model.UserDto;
import com.mailapp.client.model.AdminClientDtos.*;
import com.mailapp.client.model.MailClientDtos.*;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.ui.dialogs.ComposeDialog;
import com.mailapp.client.ui.dialogs.GmailManagerDialog;
import com.mailapp.client.ui.frames.MainFrame;
import com.mailapp.client.ui.frames.LoginFrame;
import com.mailapp.client.ui.frames.RegisterFrame;
import com.mailapp.client.ui.frames.AdminFrame;
import com.mailapp.client.util.SessionStore;
import com.mailapp.client.util.FeatureAdmission;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SwingFeatureAcceptanceTest {
    private static final PublicConfigDto ENABLED = new PublicConfigDto(true, true, true, false, true, 5, 25, 30);
    private static final PublicConfigDto DISABLED = new PublicConfigDto(false, false, false, false, false, 5, 25, 50);

    private static JButton button(Object owner, String field) throws Exception {
        return (JButton) component(owner, field);
    }

    private static Object component(Object owner, String field) throws Exception {
        var f = owner.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(owner);
    }

    private static <T> T edt(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(future);
        return future.get();
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (edt(condition)) return;
            Thread.sleep(30);
        }
        fail("Swing worker did not update the UI before timeout");
    }

    @Test
    void featureAdmissionInvalidatesOldTicketsAcrossDisableAndRecovery() {
        FeatureAdmission gate = new FeatureAdmission(true);
        var first = gate.capture();
        gate.update(true);
        assertTrue(gate.isCurrent(first), "unchanged configuration does not invalidate submission");
        gate.update(false);
        var disabled = gate.capture();
        assertFalse(gate.isCurrent(first));
        assertFalse(gate.isCurrent(disabled));
        gate.update(true);
        assertFalse(gate.isCurrent(first), "re-enable must not revive the old submission");
        assertFalse(gate.isCurrent(disabled));
        assertTrue(gate.isCurrent(gate.capture()));
    }

    @Test
    void openRegisterFormRespondsToFlagChangesAndClearsPasswords() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        edt(() -> {
            RegisterFrame form = new RegisterFrame(new ApiClient("http://localhost:19999"), null);
            try {
                FeatureAdmission admission = (FeatureAdmission) component(form, "registrationAdmission");
                var beforeDisable = admission.capture();
                JPasswordField password = (JPasswordField) component(form, "passwordField");
                password.setText("example123");
                form.applyPublicConfig(DISABLED);
                assertFalse(password.isEnabled());
                assertEquals(0, password.getPassword().length);
                assertFalse(button(form, "registerButton").isEnabled());
                assertTrue(button(form, "backToLoginButton").isEnabled());
                form.applyPublicConfig(ENABLED);
                assertTrue(password.isEnabled());
                assertTrue(button(form, "registerButton").isEnabled());
                assertFalse(admission.isCurrent(beforeDisable));
                assertTrue(admission.isCurrent(admission.capture()));
                form.applyPublicConfig(null);
                assertFalse(button(form, "registerButton").isEnabled());
                assertFalse(password.isEnabled());
                form.applyPublicConfig(ENABLED);
                assertTrue(button(form, "registerButton").isEnabled());
                button(form, "registerButton").doClick();
                assertFalse(((JLabel) component(form, "errorLabel")).getText().isBlank());
            } finally {
                form.dispose();
            }
            return null;
        });
    }

    @Test
    void mailboxPaginationAndDirectStarUseCurrentIdentityWithoutOpeningMail() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicInteger flagCalls = new AtomicInteger();
        AtomicInteger detailCalls = new AtomicInteger();
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public List<GmailAccountDto> getGmailAccounts() {
                return List.of(new GmailAccountDto(1L, "fixture@test.local", "Fixture", "APP_PASSWORD", "ACTIVE", null, null, null));
            }
            @Override public byte[] getGmailAccountAvatar(Long id) { return new byte[0]; }
            @Override public List<MailFolderDto> getMailFolders(Long id) {
                return List.of(new MailFolderDto("INBOX", "INBOX", "Inbox", "INBOX", 25, 25));
            }
            @Override public MailListResponse getMails(Long id, String folder, int page, int size,
                    String q, Boolean unread, Boolean attachment, String from, String since) {
                List<MailSummaryDto> mails = new ArrayList<>();
                int first = page * size;
                long uidBase = "different".equals(q) ? 200 : 100;
                for (int i = first; i < Math.min(first + size, 25); i++) {
                    mails.add(new MailSummaryDto(uidBase - i, 77, new MailAddressDto("Fixture", "fixture@test.local"),
                            List.of(), "Mail " + i, "", "2026-11-20T10:00:00Z",
                            new MailFlagsDto(false, false, false, false), 10, false, 0));
                }
                return new MailListResponse(mails, page, size, 25, first + size < 25, 77);
            }
            @Override public MailDetailResponse getMailDetail(Long id, String folder, long uid,
                    boolean loadRemote, Long validity) {
                detailCalls.incrementAndGet();
                throw new AssertionError("Direct star must not open mail");
            }
            @Override public MailFlagsDto updateMailFlags(Long id, String folder, long uid,
                    Boolean seen, Boolean flagged, Long validity) {
                assertEquals(1L, id);
                assertEquals("INBOX", folder);
                assertEquals(100, uid);
                assertEquals(77L, validity);
                assertNull(seen);
                assertEquals(Boolean.TRUE, flagged);
                flagCalls.incrementAndGet();
                return new MailFlagsDto(false, true, false, false);
            }
        };
        SessionStore session = SessionStore.getInstance();
        session.clearSession();
        session.setPublicConfig(new PublicConfigDto(true,true,true,false,true,5,25,20));
        MainFrame frame = edt(() -> {
            MainFrame f = new MainFrame(fake, false);
            f.loadGmailAccounts();
            return f;
        });
        try {
            JList<?> list = edt(() -> (JList<?>) component(frame, "mailList"));
            await(() -> list.getModel().getSize() == 20 && button(frame, "nextPageBtn").isEnabled());
            edt(() -> { ((JComboBox<?>) component(frame, "pageSizeCombo")).setSelectedItem(10); return null; });
            await(() -> list.getModel().getSize() == 10 && button(frame, "nextPageBtn").isEnabled());
            edt(() -> { button(frame, "nextPageBtn").doClick(); return null; });
            await(() -> list.getModel().getSize() == 10
                    && ((MailSummaryDto) list.getModel().getElementAt(0)).uid() == 90
                    && button(frame, "previousPageBtn").isEnabled());
            edt(() -> { button(frame, "previousPageBtn").doClick(); return null; });
            await(() -> list.getModel().getSize() == 10
                    && ((MailSummaryDto) list.getModel().getElementAt(0)).uid() == 100);
            edt(() -> {
                list.setSize(400, 600);
                list.setFixedCellHeight(60);
                Rectangle cell = list.getCellBounds(0, 0);
                assertNotNull(cell);
                list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_PRESSED,
                        System.currentTimeMillis(), 0, cell.x + cell.width - 12,
                        cell.y + cell.height / 2, 1, false, MouseEvent.BUTTON1));
                return null;
            });
            await(() -> ((MailSummaryDto) list.getModel().getElementAt(0)).flags().flagged());
            assertEquals(1, flagCalls.get());
            assertEquals(0, detailCalls.get());
            // Editing the query then paging must never merge different search results.
            edt(() -> {
                ((JTextField) component(frame, "searchField")).setText("different");
                button(frame, "loadMoreBtn").doClick();
                return null;
            });
            await(() -> list.getModel().getSize() == 10
                    && ((MailSummaryDto) list.getModel().getElementAt(0)).uid() == 200
                    && button(frame, "nextPageBtn").isEnabled());
            assertFalse(edt(() -> button(frame, "previousPageBtn").isEnabled()));
            edt(() -> { button(frame, "loadMoreBtn").doClick(); return null; });
            await(() -> list.getModel().getSize() == 20
                    && ((MailSummaryDto) list.getModel().getElementAt(19)).uid() == 181);
            edt(() -> {
                ((JTextField) component(frame, "searchField")).setText("");
                button(frame, "nextPageBtn").doClick();
                return null;
            });
            await(() -> list.getModel().getSize() == 10
                    && ((MailSummaryDto) list.getModel().getElementAt(0)).uid() == 100
                    && button(frame, "nextPageBtn").isEnabled());
            assertFalse(edt(() -> button(frame, "previousPageBtn").isEnabled()));
        } finally {
            edt(() -> { frame.dispose(); return null; });
            session.clearSession();
        }
    }

    @Test
    void composePreservesPendingAndCommittedRecipientDrafts() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        edt(() -> {
            ComposeDialog compose = new ComposeDialog(null, new ApiClient("http://localhost:19999"),
                    List.of(), null, DISABLED, null);
            try {
                var hasDraft = ComposeDialog.class.getDeclaredMethod("hasDraft");
                hasDraft.setAccessible(true);
                assertEquals(false, hasDraft.invoke(compose));
                Object bcc = component(compose, "bccInput");
                JTextField pendingBcc = (JTextField) component(bcc, "inputField");
                pendingBcc.setText("draft@test.local");
                assertEquals(true, hasDraft.invoke(compose));
                pendingBcc.setText("");
                assertEquals(false, hasDraft.invoke(compose));

                Object cc = component(compose, "ccInput");
                JTextField pendingCc = (JTextField) component(cc, "inputField");
                pendingCc.setText("cc@test.local");
                Object key = pendingCc.getInputMap().get(KeyStroke.getKeyStroke(','));
                assertNotNull(key);
                pendingCc.getActionMap().get(key).actionPerformed(
                        new java.awt.event.ActionEvent(pendingCc, 0, "comma"));
                assertEquals("", pendingCc.getText());
                var getEmails = cc.getClass().getDeclaredMethod("getEmails");
                getEmails.setAccessible(true);
                assertEquals(List.of("cc@test.local"), getEmails.invoke(cc));
                assertEquals(true, hasDraft.invoke(compose));
            } finally {
                compose.dispose();
            }
            return null;
        });
    }

    @Test
    void composeLocksDraftDuringSubmissionAndPreservesUnknownOutcome() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        var account = new GmailAccountDto(1L, "fixture@test.local", "Fixture", "APP_PASSWORD", "ACTIVE", null, null, null);
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public PublicConfigDto getPublicConfig() { return ENABLED; }
            @Override public SendMailResponse sendMailMultipart(SendMailPayload payload, List<java.io.File> files)
                    throws java.io.IOException, InterruptedException {
                calls.incrementAndGet();
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                throw new java.io.IOException("fixture private diagnostic");
            }
        };
        ComposeDialog dialog = edt(() -> new ComposeDialog(null, fake, List.of(account), account, ENABLED, null));
        try {
            edt(() -> {
                ((JTextField) component(component(dialog, "toInput"), "inputField")).setText("recipient@test.local");
                ((JTextField) component(dialog, "subjectField")).setText("Draft");
                ((JTextArea) component(dialog, "bodyArea")).setText("Body");
                button(dialog, "sendBtn").doClick();
                return null;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            edt(() -> {
                assertFalse(((JTextField) component(dialog, "subjectField")).isEditable());
                assertFalse(((JTextArea) component(dialog, "bodyArea")).isEditable());
                assertFalse(((JTextField) component(component(dialog, "toInput"), "inputField")).isEnabled());
                assertFalse(button(dialog, "sendBtn").isEnabled());
                return null;
            });
            release.countDown();
            await(() -> Boolean.TRUE.equals(component(dialog, "uncertainSend")) && button(dialog, "sendBtn").isEnabled());
            edt(() -> {
                assertTrue(((JTextArea) component(dialog, "bodyArea")).isEditable());
                assertEquals("Draft", ((JTextField) component(dialog, "subjectField")).getText());
                assertEquals("Body", ((JTextArea) component(dialog, "bodyArea")).getText());
                String status = ((JLabel) component(dialog, "statusLabel")).getText();
                assertTrue(status.contains("Chưa xác định"));
                assertFalse(status.contains("private diagnostic"));
                dialog.applyPublicConfig(null);
                assertFalse(button(dialog, "sendBtn").isEnabled());
                dialog.applyPublicConfig(ENABLED);
                assertEquals(status, ((JLabel) component(dialog, "statusLabel")).getText());
                return null;
            });
            assertEquals(1, calls.get());
            assertFalse(com.mailapp.client.net.ApiException.isMutationOutcomeUncertain(
                    new com.mailapp.client.net.ApiException(503, "AUDIT_UNAVAILABLE", "fixture")));
        } finally {
            release.countDown();
            edt(() -> { dialog.dispose(); return null; });
        }
    }

    @Test
    void gmailManagerIgnoresLateAccountListAfterNewerRefresh() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public List<GmailAccountDto> getGmailAccounts() throws InterruptedException {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    started.countDown();
                    try { release.await(5, TimeUnit.SECONDS); }
                    finally { finished.countDown(); }
                }
                return List.of(new GmailAccountDto((long) call, "fixture@test.local",
                        "Fixture", "APP_PASSWORD", "ACTIVE", null, null, null));
            }
        };
        GmailManagerDialog dialog = edt(() -> new GmailManagerDialog(null, fake, ENABLED, null, null));
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            edt(() -> {
                var refresh = GmailManagerDialog.class.getDeclaredMethod("refreshAccounts");
                refresh.setAccessible(true);
                refresh.invoke(dialog);
                return null;
            });
            await(() -> ((List<?>) component(dialog, "currentAccounts")).stream()
                    .anyMatch(a -> ((GmailAccountDto) a).id() == 2L));
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertEquals(2L, edt(() -> ((GmailAccountDto) ((List<?>) component(dialog, "currentAccounts")).getFirst()).id()));
        } finally {
            release.countDown();
            edt(() -> { dialog.dispose(); return null; });
        }
    }

    @Test
    void adminIgnoresOlderSearchAndDisablesSettingsUntilLoaded() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public AdminPageResponse<AdminUserSummary> adminListUsers(String q, String role,
                    Boolean enabled, int page, int size, String sortBy, String sortDir) throws InterruptedException {
                if ("old".equals(q)) {
                    started.countDown();
                    try { release.await(5, TimeUnit.SECONDS); }
                    finally { completed.countDown(); }
                }
                long id = "old".equals(q) ? 1L : 2L;
                return new AdminPageResponse<>(List.of(new AdminUserSummary(id, q, q, q + "@test.local",
                        "USER", true, false, 0L, null, null)), 0, size, 1, 1);
            }
            @Override public List<AdminSettingItem> adminGetSettings() {
                return List.of(new AdminSettingItem("MAIL_PAGE_SIZE", "20", "INTEGER", 10, 100, "", null, null));
            }
        };
        AdminFrame frame = edt(() -> new AdminFrame(fake, null, false));
        try {
            edt(() -> {
                assertFalse(button(frame, "settingsSaveBtn").isEnabled());
                ((JTextField) component(frame, "userSearchField")).setText("old");
                frame.loadUsers();
                return null;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            edt(() -> {
                ((JTextField) component(frame, "userSearchField")).setText("new");
                frame.loadUsers();
                frame.loadSettings();
                return null;
            });
            JTable table = edt(() -> (JTable) component(frame, "userTable"));
            await(() -> table.getRowCount() == 1 && Long.valueOf(2).equals(table.getValueAt(0, 0))
                    && button(frame, "settingsSaveBtn").isEnabled());
            release.countDown();
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertEquals(2L, edt(() -> table.getValueAt(0, 0)));
        } finally {
            release.countDown();
            edt(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void disposedAdminWindowDoesNotPublishLateUserRows() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public AdminPageResponse<AdminUserSummary> adminListUsers(String q, String role,
                    Boolean enabled, int page, int size, String sortBy, String sortDir) throws InterruptedException {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                    return new AdminPageResponse<>(List.of(new AdminUserSummary(99L, "fixture",
                            "Fixture", "fixture@example.test", "USER", true, false, 0L, null, null)), 0, size, 1, 1);
                } finally {
                    returned.countDown();
                }
            }
        };
        AdminFrame frame = edt(() -> new AdminFrame(fake, null, false));
        JTable table = edt(() -> (JTable) component(frame, "userTable"));
        try {
            edt(() -> { frame.loadUsers(); return null; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            edt(() -> { frame.dispose(); return null; });
            release.countDown();
            assertTrue(returned.await(5, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertEquals(0, edt(table::getRowCount));
        } finally {
            release.countDown();
            edt(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void adminPagingRestartsWhenUserOrLogCriteriaChanges() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public AdminPageResponse<AdminUserSummary> adminListUsers(String q, String role,
                    Boolean enabled, int page, int size, String sortBy, String sortDir) {
                long id = ("new".equals(q) ? 200 : 100) + page;
                return new AdminPageResponse<>(List.of(new AdminUserSummary(id, "fixture", "Fixture",
                        "fixture@test.local", "USER", true, false, 0L, null, null)), page, size, 41, 3);
            }
            @Override public AdminPageResponse<AdminAuditLogItem> adminSearchLogs(Long userId, String username,
                    String action, String from, String to, int page, int size) {
                long id = ("new".equals(username) ? 400 : 300) + page;
                return new AdminPageResponse<>(List.of(new AdminAuditLogItem(id, 1L, "fixture",
                        "MAIL_DELETE", "fixture marker", null, null)), page, size, 51, 3);
            }
        };
        AdminFrame frame = edt(() -> {
            AdminFrame f = new AdminFrame(fake, null, false);
            f.loadUsers();
            f.loadLogs();
            return f;
        });
        try {
            JTable users = edt(() -> (JTable) component(frame, "userTable"));
            JTable logs = edt(() -> (JTable) component(frame, "logTable"));
            await(() -> button(frame, "userNextBtn").isEnabled() && button(frame, "logNextBtn").isEnabled());
            edt(() -> {
                button(frame, "userNextBtn").doClick();
                button(frame, "logNextBtn").doClick();
                return null;
            });
            await(() -> Long.valueOf(101).equals(users.getValueAt(0, 0))
                    && Long.valueOf(301).equals(logs.getValueAt(0, 0))
                    && button(frame, "userNextBtn").isEnabled() && button(frame, "logNextBtn").isEnabled());
            edt(() -> {
                ((JTextField) component(frame, "userSearchField")).setText("new");
                ((JTextField) component(frame, "logUserField")).setText("new");
                button(frame, "userNextBtn").doClick();
                button(frame, "logNextBtn").doClick();
                return null;
            });
            await(() -> Long.valueOf(200).equals(users.getValueAt(0, 0))
                    && Long.valueOf(400).equals(logs.getValueAt(0, 0))
                    && button(frame, "userNextBtn").isEnabled() && button(frame, "logNextBtn").isEnabled());
            assertFalse(edt(() -> button(frame, "userPrevBtn").isEnabled()));
            assertFalse(edt(() -> button(frame, "logPrevBtn").isEnabled()));
        } finally {
            edt(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void loginPublicControlsFailClosedWithoutConfiguration() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        edt(() -> {
            LoginFrame frame = new LoginFrame(new ApiClient("http://localhost:19999"), false);
            try {
                frame.applyPublicConfig(ENABLED);
                assertTrue(button(frame, "registerButton").isVisible());
                assertTrue(button(frame, "googleLoginButton").isVisible());
                frame.applyPublicConfig(null);
                assertFalse(button(frame, "registerButton").isVisible());
                assertFalse(button(frame, "googleLoginButton").isVisible());
                frame.applyPublicConfig(ENABLED);
                assertTrue(button(frame, "registerButton").isVisible());
            } finally {
                frame.dispose();
            }
            return null;
        });
    }

    @Test
    void composeRecipientStripKeepsInputUsableAndNoAccountDisablesSend() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        edt(() -> {
            ComposeDialog dialog = new ComposeDialog(null, new ApiClient("http://localhost:19999"),
                    List.of(), null, ENABLED, null);
            try {
                JPanel input = (JPanel) component(dialog, "toInput");
                JTextField field = (JTextField) component(input, "inputField");
                for (int i = 0; i < 12; i++) {
                    field.setText("recipient" + i + "@example.test");
                    field.postActionEvent();
                }
                input.setSize(320, input.getPreferredSize().height);
                input.doLayout();
                JScrollPane scroll = (JScrollPane) component(input, "chipsScroll");
                assertTrue(scroll.isVisible());
                assertTrue(field.getWidth() >= 200);
                assertTrue(field.getHeight() > 0);
                assertEquals(12, ((JPanel) component(input, "chipsPanel")).getComponentCount());
                dialog.applyPublicConfig(ENABLED);
                assertFalse(button(dialog, "sendBtn").isEnabled());
            } finally {
                dialog.dispose();
            }
            return null;
        });
    }

    @Test
    void composeRejectsStaleGenerationBeforeSubmission() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicInteger requests = new AtomicInteger();
        var account = new GmailAccountDto(1L, "fixture@test.local", "Fixture", "APP_PASSWORD", "ACTIVE", null, null, null);
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public PublicConfigDto getPublicConfig() {
                requests.incrementAndGet();
                return ENABLED;
            }
        };
        ComposeDialog dialog = edt(() -> new ComposeDialog(null, fake, List.of(account), account, ENABLED, null));
        try {
            SessionStore.getInstance().clearSession();
            edt(() -> {
                dialog.applyPublicConfig(ENABLED);
                assertFalse(button(dialog, "sendBtn").isEnabled());
                ((JTextField) component(component(dialog, "toInput"), "inputField")).setText("recipient@test.local");
                button(dialog, "sendBtn").doClick();
                return null;
            });
            assertEquals(0, requests.get());
        } finally {
            edt(() -> { dialog.dispose(); return null; });
        }
    }

    @Test
    void composeFlagOffOnDuringPreflightDoesNotDispatchOldDraft() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        CountDownLatch enteredConfig = new CountDownLatch(1);
        CountDownLatch releaseConfig = new CountDownLatch(1);
        AtomicInteger dispatches = new AtomicInteger();
        var account = new GmailAccountDto(1L, "fixture@test.local", "Fixture", "APP_PASSWORD", "ACTIVE", null, null, null);
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public PublicConfigDto getPublicConfig() throws InterruptedException {
                enteredConfig.countDown();
                if (!releaseConfig.await(5, TimeUnit.SECONDS)) throw new AssertionError("config not released");
                return ENABLED;
            }
            @Override public SendMailResponse sendMailMultipart(SendMailPayload payload, List<java.io.File> files) {
                dispatches.incrementAndGet();
                throw new AssertionError("Invalidated request must not reach dispatch");
            }
        };
        ComposeDialog dialog = edt(() -> new ComposeDialog(null, fake, List.of(account), account, ENABLED, null));
        try {
            edt(() -> {
                ((JTextField) component(component(dialog, "toInput"), "inputField")).setText("recipient@test.local");
                ((JTextField) component(dialog, "subjectField")).setText("Draft");
                ((JTextArea) component(dialog, "bodyArea")).setText("Body");
                button(dialog, "sendBtn").doClick();
                return null;
            });
            assertTrue(enteredConfig.await(5, TimeUnit.SECONDS));
            edt(() -> {
                dialog.applyPublicConfig(DISABLED);
                dialog.applyPublicConfig(ENABLED);
                assertFalse(button(dialog, "sendBtn").isEnabled(), "pending worker still owns the draft");
                return null;
            });
            releaseConfig.countDown();
            await(() -> button(dialog, "sendBtn").isEnabled()
                    && !Boolean.TRUE.equals(component(dialog, "sending")));
            edt(() -> {
                assertEquals("Draft", ((JTextField) component(dialog, "subjectField")).getText());
                assertEquals("Body", ((JTextArea) component(dialog, "bodyArea")).getText());
                assertTrue(((JTextArea) component(dialog, "bodyArea")).isEditable());
                assertFalse(Boolean.TRUE.equals(component(dialog, "uncertainSend")));
                assertTrue(((JLabel) component(dialog, "statusLabel")).getText().contains("Bản nháp"));
                return null;
            });
            assertEquals(0, dispatches.get());
        } finally {
            releaseConfig.countDown();
            edt(() -> { dialog.dispose(); return null; });
        }
    }

    @Test
    void composeMalformedAcknowledgementPreservesUnknownOutcome() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AtomicInteger calls = new AtomicInteger();
        var account = new GmailAccountDto(1L, "fixture@test.local", "Fixture", "APP_PASSWORD", "ACTIVE", null, null, null);
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public PublicConfigDto getPublicConfig() { return ENABLED; }
            @Override public SendMailResponse sendMailMultipart(SendMailPayload payload, List<java.io.File> files) {
                calls.incrementAndGet();
                return new SendMailResponse(null, "UNKNOWN");
            }
        };
        ComposeDialog dialog = edt(() -> new ComposeDialog(null, fake, List.of(account), account, ENABLED, null));
        try {
            edt(() -> {
                ((JTextField) component(component(dialog, "toInput"), "inputField")).setText("recipient@test.local");
                ((JTextField) component(dialog, "subjectField")).setText("Draft");
                button(dialog, "sendBtn").doClick();
                return null;
            });
            await(() -> Boolean.TRUE.equals(component(dialog, "uncertainSend")));
            assertEquals(1, calls.get());
            assertEquals("Draft", edt(() -> ((JTextField) component(dialog, "subjectField")).getText()));
            assertFalse(edt(() -> Boolean.TRUE.equals(component(dialog, "sentSuccessfully"))));
        } finally {
            edt(() -> { dialog.dispose(); return null; });
        }
    }

    @Test
    void composeAndGmailControlsFollowLiveFeatureFlagsOnEdt() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Cần môi trường đồ họa để tạo JDialog");
        ApiClient fake = new ApiClient("http://localhost:19999") {
            @Override public List<GmailAccountDto> getGmailAccounts() { return List.of(); }
        };
        SwingUtilities.invokeAndWait(() -> {
            ComposeDialog compose = null;
            GmailManagerDialog gmail = null;
            try {
                var account = new GmailAccountDto(1L, "fixture@test.local", "Fixture", "APP_PASSWORD", "ACTIVE", null, null, null);
                compose = new ComposeDialog(null, fake, List.of(account), account, DISABLED, null);
                assertFalse(button(compose, "sendBtn").isEnabled());
                compose.applyPublicConfig(ENABLED);
                assertTrue(button(compose, "sendBtn").isEnabled());
                compose.applyPublicConfig(DISABLED);
                assertFalse(button(compose, "sendBtn").isEnabled());

                gmail = new GmailManagerDialog(null, fake, DISABLED, null, null);
                assertFalse(button(gmail, "oauthBtn").isVisible());
                assertFalse(button(gmail, "appPassBtn").isVisible());
                gmail.applyPublicConfig(ENABLED);
                assertTrue(button(gmail, "oauthBtn").isVisible());
                assertTrue(button(gmail, "appPassBtn").isVisible());
                FeatureAdmission admission = (FeatureAdmission) component(gmail, "appPasswordAdmission");
                var beforeDisable = admission.capture();
                gmail.applyPublicConfig(DISABLED);
                assertFalse(button(gmail, "oauthBtn").isVisible());
                assertFalse(button(gmail, "appPassBtn").isVisible());
                gmail.applyPublicConfig(ENABLED);
                assertFalse(admission.isCurrent(beforeDisable));
                assertTrue(admission.isCurrent(admission.capture()));
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                if (gmail != null) gmail.dispose();
                if (compose != null) compose.dispose();
            }
        });
    }

    @Test
    void mainFrameExposesPageSizesAndRestrictsAdminAndComposeControls() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Cần môi trường đồ họa để tạo JFrame");
        ApiClient fake = new ApiClient("http://localhost:19999");
        SessionStore session = SessionStore.getInstance();
        session.clearSession();
        try {
            session.setPublicConfig(DISABLED);
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame(fake, false);
                try {
                    frame.populateMockupForScreenshots(
                            new UserDto(1L, "tester", "Tester", "test@example.com", "USER", false, null),
                            List.of(), List.of(), List.of(), null);
                    assertFalse(button(frame, "composeBtn").isEnabled());
                    assertFalse(((JMenu) component(frame, "adminMenu")).isVisible());
                    JComboBox<?> sizes = (JComboBox<?>) component(frame, "pageSizeCombo");
                    assertEquals(4, sizes.getItemCount());
                    assertEquals(0, sizes.getItemAt(0));
                    assertEquals(10, sizes.getItemAt(1));
                    assertEquals(20, sizes.getItemAt(2));
                    assertEquals(50, sizes.getItemAt(3));
                    var apply = MainFrame.class.getDeclaredMethod("applyPublicConfig", PublicConfigDto.class);
                    apply.setAccessible(true);
                    apply.invoke(frame, ENABLED);
                    assertTrue(button(frame, "composeBtn").isEnabled());
                    frame.updateUserUI(new UserDto(2L, "admin", "Admin", "admin@example.com", "ADMIN", false, null));
                    assertTrue(((JMenu) component(frame, "adminMenu")).isVisible());
                } catch (Exception e) {
                    throw new AssertionError(e);
                } finally {
                    frame.dispose();
                }
            });
        } finally {
            session.clearSession();
        }
    }
}
