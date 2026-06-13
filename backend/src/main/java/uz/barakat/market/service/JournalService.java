package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.GlAccount;
import uz.barakat.market.domain.JournalEntry;
import uz.barakat.market.domain.JournalLine;
import uz.barakat.market.domain.JournalSource;
import uz.barakat.market.dto.AccountingDtos.JournalEntryRequest;
import uz.barakat.market.dto.AccountingDtos.JournalEntryResponse;
import uz.barakat.market.dto.AccountingDtos.JournalLineRequest;
import uz.barakat.market.dto.AccountingDtos.JournalLineResponse;
import uz.barakat.market.dto.AccountingDtos.JournalListResponse;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.GlAccountRepository;
import uz.barakat.market.repository.JournalEntryRepository;

/**
 * Manual journal entries plus the read model the Journal page renders. Every
 * entry must balance (∑debit = ∑credit in USD) and may not touch a closed
 * period. Auto-posted entries are created by {@link LedgerPostingService} and
 * are read-only here.
 */
@Service
@Transactional
public class JournalService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /** Per-entry rounding slack when summing converted lines (1 tiyin/cent). */
    private static final BigDecimal BALANCE_EPS = new BigDecimal("0.01");

    private final JournalEntryRepository entries;
    private final GlAccountRepository accounts;
    private final MoneyConverter converter;
    private final AccountingPeriodService periods;

    public JournalService(JournalEntryRepository entries, GlAccountRepository accounts,
                          MoneyConverter converter, AccountingPeriodService periods) {
        this.entries = entries;
        this.accounts = accounts;
        this.converter = converter;
        this.periods = periods;
    }

    @Transactional(readOnly = true)
    public JournalListResponse list(LocalDate from, LocalDate to) {
        Map<Long, GlAccount> byId = accountsById();
        List<JournalEntryResponse> rows = entries
                .findByEntryDateBetweenOrderByEntryDateDescIdDesc(from, to).stream()
                .map(e -> toResponse(e, byId))
                .toList();
        BigDecimal totalDebit = rows.stream().map(JournalEntryResponse::totalDebit)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal totalCredit = rows.stream().map(JournalEntryResponse::totalCredit)
                .reduce(ZERO, BigDecimal::add);
        return new JournalListResponse(from, to, totalDebit, totalCredit, rows);
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse get(Long id) {
        JournalEntry e = find(id);
        return toResponse(e, accountsById());
    }

    /** Creates a balanced manual entry. */
    public JournalEntryResponse create(JournalEntryRequest req) {
        LocalDate date = req.entryDate() != null ? req.entryDate() : LocalDate.now();
        periods.assertOpen(date);

        List<JournalLineRequest> reqLines = req.lines();
        if (reqLines == null || reqLines.size() < 2) {
            throw new BadRequestException("Yozuvda kamida 2 ta qator bo'lishi kerak");
        }
        Map<Long, GlAccount> byId = accountsById();

        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(date);
        entry.setMemo(blankToNull(req.memo()));
        entry.setSource(JournalSource.MANUAL);
        entry.setPosted(true);
        entry.setCreatedBy(AccountingPeriodService.currentUser());

        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
        for (JournalLineRequest l : reqLines) {
            if (l.accountId() == null || !byId.containsKey(l.accountId())) {
                throw new BadRequestException("Hisob tanlanmagan yoki topilmadi");
            }
            BigDecimal debit = nz(l.debit());
            BigDecimal credit = nz(l.credit());
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new BadRequestException("Debet/kredit manfiy bo'la olmaydi");
            }
            if (debit.signum() == 0 && credit.signum() == 0) {
                continue;   // skip blank rows the UI may submit
            }
            if (debit.signum() > 0 && credit.signum() > 0) {
                throw new BadRequestException(
                        "Bitta qatorda ham debet ham kredit bo'lishi mumkin emas");
            }
            Currency cur = l.currency() != null ? l.currency() : Currency.USD;
            BigDecimal debitUsd = converter.toUsd(debit, cur);
            BigDecimal creditUsd = converter.toUsd(credit, cur);

            JournalLine line = new JournalLine();
            line.setAccountId(l.accountId());
            line.setDebit(debitUsd);
            line.setCredit(creditUsd);
            line.setCurrency(cur);
            line.setOrigAmount(debit.signum() > 0 ? debit : credit);
            line.setMemo(blankToNull(l.memo()));
            entry.addLine(line);

            totalDebit = totalDebit.add(debitUsd);
            totalCredit = totalCredit.add(creditUsd);
        }
        if (entry.getLines().size() < 2) {
            throw new BadRequestException("Yozuvda kamida 2 ta to'ldirilgan qator bo'lishi kerak");
        }
        if (totalDebit.subtract(totalCredit).abs().compareTo(BALANCE_EPS) > 0) {
            throw new BadRequestException("Debet (" + totalDebit + ") va kredit ("
                    + totalCredit + ") teng emas");
        }
        return toResponse(entries.save(entry), byId);
    }

    /** Posts a mirror entry that reverses {@code id} (storno). Original is kept. */
    public JournalEntryResponse reverse(Long id) {
        JournalEntry orig = find(id);
        LocalDate today = LocalDate.now();
        periods.assertOpen(today);

        JournalEntry rev = new JournalEntry();
        rev.setEntryDate(today);
        rev.setMemo("Storno #" + id + (orig.getMemo() != null ? " — " + orig.getMemo() : ""));
        rev.setSource(JournalSource.MANUAL);
        rev.setPosted(true);
        rev.setReversedEntryId(id);
        rev.setCreatedBy(AccountingPeriodService.currentUser());
        for (JournalLine l : orig.getLines()) {
            JournalLine m = new JournalLine();
            m.setAccountId(l.getAccountId());
            m.setDebit(l.getCredit());     // swap
            m.setCredit(l.getDebit());
            m.setCurrency(l.getCurrency());
            m.setOrigAmount(l.getOrigAmount());
            m.setMemo("Storno");
            rev.addLine(m);
        }
        return toResponse(entries.save(rev), accountsById());
    }

    public void delete(Long id) {
        JournalEntry e = find(id);
        if (e.getSource() != JournalSource.MANUAL) {
            throw new BadRequestException(
                    "Avtomatik yozuvni o'chirib bo'lmaydi (storno qiling)");
        }
        periods.assertOpen(e.getEntryDate());
        entries.delete(e);
    }

    // --------------------------------------------------------------- helpers

    private Map<Long, GlAccount> accountsById() {
        return accounts.findAll().stream()
                .collect(Collectors.toMap(GlAccount::getId, Function.identity()));
    }

    private JournalEntry find(Long id) {
        return entries.findById(id).orElseThrow(() -> NotFoundException.of("Jurnal yozuvi", id));
    }

    static JournalEntryResponse toResponse(JournalEntry e, Map<Long, GlAccount> byId) {
        List<JournalLineResponse> lines = new ArrayList<>();
        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
        for (JournalLine l : e.getLines()) {
            GlAccount a = byId.get(l.getAccountId());
            lines.add(new JournalLineResponse(
                    l.getId(), l.getAccountId(),
                    a != null ? a.getCode() : null,
                    a != null ? a.getName() : null,
                    a != null ? a.getType() : null,
                    l.getDebit(), l.getCredit(), l.getCurrency(), l.getOrigAmount(), l.getMemo()));
            totalDebit = totalDebit.add(nz(l.getDebit()));
            totalCredit = totalCredit.add(nz(l.getCredit()));
        }
        return new JournalEntryResponse(
                e.getId(), e.getEntryDate(), e.getMemo(), e.getSource(), e.getSourceRef(),
                e.isPosted(), e.getCreatedBy(), e.getReversedEntryId(),
                totalDebit, totalCredit, e.getCreatedAt(), lines);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
