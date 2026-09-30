-- Executado apenas na primeira criação do volume (docker-entrypoint-initdb.d).
-- O schema de verdade é gerenciado pelo Flyway (backend/src/main/resources/db/migration),
-- então aqui ficam só as convenções que o Supabase também espera existir.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

-- Garante que a coluna de UUID do app possa ser gerada no próprio banco,
-- igual ao que fazemos com default gen_random_uuid() no schema do Supabase.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'public') THEN
    CREATE SCHEMA public;
  END IF;
END
$$;

ALTER SCHEMA public OWNER TO CURRENT_USER;
GRANT ALL ON SCHEMA public TO CURRENT_USER;
