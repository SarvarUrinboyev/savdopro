package uz.barakat.market.controller;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.barakat.market.dto.BarcodeLookupResponse;
import uz.barakat.market.dto.ProductImportResult;
import uz.barakat.market.dto.ProductRequest;
import uz.barakat.market.dto.ProductResponse;
import uz.barakat.market.dto.ScanRequest;
import uz.barakat.market.dto.ScanResponse;
import uz.barakat.market.dto.StockAdjustRequest;
import uz.barakat.market.dto.StockMovementResponse;
import uz.barakat.market.service.BarcodeLookupService;
import uz.barakat.market.service.ProductService;

/** REST API for the warehouse / inventory. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;
    private final BarcodeLookupService barcodeLookupService;

    public ProductController(ProductService service, BarcodeLookupService barcodeLookupService) {
        this.service = service;
        this.barcodeLookupService = barcodeLookupService;
    }

    /** Product list, optionally filtered by search text, category and status. */
    @GetMapping
    public List<ProductResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status) {
        return service.list(search, categoryId, status);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /**
     * Products whose current quantity has dropped to or below their
     * configured low-stock threshold. Products with {@code threshold = 0}
     * (no threshold set) are excluded. Used by the dashboard widget
     * and by the Telegram alerter; returns the same shape as {@code list}
     * so the frontend can re-use existing renderers.
     */
    @GetMapping("/low-stock")
    public List<ProductResponse> lowStock() {
        return service.lowStock();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** Stock in / out ("Kirim" / "Chiqim") with a reason. */
    @PatchMapping("/{id}/adjust")
    public ProductResponse adjust(@PathVariable Long id,
                                  @Valid @RequestBody StockAdjustRequest request) {
        return service.adjustStock(id, request);
    }

    @GetMapping("/{id}/movements")
    public List<StockMovementResponse> movements(@PathVariable Long id) {
        return service.movements(id);
    }

    /** Bulk stock count ("Inventarizatsiya"): set counted quantities, log corrections. */
    @PostMapping("/stocktake")
    public List<ProductResponse> stocktake(
            @RequestBody uz.barakat.market.dto.StocktakeRequest request) {
        return service.stocktake(request);
    }

    /**
     * Barcode scan: resolves the code against the warehouse (canonical GTIN) and
     * reports found / not-found. It does NOT touch stock or call any external
     * service — for an unknown code the browser queries the national catalogue
     * itself (the hosted backend can't reach Uzbek gov endpoints).
     */
    @PostMapping("/scan")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request) {
        return service.resolveScan(request.barcode());
    }

    /**
     * Global barcode lookup — a read-only suggestion for an UNKNOWN code. The
     * scan modal calls this as a fallback when the national catalogue has
     * nothing: it queries external product databases (Open Food Facts, then UPC
     * Item DB) and returns a name + suggested category, or {@code found:false}.
     * It creates nothing and never throws — a slow/unreachable API degrades to
     * {@code found:false} so the cashier can type the details by hand.
     */
    @GetMapping("/barcode-lookup")
    public BarcodeLookupResponse barcodeLookup(@RequestParam String code) {
        return barcodeLookupService.lookup(code);
    }

    /** Bulk import of products from an uploaded CSV / XLSX file. */
    @PostMapping("/import")
    public ProductImportResult importProducts(@RequestParam("file") MultipartFile file) {
        return service.importProducts(file);
    }

    /** Downloadable CSV template for the bulk import. */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        String csv = "Nomi,Shtrix kod,IMEI 1,IMEI 2,Kelish narxi,Sotilish narxi,Miqdor,Toifa\n"
                + "iPhone 15 Pro,4780000000017,353915110000001,353915110000002,1100,1350,5,Smartfonlar\n"
                + "Type-C kabel 1m,4780000000024,,,2,5,40,Aksessuarlar\n";
        byte[] body = ('﻿' + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ombor-shablon.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
