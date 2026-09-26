// ── CasaCapital — redefinir-senha.js ──

function getTokenDaUrl() {
  return new URLSearchParams(location.search).get('token');
}

function mostrarErroRedefinir(msg) {
  const el = document.getElementById('redefinirErro');
  if (!el) return;
  el.textContent = msg;
  el.classList.remove('oculto');
}

async function redefinirSenha() {
  const token = getTokenDaUrl();
  const novaSenha = document.getElementById('novaSenha').value;
  const confirmarNovaSenha = document.getElementById('confirmarNovaSenha').value;
  const btn = document.getElementById('btnRedefinirSenha');

  document.getElementById('redefinirErro').classList.add('oculto');

  if (!token) {
    mostrarErroRedefinir('Link inválido. Solicite uma nova redefinição de senha.');
    return;
  }
  if (!novaSenha || novaSenha.length < 6) {
    mostrarErroRedefinir('A senha deve ter ao menos 6 caracteres.');
    return;
  }
  if (novaSenha !== confirmarNovaSenha) {
    mostrarErroRedefinir('As senhas não coincidem.');
    return;
  }

  btn.disabled = true;
  btn.textContent = 'Redefinindo...';
  try {
    await api.post('/auth/redefinir-senha', { token, novaSenha });
    document.getElementById('novaSenha').disabled = true;
    document.getElementById('confirmarNovaSenha').disabled = true;
    const sucesso = document.getElementById('redefinirSucesso');
    sucesso.textContent = 'Senha redefinida com sucesso! Redirecionando para o login...';
    sucesso.classList.remove('oculto');
    setTimeout(function () { location.href = 'index.html'; }, 2000);
  } catch (erro) {
    mostrarErroRedefinir(erro.message || 'Não foi possível redefinir a senha.');
    btn.disabled = false;
    btn.textContent = 'Redefinir senha';
  }
}

document.addEventListener('DOMContentLoaded', function () {
  if (!getTokenDaUrl()) {
    mostrarErroRedefinir('Link inválido ou incompleto. Solicite uma nova redefinição de senha.');
  }
  const btn = document.getElementById('btnRedefinirSenha');
  if (btn) {
    btn.addEventListener('click', redefinirSenha);
  }
});
