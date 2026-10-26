// ── CasaCapital — metas.js: Metas (objetivos), Orçamento do mês e Investimentos (carteira) ──
// Metas e Orçamento usam as funções de app.js (renderMetas, renderOrcamentos, adicionarMeta…).

const TIPOS_INVESTIMENTO = ['Poupança', 'CDB', 'LCI / LCA', 'Tesouro Direto', 'Fundos', 'Ações', 'FIIs', 'ETFs',
  'Previdência', 'Criptomoedas', 'Outros'];
const ABAS_METAS = ['metas', 'orcamento', 'investimentos'];

let investimentos = [];      // carteira da família (GET /investimentos)
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

// ── Investimentos ──
async function carregarInvestimentos() {
  investimentos = await api.get('/investimentos');
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
  ['invNome', 'invInstituicao', 'invAplicado', 'invAtual', 'invDataAplicacao', 'invVencimento', 'invObs']
    .forEach(id => { document.getElementById(id).value = ''; });
  document.getElementById('invTipo').value = 'CDB';
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

  resumo.innerHTML = `
    <div class="resumo-topo">
      <div class="resumo-box cor-saldo"><div class="num">${fmt(atual)}</div><div class="txt">Valor atual da carteira</div></div>
      <div class="resumo-box cor-mes"><div class="num">${fmt(aplicado)}</div><div class="txt">Total aplicado</div></div>
      <div class="resumo-box ${ganho >= 0 ? 'cor-pago' : 'cor-atraso'}"><div class="num">${fmt(ganho)}</div>
        <div class="txt">${ganho >= 0 ? 'Rendimento' : 'Perda'} (${pctFmt(ganhoPct)})</div></div>
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
    return `<div class="card-investimento">
      <div class="info">
        <strong>${escM(i.nome)} <span class="tag-tipo-inv">${escM(i.tipo)}</span></strong>
        ${detalhes ? `<span>${detalhes}</span>` : ''}
        ${i.metaNome ? `<span class="meta-ligada"><span class="ico ico-trophy"></span> Meta: ${escM(i.metaNome)}</span>` : ''}
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
