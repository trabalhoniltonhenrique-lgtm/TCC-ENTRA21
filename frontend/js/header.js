// ── CasaCapital — header.js ──
(async function () {
  const script = document.currentScript;
  const pagina = location.pathname.split('/').pop() || 'dashboard.html';

  if (!estaAutenticado()) {
    location.href = 'index.html';
    return;
  }

  let familia;
  try {
    familia = await api.get('/familia/me');
  } catch (erro) {
    return; // api.js já redireciona para o login em caso de 401
  }

  window.__familia = familia; // cache simples para outras telas (moeda, separador, etc.)
  const isPremium = familia.plano === 'PREMIUM';

  const itens = [
    { href: 'dashboard.html', label: 'Início' },
    { href: 'financas.html',  label: 'Finanças' },
    { href: 'contas.html',    label: '📋 Contas' },
    { href: 'orcamento.html', label: '🎯 Orçamento' },
    { href: 'metas.html',     label: '🏆 Metas' },
    { href: 'familia.html',   label: '👨‍👩‍👧‍👦 Família' },
    { href: 'compras.html',   label: 'Compras' },
    { href: 'dispensa.html',  label: '🏠 Dispensa' },
    { href: 'historico-precos.html', label: '💰 Histórico de Preços' },
    { href: 'tarefas.html',   label: 'Tarefas' },
    ...(isPremium ? [
      { href: 'analises.html', label: '📊 Análises' },
      { href: 'alertas.html',  label: '🔔 Alertas'  },
    ] : []),
    { href: 'relatorios.html',    label: 'Relatórios' },
    { href: 'configuracoes.html', label: '⚙️ Config' },
  ];

  const badge = isPremium
    ? '<span class="badge-plano-header-nav premium">⭐ PREMIUM</span>'
    : '<span class="badge-plano-header-nav essencial">ESSENCIAL</span>';

  const navLinks = itens.map(i =>
    `<li><a href="${i.href}" ${pagina === i.href ? 'class="ativo"' : ''}>${i.label}</a></li>`
  ).join('');

  const nomeExibido = familia.nomeFamilia || 'CasaCapital';

  const html = `
    <header class="sidebar">
      <h1>${nomeExibido} ${badge}</h1>
      <nav>
        <ul>
          ${navLinks}
          <li class="menu-sair"><a href="#" id="linkSair">Sair</a></li>
        </ul>
        <button id="themeToggleBtn" class="theme-toggle" title="Alternar tema">🌙</button>
      </nav>
    </header>`;

  document.body.classList.add('tem-sidebar');

  if (!window.alternarTema) {
    const themeScript = document.createElement('script');
    themeScript.src = 'js/theme.js';
    themeScript.onload = function () { if (window.aplicarTemaSalvo) aplicarTemaSalvo(); };
    document.head.appendChild(themeScript);
  } else if (window.aplicarTemaSalvo) {
    setTimeout(aplicarTemaSalvo, 0);
  }

  if (script) {
    script.insertAdjacentHTML('afterend', html);
  } else {
    document.body.insertAdjacentHTML('afterbegin', html);
  }

  const themeBtn = document.getElementById('themeToggleBtn');
  if (themeBtn) {
    themeBtn.addEventListener('click', function () {
      if (window.alternarTema) alternarTema();
    });
  }

  const linkSair = document.getElementById('linkSair');
  if (linkSair) {
    linkSair.addEventListener('click', function (e) {
      e.preventDefault();
      limparSessao();
      location.href = 'home.html';
    });
  }

  document.dispatchEvent(new CustomEvent('familia-carregada', { detail: familia }));
})();
