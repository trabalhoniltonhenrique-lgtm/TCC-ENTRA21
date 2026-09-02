// ── CasaCapital — tarefas.js ──
// A população do select de membros responsáveis é feita centralmente em app.js (window.onload).
document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('btnAddTarefa').addEventListener('click', adicionarTarefa);
  document.getElementById('tarefa').addEventListener('keydown', function (event) {
    if (event.key === 'Enter') adicionarTarefa();
  });
});
