package uz.barakat.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import uz.barakat.market.domain.PurchaseOrderStatus;

/** Records for the supplier purchase-order / receiving / costing module. */
public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    // ----------------------------------------------------------- create/update

    public record LineRequest(Long productId, String productName,
                              Integer orderedQty, BigDecimal unitCostUsd, String note) {
    }

    public record PoRequest(Long supplierId, String supplierName,
                            LocalDate orderDate, LocalDate expectedDate,
                            String invoiceNumber, LocalDate invoiceDate, String note,
                            List<LineRequest> lines) {
    }

    // --------------------------------------------------------------- receiving

    public record ReceiveLine(Long lineId, Integer qty, BigDecimal unitCostUsd) {
    }

    public record ReceiveRequest(LocalDate receiptDate, String invoiceNumber,
                                 List<ReceiveLine> lines) {
    }

    // ---------------------------------------------------------------- responses

    public record LineResponse(Long id, Long productId, String productName,
                               int orderedQty, int receivedQty, int remainingQty,
                               BigDecimal unitCostUsd, BigDecimal lineTotalUsd) {
    }

    public record PoResponse(Long id, Long supplierId, String supplierName,
                             PurchaseOrderStatus status, LocalDate orderDate, LocalDate expectedDate,
                             String invoiceNumber, LocalDate invoiceDate, String note,
                             BigDecimal orderedTotalUsd, BigDecimal receivedTotalUsd,
                             LocalDateTime createdAt, List<LineResponse> lines) {
    }

    // ------------------------------------------------------- cost history + valuation

    public record LotRow(Long id, LocalDate receiptDate, String supplierName,
                         int qty, BigDecimal unitCostUsd, String invoiceNumber, Long poId) {
    }

    public record CostHistory(Long productId, String productName,
                              BigDecimal currentWacUsd, int currentQty, List<LotRow> lots) {
    }

    public record ValuationRow(Long productId, String productName, int qty,
                               BigDecimal wacUsd, BigDecimal wacValueUsd, BigDecimal fifoValueUsd) {
    }

    public record ValuationReport(List<ValuationRow> rows,
                                  BigDecimal wacTotalUsd, BigDecimal fifoTotalUsd) {
    }
}
