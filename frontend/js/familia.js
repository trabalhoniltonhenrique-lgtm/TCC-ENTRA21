// ── CasaCapital — familia.js (backend Java + MySQL) ──
// Usa o array compartilhado `membros` (e `tarefas`/`despesas`) carregado por app.js.
const CORES = ['#2563EB','#16A34A','#DC2626','#7C3AED','#F59E0B','#0891B2','#EA580C','#DB2777','#64748B','#1E3A8A'];
let editandoId = null;
let corSelecionada = CORES[0];

function inicialDe(nome) {
  return (nome || '?').trim().charAt(0).toUpperCase();
}

function calcIdade(nascimento) {
  if (!nascimento) return null;
  const hoje = new Date();
  const nasc = new Date(nascimento + 'T12:00:00');
  let idade   = hoje.getFullYear() - nasc.getFullYear();
  const m     = hoje.getMonth() - nasc.getMonth();
  if (m < 0 || (m === 0 && hoje.getDate() < nasc.getDate())) idade--;
  return idade;
}

function fmtRenda(v) {
  return (typeof fmt === 'function' ? fmt(v) : 'R$ ' + Number(v || 0).toFixed(2).replace('.', ','));
}

function renderCores(selecionada) {
  const lista = document.getElementById('coresLista');
  lista.innerHTML = CORES.map(c =>
    `<div class="cor-op ${c === selecionada ? 'selecionada' : ''}"
          style="--cor-op:${c};"
          data-cor="${c}"></div>`
  ).join('');
}

function selecionarCor(cor) {
  corSelecionada = cor;
  renderCores(cor);
}

function abrirFormulario(id) {
  editandoId = id ?? null;
  const form = document.getElementById('formulario');
  form.classList.remove('oculto');
  form.scrollIntoView({ behavior: 'smooth', block: 'start' });

  if (id !== undefined && id !== null) {
    const m = membros.find(x => x.id === id);
    document.getElementById('tituloForm').textContent = 'Editar Membro';
    document.getElementById('fNome').value        = m.nome       || '';
    document.getElementById('fParentesco').value  = m.parentesco || '';
    document.getElementById('fNascimento').value  = m.nascimento || '';
    document.getElementById('fRenda').value       = m.renda      || '';
    document.getElementById('fObs').value         = m.obs        || '';
    corSelecionada = m.cor || CORES[0];
  } else {
    document.getElementById('tituloForm').textContent = 'Novo Membro';
    document.getElementById('fNome').value        = '';
    document.getElementById('fParentesco').value  = '';
    document.getElementById('fNascimento').value  = '';
    document.getElementById('fRenda').value       = '';
    document.getElementById('fObs').value         = '';
    corSelecionada = CORES[membros.length % CORES.length];
  }
  renderCores(corSelecionada);
}

function fecharFormulario() {
  document.getElementById('formulario').classList.add('oculto');
  editandoId = null;
}

async function salvarMembro() {
  const nome       = document.getElementById('fNome').value.trim();
  const parentesco = document.getElementById('fParentesco').value;
  const nascimento = document.getElementById('fNascimento').value || null;
  const renda      = parseFloat(document.getElementById('fRenda').value || 0);
  const obs        = document.getElementById('fObs').value.trim();

  if (!nome || !parentesco) {
    alert('Preencha pelo menos o nome e o parentesco.');
    return;
  }

  const payload = { nome, parentesco, nascimento, renda, obs, cor: corSelecionada };

  if (editandoId !== null) {
    const atualizado = await api.put('/membros/' + editandoId, payload);
    const idx = membros.findIndex(x => x.id === editandoId);
    if (idx >= 0) membros[idx] = atualizado;
  } else {
    const novo = await api.post('/membros', payload);
    membros.push(novo);
  }

  fecharFormulario();
  renderMembros();
}

async function excluirMembro(id) {
  const m = membros.find(x => x.id === id);
  if (!confirm(`Excluir ${m?.nome || 'este membro'}?`)) return;
  await api.del('/membros/' + id);
  membros = membros.filter(x => x.id !== id);
  renderMembros();
}

function renderMembros() {
  const lista = document.getElementById('listaMembros');
  const sub   = document.getElementById('subtitulo');
  const resumo = document.getElementById('resumoFamilia');

  if (!membros.length) {
    lista.innerHTML = `
      <div class="lista-vazia membros">
        <div class="icone">👨‍👩‍👧‍👦</div>
        <p>Nenhum membro cadastrado ainda.<br>Clique em <strong>+ Adicionar Membro</strong> para começar.</p>
      </div>`;
    sub.textContent = 'Nenhum membro cadastrado.';
    resumo.classList.add('oculto');
    return;
  }

  sub.textContent = `${membros.length} membro${membros.length > 1 ? 's' : ''} cadastrado${membros.length > 1 ? 's' : ''}.`;
  resumo.classList.remove('oculto');

  lista.innerHTML = membros.map(m => {
    const idade     = calcIdade(m.nascimento);
    const idadeStr  = idade !== null ? `${idade} anos` : '';
    const rendaStr  = m.renda ? fmtRenda(m.renda) + '/mês' : '';
    const detalhes  = [m.parentesco, idadeStr, rendaStr].filter(Boolean).join(' · ');

    const tarefasMembro = (typeof tarefas !== 'undefined' ? tarefas : []).filter(t => String(t.membroId) === String(m.id));
    const pendentes = tarefasMembro.filter(t => !t.concluida).length;

    const mesAtual = new Date().toISOString().slice(0,7);
    const gastoMes = (typeof despesas !== 'undefined' ? despesas : [])
      .filter(d => String(d.membroId) === String(m.id) && d.data?.startsWith(mesAtual))
      .reduce((a,b) => a + b.valor, 0);

    return `
      <div class="membro-card">
        <div class="avatar" style="--cor-avatar:${m.cor || '#2563EB'};">${inicialDe(m.nome)}</div>
        <div class="membro-info">
          <strong>${m.nome}</strong>
          <span>${detalhes}</span>
          ${m.obs ? `<div class="obs">📝 ${m.obs}</div>` : ''}
          <div class="tags-extra">
            ${pendentes ? `<span class="pill-tarefa">✅ ${pendentes} tarefa${pendentes>1?'s':''} pendente${pendentes>1?'s':''}</span>` : ''}
            ${gastoMes ? `<span class="pill-gasto">💸 ${fmtRenda(gastoMes)} este mês</span>` : ''}
          </div>
        </div>
        <div class="membro-acoes">
          <button class="btn-sm btn-secundario" data-action="editar" data-id="${m.id}" title="Editar">✏️</button>
          <button class="btn-sm btn-perigo" data-action="excluir" data-id="${m.id}" title="Excluir">✕</button>
        </div>
      </div>`;
  }).join('');

  const rendaTotal = membros.reduce((a, b) => a + (b.renda || 0), 0);
  const comRenda   = membros.filter(m => m.renda > 0).length;
  document.getElementById('totalMembros').textContent    = membros.length;
  document.getElementById('totalRendaFamilia').textContent = fmtRenda(rendaTotal);
  document.getElementById('mediaMembros').textContent    = comRenda
    ? fmtRenda(rendaTotal / comRenda)
    : '—';
}

document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('btnAddMembro').addEventListener('click', () => abrirFormulario());
  document.getElementById('btnSalvarMembro').addEventListener('click', salvarMembro);
  document.getElementById('btnCancelarMembro').addEventListener('click', fecharFormulario);

  document.getElementById('coresLista').addEventListener('click', function (e) {
    const corEl = e.target.closest('[data-cor]');
    if (corEl) selecionarCor(corEl.getAttribute('data-cor'));
  });

  document.getElementById('listaMembros').addEventListener('click', function (e) {
    const acaoEl = e.target.closest('[data-action]');
    if (!acaoEl) return;
    const id = Number(acaoEl.getAttribute('data-id'));
    if (acaoEl.getAttribute('data-action') === 'editar')  abrirFormulario(id);
    if (acaoEl.getAttribute('data-action') === 'excluir') excluirMembro(id);
  });

  renderCores(corSelecionada);
  document.addEventListener('casacapital-dados-carregados', renderMembros);
});
