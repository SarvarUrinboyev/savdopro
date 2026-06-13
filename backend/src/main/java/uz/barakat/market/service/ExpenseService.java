package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Currency;
import uz.barakat.market.domain.Debtor;
import uz.barakat.market.domain.Expense;
import uz.barakat.market.domain.PaymentType;
import uz.barakat.market.dto.BulkImportRequest;
import uz.barakat.market.dto.BulkImportResult;
import uz.barakat.market.dto.BulkParseResult;
import uz.barakat.market.dto.ExpenseRequest;
import uz.barakat.market.dto.ExpenseResponse;
import uz.barakat.market.dto.ParsedLine;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.DebtorRepository;
import uz.barakat.market.repository.ExpenseRepository;

/** Supermarket expenses: CRUD, the KASSA/NAQD/KARTA split and bulk import. */
@Service
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final DebtorRepository debtors;
    private final BulkImportParser parser;
    private final MoneyConverter converter;
    private final ApplicationEventPublisher events;

    public ExpenseService(ExpenseRepository expenses, DebtorRepository debtors,
                          BulkImportParser parser, MoneyConverter converter,
                          ApplicationEventPublisher events) {
        this.expenses = expenses;
        this.debtors = debtors;
        this.parser = parser;
        this.converter = converter;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listByDate(LocalDate date) {
        return expenses.findByDateOrderByIdDesc(date).stream().map(Mappers::expense).toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listByRange(LocalDate from, LocalDate to) {
        return expenses.findByDateBetweenOrderByDateDescIdDesc(from, to)
                .stream().map(Mappers::expense).toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long id) {
        return Mappers.expense(find(id));
    }

    public ExpenseResponse create(ExpenseRequest request) {
        Expense expense = new Expense();
        apply(expense, request);
        expenses.save(expense);
        if (expense.getPaymentType() == PaymentType.QARZGA) {
            createLinkedDebt(expense);
        }
        events.publishEvent(new LedgerEvents.ExpenseRecorded(expense.getId()));
        return Mappers.expense(expense);
    }

    public ExpenseResponse update(Long id, ExpenseRequest request) {
        Expense expense = find(id);
        apply(expense, request);
        return Mappers.expense(expenses.save(expense));
    }

    public void delete(Long id) {
        expenses.delete(find(id));
    }

    /** Parse-only: lets the UI preview a bulk import before committing it. */
    @Transactional(readOnly = true)
    public BulkParseResult preview(BulkImportRequest request) {
        return parser.parse(request.text(), request.date());
    }

    /** Parse and persist every valid line; invalid lines are reported, not saved. */
    public BulkImportResult bulkImport(BulkImportRequest request) {
        BulkParseResult parsed = parser.parse(request.text(), request.date());
        List<String> errors = new ArrayList<>();
        int saved = 0;
        for (ParsedLine line : parsed.lines()) {
            if (!line.valid()) {
                errors.add("Qator " + line.lineNumber() + ": " + line.error());
                continue;
            }
            Expense expense = new Expense();
            expense.setDate(parsed.date());
            expense.setName(line.name());
            expense.setAmount(line.amount());
            expense.setPaymentType(line.paymentType());
            expense.setCashAmount(line.cashAmount());
            expense.setNaqdAmount(line.naqdAmount());
            expense.setCardAmount(line.cardAmount());
            expense.setCurrency(Currency.UZS);
            expenses.save(expense);
            if (expense.getPaymentType() == PaymentType.QARZGA) {
                createLinkedDebt(expense);
            }
            events.publishEvent(new LedgerEvents.ExpenseRecorded(expense.getId()));
            saved++;
        }
        return new BulkImportResult(parsed.date(), saved, parsed.lines().size() - saved, errors);
    }

    private void apply(Expense expense, ExpenseRequest request) {
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

    /** A QARZGA expense automatically opens a matching "my debt" record (in USD). */
    private void createLinkedDebt(Expense expense) {
        Debtor debtor = new Debtor();
        debtor.setDate(expense.getDate());
        debtor.setName(expense.getName());
        debtor.setProductName(expense.getName());
        debtor.setOriginalAmount(converter.toUsd(expense.getAmount(), expense.getCurrency()));
        debtor.setPaidAmount(BigDecimal.ZERO);
        debtor.setPaid(false);
        debtor.setNote("Avtomatik: QARZGA xarajatdan yaratildi");
        debtors.save(debtor);
    }

    private Expense find(Long id) {
        return expenses.findById(id).orElseThrow(() -> NotFoundException.of("Xarajat", id));
    }
}
