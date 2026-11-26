package com.mailapp.controller;

import com.mailapp.service.impl.MailAuditRecovery;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only operational visibility. An old PREPARED intent is not evidence
 * of delivery or failure and this API deliberately cannot resolve or retry it. */
@RestController
@RequestMapping("/api/admin/mail-audit")
@PreAuthorize("hasRole('ADMIN')")
public class MailAuditStatusController {
    private final MailAuditRecovery recovery;

    public MailAuditStatusController(MailAuditRecovery recovery) {
        this.recovery = recovery;
    }

    @GetMapping("/status")
    public ResponseEntity<MailAuditRecovery.Status> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(recovery.status());
    }

    @GetMapping("/events")
    public ResponseEntity<MailAuditRecovery.EventPage> events(
            @RequestParam(required = false) String outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(recovery.events(outcome, page, size));
    }
}
