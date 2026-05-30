package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Customer;
import uz.barakat.market.domain.CustomerTransaction;
import uz.barakat.market.domain.CustomerTxType;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.ShiftStatus;
import uz.barakat.market.domain.StockReason;
import uz.barakat.market.dto.CustomerDetailResponse;
import uz.barakat.market.dto.CustomerRequest;
import uz.barakat.market.dto.CustomerResponse;
import uz.barakat.market.dto.CustomerTransactionRequest;
import uz.barakat.market.dto.CustomerTransactionResponse;
import uz.barakat.market.dto.StockAdjustRequest;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.CustomerRepository;
import uz.barakat.market.repository.CustomerTransactionRepository;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.ShiftRepository;
import uz.barakat.market.telegram.CustomerBotNotifier;

/** Customers ("Mijozlar"): contact details plus the goods / payment ledger. */
@Service
@Transactional
public class CustomerService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final CustomerRepository customers;
    private final CustomerTransactionRepository transactions;
    private final ProductRepository products;
    private final ProductService productService;
    private final CustomerBotNotifier botNotifier;
    private final ShiftRepository shifts;

    public CustomerService(CustomerRepository customers,
                           CustomerTransactionRepository transactions,
                           ProductRepository products,
                           ProductService productService,
                           CustomerBotNotifier botNotifier,
                           ShiftRepository shifts) {
        this.customers = customers;
        this.transactions = transactions;
        this.products = products;
        this.productService = productService;
        this.botNotifier = botNotifier;
        this.shifts = shifts;
    }

    /** Defense-in-depth: warehouse-touching sales are only allowed when a shift is open. */
    private void requireOpenShift() {
        if (shifts.findFirstByStatusOrderByOpenedAtDesc(ShiftStatus.OPEN).isEmpty()) {
            throw new BadRequestException(
                    "Smena yopiq. Tovar berish uchun avval smenani oching.");
        }
    }

    /** All customers, each with its ledger totals and balance. */
    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        // Balances come from one GROUP BY (aggregateLedgerTotals) instead of
        // loading every customer's full ledger into memory — the old
        // transactions.findAll() pulled the entire ledger table on each call.
        Map<Long, CustomerTransactionRepository.LedgerTotals> totals =
                transactions.aggregateLedgerTotals(CustomerTxType.GOODS, CustomerTxType.PAYMENT)
                        .stream()
                        .collect(Collectors.toMap(
                                CustomerTransactionRepository.LedgerTotals::getCustomerId,
                                t -> t));
        return customers.findAllByOrderByNameAsc().stream()
                .map(c -> {
                    var t = totals.get(c.getId());
                    BigDecimal goods = t != null && t.getGoods() != null ? t.getGoods() : ZERO;
                    BigDecimal paid = t != null && t.getPaid() != null ? t.getPaid() : ZERO;
                    int count = t != null ? (int) t.getTxCount() : 0;
                    return Mappers.customer(c, goods, paid, count);
                })
                .toList();
    }

    /** A customer with the full ledger (goods given + payments received). */
    @Transactional(readOnly = true)
    public CustomerDetailResponse detail(Long id) {
        Customer customer = find(id);
        List<CustomerTransaction> ledger =
                transactions.findByCustomerIdOrderByDateDescIdDesc(id);
        List<CustomerTransactionResponse> lines = ledger.stream()
                .map(Mappers::customerTransaction).toList();
        return new CustomerDetailResponse(toResponse(customer, ledger), lines);
    }

    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        customers.save(customer);
        return toResponse(customer, List.of());
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = find(id);
        apply(customer, request);
        customers.save(customer);
        return toResponse(customer, transactions.findByCustomerIdOrderByDateDescIdDesc(id));
    }

    /** Removes the customer; the database cascade removes the ledger rows. */
    public void delete(Long id) {
        customers.delete(find(id));
    }

    /**
     * Redeem loyalty points for a UZS-equivalent discount.
     *
     * <p>Returns the new balance. The points-to-UZS ratio is the inverse
     * of the earn rule in {@code PaymentService} — 1 point = 1 000 UZS
     * off — so the cashier can simply read the value to the customer.
     * Throws if the customer doesn't have enough points; throws if
     * {@code amount} is non-positive (would let a malicious client
     * increase the balance).
     */
    public CustomerResponse redeemPoints(Long customerId, long pointsToBurn) {
        if (pointsToBurn <= 0) {
            throw new BadRequestException("Ball miqdori 0 dan katta bo'lishi kerak");
        }
        Customer customer = find(customerId);
        if (customer.getPointsBalance() < pointsToBurn) {
            throw new BadRequestException(
                    "Yetarli ball yo'q: mavjud " + customer.getPointsBalance()
                            + ", so'ralgan " + pointsToBurn);
        }
        customer.setPointsBalance(customer.getPointsBalance() - pointsToBurn);
        customers.save(customer);

        CustomerTransaction row = new CustomerTransaction();
        row.setCustomerId(customerId);
        row.setDate(java.time.LocalDate.now());
        row.setType(CustomerTxType.PAYMENT);
        row.setDescription("Loyalty redeem: −" + pointsToBurn + " ball");
        row.setAmount(java.math.BigDecimal.ZERO);
        row.setPointsDelta(-pointsToBurn);
        transactions.save(row);

        return toResponse(customer,
                transactions.findByCustomerIdOrderByDateDescIdDesc(customerId));
    }

    /**
     * Adds a ledger line. GOODS sells a real warehouse product: stock is
     * checked and deducted (a SALE movement). PAYMENT just records money in.
     * If the customer linked the Telegram bot, they are notified.
     */
    public CustomerDetailResponse addTransaction(Long customerId,
                                                 CustomerTransactionRequest request) {
        if (request.type() == CustomerTxType.GOODS) {
            requireOpenShift();
        }
        Customer customer = find(customerId);
        CustomerTransaction tx = new CustomerTransaction();
        tx.setCustomerId(customerId);
        tx.setDate(request.date() != null ? request.date() : LocalDate.now());
        tx.setType(request.type());
        tx.setAmount(request.amount());
        tx.setNote(blankToNull(request.note()));
        if (request.type() == CustomerTxType.GOODS) {
            tx.setDescription(sellFromWarehouse(customer, request));
        } else {
            tx.setDescription(blankToNull(request.description()));
        }
        transactions.save(tx);
        botNotifier.notifyTransaction(customer, tx);
        return detail(customerId);
    }

    /**
     * Adds several ledger lines at once - a basket of goods sold together.
     * Processed in one transaction: if any product is missing or out of
     * stock, the whole basket is rejected and nothing is saved.
     */
    public CustomerDetailResponse addTransactions(Long customerId,
                                                  List<CustomerTransactionRequest> requests) {
        Customer customer = find(customerId);
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("Kamida bitta tovar tanlanishi kerak");
        }
        boolean hasGoods = requests.stream()
                .anyMatch(r -> r.type() == CustomerTxType.GOODS);
        if (hasGoods) {
            requireOpenShift();
        }
        List<CustomerTransaction> created = new ArrayList<>();
        for (CustomerTransactionRequest request : requests) {
            CustomerTransaction tx = new CustomerTransaction();
            tx.setCustomerId(customerId);
            tx.setDate(request.date() != null ? request.date() : LocalDate.now());
            tx.setType(request.type());
            tx.setAmount(request.amount());
            tx.setNote(blankToNull(request.note()));
            if (request.type() == CustomerTxType.GOODS) {
                tx.setDescription(sellFromWarehouse(customer, request));
            } else {
                tx.setDescription(blankToNull(request.description()));
            }
            transactions.save(tx);
            created.add(tx);
        }
        botNotifier.notifyBatch(customer, created);
        return detail(customerId);
    }

    /**
     * Edits an existing ledger line - corrects amount / description / date /
     * note. The line type and any warehouse stock already moved are left
     * unchanged (this is a correction, not a new sale).
     */
    public CustomerDetailResponse updateTransaction(Long customerId, Long transactionId,
                                                    CustomerTransactionRequest request) {
        find(customerId);
        CustomerTransaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> NotFoundException.of("Amal", transactionId));
        if (!tx.getCustomerId().equals(customerId)) {
            throw new BadRequestException("Amal bu mijozga tegishli emas");
        }
        if (request.date() != null) {
            tx.setDate(request.date());
        }
        tx.setAmount(request.amount());
        tx.setDescription(blankToNull(request.description()));
        tx.setNote(blankToNull(request.note()));
        transactions.save(tx);
        return detail(customerId);
    }

    public CustomerDetailResponse deleteTransaction(Long customerId, Long transactionId) {
        find(customerId);
        CustomerTransaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> NotFoundException.of("Amal", transactionId));
        if (!tx.getCustomerId().equals(customerId)) {
            throw new BadRequestException("Amal bu mijozga tegishli emas");
        }
        transactions.delete(tx);
        return detail(customerId);
    }

    // --------------------------------------------------------------- helpers

    /**
     * Validates the chosen warehouse product, removes the sold quantity
     * from stock (logging a SALE movement) and returns a ledger description.
     */
    private String sellFromWarehouse(Customer customer, CustomerTransactionRequest request) {
        if (request.productId() == null) {
            throw new BadRequestException("Tovar tanlanishi shart");
        }
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new BadRequestException("Tanlangan tovar omborda topilmadi"));
        int qty = request.quantity() == null ? 1 : request.quantity();
        if (qty < 1) {
            throw new BadRequestException("Tovar soni kamida 1 bo'lishi kerak");
        }
        if (product.getQuantity() < qty) {
            throw new BadRequestException("«" + product.getName()
                    + "» — omborda yetarli emas. Qoldiq: " + product.getQuantity() + " dona");
        }
        productService.adjustStock(product.getId(), new StockAdjustRequest(
                -qty, StockReason.SALE, "Mijozga berildi: " + customer.getName()));
        return qty > 1 ? product.getName() + " × " + qty : product.getName();
    }

    private static CustomerResponse toResponse(Customer customer,
                                               List<CustomerTransaction> ledger) {
        BigDecimal goods = ZERO;
        BigDecimal paid = ZERO;
        for (CustomerTransaction tx : ledger) {
            if (tx.getType() == CustomerTxType.GOODS) {
                goods = goods.add(tx.getAmount());
            } else {
                paid = paid.add(tx.getAmount());
            }
        }
        return Mappers.customer(customer, goods, paid, ledger.size());
    }

    private static void apply(Customer customer, CustomerRequest request) {
        customer.setName(request.name().strip());
        customer.setPhone(blankToNull(request.phone()));
        customer.setAddress(blankToNull(request.address()));
        customer.setNote(blankToNull(request.note()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> NotFoundException.of("Mijoz", id));
    }
}
