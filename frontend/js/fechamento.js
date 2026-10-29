// ── CasaCapital — fechamento.js: resumo de um mês (página Fechamento do mês) ──
(function () {
  const dinheiro = v => formatarDinheiro(v, window.__familia); // util.js, com a moeda da família
  const pctTxt = v => (v > 0 ? '+' : '') + Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%';
  const nomeMes = chave => { const t = new Date(chave + '-15T12:00:00').toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' }); return t.charAt(0).toUpperCase() + t.slice(1); };
  const dataBR = formatarData; // util.js

  // variação de gasto: subir é ruim (vermelho), cair é bom (verde)
  const variacaoGasto = v => v == null ? '<span class="var-neutra">—</span>'
    : `<span class="${v > 0 ? 'var-ruim' : 'var-boa'}">${pctTxt(v)}</span>`;

  function opcoesDeMes() {
    const sel = document.getElementById('fechMes');
    const hoje = new Date();
    const meses = [];
    for (let i = 0; i < 13; i++) {
      const d = new Date(hoje.getFullYear(), hoje.getMonth() - i, 15);
      meses.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
    }
    const pedido = new URLSearchParams(location.search).get('mes');
    sel.innerHTML = meses.map((m, i) => `<option value="${m}">${nomeMes(m)}${i === 0 ? ' (em andamento)' : ''}</option>`).join('');
    sel.value = meses.includes(pedido) ? pedido : meses[1];
  }

  function render(r) {
    window.__dadosFechamento = r; // usado pelo botão Imprimir / PDF (relatorio-modulo.js)
    const el = document.getElementById('fechConteudo');
    const vazio = !Number(r.receitas) && !Number(r.despesas);
    const maxCat = Math.max(1, ...r.categorias.map(c => Number(c.valor)));
    const estouradas = r.orcamento.filter(o => o.estourou);

    el.innerHTML = `
      <div class="resumo-topo">
        <div class="resumo-box cor-pago"><div class="num">${dinheiro(r.receitas)}</div><div class="txt">Receitas ${r.receitasAnterior != null && Number(r.receitasAnterior) ? '· mês anterior ' + dinheiro(r.receitasAnterior) : ''}</div></div>
        <div class="resumo-box cor-atraso"><div class="num">${dinheiro(r.despesas)}</div><div class="txt">Despesas ${r.variacaoDespesasPct != null ? '· ' + pctTxt(r.variacaoDespesasPct) + ' vs. mês anterior' : ''}</div></div>
        <div class="resumo-box ${Number(r.saldo) >= 0 ? 'cor-saldo' : 'cor-atraso'}"><div class="num">${dinheiro(r.saldo)}</div><div class="txt">Saldo do mês</div></div>
        ${r.patrimonio ? `<div class="resumo-box cor-parcela"><div class="num">${dinheiro(r.patrimonio.total)}</div><div class="txt">Patrimônio${r.patrimonio.variacao != null ? ' · ' + (Number(r.patrimonio.variacao) >= 0 ? '+' : '') + dinheiro(r.patrimonio.variacao) + ' no mês' : ''}</div></div>` : ''}
      </div>
      ${vazio ? `<p class="dica-planejamento">Nenhuma receita ou despesa lançada em ${nomeMes(r.mes).toLowerCase()}.</p>` : ''}

      <div class="cards resumo-grade">
        <div class="card-box">
          <h3>Onde o dinheiro foi</h3>
          ${r.categorias.length ? r.categorias.map(c => `
            <div class="linha-distribuicao">
              <span class="tipo">${esc(c.categoria)}</span>
              <div class="barra-fundo-meta"><div class="barra-progresso-meta" style="--largura:${Number(c.valor) / maxCat * 100}%"></div></div>
              <span class="valor">${dinheiro(c.valor)} <small>${variacaoGasto(c.variacaoPct)}</small></span>
            </div>`).join('') : '<p class="dica-planejamento">Sem despesas no mês.</p>'}
        </div>

        <div class="card-box">
          <h3>Maiores despesas</h3>
          ${r.maioresDespesas.length ? `<ul class="lista-resumo">${r.maioresDespesas.map(d => `
            <li><span>${dataBR(d.data)} · ${esc(d.nome)} <small>${esc(d.categoria)}</small></span><strong>${dinheiro(d.valor)}</strong></li>`).join('')}</ul>`
            : '<p class="dica-planejamento">Sem despesas no mês.</p>'}
          ${r.contasPagas.quantidade ? `<p class="dica-planejamento">${r.contasPagas.quantidade} conta(s) paga(s) no mês, somando ${dinheiro(r.contasPagas.total)}.</p>` : ''}
        </div>

        <div class="card-box">
          <h3>Orçamento</h3>
          ${r.orcamento.length ? `
            <p class="${estouradas.length ? 'var-ruim' : 'var-boa'}">${r.orcamento.length - estouradas.length} de ${r.orcamento.length} categorias dentro do limite.</p>
            <ul class="lista-resumo">${r.orcamento.map(o => `
              <li><span>${esc(o.categoria)}${o.estourou ? ' <em class="selo-assin aumento">passou</em>' : ''}</span>
                <strong>${dinheiro(o.gasto)} <small>de ${dinheiro(o.limite)}</small></strong></li>`).join('')}</ul>`
            : '<p class="dica-planejamento">Nenhum limite definido para este mês (Metas → Orçamento do mês).</p>'}
        </div>

        <div class="card-box">
          <h3>Metas</h3>
          ${r.metas.length ? `<ul class="lista-resumo">${r.metas.map(m => `
            <li><span>${esc(m.nome)} <small>${m.pct}% · ${dinheiro(m.totalPoupado)} de ${dinheiro(m.valorAlvo)}</small></span>
              <strong class="${Number(m.aportadoNoMes) > 0 ? 'var-boa' : ''}">${Number(m.aportadoNoMes) > 0 ? '+' + dinheiro(m.aportadoNoMes) : '—'}</strong></li>`).join('')}</ul>
            <p class="dica-planejamento">À direita, quanto foi guardado na meta neste mês.</p>`
            : '<p class="dica-planejamento">Nenhuma meta cadastrada.</p>'}
        </div>

        <div class="card-box">
          <h3>Assinaturas</h3>
          ${r.assinaturas.quantidade ? `<p>${r.assinaturas.quantidade} cobrada(s), total <strong>${dinheiro(r.assinaturas.total)}</strong>.</p>
            <ul class="lista-resumo">${r.assinaturas.itens.map(a => `
              <li><span>${esc(a.nome)}${a.aumentoPct != null ? ` <em class="selo-assin aumento">aumentou ${pctTxt(a.aumentoPct)}</em>` : ''}</span><strong>${dinheiro(a.valor)}</strong></li>`).join('')}</ul>`
            : '<p class="dica-planejamento">Nenhuma assinatura cobrada neste mês.</p>'}
        </div>
      </div>`;
  }

  async function carregar() {
    const mes = document.getElementById('fechMes').value;
    history.replaceState(null, '', '?mes=' + mes);
    document.getElementById('fechConteudo').innerHTML = '<p class="dica-planejamento">Carregando…</p>';
    try {
      render(await api.get('/fechamento?mes=' + mes));
    } catch (e) {
      document.getElementById('fechConteudo').innerHTML = `<p class="dica-planejamento">Não foi possível montar o fechamento: ${esc(e.message)}</p>`;
    }
  }

  async function carregarPreferencias() {
    const p = await api.get('/fechamento/preferencias');
    const caixa = document.getElementById('fechEmailAtivo');
    caixa.checked = p.emailAtivo;
    caixa.disabled = !p.emailConfigurado;
    document.getElementById('btnEnviarFechamento').disabled = !p.emailConfigurado;
    document.getElementById('fechEmailInfo').innerHTML = p.emailConfigurado
      ? `Vai para: <strong>${p.destinatarios.map(esc).join(', ')}</strong> (os e-mails de login da família).`
      : 'O envio de e-mail não está configurado neste sistema (MAIL_USERNAME e MAIL_PASSWORD no configuracao.ini do instalador).';
  }

  document.addEventListener('DOMContentLoaded', () => {
    opcoesDeMes();
    document.getElementById('fechMes').addEventListener('change', carregar);
    document.getElementById('fechEmailAtivo').addEventListener('change', async e => {
      try { await api.put('/fechamento/preferencias', { emailAtivo: e.target.checked }); }
      catch (erro) { alert(erro.message); e.target.checked = !e.target.checked; }
    });
    document.getElementById('btnEnviarFechamento').addEventListener('click', async e => {
      const mes = document.getElementById('fechMes').value;
      e.target.disabled = true;
      try {
        const r = await api.post('/fechamento/enviar?mes=' + mes);
        alert(r.enviados ? `Fechamento de ${nomeMes(mes).toLowerCase()} enviado para ${r.enviados} e-mail(s).` : 'Nenhum e-mail de login encontrado para enviar.');
      } catch (erro) { alert(erro.message); }
      finally { e.target.disabled = false; }
    });
    carregar();
    carregarPreferencias().catch(() => {});
  });
})();
