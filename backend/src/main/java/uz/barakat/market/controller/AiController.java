package uz.barakat.market.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.market.service.AiChatService;
import uz.barakat.market.service.AiChatService.ChatRequest;
import uz.barakat.market.service.AiChatService.ChatResponse;
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

    public AiController(AiChatService chat, ForecastService forecast,
                        PredictionService prediction, AnomalyService anomaly) {
        this.chat = chat;
        this.forecast = forecast;
        this.prediction = prediction;
        this.anomaly = anomaly;
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

    /** Statistical anomalies (high refunds, late-night sales, product spikes). */
    @GetMapping("/anomalies")
    public List<Anomaly> anomalies() {
        return anomaly.check();
    }
}
