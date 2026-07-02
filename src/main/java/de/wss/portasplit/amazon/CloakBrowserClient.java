package de.wss.portasplit.amazon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kklisura.cdt.protocol.commands.Network;
import com.github.kklisura.cdt.protocol.commands.Page;
import com.github.kklisura.cdt.protocol.commands.Runtime;
import com.github.kklisura.cdt.protocol.types.network.Cookie;
import com.github.kklisura.cdt.protocol.types.runtime.Evaluate;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.impl.ChromeServiceImpl;
import com.github.kklisura.cdt.services.types.ChromeTab;
import de.wss.portasplit.config.AppProperties;
import de.wss.portasplit.jobs.JobLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Shared, low-level scraper that drives a CloakBrowser stealth Chromium over the Chrome DevTools
 * Protocol: navigates to a URL, waits for the page to become ready and returns the result of a
 * JavaScript extraction snippet (which must return a JSON string) as a parsed map.
 *
 * <p>The stealth browser is what gets us past bot detection; this client only navigates and reads
 * the rendered DOM. Uses a pure-Java CDP client (no Node/driver/browser download), so it behaves
 * the same whether the app runs exploded or as a Spring Boot fat jar.
 *
 * <p>Calls are serialized <strong>per stealth context</strong> (per {@code fingerprintSeed}): two
 * calls for the same seed share one tab and must not navigate it concurrently, so they take the same
 * lock; calls for <em>different</em> seeds drive separate tabs and run <strong>concurrently</strong>.
 * Each check source therefore uses its own seed (Amazon, Lidl, kleinanzeigen, the per-chain workers),
 * which is what lets the parallel workers actually scrape at the same time.
 */
@Component
public class CloakBrowserClient {

    private static final Logger log = LoggerFactory.getLogger(CloakBrowserClient.class);
    private static final long READY_POLL_MS = 600;
    private static final long MAX_READY_MS = 15000;
    private static final long CHALLENGE_WAIT_MS = 9000;

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final JobLogger jobLog;

    /**
     * Per-stealth-context locks. Same fingerprint seed → same lock → serialized (one tab); different
     * seeds → different locks → concurrent navigation across separate tabs.
     */
    private final ConcurrentHashMap<Integer, Object> seedLocks = new ConcurrentHashMap<>();

    private Object lockFor(int seed) {
        return seedLocks.computeIfAbsent(seed, k -> new Object());
    }

    public CloakBrowserClient(AppProperties props, ObjectMapper objectMapper, JobLogger jobLog) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.jobLog = jobLog;
    }

    /**
     * Navigates to {@code url} inside a dedicated per-seed stealth context (its own persistent tab),
     * then repeatedly evaluates {@code extractionJs} (an IIFE returning a {@code JSON.stringify(...)}
     * string that includes a boolean {@code ready} field) until it reports ready or the readiness
     * window elapses, and returns the parsed map. The per-seed context keeps a site's session "warm"
     * — accepted cookie-consent and any anti-bot clearance persist across polls instead of being
     * clobbered by the other scrapers — and lets different sources scrape concurrently (one tab each,
     * serialized only against other calls using the same seed).
     *
     * @return the parsed extraction result, or {@code null} if the page could not be scraped.
     */
    public Map<String, Object> fetchFingerprinted(int fingerprintSeed, String url, String extractionJs) {
        synchronized (lockFor(fingerprintSeed)) {
            return doFetchFingerprinted(fingerprintSeed, url, extractionJs);
        }
    }

    private Map<String, Object> doFetchFingerprinted(int fingerprintSeed, String url,
                                                     String extractionJs) {
        long timeoutMs = props.cloakbrowser().timeout().toMillis();
        URI cdp = URI.create(props.cloakbrowser().cdpUrl());
        int port = cdp.getPort() > 0 ? cdp.getPort() : 9222;
        String host = cdp.getHost() != null ? cdp.getHost() : "localhost";

        ChromeDevToolsService dts = null;
        try {
            ChromeTab tab = fingerprintTab(host, port, fingerprintSeed);
            ChromeService service = new ChromeServiceImpl(host, port);
            dts = service.createDevToolsService(tab);

            Page page = dts.getPage();
            Runtime runtime = dts.getRuntime();
            page.enable();
            runtime.enable();
            forceGermanLocale(dts);

            CountDownLatch loaded = new CountDownLatch(1);
            page.onLoadEventFired(e -> loaded.countDown());
            jobLog.debug("Browser: öffne {}", url);
            page.navigate(url);
            if (!loaded.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                log.debug("Load event did not fire within {} ms for {}", timeoutMs, url);
                jobLog.debug("Browser: Load-Event blieb nach {} ms aus - fahre trotzdem fort", timeoutMs);
            } else {
                jobLog.debug("Browser: Seite geladen");
            }

            long deadline = System.currentTimeMillis() + Math.min(timeoutMs, MAX_READY_MS);
            Map<String, Object> data = evaluate(runtime, extractionJs);
            while ((data == null || !Boolean.TRUE.equals(data.get("ready")))
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(READY_POLL_MS);
                data = evaluate(runtime, extractionJs);
            }
            if (data != null && Boolean.TRUE.equals(data.get("ready"))) {
                jobLog.debug("Browser: Inhalt fertig gerendert");
            } else {
                jobLog.warn("Browser: Inhalt nicht rechtzeitig bereit (Timeout / Bot-Schutz?)");
            }
            return data;
        } catch (Exception e) {
            log.warn("CloakBrowser fingerprinted scrape failed for {}: {}", url, e.getMessage());
            log.debug("CloakBrowser fingerprinted scrape error detail", e);
            jobLog.warn("Browser-Scrape fehlgeschlagen: {}", e.getMessage());
            return null;
        } finally {
            if (dts != null) {
                try {
                    dts.close();
                } catch (RuntimeException e) {
                    log.debug("Closing CDP session failed: {}", e.getMessage());
                }
            }
        }
    }

    private Map<String, Object> evaluate(Runtime runtime, String js) {
        try {
            Evaluate ev = runtime.evaluate(js, null, null, null, null, true, null, null,
                    false, null, null, null, null, null, null);
            if (ev.getExceptionDetails() != null || ev.getResult() == null
                    || ev.getResult().getValue() == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(ev.getResult().getValue().toString(), Map.class);
            return map;
        } catch (Exception e) {
            log.debug("Evaluate failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Loads {@code pageUrl} once to pass Cloudflare, then runs each of {@code requests} as an in-page
     * fetch (GET/POST + headers) that reuses the context's cf_clearance + TLS fingerprint. Returns one
     * response per request, in order. Each request's HTTP status / content-type / size is logged so a
     * chain's availability endpoint can be reverse-engineered from the job log.
     */
    public List<InPageResponse> behindCloudflare(int fingerprintSeed, String pageUrl,
                                                 List<InPageReq> requests) {
        synchronized (lockFor(fingerprintSeed)) {
            return doBehindCloudflare(fingerprintSeed, pageUrl, requests);
        }
    }

    /**
     * Navigates to {@code pageUrl}, records every network request the page fires (through load, a
     * scroll to the bottom to trigger lazy widgets, and a {@code settleMs} settle), and returns the
     * distinct request URLs. Used to discover a chain's dynamically-called availability API.
     */
    public List<String> capturePageRequests(int fingerprintSeed, String pageUrl, long settleMs) {
        synchronized (lockFor(fingerprintSeed)) {
            return doCapturePageRequests(fingerprintSeed, pageUrl, settleMs);
        }
    }

    private List<String> doCapturePageRequests(int fingerprintSeed, String pageUrl, long settleMs) {
        long timeoutMs = props.cloakbrowser().timeout().toMillis();
        URI cdp = URI.create(props.cloakbrowser().cdpUrl());
        int port = cdp.getPort() > 0 ? cdp.getPort() : 9222;
        String host = cdp.getHost() != null ? cdp.getHost() : "localhost";

        Set<String> urls = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
        ChromeDevToolsService dts = null;
        try {
            ChromeTab tab = fingerprintTab(host, port, fingerprintSeed);
            ChromeService service = new ChromeServiceImpl(host, port);
            dts = service.createDevToolsService(tab);

            Page page = dts.getPage();
            Network network = dts.getNetwork();
            Runtime runtime = dts.getRuntime();
            page.enable();
            network.enable();
            runtime.enable();
            network.onRequestWillBeSent(e -> {
                try {
                    urls.add(e.getRequest().getUrl());
                } catch (RuntimeException ignored) {
                    // best effort
                }
            });

            jobLog.info("Cloudflare: öffne {} (Netzwerk-Mitschnitt)", pageUrl);
            CountDownLatch loaded = new CountDownLatch(1);
            page.onLoadEventFired(e -> loaded.countDown());
            page.navigate(pageUrl);
            loaded.await(timeoutMs, TimeUnit.MILLISECONDS);
            Thread.sleep(CHALLENGE_WAIT_MS);
            try {
                runtime.evaluate("window.scrollTo(0,document.body.scrollHeight);", null, null, null,
                        null, true, null, null, false, null, null, null, null, null, null);
            } catch (RuntimeException ignored) {
                // scrolling is best effort
            }
            Thread.sleep(settleMs);
            return new ArrayList<>(urls);
        } catch (Exception e) {
            jobLog.warn("Netzwerk-Mitschnitt fehlgeschlagen: {}", e.getMessage());
            return new ArrayList<>(urls);
        } finally {
            if (dts != null) {
                try {
                    dts.close();
                } catch (RuntimeException e) {
                    log.debug("Closing CDP session failed: {}", e.getMessage());
                }
            }
        }
    }

    private List<InPageResponse> doBehindCloudflare(int fingerprintSeed, String pageUrl,
                                                    List<InPageReq> requests) {
        long timeoutMs = props.cloakbrowser().timeout().toMillis();
        URI cdp = URI.create(props.cloakbrowser().cdpUrl());
        int port = cdp.getPort() > 0 ? cdp.getPort() : 9222;
        String host = cdp.getHost() != null ? cdp.getHost() : "localhost";

        ChromeDevToolsService dts = null;
        try {
            ChromeTab tab = fingerprintTab(host, port, fingerprintSeed);
            ChromeService service = new ChromeServiceImpl(host, port);
            dts = service.createDevToolsService(tab);

            Page page = dts.getPage();
            Network network = dts.getNetwork();
            Runtime runtime = dts.getRuntime();
            page.enable();
            network.enable();
            runtime.enable();

            jobLog.info("Cloudflare: öffne {}", pageUrl);
            CountDownLatch loaded = new CountDownLatch(1);
            page.onLoadEventFired(e -> loaded.countDown());
            page.navigate(pageUrl);
            loaded.await(timeoutMs, TimeUnit.MILLISECONDS);
            Thread.sleep(1500);

            List<InPageResponse> responses = List.of();
            for (int attempt = 1; attempt <= 2; attempt++) {
                Thread.sleep(CHALLENGE_WAIT_MS);
                boolean clearance = hasClearance(network);
                List<InPageResponse> current = new ArrayList<>(requests.size());
                boolean anyOk = false;
                for (InPageReq req : requests) {
                    InPageResponse r = inPageFetch(runtime, req, timeoutMs);
                    current.add(r);
                    boolean challenge = looksLikeChallenge(r.body());
                    String snippet = r.body() == null ? null
                            : r.body().substring(0, Math.min(180, r.body().length())).replaceAll("\\s+", " ");
                    jobLog.info("{} {} (Versuch {}/2) → HTTP {} · ct={} · {} Zeichen · interstitial={} · "
                                    + "cf_clearance={}{}",
                            req.method(), req.path(), attempt, r.status(), r.contentType(),
                            r.body() != null ? r.body().length() : 0, challenge, clearance,
                            r.error() != null ? " · Fehler=" + r.error()
                                    : (snippet != null ? " · Anfang=" + snippet : ""));
                    if (r.status() == 200 && r.body() != null && !challenge) {
                        anyOk = true;
                    }
                }
                responses = current;
                if (anyOk) {
                    return responses;
                }
            }
            return responses;
        } catch (Exception e) {
            log.warn("Cloudflare fetch failed for {}: {}", pageUrl, e.getMessage());
            log.debug("Cloudflare fetch error detail", e);
            jobLog.warn("Cloudflare-Abruf fehlgeschlagen: {}", e.getMessage());
            return List.of();
        } finally {
            if (dts != null) {
                try {
                    dts.close();
                } catch (RuntimeException e) {
                    log.debug("Closing CDP session failed: {}", e.getMessage());
                }
            }
        }
    }

    public record InPageReq(String method, String path, Map<String, String> headers) {}

    public record InPageResponse(int status, String contentType, String body, String error) {}

    private InPageResponse inPageFetch(Runtime runtime, InPageReq req, long timeoutMs) {
        try {
            String headersJson = objectMapper.writeValueAsString(
                    req.headers() == null ? Map.of() : req.headers());
            String pathJson = objectMapper.writeValueAsString(req.path());
            String methodJson = objectMapper.writeValueAsString(req.method() == null ? "GET" : req.method());
            String js = "(async()=>{try{"
                    + "const r=await fetch(" + pathJson + ",{method:" + methodJson + ","
                    + "headers:" + headersJson + ",credentials:'include'});"
                    + "const t=await r.text();"
                    + "return JSON.stringify({status:r.status,ct:(r.headers.get('content-type')||''),body:t});"
                    + "}catch(e){return JSON.stringify({status:-1,err:String((e&&e.message)||e)});}})()";
            Evaluate ev = runtime.evaluate(js, null, null, null, null, true, null, null, true,
                    null, (double) timeoutMs, null, null, null, null);
            if (ev.getExceptionDetails() != null) {
                return new InPageResponse(-2, null, null, "eval: " + ev.getExceptionDetails().getText());
            }
            if (ev.getResult() == null || ev.getResult().getValue() == null) {
                return new InPageResponse(-3, null, null, "eval returned null");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(ev.getResult().getValue().toString(), Map.class);
            int status = (m.get("status") instanceof Number n) ? n.intValue() : -4;
            return new InPageResponse(status, str(m.get("ct")), str(m.get("body")), str(m.get("err")));
        } catch (Exception e) {
            return new InPageResponse(-5, null, null, "parse: " + e.getMessage());
        }
    }

    /**
     * Sets {@code cookies} on the target origin, loads {@code pageUrl} inside a per-seed stealth
     * context, then runs a single same-origin POST to {@code apiPath} (JSON body, credentials
     * included) and returns the response. This is the write-path counterpart to
     * {@link #behindCloudflare}: a raw out-of-browser POST to toom's {@code /shop/rest/V1/…}
     * surface is 403'd at the edge, but an in-page fetch reusing the browser's cookies/TLS answers
     * normally. Cookies are applied via {@code document.cookie} and the page is re-navigated so the
     * server session adopts them (e.g. the selected market) before the POST is sent.
     *
     * @return the POST response; {@link InPageResponse#status()} is the HTTP status (negative on a
     *         browser/eval error).
     */
    public InPageResponse inPagePost(int fingerprintSeed, String pageUrl, Map<String, String> cookies,
                                     String apiPath, String jsonBody) {
        synchronized (lockFor(fingerprintSeed)) {
            return doInPagePost(fingerprintSeed, pageUrl, cookies, apiPath, jsonBody);
        }
    }

    private InPageResponse doInPagePost(int fingerprintSeed, String pageUrl, Map<String, String> cookies,
                                        String apiPath, String jsonBody) {
        long timeoutMs = props.cloakbrowser().timeout().toMillis();
        URI cdp = URI.create(props.cloakbrowser().cdpUrl());
        int port = cdp.getPort() > 0 ? cdp.getPort() : 9222;
        String host = cdp.getHost() != null ? cdp.getHost() : "localhost";

        ChromeDevToolsService dts = null;
        try {
            ChromeTab tab = fingerprintTab(host, port, fingerprintSeed);
            ChromeService service = new ChromeServiceImpl(host, port);
            dts = service.createDevToolsService(tab);

            Page page = dts.getPage();
            Runtime runtime = dts.getRuntime();
            page.enable();
            runtime.enable();
            forceGermanLocale(dts);

            CountDownLatch loaded = new CountDownLatch(1);
            page.onLoadEventFired(e -> loaded.countDown());
            jobLog.debug("Browser: öffne {} (in-page POST)", pageUrl);
            page.navigate(pageUrl);
            loaded.await(timeoutMs, TimeUnit.MILLISECONDS);

            if (cookies != null && !cookies.isEmpty()) {
                StringBuilder js = new StringBuilder();
                for (Map.Entry<String, String> c : cookies.entrySet()) {
                    String assignment = c.getKey() + "=" + c.getValue() + "; path=/; domain=.toom.de";
                    js.append("document.cookie=").append(objectMapper.writeValueAsString(assignment)).append(';');
                }
                runtime.evaluate(js.toString(), null, null, null, null, true, null, null, false,
                        null, null, null, null, null, null);
                // Re-navigate so the cookie (e.g. the selected market) is sent on the document request
                // and adopted server-side before the reserve POST is evaluated.
                CountDownLatch reloaded = new CountDownLatch(1);
                page.onLoadEventFired(e -> reloaded.countDown());
                page.navigate(pageUrl);
                reloaded.await(timeoutMs, TimeUnit.MILLISECONDS);
            }

            return inPageFetchBody(runtime, "POST", apiPath, jsonBody, timeoutMs);
        } catch (Exception e) {
            log.warn("CloakBrowser inPagePost failed for {} {}: {}", pageUrl, apiPath, e.getMessage());
            log.debug("CloakBrowser inPagePost error detail", e);
            jobLog.warn("Browser in-page POST fehlgeschlagen: {}", e.getMessage());
            return new InPageResponse(-1, null, null, e.getMessage());
        } finally {
            if (dts != null) {
                try {
                    dts.close();
                } catch (RuntimeException e) {
                    log.debug("Closing CDP session failed: {}", e.getMessage());
                }
            }
        }
    }

    /** Like {@link #inPageFetch} but sends a JSON request body (for write endpoints). */
    private InPageResponse inPageFetchBody(Runtime runtime, String method, String path, String jsonBody,
                                           long timeoutMs) {
        try {
            String pathJson = objectMapper.writeValueAsString(path);
            String methodJson = objectMapper.writeValueAsString(method == null ? "POST" : method);
            String bodyJson = objectMapper.writeValueAsString(jsonBody == null ? "" : jsonBody);
            String js = "(async()=>{try{"
                    + "const r=await fetch(" + pathJson + ",{method:" + methodJson + ","
                    + "headers:{'Content-Type':'application/json','Accept':'application/json'},"
                    + "credentials:'include',body:" + bodyJson + "});"
                    + "const t=await r.text();"
                    + "return JSON.stringify({status:r.status,ct:(r.headers.get('content-type')||''),body:t});"
                    + "}catch(e){return JSON.stringify({status:-1,err:String((e&&e.message)||e)});}})()";
            return evalAsyncJson(runtime, js, timeoutMs);
        } catch (Exception e) {
            return new InPageResponse(-5, null, null, "build: " + e.getMessage());
        }
    }

    /** Evaluates an async IIFE that returns {@code JSON.stringify({status,ct?,body?,err?})}. */
    private InPageResponse evalAsyncJson(Runtime runtime, String js, long timeoutMs) {
        try {
            Evaluate ev = runtime.evaluate(js, null, null, null, null, true, null, null, true,
                    null, (double) timeoutMs, null, null, null, null);
            if (ev.getExceptionDetails() != null) {
                return new InPageResponse(-2, null, null, "eval: " + ev.getExceptionDetails().getText());
            }
            if (ev.getResult() == null || ev.getResult().getValue() == null) {
                return new InPageResponse(-3, null, null, "eval returned null");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(ev.getResult().getValue().toString(), Map.class);
            int status = (m.get("status") instanceof Number n) ? n.intValue() : -4;
            return new InPageResponse(status, str(m.get("ct")), str(m.get("body")), str(m.get("err")));
        } catch (Exception e) {
            return new InPageResponse(-5, null, null, "parse: " + e.getMessage());
        }
    }

    /** Evaluates a synchronous JS expression and returns its string value (or {@code null}). */
    private String evalToString(Runtime runtime, String js) {
        try {
            Evaluate ev = runtime.evaluate(js, null, null, null, null, true, null, null, false,
                    null, null, null, null, null, null);
            if (ev.getExceptionDetails() != null || ev.getResult() == null || ev.getResult().getValue() == null) {
                return null;
            }
            return ev.getResult().getValue().toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Outcome of a {@link #toomLogin} attempt. Carries no PII (no session body, no credentials). */
    public record LoginResult(boolean ok, int typeStatus, int loginStatus, String note) {}

    /**
     * Drives toom's two-step, Cloudflare-Turnstile login inside a per-seed stealth context and leaves
     * the resulting logged-in session on that seed's warm tab (cookies persist across later
     * {@link #inPagePost} calls on the same seed). Steps: load the login page, wait for Turnstile to
     * auto-populate the hidden {@code cf-turnstile-response} input, POST {@code typePath}
     * ({email, turnstile_token}), then POST {@code loginPath} ({email, password}). The password is used
     * only inside the in-page fetch body and is never logged. Requires the CloakBrowser to run headed
     * for the managed challenge to clear.
     */
    public LoginResult toomLogin(int fingerprintSeed, String loginPageUrl, String typePath, String loginPath,
                                 String email, String password) {
        synchronized (lockFor(fingerprintSeed)) {
            return doToomLogin(fingerprintSeed, loginPageUrl, typePath, loginPath, email, password);
        }
    }

    private LoginResult doToomLogin(int fingerprintSeed, String loginPageUrl, String typePath,
                                    String loginPath, String email, String password) {
        long timeoutMs = props.cloakbrowser().timeout().toMillis();
        URI cdp = URI.create(props.cloakbrowser().cdpUrl());
        int port = cdp.getPort() > 0 ? cdp.getPort() : 9222;
        String host = cdp.getHost() != null ? cdp.getHost() : "localhost";

        ChromeDevToolsService dts = null;
        try {
            ChromeTab tab = fingerprintTab(host, port, fingerprintSeed);
            ChromeService service = new ChromeServiceImpl(host, port);
            dts = service.createDevToolsService(tab);

            Page page = dts.getPage();
            Runtime runtime = dts.getRuntime();
            page.enable();
            runtime.enable();
            forceGermanLocale(dts);

            CountDownLatch loaded = new CountDownLatch(1);
            page.onLoadEventFired(e -> loaded.countDown());
            jobLog.info("toom-login: öffne Anmeldeseite");
            page.navigate(loginPageUrl);
            loaded.await(timeoutMs, TimeUnit.MILLISECONDS);

            // Give the SPA a moment to settle / client-redirect, then check whether we are already
            // logged in: an active session redirects the login page to /meinkonto/uebersicht (no email
            // field, no Turnstile). Treat that as success instead of waiting for a token that never comes.
            Thread.sleep(2500);
            boolean hasLoginForm = "1".equals(evalToString(runtime,
                    "(function(){try{return document.querySelector('input[name=\"email\"]')?'1':'0';}catch(e){return '0';}})()"));
            String path = evalToString(runtime, "location.pathname");
            boolean onAccount = path != null && path.contains("/meinkonto") && !path.contains("anmeldung");
            if (!hasLoginForm && onAccount) {
                jobLog.info("toom-login: bereits angemeldet (aktive Session) - {}", path);
                return new LoginResult(true, 200, 200, "already logged in");
            }

            // Wait for Turnstile to auto-solve and populate the hidden token input.
            String tokenJs = "(()=>{try{return (document.querySelector('input[name=\"cf-turnstile-response\"]')||{}).value||'';}catch(e){return '';}})()";
            long deadline = System.currentTimeMillis() + Math.min(timeoutMs, 30000L);
            String token = evalToString(runtime, tokenJs);
            while ((token == null || token.isBlank()) && System.currentTimeMillis() < deadline) {
                Thread.sleep(READY_POLL_MS);
                token = evalToString(runtime, tokenJs);
            }
            if (token == null || token.isBlank()) {
                jobLog.warn("toom-login: Turnstile-Token nicht erhalten (Challenge nicht gelöst?)");
                return new LoginResult(false, -10, -10, "turnstile timeout");
            }

            String emailJson = objectMapper.writeValueAsString(email);
            String typeJs = "(async()=>{try{"
                    + "const tok=(document.querySelector('input[name=\"cf-turnstile-response\"]')||{}).value||'';"
                    + "const r=await fetch(" + objectMapper.writeValueAsString(typePath) + ",{method:'POST',"
                    + "headers:{'Content-Type':'application/json','Accept':'application/json','X-Requested-With':'XMLHttpRequest'},"
                    + "credentials:'include',body:JSON.stringify({email:" + emailJson + ",turnstile_token:tok})});"
                    + "const t=await r.text();return JSON.stringify({status:r.status,body:t.slice(0,200)});}"
                    + "catch(e){return JSON.stringify({status:-1,err:String((e&&e.message)||e)});}})()";
            InPageResponse typeResp = evalAsyncJson(runtime, typeJs, timeoutMs);

            // NOTE: the login JS embeds the password — never log this string.
            String loginJs = "(async()=>{try{"
                    + "const r=await fetch(" + objectMapper.writeValueAsString(loginPath) + ",{method:'POST',"
                    + "headers:{'Content-Type':'application/json','Accept':'application/json','X-Requested-With':'XMLHttpRequest'},"
                    + "credentials:'include',body:JSON.stringify({email:" + emailJson + ",password:"
                    + objectMapper.writeValueAsString(password) + "})});"
                    + "const t=await r.text();return JSON.stringify({status:r.status,body:t.slice(0,80)});}"
                    + "catch(e){return JSON.stringify({status:-1,err:String((e&&e.message)||e)});}})()";
            InPageResponse loginResp = evalAsyncJson(runtime, loginJs, timeoutMs);

            int typeStatus = typeResp == null ? -1 : typeResp.status();
            int loginStatus = loginResp == null ? -1 : loginResp.status();
            boolean ok = loginStatus == 200;
            String note = "type=" + typeStatus + " login=" + loginStatus;
            jobLog.info("toom-login: {} ({})", ok ? "erfolgreich" : "fehlgeschlagen", note);
            return new LoginResult(ok, typeStatus, loginStatus, note);
        } catch (Exception e) {
            log.warn("toom login failed: {}", e.getMessage());
            jobLog.warn("toom-login fehlgeschlagen: {}", e.getMessage());
            return new LoginResult(false, -1, -1, e.getMessage());
        } finally {
            if (dts != null) {
                try {
                    dts.close();
                } catch (RuntimeException e) {
                    log.debug("Closing CDP session failed: {}", e.getMessage());
                }
            }
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** A fetch result that is HTML (challenge interstitial) rather than the expected JSON. */
    private static boolean looksLikeChallenge(String body) {
        if (body == null) {
            return true;
        }
        String head = body.stripLeading();
        return head.regionMatches(true, 0, "<!DOCTYPE", 0, 9)
                || head.regionMatches(true, 0, "<html", 0, 5)
                || body.contains("Just a moment");
    }

    private static boolean hasClearance(Network network) {
        for (Cookie c : network.getAllCookies()) {
            if ("cf_clearance".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** Resolves a page tab inside a per-seed stealth context via the cloakserve multiplexer. */
    private ChromeTab fingerprintTab(String host, int port, int seed) throws Exception {
        // Bound both connect and request by the configured timeout: if the CloakBrowser is down, this
        // fails fast instead of hanging the worker (and holding its per-seed lock) on the OS connect
        // timeout — important now that each source runs on its own worker.
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(props.cloakbrowser().timeout())
                .build();
        String url = "http://" + host + ":" + port + "/json/list?fingerprint=" + seed
                + "&timezone=" + enc(props.cloakbrowser().cloakTimezone())
                + "&locale=" + enc(props.cloakbrowser().cloakLocale())
                + "&platform=" + enc(props.cloakbrowser().cloakPlatform());
        String body = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(props.cloakbrowser().timeout()).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        ChromeTab[] tabs = objectMapper.readValue(body, ChromeTab[].class);
        for (ChromeTab t : tabs) {
            if ("page".equals(t.getType())) {
                return t;
            }
        }
        return tabs[0];
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    /**
     * Parses a price string in either German ("1.299,00 €") or English ("€1,299.00") format.
     * The right-most separator with 1-2 trailing digits is treated as the decimal point.
     */
    public static java.math.BigDecimal parsePrice(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9.,]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        int sep = Math.max(cleaned.lastIndexOf(','), cleaned.lastIndexOf('.'));
        if (sep >= 0) {
            String intPart = cleaned.substring(0, sep).replaceAll("[.,]", "");
            String fracPart = cleaned.substring(sep + 1).replaceAll("[.,]", "");
            cleaned = (fracPart.length() >= 1 && fracPart.length() <= 2)
                    ? intPart + "." + fracPart
                    : intPart + fracPart;
        }
        if (cleaned.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Ask the site to render in German so prices/dates match the parsers. Best effort. */
    private static void forceGermanLocale(ChromeDevToolsService dts) {
        try {
            Network network = dts.getNetwork();
            network.enable();
            Map<String, Object> headers = new HashMap<>();
            headers.put("Accept-Language", "de-DE,de;q=0.9,en;q=0.5");
            network.setExtraHTTPHeaders(headers);
        } catch (RuntimeException e) {
            log.debug("Could not set Accept-Language: {}", e.getMessage());
        }
    }
}
