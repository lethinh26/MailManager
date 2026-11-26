package com.mailapp.client.util;

/**
 * A feature toggle invalidates pending, not-yet-dispatched submissions.
 * Re-enabling a feature requires a new explicit user action, not reuse of
 * a request captured before the disable transition.
 *
 * Updates normally come from the EDT; workers read the immutable ticket.
 * This is a local preflight guard, not a replacement for server enforcement
 * and cannot recall an HTTP request already dispatched.
 */
public final class FeatureAdmission {
    public static final class Ticket {
        private final boolean allowed;
        private Ticket(boolean allowed) { this.allowed = allowed; }
    }

    private volatile Ticket current;

    public FeatureAdmission(boolean allowed) {
        current = new Ticket(allowed);
    }

    public synchronized void update(boolean allowed) {
        if (current.allowed != allowed) current = new Ticket(allowed);
    }

    public boolean isAllowed() { return current.allowed; }

    public Ticket capture() { return current; }

    public boolean isCurrent(Ticket ticket) {
        return ticket != null && ticket.allowed && current == ticket;
    }
}
