package uz.barakat.market.hardware;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.auth.JwtAuthFilter;
import uz.barakat.market.auth.TenantContext;
import uz.barakat.market.domain.Shop;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.ShopRepository;

/**
 * Bridges the receipt / drawer commands {@link EscPosBuilder} produces
 * to whatever Windows printer the shop has wired up. We resolve the
 * printer by the {@code printerName} column we added in V16
 * (Phase 3.3) — if it's null we fall back to the OS default so a
 * fresh single-shop install Just Works without any setup.
 *
 * <h2>Why javax.print</h2>
 * Thermal receipt printers from Xprinter / Star / Epson all expose
 * themselves as standard Windows printers once their driver is
 * installed. We hand the OS a raw {@code AUTOSENSE} byte stream and it
 * forwards it untouched — no parsing, no PostScript conversion, the
 * printer sees the ESC/POS commands exactly as we generated them.
 */
@Service
@Transactional(readOnly = true)
public class PrintService {

    private static final Logger log = LoggerFactory.getLogger(PrintService.class);
    private static final int RECEIPT_WIDTH = 32;   // 58 mm paper @ font A
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final ShopRepository shops;
    private final HttpServletRequest request;

    public PrintService(ShopRepository shops, HttpServletRequest request) {
        this.shops = shops;
        this.request = request;
    }

    // ============================================================ public

    public record ReceiptItem(String name, BigDecimal qty,
                              BigDecimal unitPrice, BigDecimal lineTotal) { }

    public record PrintResult(String printer, int bytesSent) { }

    /** List every printer the OS knows about — fills the shop settings dropdown. */
    public List<String> listPrinters() {
        javax.print.PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        return Arrays.stream(services).map(javax.print.PrintService::getName).toList();
    }

    /**
     * Quick "is this printer responding?" test page. Prints the shop's
     * header info, opens the drawer, cuts. Used by the settings dialog
     * so the operator can confirm wiring without ringing up a fake sale.
     */
    public PrintResult printTestPage() {
        Shop shop = activeShop();
        EscPosBuilder b = new EscPosBuilder().init();
        b.center().bold(true).doubleSize(true)
                .line(shop == null ? "SAVDOPRO" : shop.getName())
                .doubleSize(false).bold(false)
                .line("Sinov sahifasi").newline()
                .left().line("Vaqt: " + LocalDateTime.now().format(STAMP))
                .line("Printer: " + resolvePrinterName(shop))
                .line("Kassa: " + (shop == null || shop.getCashRegisterNo() == null
                        ? "—" : shop.getCashRegisterNo()))
                .newline()
                .center().line("Printer ulanish to'g'ri ishlayapti")
                .openDrawer()
                .cut();
        return sendToPrinter(shop, b.toBytes());
    }

    /** Print a sale receipt. */
    public PrintResult printReceipt(String customerLabel,
                                    String paymentMethod,
                                    List<ReceiptItem> items,
                                    BigDecimal total,
                                    BigDecimal paid,
                                    BigDecimal change) {
        Shop shop = activeShop();
        EscPosBuilder b = new EscPosBuilder().init();

        // --- Header
        b.center().bold(true).doubleSize(true)
                .line(shop == null ? "SAVDOPRO" : shop.getName())
                .doubleSize(false).bold(false);
        if (shop != null && shop.getAddress() != null) b.line(shop.getAddress());
        if (shop != null && shop.getContactPhone() != null) b.line(shop.getContactPhone());
        b.line("Vaqt: " + LocalDateTime.now().format(STAMP));
        if (shop != null && shop.getCashRegisterNo() != null) {
            b.line("Kassa #" + shop.getCashRegisterNo());
        }
        if (customerLabel != null && !customerLabel.isBlank()) {
            b.line("Mijoz: " + customerLabel);
        }

        // --- Items
        b.ruler(RECEIPT_WIDTH);
        for (ReceiptItem it : items) {
            b.line(it.name());
            String left = qtyStr(it.qty()) + " x " + money(it.unitPrice());
            b.twoCol("  " + left, money(it.lineTotal()), RECEIPT_WIDTH);
        }
        b.ruler(RECEIPT_WIDTH);

        // --- Totals
        b.bold(true).doubleSize(true)
                .twoCol("JAMI", money(total), RECEIPT_WIDTH / 2)
                .doubleSize(false).bold(false);
        if (paymentMethod != null) {
            b.line("To'lov: " + paymentMethod);
        }
        if (paid != null) b.twoCol("Berildi", money(paid), RECEIPT_WIDTH);
        if (change != null && change.signum() > 0) {
            b.twoCol("Qaytim", money(change), RECEIPT_WIDTH);
        }

        // --- Footer
        b.newline().center()
                .line("Xaridingiz uchun rahmat!");
        if (shop != null && shop.getReceiptFooter() != null) {
            b.line(shop.getReceiptFooter());
        }
        b.openDrawer().cut();

        return sendToPrinter(shop, b.toBytes());
    }

    /** Standalone cash-drawer kick — for the "no-sale" cash management button. */
    public PrintResult openDrawer() {
        Shop shop = activeShop();
        byte[] bytes = new EscPosBuilder().init().openDrawer().toBytes();
        return sendToPrinter(shop, bytes);
    }

    // ============================================================ helpers

    private Shop activeShop() {
        Long shopId = TenantContext.currentShopId();
        if (shopId == null) {
            // Caller is in consolidated / unscoped mode — fall back to
            // the account's main shop so the printer wiring still works.
            Object accObj = request.getAttribute(JwtAuthFilter.ATTR_ACCOUNT_ID);
            if (accObj instanceof Long accountId) {
                return shops.findFirstByAccountIdAndMainTrue(accountId).orElse(null);
            }
            return null;
        }
        return shops.findById(shopId).orElse(null);
    }

    private static String resolvePrinterName(Shop shop) {
        if (shop != null && shop.getPrinterName() != null
                && !shop.getPrinterName().isBlank()) {
            return shop.getPrinterName();
        }
        return "(OS default)";
    }

    /**
     * Push the raw byte stream at the chosen printer. Resolves the
     * printer by name; if none matches we fall back to the OS default
     * (which is usually what the operator wants on a single-shop laptop).
     */
    private PrintResult sendToPrinter(Shop shop, byte[] bytes) {
        String wanted = shop == null ? null : shop.getPrinterName();
        // Network ESC/POS: printer name "tcp://192.168.1.50" yoki
        // "tcp://host:9100" — xom baytlar JetDirect portiga to'g'ridan-
        // to'g'ri oqadi; Windows drayveri shart emas (LAN termal printer).
        if (wanted != null && wanted.trim().toLowerCase(java.util.Locale.ROOT).startsWith("tcp://")) {
            return sendToNetworkPrinter(wanted.trim(), bytes);
        }
        javax.print.PrintService chosen = findPrinter(wanted);
        if (chosen == null) {
            throw new BadRequestException(
                    "Printer topilmadi: " + (wanted == null ? "OS default" : wanted)
                    + ". Sozlamalardan to'g'ri printer nomini tanlang.");
        }
        try {
            Doc doc = new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
            DocPrintJob job = chosen.createPrintJob();
            job.print(doc, new HashPrintRequestAttributeSet());
            log.info("Printed {} bytes to {}", bytes.length, chosen.getName());
            return new PrintResult(chosen.getName(), bytes.length);
        } catch (Exception ex) {
            log.warn("Print job failed on {}: {}", chosen.getName(), ex.toString());
            throw new BadRequestException(
                    "Chop etishda xatolik: " + ex.getMessage());
        }
    }

    /** Raw socket print to tcp://host[:port] (default 9100 — JetDirect). */
    private PrintResult sendToNetworkPrinter(String url, byte[] bytes) {
        String hostPort = url.substring("tcp://".length());
        String host = hostPort;
        int port = 9100;
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            host = hostPort.substring(0, colon);
            try {
                port = Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException ignored) {
                // "tcp://host:" — port qismi buzuq, 9100 default qoladi
            }
        }
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().flush();
            log.info("Printed {} bytes to network printer {}:{}", bytes.length, host, port);
            return new PrintResult(url, bytes.length);
        } catch (Exception ex) {
            log.warn("Network print failed on {}: {}", url, ex.toString());
            throw new BadRequestException(
                    "Tarmoq printeriga ulanib bo'lmadi (" + url + "): " + ex.getMessage());
        }
    }

    private javax.print.PrintService findPrinter(String wantedName) {
        javax.print.PrintService[] all =
                PrintServiceLookup.lookupPrintServices(null, null);
        if (wantedName != null && !wantedName.isBlank()) {
            for (javax.print.PrintService s : all) {
                if (s.getName().equalsIgnoreCase(wantedName)) return s;
            }
        }
        return PrintServiceLookup.lookupDefaultPrintService();
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0";
        return String.format("%,d", v.setScale(0, java.math.RoundingMode.HALF_UP)
                .toBigInteger()).replace(',', ' ');
    }

    private static String qtyStr(BigDecimal v) {
        if (v == null) return "0";
        // Drop trailing zeros so "2.000" prints as "2" but "1.5" stays "1.5".
        return v.stripTrailingZeros().toPlainString();
    }
}
