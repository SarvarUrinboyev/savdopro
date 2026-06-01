package uz.barakat.market.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Payment;
import uz.barakat.market.domain.PaymentCategory;
import uz.barakat.market.domain.PaymentDirection;
import uz.barakat.market.domain.Supplier;
import uz.barakat.market.dto.PaymentResponse;
import uz.barakat.market.dto.SupplierDetailResponse;
import uz.barakat.market.dto.SupplierRequest;
import uz.barakat.market.dto.SupplierResponse;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.PaymentRepository;
import uz.barakat.market.repository.SupplierRepository;
import uz.barakat.market.util.PhoneUtil;

/**
 * Suppliers ("Yetkazib beruvchilar"): basic CRUD + a derived balance
 * that walks the payment journal so the contact card shows how much
 * we've already paid them.
 */
@Service
@Transactional
public class SupplierService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final LocalDate FAR_PAST = LocalDate.of(2000, 1, 1);
    private static final LocalDate FAR_FUTURE = LocalDate.of(2100, 1, 1);

    private final SupplierRepository suppliers;
    private final PaymentRepository payments;
    private final MoneyConverter converter;

    public SupplierService(SupplierRepository suppliers,
                           PaymentRepository payments,
                           MoneyConverter converter) {
        this.suppliers = suppliers;
        this.payments = payments;
        this.converter = converter;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        // Pull every supplier-category payment once, then index by lowercase
        // party name to compute each supplier's running total in O(n + m).
        List<Payment> all = supplierPayments();
        return suppliers.findAllByOrderByNameAsc().stream()
                .map(s -> toResponse(s, all))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierDetailResponse detail(Long id) {
        Supplier s = find(id);
        List<Payment> mine = supplierPayments().stream()
                .filter(p -> p.getParty() != null
                        && p.getParty().equalsIgnoreCase(s.getName()))
                .sorted(Comparator.comparing(Payment::getDate).reversed()
                        .thenComparing(Comparator.comparing(Payment::getId).reversed()))
                .toList();
        List<PaymentResponse> rows = mine.stream().map(Mappers::payment).toList();
        return new SupplierDetailResponse(toResponse(s, mine), rows);
    }

    public SupplierResponse create(SupplierRequest request) {
        Supplier s = new Supplier();
        apply(s, request);
        requirePhoneUnique(s.getPhone(), null);
        return toResponse(suppliers.save(s), List.of());
    }

    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier s = find(id);
        apply(s, request);
        requirePhoneUnique(s.getPhone(), id);
        return toResponse(suppliers.save(s), supplierPayments());
    }

    /** Rejects the save when another supplier in this tenant already uses the phone. */
    private void requirePhoneUnique(String phone, Long selfId) {
        if (phone == null || phone.isBlank()) return;
        boolean taken = selfId == null
                ? suppliers.existsByPhone(phone)
                : suppliers.existsByPhoneAndIdNot(phone, selfId);
        if (taken) {
            throw new BadRequestException("Bu telefon raqam allaqachon yetkazib beruvchiga biriktirilgan: " + phone);
        }
    }

    public void delete(Long id) {
        suppliers.delete(find(id));
    }

    // --------------------------------------------------------------- helpers

    private List<Payment> supplierPayments() {
        // Pull a wide window; this scales fine for desktop / small shops.
        return payments.findByDateBetweenOrderByDateDescIdDesc(FAR_PAST, FAR_FUTURE)
                .stream()
                .filter(p -> p.getCategory() == PaymentCategory.SUPPLIER)
                .toList();
    }

    private SupplierResponse toResponse(Supplier s, List<Payment> pool) {
        BigDecimal paid = pool.stream()
                .filter(p -> p.getParty() != null
                        && p.getParty().equalsIgnoreCase(s.getName())
                        && p.getDirection() == PaymentDirection.OUTGOING)
                .map(p -> converter.toUsd(p.getAmount(), p.getCurrency()))
                .reduce(ZERO, BigDecimal::add);
        // Without goods-received tracking, balance = paid (running cash out).
        // Once goods receipts are wired in, balance becomes (paid - received).
        return new SupplierResponse(
                s.getId(), s.getName(), s.getPhone(), s.getAddress(), s.getNote(),
                paid, paid, s.getCreatedAt());
    }

    private static void apply(Supplier s, SupplierRequest request) {
        s.setName(request.name().strip());
        s.setPhone(PhoneUtil.normalize(request.phone()));
        s.setAddress(blankToNull(request.address()));
        s.setNote(blankToNull(request.note()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Supplier find(Long id) {
        return suppliers.findById(id)
                .orElseThrow(() -> NotFoundException.of("Yetkazib beruvchi", id));
    }
}
