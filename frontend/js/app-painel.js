// ── CasaCapital — app-painel.js: números e card de contas do Início (dashboard) ──

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
    compras.slice(0,5).map(x=>`<li><span><span class="ico ico-cart"></span> ${x.nome}${x.qtd > 1 ? ' x'+x.qtd : ''}</span></li>`).join('') ||
    '<li class="li-vazio sem-padding">Nenhum item.</li>';
  if (el('dashboardTarefas')) el('dashboardTarefas').innerHTML =
    tarefas.filter(t => !t.concluida).slice(0,5).map(t => {
      const st = statusTarefa(t);
      return `<li><span>${st?.texto === 'Atrasada' ? '<span class="ico ico-alert-circle"></span>' : '<span class="ico ico-check-circle"></span>'} ${t.nome}</span>${st ? `<span class="tag-status-tarefa ${st.cls}">${st.texto}</span>` : ''}</li>`;
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
      <h2 class="sem-margem"><span class="ico ico-clipboard"></span> Contas a Pagar</h2>
      <a href="contas.html" class="link-ver-todas">Ver todas →</a>
    </div>
    ${urgentes.length ? `
      <div class="bloco-contas-urgentes">
        ${urgentes.map(c => `
          <div class="linha-conta-urgente">
            <span>
              ${c.vencimento < hojeStr ? '<span class="ico ico-alert-circle"></span>' : '<span class="ico ico-calendar"></span>'}
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
