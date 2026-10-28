// ── CasaCapital — app-financas.js: receitas, despesas, resumos por categoria/membro e alertas de limite ──

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
          <button class="btn-sm btn-perigo btn-excluir-item" data-action="excluir-receita" data-id="${x.id}"><span class="ico ico-x"></span></button>
        </li>`;
      }).join('')
    : '<li class="li-vazio">Nenhuma receita no período.</li>';

  if (ld) ld.innerHTML = desFilt.length
    ? [...desFilt].reverse().map((x) => {
        return `<li>
          <div class="linha-item-info">
            <span class="linha-item-nome">${x.nome}${x.origemConta ? ' <span class="tag-origem-conta"><span class="ico ico-clipboard"></span> Conta</span>' : ''}${tagMembro(x.membroId)}</span>
            <span class="linha-item-meta">${x.categoria} · ${fmtD(x.data)}</span>
          </div>
          <span class="item-despesa valor-sem-quebra">${fmt(x.valor)}</span>
          <button class="btn-sm btn-perigo btn-excluir-item" data-action="excluir-despesa" data-id="${x.id}"><span class="ico ico-x"></span></button>
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
    <h3 class="titulo-resumo-cat"><span class="ico ico-chart-bar"></span> Gastos por Categoria</h3>
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
        ${over ? `<div class="aviso-limite-excedido"><span class="ico ico-alert-triangle"></span> Limite excedido em ${fmt(val - lim)}</div>` : ''}
      </div>`;
    }).join('')}
    ${sorted[0][1] / total > 0.35
      ? `<div class="aviso-categoria-dominante">
          <span class="ico ico-alert-triangle"></span> <strong>${sorted[0][0]}</strong> representa ${((sorted[0][1]/total)*100).toFixed(0)}% dos gastos. Revise se está dentro do planejado.
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
    <h3 class="titulo-resumo-cat"><span class="ico ico-users"></span> Balanço por Membro</h3>
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
        <span class="ico ico-alert-circle"></span> <strong>${cat}</strong>: gasto de ${fmt(mapa[cat])} ultrapassou o limite de ${fmt(lim)}
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
        `<li><span><span class="ico ico-bell"></span> <strong>${cat}</strong> — limite ${fmt(lim)}</span>
         <button class="btn-sm btn-perigo" data-action="excluir-alerta" data-cat="${cat}"><span class="ico ico-x"></span></button></li>`
      ).join('')
    : '<li class="li-vazio sem-padding">Nenhum alerta configurado.</li>';
}
