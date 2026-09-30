# Imagem do frontend para rodar em qualquer host com Docker: Fly.io, DigitalOcean,
# um VPS ou o proprio Compose. Nada aqui depende de plataforma especifica.
#
# `NEXT_PUBLIC_API_URL` e uma variavel PUBLICA: o valor e embutido no bundle do
# navegador no momento do build. Por isso ela entra como ARG de build, e nao
# como ENV de runtime - mudar a variavel no container nao muda nada no bundle.

# ---- Build ----------------------------------------------------------------
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci

FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ENV NEXT_TELEMETRY_DISABLED=1
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
# Gera .next/standalone, exigido pelo runtime stage abaixo
ENV STANDALONE=1
RUN npm run build

# ---- Runtime --------------------------------------------------------------
FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# usuario nao-root (uid/gid fixos, o mesmo do Dockerfile oficial do Next)
RUN addgroup -g 1001 -S nodejs && adduser -S nextjs -u 1001

# O standalone do Next gera uma arvore minima em .next/standalone
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# O container não traz curl; wget vem do busybox e já está na imagem.
# 127.0.0.1 e não localhost: dentro do container "localhost" resolve primeiro
# para ::1, e o Node não escuta em IPv6 — o health check passaria a falhar
# com "connection refused" mesmo com a aplicação no ar.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD wget -qO- http://127.0.0.1:3000/ || exit 1

CMD ["node", "server.js"]