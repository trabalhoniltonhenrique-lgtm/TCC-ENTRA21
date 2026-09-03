// ── CasaCapital — relatorios.js ──
document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('btnVisualizarRelatorio').addEventListener('click', visualizarRelatorio);
  document.getElementById('btnImprimirRelatorio').addEventListener('click', imprimirRelatorioSelecionado);
  document.getElementById('btnBaixarRelatorioTxt').addEventListener('click', baixarRelatorioTXT);
  document.getElementById('btnBaixarRelatorioPdf').addEventListener('click', baixarRelatorioPDF);

  // A checagem que vale de verdade é a de exigirPremiumOuAvisar() dentro de baixarRelatorioPDF();
  // isto aqui só evita mostrar o botão à toa para quem já sabemos ser Essencial.
  document.addEventListener('casacapital-dados-carregados', function () {
    document.getElementById('btnBaixarRelatorioPdf').classList.toggle('oculto', !isPremium());
  });
});
