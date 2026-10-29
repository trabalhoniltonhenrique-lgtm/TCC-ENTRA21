// ── CasaCapital — util.js ──
// Funções pequenas usadas por várias telas. Carregado antes dos outros scripts da página (não depende de nenhum).
// São funções globais: os scripts das telas (inclusive os que ficam dentro de (function () { ... })()) as usam direto.

// Protege um texto antes de colocá-lo no HTML ("Arroz <5kg>" aparece como texto, não vira uma tag)
function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// Data de hoje no fuso do aparelho (AAAA-MM-DD). new Date().toISOString() usaria UTC: depois das 21h em Brasília
// já seria o dia seguinte.
function hojeISO() {
  return new Date().toLocaleDateString('sv-SE');
}

// "2026-10-28" → "28/10/2026"; vazio → "—"
function formatarData(dt) {
  return dt ? new Date(dt + 'T12:00:00').toLocaleDateString('pt-BR') : '—';
}

// Valor em dinheiro com a moeda e o separador escolhidos em Configurações: R$ 1.234,56 (ou R$ 1,234.56)
function formatarDinheiro(v, cfg) {
  const sep = (cfg && cfg.separadorDecimal) || ',';
  const moeda = (cfg && cfg.moeda) || 'R$';
  const [inteiro, dec] = Math.abs(Number(v) || 0).toFixed(2).split('.');
  const milhar = sep === ',' ? '.' : ',';
  return (v < 0 ? '-' : '') + moeda + ' ' + inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, milhar) + (sep === ',' ? ',' : '.') + dec;
}
