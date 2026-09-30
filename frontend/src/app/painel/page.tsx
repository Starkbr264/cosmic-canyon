"use client";

import { useEffect, useState } from "react";
import { API_URL } from "@/lib/api";
import { MARKETS, formatMoney } from "@/lib/money";

interface CountrySales {
  countryCode: string;
  orders: number;
  currency: string;
  revenue: number;
}

interface ProductSales {
  sku: string;
  productName: string;
  currency: string;
  units: number;
  revenue: number;
  unitPrice: number;
}

interface Dashboard {
  windowDays: number;
  ordersCreated: number;
  ordersConfirmed: number;
  revenueByCurrency: Record<string, number>;
  salesByCountry: CountrySales[];
  topProducts: ProductSales[];
  conversionRatePercent: number;
  pendingPayments: number;
  lowStockRows: number;
  declinedOrders: number;
}

/**
 * Painel do gestor.
 *
 * Os números chegam já agrupados da API — nenhuma agregação acontece no
 * navegador, porque refazer contagem no cliente significa que doisManagers veem
 * números diferentes quando o volume é grande.
 */
export default function DashboardPage() {
  const [days, setDays] = useState(30);
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    fetch(`${API_URL}/api/v1/reports/dashboard?days=${days}`, { cache: "no-store" })
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json() as Promise<Dashboard>;
      })
      .then((payload) => {
        if (!cancelled) setData(payload);
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [days]);

  const localeOf = (currency: string) =>
    MARKETS.find((m) => m.currency === currency)?.locale ?? "pt-BR";

  return (
    <main>
      <header>
        <div>
          <h1>Painel de vendas</h1>
          <p className="subtitle">
            Últimos {days} dias · leitura direta dos pedidos confirmados
          </p>
        </div>
        <nav className="filters">
          {[7, 30, 90].map((option) => (
            <button
              key={option}
              className={option === days ? "" : "ghost"}
              onClick={() => setDays(option)}
            >
              {option}d
            </button>
          ))}
        </nav>
      </header>

      {error && <div className="error">{error}</div>}
      {!data && !error && <section className="panel">Carregando…</section>}

      {data && (
        <>
          <section className="panel">
            <div className="form-grid">
              <div>
                <label>Pedidos criados</label>
                <p className="price">{data.ordersCreated}</p>
              </div>
              <div>
                <label>Pedidos confirmados</label>
                <p className="price">{data.ordersConfirmed}</p>
              </div>
              <div>
                <label>Conversão</label>
                <p className="price">{data.conversionRatePercent}%</p>
              </div>
              <div>
                <label>Pagamentos pendentes</label>
                <p className="price">{data.pendingPayments}</p>
              </div>
              <div>
                <label>Pedidos recusados</label>
                <p className="price">{data.declinedOrders}</p>
              </div>
              <div>
                <label>Itens em alerta de estoque</label>
                <p className="price">{data.lowStockRows}</p>
              </div>
            </div>
          </section>

          <section className="panel">
            <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Receita por moeda</h2>
            {Object.keys(data.revenueByCurrency).length === 0 ? (
              <p className="muted">Sem receita confirmada na janela.</p>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Moeda</th>
                    <th style={{ textAlign: "right" }}>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(data.revenueByCurrency).map(([currency, total]) => (
                    <tr key={currency}>
                      <td className="sku">{currency}</td>
                      <td className="price" style={{ textAlign: "right" }}>
                        {formatMoney(total, currency, localeOf(currency))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <p className="muted" style={{ fontSize: ".85rem", marginBottom: 0 }}>
              Moedas não são somadas entre si: cada uma vira o total da moeda em
              que foi cobrada.
            </p>
          </section>

          <section className="panel">
            <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Vendas por país</h2>
            <table>
              <thead>
                <tr>
                  <th>País</th>
                  <th style={{ textAlign: "right" }}>Pedidos</th>
                  <th style={{ textAlign: "right" }}>Receita</th>
                </tr>
              </thead>
              <tbody>
                {data.salesByCountry.map((row) => (
                  <tr key={`${row.countryCode}-${row.currency}`}>
                    <td className="sku">{row.countryCode}</td>
                    <td style={{ textAlign: "right" }}>{row.orders}</td>
                    <td className="price" style={{ textAlign: "right" }}>
                      {formatMoney(row.revenue, row.currency, localeOf(row.currency))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          <section className="panel">
            <h2 style={{ fontSize: "1rem", marginTop: 0 }}>Mais vendidos</h2>
            <table>
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Produto</th>
                  <th style={{ textAlign: "right" }}>Unidades</th>
                  <th style={{ textAlign: "right" }}>Receita</th>
                </tr>
              </thead>
              <tbody>
                {data.topProducts.map((row) => (
                  <tr key={`${row.sku}-${row.currency}`}>
                    <td className="sku">{row.sku}</td>
                    <td>{row.productName}</td>
                    <td style={{ textAlign: "right" }}>{row.units}</td>
                    <td className="price" style={{ textAlign: "right" }}>
                      {formatMoney(row.revenue, row.currency, localeOf(row.currency))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </>
      )}

      <footer>
        <span>
          <a href="/">Catálogo</a>
        </span>
        <span>
          <a href="/checkout">Checkout</a>
        </span>
      </footer>
    </main>
  );
}
