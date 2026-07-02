package de.wss.portasplit.chains;

import de.wss.portasplit.amazon.CloakBrowserClient;
import de.wss.portasplit.amazon.CloakBrowserClient.LoginResult;
import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.jobs.JobLogger;
import de.wss.portasplit.service.SecretCipher;
import de.wss.portasplit.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Owns the logged-in toom account session used by the auto-reserve feature. Credentials are stored
 * encrypted at rest ({@link SecretCipher}); the login itself runs through the {@link CloakBrowserClient}
 * (Turnstile) and leaves a warm, logged-in session on the auto-reserve fingerprint seed, which the
 * reserve action then reuses. The password is never logged and never persisted in clear text.
 */
@Service
public class ToomAuthService {

    private static final Logger log = LoggerFactory.getLogger(ToomAuthService.class);

    private static final String LOGIN_PAGE = "https://toom.de/meinkonto/anmeldung";
    private static final String TYPE_PATH = "/shop/rest/V1/toom/customer/login/type";
    private static final String LOGIN_PATH = "/shop/rest/V1/toom/customer/login";

    public enum Outcome { OK, NO_KEY, NO_CREDENTIALS, TURNSTILE_FAILED, FAILED }

    public record Status(boolean cryptoConfigured, boolean hasCredentials, String email,
                         boolean loggedIn, Instant lastLoginAt, String lastError) {}

    private final CloakBrowserClient cloak;
    private final SettingsService settings;
    private final SecretCipher cipher;
    private final AppProperties props;
    private final JobLogger jobLog;

    private volatile boolean loggedIn = false;
    private volatile Instant lastLoginAt = null;
    private volatile String lastError = null;

    public ToomAuthService(CloakBrowserClient cloak, SettingsService settings, SecretCipher cipher,
                           AppProperties props, JobLogger jobLog) {
        this.cloak = cloak;
        this.settings = settings;
        this.cipher = cipher;
        this.props = props;
        this.jobLog = jobLog;
    }

    public boolean cryptoConfigured() {
        return cipher.isConfigured();
    }

    public String storedEmail() {
        return settings.get(SettingsService.TOOM_AUTH_EMAIL).orElse(null);
    }

    public boolean hasCredentials() {
        return StringUtils.hasText(storedEmail())
                && settings.get(SettingsService.TOOM_AUTH_PASSWORD_ENC).isPresent();
    }

    /** Stores the toom credentials (password encrypted at rest). Does not log in. */
    public void setCredentials(String email, String password) {
        if (!cipher.isConfigured()) {
            throw new IllegalStateException("Kein Crypto-Key konfiguriert - Passwort kann nicht sicher gespeichert werden.");
        }
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("E-Mail und Passwort erforderlich");
        }
        settings.set(SettingsService.TOOM_AUTH_EMAIL, email.trim());
        settings.set(SettingsService.TOOM_AUTH_PASSWORD_ENC, cipher.encrypt(password));
        loggedIn = false;
        lastError = null;
    }

    public void clearCredentials() {
        settings.set(SettingsService.TOOM_AUTH_EMAIL, "");
        settings.set(SettingsService.TOOM_AUTH_PASSWORD_ENC, "");
        loggedIn = false;
        lastLoginAt = null;
        lastError = null;
    }

    /** Performs a fresh login using the stored credentials. Serialized so two triggers can't race. */
    public synchronized Outcome login() {
        if (!cipher.isConfigured()) {
            lastError = "Kein Crypto-Key konfiguriert";
            return Outcome.NO_KEY;
        }
        String email = storedEmail();
        String encPw = settings.get(SettingsService.TOOM_AUTH_PASSWORD_ENC).orElse(null);
        if (!StringUtils.hasText(email) || !StringUtils.hasText(encPw)) {
            lastError = "Keine Zugangsdaten hinterlegt";
            return Outcome.NO_CREDENTIALS;
        }
        String password;
        try {
            password = cipher.decrypt(encPw);
        } catch (Exception e) {
            lastError = "Zugangsdaten nicht entschlüsselbar (Key geändert?)";
            log.warn("toom credential decrypt failed: {}", e.getMessage());
            return Outcome.FAILED;
        }

        LoginResult r = cloak.toomLogin(props.toomReserve().fingerprintSeed(),
                LOGIN_PAGE, TYPE_PATH, LOGIN_PATH, email, password);
        lastLoginAt = Instant.now();
        if (r.ok()) {
            loggedIn = true;
            lastError = null;
            jobLog.info("toom-login: Konto angemeldet ({})", email);
            return Outcome.OK;
        }
        loggedIn = false;
        if (r.typeStatus() == -10) {
            lastError = "Turnstile nicht gelöst (evtl. erneut versuchen)";
            return Outcome.TURNSTILE_FAILED;
        }
        lastError = "Login fehlgeschlagen (" + r.note() + ")";
        return Outcome.FAILED;
    }

    /** Ensures a logged-in session; re-logs in if not currently marked logged in. */
    public synchronized boolean ensureLoggedIn() {
        if (loggedIn) {
            return true;
        }
        return login() == Outcome.OK;
    }

    /** Marks the session as logged out (does not clear stored credentials). */
    public synchronized void markLoggedOut() {
        loggedIn = false;
    }

    public Status status() {
        return new Status(cipher.isConfigured(), hasCredentials(), maskEmail(storedEmail()),
                loggedIn, lastLoginAt, lastError);
    }

    private static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "•••" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "•••" + email.substring(at);
    }
}
