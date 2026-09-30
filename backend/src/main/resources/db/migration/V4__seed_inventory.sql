-- Estoque inicial exclusivamente para o ambiente demonstrativo.
INSERT INTO inventory_locations (code, name, country_code) VALUES
    ('BR-SP-01', 'Centro de distribuicao Sao Paulo', 'BR'),
    ('US-MIA-01', 'Fulfillment Miami', 'US');

-- A lista de SKU e explicita de proposito. Um CROSS JOIN sobre a tabela
-- products daria saldo a todo produto novo, o que faz o cadastro parecer
-- magico: o produto entra no catalogo e ja aparece com 40 unidades em cada
-- local. Saldo e uma movimentacao de entrada que alguem precisa registrar.
INSERT INTO inventory (product_id, location_id, available_quantity, reorder_point)
SELECT p.id, l.id, 40, 8
FROM products p
JOIN (VALUES ('SKU-001'), ('SKU-002'), ('SKU-003'), ('SKU-004'), ('SKU-005')) AS s(sku) ON s.sku = p.sku
CROSS JOIN inventory_locations l;

-- A movement_type 'RESERVATION' e 'RELEASE' sao gravadas pelo servico de
-- checkout; o seed acima cria saldo sem movimentacao porque e carga inicial,
-- nao uma operacao do dia a dia.