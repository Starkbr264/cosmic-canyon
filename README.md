name: tradewind-fullstack

# Tradewind

Sistema de vendas internacional — **projeto acadêmico e fictício**.

> **Pagamento é simulado.** Não há gateway, cartão, chave PIX, conta bancária ou
> qualquer dado financeiro real em lugar algum do código. O endpoint de
> pagamento recebe apenas método e resultado desejado, para percorrer os três
> caminhos do fluxo (aprovado, pendente, recusado).

## O que existe

| Módulo | Endpoints |
| --- | --- |
| Catálogo | `GET/POST/PUT/DELETE /api/v1/products` |
| Checkout | `POST /api/v1/orders`, `GET /api/v1/orders/{id}` |
| Pagamento simulado | `POST /api/v1/orders/{id}/payments` |
| Estoque | `GET /api/v1/inventory`, `POST /api/v1/inventory/{id}/adjust`, `GET /api/v1/inventory/forecast` |
| Clientes | `POST/GET /api/v1/customers`, `GET /{id}/export`, `DELETE /{id}` |
| Relatórios | `GET /api/v1/reports/dashboard`, `GET /api/v1/reports/declined-payments` |

Arquitetura, comparação de ferramentas e plano de deploy: [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

## Stack

| Camada | Tecnologia |
| --- | --- |
| Backend | Java 17, Spring Boot 3.5.3, Spring Data JPA, Bean Validation |
| Banco | PostgreSQL 16 em Docker Compose |
| Migração | Flyway 11 |
| Documentação | springdoc-openapi 2.8.6 (Swagger UI + OpenAPI 3.1) |
| Testes | JUnit 5, Mockito 5, MockMvc, Testcontainers |
| Frontend | Next.js 15 (App Router), rotas `/`, `/checkout`, `/painel` |
| CI | GitHub Actions |

## Pré-requisitos

- Docker Desktop
- JDK 17 — `scripts/setup.ps1` baixa um Temurin 17 em `.tools/`, sem instalar
  no sistema e sem pedir admin
- Node 20+ para o frontend

> O `java -version` da máquina pode apontar para o JDK 11. Isso não é problema:
> o Spring Boot 3 exige 17+, e `setup.ps1` resolve.

## Subir tudo

```powershell
.\scripts\setup.ps1          # JDK 17 local, cria .env, sobe o Postgres
.\scripts\dev.ps1             # banco + API + frontend na JVM/Node locais
```

Ou a stack inteira em containers, por perfil:

```powershell
Copy-Item .env.example .env
docker compose up -d                              # só o Postgres
docker compose --profile app up -d                # Postgres + API + web
docker compose --profile app --profile cache up -d       # + Redis
docker compose --profile app --profile storage up -d     # + object storage (RustFS, S3 API)
docker compose --profile app --profile obs up -d         # + Prometheus + Grafana
docker compose --profile tools up -d                     # + pgAdmin
```

Nada além do Postgres é obrigatório: a API sobe e funciona sem Redis, sem object
storage e sem observabilidade. Os perfis existem para que acrescentar uma peça
seja `docker compose --profile <nome> up -d`, sem editar YAML.

Dev com a JVM e o Node da máquina (mais rápido para iterar):

```powershell
.\scripts\dev.ps1 api
cd frontend; npm install; npm run dev
```

## URLs

As portas vêm do `.env`. Os valores abaixo são os do `.env.example`, escolhidos
para não colidir com outros projetos que já ocupam 5432/8080 na máquina.

| Serviço | URL |
| --- | --- |
| API | http://localhost:8081 |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| Catálogo | http://localhost:3000 |
| Checkout | http://localhost:3000/checkout |
| Painel | http://localhost:3000/painel |
| Postgres (Docker) | localhost:5433 (user `tradewind`) |
| pgAdmin | http://localhost:5051 (admin@tradewind.local) |

## Como o pagamento simulado funciona

```powershell
# 1) cria o pedido (reserva estoque e trava o valor)
$order = Invoke-RestMethod -Method Post http://localhost:8081/api/v1/orders `
  -ContentType 'application/json' -Body (@{
    customerEmail='maria@example.com'; customerName='Maria'; locale='pt-BR'
    countryCode='BR'; currency='BRL'
    items=@(@{ productId='<uuid-do-produto>'; quantity=2 })
  } | ConvertTo-Json -Depth 5)

# 2) escolhe o resultado — aprovado, pendente ou recusado
Invoke-RestMethod -Method Post "http://localhost:8081/api/v1/orders/$($order.id)/payments" `
  -ContentType 'application/json' -Body (@{ method='PIX'; outcome='APPROVED' } | ConvertTo-Json)
```

O pedido vai de `AWAITING_PAYMENT` para `CONFIRMED` (aprovado),
`PAYMENT_PENDING` (pendente) ou `PAYMENT_DECLINED` (recusado). A recusa devolve
a unidade ao estoque disponível; a aprovação consome a reserva.

## Perfis de configuração

O backend tem perfis que apontam para o mesmo schema:

- `local` (default) — Postgres do Docker Compose
- `supabase` — Postgres gerenciado, via variáveis `SUPABASE_DB_*`
- `test` — H2 em memória, sobe rápido para a suíte de testes

```powershell
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

Detalhes em [`docs/SUPABASE.md`](docs/SUPABASE.md) e [`docs/VERCEL.md`](docs/VERCEL.md).

## Decisões que valem explicar

Coisas que só apareceram quando o projeto rodou de verdade:

- **`concat()` em JPQL quebra no Postgres.** `lower(concat('%', :termo, '%'))`
  deixa o bind sem tipo e o Postgres resolve a concatenação como `bytea`
  (`function lower(bytea) does not exist`). O padrão `%termo%` é montado no
  `ProductService`, e o `%`/`_` do usuário são escapados antes.
- **`spring.mvc.problemdetails.enabled` rouba o `@RestControllerAdvice`.** O
  `ProblemDetailsExceptionHandler` do Boot tem precedência alta e devolve
  `title: "Bad Request"` em vez do detalhe por campo. O advice precisa de
  `@Order` explícito para ganhar.
- **`ddl-auto` nunca.** O schema é do Flyway, senão o banco remoto e o local
  divergem em silêncio.
- **Oversell é bloqueado no banco, não no serviço.** A reserva usa
  `SELECT ... FOR UPDATE` em `inventory`; verificar saldo só em Java não segura
  dois checkouts simultâneos.
- **Cada item guarda o `inventory_id` reservado.** Sem isso, a liberação depois
  de um pagamento recusado escolheria "algum estoque com saldo" em vez de
  exatamente a linha que foi baixada.
- **Moeda não é convertida dentro do pedido.** Receita é agrupada por moeda; somar
  BRL com USD produz um número sem significado. Câmbio é serviço externo.
- **`@MockitoBean`, não `@MockBean`**, que está descontinuado no Boot 3.4+.
- **A semente de estoque lista os SKUs, não a tabela.** Um `CROSS JOIN` sobre
  `products` daria 40 unidades a todo produto novo, e o cadastro pareceria
  ter estoque próprio. Saldo é movimentação de entrada, e alguém precisa registrá-la.
- **Ajuste de estoque aceita quantidade negativa.** Com `@Positive`, contar
  divergente, perda e avaria ficavam impossíveis de registrar, e a regra que
  impede saldo negativo virava código morto. Zero é rejeitado pelo serviço, que
  consegue nomear a localização na mensagem.

## Privacidade por desenho

O que o sistema **não** coleta: documento fiscal, CPF/CNPJ, endereço completo,
telefone, dados de cartão, chave PIX ou conta bancária. `marketing_consent` nasce
`false` e só muda por ação explícita. `DELETE /api/v1/customers/{id}` anonimiza em
vez de apagar, porque o pedido é registro contábil — LGPD art. 16 e GDPR art. 17.3.b.
`GET /api/v1/customers/{id}/export` cobre a portabilidade (LGPD art. 18 V, GDPR
art. 20).

## Testes

```powershell
# unitários com Mockito (rápidos, sem container)
.\backend\mvnw.cmd -f backend\pom.xml test

# integração: Postgres real via Testcontainers (precisa de Docker)
.\backend\mvnw.cmd -f backend\pom.xml verify -Pintegration

# integração reaproveitando o Postgres que já está no docker compose
.\backend\mvnw.cmd -f backend\pom.xml verify -Pintegration `
  "-Dit.jdbc.url=jdbc:postgresql://localhost:5433/tradewind" `
  "-Dit.jdbc.user=tradewind" "-Dit.jdbc.password=tradewind_dev"

# frontend
cd frontend; npm run typecheck
```

| Teste | Tipo | Demonstra |
| --- | --- | --- |
| `TradewindApplicationTest` | Contexto | Sobe o app inteiro no H2; pega bean/datasource faltando |
| `ProductServiceTest` | Mockito | CRUD e busca com mock, sem Spring |
| `ProductControllerTest` | Mockito + MockMvc | Controller isolado com `@MockitoBean` |
| `CheckoutServiceTest` | Mockito | Reserva, estoque insuficiente, produto inativo, os três desfechos de pagamento |
| `ProductRepositoryIT` | Testcontainers | JPA + Flyway contra Postgres real |

36 testes unitários + integração.

### Se o Testcontainers não achar o Docker (Windows + Docker Desktop)

Sintoma: `Could not find a valid Docker environment`, com um `BadRequest 400`
contendo um `/info` vazio com o rótulo `com.docker.desktop.address=npipe://\\.\pipe\docker_cli`.

Causa: nesta configuração o JVM do Maven é encaminhado para o named pipe
`docker_cli` (o proxy do CLI, que não serve a API do engine) em vez do
`dockerDesktopLinuxEngine`. Nem `DOCKER_HOST` nem `docker.host` em
`~/.testcontainers.properties` corrigem — o CLI do Docker funciona normalmente
nessa máquina, é só o cliente Java que erra o pipe.

Workaround: use a segunda forma de rodar o IT, apontando para o Postgres do
compose. O CI em Linux não sofre disso.

## Estrutura

```
.
├── backend/                 Spring Boot (API, Flyway, testes, Dockerfile)
├── frontend/                Next.js 15 (catálogo, checkout, painel)
├── infra/postgres/          extensions e init do Postgres do Docker
├── infra/observability/     prometheus.yml e provisioning do Grafana
├── infra/supabase/          sync das migrations para a Supabase CLI
├── docs/                    ARQUITETURA.md, SUPABASE.md, VERCEL.md, postman/
├── scripts/                 setup.ps1, dev.ps1, stop.ps1, env.ps1
├── docker-compose.yml       stack por perfil: postgres, api, web, redis, objectstore, obs
└── .github/workflows/       CI de build, teste e geração de contrato
```
