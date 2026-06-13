package uz.barakat.market.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.AnomalyAlert;
import uz.barakat.market.dto.AnomalyResponse;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.AnomalyAlertRepository;
import uz.barakat.market.service.AnomalyDetectionService.Candidate;
import uz.barakat.market.telegram.TelegramService;

/**
 * Write side of AI anomaly control: persists what {@link AnomalyDetectionService}
 * finds, de-duplicates so a re-scan is a no-op, and pushes warn/critical alerts
 * to the owner's Telegram once each (never re-sends). Also serves the in-app
 * history + acknowledge. Mirrors {@code AnomalyAlertService}'s best-effort,
 * async-Telegram posture so a notification hiccup never breaks a scan.
 */
@Service
public class AnomalyMonitorService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyMonitorService.class);
    private static final String WARN = "warn";
    private static final String CRITICAL = "critical";

    private final AnomalyDetectionService detector;
    private final AnomalyAlertRepository repo;
    private final TelegramService telegram;

    public AnomalyMonitorService(AnomalyDetectionService detector,
                                 AnomalyAlertRepository repo, TelegramService telegram) {
        this.detector = detector;
        this.repo = repo;
        this.telegram = telegram;
    }

    /**
     * Scans {@code day} for the shop in the current tenant scope: detect →
     * persist new (deduped) alerts → Telegram for new warn/critical. The
     * scheduler sets a single shop id before calling. Returns the number of
     * newly-persisted alerts. Dedup is by {@code (shop_id, dedupe_key)}, so a
     * second scan of the same day inserts nothing and re-sends nothing.
     */
    @Transactional
    public int scanCurrentShop(LocalDate day) {
        Long shopId = TenantContext.currentShopId();
        if (shopId == null) {
            log.warn("Anomaly scan called with no shop in scope — skipping");
            return 0;
        }
        int created = 0;
        for (Candidate c : detector.detect(day)) {
            if (repo.findFirstByShopIdAndDedupeKey(shopId, c.dedupeKey()).isPresent()) {
                continue; // already flagged — no insert, no re-notify
            }
            boolean notify = isAlerting(c.severity()) && telegram.isConfigured();
            AnomalyAlert a = new AnomalyAlert();
            a.setShopId(shopId);
            a.setSeverity(c.severity());
            a.setCode(c.code());
            a.setDedupeKey(c.dedupeKey());
            a.setOccurredOn(c.occurredOn());
            a.setMessage(c.message());
            a.setDetailJson(c.detailJson());
            a.setTelegramSent(notify);
            repo.save(a);
            created++;
            if (notify) {
                String text = format(c);
                CompletableFuture.runAsync(() -> {
                    try {
                        telegram.sendMessage(text);
                    } catch (RuntimeException ex) {
                        log.warn("Anomaly Telegram alert failed [{}]: {}", c.code(), ex.toString());
                    }
                });
            }
        }
        return created;
    }

    /** Marks one alert acknowledged (idempotent). Owner action. */
    @Transactional
    public AnomalyResponse acknowledge(Long id, String actor) {
        AnomalyAlert a = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Anomaliya topilmadi"));
        if (!a.isAcknowledged()) {
            a.setAcknowledged(true);
            a.setAcknowledgedBy(actor);
            a.setAcknowledgedAt(LocalDateTime.now());
            repo.save(a);
        }
        return toResponse(a);
    }

    /** History list for the in-app page, windowed by business day, newest first. */
    @Transactional(readOnly = true)
    public List<AnomalyResponse> history(LocalDate from, LocalDate to, int limit) {
        int size = Math.min(Math.max(limit, 1), 1000);
        return repo.findByOccurredOnBetweenOrderByCreatedAtDesc(from, to, PageRequest.of(0, size))
                .stream().map(this::toResponse).toList();
    }

    /** Unacknowledged alerts from the last 2 days — feeds the dashboard banner. */
    @Transactional(readOnly = true)
    public List<AnomalyResponse> activeBanner() {
        LocalDate from = LocalDate.now().minusDays(1);
        return repo.findByAcknowledgedFalseAndOccurredOnGreaterThanEqualOrderByCreatedAtDesc(from)
                .stream().map(this::toResponse).toList();
    }

    private static boolean isAlerting(String severity) {
        return WARN.equals(severity) || CRITICAL.equals(severity);
    }

    private static String format(Candidate c) {
        String header = CRITICAL.equals(c.severity()) ? "🚨 ANOMALIYA (jiddiy)" : "⚠️ ANOMALIYA";
        return header + "\n\n" + c.message();
    }

    private AnomalyResponse toResponse(AnomalyAlert a) {
        return new AnomalyResponse(a.getId(), a.getSeverity(), a.getCode(), a.getMessage(),
                a.getCreatedAt(), a.getOccurredOn(), a.isAcknowledged(),
                a.getAcknowledgedBy(), a.getAcknowledgedAt(), a.getDetailJson());
    }
}
