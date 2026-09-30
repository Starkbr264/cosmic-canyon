-- Núcleo transacional para o demonstrador acadêmico de vendas internacionais.
-- Valores monetários são armazenados com currency ISO-4217; não há dados de cartão
-- ou credenciais de pagamento nesta aplicação.

CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    locale VARCHAR(35) NOT NULL DEFAULT 'pt-BR',
    country_code CHAR(2) NOT NULL DEFAULT 'BR',
    marketing_consent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    country_code CHAR(2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES inventory_locations(id),
    available_quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    reorder_point INTEGER NOT NULL DEFAULT 5,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_product_location UNIQUE(product_id, location_id),
    CONSTRAINT ck_inventory_available_nonnegative CHECK (available_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_nonnegative CHECK (reserved_quantity >= 0)
);

CREATE TABLE sales_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id UUID REFERENCES customers(id),
    status VARCHAR(24) NOT NULL,
    currency CHAR(3) NOT NULL,
    subtotal NUMERIC(14,2) NOT NULL,
    tax_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    grand_total NUMERIC(14,2) NOT NULL,
    country_code CHAR(2) NOT NULL,
    locale VARCHAR(35) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_order_totals CHECK (subtotal >= 0 AND tax_total >= 0 AND grand_total >= 0)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES sales_orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    -- Local fisico reservado: garante que baixa e movimentacao caiam na mesma linha.
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(14,2) NOT NULL,
    line_total NUMERIC(14,2) NOT NULL,
    CONSTRAINT ck_order_item_quantity CHECK(quantity > 0),
    CONSTRAINT ck_order_item_prices CHECK(unit_price >= 0 AND line_total >= 0)
);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES sales_orders(id),
    method VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    simulation_reference VARCHAR(64) NOT NULL UNIQUE,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_amount CHECK(amount >= 0)
);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    order_id UUID REFERENCES sales_orders(id),
    movement_type VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_inventory_movement_quantity CHECK(quantity <> 0)
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    actor_id VARCHAR(120),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_inventory_product ON inventory(product_id);
CREATE INDEX idx_orders_customer_created ON sales_orders(customer_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON sales_orders(status, created_at DESC);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_payments_order_processed ON payments(order_id, processed_at DESC);
CREATE INDEX idx_movements_inventory_created ON inventory_movements(inventory_id, created_at DESC);
CREATE INDEX idx_audit_aggregate ON audit_events(aggregate_type, aggregate_id, occurred_at DESC);
