package uz.barakat.market.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.HomeExpense;
import uz.barakat.market.domain.PaymentType;
import uz.barakat.market.dto.BulkImportRequest;
import uz.barakat.market.dto.BulkImportResult;
import uz.barakat.market.dto.BulkParseResult;
import uz.barakat.market.dto.HomeExpenseRequest;
import uz.barakat.market.dto.HomeExpenseResponse;
import uz.barakat.market.dto.ParsedLine;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.HomeExpenseRepository;

/** Shop expenses ("Do'kon xarajatlari"). Same shape as market expenses but QARZGA is not allowed. */
@Service
@Transactional
public class HomeExpenseService {

    private final HomeExpenseRepository homeExpenses;
    private final BulkImportParser parser;
    private final ApplicationEventPublisher events;

    public HomeExpenseService(HomeExpenseRepository homeExpenses, BulkImportParser parser,
                              ApplicationEventPublisher events) {
        this.homeExpenses = homeExpenses;
        this.parser = parser;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<HomeExpenseResponse> listByDate(LocalDate date) {
        return homeExpenses.findByDateOrderByIdDesc(date).stream()
                .map(Mappers::homeExpense).toList();
    }

    @Transactional(readOnly = true)
    public List<HomeExpenseResponse> listByRange(LocalDate from, LocalDate to) {
        return homeExpenses.findByDateBetweenOrderByDateDescIdDesc(from, to).stream()
                .map(Mappers::homeExpense).toList();
    }

    @Transactional(readOnly = true)
    public HomeExpenseResponse get(Long id) {
        return Mappers.homeExpense(find(id));
    }

    public HomeExpenseResponse create(HomeExpenseRequest request) {
        rejectCredit(request.paymentType());
        HomeExpense expense = new HomeExpense();
        apply(expense, request);
        HomeExpense saved = homeExpenses.save(expense);
        events.publishEvent(new LedgerEvents.HomeExpenseRecorded(saved.getId()));
        return Mappers.homeExpense(saved);
    }

    public HomeExpenseResponse update(Long id, HomeExpenseRequest request) {
        rejectCredit(request.paymentType());
        HomeExpense expense = find(id);
        apply(expense, request);
        return Mappers.homeExpense(homeExpenses.save(expense));
    }

    public void delete(Long id) {
        homeExpenses.delete(find(id));
    }

    @Transactional(readOnly = true)
    public BulkParseResult preview(BulkImportRequest request) {
        return parser.parse(request.text(), request.date());
    }

    public BulkImportResult bulkImport(BulkImportRequest request) {
        BulkParseResult parsed = parser.parse(request.text(), request.date());
        List<String> errors = new ArrayList<>();
        int saved = 0;
        for (ParsedLine line : parsed.lines()) {
            if (!line.valid()) {
                errors.add("Qator " + line.lineNumber() + ": " + line.error());
                continue;
            }
            if (line.paymentType() == PaymentType.QARZGA) {
                errors.add("Qator " + line.lineNumber()
                        + ": do'kon xarajatida qarzga ruxsat etilmaydi");
                continue;
            }
            HomeExpense expense = new HomeExpense();
            expense.setDate(parsed.date());
            expense.setName(line.name());
            expense.setAmount(line.amount());
            expense.setPaymentType(line.paymentType());
            expense.setCashAmount(line.cashAmount());
            expense.setNaqdAmount(line.naqdAmount());
            expense.setCardAmount(line.cardAmount());
            expense.setCurrency(Currency.UZS);
            homeExpenses.save(expense);
            events.publishEvent(new LedgerEvents.HomeExpenseRecorded(expense.getId()));
            saved++;
        }
        return new BulkImportResult(parsed.date(), saved, parsed.lines().size() - saved, errors);
    }

    private void apply(HomeExpense expense, HomeExpenseRequest request) {
        expense.setDate(request.date() != null ? request.date() : LocalDate.now());
        expense.setName(request.name().strip());
        expense.setAmount(request.amount());
        expense.setPaymentType(request.paymentType());
        PaymentSplits.Split split = PaymentSplits.resolve(request.paymentType(), request.amount(),
                request.cashAmount(), request.naqdAmount(), request.cardAmount());
        expense.setCashAmount(split.cash());
        expense.setNaqdAmount(split.naqd());
        expense.setCardAmount(split.card());
        expense.setCurrency(request.currency() != null ? request.currency() : Currency.UZS);
        expense.setNote(request.note());
    }

    private void rejectCredit(PaymentType type) {
        if (type == PaymentType.QARZGA) {
            throw new BadRequestException("Do'kon xarajatida 'Qarzga' to'lov turi mavjud emas");
        }
    }

    private HomeExpense find(Long id) {
        return homeExpenses.findById(id)
                .orElseThrow(() -> NotFoundException.of("Do'kon xarajati", id));
    }
}
