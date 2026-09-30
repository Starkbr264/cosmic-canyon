/**
 * Formatação por país, sem biblioteca.
 *
 * `Intl` já faz o trabalho: o mesmo código serve para pt-BR, en-US, de-DE e
 * qualquer locale novo, e o navegador carrega os dados da CLDR dele. O que
 * precisa existir em casa é só o mapa país → locale/moeda, porque a moeda não
 * vem junto do locale (pt-PT é euro, pt-BR é real).
 */

export interface Market {
  country: string;
  label: string;
  locale: string;
  currency: string;
  /** Métodos de pagamento simulados Populares no mercado. */
  methods: string[];
}

export const MARKETS: Market[] = [
  {
    country: "BR",
    label: "Brasil",
    locale: "pt-BR",
    currency: "BRL",
    methods: ["PIX", "CARD", "BANK_TRANSFER"],
  },
  {
    country: "US",
    label: "Estados Unidos",
    locale: "en-US",
    currency: "USD",
    methods: ["CARD", "DIGITAL_WALLET", "BANK_TRANSFER"],
  },
  {
    country: "DE",
    label: "Alemanha",
    locale: "de-DE",
    currency: "EUR",
    methods: ["CARD", "BANK_TRANSFER", "DIGITAL_WALLET"],
  },
  {
    country: "JP",
    label: "Japão",
    locale: "ja-JP",
    currency: "JPY",
    methods: ["CARD", "DIGITAL_WALLET"],
  },
];

export const marketByCountry = (country: string): Market =>
  MARKETS.find((m) => m.country === country) ?? MARKETS[0];

const moneyFormatters = new Map<string, Intl.NumberFormat>();

/**
 * Moeda no padrão do mercado.
 *
 * JPY tem 0 casas decimais e BRL tem 2; `Intl` resolve isso sozinho, então
 * formatar 1200 em yen e 1200 em reais dá os dois certos sem condição.
 */
export function formatMoney(value: number, currency: string, locale: string): string {
  const key = `${locale}:${currency}`;
  let formatter = moneyFormatters.get(key);
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, {
      style: "currency",
      currency,
    });
    moneyFormatters.set(key, formatter);
  }
  return formatter.format(value);
}

export const formatDate = (iso: string, locale: string): string =>
  new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(iso));

/**
 * Taxas de câmbio ilustrativas, somente para a demonstração.
 *
 * As taxas reais vêm de um serviço externo. Elas ficam isoladas neste arquivo de
 * propósito: um dia em que a cotação vier da API, a troca é substituir o corpo
 * da função e nada mais no frontend sabe onde o número estava.
 */
const RATES_FROM_BRL: Record<string, number> = {
  BRL: 1,
  USD: 0.185,
  EUR: 0.17,
  JPY: 27.4,
};

export const convertFromBRL = (value: number, currency: string): number => {
  const rate = RATES_FROM_BRL[currency];
  return rate === undefined ? value : value * rate;
};
