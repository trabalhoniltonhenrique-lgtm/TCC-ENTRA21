// ── CasaCapital — configuracoes.js (backend Java + MySQL) ──
let cfg = null; // preenchido a partir de GET /api/familia/me

// ── Carregar na tela ──
async function carregarConfig() {
  cfg = await api.get('/familia/me');

  document.getElementById('cfgNomeFamilia').value      = cfg.nomeFamilia      || '';
  document.getElementById('cfgCidade').value           = cfg.cidade           || '';
  document.getElementById('cfgResponsavel').value      = cfg.responsavel      || '';
  document.getElementById('cfgDiaFechamento').value    = cfg.diaFechamento    || 1;
  document.getElementById('cfgTemaEscuro').checked     = (localStorage.getItem('casacapital_tema') || 'claro') === 'escuro';
  document.getElementById('cfgMostrarSaldo').checked   = cfg.mostrarSaldo     !== false;
  document.getElementById('cfgAlertaContas').checked   = cfg.alertaContas     !== false;
  document.getElementById('cfgConfirmarExclusao').checked = cfg.confirmarExclusao !== false;
  document.getElementById('cfgAgruparCat').checked     = cfg.agruparCat       || false;

  document.querySelectorAll('.moeda-op').forEach(b => {
    if (b.textContent.startsWith(cfg.moeda)) b.classList.add('ativo');
    else b.classList.remove('ativo');
  });

  document.getElementById('sepVirgula').classList.toggle('ativo', cfg.separadorDecimal !== '.');
  document.getElementById('sepPonto').classList.toggle('ativo',   cfg.separadorDecimal === '.');

  const plano = cfg.plano || 'ESSENCIAL';
  document.getElementById('cfgNomePlano').innerHTML    = plano === 'PREMIUM' ? '<span class="ico ico-star"></span> Plano Premium' : '<span class="ico ico-clipboard"></span> Plano Essencial';
  document.getElementById('cfgDescPlano').textContent  = plano === 'PREMIUM' ? 'R$ 39,90/mês — Análises, alertas e relatórios avançados' : 'R$ 19,90/mês — Controle básico e organização familiar';

  previewAvatar();
}

function toggleTemaConfig() {
  const ativo = document.getElementById('cfgTemaEscuro').checked;
  localStorage.setItem('casacapital_tema', ativo ? 'escuro' : 'claro');
  document.body.classList.toggle('dark', ativo);
  if (window.atualizarIconeTema) atualizarIconeTema();
}

function previewAvatar() {
  const nome = document.getElementById('cfgNomeFamilia').value.trim();
  const av   = document.getElementById('avatarFamilia');
  av.textContent = nome ? nome.charAt(0).toUpperCase() : '?';
}

function selecionarMoeda(moeda, btn) {
  cfg.moeda = moeda;
  document.querySelectorAll('#moedasOps .moeda-op').forEach(b => b.classList.remove('ativo'));
  btn.classList.add('ativo');
}

function selecionarSep(sep, btn) {
  cfg.separadorDecimal = sep;
  document.getElementById('sepVirgula').classList.toggle('ativo', sep === ',');
  document.getElementById('sepPonto').classList.toggle('ativo',   sep === '.');
}

// ── Salvar ──
async function salvarConfig() {
  cfg.nomeFamilia       = document.getElementById('cfgNomeFamilia').value.trim();
  cfg.cidade            = document.getElementById('cfgCidade').value.trim();
  cfg.responsavel       = document.getElementById('cfgResponsavel').value.trim();
  cfg.diaFechamento     = parseInt(document.getElementById('cfgDiaFechamento').value || 1);
  cfg.mostrarSaldo      = document.getElementById('cfgMostrarSaldo').checked;
  cfg.alertaContas      = document.getElementById('cfgAlertaContas').checked;
  cfg.confirmarExclusao = document.getElementById('cfgConfirmarExclusao').checked;
  cfg.agruparCat        = document.getElementById('cfgAgruparCat').checked;

  try {
    cfg = await api.put('/familia/me', {
      nomeFamilia: cfg.nomeFamilia, cidade: cfg.cidade, responsavel: cfg.responsavel,
      moeda: cfg.moeda, separadorDecimal: cfg.separadorDecimal, diaFechamento: cfg.diaFechamento,
      mostrarSaldo: cfg.mostrarSaldo, alertaContas: cfg.alertaContas,
      confirmarExclusao: cfg.confirmarExclusao, agruparCat: cfg.agruparCat,
    });
    toast('Configurações salvas com sucesso!', '#16A34A');
  } catch (erro) {
    toast((erro.message || 'Não foi possível salvar.'), '#DC2626');
  }
}

// ── Exportar / Importar / Apagar ──
async function exportarDados() {
  const [receitas, despesas, compras, tarefas, alertas, dispensa, contas, membros] = await Promise.all([
    api.get('/receitas'), api.get('/despesas'), api.get('/compras'), api.get('/tarefas'),
    cfg.plano === 'PREMIUM' ? api.get('/alertas').catch(() => []) : Promise.resolve([]),
    api.get('/dispensa'), api.get('/contas'), api.get('/membros'),
  ]);

  const dados = {
    exportadoEm: new Date().toISOString(),
    versao: 'v1-backend',
    config: cfg,
    receitas, despesas, compras, tarefas, alertas, dispensa, contas, membros,
    plano: cfg.plano,
  };
  const nome = (cfg.nomeFamilia || 'CasaCapital').replace(/\s+/g, '_');
  const blob = new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' });
  const a    = document.createElement('a');
  a.href     = URL.createObjectURL(blob);
  a.download = `${nome}_backup_${new Date().toISOString().slice(0,10)}.json`;
  a.click();
  toast('Backup exportado com sucesso!', '#2563EB');
}

function importarDados(event) {
  const file = event.target.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = async e => {
    try {
      const dados = JSON.parse(e.target.result);
      if (!confirm(`Importar dados do arquivo "${file.name}"?\nOs itens do backup serão ADICIONADOS aos dados atuais (nada é substituído).`)) return;

      const tarefasImport = [
        ...(dados.receitas || []).map(r => api.post('/receitas', { nome: r.nome, valor: r.valor, categoria: r.categoria, data: r.data, membroId: null })),
        ...(dados.despesas || []).map(d => api.post('/despesas', { nome: d.nome, valor: d.valor, categoria: d.categoria, data: d.data, membroId: null })),
        ...(dados.compras  || []).map(c => api.post('/compras',  { nome: c.nome, qtd: c.qtd || 1, categoria: c.categoria, frequencia: FREQ_API[c.frequencia] || c.frequencia || 'SEM_FREQUENCIA' })),
        ...(dados.tarefas  || []).map(t => api.post('/tarefas',  { nome: t.nome, prazo: t.prazo || null })),
        ...(dados.membros  || []).map(m => api.post('/membros',  { nome: m.nome, parentesco: m.parentesco || 'Outro', nascimento: m.nascimento || null, renda: m.renda || 0, obs: m.obs || '', cor: m.cor || null })),
      ];

      await Promise.all(tarefasImport);
      toast('Dados importados! Recarregando...', '#16A34A');
      setTimeout(() => location.reload(), 1500);
    } catch (erro) {
      toast('Arquivo inválido ou erro ao importar. Verifique e tente novamente.', '#DC2626');
    }
  };
  reader.readAsText(file);
}

async function apagarTudo() {
  if (!confirm('Isso apagará TODOS os dados financeiros, contas, compras e membros da família.\n\nTem certeza? Esta ação não pode ser desfeita.')) return;
  if (!confirm('Confirme novamente: apagar todos os dados permanentemente?')) return;
  try {
    await api.post('/familia/apagar-dados');
    toast('Dados apagados. Redirecionando...', '#DC2626');
    setTimeout(() => location.href = 'dashboard.html', 1500);
  } catch (erro) {
    toast((erro.message || 'Não foi possível apagar os dados.'), '#DC2626');
  }
}

// ── Toast de feedback ──
function toast(msg, cor) {
  const t = document.createElement('div');
  t.textContent = msg;
  t.className = 'toast-feedback';
  t.style.background = cor;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 2800);
}

document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('cfgNomeFamilia').addEventListener('input', previewAvatar);
  document.getElementById('cfgTemaEscuro').addEventListener('change', toggleTemaConfig);
  document.getElementById('cfgMostrarSaldo').addEventListener('change', salvarConfig);
  document.getElementById('cfgAlertaContas').addEventListener('change', salvarConfig);
  document.getElementById('cfgConfirmarExclusao').addEventListener('change', salvarConfig);
  document.getElementById('cfgAgruparCat').addEventListener('change', salvarConfig);

  document.querySelectorAll('#moedasOps .moeda-op').forEach(btn => {
    btn.addEventListener('click', function () { selecionarMoeda(btn.getAttribute('data-moeda'), btn); });
  });
  document.getElementById('sepVirgula').addEventListener('click', function () { selecionarSep(',', this); });
  document.getElementById('sepPonto').addEventListener('click', function () { selecionarSep('.', this); });

  document.getElementById('btnAlterarPlano').addEventListener('click', function () { location.href = 'planos.html'; });
  document.getElementById('btnExportarDados').addEventListener('click', exportarDados);
  document.getElementById('importFile').addEventListener('change', importarDados);
  document.getElementById('btnAbrirImportar').addEventListener('click', function () {
    document.getElementById('importFile').click();
  });
  document.getElementById('btnApagarTudo').addEventListener('click', apagarTudo);

  document.getElementById('btnSalvarConfigPagina').addEventListener('click', salvarConfig);
  document.getElementById('btnVoltarDashboard').addEventListener('click', function () { location.href = 'dashboard.html'; });

  carregarConfig();
});
