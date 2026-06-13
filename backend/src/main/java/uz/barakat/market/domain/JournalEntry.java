package uz.barakat.market.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Filter;
import lombok.Getter;
import lombok.Setter;

/**
 * A balanced journal entry ("Jurnal yozuvi"): the header for N
 * {@link JournalLine}s whose debits must equal their credits (in USD).
 *
 * <p>Auto-posted entries carry {@code source} + {@code sourceRef} pointing back
 * at the originating row (sale, expense, ...); the unique constraint on
 * (shop_id, source, source_ref) makes re-posting the same event a no-op.
 */
@Filter(name = "tenantFilter", condition = "shop_id = :shopId")
@Filter(name = "accountFilter", condition = "shop_id IN (:shopIds)")
@Entity
@Table(name = "gl_journal_entry")
@Getter
@Setter
public class JournalEntry extends TenantScopedEntity {

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(length = 500)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private JournalSource source = JournalSource.MANUAL;

    @Column(name = "source_ref", length = 64)
    private String sourceRef;

    @Column(nullable = false)
    private boolean posted = true;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "reversed_entry_id")
    private Long reversedEntryId;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<JournalLine> lines = new ArrayList<>();

    public void addLine(JournalLine line) {
        line.setEntry(this);
        lines.add(line);
    }
}
