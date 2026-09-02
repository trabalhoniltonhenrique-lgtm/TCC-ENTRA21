// ── CasaCapital — dashboard.js ──
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-href]').forEach(function (el) {
    el.addEventListener('click', function () {
      location.href = el.getAttribute('data-href');
    });
  });

  const filtroMes = document.getElementById('filtroMes');
  if (filtroMes) filtroMes.addEventListener('change', aplicarFiltroMes);

  const btnLimparFiltro = document.getElementById('btnLimparFiltro');
  if (btnLimparFiltro) btnLimparFiltro.addEventListener('click', limparFiltro);
});
