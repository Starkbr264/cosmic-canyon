/**
 * Cliente da API.
 *
 * Um único lugar faz fetch, trata o `ProblemDetail` RFC 9457 e normaliza o erro
 * em `Error`. As telas nunca veem `Response`, e nenhum segredo vive aqui: o
 * frontend fala HTTP público, então qualquer chave de API no bundle é pública
 * por definição.
 */

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081";

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  price: number;
  active: boolean;
  createdAt: string | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiProblem {
  type?: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  fields?: Record<string, string>;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init?.headers,
    },
    cache: "no-store",
  });

  if (!response.ok) {
    let problem: ApiProblem = {
      title: `HTTP ${response.status}`,
      status: response.status,
    };
    try {
      problem = { ...problem, ...(await response.json()) };
    } catch {
      // corpo nao-JSON: mantem o titulo padrão
    }
    throw new Error(
      problem.fields
        ? `${problem.title}: ${Object.entries(problem.fields)
            .map(([field, msg]) => `${field} ${msg}`)
            .join("; ")}`
        : (problem.detail ?? problem.title)
    );
  }

  return response.json() as Promise<T>;
}

export const productsApi = {
  list(params?: { term?: string; active?: boolean; page?: number; size?: number }) {
    const query = new URLSearchParams();
    if (params?.term) query.set("term", params.term);
    if (params?.active !== undefined) query.set("active", String(params.active));
    if (params?.page !== undefined) query.set("page", String(params.page));
    if (params?.size !== undefined) query.set("size", String(params.size));
    const suffix = query.toString() ? `?${query}` : "";
    return request<Page<Product>>(`/api/v1/products${suffix}`);
  },

  getById(id: string) {
    return request<Product>(`/api/v1/products/${id}`);
  },

  create(payload: Omit<Product, "id" | "createdAt" | "active"> & { active?: boolean }) {
    return request<Product>("/api/v1/products", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  update(id: string, payload: Omit<Product, "id" | "createdAt">) {
    return request<Product>(`/api/v1/products/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },

  remove(id: string) {
    return request<void>(`/api/v1/products/${id}`, { method: "DELETE" });
  },
};

// ===== Checkout =====

export type PaymentMethod =
  | "PIX"
  | "CARD"
  | "BANK_TRANSFER"
  | "DIGITAL_WALLET";

export type PaymentStatus = "APPROVED" | "DECLINED" | "PENDING";

export type OrderStatus =
  | "AWAITING_PAYMENT"
  | "CONFIRMED"
  | "PAYMENT_PENDING"
  | "PAYMENT_DECLINED"
  | "CANCELLED";

export interface OrderItemView {
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  currency: string;
  subtotal: number;
  taxTotal: number;
  grandTotal: number;
  countryCode: string;
  locale: string;
  createdAt: string;
  items: OrderItemView[];
}

export interface Payment {
  id: string;
  orderId: string;
  method: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  currency: string;
  simulationReference: string;
  processedAt: string;
}

export const checkoutApi = {
  createOrder(payload: {
    customerEmail: string;
    customerName: string;
    locale: string;
    countryCode: string;
    currency: string;
    items: { productId: string; quantity: number }[];
  }) {
    return request<Order>("/api/v1/orders", {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },

  getOrder(id: string) {
    return request<Order>(`/api/v1/orders/${id}`);
  },

  /**
   * Simulação de pagamento. O payload tem exatamente dois campos: método e
   * resultado desejado. Não existe campo para número de cartão ou conta, e
   * adicionar um seria um sinal de que o fluxo saiu do escopo didático.
   */
  simulatePayment(
    orderId: string,
    payload: { method: PaymentMethod; outcome: PaymentStatus }
  ) {
    return request<Payment>(`/api/v1/orders/${orderId}/payments`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
};

export const formatBRL = (value: number) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(
    value
  );
