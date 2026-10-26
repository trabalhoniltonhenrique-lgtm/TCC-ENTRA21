// ── CasaCapital — metas.js: Metas (objetivos), Orçamento do mês e Investimentos (carteira) ──
// Metas e Orçamento usam as funções de app.js (renderMetas, renderOrcamentos, adicionarMeta…).

const TIPOS_INVESTIMENTO = ['Poupança', 'CDB', 'LCI / LCA', 'Tesouro Direto', 'Fundos', 'Ações', 'FIIs', 'ETFs',
  'Previdência', 'Criptomoedas', 'Outros'];
const ABAS_METAS = ['metas', 'orcamento', 'investimentos'];

let investimentos = [];      // carteira da família (GET /investimentos)
let cdiAnual = null;         // CDI em % ao ano, informado pela família (GET /investimentos/cdi)
let editandoInvestimento = null;
window.carteiraInvestimentos = investimentos; // renderMetas (app.js) lista os investimentos ligados a cada meta

const escM = s => String(s ?? '').replace(/[&<>"']/g, ch => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[ch]));
const pctFmt = v => (v >= 0 ? '+' : '') + v.toFixed(2).replace('.', ',') + '%';

// ── Abas ──
function abrirAbaMetas(aba) {
  if (!ABAS_METAS.includes(aba)) aba = 'metas';
  document.querySelectorAll('.aba-metas').forEach(b => {
    const ativa = b.getAttribute('data-aba') === aba;
    b.classList.toggle('ativo', ativa);
    b.setAttribute('aria-selected', ativa);
  });
  ABAS_METAS.forEach(a => document.getElementById('painel-' + a).classList.toggle('oculto', a !== aba));
  if (location.hash !== '#' + aba) history.replaceState(null, '', '#' + aba);
}

// ── Projeção de rendimento (bruta, taxa constante, rendimentos reinvestidos) ──
const UNIDADE_TAXA = { MES: '% ao mês', ANO: '% ao ano', CDI: '% do CDI' };
const fmtTaxa = v => Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 4 });

// Taxa equivalente ao mês (fração), ou null se não houver taxa ou faltar o CDI
function taxaMensal(i) {
  if (i.taxa == null || !i.tipoTaxa) return null;
  const t = Number(i.taxa) / 100;
  if (i.tipoTaxa === 'MES') return t;
  if (i.tipoTaxa === 'ANO') return Math.pow(1 + t, 1 / 12) - 1;
  if (i.tipoTaxa === 'CDI') return cdiAnual == null ? null : Math.pow(1 + (Number(cdiAnual) / 100) * t, 1 / 12) - 1;
  return null;
}

function projecao(i) {
  const m = taxaMensal(i);
  if (m == null) return null;
  const atual = Number(i.valorAtual);
  const valor12 = atual * Math.pow(1 + m, 12);
  let venc = null;
  if (i.vencimento) {
    const meses = (new Date(i.vencimento + 'T12:00:00') - new Date()) / (1000 * 60 * 60 * 24 * 30.4375);
    if (meses > 0) venc = atual * Math.pow(1 + m, meses);
  }
  return { porMes: atual * m, rend12: valor12 - atual, valor12, venc };
}

async function salvarCdi() {
  const campo = document.getElementById('invCdi');
  const v = campo.value === '' ? null : Number(campo.value);
  if (v != null && !(v >= 0 && v <= 100)) { alert('Informe o CDI em % ao ano (ex.: 14,90).'); return; }
  try {
    cdiAnual = (await api.put('/investimentos/cdi', { cdiAnual: v })).cdiAnual;
    renderInvestimentos();
  } catch (e) {
    alert(e.message);
  }
}

// ── Investimentos ──
async function carregarInvestimentos() {
  const [lista, cdi] = await Promise.all([api.get('/investimentos'), api.get('/investimentos/cdi')]);
  cdiAnual = cdi.cdiAnual;
  investimentos = lista;
  window.carteiraInvestimentos = investimentos;
  renderInvestimentos();
}

// Metas mudam de progresso quando um investimento ligado muda
async function recarregarMetas() {
  metas = await api.get('/metas');
  renderMetas();
  preencherSelectMetas();
}

function preencherSelectMetas() {
  const sel = document.getElementById('invMeta');
  const atual = sel.value;
  sel.innerHTML = '<option value="">— Nenhuma —</option>' +
    metas.map(m => `<option value="${m.id}">${escM(m.nome)}</option>`).join('');
  sel.value = metas.some(m => String(m.id) === atual) ? atual : '';
}

function limparFormInvestimento() {
  editandoInvestimento = null;
  ['invNome', 'invInstituicao', 'invAplicado', 'invAtual', 'invTaxa', 'invDataAplicacao', 'invVencimento', 'invObs']
    .forEach(id => { document.getElementById(id).value = ''; });
  document.getElementById('invTipo').value = 'CDB';
  document.getElementById('invTipoTaxa').value = 'MES';
  document.getElementById('invMeta').value = '';
  document.getElementById('tituloFormInv').textContent = '+ Novo investimento';
  document.getElementById('btnSalvarInvestimento').textContent = 'Adicionar investimento';
  document.getElementById('btnCancelarInvestimento').classList.add('oculto');
}

function editarInvestimento(id) {
  const i = investimentos.find(x => x.id === id);
  if (!i) return;
  editandoInvestimento = id;
  document.getElementById('invNome').value = i.nome;
  document.getElementById('invTipo').value = i.tipo;
  document.getElementById('invInstituicao').value = i.instituicao || '';
  document.getElementById('invAplicado').value = Number(i.valorAplicado).toFixed(2);
  document.getElementById('invAtual').value = Number(i.valorAtual).toFixed(2);
  document.getElementById('invTaxa').value = i.taxa != null ? Number(i.taxa) : '';
  document.getElementById('invTipoTaxa').value = i.tipoTaxa || 'MES';
  document.getElementById('invDataAplicacao').value = i.dataAplicacao || '';
  document.getElementById('invVencimento').value = i.vencimento || '';
  document.getElementById('invMeta').value = i.metaId || '';
  document.getElementById('invObs').value = i.obs || '';
  document.getElementById('tituloFormInv').textContent = 'Editar investimento';
  document.getElementById('btnSalvarInvestimento').textContent = 'Salvar alterações';
  document.getElementById('btnCancelarInvestimento').classList.remove('oculto');
  document.getElementById('tituloFormInv').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

async function salvarInvestimento() {
  const aplicado = document.getElementById('invAplicado').value;
  const atual = document.getElementById('invAtual').value;
  const corpo = {
    nome: document.getElementById('invNome').value.trim(),
    tipo: document.getElementById('invTipo').value,
    instituicao: document.getElementById('invInstituicao').value.trim(),
    valorAplicado: Number(aplicado),
    // sem valor atual, assume o aplicado (aplicação recém-feita)
    valorAtual: atual === '' ? Number(aplicado) : Number(atual),
    taxa: document.getElementById('invTaxa').value === '' ? null : Number(document.getElementById('invTaxa').value),
    tipoTaxa: document.getElementById('invTipoTaxa').value,
    dataAplicacao: document.getElementById('invDataAplicacao').value || null,
    vencimento: document.getElementById('invVencimento').value || null,
    obs: document.getElementById('invObs').value.trim(),
    metaId: document.getElementById('invMeta').value ? Number(document.getElementById('invMeta').value) : null,
  };
  if (!corpo.nome || aplicado === '' || !(corpo.valorAplicado >= 0) || !(corpo.valorAtual >= 0)) {
    alert('Informe o nome e o valor aplicado.');
    return;
  }
  const btn = document.getElementById('btnSalvarInvestimento');
  btn.disabled = true;
  try {
    if (editandoInvestimento) await api.put('/investimentos/' + editandoInvestimento, corpo);
    else await api.post('/investimentos', corpo);
    limparFormInvestimento();
    await carregarInvestimentos();
    await recarregarMetas();
  } catch (e) {
    alert(e.message);
  } finally {
    btn.disabled = false;
  }
}

async function atualizarValorInvestimento(id) {
  const i = investimentos.find(x => x.id === id);
  if (!i) return;
  const resp = prompt(`Valor atual de "${i.nome}" (hoje: ${fmt(i.valorAtual)}):`, Number(i.valorAtual).toFixed(2));
  if (resp === null) return;
  const v = parseFloat(resp.replace(/\./g, '').replace(',', '.'));
  if (!(v >= 0)) { alert('Valor inválido.'); return; }
  try {
    await api.put(`/investimentos/${id}/valor-atual`, { valorAtual: v });
    await carregarInvestimentos();
    if (i.metaId) await recarregarMetas();
  } catch (e) {
    alert(e.message);
  }
}

async function excluirInvestimento(id) {
  const i = investimentos.find(x => x.id === id);
  if (!i || !confirm(`Excluir "${i.nome}" da carteira?`)) return;
  try {
    await api.del('/investimentos/' + id);
    if (editandoInvestimento === id) limparFormInvestimento();
    await carregarInvestimentos();
    if (i.metaId) await recarregarMetas();
  } catch (e) {
    alert(e.message);
  }
}

function renderInvestimentos() {
  const resumo = document.getElementById('resumoInvestimentos');
  const lista = document.getElementById('listaInvestimentos');

  document.getElementById('listaInstituicoes').innerHTML = [...new Set(investimentos.map(i => i.instituicao).filter(Boolean))]
    .sort((a, b) => a.localeCompare(b)).map(n => `<option value="${escM(n)}">`).join('');

  if (!investimentos.length) {
    resumo.innerHTML = '';
    lista.innerHTML = `<div class="lista-vazia">
      <div class="icone"><span class="ico ico-trending-up"></span></div>
      <p>Nenhum investimento cadastrado ainda.<br>Adicione as aplicações da família no formulário acima.</p>
    </div>`;
    return;
  }

  const aplicado = investimentos.reduce((a, i) => a + Number(i.valorAplicado), 0);
  const atual = investimentos.reduce((a, i) => a + Number(i.valorAtual), 0);
  const ganho = atual - aplicado;
  const ganhoPct = aplicado > 0 ? (ganho / aplicado) * 100 : 0;

  // distribuição por tipo (pelo valor atual)
  const porTipo = {};
  investimentos.forEach(i => { porTipo[i.tipo] = (porTipo[i.tipo] || 0) + Number(i.valorAtual); });
  const tipos = Object.entries(porTipo).sort((a, b) => b[1] - a[1]);

  const projs = investimentos.map(projecao).filter(Boolean);
  const estMes = projs.reduce((a, p) => a + p.porMes, 0);
  const est12 = projs.reduce((a, p) => a + p.rend12, 0);
  const usaCdi = investimentos.some(i => i.tipoTaxa === 'CDI' && i.taxa != null);
  const semTaxa = investimentos.filter(i => i.taxa == null).length;

  resumo.innerHTML = `
    <div class="resumo-topo">
      <div class="resumo-box cor-saldo"><div class="num">${fmt(atual)}</div><div class="txt">Valor atual da carteira</div></div>
      <div class="resumo-box cor-mes"><div class="num">${fmt(aplicado)}</div><div class="txt">Total aplicado</div></div>
      <div class="resumo-box ${ganho >= 0 ? 'cor-pago' : 'cor-atraso'}"><div class="num">${fmt(ganho)}</div>
        <div class="txt">${ganho >= 0 ? 'Rendimento' : 'Perda'} (${pctFmt(ganhoPct)})</div></div>
      ${projs.length ? `<div class="resumo-box cor-parcela"><div class="num">≈ ${fmt(estMes)}</div>
        <div class="txt">Rendimento estimado por mês · ≈ ${fmt(est12)} em 12 meses</div></div>` : ''}
    </div>
    <div class="card projecao-config">
      <label for="invCdi">CDI atual (% ao ano)</label>
      <input id="invCdi" type="number" min="0" max="100" step="0.01" placeholder="Ex.: 14,90" value="${cdiAnual ?? ''}">
      <button id="btnSalvarCdi" class="btn-secundario">Salvar CDI</button>
      <span class="dica">${usaCdi && cdiAnual == null ? '<strong>Informe o CDI</strong> para projetar os investimentos em % do CDI. ' : ''}
        Projeções são estimativas brutas (antes de impostos), com a taxa informada constante e os rendimentos reinvestidos —
        não são garantia de rendimento.${semTaxa ? ` ${semTaxa} investimento(s) sem rentabilidade informada ficam fora da estimativa.` : ''}</span>
    </div>
    <div class="card distribuicao-carteira">
      <h3>Distribuição por tipo</h3>
      ${tipos.map(([t, v]) => {
        const p = atual > 0 ? (v / atual) * 100 : 0;
        return `<div class="linha-distribuicao">
          <span class="tipo">${escM(t)}</span>
          <div class="barra-fundo-meta"><div class="barra-progresso-meta" style="--largura:${p}%;"></div></div>
          <span class="valor">${fmt(v)} <small>${p.toFixed(0)}%</small></span>
        </div>`;
      }).join('')}
    </div>`;

  lista.innerHTML = investimentos.map(i => {
    const g = Number(i.valorAtual) - Number(i.valorAplicado);
    const gPct = Number(i.valorAplicado) > 0 ? (g / Number(i.valorAplicado)) * 100 : 0;
    const detalhes = [
      i.instituicao,
      i.dataAplicacao ? 'Aplicado em ' + fmtD(i.dataAplicacao) : null,
      i.vencimento ? 'Vence ' + fmtD(i.vencimento) : null,
      i.obs,
    ].filter(Boolean).map(escM).join(' · ');
    const p = projecao(i);
    let linhaProj = '';
    if (i.taxa != null) {
      const taxaTxt = `${fmtTaxa(i.taxa)}${UNIDADE_TAXA[i.tipoTaxa] || ''}`;
      linhaProj = p
        ? `<span class="projecao-inv">Rentabilidade ${escM(taxaTxt)} · ≈ <strong>${fmt(p.porMes)}/mês</strong> · 12 meses: ≈ ${fmt(p.rend12)} (${fmt(p.valor12)})${p.venc ? ` · no vencimento: ≈ ${fmt(p.venc)}` : ''}</span>`
        : `<span class="projecao-inv">Rentabilidade ${escM(taxaTxt)} · informe o CDI no topo para ver a projeção</span>`;
    }
    return `<div class="card-investimento">
      <div class="info">
        <strong>${escM(i.nome)} <span class="tag-tipo-inv">${escM(i.tipo)}</span></strong>
        ${detalhes ? `<span>${detalhes}</span>` : ''}
        ${i.metaNome ? `<span class="meta-ligada"><span class="ico ico-trophy"></span> Meta: ${escM(i.metaNome)}</span>` : ''}
        ${linhaProj}
        <span class="atualizado">Valor atualizado em ${fmtD(i.atualizadoEm)}</span>
      </div>
      <div class="valores">
        <div class="atual">${fmt(i.valorAtual)}</div>
        <div class="aplicado">aplicado ${fmt(i.valorAplicado)}</div>
        <div class="${g >= 0 ? 'ganho' : 'perda'}">${fmt(g)} (${pctFmt(gPct)})</div>
      </div>
      <div class="acoes">
        <button class="btn-sm btn-verde" data-acao-inv="valor" data-id="${i.id}" title="Atualizar valor atual"><span class="ico ico-repeat"></span></button>
        <button class="btn-sm btn-secundario" data-acao-inv="editar" data-id="${i.id}" title="Editar"><span class="ico ico-pencil"></span></button>
        <button class="btn-sm btn-perigo" data-acao-inv="excluir" data-id="${i.id}" title="Excluir"><span class="ico ico-x"></span></button>
      </div>
    </div>`;
  }).join('');
}

// ── Inicialização ──
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('.aba-metas').forEach(b =>
    b.addEventListener('click', () => abrirAbaMetas(b.getAttribute('data-aba'))));
  abrirAbaMetas(location.hash.slice(1));
  window.addEventListener('hashchange', () => abrirAbaMetas(location.hash.slice(1)));

  // Metas
  document.getElementById('btnCriarMeta').addEventListener('click', async () => {
    await adicionarMeta();
    preencherSelectMetas();
  });

  // Orçamento do mês
  document.getElementById('orcCat').innerHTML = CATEGORIAS_DESPESA.map(c => `<option>${c}</option>`).join('');
  const mesAtual = new Date().toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  document.getElementById('labelMesOrc').textContent = 'Referente a ' + mesAtual.charAt(0).toUpperCase() + mesAtual.slice(1);
  document.getElementById('btnCopiarMes').addEventListener('click', usarOrcamentoMesAnterior);
  document.getElementById('btnSalvarOrcamento').addEventListener('click', adicionarOrcamento);

  // Investimentos
  document.getElementById('invTipo').innerHTML = TIPOS_INVESTIMENTO.map(t => `<option>${t}</option>`).join('');
  document.getElementById('invTipo').value = 'CDB';
  document.getElementById('btnSalvarInvestimento').addEventListener('click', salvarInvestimento);
  document.getElementById('btnCancelarInvestimento').addEventListener('click', limparFormInvestimento);
  document.getElementById('resumoInvestimentos').addEventListener('click', e => {
    if (e.target.closest('#btnSalvarCdi')) salvarCdi();
  });
  document.getElementById('resumoInvestimentos').addEventListener('keydown', e => {
    if (e.target.id === 'invCdi' && e.key === 'Enter') salvarCdi();
  });
  document.getElementById('listaInvestimentos').addEventListener('click', e => {
    const b = e.target.closest('[data-acao-inv]');
    if (!b) return;
    const id = Number(b.getAttribute('data-id'));
    const acao = b.getAttribute('data-acao-inv');
    if (acao === 'valor') atualizarValorInvestimento(id);
    if (acao === 'editar') editarInvestimento(id);
    if (acao === 'excluir') excluirInvestimento(id);
  });

  document.addEventListener('casacapital-dados-carregados', async () => {
    preencherSelectMetas();
    try {
      await carregarInvestimentos();
    } catch (e) {
      console.error('Falha ao carregar os investimentos:', e);
    }
    renderOrcamentos();
    renderMetas(); // agora com os investimentos ligados a cada meta
  });
});
