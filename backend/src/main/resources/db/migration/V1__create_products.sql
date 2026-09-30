-- Schema do catalogo. Fonte unica de verdade: o mesmo arquivo roda no
-- Postgres do Docker e no Supabase, entao nada aqui pode usar sintaxe
-- exclusiva de um dos dois.

CREATE TABLE products (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    sku         VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    price       NUMERIC(12, 2) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_price CHECK (price >= 0)
);

-- Filtro mais usado (lista paginada de ativos): indice composto parcial.
CREATE INDEX idx_products_active_created_at ON products (created_at DESC) WHERE active = TRUE;

-- Busca textual por nome/sku usada pelo parametro `term`.
CREATE INDEX idx_products_name_lower ON products (lower(name));

COMMENT ON TABLE products IS 'Catalogo de produtos';
COMMENT ON COLUMN products.price IS 'Preco unitario em BRL';
