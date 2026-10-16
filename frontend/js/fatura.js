// ── CasaCapital — fatura.js: importa fatura em PDF — cartão (lançamentos) ou conta de luz/água (uma conta) ──
// Usa fmt, fmtD, mostrarToast e carregarContas de contas.js.

const CATEGORIAS_DESPESA_FATURA = ['Alimentação', 'Moradia', 'Transporte', 'Saúde', 'Educação',
  'Lazer', 'Vestuário', 'Contas & Serviços', 'Outros'];

let faturaLida = null;   // resposta de /faturas/analisar
let faturaItens = [];    // itens editáveis da revisão: { ...item, sel }

const escFatura = s => String(s ?? '').replace(/[&<>"']/g, ch =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));

function abrirPainelFatura() {
  document.getElementById('formulario').classList.add('oculto');
  document.getElementById('painelFatura').classList.remove('oculto');
  voltarEnvioFatura();
  document.getElementById('painelFatura').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function fecharPainelFatura() {
  document.getElementById('painelFatura').classList.add('oculto');
  faturaLida = null;
  faturaItens = [];
}

function voltarEnvioFatura() {
  document.getElementById('faturaEnvio').classList.remove('oculto');
  document.getElementById('faturaRevisao').classList.add('oculto');
  document.getElementById('faturaContaRevisao').classList.add('oculto');
  document.getElementById('fArquivoFatura').value = '';
  document.getElementById('fSenhaFatura').value = '';
  document.getElementById('campoSenhaFatura').classList.add('oculto');
  document.getElementById('btnLerFatura').disabled = false;
  statusFatura('');
}

function statusFatura(texto, tipo) {
  const el = document.getElementById('faturaStatus');
  el.textContent = texto;
  el.className = 'fatura-status' + (texto ? '' : ' oculto') + (tipo ? ' ' + tipo : '');
}

async function lerFatura() {
  const arquivo = document.getElementById('fArquivoFatura').files[0];
  if (!arquivo) { statusFatura('Selecione o arquivo da fatura.', 'erro'); return; }

  const dados = new FormData();
  dados.append('arquivo', arquivo);
  const senha = document.getElementById('fSenhaFatura').value;
  if (senha) dados.append('senha', senha);

  const btn = document.getElementById('btnLerFatura');
  btn.disabled = true;
  statusFatura('Lendo a fatura…', 'carregando');
  try {
    faturaLida = await apiRequest('POST', '/faturas/analisar', dados);
    statusFatura('');
    if (faturaLida.tipo === 'CONTA_CONSUMO') {
      mostrarContaFatura(faturaLida.conta);
      return;
    }
    faturaItens = faturaLida.itens.map(it => ({ ...it, sel: it.acao !== 'JA_IMPORTADA' }));
    mostrarRevisaoFatura();
  } catch (e) {
    if (e.codigo === 'PDF_PROTEGIDO' || e.codigo === 'SENHA_INCORRETA') {
      document.getElementById('campoSenhaFatura').classList.remove('oculto');
      document.getElementById('fSenhaFatura').focus();
    }
    statusFatura(e.message, 'erro');
  } finally {
    btn.disabled = false;
  }
}

// ── Conta de luz/água: uma conta a pagar ──

function mostrarContaFatura(conta) {
  document.getElementById('faturaEnvio').classList.add('oculto');
  document.getElementById('faturaContaRevisao').classList.remove('oculto');

  document.getElementById('fcDesc').value = conta.descricao || '';
  document.getElementById('fcCat').value = conta.categoria || 'Outros';
  document.getElementById('fcValor').value = conta.valor != null ? Number(conta.valor).toFixed(2) : '';
  document.getElementById('fcVenc').value = conta.vencimento || '';

  let aviso = `<div class="fatura-status">Conta de ${escFatura(conta.categoria.toLowerCase())} reconhecida` +
    (conta.referencia ? ` (referência ${escFatura(conta.referencia)})` : '') + '. Confira os dados abaixo.</div>';
  if (conta.jaCadastrada) {
    aviso = `<div class="fatura-status erro">Já existe a conta "${escFatura(conta.contaExistenteDescricao)}" com este valor e vencimento. Cadastrar de novo vai duplicá-la.</div>`;
  }
  if (!conta.vencimento) {
    aviso += '<div class="fatura-status erro">Não encontrei o vencimento no PDF. Preencha antes de cadastrar.</div>';
  }
  document.getElementById('faturaContaAviso').innerHTML = aviso;

  const wrap = document.getElementById('fcAtualizarWrap');
  wrap.classList.toggle('oculto', !conta.contaId);
  document.getElementById('fcAtualizar').checked = !!conta.contaId;
  document.getElementById('fcAtualizarTexto').textContent = conta.contaId
    ? `Atualizar a conta em aberto "${conta.contaExistenteDescricao}" com este valor e vencimento (em vez de criar outra)`
    : '';
  atualizarBotaoContaFatura();
}

function atualizarBotaoContaFatura() {
  const atualizar = faturaLida?.conta?.contaId && document.getElementById('fcAtualizar').checked;
  document.getElementById('btnConfirmarContaFatura').textContent = atualizar ? 'Atualizar conta' : 'Cadastrar conta';
}

async function confirmarContaFatura() {
  const conta = faturaLida.conta;
  const corpo = {
    descricao: document.getElementById('fcDesc').value.trim(),
    categoria: document.getElementById('fcCat').value,
    valor: Number(document.getElementById('fcValor').value),
    vencimento: document.getElementById('fcVenc').value,
    contaId: conta.contaId && document.getElementById('fcAtualizar').checked ? conta.contaId : null,
  };
  if (!corpo.descricao || !(corpo.valor > 0) || !corpo.vencimento) {
    alert('Preencha descrição, valor e vencimento.');
    return;
  }
  const btn = document.getElementById('btnConfirmarContaFatura');
  btn.disabled = true;
  try {
    const salva = await api.post('/faturas/importar-conta', corpo);
    mostrarToast(`${corpo.contaId ? 'Conta atualizada' : 'Conta cadastrada'}: ${salva.descricao} — ${fmt(salva.valor)}, vence ${fmtD(salva.vencimento)}`);
    fecharPainelFatura();
    await carregarContas();
  } catch (e) {
    alert(e.message);
  } finally {
    btn.disabled = false;
  }
}

// ── Fatura do cartão: vários lançamentos ──

function mostrarRevisaoFatura() {
  document.getElementById('faturaEnvio').classList.add('oculto');
  document.getElementById('faturaRevisao').classList.remove('oculto');

  if (faturaItens.length === 0) {
    document.getElementById('faturaResumo').innerHTML =
      '<div class="fatura-status erro">Nenhum lançamento foi encontrado neste arquivo.</div>';
  }

  const creditos = faturaLida.creditos || [];
  const elCred = document.getElementById('faturaCreditos');
  if (creditos.length) {
    const soma = creditos.reduce((s, c) => s + Number(c.valor), 0);
    elCred.innerHTML = `<strong>Estornos e créditos na fatura (não importados):</strong> ` +
      creditos.map(c => `${escFatura(c.descricao)} ${fmt(c.valor)}`).join(' · ') +
      ` — total ${fmt(soma)}`;
    elCred.classList.remove('oculto');
  } else {
    elCred.classList.add('oculto');
  }

  renderItensFatura();
}

function tipoItemFatura(it) {
  const tipo = it.parcelaTotal ? `Parcela ${it.parcelaAtual}/${it.parcelaTotal}` : 'À vista';
  const classe = it.acao === 'JA_IMPORTADA' ? 'aviso-dup' : it.acao === 'QUITAR_PARCELA' ? 'aviso-quitar' : '';
  return `<div class="fatura-tipo">${tipo}</div>` +
    (it.aviso ? `<div class="fatura-aviso ${classe}">${escFatura(it.aviso)}</div>` : '');
}

function renderItensFatura() {
  const corpo = document.getElementById('faturaItens');
  corpo.innerHTML = faturaItens.map((it, i) => `
    <tr class="${it.sel ? '' : 'desmarcado'}" data-i="${i}">
      <td><input type="checkbox" data-campo="sel" ${it.sel ? 'checked' : ''} aria-label="Importar este lançamento"></td>
      <td><input type="date" data-campo="data" value="${escFatura(it.data)}"></td>
      <td><input type="text" data-campo="descricao" value="${escFatura(it.descricao)}" maxlength="150"></td>
      <td>${tipoItemFatura(it)}</td>
      <td><select data-campo="categoria">${CATEGORIAS_DESPESA_FATURA.map(c =>
        `<option ${c === it.categoria ? 'selected' : ''}>${escFatura(c)}</option>`).join('')}</select></td>
      <td class="num"><input type="number" data-campo="valor" value="${Number(it.valor).toFixed(2)}" min="0.01" step="0.01"></td>
    </tr>`).join('');
  atualizarResumoFatura();
}

function atualizarResumoFatura() {
  const selecionados = faturaItens.filter(it => it.sel);
  const somaSel = selecionados.reduce((s, it) => s + (Number(it.valor) || 0), 0);
  const somaTodos = faturaItens.reduce((s, it) => s + (Number(it.valor) || 0), 0);
  const somaCred = (faturaLida.creditos || []).reduce((s, c) => s + Number(c.valor), 0);
  const novas = selecionados.filter(it => it.parcelaTotal && !it.contaId && it.parcelaAtual < it.parcelaTotal).length;

  // Confere a leitura: lançamentos − créditos deve bater com o total impresso na fatura
  let conferencia = '';
  if (faturaLida.total) {
    const lido = somaTodos - somaCred;
    const diff = Math.abs(lido - Number(faturaLida.total));
    conferencia = diff <= 0.05
      ? `<span class="fatura-ok"><span class="ico ico-check-circle"></span> Soma dos lançamentos confere com o total da fatura</span>`
      : `<span class="fatura-alerta"><span class="ico ico-alert-triangle"></span> Lançamentos somam ${fmt(lido)}, mas a fatura diz ${fmt(faturaLida.total)}. Confira se faltou algo.</span>`;
  }

  document.getElementById('faturaResumo').innerHTML = `
    <div class="fatura-resumo-grid">
      <div><span class="rotulo">Cartão</span><strong>${escFatura(faturaLida.emissor || '—')}</strong></div>
      <div><label class="rotulo" for="fVencFatura">Vencimento da fatura</label>
        <input type="date" id="fVencFatura" value="${escFatura(faturaLida.vencimento || '')}"></div>
      <div><span class="rotulo">Total da fatura</span><strong>${faturaLida.total ? fmt(faturaLida.total) : '—'}</strong></div>
      <div><span class="rotulo">Selecionados</span><strong>${selecionados.length} de ${faturaItens.length} · ${fmt(somaSel)}</strong></div>
    </div>
    ${conferencia ? `<div class="fatura-conferencia">${conferencia}</div>` : ''}
    ${novas ? `<div class="fatura-ajuda">${novas} compra(s) parcelada(s) vão gerar as próximas parcelas em Contas a Pagar, a partir do vencimento acima.</div>` : ''}`;

  const todos = document.getElementById('fatSelTodos');
  todos.checked = faturaItens.length > 0 && selecionados.length === faturaItens.length;
  todos.indeterminate = selecionados.length > 0 && selecionados.length < faturaItens.length;
}

function editarItemFatura(e) {
  const tr = e.target.closest('tr[data-i]');
  const campo = e.target.getAttribute('data-campo');
  if (!tr || !campo) return;
  const it = faturaItens[Number(tr.getAttribute('data-i'))];
  if (campo === 'sel') {
    it.sel = e.target.checked;
    tr.classList.toggle('desmarcado', !it.sel);
  } else {
    it[campo] = e.target.value;
  }
  // O campo de vencimento é recriado ao atualizar o resumo; guarda o valor digitado antes
  const venc = document.getElementById('fVencFatura');
  if (venc) faturaLida.vencimento = venc.value;
  atualizarResumoFatura();
}

async function confirmarFatura() {
  const venc = document.getElementById('fVencFatura').value;
  const selecionados = faturaItens.filter(it => it.sel);
  if (!selecionados.length) { alert('Selecione pelo menos um lançamento.'); return; }
  if (!venc) { alert('Informe o vencimento da fatura.'); return; }
  const invalido = selecionados.find(it => !it.data || !String(it.descricao).trim() || !(Number(it.valor) > 0));
  if (invalido) { alert('Confira data, descrição e valor de todos os lançamentos selecionados.'); return; }

  const corpo = {
    vencimento: venc,
    itens: selecionados.map(it => ({
      data: it.data,
      descricao: String(it.descricao).trim(),
      valor: Number(it.valor),
      parcelaAtual: it.parcelaAtual,
      parcelaTotal: it.parcelaTotal,
      categoria: it.categoria,
      contaId: it.acao === 'QUITAR_PARCELA' ? it.contaId : null,
    })),
  };

  const btn = document.getElementById('btnConfirmarFatura');
  btn.disabled = true;
  try {
    const r = await api.post('/faturas/importar', corpo);
    const partes = [];
    if (r.despesasCriadas) partes.push(`${r.despesasCriadas} despesa(s) à vista`);
    if (r.parcelamentosCriados) partes.push(`${r.parcelamentosCriados} compra(s) parcelada(s)`);
    if (r.parcelasQuitadas) partes.push(`${r.parcelasQuitadas} parcela(s) quitada(s)`);
    mostrarToast(`Fatura importada: ${partes.join(', ') || 'nada novo'} — ${fmt(r.totalImportado)}`);
    fecharPainelFatura();
    await carregarContas();
  } catch (e) {
    alert(e.message);
  } finally {
    btn.disabled = false;
  }
}

document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('btnImportarFatura').addEventListener('click', abrirPainelFatura);
  document.getElementById('btnFecharFatura').addEventListener('click', fecharPainelFatura);
  document.getElementById('btnLerFatura').addEventListener('click', lerFatura);
  document.getElementById('btnVoltarFatura').addEventListener('click', voltarEnvioFatura);
  document.getElementById('btnConfirmarFatura').addEventListener('click', confirmarFatura);
  document.getElementById('btnConfirmarContaFatura').addEventListener('click', confirmarContaFatura);
  document.getElementById('btnVoltarContaFatura').addEventListener('click', voltarEnvioFatura);
  document.getElementById('fcAtualizar').addEventListener('change', atualizarBotaoContaFatura);
  document.getElementById('fSenhaFatura').addEventListener('keydown', e => { if (e.key === 'Enter') lerFatura(); });

  const corpo = document.getElementById('faturaItens');
  corpo.addEventListener('change', editarItemFatura);
  corpo.addEventListener('input', e => { if (e.target.getAttribute('data-campo') === 'valor') editarItemFatura(e); });

  document.getElementById('fatSelTodos').addEventListener('change', e => {
    faturaItens.forEach(it => { it.sel = e.target.checked; });
    renderItensFatura();
  });
  document.getElementById('faturaResumo').addEventListener('change', e => {
    if (e.target.id === 'fVencFatura') faturaLida.vencimento = e.target.value;
  });
});
