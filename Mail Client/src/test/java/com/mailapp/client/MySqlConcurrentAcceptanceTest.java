package com.mailapp.client;

import com.mailapp.client.model.RegisterRequest;
import com.mailapp.client.model.UserDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.net.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Concurrent registration against a fresh native MySQL server, not H2 or mocks. */
@EnabledIfEnvironmentVariable(named = "MAILAPP_E2E", matches = "1")
@EnabledIfEnvironmentVariable(named = "MAILAPP_E2E_DATABASE_ENGINE", matches = "MySQL")
class MySqlConcurrentAcceptanceTest {
    private ApiClient client() {
        assertEquals("1", System.getenv("MAILAPP_E2E_DISPOSABLE"));
        String url = System.getenv("MAILAPP_E2E_URL");
        URI uri = URI.create(url);
        assertEquals("https", uri.getScheme());
        assertTrue(List.of("localhost", "127.0.0.1").contains(uri.getHost()));
        assertTrue(uri.getPort() > 0 && uri.getPort() != 8443);
        File cert = new File(System.getenv("MAILAPP_E2E_CERT"));
        assertTrue(cert.isFile());
        return new ApiClient(url, cert);
    }

    private record Result(RegisterRequest request, UserDto user, ApiException failure) {}

    private Result register(RegisterRequest req, CyclicBarrier barrier) throws Exception {
        ApiClient api = client();
        barrier.await(10, TimeUnit.SECONDS);
        try { return new Result(req, api.register(req), null); }
        catch (ApiException ex) { return new Result(req, null, ex); }
    }

    private void race(RegisterRequest first, RegisterRequest second, String expectedCode) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Result> a = executor.submit(() -> register(first, barrier));
            Future<Result> b = executor.submit(() -> register(second, barrier));
            List<Result> outcomes = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(r -> r.user != null).count());
            Result winner = outcomes.stream().filter(r -> r.user != null).findFirst().orElseThrow();
            Result loser = outcomes.stream().filter(r -> r.failure != null).findFirst().orElseThrow();
            assertEquals(409, loser.failure.getStatusCode());
            assertEquals(expectedCode, loser.failure.getCode());
            assertEquals("USER", winner.user.role(), "Public registration must not accept requested ADMIN role");
            ApiClient verified = client();
            verified.login(winner.request.username(), winner.request.password());
            assertEquals(winner.user.id(), verified.getCurrentUser().id());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void competingUsernamesHaveOneWinner() throws Exception {
        String id = "race_" + UUID.randomUUID().toString().replace("-", "");
        race(new RegisterRequest(id, id + "a@test.local", "RaceFixture1!", "First", "ADMIN"),
                new RegisterRequest(id, id + "b@test.local", "RaceFixture2!", "Second", "USER"), "USERNAME_TAKEN");
    }

    @Test
    void competingCanonicalEmailsHaveOneWinner() throws Exception {
        String id = "race_" + UUID.randomUUID().toString().replace("-", "");
        race(new RegisterRequest(id + "a", id + "@test.local", "RaceFixture1!", "First", "USER"),
                new RegisterRequest(id + "b", id.toUpperCase(java.util.Locale.ROOT) + "@test.local", "RaceFixture2!", "Second", "USER"), "EMAIL_TAKEN");
    }
}
