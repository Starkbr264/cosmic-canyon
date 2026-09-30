-- Seed de desenvolvimento. Roda automaticamente em todo banco novo
-- (Docker e Supabase). Em producao, desative com FLYWAY_SEED_ENABLED=false
-- ou limpe a tabela depois do primeiro deploy.

INSERT INTO products (sku, name, description, price, active) VALUES
    ('SKU-001', 'Cafe Torrado 500g', 'Torra media, notas de chocolate', 39.90, TRUE),
    ('SKU-002', 'Cafe Torrado 1kg',  'Torra escura, corpo intenso',    74.50, TRUE),
    ('SKU-003', 'Filtro de Papel 100', 'Cabo de silicone, 100 folhas', 18.00, TRUE),
    ('SKU-004', 'Moedor Manual',       'Aco inox, 38mm',                 129.90, TRUE),
    ('SKU-005', 'Cafe Descafeinado 500g', 'Processo agua, sem cafeina',  41.90, FALSE);
