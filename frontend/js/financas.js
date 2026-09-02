// ── CasaCapital — financas.js ──
document.addEventListener('DOMContentLoaded', function () {
  const filtroMes = document.getElementById('filtroMes');
  if (filtroMes) filtroMes.addEventListener('change', aplicarFiltroMes);

  const btnLimparFiltro = document.getElementById('btnLimparFiltro');
  if (btnLimparFiltro) btnLimparFiltro.addEventListener('click', limparFiltro);

  const btnAddReceita = document.getElementById('btnAddReceita');
  if (btnAddReceita) btnAddReceita.addEventListener('click', adicionarReceita);

  const btnAddDespesa = document.getElementById('btnAddDespesa');
  if (btnAddDespesa) btnAddDespesa.addEventListener('click', adicionarDespesa);

  // A população dos selects de membro é feita centralmente em app.js (window.onload).
});
