// ── CasaCapital — onboarding.js ──
let atual = 1;
const total = 5;

function navStep(dir) {
  document.getElementById('step' + atual).classList.remove('ativo');
  document.getElementById('d' + atual).classList.remove('ativo');
  atual += dir;
  if (atual < 1) atual = 1;
  if (atual > total) { location.href = 'dashboard.html'; return; }
  document.getElementById('step' + atual).classList.add('ativo');
  document.getElementById('d' + atual).classList.add('ativo');
  document.getElementById('btnAnterior').classList.toggle('oculto', atual <= 1);
  document.getElementById('btnProximo').innerText = atual === total ? 'Começar!' : 'Próximo';
}

document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('btnAnterior').addEventListener('click', function () { navStep(-1); });
  document.getElementById('btnProximo').addEventListener('click', function () { navStep(1); });
});
