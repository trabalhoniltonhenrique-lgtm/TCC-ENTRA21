// ── CasaCapital — theme.js ──
// Aplica o tema salvo imediatamente, antes da página renderizar (evita "flash" claro/escuro)
(function () {
  const tema = localStorage.getItem('casacapital_tema') || 'claro';
  if (tema === 'escuro') {
    document.documentElement.classList.add('dark-pending');
  }
})();

function aplicarTemaSalvo() {
  const tema = localStorage.getItem('casacapital_tema') || 'claro';
  document.body.classList.toggle('dark', tema === 'escuro');
  document.documentElement.classList.remove('dark-pending');
  atualizarIconeTema();
}

function alternarTema() {
  const atual = localStorage.getItem('casacapital_tema') || 'claro';
  const novo  = atual === 'escuro' ? 'claro' : 'escuro';
  localStorage.setItem('casacapital_tema', novo);
  document.body.classList.toggle('dark', novo === 'escuro');
  atualizarIconeTema();
}

function atualizarIconeTema() {
  const btn = document.getElementById('themeToggleBtn');
  if (!btn) return;
  const escuro = document.body.classList.contains('dark');
  btn.textContent = escuro ? '☀️' : '🌙';
  btn.title = escuro ? 'Mudar para tema claro' : 'Mudar para tema escuro';
}

// Aplica assim que o DOM estiver pronto (o header.js insere o botão dinamicamente)
document.addEventListener('DOMContentLoaded', aplicarTemaSalvo);
