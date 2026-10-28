// ── CasaCapital — app.js (núcleo, backend Java + MySQL) ──
// Estado carregado da API, formatação (fmt, fmtD, hoje, el), filtro por período, saldo, membros,
// inicialização da página e delegação de cliques. As telas antigas carregam, depois deste arquivo:
//   app-financas.js (receitas, despesas, resumos, alertas de limite), app-compras.js (lista de compras e
//   histórico de preços), app-tarefas.js (quadro de tarefas), app-painel.js (cards do Início),
//   app-planejamento.js (orçamento e metas) e, só em Análises, app-analises.js (gráficos Premium).
// Todos são scripts comuns (não módulos): as variáveis e funções de um ficam visíveis para os outros.

const CATEGORIAS_RECEITA = ['Salário','Freelance','Aluguel recebido','Investimentos','Outros'];
const CATEGORIAS_DESPESA = ['Alimentação','Moradia','Transporte','Saúde','Educação','Lazer','Vestuário','Contas & Serviços','Outros'];

// ── Estado em memória (carregado da API, não mais do localStorage) ──
let receitas  = [];
let despesas  = [];
let compras   = [];
let tarefas   = [];
let alertas   = {};   // { categoria: limite }, montado a partir de /api/alertas
let dispensa  = [];
let orcamentos = {};  // { 'YYYY-MM': { categoria: limite } }, carregado sob demanda por mês
let metas     = [];
let historicoPrecos = {}; // { 'nome em minúsculo': [{ data, preco }] }
let membros   = [];
let _familia  = null;

const isPremium = () => !!_familia && _familia.plano === 'PREMIUM';

// Recurso Premium implementado 100% no navegador (ex: exportar PDF com jsPDF) não tem como
// ser bloqueado pelo servidor da forma como um endpoint é — então, antes de gerar o arquivo,
// confirmamos o plano com uma chamada nova (não o `_familia` já em cache) para não depender
// só do que já foi carregado na página.
async function exigirPremiumOuAvisar(mensagem) {
  try {
    const familiaAtual = await api.get('/familia/me');
    if (familiaAtual.plano !== 'PREMIUM') {
      alert(mensagem || 'Este recurso é exclusivo do plano Premium.');
      return false;
    }
    return true;
  } catch {
    alert('Não foi possível confirmar seu plano. Tente novamente.');
    return false;
  }
}

// ── Utilidades ──
function getCfg() {
  return _familia || {};
}
function getMoeda() { return getCfg().moeda || 'R$'; }
function getSep()   { return getCfg().separadorDecimal || ','; }
// Formata o valor absoluto com 2 casas e separador de milhar: 10.000,00 (ou 10,000.00 se o decimal for ponto)
function fmtNumero(v, sep) {
  const [inteiro, dec] = Math.abs(Number(v) || 0).toFixed(2).split('.');
  const milhar = sep === ',' ? '.' : ',';
  return inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, milhar) + (sep === ',' ? ',' : '.') + dec;
}
function fmtValor(v) {
  return (v < 0 ? '-' : '') + getMoeda() + ' ' + fmtNumero(v, getSep());
}
// Atualiza rótulos/placeholders estáticos que mencionam a moeda (ex: "Limite mensal ({moeda})")
// para refletir a moeda configurada em Configurações, em vez do "R$" fixo do HTML.
function aplicarUnidadeMoeda() {
  const moeda = getMoeda();
  document.querySelectorAll('[data-moeda-label]').forEach(elx => {
    const texto = elx.getAttribute('data-moeda-label').replace('{moeda}', moeda);
    if (elx.tagName === 'INPUT' || elx.tagName === 'TEXTAREA') elx.placeholder = texto;
    else elx.textContent = texto;
  });
}
const fmt  = v => fmtValor(v);
const fmtN = v => (v < 0 ? '-' : '') + fmtNumero(v, getSep());
const fmtD = dt => { const d = new Date(dt + 'T12:00:00'); return d.toLocaleDateString('pt-BR'); };
const hoje = () => new Date().toISOString().split('T')[0];
const el   = id => document.getElementById(id);

// ── Carregamento inicial de dados (substitui a leitura direta de localStorage) ──
async function carregarTudo() {
  const [fam, rec, des, comp, tar, disp, hist, memb] = await Promise.all([
    api.get('/familia/me'),
    api.get('/receitas'),
    api.get('/despesas'),
    api.get('/compras'),
    api.get('/tarefas'),
    api.get('/dispensa'),
    api.get('/historico-precos'),
    api.get('/membros'),
  ]);

  _familia = fam;
  window.__familia = fam;
  receitas = rec;
  despesas = des;
  compras  = comp.map(c => ({ ...c, frequencia: FREQ_UI[c.frequencia] || 'sem-frequencia' }));
  tarefas  = tar;
  dispensa = disp;
  membros  = memb;

  historicoPrecos = {};
  hist.forEach(p => { historicoPrecos[p.produtoChave] = p.registros; });

  metas = await api.get('/metas');

  if (isPremium()) {
    try {
      const listaAlertas = await api.get('/alertas');
      alertas = {};
      listaAlertas.forEach(a => { alertas[a.categoria] = a.limite; });
    } catch { alertas = {}; }
  }

  await carregarOrcamentoMes(mesAtualKey());
}

function calcTotais(lista_r, lista_d) {
  const r = lista_r || receitas;
  const d = lista_d || despesas;
  const tr = r.reduce((a, b) => a + b.valor, 0);
  const td = d.reduce((a, b) => a + b.valor, 0);
  return { tr, td, saldo: tr - td };
}

// ── Filtro por período ──
function filtrarPorPeriodo(lista, de, ate) {
  if (!de && !ate) return lista;
  return lista.filter(x => {
    const d = x.data || hoje();
    return (!de || d >= de) && (!ate || d <= ate);
  });
}

function getPeriodoFiltro() {
  return getFiltroAtual ? getFiltroAtual() : { de: '', ate: '' };
}

let _filtroMesKey = '';

function popularSeletorMeses() {
  const sel = el('filtroMes');
  if (!sel) return;
  const todas = [...receitas, ...despesas];
  const meses = [...new Set(todas.map(x => x.data?.slice(0, 7)).filter(Boolean))].sort();
  const atual = _filtroMesKey;
  sel.innerHTML = '<option value="">Todos os meses</option>';
  meses.forEach(key => {
    const [ano, mes] = key.split('-');
    const label = new Date(Number(ano), Number(mes) - 1, 1)
      .toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
    const opt = document.createElement('option');
    opt.value = key;
    opt.textContent = label.charAt(0).toUpperCase() + label.slice(1);
    opt.style.background = '#fff';
    opt.style.color      = '#111827';
    if (key === atual) opt.selected = true;
    sel.appendChild(opt);
  });
  if (_filtroMesKey) sel.value = _filtroMesKey;
}

function aplicarFiltroMes() {
  const sel = el('filtroMes');
  _filtroMesKey = sel?.value || '';
  atualizarLabelPeriodo();
  renderFinanceiro();
  atualizarDashboard();
  renderResumoCategoria();
}

function limparFiltro() {
  _filtroMesKey = '';
  const sel = el('filtroMes');
  if (sel) sel.value = '';
  atualizarLabelPeriodo();
  renderFinanceiro();
  atualizarDashboard();
  renderResumoCategoria();
}

function atualizarLabelPeriodo() {
  const label = el('labelPeriodo');
  const labelSaldo = el('labelSaldo');
  if (!_filtroMesKey) {
    if (label) label.textContent = '';
    if (labelSaldo) labelSaldo.textContent = 'Saldo Familiar';
    return;
  }
  const [ano, mes] = _filtroMesKey.split('-');
  const nomeMes = new Date(Number(ano), Number(mes) - 1, 1)
    .toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  const txt = nomeMes.charAt(0).toUpperCase() + nomeMes.slice(1);
  if (label) label.textContent = '→ ' + txt;
  if (labelSaldo) labelSaldo.textContent = 'Balanço: ' + txt;
}

function getFiltroAtual() {
  if (!_filtroMesKey) return { de: '', ate: '' };
  const [ano, mes] = _filtroMesKey.split('-');
  const ultimo = new Date(Number(ano), Number(mes), 0).getDate();
  return {
    de:  _filtroMesKey + '-01',
    ate: _filtroMesKey + '-' + String(ultimo).padStart(2, '0')
  };
}

// ── Saldo ──
function atualizarSaldo() {
  const { de, ate } = getPeriodoFiltro();
  const { tr, td, saldo } = calcTotais(
    filtrarPorPeriodo(receitas, de, ate),
    filtrarPorPeriodo(despesas, de, ate)
  );
  if (el('saldo')) {
    el('saldo').innerText      = fmt(saldo);
    el('saldo').style.color    = saldo < 0 ? '#FCA5A5' : '#ffffff';
  }
  if (el('resumoReceitas')) el('resumoReceitas').innerText = '↑ Receitas: ' + fmt(tr);
  if (el('resumoDespesas')) el('resumoDespesas').innerText = '↓ Despesas: ' + fmt(td);
}

// ── Membros da família (helper) ──
function getMembros() {
  return membros;
}
function getMembroPorId(id) {
  if (!id) return null;
  return getMembros().find(m => String(m.id) === String(id)) || null;
}
function tagMembro(membroId) {
  const m = getMembroPorId(membroId);
  if (!m) return '';
  const cor = m.cor || '#2563EB';
  return ` <span class="tag-membro" style="--cor-membro:${cor};--bg-membro:${cor}22;"><span class="ico ico-user"></span> ${m.nome}</span>`;
}

// ── Init ──
window.onload = async function () {
  if (!estaAutenticado()) { location.href = 'index.html'; return; }
  if (ehDependente()) return; // header.js leva o filho para a área dele

  try {
    await carregarTudo();
  } catch (erro) {
    console.error('Falha ao carregar dados iniciais:', erro);
    return;
  }
  aplicarUnidadeMoeda();

  // Avisa scripts de página (dispensa.js, historico-precos.js, familia.js, ...) que os
  // dados globais (receitas, despesas, compras, dispensa, membros, etc.) já estão prontos.
  document.dispatchEvent(new CustomEvent('casacapital-dados-carregados'));

  ['receitaCat','despesaCat','alertaCat'].forEach(id => {
    const s = el(id); if (!s) return;
    const lista = id === 'receitaCat' ? CATEGORIAS_RECEITA : CATEGORIAS_DESPESA;
    s.innerHTML = lista.map(c => `<option>${c}</option>`).join('');
  });
  ['receitaData','despesaData'].forEach(id => { const i = el(id); if(i) i.value = hoje(); });

  ['receitaMembro','despesaMembro','tarefaMembro'].forEach(id => {
    const s = el(id);
    if (s && membros.length) s.innerHTML += membros.map(m => `<option value="${m.id}">${m.nome}</option>`).join('');
  });

  renderFinanceiro();
  popularSeletorMeses();
  renderCompras();
  renderTarefas();
  atualizarSaldo();
  atualizarDashboard();
  renderCardContas();
  renderResumoOrcamentoDashboard();
  renderResumoMetasDashboard();
  renderAlertas();
  verificarAlertas();

  // Análises (gráficos/tendências/projeção) é inicializado por analises.js,
  // que busca os dados em GET /api/analises/resumo (bloqueado no servidor para Essencial).

  const badge = el('planoBadge');
  if (badge) badge.innerHTML = isPremium()
    ? '<span class="badge-plano-header premium"><span class="ico ico-star"></span> PREMIUM</span>'
    : '<span class="badge-plano-header essencial">ESSENCIAL</span>';
};

// ══════════════════════════════════════════════
// ── Delegação global de eventos ──
// ══════════════════════════════════════════════
document.addEventListener('click', function (e) {
  const acaoEl = e.target.closest('[data-action]');
  if (!acaoEl) return;
  const acao = acaoEl.getAttribute('data-action');
  const id    = acaoEl.getAttribute('data-id');
  const cat   = acaoEl.getAttribute('data-cat');

  switch (acao) {
    case 'excluir-receita':   excluirReceita(Number(id)); break;
    case 'excluir-despesa':   excluirDespesa(Number(id)); break;
    case 'comprar-item':      comprarItem(Number(id)); break;
    case 'excluir-compra':    excluirCompra(Number(id)); break;
    case 'filtro-freq-compra': definirFiltroFrequenciaCompra(acaoEl.getAttribute('data-freq'), acaoEl); break;
    case 'concluir-tarefa':   concluirTarefa(Number(id)); break;
    case 'excluir-tarefa':    excluirTarefa(Number(id)); break;
    case 'excluir-alerta':    excluirAlerta(cat); break;
    case 'excluir-orcamento': excluirOrcamentoUI(cat); break;
    case 'excluir-meta':      excluirMeta(Number(id)); break;
    case 'aporte-meta':       abrirAporte(Number(id)); break;
  }
});
