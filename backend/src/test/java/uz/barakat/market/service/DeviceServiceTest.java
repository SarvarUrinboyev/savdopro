package uz.barakat.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.barakat.market.domain.SoldDevice;
import uz.barakat.market.dto.DeviceDtos.DeviceResponse;
import uz.barakat.market.dto.DeviceDtos.DeviceStatusRequest;
import uz.barakat.market.exception.BadRequestException;
import uz.barakat.market.repository.SoldDeviceRepository;

/** Unit tests for the sold-device (IMEI) list/filter + status update logic. */
class DeviceServiceTest {

    private SoldDeviceRepository repo;
    private DeviceService service;

    @BeforeEach
    void setUp() {
        repo = mock(SoldDeviceRepository.class);
        service = new DeviceService(repo);
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                device(1L, "355745371870412", "Galaxy S22 5G", "Akmal", "QARZGA", "ACTIVE"),
                device(2L, "860384053135999", "iPhone 15", "Bek", "NAQD", "ACTIVE"),
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
        SoldDevice d = device(1L, "355745371870412", "Galaxy S22 5G", "Akmal", "QARZGA", "ACTIVE");
        when(repo.findById(1L)).thenReturn(Optional.of(d));
        when(repo.save(any(SoldDevice.class))).thenAnswer(i -> i.getArgument(0));

        DeviceResponse r = service.updateStatus(1L, new DeviceStatusRequest("blocked", "qarz to'lanmadi"));

        assertEquals("BLOCKED", r.status());
        assertEquals("qarz to'lanmadi", r.note());
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
