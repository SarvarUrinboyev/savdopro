package uz.barakat.market.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.GlAccount;
import uz.barakat.market.domain.GlAccountType;
import uz.barakat.market.domain.NormalBalance;
import uz.barakat.market.dto.AccountingDtos.GlAccountRequest;
import uz.barakat.market.dto.AccountingDtos.GlAccountResponse;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.GlAccountRepository;
import uz.barakat.market.repository.JournalLineRepository;

/**
 * The Chart of Accounts ("Hisoblar rejasi"). Owns the standard set of accounts
 * (seeded per shop on first use) plus user-defined ones, and is the lookup the
 * posting engine uses to turn a code into an account id.
 */
@Service
@Transactional
public class ChartOfAccountsService {

    // ---- Standard account codes the auto-posting engine targets ----
    public static final String CASH = "1100";            // Kassa (naqd pul)
    public static final String BANK = "1200";            // Bank / terminal
    public static final String INVENTORY = "1300";       // Tovar zaxirasi
    public static final String RECEIVABLE = "1400";      // Mijozlar qarzi (debitor)
    public static final String PAYABLE = "2100";         // Yetkazib beruvchiga qarz
    public static final String OWNER_EQUITY = "3100";    // Egasining kapitali
    public static final String RETAINED_EARNINGS = "3300"; // Taqsimlanmagan foyda
    public static final String OPENING_EQUITY = "3900";  // Boshlang'ich qoldiq
    public static final String SALES = "4100";           // Savdo tushumi
    public static final String SALES_DISCOUNT = "4200";  // Sotuv chegirmalari (contra)
    public static final String SALES_RETURNS = "4300";   // Tovar qaytarishlar (contra)
    public static final String OTHER_INCOME = "4900";    // Boshqa daromad
    public static final String COGS = "5100";            // Sotilgan tovar tannarxi
    public static final String SHRINKAGE = "5200";       // Tovar yo'qotish / hisobdan chiqarish
    public static final String SALARY = "6100";          // Ish haqi
    public static final String TAX = "6200";             // Soliqlar
    public static final String RENT = "6300";            // Ijara va kommunal
    public static final String OTHER_EXPENSE = "6900";   // Boshqa xarajatlar

    /** One row of the seeded standard chart. */
    private record Seed(String code, String name, GlAccountType type, NormalBalance nb) {
        Seed(String code, String name, GlAccountType type) {
            this(code, name, type, type.normalBalance());
        }
    }

    private static final List<Seed> STANDARD = List.of(
            new Seed(CASH, "Kassa (naqd pul)", GlAccountType.ASSET),
            new Seed(BANK, "Bank / terminal", GlAccountType.ASSET),
            new Seed(INVENTORY, "Tovar zaxirasi (ombor)", GlAccountType.ASSET),
            new Seed(RECEIVABLE, "Mijozlar qarzi (debitorlar)", GlAccountType.ASSET),
            new Seed(PAYABLE, "Yetkazib beruvchilarga qarz", GlAccountType.LIABILITY),
            new Seed(OWNER_EQUITY, "Egasining kapitali", GlAccountType.EQUITY),
            new Seed(RETAINED_EARNINGS, "Taqsimlanmagan foyda", GlAccountType.EQUITY),
            new Seed(OPENING_EQUITY, "Boshlang'ich qoldiq", GlAccountType.EQUITY),
            new Seed(SALES, "Savdo tushumi", GlAccountType.REVENUE),
            // Contra-revenue: a revenue account that normally carries a DEBIT.
            new Seed(SALES_DISCOUNT, "Sotuv chegirmalari", GlAccountType.REVENUE, NormalBalance.DEBIT),
            new Seed(SALES_RETURNS, "Tovar qaytarishlar", GlAccountType.REVENUE, NormalBalance.DEBIT),
            new Seed(OTHER_INCOME, "Boshqa daromad", GlAccountType.REVENUE),
            new Seed(COGS, "Sotilgan tovar tannarxi", GlAccountType.EXPENSE),
            new Seed(SHRINKAGE, "Tovar yo'qotish / hisobdan chiqarish", GlAccountType.EXPENSE),
            new Seed(SALARY, "Ish haqi", GlAccountType.EXPENSE),
            new Seed(TAX, "Soliqlar", GlAccountType.EXPENSE),
            new Seed(RENT, "Ijara va kommunal to'lovlar", GlAccountType.EXPENSE),
            new Seed(OTHER_EXPENSE, "Boshqa xarajatlar", GlAccountType.EXPENSE));

    private final GlAccountRepository accounts;
    private final JournalLineRepository lines;

    public ChartOfAccountsService(GlAccountRepository accounts, JournalLineRepository lines) {
        this.accounts = accounts;
        this.lines = lines;
    }

    /** Seeds the standard chart for the current shop the first time it's needed. */
    public void ensureSeeded() {
        if (accounts.countBySystemTrue() > 0) {
            return;
        }
        for (Seed s : STANDARD) {
            if (accounts.existsByCode(s.code())) {
                continue;
            }
            GlAccount a = new GlAccount();
            a.setCode(s.code());
            a.setName(s.name());
            a.setType(s.type());
            a.setNormalBalance(s.nb());
            a.setSystem(true);
            a.setActive(true);
            accounts.save(a);
        }
    }

    /** Resolves a standard code to its account (seeding the chart if needed). */
    public GlAccount accountByCode(String code) {
        ensureSeeded();
        return accounts.findFirstByCode(code)
                .orElseThrow(() -> new IllegalStateException("Hisob topilmadi: " + code));
    }

    /** All accounts keyed by code, chart seeded — used by the posting engine. */
    public Map<String, GlAccount> byCode() {
        ensureSeeded();
        Map<String, GlAccount> map = new LinkedHashMap<>();
        for (GlAccount a : accounts.findAllByOrderByCodeAsc()) {
            map.put(a.getCode(), a);
        }
        return map;
    }

    // ------------------------------------------------------------------ CRUD

    /** Writable (not read-only) so the standard chart can be seeded on first call. */
    public List<GlAccountResponse> list() {
        ensureSeeded();
        return accounts.findAllByOrderByCodeAsc().stream()
                .map(ChartOfAccountsService::toResponse).toList();
    }

    public GlAccountResponse create(GlAccountRequest req) {
        String code = req.code() == null ? "" : req.code().strip();
        if (code.isEmpty()) {
            throw new BadRequestException("Hisob kodi kiritilishi shart");
        }
        if (accounts.existsByCode(code)) {
            throw new BadRequestException("Bu kodli hisob allaqachon mavjud: " + code);
        }
        if (req.type() == null) {
            throw new BadRequestException("Hisob turi tanlanmagan");
        }
        GlAccount a = new GlAccount();
        a.setCode(code);
        a.setName(req.name().strip());
        a.setType(req.type());
        a.setNormalBalance(req.type().normalBalance());
        a.setParentId(req.parentId());
        a.setActive(req.active() == null || req.active());
        a.setDescription(blankToNull(req.description()));
        a.setSystem(false);
        return toResponse(accounts.save(a));
    }

    public GlAccountResponse update(Long id, GlAccountRequest req) {
        GlAccount a = find(id);
        // Name / description / active are always editable. Code + type are
        // locked on system accounts because the posting engine targets them.
        a.setName(req.name().strip());
        a.setDescription(blankToNull(req.description()));
        a.setParentId(req.parentId());
        if (req.active() != null) {
            a.setActive(req.active());
        }
        if (!a.isSystem()) {
            String code = req.code() == null ? a.getCode() : req.code().strip();
            if (!code.equals(a.getCode())) {
                if (accounts.existsByCode(code)) {
                    throw new BadRequestException("Bu kodli hisob allaqachon mavjud: " + code);
                }
                a.setCode(code);
            }
            if (req.type() != null) {
                a.setType(req.type());
                a.setNormalBalance(req.type().normalBalance());
            }
        }
        return toResponse(accounts.save(a));
    }

    public void delete(Long id) {
        GlAccount a = find(id);
        if (a.isSystem()) {
            throw new BadRequestException("Tizim hisobini o'chirib bo'lmaydi");
        }
        if (lines.existsByAccountId(id)) {
            throw new BadRequestException(
                    "Bu hisobda yozuvlar bor — o'chirib bo'lmaydi. Uni nofaol qiling.");
        }
        accounts.delete(a);
    }

    // --------------------------------------------------------------- helpers

    private GlAccount find(Long id) {
        return accounts.findById(id).orElseThrow(() -> NotFoundException.of("Hisob", id));
    }

    static GlAccountResponse toResponse(GlAccount a) {
        return new GlAccountResponse(a.getId(), a.getCode(), a.getName(), a.getType(),
                a.getNormalBalance(), a.getParentId(), a.isSystem(), a.isActive(),
                a.getDescription());
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }
}
