package uz.barakat.market.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.barakat.market.domain.AuditEntry;
import uz.barakat.market.dto.AuditResponse;
import uz.barakat.market.repository.AuditEntryRepository;

/** Records and reads the local data-mutation audit trail. */
@Service
public class AuditService {

    private final AuditEntryRepository repo;
    private final MeterRegistry metrics;

    public AuditService(AuditEntryRepository repo, MeterRegistry metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    /**
     * Writes one audit row in its own transaction so it is independent of the
     * (already-finished) request transaction. Called best-effort from
     * {@code AuditInterceptor}, which swallows any failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long shopId, String actor, String method, String path,
                       int status, String clientIp) {
        AuditEntry e = new AuditEntry();
        e.setShopId(shopId);
        e.setActor(actor);
        e.setMethod(method);
        e.setPath(path != null && path.length() > 300 ? path.substring(0, 300) : path);
        e.setStatus(status);
        e.setClientIp(clientIp);
        repo.save(e);
        metrics.counter("audit.events").increment();
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> recent(LocalDate from, LocalDate to, int limit) {
        int size = Math.min(Math.max(limit, 1), 1000);
        return repo.findByCreatedAtBetweenOrderByIdDesc(
                        from.atStartOfDay(), to.plusDays(1).atStartOfDay(), PageRequest.of(0, size))
                .stream()
                .map(e -> new AuditResponse(e.getId(), e.getActor(), e.getMethod(), e.getPath(),
                        e.getStatus(), e.getClientIp(), e.getCreatedAt()))
                .toList();
    }
}
