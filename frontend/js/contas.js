// ── CasaCapital — contas.js (backend Java + MySQL) ──
let contas      = [];
let filtroAtivo = null; // null = lista recolhida; clicar numa aba expande, clicar de novo recolhe
let filtroCategoriaAtivo = 'todas';
let editandoId  = null;
let gruposExpandidos = {}; // { grupoParcelaId: true/false }
let filtroCartaoAtivo = 'todos'; // na aba Cartões: 'todos' ou o nome do cartão
const selecionadas = new Set();  // ids de contas em aberto marcadas para pagar em lote

const SEM_CARTAO = 'Cartão não informado';
const ehDeCartao = c => !!c.cartao || c.categoria === 'Cartão de Crédito';
const nomeCartao = c => c.cartao || SEM_CARTAO;
const escC = s => String(s ?? '').replace(/[&<>"']/g, ch => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[ch]));

const hoje = () => new Date().toLocaleDateString('sv-SE'); // AAAA-MM-DD no fuso local (não UTC)
let _moedaContas = 'R$';
let _sepContas    = ',';
const fmt  = v => {
  const [inteiro, dec] = Math.abs(Number(v) || 0).toFixed(2).split('.');
  const milhar = _sepContas === ',' ? '.' : ',';
  const numero = inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, milhar) + (_sepContas === ',' ? ',' : '.') + dec;
  return (v < 0 ? '-' : '') + _moedaContas + ' ' + numero;
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
  if (dias < 0)  return `<div class="vencimento-aviso aviso-atrasado"><span class="ico ico-alert-triangle"></span> Atrasada há ${Math.abs(dias)} dia${Math.abs(dias)>1?'s':''}</div>`;
  if (dias === 0) return `<div class="vencimento-aviso aviso-hoje"><span class="ico ico-calendar"></span> Vence hoje!</div>`;
  if (dias <= 5)  return `<div class="vencimento-aviso aviso-breve"><span class="ico ico-clock"></span> Vence em ${dias} dia${dias>1?'s':''}</div>`;
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
    document.getElementById('fCartao').value = c.cartao      || '';

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
    document.getElementById('fCartao').value = '';
  }
  atualizarListaCartoes();
}

// Sugestões do campo Cartão: os já usados + os bancos mais comuns
function atualizarListaCartoes() {
  const nomes = new Set(['Itaú', 'Nubank', 'Inter', 'Caixa']);
  contas.forEach(c => { if (c.cartao) nomes.add(c.cartao); });
  document.getElementById('listaCartoes').innerHTML =
    [...nomes].sort((a, b) => a.localeCompare(b)).map(n => `<option value="${escC(n)}">`).join('');
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

  // Pelo total, o servidor arredonda as parcelas para baixo e a última leva os centavos que sobram (R$ 100 em 3x = 33,33 + 33,33 + 33,34)
  const centavos     = Math.round(valor * 100); // 1.15 * 100 = 114.999... no JavaScript
  const valorParcela = tipo === 'total' ? Math.floor(centavos / qtd) / 100 : valor;
  const valorUltima  = tipo === 'total' ? (centavos - Math.floor(centavos / qtd) * (qtd - 1)) / 100 : valor;
  const valorTotal   = tipo === 'total' ? valor : valor * qtd;
  const parcelas = valorUltima === valorParcela
    ? `${qtd} parcelas de ${fmt(valorParcela)}`
    : `${qtd - 1} parcelas de ${fmt(valorParcela)} e a última de ${fmt(valorUltima)}`;
  prev.innerHTML = `<span class="ico ico-calendar"></span> Serão geradas <strong>${parcelas}</strong>, totalizando <strong>${fmt(valorTotal)}</strong>, uma por mês a partir do vencimento informado.`;
}

async function salvarConta() {
  const desc   = document.getElementById('fDesc').value.trim();
  const valor  = parseFloat(document.getElementById('fValor').value || 0);
  const venc   = document.getElementById('fVenc').value;
  const recorr = document.getElementById('fRecorr').value;
  if (!desc || !valor || !venc) { alert('Preencha descrição, valor e vencimento.'); return; }

  const cat = document.getElementById('fCat').value;
  const obs = document.getElementById('fObs').value.trim();
  const cartao = document.getElementById('fCartao').value.trim();

  // ── Edição de conta existente (não gera novas parcelas) ──
  if (editandoId !== null) {
    const atualizada = await api.put('/contas/' + editandoId, {
      descricao: desc, categoria: cat, valor, vencimento: venc, recorrencia: RECORR_API[recorr], obs, cartao,
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
      descricao: desc, categoria: cat, valor, vencimento: venc, recorrencia: 'PARCELADA', obs, cartao,
      qtdParcelas: qtd, tipoValorParcela: tipo,
    });
    contas.push(...geradas);
    fecharForm();
    renderContas();
    mostrarToast(`${qtd} parcelas de "${desc}" criadas com sucesso!`);
    return;
  }

  // ── Conta única, mensal ou anual ──
  const [nova] = await api.post('/contas', {
    descricao: desc, categoria: cat, valor, vencimento: venc, recorrencia: RECORR_API[recorr], obs, cartao,
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
  mostrarToast(`"${c.descricao}" marcada como paga e lançada nas Despesas!`);
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

function setFiltro(f) {
  filtroAtivo = filtroAtivo === f ? null : f;
  filtroCartaoAtivo = 'todos';
  selecionadas.clear(); // não pagar contas que deixaram de aparecer na lista
  renderContas();
}

// Abas de status: marca a expandida e mostra quantas contas há em cada uma (respeitando a categoria)
function atualizarAbasFiltro() {
  const daCategoria = filtroCategoriaAtivo === 'todas' ? contas : contas.filter(c => c.categoria === filtroCategoriaAtivo);
  const contagem = {
    todas:    daCategoria.length,
    pendente: daCategoria.filter(c => calcStatus(c) === 'pendente').length,
    atrasado: daCategoria.filter(c => calcStatus(c) === 'atrasado').length,
    hoje:     daCategoria.filter(c => calcStatus(c) === 'hoje').length,
    pago:     daCategoria.filter(c => c.pago).length,
    cartoes:  daCategoria.filter(ehDeCartao).length,
  };
  document.querySelectorAll('.bloco-filtros-contas .filtro-tab').forEach(tab => {
    const f = tab.getAttribute('data-filtro');
    const aberta = f === filtroAtivo;
    tab.classList.toggle('ativo', aberta);
    tab.setAttribute('aria-expanded', aberta);
    tab.title = aberta ? 'Clique para recolher' : 'Clique para expandir';
    let cont = tab.querySelector('.filtro-tab-contagem');
    if (!cont) {
      cont = document.createElement('span');
      cont.className = 'filtro-tab-contagem';
      tab.appendChild(cont);
    }
    cont.textContent = contagem[f];
  });
}

function setFiltroCategoria(cat) {
  filtroCategoriaAtivo = cat;
  selecionadas.clear();
  renderContas();
}

function setFiltroCartao(nome) {
  filtroCartaoAtivo = nome;
  selecionadas.clear();
  renderContas();
}

// ── Pagamento em lote ──
const caixaSelecao = (c, rotulo) => `<label class="sel-conta" title="Selecionar para pagar em lote" data-action="rotulo-selecao">
    <input type="checkbox" data-action="selecionar" data-id="${c.id}" ${selecionadas.has(c.id) ? 'checked' : ''}
      aria-label="Selecionar ${escC(c.descricao)}">${rotulo ? `<span>${rotulo}</span>` : ''}</label>`;

// Opções do lote, na linha "Selecionar todas": só aparecem quando há contas selecionadas
function opcoesLote() {
  const lista = contas.filter(c => selecionadas.has(c.id) && !c.pago);
  if (!lista.length) return '';
  const total = lista.reduce((a, c) => a + c.valor, 0);
  return `<div class="lote-opcoes">
      <span><strong>${lista.length}</strong> selecionada${lista.length !== 1 ? 's' : ''} · <strong>${fmt(total)}</strong></span>
      <button class="btn-verde btn-lote" data-action="pagar-lote"><span class="ico ico-check"></span> Marcar como pagas</button>
      <button class="btn-secundario btn-lote" data-action="limpar-lote">Limpar seleção</button>
    </div>`;
}

async function pagarSelecionadas() {
  const lista = contas.filter(c => selecionadas.has(c.id) && !c.pago);
  if (!lista.length) return;
  const total = lista.reduce((a, c) => a + c.valor, 0);
  if (!confirm(`Marcar ${lista.length} conta(s) como paga(s), somando ${fmt(total)}?\n\nCada uma será lançada nas Despesas.`)) return;
  const btn = document.querySelector('[data-action="pagar-lote"]');
  if (btn) btn.disabled = true;
  try {
    const pagas = await api.post('/contas/marcar-pago', { ids: lista.map(c => c.id) });
    selecionadas.clear();
    await carregarContas();
    mostrarToast(`${pagas.length} conta(s) marcada(s) como paga(s) e lançada(s) nas Despesas!`);
  } catch (e) {
    alert(e.message);
    if (btn) btn.disabled = false;
  }
}

function toggleGrupoParcela(grupoId) {
  gruposExpandidos[grupoId] = !gruposExpandidos[grupoId];
  renderContas();
}

function renderItemConta(c) {
  const st     = calcStatus(c);
  const tagTxt = st === 'hoje' ? 'Vence Hoje' : st === 'atrasado' ? 'Atrasada' : st === 'pago' ? 'Pago' : 'Pendente';
  const tagCls = st === 'hoje' ? 'hoje' : st;
  const recorrIcon = c.recorrencia === 'MENSAL' ? ' <span class="ico ico-repeat"></span>' : c.recorrencia === 'ANUAL' ? ' <span class="ico ico-calendar-days"></span>' : c.grupoParcelaId ? ' <span class="ico ico-credit-card"></span>' : '';
  const corValor = st === 'pago' ? 'cor-pago' : st === 'atrasado' ? 'cor-atrasado' : 'cor-normal';
  return `
    <div class="conta-card ${st === 'hoje' ? 'pendente' : st}${selecionadas.has(c.id) ? ' selecionada' : ''}">
      ${!c.pago ? caixaSelecao(c) : '<span class="sel-conta-vazio"></span>'}
      <div class="conta-info">
        <strong>${c.descricao}${recorrIcon}</strong>
        <span>${c.categoria}${c.cartao ? ' · ' + escC(c.cartao) : ''} · Venc: ${fmtD(c.vencimento)}${c.obs ? ' · ' + c.obs : ''}</span>
        ${avisoVencimento(c)}
        ${c.pago && c.dataPagamento ? `<div class="pago-em"><span class="ico ico-check"></span> Pago em ${fmtD(c.dataPagamento)}</div>` : ''}
      </div>
      <span class="conta-valor ${corValor}">${fmt(c.valor)}</span>
      <span class="tag ${tagCls}">${tagTxt}</span>
      <div class="conta-acoes">
        ${!c.pago
          ? `<button class="btn-sm btn-verde" data-action="marcar-pago" data-id="${c.id}" title="Marcar como pago"><span class="ico ico-check"></span></button>`
          : `<button class="btn-sm btn-secundario" data-action="reabrir" data-id="${c.id}" title="Reabrir">↩</button>`}
        <button class="btn-sm btn-secundario" data-action="editar" data-id="${c.id}" title="Editar"><span class="ico ico-pencil"></span></button>
        <button class="btn-sm btn-perigo" data-action="excluir" data-id="${c.id}" title="Excluir"><span class="ico ico-x"></span></button>
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
        ${proxima ? caixaSelecao(proxima, 'próx.') : '<span class="sel-conta-vazio"></span>'}
        <div class="conta-info">
          <strong><span class="ico ico-credit-card"></span> ${nomeBase}</strong>
          <span>${cat}${parcelas[0].cartao ? ' · ' + escC(parcelas[0].cartao) : ''} · ${pagas.length}/${parcelas.length} parcelas pagas
            ${proxima ? ` · Próxima: ${fmtD(proxima.vencimento)}` : ' · Concluído'}
          </span>
          ${temAtrasada ? `<div class="vencimento-aviso aviso-atrasado"><span class="ico ico-alert-triangle"></span> Há parcela(s) atrasada(s)</div>` : ''}
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
              ${!p.pago ? caixaSelecao(p) : '<span class="sel-conta-vazio"></span>'}
              <span class="desc">Parcela ${p.parcelaNum}/${p.parcelaTotal} <span class="data">· ${fmtD(p.vencimento)}</span></span>
              <span class="tag com-margem ${stP === 'hoje' ? 'hoje' : stP}">${tagTxtP}</span>
              <span class="valor ${corValorP}">${fmt(p.valor)}</span>
              ${!p.pago
                ? `<button class="btn-sm btn-verde" data-action="marcar-pago" data-id="${p.id}" title="Marcar como pago"><span class="ico ico-check"></span></button>`
                : `<button class="btn-sm btn-secundario" data-action="reabrir" data-id="${p.id}" title="Reabrir">↩</button>`}
              <button class="btn-sm btn-secundario" data-action="editar" data-id="${p.id}" title="Editar"><span class="ico ico-pencil"></span></button>
              <button class="btn-sm btn-perigo" data-action="excluir" data-id="${p.id}" title="Excluir"><span class="ico ico-x"></span></button>
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
      <div class="txt"><span class="ico ico-credit-card"></span> Comprometido em parcelas</div>
    </div>` : ''}
  `;

  atualizarAbasFiltro();
  if (!filtroAtivo) {
    lista.innerHTML = `<div class="contas-recolhidas">
      Selecione uma opção acima para ver as contas.
    </div>`;
    return;
  }

  let lista2 = [...contas].sort((a, b) => a.vencimento?.localeCompare(b.vencimento));
  if (filtroAtivo === 'pendente') lista2 = lista2.filter(c => calcStatus(c) === 'pendente');
  if (filtroAtivo === 'atrasado') lista2 = lista2.filter(c => calcStatus(c) === 'atrasado');
  if (filtroAtivo === 'hoje')     lista2 = lista2.filter(c => calcStatus(c) === 'hoje');
  if (filtroAtivo === 'pago')     lista2 = lista2.filter(c => c.pago);
  if (filtroCategoriaAtivo !== 'todas') lista2 = lista2.filter(c => c.categoria === filtroCategoriaAtivo);

  // Aba Cartões: só contas de cartão, com um botão por cartão mostrando o total em aberto
  let chipsCartao = '';
  if (filtroAtivo === 'cartoes') {
    lista2 = lista2.filter(ehDeCartao);
    const porCartao = {};
    lista2.forEach(c => {
      const n = nomeCartao(c);
      porCartao[n] = porCartao[n] || { aberto: 0, qtd: 0 };
      porCartao[n].qtd++;
      if (!c.pago) porCartao[n].aberto += c.valor;
    });
    const nomes = Object.keys(porCartao).sort((a, b) => (a === SEM_CARTAO) - (b === SEM_CARTAO) || a.localeCompare(b));
    if (filtroCartaoAtivo !== 'todos' && !porCartao[filtroCartaoAtivo]) filtroCartaoAtivo = 'todos';
    const totalAberto = Object.values(porCartao).reduce((a, x) => a + x.aberto, 0);
    chipsCartao = nomes.length ? `<div class="chips-cartao" role="group" aria-label="Filtrar por cartão">
      <button class="chip-cartao ${filtroCartaoAtivo === 'todos' ? 'ativo' : ''}" data-action="filtro-cartao" data-cartao="todos">
        Todos <span>${fmt(totalAberto)} em aberto</span></button>
      ${nomes.map(n => `<button class="chip-cartao ${filtroCartaoAtivo === n ? 'ativo' : ''}" data-action="filtro-cartao" data-cartao="${escC(n)}">
        ${escC(n)} <span>${fmt(porCartao[n].aberto)} em aberto</span></button>`).join('')}
    </div>` : '';
    if (filtroCartaoAtivo !== 'todos') lista2 = lista2.filter(c => nomeCartao(c) === filtroCartaoAtivo);
  }

  if (!lista2.length) {
    lista.innerHTML = chipsCartao + `<div class="lista-vazia">
      <div class="icone"><span class="ico ico-clipboard"></span></div>
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

  // "Selecionar todas": contas soltas em aberto + a próxima parcela de cada compra parcelada
  const selecionaveis = [
    ...soltas.filter(c => !c.pago),
    ...Object.values(grupos).map(ps => ps.filter(p => !p.pago).sort((a, b) => a.vencimento.localeCompare(b.vencimento))[0]).filter(Boolean),
  ];
  const todasMarcadas = selecionaveis.length > 0 && selecionaveis.every(c => selecionadas.has(c.id));
  const barraSelecao = selecionaveis.length || selecionadas.size ? `<div class="selecionar-todas">
      ${selecionaveis.length ? `<label><input type="checkbox" data-action="selecionar-todas" data-ids="${selecionaveis.map(c => c.id).join(',')}" ${todasMarcadas ? 'checked' : ''}>
        Selecionar todas em aberto (${selecionaveis.length})</label>
      <span class="dica">Nas compras parceladas, entra só a próxima parcela.</span>` : ''}
      ${opcoesLote()}
    </div>` : '';

  lista.innerHTML = chipsCartao + barraSelecao + blocos.map(b => b.render()).join('');
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
    tab.addEventListener('click', () => setFiltro(tab.getAttribute('data-filtro')));
    tab.setAttribute('role', 'button');
    tab.setAttribute('tabindex', '0');
    tab.addEventListener('keydown', e => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setFiltro(tab.getAttribute('data-filtro')); }
    });
  });

  document.getElementById('filtroCategoria').addEventListener('change', function (e) {
    setFiltroCategoria(e.target.value);
  });

  document.getElementById('listaContas').addEventListener('click', function (e) {
    const acaoEl = e.target.closest('[data-action]');
    if (!acaoEl) return;
    const acao = acaoEl.getAttribute('data-action');
    const id   = acaoEl.getAttribute('data-id');

    if (acao === 'selecionar') {
      e.stopPropagation();
      const idSel = Number(id);
      if (acaoEl.checked) selecionadas.add(idSel); else selecionadas.delete(idSel);
      renderContas();
      return;
    }
    if (acao === 'selecionar-todas') {
      acaoEl.getAttribute('data-ids').split(',').map(Number)
        .forEach(x => acaoEl.checked ? selecionadas.add(x) : selecionadas.delete(x));
      renderContas();
      return;
    }
    if (acao === 'pagar-lote')  { pagarSelecionadas(); return; }
    if (acao === 'limpar-lote') { selecionadas.clear(); renderContas(); return; }
    if (acao === 'filtro-cartao') { setFiltroCartao(acaoEl.getAttribute('data-cartao')); return; }
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
