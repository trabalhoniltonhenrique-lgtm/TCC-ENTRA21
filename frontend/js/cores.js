// ── CasaCapital — cores.js ──
// Aplica a cor principal escolhida pela família (Configurações → Cores da Família).
// Todos os tons usados no CSS (--azul, --azul-escuro, --azul-claro...) são derivados
// de uma única cor. Sem cor escolhida, valem os valores padrão do style.css (azul).

const CHAVE_COR = 'casacapital_cor';

const CORES_FAMILIA = [
  { nome: 'Azul (padrão)', cor: ''        },
  { nome: 'Verde',         cor: '#15803D' },
  { nome: 'Turquesa',      cor: '#0E7490' },
  { nome: 'Roxo',          cor: '#7C3AED' },
  { nome: 'Rosa',          cor: '#BE185D' },
  { nome: 'Vinho',         cor: '#9F1239' },
  { nome: 'Laranja',       cor: '#C2410C' },
  { nome: 'Marrom',        cor: '#92400E' },
  { nome: 'Grafite',       cor: '#334155' },
];

const VARIAVEIS_COR = ['--azul', '--azul-escuro', '--azul-claro', '--azul-100', '--azul-200',
                       '--azul-300', '--azul-claro-escuro', '--sidebar-escuro'];

function hexParaRgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}
function rgbParaHex(rgb) {
  return '#' + rgb.map(v => Math.round(v).toString(16).padStart(2, '0')).join('').toUpperCase();
}
// Mistura `hex` com `outra` (peso = quanto de `outra` entra, 0..1)
function misturar(hex, outra, peso) {
  const a = hexParaRgb(hex), b = hexParaRgb(outra);
  return rgbParaHex(a.map((v, i) => v + (b[i] - v) * peso));
}
function luminancia(hex) {
  const [r, g, b] = hexParaRgb(hex).map(v => {
    v /= 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
// Botões e a sidebar usam texto branco sobre a cor principal: escurece cores muito
// claras (ex.: amarelo) até atingir contraste mínimo de 4.5:1 com o branco.
function garantirContraste(hex) {
  let cor = hex;
  for (let i = 0; i < 20 && 1.05 / (luminancia(cor) + 0.05) < 4.5; i++) cor = misturar(cor, '#000000', 0.1);
  return cor;
}

function corValida(cor) {
  return typeof cor === 'string' && /^#[0-9A-Fa-f]{6}$/.test(cor);
}

// Aplica a cor no documento (sem salvar no servidor). Cor vazia/inválida volta ao padrão.
// guardar=false é usado na pré-visualização de Configurações: não grava no cache local,
// para que uma cor experimentada e não salva não reapareça nas outras telas.
function aplicarCorFamilia(cor, guardar = true) {
  const raiz = document.documentElement.style;
  if (!corValida(cor)) {
    VARIAVEIS_COR.forEach(v => raiz.removeProperty(v));
    if (guardar) try { localStorage.removeItem(CHAVE_COR); } catch { /* sem storage: só não guarda */ }
    return;
  }
  const base = garantirContraste(cor.toUpperCase());
  raiz.setProperty('--azul',              base);
  raiz.setProperty('--azul-escuro',       misturar(base, '#0B1020', 0.45));
  raiz.setProperty('--azul-claro',        misturar(base, '#FFFFFF', 0.93));
  raiz.setProperty('--azul-100',          misturar(base, '#FFFFFF', 0.84));
  raiz.setProperty('--azul-200',          misturar(base, '#FFFFFF', 0.72));
  raiz.setProperty('--azul-300',          misturar(base, '#FFFFFF', 0.50));
  raiz.setProperty('--azul-claro-escuro', misturar(base, '#0F172A', 0.72));
  raiz.setProperty('--sidebar-escuro',    misturar(base, '#0B1220', 0.85));
  if (guardar) try { localStorage.setItem(CHAVE_COR, cor.toUpperCase()); } catch { /* idem */ }
}

// Valor atual de uma variável de cor (para gráficos desenhados em canvas/SVG pelo JS)
function corTema(variavel) {
  return getComputedStyle(document.documentElement).getPropertyValue(variavel).trim();
}

// Aplica já a última cor conhecida, antes da página renderizar (evita "piscar" o azul);
// o header.js confirma com o valor do servidor assim que carregar a família.
(function () {
  let salva = null;
  try { salva = localStorage.getItem(CHAVE_COR); } catch { /* sem storage */ }
  if (corValida(salva)) aplicarCorFamilia(salva);
})();
