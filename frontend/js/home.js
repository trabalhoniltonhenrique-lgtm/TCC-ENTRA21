// ── CasaCapital — home.js ──
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-href]').forEach(function (el) {
    el.addEventListener('click', function () {
      location.href = el.getAttribute('data-href');
    });
  });
});
