// ── CasaCapital — analises.js (backend Java + MySQL) ──
// Os gráficos só existem se a chamada a GET /api/analises/resumo for bem-sucedida —
// para uma família Essencial, o backend responde 403 e nada é renderizado aqui.

function mostrarBloqueioPremium() {
  const container = document.querySelector('.analises-container');
  if (!container) return;
  container.innerHTML = `
    <div class="bloco-analises">
      <div class="secao" style="text-align:center;">
        <h2>🔒 Recurso Premium</h2>
        <p>Análises (gráficos, tendências e projeção financeira) estão disponíveis apenas no plano Premium.</p>
        <a href="configuracoes.html"><button class="btn-calcular-projecao" style="margin-top:1rem;">Ver planos em Configurações</button></a>
      </div>
    </div>`;
}

document.addEventListener('DOMContentLoaded', async function () {
  document.getElementById('compBtn6').addEventListener('click', function () { definirPeriodoComparativo(6); });
  document.getElementById('compBtn12').addEventListener('click', function () { definirPeriodoComparativo(12); });
  document.getElementById('btnCalcularProjecao').addEventListener('click', calcularProjecao);

  document.addEventListener('casacapital-dados-carregados', async function () {
    try {
      const dados = await api.get('/analises/resumo');
      renderGraficos(dados);
    } catch (erro) {
      if (erro.status === 403) {
        mostrarBloqueioPremium();
      } else {
        console.error('Falha ao carregar análises:', erro);
      }
    }
  });
});
