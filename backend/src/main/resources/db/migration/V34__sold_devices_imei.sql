-- =====================================================================
--  V34: per-unit device (IMEI) tracking for sold electronics / phones.
--
--  products.requires_imei  — owner marks a product as IMEI-tracked, so the POS
--                            prompts for each unit's IMEI/serial at sale time.
--  sold_devices            — one row per physical device handed to a customer:
--                            its IMEI(s)/serial, the sale + customer it went to,
--                            and a status the shop maintains (ACTIVE / BLOCKED /
--                            RETURNED) for its own records + Knox Guard enrolment.
--                            Shop-scoped (shop_id, no FK — matches sales/payments).
-- =====================================================================

ALTER TABLE products ADD COLUMN requires_imei BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE sold_devices (
    id             BIGSERIAL    PRIMARY KEY,
    shop_id        BIGINT       NOT NULL,
    sale_id        BIGINT,
    sale_item_id   BIGINT,
    product_id     BIGINT,
    product_name   VARCHAR(200) NOT NULL,
    imei1          VARCHAR(40),
    imei2          VARCHAR(40),
    serial_number  VARCHAR(80),
    customer_id    BIGINT,
    customer_name  VARCHAR(200),
    payment_method VARCHAR(20),
    sale_price_uzs NUMERIC(15,2),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | BLOCKED | RETURNED
    note           VARCHAR(500),
    sold_at        TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_sold_devices_shop ON sold_devices (shop_id, created_at DESC);
CREATE INDEX idx_sold_devices_imei1 ON sold_devices (imei1);
CREATE INDEX idx_sold_devices_imei2 ON sold_devices (imei2);
CREATE INDEX idx_sold_devices_customer ON sold_devices (customer_id);
