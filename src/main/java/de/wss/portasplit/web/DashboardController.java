package de.wss.portasplit.web;

import de.wss.portasplit.domain.Product;
import de.wss.portasplit.jobs.CheckJobService;
import de.wss.portasplit.jobs.JobTrigger;
import de.wss.portasplit.service.DashboardService;
import de.wss.portasplit.service.NotificationService;
import de.wss.portasplit.service.TelegramService;
import de.wss.portasplit.web.dto.EventDto;
import de.wss.portasplit.web.dto.JobsOverviewDto;
import de.wss.portasplit.web.dto.OverviewDto;
import de.wss.portasplit.web.dto.TelegramTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CheckJobService jobService;
    private final NotificationService notificationService;
    private final TelegramService telegramService;

    public DashboardController(DashboardService dashboardService,
                              CheckJobService jobService,
                              NotificationService notificationService,
                              TelegramService telegramService) {
        this.dashboardService = dashboardService;
        this.jobService = jobService;
        this.notificationService = notificationService;
        this.telegramService = telegramService;
    }

    @GetMapping("/overview")
    public OverviewDto overview(@RequestParam(name = "all", defaultValue = "false") boolean all) {
        return dashboardService.buildOverview(all);
    }

    @GetMapping("/kleinanzeigen")
    public de.wss.portasplit.web.dto.KleinanzeigenStatusDto kleinanzeigen() {
        return dashboardService.buildKleinanzeigenStatus();
    }

    /**
     * Manually trigger a poll now. All currently-enabled sources are <em>enqueued</em> on their own
     * workers and run in the background (in parallel); this returns immediately with the current queue
     * snapshot, so the page is never blocked waiting for a (slow) scrape. Disabled sources are skipped.
     */
    @PostMapping("/check")
    public JobsOverviewDto runCheck() {
        jobService.submitAllEnabled(JobTrigger.MANUAL);
        return JobsOverviewDto.from(jobService.snapshot());
    }

    @GetMapping("/history")
    public List<EventDto> history(@RequestParam Long shopId, @RequestParam Product product) {
        return dashboardService.history(shopId, product);
    }

    @GetMapping("/events")
    public List<EventDto> recentEvents(@RequestParam(defaultValue = "50") int limit) {
        return dashboardService.recentEvents(Math.min(Math.max(limit, 1), 500));
    }

    @PostMapping("/telegram/test")
    public TelegramTestResult telegramTest() {
        boolean configured = telegramService.isConfigured();
        if (!configured) {
            return new TelegramTestResult(false, false,
                    "Telegram ist nicht konfiguriert. Bitte app.telegram.enabled, bot-token und chat-id setzen.");
        }
        boolean sent = notificationService.sendTest();
        int recipients = telegramService.recipientCount();
        String okMsg = recipients > 1
                ? "Testnachricht an " + recipients + " Empfänger gesendet."
                : "Testnachricht gesendet.";
        return new TelegramTestResult(true, sent,
                sent ? okMsg : "Senden fehlgeschlagen - siehe Server-Log.");
    }
}
