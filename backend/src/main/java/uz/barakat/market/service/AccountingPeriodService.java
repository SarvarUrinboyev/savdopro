package uz.barakat.market.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.AccountingPeriod;
import uz.barakat.market.domain.PeriodStatus;
import uz.barakat.market.dto.AccountingDtos.ClosePeriodRequest;
import uz.barakat.market.dto.AccountingDtos.PeriodResponse;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.AccountingPeriodRepository;

/**
 * Accounting periods and the lock they enforce. Closing a period freezes every
 * journal entry dated inside it — no create / edit / delete / auto-post may
 * touch a closed range.
 */
@Service
@Transactional
public class AccountingPeriodService {

    private final AccountingPeriodRepository periods;

    public AccountingPeriodService(AccountingPeriodRepository periods) {
        this.periods = periods;
    }

    @Transactional(readOnly = true)
    public List<PeriodResponse> list() {
        return periods.findAllByOrderByPeriodStartDesc().stream().map(this::toResponse).toList();
    }

    /**
     * Throws if {@code date} falls inside a CLOSED period. Called before every
     * posting — manual or automatic — so a locked month can never change.
     */
    @Transactional(readOnly = true)
    public void assertOpen(LocalDate date) {
        if (date == null) {
            return;
        }
        if (!periods.findClosedCovering(date).isEmpty()) {
            throw new BadRequestException(
                    "Bu sana yopilgan hisobot davriga to'g'ri keladi — yozuv kiritib bo'lmaydi");
        }
    }

    /** True without throwing — used by best-effort auto-posting to skip silently. */
    @Transactional(readOnly = true)
    public boolean isLocked(LocalDate date) {
        return date != null && !periods.findClosedCovering(date).isEmpty();
    }

    /** Closes (locks) a period; if one with the same range exists it's updated. */
    public PeriodResponse close(ClosePeriodRequest req) {
        LocalDate start = req.periodStart();
        LocalDate end = req.periodEnd();
        if (start == null || end == null) {
            throw new BadRequestException("Davr boshlanishi va tugashi kiritilishi shart");
        }
        if (end.isBefore(start)) {
            throw new BadRequestException("Davr tugashi boshlanishidan oldin bo'la olmaydi");
        }
        AccountingPeriod p = periods.findAllByOrderByPeriodStartDesc().stream()
                .filter(x -> x.getPeriodStart().equals(start) && x.getPeriodEnd().equals(end))
                .findFirst()
                .orElseGet(AccountingPeriod::new);
        p.setPeriodStart(start);
        p.setPeriodEnd(end);
        p.setStatus(PeriodStatus.CLOSED);
        p.setClosedAt(LocalDateTime.now());
        p.setClosedBy(currentUser());
        p.setNote(blankToNull(req.note()));
        return toResponse(periods.save(p));
    }

    /** Re-opens a closed period so entries can be posted into it again. */
    public PeriodResponse reopen(Long id) {
        AccountingPeriod p = find(id);
        p.setStatus(PeriodStatus.OPEN);
        p.setClosedAt(null);
        p.setClosedBy(null);
        return toResponse(periods.save(p));
    }

    public void delete(Long id) {
        periods.delete(find(id));
    }

    // --------------------------------------------------------------- helpers

    private AccountingPeriod find(Long id) {
        return periods.findById(id).orElseThrow(() -> NotFoundException.of("Davr", id));
    }

    private PeriodResponse toResponse(AccountingPeriod p) {
        return new PeriodResponse(p.getId(), p.getPeriodStart(), p.getPeriodEnd(),
                p.getStatus(), p.getClosedAt(), p.getClosedBy(), p.getNote());
    }

    static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
