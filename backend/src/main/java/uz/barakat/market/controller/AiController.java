package uz.barakat.market.controller;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.dto.AnomalyResponse;
import uz.barakat.market.service.AiChatService;
import uz.barakat.market.service.AiChatService.ChatRequest;
import uz.barakat.market.service.AiChatService.ChatResponse;
import uz.barakat.market.service.AnomalyMonitorService;
import uz.barakat.market.service.AnomalyService;
import uz.barakat.market.service.AnomalyService.Anomaly;
import uz.barakat.market.service.ForecastService;
import uz.barakat.market.service.ForecastService.ProductForecast;
import uz.barakat.market.service.ForecastService.SlowMover;
import uz.barakat.market.service.PredictionService;
import uz.barakat.market.service.PredictionService.CashboxForecast;

/**
 * Smart endpoints — chatbot, forecasting and slow-mover recommendations.
 *
 * Mounted under {@code /api/ai/*} so the frontend can hit one prefix
 * for all AI-flavoured features and the Sidebar can gate visibility
 * on a single module key.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatService chat;
    private final ForecastService forecast;
    private final PredictionService prediction;
    private final AnomalyService anomaly;
    private final AnomalyMonitorService anomalyMonitor;

    public AiController(AiChatService chat, ForecastService forecast,
                        PredictionService prediction, AnomalyService anomaly,
                        AnomalyMonitorService anomalyMonitor) {
        this.chat = chat;
        this.forecast = forecast;
        this.prediction = prediction;
        this.anomaly = anomaly;
        this.anomalyMonitor = anomalyMonitor;
    }

    /** POST a natural-language question, get a plain-language answer. */
    @PostMapping("/ask")
    public ChatResponse ask(@Valid @RequestBody ChatRequest req) {
        return chat.ask(req);
    }

    /** Per-product velocity + days-of-stock + run-out date forecasts. */
    @GetMapping("/forecast")
    public List<ProductForecast> forecast() {
        return forecast.forecast();
    }

    /** Just the re-order subset — for the "Buyurtma kerak" widget. */
    @GetMapping("/reorder-queue")
    public List<ProductForecast> reorderQueue() {
        return forecast.reorderQueue();
    }

    /** Slow movers + recommended discount %. */
    @GetMapping("/slow-movers")
    public List<SlowMover> slowMovers() {
        return forecast.slowMovers();
    }

    /** Predictive cashbox: next-7-days revenue + sales-count projection. */
    @GetMapping("/cashbox-forecast")
    public CashboxForecast cashboxForecast() {
        return prediction.forecastNext7Days();
    }

    /** Live banner feed: transient rules + persisted unacknowledged alerts. */
    @GetMapping("/anomalies")
    public List<Anomaly> anomalies() {
        return anomaly.check();
    }

    /** Persisted anomaly history (default: last 30 days), newest first. */
    @GetMapping("/anomalies/history")
    public List<AnomalyResponse> anomalyHistory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit) {
        LocalDate today = LocalDate.now();
        LocalDate start = from != null ? from : today.minusDays(30);
        LocalDate end = to != null ? to : today;
        return anomalyMonitor.history(start, end, limit);
    }

    /** Acknowledge an anomaly (owner action — gated to REPORTS:WRITE). */
    @PostMapping("/anomalies/{id}/acknowledge")
    public AnomalyResponse acknowledgeAnomaly(@PathVariable Long id, Principal principal) {
        return anomalyMonitor.acknowledge(id, principal == null ? null : principal.getName());
    }
}
