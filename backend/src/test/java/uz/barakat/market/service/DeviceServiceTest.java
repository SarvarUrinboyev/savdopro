package uz.barakat.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.barakat.market.domain.Product;
import uz.barakat.market.domain.SoldDevice;
import uz.barakat.market.domain.StockMovement;
import uz.barakat.market.dto.DeviceDtos.DeviceResponse;
import uz.barakat.market.dto.DeviceDtos.DeviceStatusRequest;
import uz.barakat.market.dto.DeviceDtos.IntakeRequest;
import uz.barakat.market.dto.PosDtos.DeviceInput;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.ProductRepository;
import uz.barakat.market.repository.SoldDeviceRepository;
import uz.barakat.market.repository.StockMovementRepository;

/** Unit tests for the device (IMEI) intake, list/filter and status update logic. */
class DeviceServiceTest {

    private SoldDeviceRepository repo;
    private ProductRepository products;
    private StockMovementRepository movements;
    private DeviceService service;

    @BeforeEach
    void setUp() {
        repo = mock(SoldDeviceRepository.class);
        products = mock(ProductRepository.class);
        movements = mock(StockMovementRepository.class);
        service = new DeviceService(repo, products, movements);
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                device(1L, "355745371870412", "Galaxy S22 5G", "Akmal", "QARZGA", "SOLD"),
                device(2L, "860384053135999", "iPhone 15", "Bek", "NAQD", "SOLD"),
                device(3L, "111122223333444", "Redmi Note", "Akmal", "QARZGA", "BLOCKED")));
    }

    @Test
    void listReturnsAllWhenNoFilter() {
        assertEquals(3, service.list(null, null, false).size());
    }

    @Test
    void listFiltersByDebtOnly() {
        List<DeviceResponse> debt = service.list(null, null, true);
        assertEquals(2, debt.size());
        assertTrue(debt.stream().allMatch(d -> "QARZGA".equals(d.paymentMethod())));
    }

    @Test
    void listFiltersByStatus() {
        List<DeviceResponse> blocked = service.list(null, "blocked", false);
        assertEquals(1, blocked.size());
        assertEquals("BLOCKED", blocked.get(0).status());
    }

    @Test
    void listSearchesByImeiAndCustomerAndProduct() {
        assertEquals(1, service.list("355745", null, false).size());      // imei
        assertEquals(2, service.list("akmal", null, false).size());        // customer name
        assertEquals(1, service.list("iphone", null, false).size());       // product name
    }

    @Test
    void updateStatusRejectsUnknownStatus() {
        assertThrows(BadRequestException.class,
                () -> service.updateStatus(1L, new DeviceStatusRequest("FROZEN", null)));
    }

    @Test
    void updateStatusSetsStatusAndNote() {
        SoldDevice d = device(1L, "355745371870412", "Galaxy S22 5G", "Akmal", "QARZGA", "SOLD");
        when(repo.findById(1L)).thenReturn(Optional.of(d));
        when(repo.save(any(SoldDevice.class))).thenAnswer(i -> i.getArgument(0));

        DeviceResponse r = service.updateStatus(1L, new DeviceStatusRequest("blocked", "qarz to'lanmadi"));

        assertEquals("BLOCKED", r.status());
        assertEquals("qarz to'lanmadi", r.note());
    }

    @Test
    void intakeRegistersInStockDevicesAndBumpsStock() {
        Product p = new Product();
        p.setId(50L);
        p.setName("iPhone 15");
        p.setQuantity(2);
        p.setSalePrice(BigDecimal.TEN);
        p.setPurchasePrice(BigDecimal.ONE);
        when(products.findById(50L)).thenReturn(Optional.of(p));
        when(repo.save(any(SoldDevice.class))).thenAnswer(i -> i.getArgument(0));

        IntakeRequest req = new IntakeRequest(50L, List.of(
                new DeviceInput("111111111111111", null, null, null),
                new DeviceInput("222222222222222", null, null, "id@icloud.com"),
                new DeviceInput("", "", "", "")));   // empty row → skipped

        List<DeviceResponse> out = service.intake(req);

        assertEquals(2, out.size());
        assertEquals("IN_STOCK", out.get(0).status());
        assertEquals(4, p.getQuantity());            // 2 existing + 2 registered
        verify(movements).save(any(StockMovement.class));
    }

    @Test
    void exportCsvHasHeaderAndRows() {
        String csv = service.exportCsv(null, null, false);
        assertTrue(csv.startsWith("IMEI1,IMEI2,Serial,"));
        assertTrue(csv.contains("355745371870412"));
        assertEquals(4, csv.strip().split("\n").length); // header + 3 rows
    }

    private static SoldDevice device(Long id, String imei, String product, String customer,
                                     String payment, String status) {
        SoldDevice d = new SoldDevice();
        d.setId(id);
        d.setImei1(imei);
        d.setProductName(product);
        d.setCustomerName(customer);
        d.setPaymentMethod(payment);
        d.setStatus(status);
        return d;
    }
}
