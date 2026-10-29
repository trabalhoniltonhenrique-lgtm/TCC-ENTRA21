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

  // Receitas e despesas começam recolhidas; cada botão do topo abre/fecha o seu quadro.
  // financas.html#receitas, #despesas ou #lancar (os dois) já abre aberto — usado pelo "+ Receita / Despesa" do Início.
  function alternarQuadro(id, abrir) {
    const quadro = document.getElementById(id);
    const botao = document.querySelector(`[data-abrir-quadro="${id}"]`);
    const aberto = abrir !== undefined ? abrir : quadro.classList.contains('oculto');
    quadro.classList.toggle('oculto', !aberto);
    botao.classList.toggle('ativo', aberto);
    botao.setAttribute('aria-expanded', String(aberto));
    return aberto;
  }
  document.querySelectorAll('[data-abrir-quadro]').forEach(function (botao) {
    botao.addEventListener('click', function () {
      const id = botao.getAttribute('data-abrir-quadro');
      if (alternarQuadro(id)) document.getElementById(id).scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });
  const pedido = location.hash.slice(1);
  if (pedido === 'receitas' || pedido === 'lancar') alternarQuadro('quadroReceitas', true);
  if (pedido === 'despesas' || pedido === 'lancar') alternarQuadro('quadroDespesas', true);
});
