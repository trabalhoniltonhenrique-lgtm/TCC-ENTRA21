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
  if (window.aplicarCorFamilia) aplicarCorFamilia(familia.corPrimaria);
  const isPremium = familia.plano === 'PREMIUM';

  const itens = [
    { href: 'dashboard.html', label: '<span class="ico ico-dashboard"></span> Início' },
    { href: 'financas.html',  label: '<span class="ico ico-wallet"></span> Finanças' },
    { href: 'contas.html',    label: '<span class="ico ico-clipboard"></span> Contas' },
    { href: 'orcamento.html', label: '<span class="ico ico-target"></span> Orçamento' },
    { href: 'metas.html',     label: '<span class="ico ico-trophy"></span> Metas' },
    { href: 'familia.html',   label: '<span class="ico ico-users"></span> Família' },
    { href: 'compras.html',   label: '<span class="ico ico-cart"></span> Compras' },
    { href: 'dispensa.html',  label: '<span class="ico ico-package"></span> Dispensa' },
    { href: 'historico-precos.html', label: '<span class="ico ico-tag"></span> Histórico de Preços' },
    { href: 'tarefas.html',   label: '<span class="ico ico-check-square"></span> Tarefas' },
    ...(isPremium ? [
      { href: 'analises.html', label: '<span class="ico ico-chart-bar"></span> Análises' },
      { href: 'alertas.html',  label: '<span class="ico ico-bell"></span> Alertas'  },
    ] : []),
    { href: 'configuracoes.html', label: '<span class="ico ico-settings"></span> Config' },
  ];

  const badge = isPremium
    ? '<span class="badge-plano-header-nav premium"><span class="ico ico-star"></span> PREMIUM</span>'
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
          <li class="menu-sair"><a href="#" id="linkSair"><span class="ico ico-logout"></span> Sair</a></li>
        </ul>
        <button id="themeToggleBtn" class="theme-toggle" title="Alternar tema"><span class="ico ico-moon"></span></button>
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
