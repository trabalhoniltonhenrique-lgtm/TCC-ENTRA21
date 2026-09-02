// ── CasaCapital — contas.js (backend Java + MySQL) ──
let contas      = [];
let filtroAtivo = 'todas';
let editandoId  = null;
let gruposExpandidos = {}; // { grupoParcelaId: true/false }

const hoje = () => new Date().toISOString().split('T')[0];
let _moedaContas = 'R$';
let _sepContas    = ',';
const fmt  = v => {
  const abs = Number(v || 0).toFixed(2);
  return _moedaContas + ' ' + (_sepContas === ',' ? abs.replace('.', ',') : abs);
};
const fmtD = dt => dt ? new Date(dt + 'T12:00:00').toLocaleDateString('pt-BR') : '—';

async function carregarContas() {
  contas = await api.get('/contas');
  renderContas();
}

async function aplicarUnidadeMoedaContas() {
  try {
    const familia = await api.get('/familia/me');
    _moedaContas = familia.moeda || 'R$';
    _sepContas   = familia.separadorDecimal || ',';
    document.querySelectorAll('[data-moeda-label]').forEach(elx => {
      elx.textContent = elx.getAttribute('data-moeda-label').replace('{moeda}', _moedaContas);
    });
  } catch { /* mantém o padrão em R$ se a chamada falhar */ }
}

function calcStatus(c) {
  if (c.pago) return 'pago';
  if (c.vencimento < hoje()) return 'atrasado';
  if (c.vencimento === hoje()) return 'hoje';
  return 'pendente';
}

function diasParaVencer(venc) {
  const diff = Math.ceil((new Date(venc + 'T12:00:00') - new Date()) / (1000*60*60*24));
  return diff;
}

function avisoVencimento(c) {
  if (c.pago) return '';
  const dias = diasParaVencer(c.vencimento);
  if (dias < 0)  return `<div class="vencimento-aviso aviso-atrasado">⚠️ Atrasada há ${Math.abs(dias)} dia${Math.abs(dias)>1?'s':''}</div>`;
  if (dias === 0) return `<div class="vencimento-aviso aviso-hoje">📅 Vence hoje!</div>`;
  if (dias <= 5)  return `<div class="vencimento-aviso aviso-breve">⏰ Vence em ${dias} dia${dias>1?'s':''}</div>`;
  return '';
}

function mostrarToast(texto) {
  const msg = document.createElement('div');
  msg.textContent = texto;
  msg.className = 'toast-sucesso';
  document.body.appendChild(msg);
  setTimeout(() => msg.remove(), 3000);
}

function abrirForm(id) {
  editandoId = id ?? null;
  const form = document.getElementById('formulario');
  form.classList.remove('oculto');
  form.scrollIntoView({ behavior:'smooth', block:'start' });

  document.getElementById('campoQtdParcelas').classList.add('oculto');
  document.getElementById('campoValorBase').classList.add('oculto');
  document.getElementById('previewParcelas').classList.add('oculto');
  document.getElementById('previewParcelas').innerHTML = '';

  const selRecorr = document.getElementById('fRecorr');

  if (id !== undefined && id !== null) {
    const c = contas.find(x => x.id === id);
    document.getElementById('tituloForm').textContent = c.grupoParcelaId
      ? `Editar Parcela (${c.parcelaNum}/${c.parcelaTotal})`
      : 'Editar Conta';
    document.getElementById('fDesc').value   = c.descricao   || '';
    document.getElementById('fCat').value    = c.categoria   || 'Outros';
    document.getElementById('fValor').value  = c.valor       || '';
    document.getElementById('fVenc').value   = c.vencimento  || '';
    document.getElementById('fObs').value    = c.obs         || '';

    if (c.grupoParcelaId) {
      if (!Array.from(selRecorr.options).some(o => o.value === 'parcelada')) {
        const opt = document.createElement('option');
        opt.value = 'parcelada'; opt.textContent = 'Parcelada (cartão)';
        selRecorr.appendChild(opt);
      }
      selRecorr.value = 'parcelada';
      selRecorr.disabled = true;
    } else {
      selRecorr.disabled = false;
      selRecorr.value = RECORR_UI[c.recorrencia] || 'unica';
    }
  } else {
    document.getElementById('tituloForm').textContent = 'Nova Conta';
    document.getElementById('fDesc').value   = '';
    document.getElementById('fValor').value  = '';
    document.getElementById('fVenc').value   = '';
    selRecorr.disabled = false;
    selRecorr.value = 'unica';
    document.getElementById('fObs').value    = '';
  }
}

function fecharForm() {
  document.getElementById('formulario').classList.add('oculto');
  document.getElementById('fRecorr').disabled = false;
  editandoId = null;
}

// ── Controle de campos do formulário de parcelamento ──
function toggleCamposParcela() {
  const recorr = document.getElementById('fRecorr').value;
  const ehParcelada = recorr === 'parcelada';
  document.getElementById('campoQtdParcelas').classList.toggle('oculto', !ehParcelada);
  document.getElementById('campoValorBase').classList.toggle('oculto', !ehParcelada);
  document.getElementById('previewParcelas').classList.toggle('oculto', !ehParcelada);
  if (ehParcelada) atualizarPreviewParcelas();
}

function atualizarPreviewParcelas() {
  const recorr = document.getElementById('fRecorr').value;
  if (recorr !== 'parcelada') return;
  const valor = parseFloat(document.getElementById('fValor').value || 0);
  const qtd   = parseInt(document.getElementById('fQtdParcelas').value || 0);
  const tipo  = document.getElementById('fTipoValorParcela').value;
  const prev  = document.getElementById('previewParcelas');
  if (!valor || !qtd) { prev.innerHTML = ''; return; }

  const valorParcela = tipo === 'total' ? valor / qtd : valor;
  const valorTotal    = tipo === 'total' ? valor : valor * qtd;
  prev.innerHTML = `📅 Serão geradas <strong>${qtd} parcelas de ${fmt(valorParcela)}</strong>, totalizando <strong>${fmt(valorTotal)}</strong>, uma por mês a partir do vencimento informado.`;
}

async function salvarConta() {
  const desc   = document.getElementById('fDesc').value.trim();
  const valor  = parseFloat(document.getElementById('fValor').value || 0);
  const venc   = document.getElementById('fVenc').value;
  const recorr = document.getElementById('fRecorr').value;
  if (!desc || !valor || !venc) { alert('Preencha descrição, valor e vencimento.'); return; }

  const cat = document.getElementById('fCat').value;
  const obs = document.getElementById('fObs').value.trim();

  // ── Edição de conta existente (não gera novas parcelas) ──
  if (editandoId !== null) {
    const atualizada = await api.put('/contas/' + editandoId, {
      descricao: desc, categoria: cat, valor, vencimento: venc, recorrencia: RECORR_API[recorr], obs,
    });
    const idx = contas.findIndex(x => x.id === editandoId);
    if (idx >= 0) contas[idx] = atualizada;
    fecharForm();
    renderContas();
    return;
  }

  // ── Nova conta parcelada ──
  if (recorr === 'parcelada') {
    const qtd  = parseInt(document.getElementById('fQtdParcelas').value || 0);
    const tipo = document.getElementById('fTipoValorParcela').value;
    if (!qtd || qtd < 2) { alert('Informe um número de parcelas válido (mínimo 2).'); return; }

    const geradas = await api.post('/contas', {
      descricao: desc, categoria: cat, valor, vencimento: venc, recorrencia: 'PARCELADA', obs,
      qtdParcelas: qtd, tipoValorParcela: tipo,
    });
    contas.push(...geradas);
    fecharForm();
    renderContas();
    mostrarToast(`✔ ${qtd} parcelas de "${desc}" criadas com sucesso!`);
    return;
  }

  // ── Conta única, mensal ou anual ──
  const [nova] = await api.post('/contas', {
    descricao: desc, categoria: cat, valor, vencimento: venc, recorrencia: RECORR_API[recorr], obs,
  });
  contas.push(nova);
  fecharForm();
  renderContas();
}

async function marcarPago(id) {
  const c = contas.find(x => x.id === id);
  if (!c) return;
  await api.post(`/contas/${id}/marcar-pago`);
  await carregarContas(); // recarrega para refletir eventual próxima ocorrência gerada
  mostrarToast(`✔ "${c.descricao}" marcada como paga e lançada nas Despesas!`);
}

async function reabrirConta(id) {
  const atualizada = await api.post(`/contas/${id}/reabrir`);
  const idx = contas.findIndex(x => x.id === id);
  if (idx >= 0) contas[idx] = atualizada;
  renderContas();
}

async function excluirConta(id) {
  const c = contas.find(x => x.id === id);
  if (!c) return;

  let apagarGrupo = false;
  if (c.grupoParcelaId) {
    const futurasNaoPagas = contas.filter(x =>
      x.grupoParcelaId === c.grupoParcelaId && !x.pago && x.vencimento >= c.vencimento
    );
    if (futurasNaoPagas.length > 1) {
      apagarGrupo = confirm(
        `Esta parcela faz parte de "${c.descricao.replace(/\s*\(\d+\/\d+\)$/, '')}".\n\n` +
        `Clique OK para cancelar TODAS as ${futurasNaoPagas.length} parcelas futuras não pagas deste grupo.\n` +
        `Clique Cancelar para excluir apenas esta parcela.`
      );
    }
  }

  if (!apagarGrupo && !confirm(`Excluir "${c.descricao}"?`)) return;

  await api.del(`/contas/${id}?apagarGrupo=${apagarGrupo}`);
  await carregarContas();
}

function setFiltro(f, elTab) {
  filtroAtivo = f;
  document.querySelectorAll('.filtro-tab').forEach(t => t.classList.remove('ativo'));
  elTab.classList.add('ativo');
  renderContas();
}

function toggleGrupoParcela(grupoId) {
  gruposExpandidos[grupoId] = !gruposExpandidos[grupoId];
  renderContas();
}

function renderItemConta(c) {
  const st     = calcStatus(c);
  const tagTxt = st === 'hoje' ? 'Vence Hoje' : st === 'atrasado' ? 'Atrasada' : st === 'pago' ? 'Pago' : 'Pendente';
  const tagCls = st === 'hoje' ? 'hoje' : st;
  const recorrIcon = c.recorrencia === 'MENSAL' ? ' 🔄' : c.recorrencia === 'ANUAL' ? ' 📆' : c.grupoParcelaId ? ' 💳' : '';
  const corValor = st === 'pago' ? 'cor-pago' : st === 'atrasado' ? 'cor-atrasado' : 'cor-normal';
  return `
    <div class="conta-card ${st === 'hoje' ? 'pendente' : st}">
      <div class="conta-info">
        <strong>${c.descricao}${recorrIcon}</strong>
        <span>${c.categoria} · Venc: ${fmtD(c.vencimento)}${c.obs ? ' · ' + c.obs : ''}</span>
        ${avisoVencimento(c)}
        ${c.pago && c.dataPagamento ? `<div class="pago-em">✔ Pago em ${fmtD(c.dataPagamento)}</div>` : ''}
      </div>
      <span class="conta-valor ${corValor}">${fmt(c.valor)}</span>
      <span class="tag ${tagCls}">${tagTxt}</span>
      <div class="conta-acoes">
        ${!c.pago
          ? `<button class="btn-sm btn-verde" data-action="marcar-pago" data-id="${c.id}" title="Marcar como pago">✔</button>`
          : `<button class="btn-sm btn-secundario" data-action="reabrir" data-id="${c.id}" title="Reabrir">↩</button>`}
        <button class="btn-sm btn-secundario" data-action="editar" data-id="${c.id}" title="Editar">✏️</button>
        <button class="btn-sm btn-perigo" data-action="excluir" data-id="${c.id}" title="Excluir">✕</button>
      </div>
    </div>`;
}

function renderGrupoParcela(grupoId, parcelas) {
  const nomeBase   = parcelas[0].descricao.replace(/\s*\(\d+\/\d+\)$/, '');
  const cat         = parcelas[0].categoria;
  const pagas        = parcelas.filter(p => p.pago);
  const naoPagas      = parcelas.filter(p => !p.pago);
  const proxima       = [...naoPagas].sort((a,b) => a.vencimento.localeCompare(b.vencimento))[0];
  const valorRestante = naoPagas.reduce((a,p) => a + p.valor, 0);
  const pct            = (pagas.length / parcelas.length) * 100;
  const temAtrasada    = naoPagas.some(p => p.vencimento < hoje());
  const expandido      = !!gruposExpandidos[grupoId];

  const corBarra = temAtrasada ? 'cor-atrasada' : pct === 100 ? 'cor-completa' : 'cor-andamento';

  return `
    <div class="conta-card grupo ${temAtrasada ? 'atrasado-grupo' : 'normal-grupo'}"
         data-action="toggle-grupo" data-id="${grupoId}">
      <div class="grupo-linha-topo">
        <div class="conta-info">
          <strong>💳 ${nomeBase}</strong>
          <span>${cat} · ${pagas.length}/${parcelas.length} parcelas pagas
            ${proxima ? ` · Próxima: ${fmtD(proxima.vencimento)}` : ' · Concluído'}
          </span>
          ${temAtrasada ? `<div class="vencimento-aviso aviso-atrasado">⚠️ Há parcela(s) atrasada(s)</div>` : ''}
        </div>
        <div class="grupo-restante">
          <div class="rotulo">Restante</div>
          <div class="valor">${fmt(valorRestante)}</div>
        </div>
        <span class="grupo-seta ${expandido ? 'expandido' : ''}">▾</span>
      </div>

      <div class="grupo-barra-fundo">
        <div class="grupo-barra-progresso ${corBarra}" style="--largura:${pct}%;"></div>
      </div>

      ${expandido ? `
        <div class="grupo-detalhes" data-action="stop-propagation">
          ${[...parcelas].sort((a,b) => a.vencimento.localeCompare(b.vencimento)).map(p => {
            const stP = calcStatus(p);
            const tagTxtP = stP === 'hoje' ? 'Vence Hoje' : stP === 'atrasado' ? 'Atrasada' : stP === 'pago' ? 'Pago' : 'Pendente';
            const corValorP = stP === 'pago' ? 'cor-pago' : 'cor-normal';
            return `
            <div class="parcela-linha">
              <span class="desc">Parcela ${p.parcelaNum}/${p.parcelaTotal} <span class="data">· ${fmtD(p.vencimento)}</span></span>
              <span class="tag com-margem ${stP === 'hoje' ? 'hoje' : stP}">${tagTxtP}</span>
              <span class="valor ${corValorP}">${fmt(p.valor)}</span>
              ${!p.pago
                ? `<button class="btn-sm btn-verde" data-action="marcar-pago" data-id="${p.id}" title="Marcar como pago">✔</button>`
                : `<button class="btn-sm btn-secundario" data-action="reabrir" data-id="${p.id}" title="Reabrir">↩</button>`}
              <button class="btn-sm btn-secundario" data-action="editar" data-id="${p.id}" title="Editar">✏️</button>
              <button class="btn-sm btn-perigo" data-action="excluir" data-id="${p.id}" title="Excluir">✕</button>
            </div>`;
          }).join('')}
        </div>
      ` : ''}
    </div>`;
}

function renderContas() {
  const lista  = document.getElementById('listaContas');
  const resumo = document.getElementById('resumoContas');

  const total      = contas.reduce((a, c) => a + (c.pago ? 0 : c.valor), 0);
  const atrasadas  = contas.filter(c => calcStatus(c) === 'atrasado');
  const pagas      = contas.filter(c => c.pago);
  const totalMes   = contas.filter(c => c.vencimento?.startsWith(hoje().slice(0,7)))
                           .reduce((a, c) => a + c.valor, 0);
  const comprometidoParcelas = contas.filter(c => c.grupoParcelaId && !c.pago)
                                      .reduce((a, c) => a + c.valor, 0);

  resumo.innerHTML = `
    <div class="resumo-box cor-saldo">
      <div class="num">${fmt(total)}</div>
      <div class="txt">Total em aberto</div>
    </div>
    <div class="resumo-box cor-mes">
      <div class="num">${fmt(totalMes)}</div>
      <div class="txt">Vencimentos este mês</div>
    </div>
    <div class="resumo-box cor-atraso">
      <div class="num">${atrasadas.length}</div>
      <div class="txt">Atrasada${atrasadas.length!==1?'s':''}</div>
    </div>
    <div class="resumo-box cor-pago">
      <div class="num">${pagas.length}</div>
      <div class="txt">Paga${pagas.length!==1?'s':''} este mês</div>
    </div>
    ${comprometidoParcelas ? `
    <div class="resumo-box cor-parcela">
      <div class="num">${fmt(comprometidoParcelas)}</div>
      <div class="txt">💳 Comprometido em parcelas</div>
    </div>` : ''}
  `;

  let lista2 = [...contas].sort((a, b) => a.vencimento?.localeCompare(b.vencimento));
  if (filtroAtivo === 'pendente') lista2 = lista2.filter(c => calcStatus(c) === 'pendente');
  if (filtroAtivo === 'atrasado') lista2 = lista2.filter(c => calcStatus(c) === 'atrasado');
  if (filtroAtivo === 'hoje')     lista2 = lista2.filter(c => calcStatus(c) === 'hoje');
  if (filtroAtivo === 'pago')     lista2 = lista2.filter(c => c.pago);

  if (!lista2.length) {
    lista.innerHTML = `<div class="lista-vazia">
      <div class="icone">📋</div>
      <p>Nenhuma conta encontrada.</p>
    </div>`;
    return;
  }

  const grupos = {};
  const soltas  = [];
  lista2.forEach(c => {
    if (c.grupoParcelaId) {
      if (!grupos[c.grupoParcelaId]) grupos[c.grupoParcelaId] = [];
      grupos[c.grupoParcelaId].push(c);
    } else {
      soltas.push(c);
    }
  });

  const blocos = [
    ...soltas.map(c => ({ tipo: 'solta', ref: c.vencimento, render: () => renderItemConta(c) })),
    ...Object.entries(grupos).map(([gid, parcelas]) => {
      const refData = [...parcelas].sort((a,b) => a.vencimento.localeCompare(b.vencimento))[0].vencimento;
      return { tipo: 'grupo', ref: refData, render: () => renderGrupoParcela(gid, parcelas) };
    })
  ].sort((a, b) => a.ref.localeCompare(b.ref));

  lista.innerHTML = blocos.map(b => b.render()).join('');
}

// ── Listeners estáticos (elementos fixos do formulário/cabeçalho) ──
document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('btnNovaConta').addEventListener('click', () => abrirForm());
  document.getElementById('btnSalvarConta').addEventListener('click', salvarConta);
  document.getElementById('btnCancelarConta').addEventListener('click', fecharForm);
  document.getElementById('fValor').addEventListener('input', atualizarPreviewParcelas);
  document.getElementById('fRecorr').addEventListener('change', toggleCamposParcela);
  document.getElementById('fQtdParcelas').addEventListener('input', atualizarPreviewParcelas);
  document.getElementById('fTipoValorParcela').addEventListener('change', atualizarPreviewParcelas);

  document.querySelectorAll('.filtro-tab').forEach(tab => {
    tab.addEventListener('click', () => setFiltro(tab.getAttribute('data-filtro'), tab));
  });

  document.getElementById('listaContas').addEventListener('click', function (e) {
    const acaoEl = e.target.closest('[data-action]');
    if (!acaoEl) return;
    const acao = acaoEl.getAttribute('data-action');
    const id   = acaoEl.getAttribute('data-id');

    if (acao === 'stop-propagation') { e.stopPropagation(); return; }
    if (acao !== 'toggle-grupo') e.stopPropagation();

    if (acao === 'marcar-pago')  marcarPago(Number(id));
    if (acao === 'reabrir')      reabrirConta(Number(id));
    if (acao === 'editar')       abrirForm(Number(id));
    if (acao === 'excluir')      excluirConta(Number(id));
    if (acao === 'toggle-grupo') toggleGrupoParcela(Number(id));
  });

  aplicarUnidadeMoedaContas().then(carregarContas);
});
