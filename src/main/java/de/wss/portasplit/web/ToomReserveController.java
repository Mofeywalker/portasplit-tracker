package de.wss.portasplit.web;

import de.wss.portasplit.chains.ToomAuthService;
import de.wss.portasplit.jobs.CheckJobService;
import de.wss.portasplit.jobs.JobTrigger;
import de.wss.portasplit.jobs.JobType;
import de.wss.portasplit.service.SettingsService;
import de.wss.portasplit.web.dto.ToomCredentialsRequest;
import de.wss.portasplit.web.dto.ToomReserveStatusDto;
import de.wss.portasplit.web.dto.ToomReserveToggleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dashboard control for the toom auto-reserve feature: on/off switch, credential entry (password is
 * encrypted at rest and never returned), and login. Login runs the browser Turnstile flow and can take
 * a few seconds — the caller shows a spinner.
 */
@RestController
@RequestMapping("/api/toom-reserve")
public class ToomReserveController {

    private final SettingsService settings;
    private final ToomAuthService auth;
    private final CheckJobService jobs;

    public ToomReserveController(SettingsService settings, ToomAuthService auth, CheckJobService jobs) {
        this.settings = settings;
        this.auth = auth;
        this.jobs = jobs;
    }

    @GetMapping("/status")
    public ToomReserveStatusDto status() {
        return buildStatus();
    }

    @PutMapping("/enabled")
    public ToomReserveStatusDto setEnabled(@RequestBody ToomReserveToggleRequest req) {
        if (req.enabled() != null) {
            settings.putBool(SettingsService.TOOM_AUTORESERVE_ENABLED, req.enabled());
        }
        return buildStatus();
    }

    /** Stores credentials (password encrypted) and immediately attempts a login. */
    @PutMapping("/credentials")
    public ToomReserveStatusDto setCredentials(@RequestBody ToomCredentialsRequest req) {
        if (!auth.cryptoConfigured()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "Kein Crypto-Key gesetzt (APP_TOOM_RESERVE_CRYPTO_KEY) - Passwort kann nicht sicher gespeichert werden.");
        }
        if (!StringUtils.hasText(req.email()) || !StringUtils.hasText(req.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-Mail und Passwort erforderlich");
        }
        try {
            auth.setCredentials(req.email(), req.password());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        // Run the login inside a job context so its steps show up in the dashboard logbook.
        jobs.runInline(JobType.TOOM, JobTrigger.MANUAL, "toom-Login gestartet", auth::login);
        return buildStatus();
    }

    /** Re-attempts login with the stored credentials. */
    @PostMapping("/login")
    public ToomReserveStatusDto login() {
        if (!auth.cryptoConfigured()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Kein Crypto-Key gesetzt");
        }
        if (!auth.hasCredentials()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keine Zugangsdaten hinterlegt");
        }
        jobs.runInline(JobType.TOOM, JobTrigger.MANUAL, "toom-Login gestartet", auth::login);
        return buildStatus();
    }

    @DeleteMapping("/credentials")
    public ToomReserveStatusDto clearCredentials() {
        auth.clearCredentials();
        return buildStatus();
    }

    private ToomReserveStatusDto buildStatus() {
        ToomAuthService.Status s = auth.status();
        return new ToomReserveStatusDto(settings.toomAutoReserveEnabled(), s.cryptoConfigured(),
                s.hasCredentials(), s.email(), s.loggedIn(), s.lastLoginAt(), s.lastError());
    }
}
