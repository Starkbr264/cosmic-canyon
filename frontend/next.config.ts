import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,

  // `standalone` gera a arvore minima que o Dockerfile da pasta usa, mas
  // conflita com `next start`. Como so o container precisa dele, o modo fica
  // ligado por variavel de ambiente e o Dockerfile faz `RUN STANDALONE=1`.
  // A build da Vercel e o `npm start` local usam o Next normal.
  ...(process.env.STANDALONE === "1" ? { output: "standalone" as const } : {}),

  // A URL da API vem do ambiente da Vercel (NEXT_PUBLIC_API_URL).
  // Em dev, o fallback local evita build quebrado por variavel ausente.
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081",
  },
};

export default nextConfig;
