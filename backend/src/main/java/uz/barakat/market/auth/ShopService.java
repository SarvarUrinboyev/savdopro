package uz.barakat.market.auth;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.exception.NotFoundException;
import uz.barakat.market.repository.ShopRepository;

/**
 * Shop CRUD scoped to a single account. Every operation takes the
 * caller's accountId (extracted from the JWT) so a tenant can never
 * touch another tenant's shops.
 */
@Service
@Transactional
public class ShopService {

    private final ShopRepository shops;

    public ShopService(ShopRepository shops) {
        this.shops = shops;
    }

    public record ShopResponse(Long id, String name, boolean main,
                                String address, String contactPhone,
                                String printerName, String cashRegisterNo,
                                String receiptFooter) {
    }

    public record CreateShopRequest(
            @NotBlank(message = "Do'kon nomi kiritilishi shart") String name,
            String address, String contactPhone,
            String printerName, String cashRegisterNo, String receiptFooter) {
    }

    public record UpdateShopRequest(
            @NotBlank(message = "Do'kon nomi kiritilishi shart") String name,
            String address, String contactPhone,
            String printerName, String cashRegisterNo, String receiptFooter) {
    }

    public List<ShopResponse> list(Long accountId) {
        // Phase 2 lazy-bootstrap: an account managed centrally by the
        // License Server may be brand-new to this install — the V13
        // migration only seeded "Asosiy do'kon" for accounts that
        // existed at migration time. Auto-create one on first list so
        // the ShopSwitcher always has a default option and the user
        // doesn't see an empty workspace they can't act on.
        if (accountId != null && shops.countByAccountId(accountId) == 0) {
            Shop bootstrap = new Shop();
            bootstrap.setAccountId(accountId);
            bootstrap.setName("Asosiy do'kon");
            bootstrap.setMain(true);
            shops.save(bootstrap);
        }
        return shops.findByAccountIdOrderByMainDescNameAsc(accountId).stream()
                .map(ShopService::toResponse)
                .toList();
    }

    public ShopResponse create(Long accountId, int maxShops, CreateShopRequest request) {
        if (shops.countByAccountId(accountId) >= maxShops) {
            throw new BadRequestException(
                    "Tarif rejangiz bo'yicha do'konlar chegarasi (" + maxShops + ") to'ldi. "
                            + "Rejani yangilang.");
        }
        Shop s = new Shop();
        s.setAccountId(accountId);
        s.setName(request.name().trim());
        s.setAddress(blankToNull(request.address()));
        s.setContactPhone(blankToNull(request.contactPhone()));
        s.setPrinterName(blankToNull(request.printerName()));
        s.setCashRegisterNo(blankToNull(request.cashRegisterNo()));
        s.setReceiptFooter(blankToNull(request.receiptFooter()));
        // First shop of an account auto-becomes the main shop.
        s.setMain(shops.countByAccountId(accountId) == 0);
        return toResponse(shops.save(s));
    }

    public ShopResponse update(Long accountId, Long id, UpdateShopRequest request) {
        Shop s = requireOwned(accountId, id);
        s.setName(request.name().trim());
        s.setAddress(blankToNull(request.address()));
        s.setContactPhone(blankToNull(request.contactPhone()));
        s.setPrinterName(blankToNull(request.printerName()));
        s.setCashRegisterNo(blankToNull(request.cashRegisterNo()));
        s.setReceiptFooter(blankToNull(request.receiptFooter()));
        return toResponse(shops.save(s));
    }

    public void delete(Long accountId, Long id) {
        Shop s = requireOwned(accountId, id);
        if (s.isMain()) {
            throw new BadRequestException("Asosiy do'konni o'chirib bo'lmaydi");
        }
        shops.delete(s);
    }

    public ShopResponse setMain(Long accountId, Long id) {
        Shop newMain = requireOwned(accountId, id);
        shops.findFirstByAccountIdAndMainTrue(accountId).ifPresent(old -> {
            if (!old.getId().equals(newMain.getId())) {
                old.setMain(false);
                shops.save(old);
            }
        });
        newMain.setMain(true);
        return toResponse(shops.save(newMain));
    }

    private Shop requireOwned(Long accountId, Long id) {
        Shop s = shops.findById(id)
                .orElseThrow(() -> NotFoundException.of("Do'kon", id));
        if (!s.getAccountId().equals(accountId)) {
            throw new BadRequestException("Bu do'kon sizning akkauntingizga tegishli emas");
        }
        return s;
    }

    private static ShopResponse toResponse(Shop s) {
        return new ShopResponse(s.getId(), s.getName(), s.isMain(),
                s.getAddress(), s.getContactPhone(),
                s.getPrinterName(), s.getCashRegisterNo(), s.getReceiptFooter());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
