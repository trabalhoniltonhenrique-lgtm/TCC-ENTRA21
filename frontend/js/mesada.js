// ── CasaCapital — mesada.js: mesada e login dos filhos (tela dos pais) ──
(function () {
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const dinheiro = v => (Number(v) < 0 ? '-' : '') + 'R$ ' + Math.abs(Number(v) || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const dataBR = d => d ? new Date(d + 'T12:00:00').toLocaleDateString('pt-BR') : '—';
  const hoje = () => new Date().toLocaleDateString('sv-SE'); // AAAA-MM-DD no fuso local
  const TIPO = { MESADA: 'Mesada', EXTRA: 'Extra', GASTO: 'Gasto' };

  let lista = [];

  function extrato(m) {
    if (!m.movimentos.length) return '<p class="dica-planejamento">Nenhum lançamento ainda.</p>';
    return `<ul class="extrato-mesada">${m.movimentos.map(mv => `
      <li>
        <span><small>${dataBR(mv.data)} · ${TIPO[mv.tipo]}${mv.criadoPor ? ' · por ' + esc(mv.criadoPor) : ''}</small>${esc(mv.descricao)}</span>
        <strong class="${mv.tipo === 'GASTO' ? 'var-ruim' : 'var-boa'}">${mv.tipo === 'GASTO' ? '−' : '+'}${dinheiro(mv.valor)}</strong>
        ${mv.tipo !== 'MESADA' ? `<button class="btn-sm btn-icone-perigo" data-excluir-mov="${mv.id}" title="Apagar lançamento" aria-label="Apagar lançamento"><span class="ico ico-trash"></span></button>` : '<span class="espaco-btn"></span>'}
      </li>`).join('')}</ul>`;
  }

  function cartao(m) {
    const meta = m.metaNome && m.metaValor
      ? `<div class="meta-mesada">
          <div class="meta-mesada-txt"><span><span class="ico ico-target"></span> ${esc(m.metaNome)}</span><span>${m.metaPct}% · ${dinheiro(m.saldo)} de ${dinheiro(m.metaValor)}</span></div>
          <div class="barra-fundo-meta"><div class="barra-progresso-meta" style="--largura:${m.metaPct}%"></div></div>
        </div>` : '';

    const situacao = !m.configurada ? 'Sem mesada definida'
      : !m.ativa ? 'Mesada pausada'
      : `${dinheiro(m.valor)} todo dia ${m.diaPagamento} · próxima em ${dataBR(m.proximaMesada)}`;

    return `<div class="card-box cartao-mesada" data-membro="${m.membroId}">
      <div class="cartao-mesada-topo">
        <h3><span class="ponto-membro" style="background:${esc(m.cor || '#94a3b8')}"></span> ${esc(m.nome)}</h3>
        ${m.login ? `<span class="selo-login"><span class="ico ico-user"></span> ${esc(m.login)}</span>` : ''}
      </div>
      <div class="saldo-mesada ${Number(m.saldo) < 0 ? 'negativo' : ''}">${dinheiro(m.saldo)}</div>
      <p class="sub-mesada">${situacao}</p>
      <div class="mesada-mes"><span>Recebido no mês <strong>${dinheiro(m.recebidoNoMes)}</strong></span><span>Gasto no mês <strong>${dinheiro(m.gastoNoMes)}</strong></span></div>
      ${meta}

      <details class="secao-mesada">
        <summary><span class="ico ico-settings"></span> ${m.configurada ? 'Alterar mesada' : 'Definir mesada'}</summary>
        <form class="form-mesada" data-form="config">
          <label>Valor por mês (R$)<input name="valor" type="number" min="0.01" step="0.01" required value="${m.valor ?? ''}"></label>
          <label>Dia do pagamento<input name="diaPagamento" type="number" min="1" max="31" required value="${m.diaPagamento ?? 1}"></label>
          <label>Objetivo (opcional)<input name="metaNome" maxlength="120" placeholder="Ex: bicicleta" value="${esc(m.metaNome ?? '')}"></label>
          <label>Valor do objetivo (R$)<input name="metaValor" type="number" min="0.01" step="0.01" value="${m.metaValor ?? ''}"></label>
          ${m.configurada ? `<label class="check-mesada"><input name="ativa" type="checkbox" ${m.ativa ? 'checked' : ''}> Mesada ativa</label>` : ''}
          <div class="acoes-form-mesada">
            <button type="submit"><span class="ico ico-save"></span> Salvar</button>
            ${m.configurada ? '<button type="button" class="btn-secundario" data-remover-config>Tirar mesada</button>' : ''}
          </div>
          <p class="dica-planejamento">A mesada entra sozinha no saldo no dia escolhido (nos meses mais curtos, no último dia).</p>
        </form>
      </details>

      <details class="secao-mesada">
        <summary><span class="ico ico-coins"></span> Lançar extra ou gasto</summary>
        <form class="form-mesada" data-form="movimento">
          <label>Tipo<select name="tipo"><option value="EXTRA">Extra (dinheiro a mais)</option><option value="GASTO">Gasto</option></select></label>
          <label>Valor (R$)<input name="valor" type="number" min="0.01" step="0.01" required></label>
          <label class="campo-largo">Descrição<input name="descricao" maxlength="160" required placeholder="Ex: ajudou a lavar o carro"></label>
          <label>Data<input name="data" type="date" value="${hoje()}"></label>
          <div class="acoes-form-mesada"><button type="submit">Lançar</button></div>
        </form>
      </details>

      <details class="secao-mesada">
        <summary><span class="ico ico-lock"></span> ${m.login ? 'Login do filho' : 'Criar login do filho'}</summary>
        <form class="form-mesada" data-form="acesso">
          <label>Usuário<input name="login" required minlength="3" maxlength="40" pattern="[A-Za-z0-9._\\-]{3,40}" autocapitalize="none" spellcheck="false"
            placeholder="Ex: ${esc((m.nome || '').split(' ')[0].toLowerCase().normalize('NFD').replace(/[^a-z0-9]/g, ''))}" value="${esc(m.login ?? '')}"></label>
          <label>${m.login ? 'Nova senha (deixe vazio para manter)' : 'Senha'}<input name="senha" type="password" minlength="6" maxlength="72" ${m.login ? '' : 'required'} autocomplete="new-password"></label>
          <div class="acoes-form-mesada">
            <button type="submit"><span class="ico ico-save"></span> ${m.login ? 'Salvar' : 'Criar login'}</button>
            ${m.login ? '<button type="button" class="btn-secundario" data-remover-acesso>Tirar o acesso</button>' : ''}
          </div>
          <p class="dica-planejamento">Na tela de entrada, o filho digita esse usuário no lugar do e-mail. Ele vê só a mesada, as tarefas dele e a lista de compras.</p>
        </form>
      </details>

      <details class="secao-mesada">
        <summary><span class="ico ico-clipboard"></span> Extrato (${m.movimentos.length})</summary>
        ${extrato(m)}
      </details>
    </div>`;
  }

  function render() {
    const el = document.getElementById('listaMesadas');
    if (!lista.length) {
      el.innerHTML = `<div class="card-box"><p class="dica-planejamento">Nenhum membro cadastrado ainda. Cadastre os filhos em
        <a href="familia.html">Família</a> e volte aqui para definir a mesada e o login de cada um.</p></div>`;
      return;
    }
    // Abertos antes de recarregar continuam abertos
    const abertos = new Set([...el.querySelectorAll('details[open]')].map(d => d.closest('[data-membro]').dataset.membro + '|' + d.querySelector('summary').textContent.trim().slice(0, 12)));
    el.innerHTML = `<div class="cards resumo-grade">${lista.map(cartao).join('')}</div>`;
    el.querySelectorAll('details').forEach(d => {
      if (abertos.has(d.closest('[data-membro]').dataset.membro + '|' + d.querySelector('summary').textContent.trim().slice(0, 12))) d.open = true;
    });
  }

  function atualizar(membroId, novo) {
    lista = lista.map(m => m.membroId === membroId ? novo : m);
    render();
  }

  async function carregar() {
    try {
      lista = await api.get('/mesada');
      render();
    } catch (e) {
      document.getElementById('listaMesadas').innerHTML = `<p class="dica-planejamento">Não foi possível carregar: ${esc(e.message)}</p>`;
    }
  }

  async function enviar(form, membroId) {
    const d = Object.fromEntries(new FormData(form));
    const botao = form.querySelector('button[type=submit]');
    botao.disabled = true;
    try {
      let r;
      if (form.dataset.form === 'config') {
        r = await api.put(`/mesada/${membroId}`, {
          valor: Number(d.valor), diaPagamento: Number(d.diaPagamento),
          ativa: form.querySelector('[name=ativa]') ? form.querySelector('[name=ativa]').checked : true,
          metaNome: d.metaNome || null, metaValor: d.metaNome && d.metaValor ? Number(d.metaValor) : null,
        });
      } else if (form.dataset.form === 'movimento') {
        r = await api.post(`/mesada/${membroId}/movimentos`, { tipo: d.tipo, valor: Number(d.valor), descricao: d.descricao, data: d.data || null });
      } else {
        r = await api.put(`/mesada/${membroId}/acesso`, { login: d.login, senha: d.senha || null });
        alert(`Login salvo. ${r.nome} entra com o usuário "${r.login}"${d.senha ? ' e a senha definida' : ''}.`);
      }
      atualizar(membroId, r);
    } catch (e) {
      alert(e.message);
      botao.disabled = false;
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const el = document.getElementById('listaMesadas');
    el.addEventListener('submit', e => {
      e.preventDefault();
      enviar(e.target, Number(e.target.closest('[data-membro]').dataset.membro));
    });
    el.addEventListener('click', async e => {
      const card = e.target.closest('[data-membro]');
      if (!card) return;
      const membroId = Number(card.dataset.membro);
      const nome = lista.find(m => m.membroId === membroId)?.nome;
      try {
        const mov = e.target.closest('[data-excluir-mov]');
        if (mov) {
          if (!confirm('Apagar este lançamento?')) return;
          atualizar(membroId, await api.del(`/mesada/${membroId}/movimentos/${mov.dataset.excluirMov}`));
        } else if (e.target.closest('[data-remover-config]')) {
          if (!confirm(`Tirar a mesada de ${nome}? O saldo e o extrato continuam guardados.`)) return;
          await api.del(`/mesada/${membroId}`);
          atualizar(membroId, await api.get(`/mesada/${membroId}`));
        } else if (e.target.closest('[data-remover-acesso]')) {
          if (!confirm(`Tirar o login de ${nome}? Ele não vai mais conseguir entrar.`)) return;
          atualizar(membroId, await api.del(`/mesada/${membroId}/acesso`));
        }
      } catch (erro) { alert(erro.message); }
    });
    carregar();
  });
})();
