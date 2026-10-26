// ── CasaCapital — planejamento.js: cartões do Início "Patrimônio" e "Fluxo de caixa" ──
// Usa fmt/fmtD de app.js; os dados vêm de /api/planejamento.

(function () {
  const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
  const nomeMes = chave => { const [a, m] = chave.split('-'); return `${MESES[Number(m) - 1]}/${a.slice(2)}`; };
  const nomeMesLongo = chave => {
    const t = new Date(chave + '-15T12:00:00').toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
    return t.charAt(0).toUpperCase() + t.slice(1);
  };
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  function renderPatrimonio(p) {
    const card = document.getElementById('cardPatrimonio');
    const a = p.atual;
    const hist = p.historico || [];
    const anterior = hist.length >= 2 ? hist[hist.length - 2] : null;
    const variacao = anterior ? Number(a.total) - Number(anterior.total) : null;
    const maxAbs = Math.max(1, ...hist.map(h => Math.abs(Number(h.total))));

    card.innerHTML = `
      <h2><span class="ico ico-trending-up"></span> Patrimônio da família</h2>
      <div class="patrimonio-total ${Number(a.total) < 0 ? 'negativo' : ''}">${fmt(a.total)}</div>
      ${variacao != null ? `<div class="patrimonio-variacao ${variacao >= 0 ? 'positiva' : 'negativa'}">
        ${variacao >= 0 ? '↑' : '↓'} ${fmt(Math.abs(variacao))} desde ${nomeMesLongo(anterior.mes).toLowerCase()}</div>` : ''}
      <div class="patrimonio-composicao">
        <div><span>Saldo (receitas − despesas)</span><strong>${fmt(a.saldo)}</strong></div>
        <div><span>+ Investimentos</span><strong>${fmt(a.investimentos)}</strong></div>
        <div><span>− Parcelas a vencer</span><strong>${fmt(a.dividas)}</strong></div>
      </div>
      ${hist.length >= 2 ? `<div class="patrimonio-grafico" role="img" aria-label="Evolução do patrimônio nos últimos ${hist.length} meses">
        ${hist.map(h => {
          const v = Number(h.total);
          const alt = Math.max(4, Math.round(Math.abs(v) / maxAbs * 100));
          return `<div class="barra-patrimonio" title="${nomeMesLongo(h.mes)}: ${fmt(v)}">
            <div class="coluna ${v < 0 ? 'negativa' : ''}" style="--altura:${alt}%"></div>
            <span>${nomeMes(h.mes)}</span>
          </div>`;
        }).join('')}
      </div>` : `<p class="dica-planejamento">O gráfico de evolução começa a aparecer no próximo mês: o sistema guarda uma foto do patrimônio a cada mês.</p>`}
      <a href="metas.html#investimentos" class="link-ver-mais">Ver investimentos →</a>`;
  }

  function renderFluxo(f) {
    const card = document.getElementById('cardFluxoCaixa');
    const alerta = f.primeiroMesNegativo
      ? (() => {
          const m = f.meses.find(x => x.mes === f.primeiroMesNegativo);
          return `<div class="fluxo-alerta"><span class="ico ico-alert-triangle"></span>
            Atenção: em <strong>${nomeMesLongo(f.primeiroMesNegativo).toLowerCase()}</strong> o saldo previsto fica negativo (${fmt(m.saldoFinal)}).</div>`;
        })()
      : `<div class="fluxo-ok"><span class="ico ico-check-circle"></span> O saldo previsto fica positivo nos próximos ${f.meses.length} meses.</div>`;

    card.innerHTML = `
      <h2><span class="ico ico-calendar-days"></span> Fluxo de caixa — próximos ${f.meses.length} meses</h2>
      ${alerta}
      <div class="fluxo-cabecalho"><span>Mês</span><span>Entradas</span><span>Contas</span><span>Dia a dia</span><span>Saldo previsto</span></div>
      ${f.meses.map(m => `
        <details class="fluxo-mes">
          <summary>
            <span class="mes">${nomeMes(m.mes)}</span>
            <span class="entrada">${fmt(m.receitas)}</span>
            <span class="saida" title="${Number(m.parcelas) > 0 ? 'Inclui ' + fmt(m.parcelas) + ' de parcelas' : ''}">${fmt(m.contas)}</span>
            <span class="saida">${fmt(m.gastosVariaveis)}</span>
            <span class="saldo ${Number(m.saldoFinal) < 0 ? 'negativo' : ''}">${fmt(m.saldoFinal)}</span>
          </summary>
          ${m.itens.length ? `<ul class="fluxo-itens">${m.itens.map(i => `
            <li><span>${fmtD(i.vencimento)} · ${esc(i.descricao)}${i.parcela ? ' <em>parcela</em>' : ''}${i.prevista ? ' <em>prevista</em>' : ''}</span>
              <strong>${fmt(i.valor)}</strong></li>`).join('')}</ul>`
            : '<p class="fluxo-sem-contas">Nenhuma conta cadastrada para este mês.</p>'}
        </details>`).join('')}
      <p class="dica-planejamento">
        ${f.mesesDeHistorico
          ? `Entradas e gastos do dia a dia pela média dos últimos ${f.mesesDeHistorico} mês(es): ${fmt(f.mediaReceitas)} e ${fmt(f.mediaGastosVariaveis)} por mês.`
          : 'Ainda não há meses anteriores lançados: entradas e gastos do dia a dia entram como zero.'}
        Contas: as cadastradas em aberto (inclusive parcelas do cartão) e as próximas das contas mensais/anuais (<em>prevista</em>).
        Clique num mês para ver as contas. É uma estimativa, não uma garantia.</p>
      <a href="contas.html" class="link-ver-mais">Ver contas →</a>`;
  }

  async function carregar() {
    const cardP = document.getElementById('cardPatrimonio');
    const cardF = document.getElementById('cardFluxoCaixa');
    if (!cardP || !cardF) return;
    const [p, f] = await Promise.allSettled([api.get('/planejamento/patrimonio'), api.get('/planejamento/fluxo-caixa?meses=6')]);
    if (p.status === 'fulfilled') renderPatrimonio(p.value);
    else cardP.innerHTML = '<h2>Patrimônio da família</h2><p class="dica-planejamento">Não foi possível calcular agora.</p>';
    if (f.status === 'fulfilled') renderFluxo(f.value);
    else cardF.innerHTML = '<h2>Fluxo de caixa</h2><p class="dica-planejamento">Não foi possível calcular agora.</p>';
  }

  document.addEventListener('casacapital-dados-carregados', carregar);
})();
