package com.mailapp.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2StateStoreTest {

    private final OAuth2StateStore stateStore = new OAuth2StateStore();

    @Test
    @DisplayName("Sinh state và tiêu thụ thành công đúng userId và codeVerifier (chống replay)")
    void testGenerateAndConsumeState() {
        Long userId = 42L;
        String verifier = "sample-code-verifier-1234567890-abcdefghijklmnop";
        String state = stateStore.generateState(userId, verifier);

        assertNotNull(state);
        assertFalse(state.isBlank());

        // Tiêu thụ lần đầu -> thành công
        Optional<OAuth2StateStore.StateEntry> entryOpt = stateStore.validateAndConsumeState(state);
        assertTrue(entryOpt.isPresent());
        assertEquals(userId, entryOpt.get().userId());
        assertEquals(verifier, entryOpt.get().codeVerifier());

        // Tiêu thụ lần 2 -> phải trả về empty vì đã bị xoá khỏi store
        Optional<OAuth2StateStore.StateEntry> replayOpt = stateStore.validateAndConsumeState(state);
        assertTrue(replayOpt.isEmpty());
    }

    @Test
    @DisplayName("State không tồn tại hoặc null phải trả về empty")
    void testInvalidState() {
        assertTrue(stateStore.validateAndConsumeState(null).isEmpty());
        assertTrue(stateStore.validateAndConsumeState("").isEmpty());
        assertTrue(stateStore.validateAndConsumeState("non-existent-state").isEmpty());
    }

    @Test
    void stateAndPendingPollExpireAfterFiveMinutesWithoutSleeping() {
        var ticks = new java.util.concurrent.atomic.AtomicLong();
        OAuth2StateStore store = new OAuth2StateStore(ticks::get);
        var expiredLink = store.generateLinkState(42L, "verifier", null);
        var expiredLogin = store.generateGoogleLoginState("verifier");
        var beforeExpiry = store.generateState(42L, "verifier");
        ticks.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(299));
        assertTrue(store.validateAndConsumeState(beforeExpiry).isPresent());
        assertTrue(store.pollLinkSession(expiredLink.pollToken(), 42L).isPresent());
        ticks.set(java.util.concurrent.TimeUnit.MINUTES.toNanos(5));
        assertTrue(store.validateAndConsumeState(expiredLink.state()).isEmpty());
        assertTrue(store.pollLinkSession(expiredLink.pollToken(), 42L).isEmpty());
        assertTrue(store.validateAndConsumeState(expiredLogin.state()).isEmpty());
        assertTrue(store.pollLoginSession(expiredLogin.pollToken()).isEmpty());
    }
}
