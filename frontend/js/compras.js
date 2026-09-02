// ── CasaCapital — compras.js ──
document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('btnAddCompra').addEventListener('click', adicionarCompra);
  document.getElementById('compra').addEventListener('keydown', function (event) {
    if (event.key === 'Enter') adicionarCompra();
  });
});
