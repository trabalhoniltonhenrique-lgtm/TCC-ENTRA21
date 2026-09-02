// ── CasaCapital — index.js (login) ──

function mostrarErroLogin(msg) {
  const el = document.getElementById('loginErro');
  if (!el) return;
  el.textContent = msg;
  el.classList.remove('oculto');
}

async function entrar() {
  const email = document.getElementById('loginEmail').value.trim();
  const senha = document.getElementById('loginSenha').value;
  const btn = document.getElementById('btnEntrar');

  document.getElementById('loginErro').classList.add('oculto');

  if (!email || !senha) {
    mostrarErroLogin('Informe e-mail e senha.');
    return;
  }

  btn.disabled = true;
  btn.textContent = 'Entrando...';
  try {
    const resp = await api.post('/auth/login', { email, senha });
    setToken(resp.token);
    location.href = 'dashboard.html';
  } catch (erro) {
    mostrarErroLogin(erro.message || 'Não foi possível entrar. Verifique seus dados.');
    btn.disabled = false;
    btn.textContent = 'Entrar';
  }
}

document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('[data-href]').forEach(function (el) {
    el.addEventListener('click', function () {
      location.href = el.getAttribute('data-href');
    });
  });

  const btnEntrar = document.getElementById('btnEntrar');
  if (btnEntrar) {
    btnEntrar.addEventListener('click', entrar);
    document.getElementById('loginSenha').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') entrar();
    });
  }

  // Se já está logado, pula direto para o dashboard.
  if (estaAutenticado()) {
    location.href = 'dashboard.html';
  }
});
