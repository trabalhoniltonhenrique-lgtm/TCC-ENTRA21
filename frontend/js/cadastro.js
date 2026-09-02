// ── CasaCapital — cadastro.js ──

function mostrarErroCadastro(msg) {
  const el = document.getElementById('cadastroErro');
  if (!el) return;
  el.textContent = msg;
  el.classList.remove('oculto');
}

async function finalizarCadastro() {
  const nome = document.getElementById('cadNome').value.trim();
  const nomeFamilia = document.getElementById('cadNomeFamilia').value.trim();
  const email = document.getElementById('cadEmail').value.trim();
  const senha = document.getElementById('cadSenha').value;
  const confirmarSenha = document.getElementById('cadConfirmarSenha').value;
  const btn = document.getElementById('btnFinalizarCadastro');

  document.getElementById('cadastroErro').classList.add('oculto');

  if (!nome || !nomeFamilia || !email || !senha) {
    mostrarErroCadastro('Preencha todos os campos.');
    return;
  }
  if (senha.length < 6) {
    mostrarErroCadastro('A senha deve ter ao menos 6 caracteres.');
    return;
  }
  if (senha !== confirmarSenha) {
    mostrarErroCadastro('As senhas não coincidem.');
    return;
  }

  btn.disabled = true;
  btn.textContent = 'Criando conta...';
  try {
    const resp = await api.post('/auth/registrar', { nomeFamilia, nome, email, senha });
    setToken(resp.token);
    localStorage.removeItem('plano'); // agora o plano vem da API (familia.plano)
    location.href = 'onboarding.html';
  } catch (erro) {
    mostrarErroCadastro(erro.message || 'Não foi possível criar sua conta.');
    btn.disabled = false;
    btn.textContent = 'Finalizar Cadastro';
  }
}

document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-href]').forEach(function (el) {
    el.addEventListener('click', function () {
      location.href = el.getAttribute('data-href');
    });
  });

  const plano = localStorage.getItem('plano') || 'essencial';
  const badge = document.getElementById('badgePlano');
  const texto = document.getElementById('textoBadge');
  if (plano === 'premium') {
    badge.classList.add('premium');
    texto.innerHTML = '<strong>⭐ Plano Premium</strong> — R$ 39,90/mês';
  }

  const btn = document.getElementById('btnFinalizarCadastro');
  if (btn) btn.addEventListener('click', finalizarCadastro);
});
