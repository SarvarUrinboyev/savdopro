package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Shift;
import uz.barakat.market.domain.ShiftStatus;
import uz.barakat.market.dto.BalanceRequest;
import uz.barakat.market.dto.EndOfDayReport;
import uz.barakat.market.dto.ShiftCloseRequest;
import uz.barakat.market.dto.ShiftOpenRequest;
import uz.barakat.market.dto.ShiftResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.ShiftRepository;
import uz.barakat.market.telegram.TelegramService;

/** Opening, closing and the history of working shifts. */
@Service
@Transactional
public class ShiftService {

    private static final Logger log = LoggerFactory.getLogger(ShiftService.class);

    private final ShiftRepository shifts;
    private final BalanceService balanceService;
    private final ReportService reportService;
    private final ReportPdfService pdfService;
    private final TelegramService telegram;

    public ShiftService(ShiftRepository shifts, BalanceService balanceService,
                        ReportService reportService, ReportPdfService pdfService,
                        TelegramService telegram) {
        this.shifts = shifts;
        this.balanceService = balanceService;
        this.reportService = reportService;
        this.pdfService = pdfService;
        this.telegram = telegram;
    }

    /** The currently open shift, or {@code null} when none is open. */
    @Transactional(readOnly = true)
    public ShiftResponse current() {
        return shifts.findFirstByStatusOrderByOpenedAtDesc(ShiftStatus.OPEN)
                .map(this::toResponse)
                .orElse(null);
    }

    /** Opens a shift and records today's morning balance. */
    public ShiftResponse open(ShiftOpenRequest request) {
        shifts.findFirstByStatusOrderByOpenedAtDesc(ShiftStatus.OPEN).ifPresent(s -> {
            throw new BadRequestException("Smena allaqachon ochiq - avval uni yoping");
        });
        Shift shift = new Shift();
        shift.setOpenedAt(LocalDateTime.now());
        shift.setOpenedBy(request.openedBy() == null || request.openedBy().isBlank()
                ? "Egasi" : request.openedBy().strip());
        shift.setStatus(ShiftStatus.OPEN);
        shifts.save(shift);
        balanceService.set(new BalanceRequest(request.startingCash(), LocalDate.now()));
        return toResponse(shift);
    }

    /**
     * Closes the open shift (if any), pushes today's end-of-day report
     * to Telegram, and (Phase 4.2) attaches the branded sales-report
     * PDF for the day so the owner has a printable receipt sitting in
     * their chat — no need to switch back to the desktop.
     */
    public EndOfDayReport close(ShiftCloseRequest request) {
        LocalDate today = LocalDate.now();
        // Build the report first so we can snapshot the books' expected cash
        // onto the shift row before flipping it closed.
        EndOfDayReport text = reportService.sendToTelegram(today);
        BigDecimal counted = request == null ? null : request.countedCash();
        shifts.findFirstByStatusOrderByOpenedAtDesc(ShiftStatus.OPEN).ifPresent(shift -> {
            shift.setClosedAt(LocalDateTime.now());
            shift.setStatus(ShiftStatus.CLOSED);
            // Freeze expected (from the books) and counted (physical) cash on the
            // shift so it stays a self-contained reconciliation record even if
            // today's expenses are edited afterwards.
            shift.setExpectedCash(text.estimatedCash());
            shift.setCountedCash(counted);
            shifts.save(shift);
            alertCashShortfall(shift);
        });
        // Attach the PDF separately so the text summary still posts even
        // if PDF rendering or upload trips on a corner case.
        try {
            byte[] pdf = pdfService.salesReport(today, today);
            telegram.sendDocument(pdf,
                    "savdo-" + today + ".pdf",
                    "📊 Kunlik savdo hisoboti — " + today);
        } catch (RuntimeException ex) {
            log.warn("End-of-shift PDF delivery failed for {}: {}", today, ex.toString());
        }
        return text;
    }

    /**
     * Best-effort owner alert when the counted cash falls short of what the
     * books expect — a till discrepancy worth the owner's eyes. Async and
     * guarded exactly like the below-cost alert, so a Telegram hiccup never
     * blocks the close. A no-op when nothing was counted, the count meets or
     * beats expectations, or the owner bot isn't configured.
     */
    private void alertCashShortfall(Shift shift) {
        BigDecimal expected = shift.getExpectedCash();
        BigDecimal counted = shift.getCountedCash();
        if (counted == null || expected == null || !telegram.isConfigured()) {
            return;
        }
        BigDecimal shortfall = expected.subtract(counted);
        if (shortfall.signum() <= 0) {
            return; // exact or over the books — no loss to flag
        }
        try {
            String who = (shift.getOpenedBy() == null || shift.getOpenedBy().isBlank())
                    ? "—" : shift.getOpenedBy();
            String msg = "⚠️ KASSADA KAMOMAD — Smena #" + shift.getId() + "\n\n"
                    + "Kutilgan naqd: " + MoneyFormat.usd(expected) + "\n"
                    + "Sanab chiqilgan: " + MoneyFormat.usd(counted) + "\n"
                    + "Kamomad: " + MoneyFormat.usd(shortfall) + "\n"
                    + "Smenani ochgan: " + who + "\n\n"
                    + "Kassani va kassirlarni tekshiring.";
            CompletableFuture.runAsync(() -> telegram.sendMessage(msg));
        } catch (Exception ex) {
            log.warn("Cash-shortfall alert failed for shift {}: {}",
                    shift.getId(), ex.toString());
        }
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> history() {
        return shifts.findAllByOrderByOpenedAtDesc().stream().map(this::toResponse).toList();
    }

    /** Removes closed shifts from the history; keeps any open shift. */
    public int clearHistory() {
        List<Shift> closed = shifts.findAllByOrderByOpenedAtDesc().stream()
                .filter(s -> s.getStatus() == ShiftStatus.CLOSED)
                .toList();
        shifts.deleteAll(closed);
        return closed.size();
    }

    private ShiftResponse toResponse(Shift shift) {
        return Mappers.shift(shift, balanceService.startingCash(shift.getOpenedAt().toLocalDate()));
    }
}
