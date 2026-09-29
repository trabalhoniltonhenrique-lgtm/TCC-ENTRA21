// ── CasaCapital — app.js (v12, backend Java + MySQL) ──

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

// ── Finanças ──
async function adicionarReceita() {
  const nome  = el('receitaNome')?.value.trim();
  const valor = parseFloat(el('receitaValor')?.value || 0);
  const cat   = el('receitaCat')?.value  || 'Outros';
  const data  = el('receitaData')?.value || hoje();
  const membroId = el('receitaMembro')?.value || null;
  if (!nome || !valor) return;
  const nova = await api.post('/receitas', { nome, valor, categoria: cat, data, membroId });
  receitas.push(nova);
  el('receitaNome').value = ''; el('receitaValor').value = '';
  renderFinanceiro(); verificarAlertas(); popularSeletorMeses();
}

async function adicionarDespesa() {
  const nome  = el('despesaNome')?.value.trim();
  const valor = parseFloat(el('despesaValor')?.value || 0);
  const cat   = el('despesaCat')?.value  || 'Outros';
  const data  = el('despesaData')?.value || hoje();
  const membroId = el('despesaMembro')?.value || null;
  if (!nome || !valor) return;
  const nova = await api.post('/despesas', { nome, valor, categoria: cat, data, membroId });
  despesas.push(nova);
  el('despesaNome').value = ''; el('despesaValor').value = '';
  renderFinanceiro(); verificarAlertas(); popularSeletorMeses();
}

async function excluirReceita(id) {
  if (!confirm('Excluir esta receita?')) return;
  await api.del('/receitas/' + id);
  receitas = receitas.filter(x => x.id !== id);
  renderFinanceiro();
}
async function excluirDespesa(id) {
  if (!confirm('Excluir esta despesa?')) return;
  await api.del('/despesas/' + id);
  despesas = despesas.filter(x => x.id !== id);
  renderFinanceiro();
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
  return ` <span class="tag-membro" style="--cor-membro:${cor};--bg-membro:${cor}22;">👤 ${m.nome}</span>`;
}

function renderFinanceiro() {
  const { de, ate } = getPeriodoFiltro();
  const recFilt = filtrarPorPeriodo(receitas, de, ate);
  const desFilt = filtrarPorPeriodo(despesas, de, ate);

  const lr = el('listaReceitas');
  const ld = el('listaDespesas');
  if (lr) lr.innerHTML = recFilt.length
    ? [...recFilt].reverse().map((x) => {
        return `<li>
          <div class="linha-item-info">
            <span class="linha-item-nome">${x.nome}${tagMembro(x.membroId)}</span>
            <span class="linha-item-meta">${x.categoria} · ${fmtD(x.data)}</span>
          </div>
          <span class="item-receita valor-sem-quebra">${fmt(x.valor)}</span>
          <button class="btn-sm btn-perigo btn-excluir-item" data-action="excluir-receita" data-id="${x.id}">✕</button>
        </li>`;
      }).join('')
    : '<li class="li-vazio">Nenhuma receita no período.</li>';

  if (ld) ld.innerHTML = desFilt.length
    ? [...desFilt].reverse().map((x) => {
        return `<li>
          <div class="linha-item-info">
            <span class="linha-item-nome">${x.nome}${x.origemConta ? ' <span class="tag-origem-conta">📋 Conta</span>' : ''}${tagMembro(x.membroId)}</span>
            <span class="linha-item-meta">${x.categoria} · ${fmtD(x.data)}</span>
          </div>
          <span class="item-despesa valor-sem-quebra">${fmt(x.valor)}</span>
          <button class="btn-sm btn-perigo btn-excluir-item" data-action="excluir-despesa" data-id="${x.id}">✕</button>
        </li>`;
      }).join('')
    : '<li class="li-vazio">Nenhuma despesa no período.</li>';

  atualizarSaldo();
  atualizarDashboard();
  renderResumoCategoria();
  renderResumoPorMembro();
}

// ── Resumo por categoria ──
function renderResumoCategoria() {
  const el2 = el('resumoCategoria');
  if (!el2) return;
  const { de, ate } = getPeriodoFiltro();
  const desFilt = filtrarPorPeriodo(despesas, de, ate);
  if (!desFilt.length) { el2.innerHTML = ''; return; }

  const mapa = {};
  desFilt.forEach(d => { mapa[d.categoria] = (mapa[d.categoria] || 0) + d.valor; });
  const total  = Object.values(mapa).reduce((a, b) => a + b, 0);
  const sorted = Object.entries(mapa).sort((a, b) => b[1] - a[1]);

  el2.innerHTML = `
    <h3 class="titulo-resumo-cat">📊 Gastos por Categoria</h3>
    ${sorted.map(([cat, val]) => {
      const pct  = ((val / total) * 100).toFixed(0);
      const lim  = alertas[cat];
      const over = lim && val > lim;
      const corCls = over ? 'cor-alerta' : (val === sorted[0][1] ? 'cor-destaque' : 'cor-normal');
      return `<div class="bloco-cat-resumo">
        <div class="linha-cat-resumo">
          <span>${cat}${lim ? ` <span class="limite-info">(limite: ${fmt(lim)})</span>` : ''}</span>
          <span class="valor-cat-resumo ${corCls}">${fmt(val)} (${pct}%)</span>
        </div>
        <div class="barra-fundo-cat">
          <div class="barra-progresso-cat ${corCls}" style="--largura:${Math.min(pct,100)}%;"></div>
        </div>
        ${over ? `<div class="aviso-limite-excedido">⚠️ Limite excedido em ${fmt(val - lim)}</div>` : ''}
      </div>`;
    }).join('')}
    ${sorted[0][1] / total > 0.35
      ? `<div class="aviso-categoria-dominante">
          ⚠️ <strong>${sorted[0][0]}</strong> representa ${((sorted[0][1]/total)*100).toFixed(0)}% dos gastos. Revise se está dentro do planejado.
         </div>`
      : ''}
  `;
}

// ── Resumo por membro da família ──
function renderResumoPorMembro() {
  const el2 = el('resumoMembros');
  if (!el2) return;
  const membrosLista = getMembros();
  if (!membrosLista.length) { el2.innerHTML = ''; return; }

  const { de, ate } = getPeriodoFiltro();
  const recFilt = filtrarPorPeriodo(receitas, de, ate);
  const desFilt = filtrarPorPeriodo(despesas, de, ate);

  const linhas = membrosLista.map(m => {
    const totalReceita = recFilt.filter(r => String(r.membroId) === String(m.id)).reduce((a,b) => a+b.valor, 0);
    const totalDespesa = desFilt.filter(d => String(d.membroId) === String(m.id)).reduce((a,b) => a+b.valor, 0);
    return { m, totalReceita, totalDespesa };
  }).filter(x => x.totalReceita > 0 || x.totalDespesa > 0);

  const semVinculoR = recFilt.filter(r => !r.membroId).reduce((a,b) => a+b.valor, 0);
  const semVinculoD = desFilt.filter(d => !d.membroId).reduce((a,b) => a+b.valor, 0);

  if (!linhas.length && !semVinculoR && !semVinculoD) { el2.innerHTML = ''; return; }

  el2.innerHTML = `
    <h3 class="titulo-resumo-cat">👨‍👩‍👧‍👦 Balanço por Membro</h3>
    ${linhas.map(({ m, totalReceita, totalDespesa }) => `
      <div class="linha-balanco-membro">
        <div class="avatar-mini" style="--cor-avatar:${m.cor || '#2563EB'};">
          ${(m.nome||'?').charAt(0).toUpperCase()}
        </div>
        <span class="nome-balanco-membro">${m.nome}</span>
        <div class="valores-balanco-membro">
          <div class="cor-receita">+${fmt(totalReceita)}</div>
          <div class="cor-despesa">-${fmt(totalDespesa)}</div>
        </div>
      </div>`).join('')}
    ${(semVinculoR || semVinculoD) ? `
      <div class="linha-balanco-membro sem-vinculo">
        <div class="avatar-mini avatar-mini-cinza">?</div>
        <span class="nome-balanco-membro sem-membro">Sem membro vinculado</span>
        <div class="valores-balanco-membro">
          <div class="cor-receita">+${fmt(semVinculoR)}</div>
          <div class="cor-despesa">-${fmt(semVinculoD)}</div>
        </div>
      </div>` : ''}
  `;
}

// ── Alertas de limite (Premium) ──
function verificarAlertas() {
  const painelAlertas = el('painelAlertas');
  if (!painelAlertas || !isPremium()) return;
  const mapa = {};
  despesas.forEach(d => { mapa[d.categoria] = (mapa[d.categoria] || 0) + d.valor; });
  const avisos = Object.entries(alertas)
    .filter(([cat, lim]) => mapa[cat] && mapa[cat] > lim)
    .map(([cat, lim]) => `<div class="aviso-alerta-disparado">
        🚨 <strong>${cat}</strong>: gasto de ${fmt(mapa[cat])} ultrapassou o limite de ${fmt(lim)}
      </div>`);
  painelAlertas.innerHTML = avisos.length
    ? `<div class="bloco-avisos-margem">${avisos.join('')}</div>` : '';
}

async function salvarAlerta() {
  const cat = el('alertaCat')?.value;
  const lim = parseFloat(el('alertaLimite')?.value || 0);
  if (!cat || !lim) return;
  try {
    await api.post('/alertas', { categoria: cat, limite: lim });
  } catch (erro) {
    alert(erro.status === 403 ? 'Alertas de limite são um recurso Premium. Faça upgrade do seu plano para usar.' : (erro.message || 'Não foi possível salvar o alerta.'));
    return;
  }
  alertas[cat] = lim;
  renderAlertas();
  verificarAlertas();
  renderResumoCategoria();
  if (el('alertaLimite')) el('alertaLimite').value = '';
}

async function excluirAlerta(cat) {
  await api.del('/alertas/' + encodeURIComponent(cat));
  delete alertas[cat];
  renderAlertas(); renderResumoCategoria(); verificarAlertas();
}

function renderAlertas() {
  const l = el('listaAlertas');
  if (!l) return;
  const entradas = Object.entries(alertas);
  l.innerHTML = entradas.length
    ? entradas.map(([cat, lim]) =>
        `<li><span>🔔 <strong>${cat}</strong> — limite ${fmt(lim)}</span>
         <button class="btn-sm btn-perigo" data-action="excluir-alerta" data-cat="${cat}">✕</button></li>`
      ).join('')
    : '<li class="li-vazio sem-padding">Nenhum alerta configurado.</li>';
}

// ══════════════════════════════════════════════
// ── Histórico de Preços ──
// Carregado a partir de /api/historico-precos (calculado no servidor); a
// gravação de novos preços acontece como parte de POST /api/compras/{id}/comprar.
// ══════════════════════════════════════════════

function chaveProduto(nome) {
  return (nome || '').trim().toLowerCase();
}

function historicoDoProduto(nome) {
  return historicoPrecos[chaveProduto(nome)] || [];
}

function ultimoPreco(nome) {
  const h = historicoDoProduto(nome);
  return h.length ? h[h.length - 1].preco : null;
}

function variacaoPreco(nome) {
  const h = historicoDoProduto(nome);
  if (h.length < 2) return null;
  const atual    = h[h.length - 1].preco;
  const anterior = h[h.length - 2].preco;
  if (anterior === 0) return null;
  const pct = ((atual - anterior) / anterior) * 100;
  return { atual, anterior, pct, subiu: pct > 0.05, desceu: pct < -0.05 };
}

function tagVariacaoPreco(nome) {
  const v = variacaoPreco(nome);
  const ultimo = ultimoPreco(nome);
  if (ultimo === null) return '';
  const precoTxt = fmt(ultimo) + '/un.';
  if (!v) return `<span class="tag-preco">${precoTxt}</span>`;
  const cls  = v.subiu ? 'preco-subiu' : v.desceu ? 'preco-desceu' : 'preco-igual';
  const seta = v.subiu ? '↑' : v.desceu ? '↓' : '→';
  return `<span class="tag-preco ${cls}" title="Compra anterior: ${fmt(v.anterior)}">${precoTxt} ${seta} ${Math.abs(v.pct).toFixed(0)}%</span>`;
}

async function recarregarHistoricoPrecos() {
  const hist = await api.get('/historico-precos');
  historicoPrecos = {};
  hist.forEach(p => { historicoPrecos[p.produtoChave] = p.registros; });
}

// ── Compras ──
const CATS_COMPRA = ['Geral','Hortifruti','Laticínios','Carnes','Bebidas','Higiene','Limpeza','Frios','Padaria','Outros'];

const FREQUENCIAS_COMPRA = [
  { valor: 'diaria',         label: 'Diária',          icone: '☀️' },
  { valor: 'semanal',        label: 'Semanal',         icone: '📅' },
  { valor: 'quinzenal',      label: 'Quinzenal',       icone: '🗓️' },
  { valor: 'mensal',         label: 'Mensal',          icone: '📆' },
  { valor: 'sem-frequencia', label: 'Sem frequência',  icone: '➖' },
];
function infoFrequencia(valor) {
  return FREQUENCIAS_COMPRA.find(f => f.valor === valor) || FREQUENCIAS_COMPRA[FREQUENCIAS_COMPRA.length - 1];
}

let _filtroFrequenciaCompra = 'todas';

async function adicionarCompra() {
  const nome = el('compra')?.value.trim();
  const qtd  = parseInt(el('compraQtd')?.value || 1);
  const cat  = el('compraCat')?.value || 'Geral';
  const freq = el('compraFrequencia')?.value || 'sem-frequencia';
  if (!nome) return;
  const nova = await api.post('/compras', { nome, qtd: qtd || 1, categoria: cat, frequencia: FREQ_API[freq] });
  compras.push({ ...nova, frequencia: freq });
  el('compra').value = '';
  if (el('compraQtd'))  el('compraQtd').value  = '1';
  if (el('compraCat'))  el('compraCat').value   = 'Geral';
  renderCompras(); atualizarDashboard();
}

async function excluirCompra(id) {
  if (!confirm('Excluir item?')) return;
  await api.del('/compras/' + id);
  compras = compras.filter(x => x.id !== id);
  renderCompras(); atualizarDashboard();
}

function comprarItem(id) {
  const item = compras.find(x => x.id === id);
  if (!item) return;
  abrirModalPrecoCompra(item);
}

// Chamada pelo modal de preço (botão "Confirmar" ou "Pular preço")
async function confirmarCompraItem(id, precoUnitario) {
  const item = compras.find(x => x.id === id);
  if (!item) return;

  const itemDispensa = await api.post(`/compras/${id}/comprar`, {
    precoUnitario: precoUnitario && precoUnitario > 0 ? precoUnitario : null,
  });

  compras = compras.filter(x => x.id !== id);
  const idxDispensa = dispensa.findIndex(d => d.id === itemDispensa.id);
  if (idxDispensa >= 0) dispensa[idxDispensa] = itemDispensa;
  else dispensa.push(itemDispensa);

  if (precoUnitario && precoUnitario > 0) {
    await recarregarHistoricoPrecos();
  }

  renderCompras(); atualizarDashboard();
  if (typeof renderDispensa === 'function') renderDispensa();
  fecharModalPrecoCompra();

  const msg = el('msgCompra');
  if (msg) { msg.textContent = `✔ "${item.nome}" movido para a dispensa!`; setTimeout(() => msg.textContent = '', 2500); }
}

// ── Modal de preço ao marcar item como comprado ──
function abrirModalPrecoCompra(item) {
  fecharModalPrecoCompra();

  const ultimo = ultimoPreco(item.nome);
  const overlay = document.createElement('div');
  overlay.id = 'modalPrecoCompra';
  overlay.className = 'modal-overlay';
  overlay.innerHTML = `
    <div class="modal-box">
      <h3>💰 Quanto custou "${item.nome}"?</h3>
      <p class="modal-sub">Preço por unidade${item.qtd > 1 ? ` (você está comprando ${item.qtd})` : ''}. Informar é opcional.</p>
      ${ultimo !== null ? `<p class="modal-info-anterior">Última compra: ${fmt(ultimo)}/un.</p>` : ''}
      <input id="inputPrecoCompra" type="number" min="0" step="0.01" placeholder="Ex: 5,90" autofocus>
      <div class="modal-acoes">
        <button id="btnConfirmarPreco">Confirmar compra</button>
        <button id="btnPularPreco" class="btn-secundario">Pular preço</button>
      </div>
    </div>`;
  document.body.appendChild(overlay);

  document.getElementById('btnConfirmarPreco').addEventListener('click', () => {
    const v = parseFloat(document.getElementById('inputPrecoCompra').value || 0);
    confirmarCompraItem(item.id, v);
  });
  document.getElementById('btnPularPreco').addEventListener('click', () => {
    confirmarCompraItem(item.id, 0);
  });
  document.getElementById('inputPrecoCompra').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') document.getElementById('btnConfirmarPreco').click();
  });
  document.getElementById('inputPrecoCompra').focus();
}

function fecharModalPrecoCompra() {
  const m = document.getElementById('modalPrecoCompra');
  if (m) m.remove();
}

function definirFiltroFrequenciaCompra(valor, elTab) {
  _filtroFrequenciaCompra = valor;
  if (elTab) {
    const tabs = el('filtrosFrequenciaCompra');
    if (tabs) tabs.querySelectorAll('.filtro-tab').forEach(t => t.classList.remove('ativo'));
    elTab.classList.add('ativo');
  }
  renderCompras();
}

function renderCompras() {
  const sc = el('compraCat');
  if (sc && !sc.options.length) sc.innerHTML = CATS_COMPRA.map(c => `<option>${c}</option>`).join('');

  const sf = el('compraFrequencia');
  if (sf && !sf.options.length) sf.innerHTML = FREQUENCIAS_COMPRA.map(f => `<option value="${f.valor}">${f.icone} ${f.label}</option>`).join('');

  const tabs = el('filtrosFrequenciaCompra');
  if (tabs && !tabs.dataset.montado) {
    tabs.innerHTML = `<div class="filtro-tab ativo" data-action="filtro-freq-compra" data-freq="todas">Todas</div>` +
      FREQUENCIAS_COMPRA.map(f => `<div class="filtro-tab" data-action="filtro-freq-compra" data-freq="${f.valor}">${f.icone} ${f.label}</div>`).join('');
    tabs.dataset.montado = '1';
  }

  const l = el('listaCompras');
  if (!l) return;

  const filtradas = _filtroFrequenciaCompra === 'todas'
    ? compras
    : compras.filter(x => x.frequencia === _filtroFrequenciaCompra);

  if (!filtradas.length) {
    l.innerHTML = `<li class="li-vazio">${compras.length ? 'Nenhum item nesta frequência.' : 'Lista de compras vazia.'}</li>`;
    return;
  }

  const grupos = {};
  filtradas.forEach(x => { if (!grupos[x.categoria]) grupos[x.categoria] = []; grupos[x.categoria].push(x); });

  l.innerHTML = Object.entries(grupos).map(([cat, itens]) => `
    <li class="li-grupo-compra">
      <div class="cat-label-compra">${cat}</div>
      ${itens.map(x => {
        const freq = infoFrequencia(x.frequencia);
        return `
        <div class="linha-item-compra">
          <button class="btn-marcar-comprado" data-action="comprar-item" data-id="${x.id}" title="Marcar como comprado">✔</button>
          <span class="nome-item-compra">${x.nome}</span>
          <span class="tag-frequencia-compra freq-${freq.valor}">${freq.icone} ${freq.label}</span>
          <span class="qtd-item-compra">x${x.qtd}</span>
          <button class="btn-sm btn-perigo btn-sem-margem" data-action="excluir-compra" data-id="${x.id}">✕</button>
        </div>`;
      }).join('')}
    </li>`).join('');
}

// ── Tarefas ──
async function adicionarTarefa() {
  const nome     = el('tarefa')?.value.trim();
  const prazo    = el('tarefaPrazo')?.value || null;
  const membroId = el('tarefaMembro')?.value || null;
  if (!nome) return;
  const nova = await api.post('/tarefas', { nome, prazo, membroId });
  tarefas.push(nova);
  el('tarefa').value = '';
  if (el('tarefaPrazo')) el('tarefaPrazo').value = '';
  renderTarefas(); atualizarDashboard();
}

async function excluirTarefa(id) {
  if (!confirm('Excluir tarefa?')) return;
  await api.del('/tarefas/' + id);
  tarefas = tarefas.filter(x => x.id !== id);
  renderTarefas(); atualizarDashboard();
}

async function concluirTarefa(id) {
  const atualizada = await api.post(`/tarefas/${id}/concluir`);
  const idx = tarefas.findIndex(x => x.id === id);
  if (idx >= 0) tarefas[idx] = atualizada;
  renderTarefas(); atualizarDashboard();
}

function statusTarefa(t) {
  if (t.concluida) return { texto: 'Concluída', cls: 'status-concluida' };
  if (!t.prazo)    return null;
  const hojeStr = hoje();
  if (t.prazo < hojeStr)  return { texto: 'Atrasada', cls: 'status-atrasada' };
  if (t.prazo === hojeStr) return { texto: 'Hoje', cls: 'status-hoje' };
  return null;
}

function tagResponsavel(membroId) {
  const m = getMembroPorId(membroId);
  if (!m) return '';
  return `<div class="tag-responsavel" style="--cor-membro:${m.cor || '#2563EB'};">👤 ${m.nome}</div>`;
}

function renderTarefas() {
  const l = el('listaTarefas');
  if (!l) return;
  if (!tarefas.length) {
    l.innerHTML = '<li class="li-vazio sem-padding">Nenhuma tarefa.</li>';
    return;
  }
  const ordenadas = [...tarefas].sort((a, b) => {
    if (a.concluida !== b.concluida) return a.concluida ? 1 : -1;
    return (a.prazo || '9999').localeCompare(b.prazo || '9999');
  });

  l.innerHTML = ordenadas.map(t => {
    const st = statusTarefa(t);
    return `<li class="${t.concluida ? 'tarefa-concluida' : ''}">
      <div class="linha-tarefa-info">
        <button class="btn-concluir-tarefa ${t.concluida ? 'feita' : ''}" data-action="concluir-tarefa" data-id="${t.id}" title="${t.concluida ? 'Reabrir' : 'Concluir'}">${t.concluida ? '✔' : ''}</button>
        <div class="tarefa-texto-wrap">
          <span class="${t.concluida ? 'tarefa-riscada' : ''}">${t.nome}</span>
          ${t.prazo ? `<div class="tarefa-prazo">📅 ${fmtD(t.prazo)}</div>` : ''}
          ${tagResponsavel(t.membroId)}
        </div>
      </div>
      ${st ? `<span class="tag-status-tarefa ${st.cls}">${st.texto}</span>` : ''}
      <button class="btn-sm btn-perigo btn-excluir-tarefa" data-action="excluir-tarefa" data-id="${t.id}">✕</button>
    </li>`;
  }).join('');
}

// ── Card de tarefas atrasadas no Dashboard ──
function renderCardTarefasAtrasadas() {
  const card = el('cardTarefasAtrasadas');
  if (!card) return;
  const hojeStr = hoje();
  const atrasadas = tarefas.filter(t => !t.concluida && t.prazo && t.prazo < hojeStr);
  const hojeT     = tarefas.filter(t => !t.concluida && t.prazo === hojeStr);
  const urgentes  = [...atrasadas, ...hojeT];

  if (!urgentes.length) { card.innerHTML = ''; card.classList.add('oculto'); return; }
  card.classList.remove('oculto');
  card.innerHTML = `
    <div class="card-tarefas-urgentes">
      <div class="titulo-tarefas-urgentes">⚠️ Tarefas que precisam de atenção</div>
      ${urgentes.map(t => `
        <div class="linha-tarefa-urgente">
          <span>${t.prazo < hojeStr ? '🚨' : '📅'} ${t.nome}</span>
          <span class="status-urgente-texto">${t.prazo < hojeStr ? 'Atrasada' : 'Hoje'}</span>
        </div>`).join('')}
    </div>`;
}

// ── Dashboard ──
function atualizarDashboard() {
  const { de, ate } = getFiltroAtual ? getFiltroAtual() : { de: '', ate: '' };
  const recFilt = filtrarPorPeriodo(receitas, de, ate);
  const desFilt = filtrarPorPeriodo(despesas, de, ate);
  const { tr, td, saldo } = calcTotais(recFilt, desFilt);

  if (el('saldo')) {
    el('saldo').innerText = fmt(saldo);
    el('saldo').classList.toggle('saldo-negativo', saldo < 0);
  }
  if (el('resumoReceitas')) el('resumoReceitas').innerText = '↑ Receitas: ' + fmt(tr);
  if (el('resumoDespesas')) el('resumoDespesas').innerText = '↓ Despesas: ' + fmt(td);

  if (el('dashboardCompras')) el('dashboardCompras').innerHTML =
    compras.slice(0,5).map(x=>`<li>🛒 ${x.nome}${x.qtd > 1 ? ' x'+x.qtd : ''}</li>`).join('') ||
    '<li class="li-vazio sem-padding">Nenhum item.</li>';
  if (el('dashboardTarefas')) el('dashboardTarefas').innerHTML =
    tarefas.filter(t => !t.concluida).slice(0,5).map(t => {
      const st = statusTarefa(t);
      return `<li><span>${st?.texto === 'Atrasada' ? '🚨' : '✅'} ${t.nome}</span>${st ? `<span class="tag-status-tarefa ${st.cls}">${st.texto}</span>` : ''}</li>`;
    }).join('') ||
    '<li class="li-vazio sem-padding">Nenhuma tarefa.</li>';
  renderCardTarefasAtrasadas();

  const ultEl = el('ultimasTransacoes');
  if (ultEl) {
    const todas = [
      ...recFilt.map(r => ({ ...r, tipo: 'receita' })),
      ...desFilt.map(d => ({ ...d, tipo: 'despesa' }))
    ].sort((a, b) => new Date(b.data) - new Date(a.data)).slice(0, 5);
    ultEl.innerHTML = todas.length
      ? todas.map(t => `<li>
          <div class="transacao-info-flex">
            <span class="transacao-nome">${t.nome}</span>
            <span class="transacao-meta">${t.categoria} · ${fmtD(t.data)}</span>
          </div>
          <span class="${t.tipo === 'receita' ? 'item-receita' : 'item-despesa'} transacao-valor">
            ${t.tipo === 'receita' ? '+' : '-'}${fmt(t.valor)}
          </span>
        </li>`).join('')
      : '<li class="li-vazio sem-padding">Nenhuma transação no período.</li>';
  }
}

// ══════════════════════════════════════════════
// ── PREMIUM: Gráficos ──
// ══════════════════════════════════════════════

// Os dados desta seção vêm exclusivamente de GET /api/analises/resumo (ver analises.js),
// que só responde para famílias Premium (403 para Essencial) — não há cálculo a partir de
// `receitas`/`despesas` locais aqui, para que o bloqueio de plano seja aplicado de verdade
// no servidor, e não apenas escondido na tela.
function renderGraficos(dados) {
  _saldoAtualAnalise = dados.saldoAtual;
  renderGraficoBarras(dados.seriesMensal);
  renderGraficoPizza(dados.despesasPorCategoria);
  renderTendencias(dados.tendenciaCategorias);
  renderComparativoMeses(dados.seriesMensal);
}

function renderGraficoBarras(seriesMensal) {
  const canvas = el('graficoBarras');
  if (!canvas) return;

  const ultimos6 = seriesMensal.slice(-6);
  const meses = ultimos6.map(s => {
    const [ano, mes] = s.mesAno.split('-');
    const label = new Date(Number(ano), Number(mes) - 1, 1).toLocaleDateString('pt-BR', { month: 'short', year: '2-digit' });
    return { label, key: s.mesAno };
  });

  const dadosR = ultimos6.map(s => s.receitas);
  const dadosD = ultimos6.map(s => s.despesas);
  const maxVal = Math.max(...dadosR, ...dadosD, 1);

  const W = canvas.offsetWidth || 560;
  const H = 220;
  canvas.width  = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, W, H);

  const pad  = { top:20, right:20, bottom:40, left:60 };
  const gW   = W - pad.left - pad.right;
  const gH   = H - pad.top  - pad.bottom;
  const grupW = gW / meses.length;
  const barW  = grupW * 0.32;

  ctx.strokeStyle = '#E5E7EB'; ctx.lineWidth = 1;
  for (let i = 0; i <= 4; i++) {
    const y = pad.top + gH - (gH / 4) * i;
    ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(W - pad.right, y); ctx.stroke();
    ctx.fillStyle = '#9CA3AF'; ctx.font = '10px Arial'; ctx.textAlign = 'right';
    ctx.fillText(fmt((maxVal / 4) * i), pad.left - 5, y + 3);
  }

  meses.forEach((m, idx) => {
    const x    = pad.left + grupW * idx + grupW / 2;
    const hR   = (dadosR[idx] / maxVal) * gH;
    const hD   = (dadosD[idx] / maxVal) * gH;
    const yBot = pad.top + gH;

    ctx.fillStyle = '#16A34A';
    ctx.fillRect(x - barW - 2, yBot - hR, barW, hR);
    ctx.fillStyle = '#DC2626';
    ctx.fillRect(x + 2, yBot - hD, barW, hD);

    ctx.fillStyle = '#6B7280'; ctx.font = '10px Arial'; ctx.textAlign = 'center';
    ctx.fillText(m.label, x, H - 8);
  });

  ctx.fillStyle = '#16A34A'; ctx.fillRect(pad.left, 4, 12, 10);
  ctx.fillStyle = '#374151'; ctx.font = '11px Arial'; ctx.textAlign = 'left';
  ctx.fillText('Receitas', pad.left + 16, 13);
  ctx.fillStyle = '#DC2626'; ctx.fillRect(pad.left + 80, 4, 12, 10);
  ctx.fillStyle = '#374151'; ctx.fillText('Despesas', pad.left + 96, 13);
}

function renderGraficoPizza(despesasPorCategoria) {
  const canvas = el('graficoPizza');
  if (!canvas || !despesasPorCategoria.length) return;

  const entries = despesasPorCategoria.map(c => [c.categoria, c.valor]);
  const total   = entries.reduce((a, [, v]) => a + v, 0);
  const cores   = ['#2563EB','#16A34A','#F59E0B','#DC2626','#7C3AED','#0891B2','#EA580C','#DB2777','#64748B'];

  const S = 200;
  canvas.width = S; canvas.height = S;
  const ctx  = canvas.getContext('2d');
  ctx.clearRect(0, 0, S, S);
  const cx   = S / 2, cy = S / 2, r = 80;
  let  angulo = -Math.PI / 2;

  entries.forEach(([cat, val], i) => {
    const fatia = (val / total) * 2 * Math.PI;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.arc(cx, cy, r, angulo, angulo + fatia);
    ctx.closePath();
    ctx.fillStyle = cores[i % cores.length];
    ctx.fill();
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 2; ctx.stroke();
    angulo += fatia;
  });

  ctx.beginPath(); ctx.arc(cx, cy, 45, 0, 2*Math.PI);
  ctx.fillStyle = '#fff'; ctx.fill();
  ctx.fillStyle = '#1E3A8A'; ctx.font = 'bold 11px Arial'; ctx.textAlign = 'center';
  ctx.fillText('Total', cx, cy - 5);
  ctx.fillText(fmt(total), cx, cy + 10);

  const leg = el('legendaPizza');
  if (leg) {
    leg.innerHTML = entries.map(([cat, val], i) => {
      const pct = ((val/total)*100).toFixed(1);
      return `<div class="legenda-pizza-linha">
        <div class="legenda-pizza-cor" style="--cor-legenda:${cores[i%cores.length]};"></div>
        <span class="legenda-pizza-cat">${cat}</span>
        <span class="legenda-pizza-pct">${pct}%</span>
      </div>`;
    }).join('');
  }
}

// ── PREMIUM: Tendências ──
function renderTendencias(tendenciaCategorias) {
  const el2 = el('painelTendencias');
  if (!el2) return;

  const mesAtual = new Date();
  const mesAnt   = new Date(); mesAnt.setMonth(mesAnt.getMonth() - 1);

  if (!tendenciaCategorias.length) { el2.innerHTML = '<p class="texto-vazio-tendencia">Dados insuficientes. Adicione transações em meses diferentes.</p>'; return; }

  const linhas = tendenciaCategorias.map(t => {
    const cat = t.categoria;
    const a   = t.anterior;
    const b   = t.atual;
    const var_ = a ? ((b - a) / a * 100).toFixed(1) : null;
    const seta = b > a ? '↑' : b < a ? '↓' : '→';
    const corCls = b > a ? 'cor-subiu' : b < a ? 'cor-desceu' : 'cor-igual';
    return `<div class="linha-tendencia">
      <span class="tendencia-cat">${cat}</span>
      <div class="tendencia-valores">
        <span class="tendencia-anterior">${a ? fmt(a) : '—'}</span>
        <span class="tendencia-atual">${b ? fmt(b) : '—'}</span>
        <span class="tendencia-variacao ${corCls}">${seta} ${var_ !== null ? var_+'%' : ''}</span>
      </div>
    </div>`;
  }).join('');

  el2.innerHTML = `
    <div class="cabecalho-tendencia">
      <span>Categoria</span>
      <div class="cabecalho-tendencia-valores">
        <span>${mesAnt.toLocaleDateString('pt-BR',{month:'short'})}</span>
        <span>${mesAtual.toLocaleDateString('pt-BR',{month:'short'})}</span>
        <span class="cabecalho-tendencia-variacao">Variação</span>
      </div>
    </div>
    ${linhas}`;
}

// ── PREMIUM: Comparativo de Meses (6 ou 12 meses) ──
let _qtdMesesComparativo = 6;
let _seriesMensalAnalise = [];

function definirPeriodoComparativo(qtd) {
  _qtdMesesComparativo = qtd;
  document.querySelectorAll('.comp-periodo-btn').forEach(b => b.classList.remove('ativo'));
  const btn = el('compBtn' + qtd);
  if (btn) btn.classList.add('ativo');
  renderComparativoMeses(_seriesMensalAnalise);
}

function renderComparativoMeses(seriesMensal) {
  const cont = el('tabelaComparativo');
  if (!cont) return;
  _seriesMensalAnalise = seriesMensal;

  const qtd = _qtdMesesComparativo;
  const linhas = seriesMensal.slice(-qtd).map(s => {
    const [ano, mes] = s.mesAno.split('-');
    const label = new Date(Number(ano), Number(mes) - 1, 1).toLocaleDateString('pt-BR', { month: 'short', year: '2-digit' });
    return { label, receita: s.receitas, despesa: s.despesas, saldo: s.saldo };
  });

  function variacao(atual, anterior) {
    if (anterior === 0) return atual === 0 ? null : 100;
    return ((atual - anterior) / Math.abs(anterior)) * 100;
  }

  function celulaVar(atual, anterior, inverteCor) {
    const v = variacao(atual, anterior);
    if (v === null) return '<span class="var-cel-vazia">—</span>';
    const subiu = v > 0;
    const corBoa = inverteCor ? !subiu : subiu;
    const corCls = Math.abs(v) < 0.5 ? 'var-cel-neutra' : (corBoa ? 'var-cel-boa' : 'var-cel-ruim');
    const seta = Math.abs(v) < 0.5 ? '→' : (subiu ? '↑' : '↓');
    return `<span class="var-cel ${corCls}">${seta} ${Math.abs(v).toFixed(0)}%</span>`;
  }

  const totalReceitas = linhas.reduce((a,l) => a + l.receita, 0);
  const totalDespesas = linhas.reduce((a,l) => a + l.despesa, 0);
  const mediaReceitas  = totalReceitas / linhas.length;
  const mediaDespesas  = totalDespesas / linhas.length;

  cont.innerHTML = `
    <div class="tabela-comparativo-wrap">
      <table class="tabela-comparativo">
        <thead>
          <tr class="linha-cabecalho-comp">
            <th class="th-mes">Mês</th>
            <th class="th-receita">Receitas</th>
            <th class="th-var">Var.</th>
            <th class="th-despesa">Despesas</th>
            <th class="th-var">Var.</th>
            <th class="th-saldo">Saldo</th>
          </tr>
        </thead>
        <tbody>
          ${linhas.map((l, idx) => {
            const ant = idx > 0 ? linhas[idx-1] : null;
            return `
            <tr class="linha-comp">
              <td class="td-mes">${l.label}</td>
              <td class="td-receita">${fmt(l.receita)}</td>
              <td class="td-var">${ant ? celulaVar(l.receita, ant.receita, false) : '<span class="var-cel-vazia">—</span>'}</td>
              <td class="td-despesa">${fmt(l.despesa)}</td>
              <td class="td-var">${ant ? celulaVar(l.despesa, ant.despesa, true) : '<span class="var-cel-vazia">—</span>'}</td>
              <td class="td-saldo ${l.saldo<0?'saldo-negativo-cel':'saldo-positivo-cel'}">${fmt(l.saldo)}</td>
            </tr>`;
          }).join('')}
        </tbody>
        <tfoot>
          <tr class="linha-media-comp">
            <td class="td-media-label">Média</td>
            <td class="td-media-receita">${fmt(mediaReceitas)}</td>
            <td></td>
            <td class="td-media-despesa">${fmt(mediaDespesas)}</td>
            <td></td>
            <td class="td-media-saldo">${fmt(mediaReceitas-mediaDespesas)}</td>
          </tr>
        </tfoot>
      </table>
    </div>`;
}

// ── PREMIUM: Projeção Financeira ──
let _saldoAtualAnalise = 0;

function calcularProjecao() {
  const economia = parseFloat(el('projEconomia')?.value || 0);
  const meses    = parseInt(el('projMeses')?.value    || 12);
  const meta     = parseFloat(el('projMeta')?.value   || 0);
  const saldoAtual = _saldoAtualAnalise;
  const saldoMensal = economia > 0 ? economia : Math.max(saldoAtual, 0);

  const resultado = el('projResultado');
  if (!resultado) return;
  if (!saldoMensal && !saldoAtual) {
    resultado.innerHTML = '<p class="texto-vazio-projecao">Adicione receitas e despesas para calcular.</p>';
    return;
  }

  const total      = saldoAtual + saldoMensal * meses;
  const mesesMeta  = meta > saldoAtual && saldoMensal > 0 ? Math.ceil((meta - saldoAtual) / saldoMensal) : null;
  const atingeMeta = meta && total >= meta;

  const pontos = Array.from({length: meses + 1}, (_, i) => saldoAtual + saldoMensal * i);
  const maxP   = Math.max(...pontos, meta || 0);
  const minP   = Math.min(...pontos, 0);
  const rangeP = maxP - minP || 1;
  const W = 400, H = 100;
  const pts = pontos.map((v, i) => ({
    x: (i / meses) * (W - 40) + 20,
    y: H - 20 - ((v - minP) / rangeP) * (H - 30)
  }));

  const svg = `<svg viewBox="0 0 ${W} ${H}" class="svg-projecao">
    ${meta ? `<line x1="20" y1="${H-20-((meta-minP)/rangeP)*(H-30)}" x2="${W-20}" y2="${H-20-((meta-minP)/rangeP)*(H-30)}" stroke="#F59E0B" stroke-width="1.5" stroke-dasharray="4"/>` : ''}
    <polyline points="${pts.map(p=>`${p.x},${p.y}`).join(' ')}" fill="none" stroke="#2563EB" stroke-width="2.5" stroke-linejoin="round"/>
    ${pts.map((p,i)=> i===0||i===meses ? `<circle cx="${p.x}" cy="${p.y}" r="4" fill="#2563EB"/>` : '').join('')}
    <text x="20" y="${H-5}" font-size="10" fill="#9CA3AF">Hoje</text>
    <text x="${W-45}" y="${H-5}" font-size="10" fill="#9CA3AF">${meses}m</text>
    ${meta ? `<text x="${W-20}" y="${H-20-((meta-minP)/rangeP)*(H-30)-4}" font-size="10" fill="#F59E0B" text-anchor="end">meta</text>` : ''}
  </svg>`;

  resultado.innerHTML = `
    <div class="grafico-projecao-wrap">${svg}</div>
    <div class="grid-projecao-stats">
      <div class="stat-projecao cor-saldo-hoje">
        <div class="stat-projecao-label">Saldo hoje</div>
        <div class="stat-projecao-valor">${fmt(saldoAtual)}</div>
      </div>
      <div class="stat-projecao cor-saldo-futuro">
        <div class="stat-projecao-label">Em ${meses} meses</div>
        <div class="stat-projecao-valor">${fmt(total)}</div>
      </div>
      <div class="stat-projecao cor-economia-mes">
        <div class="stat-projecao-label">Economia/mês</div>
        <div class="stat-projecao-valor">${fmt(saldoMensal)}</div>
      </div>
      ${meta ? `<div class="stat-projecao ${atingeMeta ? 'cor-meta-atingida' : 'cor-meta-pendente'}">
        <div class="stat-projecao-label">Meta ${fmt(meta)}</div>
        <div class="stat-projecao-valor-meta ${atingeMeta ? 'cor-meta-atingida-txt' : 'cor-meta-pendente-txt'}">${atingeMeta ? '✔ Atingida!' : mesesMeta ? `em ${mesesMeta} meses` : '—'}</div>
      </div>` : ''}
    </div>`;
}

// ── Relatórios ──
function obterTextoRelatorio() {
  const tipo = el('tipoRelatorio')?.value || 'geral';
  const { de, ate } = getPeriodoFiltro();
  const recFilt = filtrarPorPeriodo(receitas, de, ate);
  const desFilt = filtrarPorPeriodo(despesas, de, ate);
  const { tr, td, saldo } = calcTotais(recFilt, desFilt);
  const data  = new Date().toLocaleDateString('pt-BR');
  const sep   = '─'.repeat(40);
  const perStr = de || ate ? `\nPeríodo: ${de ? fmtD(de) : 'início'} até ${ate ? fmtD(ate) : 'hoje'}` : '';
  const header = `CASACAPITAL — Relatório\nEmitido em: ${data}${perStr}\n${sep}\n`;

  const catMap = {};
  desFilt.forEach(d => { catMap[d.categoria] = (catMap[d.categoria] || 0) + d.valor; });
  const catTxt = Object.entries(catMap).sort((a,b)=>b[1]-a[1]).map(([c,v])=>`  ${c}: ${fmt(v)}`).join('\n');

  if (tipo === 'financeiro')
    return header +
      `FINANCEIRO\n\nReceitas:  ${fmt(tr)}\nDespesas:  ${fmt(td)}\nSaldo:     ${fmt(saldo)}\n\n` +
      `GASTOS POR CATEGORIA:\n${catTxt||'  (nenhuma)'}\n\n` +
      `RECEITAS:\n`+(recFilt.map(x=>`  [${fmtD(x.data)}] ${x.nome} (${x.categoria}): ${fmt(x.valor)}`).join('\n')||'  (nenhuma)')+
      `\n\nDESPESAS:\n`+(desFilt.map(x=>`  [${fmtD(x.data)}] ${x.nome} (${x.categoria}): ${fmt(x.valor)}`).join('\n')||'  (nenhuma)');
  if (tipo === 'compras')
    return header + 'LISTA DE COMPRAS\n\n' + (compras.map(x=>`  • ${x.nome} x${x.qtd}`).join('\n')||'  (vazia)');
  if (tipo === 'tarefas')
    return header + 'TAREFAS FAMILIARES\n\n' + (tarefas.map(x=>`  • ${x.nome}${x.concluida ? ' (concluída)' : ''}`).join('\n')||'  (nenhuma)');
  return header +
    `RELATÓRIO GERAL CRUZADO\n\nReceitas:  ${fmt(tr)}\nDespesas:  ${fmt(td)}\nSaldo:     ${fmt(saldo)}\n\n` +
    `GASTOS POR CATEGORIA:\n${catTxt||'  (nenhuma)'}\n\n` +
    `COMPRAS (${compras.length} itens):\n`+(compras.map(x=>`  • ${x.nome} x${x.qtd}`).join('\n')||'  (vazia)')+
    `\n\nTAREFAS (${tarefas.length}):\n`+(tarefas.map(x=>`  • ${x.nome}${x.concluida ? ' (concluída)' : ''}`).join('\n')||'  (nenhuma)');
}

function visualizarRelatorio() {
  const a = el('areaRelatorio');
  if (a) a.innerHTML = '<pre class="pre-relatorio">' + obterTextoRelatorio() + '</pre>';
}

function baixarRelatorioTXT() {
  const blob = new Blob([obterTextoRelatorio()], { type: 'text/plain' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'Relatorio_CasaCapital.txt'; a.click();
}

function imprimirRelatorioSelecionado() {
  visualizarRelatorio();
  const area = el('areaRelatorio');
  const tipo = el('tipoRelatorio')?.value || 'geral';
  const data = new Date().toLocaleDateString('pt-BR');
  const w    = window.open('','','width=900,height=700');
  w.document.write(`<html><head><title>Relatório CasaCapital</title>
    <link rel="stylesheet" href="css/impressao.css"></head><body>
    <h1>CasaCapital</h1><p>Relatório: <strong>${tipo}</strong></p><p>Emitido em: ${data}</p><hr>
    ${area?.innerHTML||''}
    </body></html>`);
  w.document.close(); w.focus(); setTimeout(()=>w.print(), 300);
}

// ── PREMIUM: Export PDF ──
async function baixarRelatorioPDF() {
  if (!(await exigirPremiumOuAvisar('Exportar relatórios em PDF é um recurso Premium.'))) return;
  const { jsPDF } = window.jspdf;
  const doc  = new jsPDF();
  const data = new Date().toLocaleDateString('pt-BR');
  const { tr, td, saldo } = calcTotais();

  doc.setFillColor(30, 58, 138); doc.rect(0, 0, 210, 28, 'F');
  doc.setTextColor(255,255,255); doc.setFontSize(16); doc.setFont('helvetica','bold');
  doc.text('CasaCapital', 14, 12);
  doc.setFontSize(9); doc.setFont('helvetica','normal');
  doc.text('Relatório Financeiro — ' + data, 14, 21);

  doc.setTextColor(30,58,138); doc.setFontSize(11); doc.setFont('helvetica','bold');
  doc.text('Resumo Financeiro', 14, 38);
  doc.setDrawColor(191,219,254); doc.line(14, 40, 196, 40);

  const blocos = [
    { label:'Receitas', val: fmt(tr), cor:[22,163,74]  },
    { label:'Despesas', val: fmt(td), cor:[220,38,38]  },
    { label:'Saldo',    val: fmt(saldo), cor:[30,58,138] },
  ];
  blocos.forEach((b, i) => {
    const x = 14 + i * 62;
    doc.setFillColor(239,246,255); doc.roundedRect(x, 44, 58, 18, 2, 2, 'F');
    doc.setTextColor(107,114,128); doc.setFontSize(8); doc.setFont('helvetica','normal');
    doc.text(b.label, x+4, 51);
    doc.setTextColor(...b.cor); doc.setFontSize(11); doc.setFont('helvetica','bold');
    doc.text(b.val, x+4, 58);
  });

  const catMap = {};
  despesas.forEach(d => { catMap[d.categoria] = (catMap[d.categoria] || 0) + d.valor; });
  const cats = Object.entries(catMap).sort((a,b)=>b[1]-a[1]);
  let y = 75;
  if (cats.length) {
    doc.setTextColor(30,58,138); doc.setFontSize(11); doc.setFont('helvetica','bold');
    doc.text('Gastos por Categoria', 14, y); y += 4;
    doc.setDrawColor(191,219,254); doc.line(14, y, 196, y); y += 5;
    cats.forEach(([cat, val]) => {
      doc.setTextColor(55,65,81); doc.setFontSize(9); doc.setFont('helvetica','normal');
      doc.text(cat, 14, y);
      doc.setTextColor(220,38,38); doc.setFont('helvetica','bold');
      doc.text(fmt(val), 160, y, { align:'right' });
      y += 7; if (y > 270) { doc.addPage(); y = 20; }
    });
  }

  y += 4;
  doc.setTextColor(30,58,138); doc.setFontSize(11); doc.setFont('helvetica','bold');
  doc.text('Transações Detalhadas', 14, y); y += 4;
  doc.setDrawColor(191,219,254); doc.line(14, y, 196, y); y += 5;
  [...receitas.map(r=>({...r,tipo:'R'})), ...despesas.map(d=>({...d,tipo:'D'}))]
    .sort((a,b)=>new Date(b.data)-new Date(a.data))
    .forEach(t => {
      doc.setTextColor(55,65,81); doc.setFontSize(8); doc.setFont('helvetica','normal');
      doc.text(`[${fmtD(t.data)}] ${t.nome} (${t.categoria})`, 14, y);
      doc.setTextColor(...(t.tipo==='R'?[22,163,74]:[220,38,38]));
      doc.setFont('helvetica','bold');
      doc.text(`${t.tipo==='R'?'+':'-'}${fmtN(t.valor)}`, 196, y, { align:'right' });
      y += 6; if (y > 275) { doc.addPage(); y = 20; }
    });

  const pages = doc.internal.getNumberOfPages();
  for (let p = 1; p <= pages; p++) {
    doc.setPage(p);
    doc.setFillColor(239,246,255); doc.rect(0,285,210,12,'F');
    doc.setTextColor(156,163,175); doc.setFontSize(8); doc.setFont('helvetica','normal');
    doc.text('CasaCapital — Gestão Financeira Doméstica', 14, 292);
    doc.text(`Página ${p} de ${pages}`, 196, 292, { align:'right' });
  }

  doc.save('Relatorio_CasaCapital.pdf');
}

// ── Card Contas no Dashboard ──
async function renderCardContas() {
  const card = el('cardContas');
  if (!card) return;
  const contasLista = await api.get('/contas');
  const hojeStr = hoje();
  const fmt2    = v => fmt(v);
  const fmtD2   = dt => dt ? new Date(dt+'T12:00:00').toLocaleDateString('pt-BR') : '—';

  const atrasadas = contasLista.filter(c => !c.pago && c.vencimento < hojeStr);
  const venceHoje = contasLista.filter(c => !c.pago && c.vencimento === hojeStr);
  const proximas  = contasLista.filter(c => !c.pago && c.vencimento > hojeStr)
                          .sort((a,b) => a.vencimento.localeCompare(b.vencimento))
                          .slice(0, 3);

  const urgentes = [...atrasadas, ...venceHoje];
  if (!contasLista.length) { card.classList.add('oculto'); return; }
  card.classList.remove('oculto');

  card.innerHTML = `
    <div class="cabecalho-card-contas">
      <h2 class="sem-margem">📋 Contas a Pagar</h2>
      <a href="contas.html" class="link-ver-todas">Ver todas →</a>
    </div>
    ${urgentes.length ? `
      <div class="bloco-contas-urgentes">
        ${urgentes.map(c => `
          <div class="linha-conta-urgente">
            <span>
              ${c.vencimento < hojeStr ? '🚨' : '📅'}
              <strong>${c.descricao}</strong>
              <span class="data-conta-urgente">${fmtD2(c.vencimento)}</span>
            </span>
            <span class="valor-conta-urgente">${fmt2(c.valor)}</span>
          </div>`).join('')}
      </div>` : ''}
    ${proximas.length ? `
      <div>
        <div class="label-proximas-contas">Próximas</div>
        ${proximas.map(c => `
          <div class="linha-conta-proxima">
            <span><strong>${c.descricao}</strong> <span class="data-conta-proxima">${fmtD2(c.vencimento)}</span></span>
            <span class="valor-conta-proxima">${fmt2(c.valor)}</span>
          </div>`).join('')}
      </div>` : ''}
  `;
}

// ══════════════════════════════════════════════
// ── PLANEJAMENTO FINANCEIRO: Orçamento Mensal ──
// ══════════════════════════════════════════════

function mesAtualKey() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`;
}

async function carregarOrcamentoMes(mesKey) {
  mesKey = mesKey || mesAtualKey();
  const lista = await api.get('/orcamentos/' + mesKey);
  orcamentos[mesKey] = {};
  lista.forEach(o => { orcamentos[mesKey][o.categoria] = o.limite; });
  return orcamentos[mesKey];
}

function getOrcamentoMes(mesKey) {
  mesKey = mesKey || mesAtualKey();
  return orcamentos[mesKey] || {};
}

async function definirOrcamento(categoria, limite, mesKey) {
  mesKey = mesKey || mesAtualKey();
  await api.put('/orcamentos/' + mesKey, { categoria, limite });
  if (!orcamentos[mesKey]) orcamentos[mesKey] = {};
  orcamentos[mesKey][categoria] = limite;
}

async function removerOrcamento(categoria, mesKey) {
  mesKey = mesKey || mesAtualKey();
  await api.del('/orcamentos/' + mesKey + '/' + encodeURIComponent(categoria));
  if (orcamentos[mesKey]) delete orcamentos[mesKey][categoria];
}

function gastoPorCategoriaNoMes(mesKey) {
  mesKey = mesKey || mesAtualKey();
  const mapa = {};
  despesas.filter(d => d.data?.startsWith(mesKey)).forEach(d => {
    mapa[d.categoria] = (mapa[d.categoria] || 0) + d.valor;
  });
  return mapa;
}

async function copiarOrcamentoMesAnterior(mesKey) {
  mesKey = mesKey || mesAtualKey();
  try {
    const lista = await api.post('/orcamentos/' + mesKey + '/copiar-mes-anterior');
    orcamentos[mesKey] = {};
    lista.forEach(o => { orcamentos[mesKey][o.categoria] = o.limite; });
    return true;
  } catch {
    return false;
  }
}

function renderOrcamentos(mesKey) {
  const cont = el('listaOrcamentos');
  if (!cont) return;
  mesKey = mesKey || mesAtualKey();
  const orc    = getOrcamentoMes(mesKey);
  const gastos = gastoPorCategoriaNoMes(mesKey);

  if (!Object.keys(orc).length) {
    cont.innerHTML = `<div class="lista-vazia">
      <div class="icone">🎯</div>
      <p>Nenhum orçamento definido para este mês.<br>Use o formulário acima para começar.</p>
    </div>`;
    return;
  }

  cont.innerHTML = Object.entries(orc).map(([cat, limite]) => {
    const gasto = gastos[cat] || 0;
    const pct   = Math.min((gasto / limite) * 100, 100);
    const pctReal = (gasto / limite) * 100;
    let corCls = 'cor-orc-ok';
    if (pctReal >= 100) corCls = 'cor-orc-excedido';
    else if (pctReal >= 80) corCls = 'cor-orc-atencao';
    const restante = limite - gasto;

    return `<div class="card-orcamento-item">
      <div class="cabecalho-orcamento-item">
        <span class="cat-orcamento-item">${cat}</span>
        <button class="btn-sm btn-perigo btn-excluir-orc" data-action="excluir-orcamento" data-cat="${cat}">✕</button>
      </div>
      <div class="linha-valores-orcamento">
        <span>${fmt(gasto)} de ${fmt(limite)}</span>
        <span class="pct-orcamento ${corCls}">${pctReal.toFixed(0)}%</span>
      </div>
      <div class="barra-fundo-orc">
        <div class="barra-progresso-orc ${corCls}" style="--largura:${pct}%;"></div>
      </div>
      <div class="status-restante-orc ${restante < 0 ? 'cor-orc-excedido' : 'cor-orc-ok'}">
        ${restante < 0 ? `⚠️ Ultrapassou em ${fmt(Math.abs(restante))}` : `✔ Restam ${fmt(restante)}`}
      </div>
    </div>`;
  }).join('');
}

async function excluirOrcamentoUI(cat) {
  if (!confirm(`Remover orçamento de "${cat}"?`)) return;
  await removerOrcamento(cat);
  renderOrcamentos();
}

async function adicionarOrcamento() {
  const cat    = el('orcCat')?.value;
  const limite = parseFloat(el('orcLimite')?.value || 0);
  if (!cat || !limite) { alert('Selecione a categoria e informe o limite.'); return; }
  await definirOrcamento(cat, limite);
  if (el('orcLimite')) el('orcLimite').value = '';
  renderOrcamentos();
}

async function usarOrcamentoMesAnterior() {
  if (await copiarOrcamentoMesAnterior()) {
    renderOrcamentos();
    alert('Orçamento do mês anterior copiado com sucesso!');
  } else {
    alert('Não há orçamento definido no mês anterior.');
  }
}

async function renderResumoOrcamentoDashboard() {
  const card = el('cardOrcamento');
  if (!card) return;
  const mesKey = mesAtualKey();
  const orc    = getOrcamentoMes(mesKey);
  if (!Object.keys(orc).length) { card.classList.add('oculto'); return; }

  const gastos = gastoPorCategoriaNoMes(mesKey);
  const totalLimite = Object.values(orc).reduce((a,b) => a+b, 0);
  const totalGasto   = Object.keys(orc).reduce((a,cat) => a + (gastos[cat]||0), 0);
  const pct = totalLimite ? Math.min((totalGasto/totalLimite)*100, 100) : 0;
  const razao = totalGasto / totalLimite;
  const corCls = razao >= 1 ? 'cor-orc-excedido' : razao >= .8 ? 'cor-orc-atencao' : 'cor-orc-ok';

  card.classList.remove('oculto');
  card.innerHTML = `
    <div class="cabecalho-resumo-orc">
      <h2 class="sem-margem">🎯 Orçamento do Mês</h2>
      <a href="orcamento.html" class="link-ver-todas">Gerenciar →</a>
    </div>
    <div class="linha-valores-orcamento">
      <span>${fmt(totalGasto)} de ${fmt(totalLimite)}</span>
      <span class="pct-orcamento ${corCls}">${((totalGasto/totalLimite)*100).toFixed(0)}%</span>
    </div>
    <div class="barra-fundo-orc">
      <div class="barra-progresso-orc ${corCls}" style="--largura:${pct}%;"></div>
    </div>`;
}

// ══════════════════════════════════════════════
// ── PLANEJAMENTO FINANCEIRO: Metas de Economia ──
// ══════════════════════════════════════════════

function totalPoupadoMeta(meta) {
  return (meta.aportes || []).reduce((a, b) => a + b.valor, 0);
}

async function adicionarMeta() {
  const nome  = el('metaNome')?.value.trim();
  const valor = parseFloat(el('metaValor')?.value || 0);
  const prazo = el('metaPrazo')?.value || null;
  if (!nome || !valor) { alert('Informe o nome e o valor da meta.'); return; }
  const nova = await api.post('/metas', { nome, valorAlvo: valor, prazo });
  metas.push(nova);
  el('metaNome').value = ''; el('metaValor').value = ''; el('metaPrazo').value = '';
  renderMetas();
}

async function excluirMeta(id) {
  if (!confirm('Excluir esta meta?')) return;
  await api.del('/metas/' + id);
  metas = metas.filter(m => m.id !== id);
  renderMetas();
}

async function abrirAporte(id) {
  const valor = prompt('Quanto deseja adicionar a esta meta?');
  const v = parseFloat(valor);
  if (!v || v <= 0) return;
  const atualizada = await api.post(`/metas/${id}/aporte`, { valor: v });
  const idx = metas.findIndex(m => m.id === id);
  if (idx >= 0) metas[idx] = atualizada;
  renderMetas();
}

function renderMetas() {
  const cont = el('listaMetas');
  if (!cont) return;

  if (!metas.length) {
    cont.innerHTML = `<div class="lista-vazia">
      <div class="icone">🏆</div>
      <p>Nenhuma meta cadastrada ainda.<br>Crie sua primeira meta de economia acima.</p>
    </div>`;
    return;
  }

  cont.innerHTML = metas.map(m => {
    const poupado = totalPoupadoMeta(m);
    const pct     = Math.min((poupado / m.valorAlvo) * 100, 100);
    const completa = poupado >= m.valorAlvo;
    const diasRestantes = m.prazo ? Math.ceil((new Date(m.prazo+'T12:00:00') - new Date()) / 86400000) : null;

    return `<div class="card-meta-item ${completa ? 'meta-completa' : ''}">
      <div class="cabecalho-meta-item">
        <div>
          <div class="nome-meta-item">${completa ? '🏆 ' : '🎯 '}${m.nome}</div>
          ${m.prazo ? `<div class="prazo-meta-item">Prazo: ${fmtD(m.prazo)}${diasRestantes !== null && !completa ? ` (${diasRestantes >= 0 ? diasRestantes + ' dias restantes' : 'prazo vencido'})` : ''}</div>` : ''}
        </div>
        <button class="btn-sm btn-perigo btn-sem-margem" data-action="excluir-meta" data-id="${m.id}">✕</button>
      </div>
      <div class="linha-valores-meta">
        <span class="poupado-meta">${fmt(poupado)}</span>
        <span>de ${fmt(m.valorAlvo)}</span>
      </div>
      <div class="barra-fundo-meta">
        <div class="barra-progresso-meta ${completa ? 'meta-completa' : ''}" style="--largura:${pct}%;"></div>
      </div>
      <div class="rodape-meta-item">
        <span class="texto-progresso-meta ${completa ? 'meta-completa' : ''}">
          ${completa ? '✔ Meta atingida!' : pct.toFixed(0) + '% concluído'}
        </span>
        ${!completa ? `<button class="btn-add-aporte" data-action="aporte-meta" data-id="${m.id}">+ Adicionar valor</button>` : ''}
      </div>
    </div>`;
  }).join('');
}

function renderResumoMetasDashboard() {
  const card = el('cardMetas');
  if (!card) return;
  if (!metas.length) { card.classList.add('oculto'); return; }

  const ativas = metas.filter(m => totalPoupadoMeta(m) < m.valorAlvo);
  if (!ativas.length) { card.classList.add('oculto'); return; }

  card.classList.remove('oculto');
  card.innerHTML = `
    <div class="cabecalho-resumo-orc">
      <h2 class="sem-margem">🏆 Metas de Economia</h2>
      <a href="metas.html" class="link-ver-todas">Ver todas →</a>
    </div>
    ${ativas.slice(0,2).map(m => {
      const poupado = totalPoupadoMeta(m);
      const pct = Math.min((poupado/m.valorAlvo)*100, 100);
      return `<div class="linha-resumo-meta">
        <div class="topo-resumo-meta">
          <span class="nome-resumo-meta">${m.nome}</span>
          <span class="pct-resumo-meta">${pct.toFixed(0)}%</span>
        </div>
        <div class="barra-fundo-resumo-meta">
          <div class="barra-progresso-resumo-meta" style="--largura:${pct}%;"></div>
        </div>
      </div>`;
    }).join('')}`;
}

// ── Init ──
window.onload = async function () {
  if (!estaAutenticado()) { location.href = 'index.html'; return; }

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
    ? '<span class="badge-plano-header premium">⭐ PREMIUM</span>'
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
