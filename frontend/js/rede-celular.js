// ── CasaCapital — rede-celular.js: endereço e QR code para abrir o sistema no celular (Configurações) ──
(function () {

  // QR code (linhas de "0"/"1" vindas do servidor) desenhado em SVG, com a margem branca que os leitores pedem
  function svgQr(linhas) {
    const n = linhas.length, margem = 4, total = n + margem * 2;
    let quadros = '';
    linhas.forEach((linha, y) => {
      for (let x = 0; x < linha.length; x++) if (linha[x] === '1') quadros += `M${x + margem} ${y + margem}h1v1h-1z`;
    });
    return `<svg class="qr-celular" viewBox="0 0 ${total} ${total}" role="img" aria-label="QR code do endereço"
      shape-rendering="crispEdges"><rect width="${total}" height="${total}" fill="#fff"/><path d="${quadros}" fill="#000"/></svg>`;
  }

  async function carregar() {
    const el = document.getElementById('redeCelular');
    if (!el) return;
    // Aberto pelo próprio celular: o endereço já é o da barra do navegador
    if (!['localhost', '127.0.0.1'].includes(location.hostname)) {
      el.innerHTML = `<div class="status-backup ok"><span class="ico ico-check-circle"></span>
        Você já está usando o sistema pela rede: <strong>${esc(location.origin)}</strong></div>`;
      return;
    }
    try {
      const r = await api.get('/rede');
      if (!r.enderecos.length) {
        el.innerHTML = '<div class="status-backup aviso">Este computador não está conectado a uma rede Wi-Fi ou cabo agora.</div>';
        return;
      }
      el.innerHTML = r.enderecos.map(e => `
        <div class="endereco-celular">
          ${e.qr.length ? svgQr(e.qr) : ''}
          <div>
            <div class="config-label">No celular, abra:</div>
            <div class="url-celular">${esc(e.url)}</div>
            <div class="config-desc">${esc(e.adaptador)}</div>
          </div>
        </div>`).join('');
    } catch {
      el.innerHTML = '<div class="status-backup aviso">Não foi possível descobrir o endereço na rede agora.</div>';
    }
  }

  document.addEventListener('DOMContentLoaded', carregar);
})();
