# Deploy

O projeto tem duas pecas deployaveis, e elas nao vao para o mesmo lugar:

| Peca        | Onde roda                                    | Como                                                |
| ----------- | -------------------------------------------- | --------------------------------------------------- |
| `backend/`  | Container (Fly.io, Railway, Render, VPS)     | `Dockerfile` multi-stage                            |
| `frontend/` | Vercel                                       | `next build` detectado automaticamente              |

**A Vercel nao executa a API Spring Boot.** O frontend na Vercel consome a API publicada em outro lugar, via `NEXT_PUBLIC_API_URL`.

```
Navegador
   |
   v
Vercel (Next.js, server components) ---HTTPS---> API Spring Boot (container)
                                                     |
                                                     v
                                            Supabase (Postgres)
```

---

## 1. Publicar a API

### Variaveis de ambiente

```bash
SPRING_PROFILES_ACTIVE=supabase
SUPABASE_DB_URL=jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require
SUPABASE_DB_USER=postgres
SUPABASE_DB_PASSWORD=<senha>
API_PORT=8080
# Origem do frontend na Vercel (domínio exato, sem barra final)
API_CORS_ALLOWED_ORIGINS=https://seu-app.vercel.app
API_PUBLIC_URL=https://api.seu-dominio.com
```

`API_CORS_ALLOWED_ORIGINS` precisa bater com o dominio real da Vercel - sem `https://` e sem barra final.

### Build da imagem

```bash
docker build -t registry.exemplo.com/tradewind-backend:1.0.0 backend/
docker push registry.exemplo.com/tradewind-backend:1.0.0
```

O `Dockerfile` faz build multi-stage: `maven:3.9-eclipse-temurin-17` compila, e a imagem final e `eclipse-temurin:17-jre-alpine` com usuario nao-root e healthcheck em `/actuator/health`.

### Provedores

- **Fly.io** - `fly launch`; conecte o Postgres como *external* e aponte para o Supabase.
- **Railway** - New Project → Deploy from Dockerfile, em `backend/`.
- **Render** - Web Service apontando para `backend/Dockerfile`.
- **VPS** - `docker compose --profile app up -d`.

### Healthcheck

O health check da plataforma deve apontar para `GET /actuator/health`. Ele responde `{"status":"UP"}` e so fica `UP` se o Postgres aceitar consultas, entao detecta banco caiu tanto quanto aplicacao caiu.

---

## 2. Publicar o frontend na Vercel

```bash
npm i -g vercel
cd frontend
vercel link
```

Defina a variavel no projeto da Vercel (**Settings → Environment Variables**):

| Variavel              | Valor                                | Ambiente         |
| --------------------- | ------------------------------------ | ---------------- |
| `NEXT_PUBLIC_API_URL` | `https://api.seu-dominio.com`        | Production       |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8081`              | Development      |

Depois:

```bash
vercel --prod
```

O `NEXT_PUBLIC_*` e **embutido no bundle no build**. Mudar a variavel exige um novo deploy, nao basta reiniciar.

### CORS

Com o frontend na Vercel e a API em outro dominio, o navegador faz requisicao cross-origin. A API so aceita as origens em `API_CORS_ALLOWED_ORIGINS`. Se aparecer erro de CORS no console do navegador, esse e o primeiro lugar a olhar.

### Previews da Vercel

Cada preview gera um dominio proprio (`tradewind-frontend-abc123.vercel.app`). Para a API aceitar os previews, libere o padrão em dev, nunca em producao:

```
API_CORS_ALLOWED_ORIGINS=https://seu-app.vercel.app,https://tradewind-frontend-*.vercel.app
```

O `*` em `allowedOrigins` do Spring so aceita um unico `*`, e ele conflita com `allowCredentials(true)`. O padrão com wildcard no meio **nao** e suportado pelo Spring - use uma lista explicita ou um proxy.

---

## 3. CI/CD

O workflow em [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) faz, a cada push:

1. Testes unitarios (Mockito) e de integracao (Testcontainers) no backend
2. Build do frontend
3. Sobe a API com Postgres, exporta o `openapi.json`
4. Confere se o contrato versionado mudou

Assim o Swagger, a colecao do Postman e o cliente do frontend nao saem de sincronia com a API sem ninguem perceber no code review.

Deploy da Vercel ja e nativo: conectar o repositorio e a propria Vercel dispara o build a cada push na branch de producao.

---

## Checklist de publicacao

- [ ] Migrations aplicadas no banco de destino (`flyway:migrate`)
- [ ] `API_CORS_ALLOWED_ORIGINS` com o dominio final do frontend
- [ ] `NEXT_PUBLIC_API_URL` apontando para a API publica
- [ ] Healthcheck respondendo `UP`
- [ ] `SUPABASE_DB_PASSWORD` e a senha do **banco**, nao a `anon key`
- [ ] HTTPS em todos os hops (a Vercel exige conexao segura)
- [ ] Seed (`V2`) removida se nao for desejada em producao
