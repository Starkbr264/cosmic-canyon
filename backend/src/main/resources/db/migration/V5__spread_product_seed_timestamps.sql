-- Separa os timestamps dos produtos do seed.
--
-- V2 insere os cinco produtos em um unico INSERT, entao todos saem com o mesmo
-- created_at. A listagem ordena por created_at DESC, e com empate o Postgres
-- devolve a ordem que o plano de execucao dissolveu: o "produto mais recente"
-- mudava entre requisicoes, e o catalogo podia devolver um produto inativo como
-- primeiro item. Corrigir a ordenacao (desempate por SKU) ja resolve a
-- Instabilidade; isto deixa o dado coerente com o nome da coluna.
--
-- Migration nova, e nao edicao da V2: arquivo ja aplicado tem checksum no
-- Flyway, e mexer nele quebra qualquer banco existente no validate.
--
-- A janela fica no SELECT, e nao no UPDATE: o Postgres nao aceita window
-- function na clausula de atualizacao.

UPDATE products p
   SET created_at = s.created_at
  FROM (
        SELECT id,
               now() - ((5 - (ROW_NUMBER() OVER (ORDER BY sku) - 1)) * INTERVAL '1 minute') AS created_at
          FROM products
         WHERE sku IN ('SKU-001', 'SKU-002', 'SKU-003', 'SKU-004', 'SKU-005')
       ) s
 WHERE p.id = s.id;
