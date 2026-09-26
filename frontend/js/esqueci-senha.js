// ── CasaCapital — esqueci-senha.js ──

function mostrarErroRecuperacao(msg) {
  const el = document.getElementById('recErro');
  if (!el) return;
  el.textContent = msg;
  el.classList.remove('oculto');
}

async function enviarRecuperacao() {
  const email = document.getElementById('recEmail').value.trim();
  const btn = document.getElementById('btnEnviarRecuperacao');

  document.getElementById('recErro').classList.add('oculto');
  document.getElementById('recSucesso').classList.add('oculto');

  if (!email) {
    mostrarErroRecuperacao('Informe seu e-mail.');
    return;
  }

  btn.disabled = true;
  btn.textContent = 'Enviando...';
  try {
    await api.post('/auth/esqueci-senha', { email });
    document.getElementById('recSucesso').textContent =
      'Se este e-mail estiver cadastrado, você receberá um link de redefinição em breve.';
    document.getElementById('recSucesso').classList.remove('oculto');
  } catch (erro) {
    mostrarErroRecuperacao(erro.message || 'Não foi possível enviar o e-mail.');
    btn.disabled = false;
    btn.textContent = 'Enviar link de redefinição';
  }
}

document.addEventListener('DOMContentLoaded', function () {
  const btn = document.getElementById('btnEnviarRecuperacao');
  if (btn) {
    btn.addEventListener('click', enviarRecuperacao);
    document.getElementById('recEmail').addEventListener('keydown', function (e) {
      if (e.key === 'Enter') enviarRecuperacao();
    });
  }
});
