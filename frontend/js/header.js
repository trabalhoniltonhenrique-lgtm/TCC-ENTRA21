// ── CasaCapital — header.js ──
(async function () {
  const script = document.currentScript;
  const pagina = location.pathname.split('/').pop() || 'dashboard.html';

  if (!estaAutenticado()) {
    location.href = 'index.html';
    return;
  }

  // O filho (perfil dependente) só tem a área dele
  const dependente = ehDependente();
  if (dependente && pagina !== 'minha-area.html') {
    location.replace('minha-area.html');
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

  const itens = dependente ? [
    { href: 'minha-area.html', label: '<span class="ico ico-coins"></span> Minha área' },
  ] : [
    { href: 'dashboard.html', label: '<span class="ico ico-dashboard"></span> Início' },
    { href: 'financas.html',  label: '<span class="ico ico-wallet"></span> Finanças' },
    { href: 'contas.html',    label: '<span class="ico ico-clipboard"></span> Contas' },
    { href: 'metas.html',     label: '<span class="ico ico-trophy"></span> Metas' },
    { href: 'familia.html',   label: '<span class="ico ico-users"></span> Família' },
    { href: 'mesada.html',    label: '<span class="ico ico-coins"></span> Mesada' },
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

  const badge = dependente ? '' : isPremium
    ? '<span class="badge-plano-header-nav premium"><span class="ico ico-star"></span> PREMIUM</span>'
    : '<span class="badge-plano-header-nav essencial">ESSENCIAL</span>';

  const navLinks = itens.map(i =>
    `<li><a href="${i.href}" ${pagina === i.href ? 'class="ativo"' : ''}>${i.label}</a></li>`
  ).join('');

  const nomeExibido = familia.nomeFamilia || 'CasaCapital';

  const html = `
    <header class="sidebar">
      <h1>${nomeExibido} ${badge}</h1>
      <button id="btnMenuCelular" class="btn-menu-celular" aria-expanded="false" aria-controls="menuPrincipal">☰ Menu</button>
      <nav>
        <ul id="menuPrincipal">
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

  // Celular: o menu fica recolhido atrás do botão "☰ Menu"
  const btnMenu = document.getElementById('btnMenuCelular');
  if (btnMenu) {
    btnMenu.addEventListener('click', function () {
      const header = btnMenu.closest('header');
      const aberto = header.classList.toggle('menu-aberto');
      btnMenu.setAttribute('aria-expanded', aberto);
      btnMenu.textContent = aberto ? '✕ Fechar' : '☰ Menu';
    });
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

  // Alerta sonoro e visual dos compromissos, em todas as telas
  if (!window.CasaAlertas) {
    const alertaScript = document.createElement('script');
    alertaScript.src = 'js/alerta-compromissos.js';
    document.head.appendChild(alertaScript);
  }

  // Foto da família no topo do menu (chega depois, sem atrasar o resto da tela)
  fotoDaFamilia(familia).then(function (dataUrl) {
    const h1 = document.querySelector('header.sidebar h1');
    if (!dataUrl || !h1) return;
    const img = document.createElement('img');
    img.className = 'foto-familia-menu';
    img.alt = 'Foto da família';
    img.src = dataUrl;
    h1.prepend(img);
  });

  document.dispatchEvent(new CustomEvent('familia-carregada', { detail: familia }));
})();
