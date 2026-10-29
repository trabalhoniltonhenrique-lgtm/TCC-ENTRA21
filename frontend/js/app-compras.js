// ── CasaCapital — app-compras.js: lista de compras e histórico de preços ──

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
  { valor: 'diaria',         label: 'Diária',          icone: 'sun' },
  { valor: 'semanal',        label: 'Semanal',         icone: 'calendar' },
  { valor: 'quinzenal',      label: 'Quinzenal',       icone: 'calendar-days' },
  { valor: 'mensal',         label: 'Mensal',          icone: 'calendar-days' },
  { valor: 'sem-frequencia', label: 'Sem frequência',  icone: 'minus' },
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
  // Mesmo produto já na lista (sem diferenciar maiúsculas/espaços): pergunta antes de repetir
  const repetido = compras.find(c => chaveProduto(c.nome) === chaveProduto(nome));
  if (repetido && !confirm(`"${repetido.nome}" já está na lista (x${repetido.qtd || 1}). Adicionar de novo mesmo assim?`)) {
    el('compra').select();
    return;
  }
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
  if (msg) { msg.textContent = `"${item.nome}" movido para a dispensa!`; setTimeout(() => msg.textContent = '', 2500); }
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
      <h3><span class="ico ico-wallet"></span> Quanto custou "${item.nome}"?</h3>
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
  if (sf && !sf.options.length) sf.innerHTML = FREQUENCIAS_COMPRA.map(f => `<option value="${f.valor}">${f.label}</option>`).join('');

  const tabs = el('filtrosFrequenciaCompra');
  if (tabs && !tabs.dataset.montado) {
    tabs.innerHTML = `<div class="filtro-tab ativo" data-action="filtro-freq-compra" data-freq="todas">Todas</div>` +
      FREQUENCIAS_COMPRA.map(f => `<div class="filtro-tab" data-action="filtro-freq-compra" data-freq="${f.valor}"><span class="ico ico-${f.icone}"></span> ${f.label}</div>`).join('');
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
          <button class="btn-marcar-comprado" data-action="comprar-item" data-id="${x.id}" title="Marcar como comprado"><span class="ico ico-check"></span></button>
          <span class="nome-item-compra">${x.nome}</span>
          <span class="tag-frequencia-compra freq-${freq.valor}"><span class="ico ico-${freq.icone}"></span> ${freq.label}</span>
          <span class="qtd-item-compra">x${x.qtd}</span>
          <button class="btn-sm btn-perigo btn-sem-margem" data-action="excluir-compra" data-id="${x.id}"><span class="ico ico-x"></span></button>
        </div>`;
      }).join('')}
    </li>`).join('');
}
