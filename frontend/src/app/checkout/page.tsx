"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  checkoutApi,
  productsApi,
  type Order,
  type Payment,
  type PaymentMethod,
  type Product,
} from "@/lib/api";
import { MARKETS, convertFromBRL, formatMoney, marketByCountry } from "@/lib/money";

interface CartLine {
  productId: string;
  sku: string;
  name: string;
  /** Preço de catálogo em BRL, a moeda de referência do seed. */
  priceBRL: number;
  quantity: number;
}

const STATUS_LABEL: Record<string, string> = {
  AWAITING_PAYMENT: "aguardando pagamento",
  CONFIRMED: "confirmado",
  PAYMENT_PENDING: "pagamento pendente",
  PAYMENT_DECLINED: "pagamento recusado",
  CANCELLED: "cancelado",
};

const METHOD_LABEL: Record<PaymentMethod, string> = {
  PIX: "PIX",
  CARD: "Cartão",
  BANK_TRANSFER: "Transferência bancária",
  DIGITAL_WALLET: "Carteira digital",
};

/**
 * Checkout multi-país.
 *
 * A experiência é a mesma em toda parte; o que muda é o que o `Intl` decide —
 * símbolo da moeda, separador decimal, formato de data. Trocar de mercado
 * reescreve três campos (locale, moeda, país) e nada mais.
 */
export default function CheckoutPage() {
  const [country, setCountry] = useState("BR");
  const [catalog, setCatalog] = useState<Product[]>([]);
  const [lines, setLines] = useState<CartLine[]>([]);
  const [email, setEmail] = useState("");
  const [customerName, setCustomerName] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("PIX");
  const [order, setOrder] = useState<Order | null>(null);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const market = marketByCountry(country);

  useEffect(() => {
    let cancelled = false;
    productsApi
      .list({ active: true, size: 50 })
      .then((page) => {
        if (!cancelled) setCatalog(page.content);
      })
      .catch((err: Error) => {
        if (!cancelled) setError(`Catálogo indisponível: ${err.message}`);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const subtotalBRL = useMemo(
    () => lines.reduce((sum, line) => sum + line.priceBRL * line.quantity, 0),
    [lines]
  );

  const add = useCallback((product: Product) => {
    setLines((current) => {
      const existing = current.find((line) => line.productId === product.id);
      if (existing) {
        return current.map((line) =>
          line.productId === product.id ? { ...line, quantity: line.quantity + 1 } : line
        );
      }
      return [
        ...current,
        {
          productId: product.id,
          sku: product.sku,
          name: product.name,
          priceBRL: product.price,
          quantity: 1,
        },
      ];
    });
  }, []);

  const changeQuantity = (productId: string, quantity: number) =>
    setLines((current) =>
      quantity <= 0
        ? current.filter((line) => line.productId !== productId)
        : current.map((line) =>
            line.productId === productId ? { ...line, quantity } : line
          )
    );

  const price = (valueBRL: number) =>
    formatMoney(convertFromBRL(valueBRL, market.currency), market.currency, market.locale);

  async function placeOrder() {
    setBusy(true);
    setError(null);
    try {
      const created = await checkoutApi.createOrder({
        customerEmail: email.trim(),
        customerName: customerName.trim(),
        locale: market.locale,
        countryCode: market.country,
        currency: market.currency,
        items: lines.map((line) => ({ productId: line.productId, quantity: line.quantity })),
      });
      setOrder(created);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao criar o pedido");
    } finally {
      setBusy(false);
    }
  }

  async function pay(outcome: "APPROVED" | "DECLINED" | "PENDING") {
    if (!order) return;
    setBusy(true);
    setError(null);
    try {
      const result = await checkoutApi.simulatePayment(order.id, { method, outcome });
      setPayment(result);
      setOrder(await checkoutApi.getOrder(order.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao simular o pagamento");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <header>
        <div>
          <h1>Checkout internacional</h1>
          <p className="subtitle">
            Pagamento <strong>simulado</strong> · nenhuma cobrança real é feita
          </p>
        </div>
        <a className="badge" href="/">
          Ver catálogo
        </a>
      </header>

      {error && <div className="error">{error}</div>}

      <section className="panel">
        <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Mercado</h2>
        <div className="filters">
          {MARKETS.map((option) => (
            <button
              key={option.country}
              className={option.country === country ? "" : "ghost"}
              onClick={() => {
                setCountry(option.country);
                setMethod(option.methods[0] as PaymentMethod);
              }}
            >
              {option.label}
            </button>
          ))}
        </div>
        <p className="muted" style={{ marginBottom: 0 }}>
          {market.locale} · {market.currency} ·{" "}
          {market.methods.map((m) => METHOD_LABEL[m as PaymentMethod]).join(", ")}
        </p>
      </section>

      <section className="panel">
        <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Catálogo</h2>
        {catalog.length === 0 ? (
          <p className="muted">Carregando produtos…</p>
        ) : (
          <div className="filters">
            {catalog.map((product) => (
              <button key={product.id} className="ghost" onClick={() => add(product)}>
                + {product.name}
              </button>
            ))}
          </div>
        )}
      </section>

      <section className="panel">
        <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Carrinho</h2>
        {lines.length === 0 ? (
          <p className="muted">Nenhum item selecionado.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Produto</th>
                <th>Qtd</th>
                <th style={{ textAlign: "right" }}>Total</th>
              </tr>
            </thead>
            <tbody>
              {lines.map((line) => (
                <tr key={line.productId}>
                  <td>
                    <span className="sku">{line.sku}</span> {line.name}
                  </td>
                  <td>
                    <input
                      type="number"
                      min={1}
                      style={{ width: 70 }}
                      value={line.quantity}
                      onChange={(e) => changeQuantity(line.productId, Number(e.target.value))}
                      aria-label={`Quantidade de ${line.name}`}
                    />
                  </td>
                  <td className="price" style={{ textAlign: "right" }}>
                    {price(line.priceBRL * line.quantity)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="price" style={{ marginTop: "1rem", marginBottom: 0 }}>
          Subtotal: {price(subtotalBRL)}
        </p>
      </section>

      {!order && (
        <section className="panel">
          <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Comprador</h2>
          <div className="form-grid">
            <div>
              <label htmlFor="name">Nome</label>
              <input
                id="name"
                required
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                placeholder="Maria Oliveira"
              />
            </div>
            <div>
              <label htmlFor="email">E-mail</label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="maria@example.com"
              />
            </div>
            <button
              onClick={placeOrder}
              disabled={busy || lines.length === 0 || !email || !customerName}
            >
              {busy ? "Criando pedido…" : "Confirmar pedido"}
            </button>
          </div>
          <p className="muted" style={{ fontSize: ".85rem", marginBottom: 0 }}>
            O cadastro coleta apenas o necessário para emitir o pedido: nome,
            e-mail, idioma e país. Sem documento, telefone ou endereço completo.
          </p>
        </section>
      )}

      {order && (
        <section className="panel">
          <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Pedido {order.orderNumber}</h2>
          <p>
            Status:{" "}
            <span className={`badge ${order.status === "CONFIRMED" ? "on" : ""}`}>
              {STATUS_LABEL[order.status] ?? order.status}
            </span>
          </p>
          <p className="price">
            Total:{" "}
            {formatMoney(
              convertFromBRL(order.grandTotal, order.currency),
              order.currency,
              order.locale
            )}
          </p>

          {!payment && (
            <>
              <div className="form-grid">
                <div>
                  <label htmlFor="method">Método de pagamento</label>
                  <select
                    id="method"
                    value={method}
                    onChange={(e) => setMethod(e.target.value as PaymentMethod)}
                  >
                    {market.methods.map((option) => (
                      <option key={option} value={option}>
                        {METHOD_LABEL[option as PaymentMethod]}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <p className="muted" style={{ fontSize: ".85rem" }}>
                Escolha o resultado para percorrer os três caminhos do fluxo. O
                sistema não pede número de cartão, chave PIX ou dados bancários.
              </p>
              <div className="filters">
                <button onClick={() => pay("APPROVED")} disabled={busy}>
                  Simular aprovado
                </button>
                <button className="ghost" onClick={() => pay("PENDING")} disabled={busy}>
                  Simular pendente
                </button>
                <button className="danger" onClick={() => pay("DECLINED")} disabled={busy}>
                  Simular recusado
                </button>
              </div>
            </>
          )}

          {payment && (
            <p>
              Pagamento <strong>{payment.status}</strong> · referência{" "}
              <span className="sku">{payment.simulationReference}</span>
            </p>
          )}
        </section>
      )}

      <footer>
        <span>
          API: <a href="/api-docs">OpenAPI</a>
        </span>
        <span>
          Docs:{" "}
          <a href={`${process.env.NEXT_PUBLIC_API_URL ?? ""}/swagger-ui.html`}>
            Swagger UI
          </a>
        </span>
      </footer>
    </main>
  );
}
