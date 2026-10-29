// ── CasaCapital — assinaturas.js: painel de assinaturas (Finanças) e aviso de aumento/nova (Início) ──
// As assinaturas são detectadas no servidor a partir das despesas que se repetem todo mês. Usa fmt/fmtD de app.js.

(function () {
  const pct = v => Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%';

  function item(a) {
    const selos = [
      a.aumentoPct != null ? `<span class="selo-assin aumento" title="Era ${fmt(a.valorAnterior)}">aumentou ${pct(a.aumentoPct)}</span>` : '',
      a.nova ? '<span class="selo-assin nova">nova</span>' : '',
      a.provavel ? '<span class="selo-assin provavel" title="Serviço de assinatura conhecido, visto uma vez só até agora">a confirmar</span>' : '',
    ].join('');
    return `<li class="item-assinatura ${a.ativa ? '' : 'inativa'}">
      <div class="info">
        <div class="titulo"><strong>${esc(a.nome)}</strong>${selos}</div>
        <span>${esc(a.categoria)} · última cobrança ${fmtD(a.ultimaCobranca)}${a.meses > 1 ? ` · ${a.meses} meses` : ''}</span>
      </div>
      <div class="valor">${fmt(a.valor)}<small>/mês</small></div>
      <button class="btn-sm btn-secundario" data-ignorar-assin="${esc(a.chave)}" title="Não é assinatura (esconder)"><span class="ico ico-x"></span></button>
    </li>`;
  }

  function renderCard(r) {
    const card = document.getElementById('cardAssinaturas');
    if (!card) return;
    const ativas = r.assinaturas.filter(a => a.ativa);
    const paradas = r.assinaturas.filter(a => !a.ativa);
    card.innerHTML = `
      <h2><span class="ico ico-repeat"></span> Assinaturas</h2>
      ${ativas.length ? `<div class="total-assinaturas"><strong>${fmt(r.totalMensal)}</strong> por mês · ${fmt(r.totalAnual)} por ano</div>
        <ul class="lista-assinaturas">${ativas.map(item).join('')}</ul>`
        : `<p class="dica-assinaturas">Nenhuma assinatura ativa encontrada ainda. Elas aparecem sozinhas conforme as faturas do
           cartão são importadas (o que é cobrado uma vez por mês, todo mês, com valor parecido).</p>`}
      ${paradas.length ? `<details class="assinaturas-paradas"><summary>Sem cobrança há mais de 45 dias (${paradas.length}) — canceladas?</summary>
        <ul class="lista-assinaturas">${paradas.map(item).join('')}</ul></details>` : ''}
      ${r.ignoradas.length ? `<details class="assinaturas-paradas"><summary>Marcadas como "não é assinatura" (${r.ignoradas.length})</summary>
        <ul class="lista-ignoradas">${r.ignoradas.map(c => `<li>${esc(c)}
          <button class="btn-sm btn-secundario" data-mostrar-assin="${esc(c)}">Voltar a mostrar</button></li>`).join('')}</ul></details>` : ''}
      <p class="dica-assinaturas">Detectadas pelas despesas dos últimos 12 meses. Use o <strong>×</strong> para esconder o que não for assinatura.</p>`;
  }

  function renderAviso(r) {
    const el = document.getElementById('avisoAssinaturas');
    if (!el) return;
    const aumentos = r.assinaturas.filter(a => a.ativa && a.aumentoPct != null);
    const novas = r.assinaturas.filter(a => a.ativa && a.nova);
    if (!aumentos.length && !novas.length) { el.classList.add('oculto'); return; }
    const partes = [];
    if (aumentos.length) partes.push(aumentos.map(a => `<strong>${esc(a.nome)}</strong> aumentou ${pct(a.aumentoPct)} (agora ${fmt(a.valor)})`).join('; '));
    if (novas.length) partes.push(`${novas.length === 1 ? 'assinatura nova' : novas.length + ' assinaturas novas'}: ${novas.map(a => `<strong>${esc(a.nome)}</strong> ${fmt(a.valor)}`).join(', ')}`);
    el.innerHTML = `<span class="ico ico-repeat"></span> <span>${partes.join(' · ')}.</span> <a href="financas.html#assinaturas">Ver assinaturas →</a>`;
    el.classList.remove('oculto');
  }

  async function carregar() {
    if (!document.getElementById('cardAssinaturas') && !document.getElementById('avisoAssinaturas')) return;
    try {
      const r = await api.get('/assinaturas');
      renderCard(r);
      renderAviso(r);
      if (location.hash === '#assinaturas') document.getElementById('cardAssinaturas')?.scrollIntoView({ block: 'start' });
    } catch (e) {
      const card = document.getElementById('cardAssinaturas');
      if (card) card.innerHTML = '<h2>Assinaturas</h2><p class="dica-assinaturas">Não foi possível carregar agora.</p>';
    }
  }

  document.addEventListener('click', async e => {
    const ign = e.target.closest('[data-ignorar-assin]');
    const mos = e.target.closest('[data-mostrar-assin]');
    if (!ign && !mos) return;
    try {
      if (ign) {
        if (!confirm('Esconder este item? Ele deixa de aparecer como assinatura (dá para voltar a mostrar depois).')) return;
        await api.post('/assinaturas/ignoradas', { chave: ign.getAttribute('data-ignorar-assin') });
      } else {
        await api.del('/assinaturas/ignoradas?chave=' + encodeURIComponent(mos.getAttribute('data-mostrar-assin')));
      }
      await carregar();
    } catch (erro) {
      alert(erro.message);
    }
  });

  document.addEventListener('casacapital-dados-carregados', carregar);
})();
