// ── CasaCapital — planos.js ──
function escolherPlano(plano) {
  localStorage.setItem('plano', plano);
  location.href = 'cadastro.html';
}

document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-plano]').forEach(function (el) {
    el.addEventListener('click', function () {
      escolherPlano(el.getAttribute('data-plano'));
    });
  });
});
