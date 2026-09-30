"use client";

import { useCallback, useEffect, useState } from "react";
import {
  API_URL,
  ApiError,
  formatBRL,
  productsApi,
  type Product,
} from "@/lib/api";

export default function Home() {
  const [products, setProducts] = useState<Product[]>([]);
  const [term, setTerm] = useState("");
  const [onlyActive, setOnlyActive] = useState(false);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  // Dois canais separados: erro e recusa de regra de negocio. Misturar os dois
  // fazia o 409 parecer defeito.
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [form, setForm] = useState({
    sku: "",
    name: "",
    price: "",
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await productsApi.list({
        term: term || undefined,
        active: onlyActive ? true : undefined,
      });
      setProducts(page.content);
      setTotal(page.totalElements);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Nao foi possivel carregar os produtos"
      );
    } finally {
      setLoading(false);
    }
  }, [term, onlyActive]);

  useEffect(() => {
    void load();
  }, [load]);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await productsApi.create({
        sku: form.sku.trim(),
        name: form.name.trim(),
        price: Number(form.price),
        description: null,
      });
      setForm({ sku: "", name: "", price: "" });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao criar produto");
    } finally {
      setSaving(false);
    }
  }

  async function remove(id: string) {
    setError(null);
    setNotice(null);
    try {
      await productsApi.remove(id);
      await load();
    } catch (err) {
      // 409 e a resposta esperada quando o produto tem estoque ou pedidos. A
      // mensagem da API ja diz o que fazer, entao o erro nao e tratado como
      // falha generica.
      if (err instanceof ApiError && err.status === 409) {
        setNotice(err.message);
      } else {
        setError(err instanceof Error ? err.message : "Falha ao remover");
      }
    }
  }

  // Desativar e o caminho que a API sugere no 409. Vai por PUT porque nao ha
  // rota de patch: o payload do PUT exige todos os campos, e o produto da tela
  // ja tem todos.
  async function toggleActive(p: Product) {
    setError(null);
    setNotice(null);
    try {
      await productsApi.update(p.id, {
        sku: p.sku,
        name: p.name,
        description: p.description,
        price: p.price,
        active: !p.active,
      });
      await load();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Falha ao alterar a situacao",
      );
    }
  }

  return (
    <main>
      <header>
        <div>
          <h1>Catalogo de Produtos</h1>
          <p className="subtitle">
            Next.js self-hosted &middot; Spring Boot + Postgres &middot;{" "}
            <code>{API_URL}</code>
          </p>
        </div>
        <nav className="filters">
          <a className="badge" href="/checkout">
            Checkout
          </a>
          <a className="badge" href="/painel">
            Painel
          </a>
          <a className="badge" href="/api-docs" target="_blank" rel="noreferrer">
            OpenAPI
          </a>
        </nav>
      </header>

      {error && <div className="error">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      <section className="panel">
        <div className="filters">
          <input
            placeholder="Buscar por nome ou sku..."
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            style={{ flex: 1, minWidth: 200 }}
          />
          <label
            style={{ display: "flex", alignItems: "center", gap: ".4rem", margin: 0 }}
          >
            <input
              type="checkbox"
              checked={onlyActive}
              onChange={(e) => setOnlyActive(e.target.checked)}
              style={{ width: 16, height: 16 }}
            />
            Somente ativos
          </label>
          <button className="ghost" onClick={load} disabled={loading}>
            {loading ? "Carregando..." : "Atualizar"}
          </button>
        </div>
      </section>

      <section className="panel">
        <table>
          <thead>
            <tr>
              <th>SKU</th>
              <th>Produto</th>
              <th>Preco</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {products.length === 0 && !loading && (
              <tr>
                <td colSpan={5} className="muted">
                  Nenhum produto encontrado.
                </td>
              </tr>
            )}
            {products.map((p) => (
              <tr key={p.id}>
                <td className="sku">{p.sku}</td>
                <td>
                  {p.name}
                  {p.description && (
                    <div className="muted" style={{ fontSize: ".82rem" }}>
                      {p.description}
                    </div>
                  )}
                </td>
                <td className="price">{formatBRL(p.price)}</td>
                <td>
                  <span className={`badge ${p.active ? "on" : ""}`}>
                    {p.active ? "ativo" : "inativo"}
                  </span>
                </td>
                <td style={{ textAlign: "right" }}>
                  <div className="row-actions">
                    <button
                      className="ghost"
                      onClick={() => toggleActive(p)}
                    >
                      {p.active ? "Desativar" : "Ativar"}
                    </button>
                    <button className="danger" onClick={() => remove(p.id)}>
                      Remover
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="muted" style={{ marginBottom: 0, fontSize: ".85rem" }}>
          {total} produto(s) no total
        </p>
      </section>

      <section className="panel">
        <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Novo produto</h2>
        <form className="form-grid" onSubmit={create}>
          <div>
            <label htmlFor="sku">SKU</label>
            <input
              id="sku"
              required
              value={form.sku}
              onChange={(e) => setForm({ ...form, sku: e.target.value })}
              placeholder="SKU-006"
            />
          </div>
          <div>
            <label htmlFor="name">Nome</label>
            <input
              id="name"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Cafe especial 250g"
            />
          </div>
          <div>
            <label htmlFor="price">Preco</label>
            <input
              id="price"
              required
              type="number"
              step="0.01"
              min="0"
              value={form.price}
              onChange={(e) => setForm({ ...form, price: e.target.value })}
              placeholder="49.90"
            />
          </div>
          <button type="submit" disabled={saving}>
            {saving ? "Salvando..." : "Criar"}
          </button>
        </form>
      </section>

      <footer>
        <span>API: {API_URL}</span>
        <span>
          Docs: <a href={`${API_URL}/swagger-ui.html`}>Swagger UI</a>
        </span>
      </footer>
    </main>
  );
}
