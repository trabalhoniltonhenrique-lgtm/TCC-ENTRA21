// ── CasaCapital — compromissos.js: aba Compromissos da página Tarefas ──
(function () {
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const $ = id => document.getElementById(id);
  const ABAS = ['tarefas', 'compromissos'];
  const AVISO = { 0: 'aviso na hora', 5: 'aviso 5 min antes', 15: 'aviso 15 min antes', 30: 'aviso 30 min antes',
    60: 'aviso 1 h antes', 120: 'aviso 2 h antes', 1440: 'aviso 1 dia antes', 2880: 'aviso 2 dias antes' };

  let lista = [];

  function abrirAba(aba) {
    if (!ABAS.includes(aba)) aba = 'tarefas';
    document.querySelectorAll('[data-aba-tarefas]').forEach(b => {
      const ativa = b.dataset.abaTarefas === aba;
      b.classList.toggle('ativo', ativa);
      b.setAttribute('aria-selected', ativa);
    });
    ABAS.forEach(a => $('painel-' + a).classList.toggle('oculto', a !== aba));
    if (location.hash !== '#' + aba) history.replaceState(null, '', '#' + aba);
  }

  const diaLocal = d => d.toLocaleDateString('sv-SE'); // AAAA-MM-DD

  function grupoDe(c) {
    const ini = new Date(c.inicio);
    const hoje = new Date(); hoje.setHours(0, 0, 0, 0);
    const dia = new Date(ini); dia.setHours(0, 0, 0, 0);
    const dias = Math.round((dia - hoje) / 86400000);
    if (ini < new Date() && dias < 0) return 'Passados';
    if (dias <= 0) return 'Hoje';
    if (dias === 1) return 'Amanhã';
    if (dias <= 7) return 'Próximos 7 dias';
    return 'Mais adiante';
  }

  function item(c) {
    const ini = new Date(c.inicio);
    const passou = ini < new Date();
    return `<div class="comp-item ${passou ? 'passou' : ''}" data-id="${c.id}">
      <div class="comp-hora">
        <strong>${ini.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</strong>
        <small>${ini.toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' })}</small>
      </div>
      <div class="comp-info">
        <strong>${esc(c.titulo)}</strong>
        <div class="comp-det">
          ${c.local ? `<span><span class="ico ico-home"></span> ${esc(c.local)}</span>` : ''}
          <span><span class="ico ico-user"></span> ${esc(c.membroNome || 'Família toda')}</span>
          <span><span class="ico ico-bell"></span> ${AVISO[c.antecedenciaMin] || `aviso ${c.antecedenciaMin} min antes`}${c.avisoDispensado ? ' · visto' : ''}</span>
        </div>
        ${c.obs ? `<div class="comp-obs">${esc(c.obs)}</div>` : ''}
      </div>
      <div class="comp-acoes">
        <button class="btn-sm btn-secundario" data-editar="${c.id}" title="Editar" aria-label="Editar"><span class="ico ico-pencil"></span></button>
        <button class="btn-sm btn-icone-perigo" data-excluir="${c.id}" title="Excluir" aria-label="Excluir"><span class="ico ico-trash"></span></button>
      </div>
    </div>`;
  }

  function render() {
    const grupos = {};
    lista.forEach(c => (grupos[grupoDe(c)] = grupos[grupoDe(c)] || []).push(c));
    const futuros = lista.filter(c => new Date(c.inicio) >= new Date()).length;
    const cont = $('contCompromissos');
    cont.textContent = futuros;
    cont.classList.toggle('oculto', !futuros);

    const ordem = ['Hoje', 'Amanhã', 'Próximos 7 dias', 'Mais adiante'];
    let html = ordem.filter(g => grupos[g]).map(g => `
      <div class="card comp-grupo"><h3>${g}</h3>${grupos[g].map(item).join('')}</div>`).join('');
    if (!futuros) html = '<div class="card comp-grupo"><p class="dica-planejamento">Nenhum compromisso agendado. Use o formulário acima.</p></div>' + html;
    if (grupos['Passados']) {
      html += `<details class="card comp-grupo comp-passados"><summary>Passados (últimos 30 dias) · ${grupos['Passados'].length}</summary>
        ${grupos['Passados'].slice().reverse().map(item).join('')}</details>`;
    }
    $('listaCompromissos').innerHTML = html;
  }

  async function carregar() {
    try {
      lista = await api.get('/compromissos');
      render();
    } catch (e) {
      $('listaCompromissos').innerHTML = `<div class="card"><p class="dica-planejamento">Não foi possível carregar: ${esc(e.message)}</p></div>`;
    }
  }

  function limparForm() {
    $('formCompromisso').reset();
    $('compId').value = '';
    $('compAntecedencia').value = '15';
    $('compData').value = diaLocal(new Date());
    $('btnSalvarCompromisso').textContent = '+ Agendar';
    $('btnCancelarEdicao').classList.add('oculto');
  }

  function editar(id) {
    const c = lista.find(x => x.id === id);
    if (!c) return;
    $('compId').value = c.id;
    $('compTitulo').value = c.titulo;
    $('compData').value = c.inicio.slice(0, 10);
    $('compHora').value = c.inicio.slice(11, 16);
    $('compAntecedencia').value = String(c.antecedenciaMin);
    if (!$('compAntecedencia').value) $('compAntecedencia').value = '15';
    $('compMembro').value = c.membroId ?? '';
    $('compLocal').value = c.local || '';
    $('compObs').value = c.obs || '';
    $('btnSalvarCompromisso').innerHTML = '<span class="ico ico-save"></span> Salvar alteração';
    $('btnCancelarEdicao').classList.remove('oculto');
    $('formCompromisso').scrollIntoView({ behavior: 'smooth', block: 'start' });
    $('compTitulo').focus();
  }

  async function salvar(e) {
    e.preventDefault();
    const id = $('compId').value;
    const corpo = {
      titulo: $('compTitulo').value.trim(),
      inicio: `${$('compData').value}T${$('compHora').value}:00`,
      antecedenciaMin: Number($('compAntecedencia').value),
      membroId: $('compMembro').value ? Number($('compMembro').value) : null,
      local: $('compLocal').value.trim() || null,
      obs: $('compObs').value.trim() || null,
    };
    const btn = $('btnSalvarCompromisso');
    btn.disabled = true;
    try {
      if (id) await api.put(`/compromissos/${id}`, corpo);
      else await api.post('/compromissos', corpo);
      limparForm();
      await carregar();
      if (window.CasaAlertas) CasaAlertas.verificar(); // se já está na hora do aviso, aparece agora
    } catch (erro) { alert(erro.message); }
    finally { btn.disabled = false; }
  }

  function botaoNotificacao() {
    const p = $('avisoSomCompromisso');
    if (!('Notification' in window) || !window.isSecureContext || Notification.permission !== 'default') return;
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'btn-secundario btn-notif-sistema';
    b.innerHTML = '<span class="ico ico-bell"></span> Avisar também na área de notificações do computador';
    b.addEventListener('click', async () => { await Notification.requestPermission(); b.remove(); });
    p.after(b);
  }

  document.addEventListener('DOMContentLoaded', async () => {
    document.querySelectorAll('[data-aba-tarefas]').forEach(b => b.addEventListener('click', () => abrirAba(b.dataset.abaTarefas)));
    abrirAba(location.hash.slice(1));
    limparForm();

    $('formCompromisso').addEventListener('submit', salvar);
    $('btnCancelarEdicao').addEventListener('click', limparForm);
    $('btnTestarAlerta').addEventListener('click', () => {
      if (window.CasaAlertas) CasaAlertas.testar();
      else alert('O alerta ainda está carregando. Tente de novo em alguns segundos.');
    });
    $('listaCompromissos').addEventListener('click', async e => {
      const ed = e.target.closest('[data-editar]');
      const ex = e.target.closest('[data-excluir]');
      if (ed) editar(Number(ed.dataset.editar));
      if (ex) {
        const c = lista.find(x => x.id === Number(ex.dataset.excluir));
        if (!confirm(`Excluir o compromisso "${c ? c.titulo : ''}"?`)) return;
        try { await api.del(`/compromissos/${ex.dataset.excluir}`); await carregar(); }
        catch (erro) { alert(erro.message); }
      }
    });
    document.addEventListener('compromissos-alterados', carregar);

    try {
      const membros = await api.get('/membros');
      $('compMembro').innerHTML = '<option value="">Família toda</option>' +
        membros.map(m => `<option value="${m.id}">${esc(m.nome)}</option>`).join('');
    } catch { /* fica só "Família toda" */ }
    botaoNotificacao();
    setTimeout(() => window.CasaAlertas && CasaAlertas.atualizarAvisoSom(), 1500);
    carregar();
    setInterval(render, 60000); // grupos Hoje/Passados mudam com o relógio
  });
})();
