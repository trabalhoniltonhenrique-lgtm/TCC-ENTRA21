// ── CasaCapital — orcamento.js ──
document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('orcCat').innerHTML = CATEGORIAS_DESPESA.map(c => `<option>${c}</option>`).join('');
  const mesAtual = new Date().toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  document.getElementById('labelMesOrc').textContent = 'Referente a ' + mesAtual.charAt(0).toUpperCase() + mesAtual.slice(1);

  document.getElementById('btnCopiarMes').addEventListener('click', usarOrcamentoMesAnterior);
  document.getElementById('btnSalvarOrcamento').addEventListener('click', adicionarOrcamento);

  document.addEventListener('casacapital-dados-carregados', () => renderOrcamentos());
});
