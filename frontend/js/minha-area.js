// ── CasaCapital — minha-area.js: a área do filho (perfil dependente) ──
// Mesada, tarefas dele e lista de compras. O servidor só devolve o que é dele.
(function () {
  const dinheiro = v => formatarDinheiro(v, window.__familia); // util.js, com a moeda da família
  const dataBR = formatarData; // util.js
  const DIAS = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];

  function renderMesada(m) {
    document.getElementById('saudacao').innerHTML = `<span class="ico ico-coins"></span> Olá, ${esc((m.nome || '').split(' ')[0])}!`;
    const card = document.getElementById('cardMinhaMesada');
    const falta = m.metaValor ? Math.max(0, Number(m.metaValor) - Number(m.saldo)) : 0;

    card.innerHTML = `
      <h3>Minha mesada</h3>
      <div class="saldo-mesada ${Number(m.saldo) < 0 ? 'negativo' : ''}">${dinheiro(m.saldo)}</div>
      <p class="sub-mesada">${m.configurada && m.ativa
        ? `Próxima mesada: <strong>${dinheiro(m.valor)}</strong> em ${dataBR(m.proximaMesada)}`
        : 'Seus pais ainda não definiram a mesada.'}</p>
      <div class="mesada-mes"><span>Recebi no mês <strong>${dinheiro(m.recebidoNoMes)}</strong></span><span>Gastei no mês <strong>${dinheiro(m.gastoNoMes)}</strong></span></div>
      ${m.metaNome && m.metaValor ? `<div class="meta-mesada">
        <div class="meta-mesada-txt"><span><span class="ico ico-target"></span> ${esc(m.metaNome)}</span><span>${m.metaPct}%</span></div>
        <div class="barra-fundo-meta"><div class="barra-progresso-meta" style="--largura:${m.metaPct}%"></div></div>
        <p class="dica-planejamento">${falta > 0 ? `Faltam <strong>${dinheiro(falta)}</strong> para o objetivo.` : 'Você já juntou o suficiente para o objetivo! 🎉'}</p>
      </div>` : ''}

      <details class="secao-mesada">
        <summary><span class="ico ico-pencil"></span> Anotar um gasto</summary>
        <form id="formGastoFilho" class="form-mesada">
          <label>Valor (R$)<input name="valor" type="number" min="0.01" step="0.01" required></label>
          <label class="campo-largo">Com o que gastei<input name="descricao" maxlength="160" required placeholder="Ex: lanche na escola"></label>
          <div class="acoes-form-mesada"><button type="submit">Anotar</button></div>
        </form>
      </details>

      <details class="secao-mesada">
        <summary><span class="ico ico-clipboard"></span> Extrato</summary>
        ${m.movimentos.length ? `<ul class="extrato-mesada">${m.movimentos.map(mv => `
          <li><span><small>${dataBR(mv.data)}</small>${esc(mv.descricao)}</span>
            <strong class="${mv.tipo === 'GASTO' ? 'var-ruim' : 'var-boa'}">${mv.tipo === 'GASTO' ? '−' : '+'}${dinheiro(mv.valor)}</strong></li>`).join('')}</ul>`
          : '<p class="dica-planejamento">Nada por aqui ainda.</p>'}
      </details>`;

    document.getElementById('formGastoFilho').addEventListener('submit', async e => {
      e.preventDefault();
      const d = Object.fromEntries(new FormData(e.target));
      try {
        renderMesada(await api.post('/mesada/minha/gastos', { valor: Number(d.valor), descricao: d.descricao }));
      } catch (erro) { alert(erro.message); }
    });
  }

  function renderTarefas(tarefas) {
    const ul = document.getElementById('minhasTarefas');
    if (!tarefas.length) { ul.innerHTML = '<li class="dica-planejamento">Nenhuma tarefa para você agora.</li>'; return; }
    const ordenadas = [...tarefas].sort((a, b) => a.concluida - b.concluida || String(a.prazo || '9').localeCompare(String(b.prazo || '9')));
    ul.innerHTML = ordenadas.map(t => `
      <li class="${t.concluida ? 'feita' : ''}">
        <label><input type="checkbox" data-tarefa="${t.id}" ${t.concluida ? 'checked' : ''}>
          <span>${esc(t.nome)}${t.prazo ? ` <small>até ${dataBR(t.prazo)}</small>` : ''}${t.diaSemana != null ? ` <small>· ${DIAS[t.diaSemana % 7]}</small>` : ''}</span></label>
      </li>`).join('');
  }

  function renderCompras(compras) {
    const ul = document.getElementById('listaComprasFilho');
    ul.innerHTML = compras.length
      ? compras.map(c => `<li><span>${esc(c.nome)}</span><strong>${c.qtd > 1 ? c.qtd + '×' : ''}</strong></li>`).join('')
      : '<li class="dica-planejamento">A lista está vazia.</li>';
  }

  function renderCompromissos(lista) {
    const ul = document.getElementById('meusCompromissos');
    const futuros = lista.filter(c => new Date(c.inicio) >= new Date(Date.now() - 3600000));
    ul.innerHTML = futuros.length ? futuros.slice(0, 10).map(c => {
      const ini = new Date(c.inicio);
      return `<li><span>${esc(c.titulo)}${c.local ? ` <small>${esc(c.local)}</small>` : ''}</span>
        <strong>${ini.toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' })} ${ini.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</strong></li>`;
    }).join('') : '<li class="dica-planejamento">Nenhum compromisso agendado para você.</li>';
  }

  async function carregar() {
    api.get('/compromissos').then(renderCompromissos).catch(() => { document.getElementById('meusCompromissos').innerHTML = ''; });
    const [m, t, c] = await Promise.allSettled([api.get('/mesada/minha'), api.get('/tarefas'), api.get('/compras')]);
    if (m.status === 'fulfilled') renderMesada(m.value);
    else document.getElementById('cardMinhaMesada').innerHTML = `<p class="dica-planejamento">Não foi possível carregar a mesada: ${esc(m.reason.message)}</p>`;
    if (t.status === 'fulfilled') renderTarefas(t.value);
    if (c.status === 'fulfilled') renderCompras(c.value);
  }

  document.addEventListener('DOMContentLoaded', () => {
    if (!ehDependente()) { location.replace('dashboard.html'); return; } // pais usam a tela Mesada

    document.getElementById('minhasTarefas').addEventListener('change', async e => {
      const id = e.target.dataset.tarefa;
      if (!id) return;
      try {
        await api.post(`/tarefas/${id}/concluir`);
        renderTarefas(await api.get('/tarefas'));
      } catch (erro) { alert(erro.message); e.target.checked = !e.target.checked; }
    });

    document.getElementById('formCompraFilho').addEventListener('submit', async e => {
      e.preventDefault();
      const campo = document.getElementById('compraFilhoNome');
      try {
        await api.post('/compras', { nome: campo.value.trim(), qtd: 1, categoria: 'Geral', frequencia: 'SEM_FREQUENCIA' });
        campo.value = '';
        renderCompras(await api.get('/compras'));
      } catch (erro) { alert(erro.message); }
    });

    carregar();
  });
})();
