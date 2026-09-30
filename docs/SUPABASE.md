# Supabase

O mesmo codigo e o mesmo schema rodam no Postgres do Docker (dev) e no Supabase (cloud). A unica coisa que muda e o perfil ativo e as credenciais.

## Como o projeto se conecta

O schema **nao** e gerado pelo Hibernate. Quem controla o banco e o Flyway, em `backend/src/main/resources/db/migration`:

| Vercao | Arquivo                                   | O que faz                       |
| ------ | ----------------------------------------- | ------------------------------- |
| V1     | `V1__create_products.sql`                 | Tabela, constraints e indices   |
| V2     | `V2__seed_products.sql`                   | Dados de exemplo                |

Isso garante que o banco local e o do Supabase nao divirjam: nao existe `ddl-auto` para baguncar o schema em um dos dois lados.

## 1. Criar o projeto

1. Crie o projeto em <https://supabase.com/dashboard> (ou `supabase projects create tradewind`).
2. Guarde os dados de conexao em **Project Settings → Database**.

## 2. Descobrir a senha do banco

O Supabase **nao mostra a senha depois de criada**. Se voce ainda nao tem:

- Dashboard → **Project Settings → Database** → *Reset database password*
- Ou pela CLI: `supabase db reset` (ambiente local da CLI)

> A `anon key` do dashboard **nao** e a senha do banco. Misturar as duas falha na autenticacao do driver JDBC.

## 3. Configurar a API

Connection string em **Project Settings → Database → Connection string**, no modo **Session** (porta `5432`, nao a pooler `6543`):

```powershell
$env:SPRING_PROFILES_ACTIVE="supabase"
$env:SUPABASE_DB_URL="jdbc:postgresql://db.<seu-ref>.supabase.co:5432/postgres?sslmode=require"
$env:SUPABASE_DB_USER="postgres"
$env:SUPABASE_DB_PASSWORD="<senha-do-banco>"

.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

Verifique:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8081/api/v1/products
```

Se a API subiu, o Flyway aplicou as migrations no Supabase. Confira em **Database → Tables** ou:

```sql
select installed_rank, version, description, success
from flyway_schema_history
order by installed_rank;
```

### Variaveis do perfil `supabase`

| Variavel             | Obrigatoria | Origem                                  |
| -------------------- | ----------- | --------------------------------------- |
| `SUPABASE_DB_URL`    | sim         | Connection string (modo Session)        |
| `SUPABASE_DB_USER`   | nao         | PadrÃ£o: `postgres`                      |
| `SUPABASE_DB_PASSWORD` | sim       | Senha do banco                          |
| `SUPABASE_URL`       | nao         | Project URL (so para o client PostgREST)|
| `SUPABASE_ANON_KEY`  | nao         | Project API → anon public key           |
| `SUPABASE_REST_ENABLED` | nao      | `true` liga o `SupabaseRestClient`      |

## 4. Cliente PostgREST (opcional)

`SupabaseRestClient` le a tabela direto pela API REST do Supabase, sem passar pela aplicacao. Util para relatorios que precisam do dado em tempo real.

```powershell
$env:SUPABASE_REST_ENABLED="true"
$env:SUPABASE_URL="https://<seu-ref>.supabase.co"
$env:SUPABASE_ANON_KEY="<anon key>"
```

Com o perfil `supabase` ativo e essas variaveis setadas, o bean sobe. Sem elas, o perfil `local` nem tenta instancia-lo (`@ConditionalOnProperty`).

## 5. Migracoes

- O app roda as migrations no startup. Isso e intencional em dev, mas em producao o ideal e rodar em um passo separado:

```powershell
# aplica migrations e sai (sem deixar a API no ar)
.\backend\mvnw.cmd -f backend\pom.xml flyway:migrate `
  -Dflyway.url="$env:SUPABASE_DB_URL" `
  -Dflyway.user="postgres" `
  -Dflyway.password="$env:SUPABASE_DB_PASSWORD"
```

- Para inspecionar o estado sem alterar nada: `flyway:info`.
- Para marcar um banco existente como baseline: `flyway:baseline`.

## 6. RLS (Row Level Security)

O Supabase liga RLS por padrÃ£o nas tabelas criadas pelo dashboard. A tabela criada pelo nosso Flyway **nao** fica com RLS ativo, entao:

- A API (que conecta como `postgres`) enxerga tudo normalmente.
- Se voce habilitar RLS para expor a tabela via PostgREST, o frontend so vera as linhas permitidas - o que muda o comportamento da listagem.

Decida isso explicitamente antes de publicar:

```sql
-- deixar bloqueado (padrÃ£o seguro)
alter table products enable row level security;

-- leitura publica (so faz sentido para catalogo)
alter table products enable row level security;
create policy "leitura publica do catalogo"
  on products for select
  using (active = true);
```

## Armadilhas conhecidas

| Sintoma                                              | Causa                                                      |
| ---------------------------------------------------- | ---------------------------------------------------------- |
| `password authentication failed for user "postgres"` | Voce passou a `anon key`/`service_role key` no lugar da senha |
| `SSL is not enabled on the server`                   | Faltou `?sslmode=require` na URL                           |
| `too many connections`                               | Plano com pool pequeno; reduza `maximum-pool-size` (hoje 10) |
| `relation "products" does not exist`                 | Flyway nao rodou; confira `flyway_schema_history`           |
| Nada funciona na porta 6543                          | A pooler (transaction mode) nao aceita_prepared statements; use a porta 5432 |

## Supabase CLI (opcional)

Se quiser o Supabase CLI configurando o ambiente local com os mesmos arquivos:

```powershell
npm i -g supabase
supabase init
supabase start
supabase db reset      # aplica as migrations de supabase/migrations/
```

Copie os arquivos de `backend/src/main/resources/db/migration` para
`supabase/migrations/` mantendo o prefixo de versao, ou use
[`infra/supabase/sync-migrations.ps1`](sync-migrations.ps1).
