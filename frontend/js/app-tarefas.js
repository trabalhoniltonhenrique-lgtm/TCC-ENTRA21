// ── CasaCapital — app-tarefas.js: quadro de tarefas (arrastar entre os dias) e card de atrasadas ──

// ── Tarefas ──
const DIAS_SEMANA = ['Segunda-feira','Terça-feira','Quarta-feira','Quinta-feira','Sexta-feira','Sábado','Domingo'];

async function adicionarTarefa() {
  const nome      = el('tarefa')?.value.trim();
  const prazo     = el('tarefaPrazo')?.value || null;
  const membroId  = el('tarefaMembro')?.value || null;
  const diaSemana = el('tarefaDia')?.value ? Number(el('tarefaDia').value) : null;
  if (!nome) return;
  const nova = await api.post('/tarefas', { nome, prazo, membroId, diaSemana });
  tarefas.push(nova);
  // Limpa o formulário inteiro: a próxima tarefa não herda o responsável nem o dia da anterior
  el('tarefa').value = '';
  if (el('tarefaPrazo')) el('tarefaPrazo').value = '';
  if (el('tarefaDia')) el('tarefaDia').value = '';
  if (el('tarefaMembro')) el('tarefaMembro').value = '';
  renderTarefas(); atualizarDashboard();
}

async function excluirTarefa(id) {
  if (!confirm('Excluir tarefa?')) return;
  await api.del('/tarefas/' + id);
  tarefas = tarefas.filter(x => x.id !== id);
  renderTarefas(); atualizarDashboard();
}

async function concluirTarefa(id) {
  const atualizada = await api.post(`/tarefas/${id}/concluir`);
  const idx = tarefas.findIndex(x => x.id === id);
  if (idx >= 0) tarefas[idx] = atualizada;
  renderTarefas(); atualizarDashboard();
}

async function moverTarefa(id, diaSemana) {
  const t = tarefas.find(x => x.id === id);
  if (!t || diaTarefa(t) === diaSemana) return;
  const atualizada = await api.put('/tarefas/' + id,
    { nome: t.nome, prazo: t.prazo, membroId: t.membroId, diaSemana });
  tarefas[tarefas.indexOf(t)] = atualizada;
  renderTarefas();
}

// Coluna do kanban: dia escolhido; senão o dia da semana do prazo; senão null ("Sem dia").
function diaTarefa(t) {
  if (t.diaSemana) return t.diaSemana;
  if (!t.prazo) return null;
  return new Date(t.prazo + 'T12:00:00').getDay() || 7;
}

function statusTarefa(t) {
  if (t.concluida) return { texto: 'Concluída', cls: 'status-concluida' };
  if (!t.prazo)    return null;
  const hojeStr = hoje();
  if (t.prazo < hojeStr)  return { texto: 'Atrasada', cls: 'status-atrasada' };
  if (t.prazo === hojeStr) return { texto: 'Hoje', cls: 'status-hoje' };
  return null;
}

function tagResponsavel(membroId) {
  const m = getMembroPorId(membroId);
  if (!m) return '';
  return `<div class="tag-responsavel" style="--cor-membro:${m.cor || '#2563EB'};"><span class="ico ico-user"></span> ${m.nome}</div>`;
}

function cartaoTarefa(t) {
  const st = statusTarefa(t);
  return `<div class="kanban-cartao ${t.concluida ? 'tarefa-concluida' : ''}" draggable="true" data-tarefa-id="${t.id}">
      <button class="btn-concluir-tarefa ${t.concluida ? 'feita' : ''}" data-action="concluir-tarefa" data-id="${t.id}" title="${t.concluida ? 'Reabrir' : 'Concluir'}">${t.concluida ? '<span class="ico ico-check"></span>' : ''}</button>
      <div class="tarefa-texto-wrap">
        <span class="kanban-cartao-nome ${t.concluida ? 'tarefa-riscada' : ''}">${t.nome}</span>
        ${t.prazo ? `<div class="tarefa-prazo"><span class="ico ico-calendar"></span> ${fmtD(t.prazo)}</div>` : ''}
        ${tagResponsavel(t.membroId)}
        ${st && !t.concluida ? `<span class="tag-status-tarefa ${st.cls}">${st.texto}</span>` : ''}
      </div>
      <button class="btn-excluir-cartao" data-action="excluir-tarefa" data-id="${t.id}" title="Excluir"><span class="ico ico-x"></span></button>
    </div>`;
}

function renderTarefas() {
  const quadro = el('kanbanTarefas');
  if (!quadro) return;
  const ordenadas = [...tarefas].sort((a, b) => {
    if (a.concluida !== b.concluida) return a.concluida ? 1 : -1;
    return (a.prazo || '9999').localeCompare(b.prazo || '9999');
  });
  const diaHoje = new Date().getDay() || 7;
  const colunas = DIAS_SEMANA.map((nome, i) => ({ dia: i + 1, nome }));
  if (ordenadas.some(t => diaTarefa(t) === null)) colunas.push({ dia: null, nome: 'Sem dia' });

  quadro.innerHTML = colunas.map(c => {
    const daColuna = ordenadas.filter(t => diaTarefa(t) === c.dia);
    return `<div class="kanban-coluna ${c.dia === diaHoje ? 'hoje' : ''}" data-dia="${c.dia ?? ''}">
      <h3>${c.nome}${c.dia === diaHoje ? ' <span class="kanban-hoje">hoje</span>' : ''}</h3>
      <div class="kanban-cartoes">
        ${daColuna.map(cartaoTarefa).join('') || '<div class="kanban-vazio">Nenhuma tarefa</div>'}
      </div>
    </div>`;
  }).join('');
}

// Arrastar cartões entre colunas (eventos delegados no documento, registrados uma vez).
document.addEventListener('dragstart', e => {
  const cartao = e.target.closest?.('.kanban-cartao');
  if (!cartao) return;
  e.dataTransfer.setData('text/plain', cartao.dataset.tarefaId);
  e.dataTransfer.effectAllowed = 'move';
  cartao.classList.add('arrastando');
});
document.addEventListener('dragend', e => e.target.closest?.('.kanban-cartao')?.classList.remove('arrastando'));
document.addEventListener('dragover', e => {
  const coluna = e.target.closest?.('.kanban-coluna');
  if (!coluna) return;
  e.preventDefault();
  document.querySelectorAll('.kanban-coluna.alvo').forEach(c => c !== coluna && c.classList.remove('alvo'));
  coluna.classList.add('alvo');
});
document.addEventListener('dragleave', e => {
  const coluna = e.target.closest?.('.kanban-coluna');
  if (coluna && !coluna.contains(e.relatedTarget)) coluna.classList.remove('alvo');
});
document.addEventListener('drop', e => {
  const coluna = e.target.closest?.('.kanban-coluna');
  if (!coluna) return;
  e.preventDefault();
  coluna.classList.remove('alvo');
  const id = Number(e.dataTransfer.getData('text/plain'));
  if (id) moverTarefa(id, coluna.dataset.dia ? Number(coluna.dataset.dia) : null);
});

// ── Card de tarefas atrasadas no Dashboard ──
function renderCardTarefasAtrasadas() {
  const card = el('cardTarefasAtrasadas');
  if (!card) return;
  const hojeStr = hoje();
  const atrasadas = tarefas.filter(t => !t.concluida && t.prazo && t.prazo < hojeStr);
  const hojeT     = tarefas.filter(t => !t.concluida && t.prazo === hojeStr);
  const urgentes  = [...atrasadas, ...hojeT];

  if (!urgentes.length) { card.innerHTML = ''; card.classList.add('oculto'); return; }
  card.classList.remove('oculto');
  card.innerHTML = `
    <div class="card-tarefas-urgentes">
      <div class="titulo-tarefas-urgentes"><span class="ico ico-alert-triangle"></span> Tarefas que precisam de atenção</div>
      ${urgentes.map(t => `
        <div class="linha-tarefa-urgente">
          <span>${t.prazo < hojeStr ? '<span class="ico ico-alert-circle"></span>' : '<span class="ico ico-calendar"></span>'} ${t.nome}</span>
          <span class="status-urgente-texto">${t.prazo < hojeStr ? 'Atrasada' : 'Hoje'}</span>
        </div>`).join('')}
    </div>`;
}
