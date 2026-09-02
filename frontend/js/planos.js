// ── CasaCapital — planos.js ──
async function escolherPlano(plano) {
  const logado = typeof estaAutenticado === 'function' && estaAutenticado();

  if (logado) {
    try {
      await api.put('/familia/plano', { plano: plano.toUpperCase() });
      alert(`Plano alterado para ${plano === 'premium' ? 'Premium' : 'Essencial'} com sucesso!`);
      location.href = 'configuracoes.html';
    } catch (erro) {
      alert(erro.message || 'Não foi possível alterar o plano. Tente novamente.');
    }
    return;
  }

  localStorage.setItem('plano', plano);
  location.href = 'cadastro.html';
}

document.addEventListener('DOMContentLoaded', function () {
  const logado = typeof estaAutenticado === 'function' && estaAutenticado();

  if (logado) {
    document.querySelectorAll('[data-plano]').forEach(function (btn) {
      btn.textContent = 'Mudar para ' + (btn.getAttribute('data-plano') === 'premium' ? 'Premium' : 'Essencial');
    });
    const voltar = document.querySelector('.menu-sair a');
    if (voltar) { voltar.textContent = 'Voltar às Configurações'; voltar.href = 'configuracoes.html'; }
  }

  document.querySelectorAll('[data-plano]').forEach(function (el) {
    el.addEventListener('click', function () {
      escolherPlano(el.getAttribute('data-plano'));
    });
  });
});
