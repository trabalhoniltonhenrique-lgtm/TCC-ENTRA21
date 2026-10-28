// ── CasaCapital — app-planejamento.js: orçamento mensal e metas de economia ──

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
      <div class="icone"><span class="ico ico-target"></span></div>
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
        <button class="btn-sm btn-perigo btn-excluir-orc" data-action="excluir-orcamento" data-cat="${cat}"><span class="ico ico-x"></span></button>
      </div>
      <div class="linha-valores-orcamento">
        <span>${fmt(gasto)} de ${fmt(limite)}</span>
        <span class="pct-orcamento ${corCls}">${pctReal.toFixed(0)}%</span>
      </div>
      <div class="barra-fundo-orc">
        <div class="barra-progresso-orc ${corCls}" style="--largura:${pct}%;"></div>
      </div>
      <div class="status-restante-orc ${restante < 0 ? 'cor-orc-excedido' : 'cor-orc-ok'}">
        ${restante < 0 ? `<span class="ico ico-alert-triangle"></span> Ultrapassou em ${fmt(Math.abs(restante))}` : `<span class="ico ico-check"></span> Restam ${fmt(restante)}`}
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
      <h2 class="sem-margem"><span class="ico ico-target"></span> Orçamento do Mês</h2>
      <a href="metas.html#orcamento" class="link-ver-todas">Gerenciar →</a>
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

// Aportes + valor atual dos investimentos ligados (calculado pelo servidor em totalPoupado)
function totalPoupadoMeta(meta) {
  if (meta.totalPoupado != null) return Number(meta.totalPoupado);
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
      <div class="icone"><span class="ico ico-trophy"></span></div>
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
          <div class="nome-meta-item">${completa ? '<span class="ico ico-trophy"></span> ' : '<span class="ico ico-target"></span> '}${m.nome}</div>
          ${m.prazo ? `<div class="prazo-meta-item">Prazo: ${fmtD(m.prazo)}${diasRestantes !== null && !completa ? ` (${diasRestantes >= 0 ? diasRestantes + ' dias restantes' : 'prazo vencido'})` : ''}</div>` : ''}
        </div>
        <button class="btn-sm btn-perigo btn-sem-margem" data-action="excluir-meta" data-id="${m.id}"><span class="ico ico-x"></span></button>
      </div>
      <div class="linha-valores-meta">
        <span class="poupado-meta">${fmt(poupado)}</span>
        <span>de ${fmt(m.valorAlvo)}</span>
      </div>
      ${Number(m.totalInvestimentos) > 0 ? `<div class="composicao-meta">
        Aportes ${fmt(m.totalAportes)} + investimentos ${fmt(m.totalInvestimentos)}
        ${(window.carteiraInvestimentos || []).filter(i => i.metaId === m.id).length ? `<span class="invs-meta">(${(window.carteiraInvestimentos || [])
          .filter(i => i.metaId === m.id).map(i => i.nome.replace(/[&<>"']/g, '')).join(', ')})</span>` : ''}
      </div>` : ''}
      <div class="barra-fundo-meta">
        <div class="barra-progresso-meta ${completa ? 'meta-completa' : ''}" style="--largura:${pct}%;"></div>
      </div>
      <div class="rodape-meta-item">
        <span class="texto-progresso-meta ${completa ? 'meta-completa' : ''}">
          ${completa ? '<span class="ico ico-check"></span> Meta atingida!' : pct.toFixed(0) + '% concluído'}
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
      <h2 class="sem-margem"><span class="ico ico-trophy"></span> Metas de Economia</h2>
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
