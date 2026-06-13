-- =====================================================================
--  V28: Double-entry accounting core (Bosh kitob / General Ledger)
--
--  Until now finance was a flat "kirim/chiqim" journal (payments) plus
--  on-the-fly P&L computed from stock movements (Menejment page). V28 adds
--  a proper double-entry ledger that the strong-accounting features build on:
--
--    • gl_account        — Chart of Accounts (Hisoblar rejasi), hierarchical
--    • gl_journal_entry   — a balanced journal entry header (∑debit = ∑credit)
--    • gl_journal_line    — one debit-or-credit posting line, valued in USD
--    • gl_period          — accounting periods that can be CLOSED (locked)
--
--  Design notes:
--    - USD is the canonical ledger unit (matches MoneyConverter + the
--      Menejment P&L). Each line also keeps the ORIGINAL currency + amount
--      so nothing is lost and the UI can show either.
--    - Everything is shop-scoped (shop_id) like the rest of the schema.
--      No FK on shop_id (matches sales/payments) — the app enforces tenancy.
--    - (shop_id, source, source_ref) is UNIQUE so auto-posting from a sale /
--      expense / payment is IDEMPOTENT: replaying an event never double-posts.
--      source_ref is NULL for manual entries; multiple NULLs are allowed
--      (standard SQL NULL-distinct semantics on both PostgreSQL and H2).
--    - The standard chart of accounts is seeded per-shop in application code
--      (ChartOfAccountsService.ensureSeeded) — shops are created dynamically,
--      so a migration cannot enumerate them.
-- =====================================================================

-- Chart of Accounts: the named buckets every posting lands in.
CREATE TABLE gl_account (
    id             BIGSERIAL PRIMARY KEY,
    shop_id        BIGINT       NOT NULL,
    -- Stable numeric code, e.g. '1100' (Kassa), '4100' (Savdo tushumi).
    code           VARCHAR(20)  NOT NULL,
    name           VARCHAR(180) NOT NULL,
    -- ASSET | LIABILITY | EQUITY | REVENUE | EXPENSE
    type           VARCHAR(12)  NOT NULL,
    -- DEBIT | CREDIT — the side that increases this account's balance.
    normal_balance VARCHAR(6)   NOT NULL,
    -- Optional parent for a tree view (rolls children up under a heading).
    parent_id      BIGINT REFERENCES gl_account(id) ON DELETE SET NULL,
    -- System accounts are seeded + targeted by auto-posting; cannot be deleted.
    is_system      BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    description    VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_gl_account_shop_code UNIQUE (shop_id, code)
);
CREATE INDEX idx_gl_account_shop ON gl_account (shop_id);
CREATE INDEX idx_gl_account_type ON gl_account (shop_id, type);

-- Journal entry header — one balanced transaction.
CREATE TABLE gl_journal_entry (
    id                BIGSERIAL PRIMARY KEY,
    shop_id           BIGINT       NOT NULL,
    entry_date        DATE         NOT NULL,
    memo              VARCHAR(500),
    -- MANUAL | SALE | SALE_REFUND | STOCK_IN | STOCK_WRITEOFF |
    -- EXPENSE | HOME_EXPENSE | MANAGEMENT_COST | PAYMENT | OPENING_BALANCE
    source            VARCHAR(24)  NOT NULL DEFAULT 'MANUAL',
    -- Back-reference into the originating row (sale id, expense id, ...).
    -- NULL for manual entries. Drives idempotent auto-posting.
    source_ref        VARCHAR(64),
    posted            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by        VARCHAR(120),
    -- When this entry reverses another (storno), points at the original.
    reversed_entry_id BIGINT REFERENCES gl_journal_entry(id) ON DELETE SET NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_gl_entry_source UNIQUE (shop_id, source, source_ref)
);
CREATE INDEX idx_gl_entry_shop_date ON gl_journal_entry (shop_id, entry_date);
CREATE INDEX idx_gl_entry_source    ON gl_journal_entry (source, source_ref);

-- Journal line — a single debit OR credit against one account, in USD.
CREATE TABLE gl_journal_line (
    id          BIGSERIAL PRIMARY KEY,
    shop_id     BIGINT        NOT NULL,
    entry_id    BIGINT        NOT NULL REFERENCES gl_journal_entry(id) ON DELETE CASCADE,
    account_id  BIGINT        NOT NULL REFERENCES gl_account(id),
    -- Exactly one of debit / credit is > 0 (canonical USD).
    debit       NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit      NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- The currency + amount as originally entered (USD by default), kept so
    -- the UI can show the source figure without a lossy back-conversion.
    currency    VARCHAR(3)    NOT NULL DEFAULT 'USD',
    orig_amount NUMERIC(15,2),
    memo        VARCHAR(300),
    created_at  TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_gl_line_entry   ON gl_journal_line (entry_id);
CREATE INDEX idx_gl_line_account ON gl_journal_line (account_id);
CREATE INDEX idx_gl_line_shop    ON gl_journal_line (shop_id);

-- Accounting period — close (lock) a finished month so its entries freeze.
CREATE TABLE gl_period (
    id           BIGSERIAL PRIMARY KEY,
    shop_id      BIGINT      NOT NULL,
    period_start DATE        NOT NULL,
    period_end   DATE        NOT NULL,
    -- OPEN | CLOSED
    status       VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    closed_at    TIMESTAMP,
    closed_by    VARCHAR(120),
    note         VARCHAR(500),
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_gl_period_shop_range UNIQUE (shop_id, period_start, period_end)
);
CREATE INDEX idx_gl_period_shop ON gl_period (shop_id, period_start);
