// ── CasaCapital — editor-foto.js ──
// Janela para enquadrar a foto da família antes de salvar: aproximar/afastar (controle, rodinha do mouse ou
// pinça com dois dedos), arrastar para posicionar e girar. A foto sai recortada no formato 4:3, o mesmo
// em que ela aparece no perfil e no menu.
//
// abrirEditorFoto(fonte) — fonte é um File/Blob ou uma URL de imagem; devolve uma Promise com o Blob JPEG
// recortado, ou null se a pessoa cancelar. Rejeita se o navegador não conseguir abrir a imagem.

(function () {
  const PROPORCAO = 4 / 3;
  const SAIDA_LARGURA = 1280, SAIDA_ALTURA = 960;
  const ZOOM_MAXIMO = 5;

  function carregarImagem(fonte) {
    return new Promise(function (ok, falha) {
      const url = typeof fonte === 'string' ? fonte : URL.createObjectURL(fonte);
      const img = new Image();
      img.onload = function () { ok({ img: img, url: url }); };
      img.onerror = function () {
        if (url !== fonte) URL.revokeObjectURL(url);
        falha(new Error('Não foi possível abrir esta imagem. Use uma foto JPG ou PNG.'));
      };
      img.src = url;
    });
  }

  window.abrirEditorFoto = async function (fonte) {
    const { img, url } = await carregarImagem(fonte);

    const estado = { zoom: 1, giro: 0, x: 0, y: 0 }; // x/y: deslocamento do centro, em fração da largura/altura do quadro

    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
      <div class="modal-box editor-foto" role="dialog" aria-modal="true" aria-labelledby="editorFotoTitulo">
        <h3 id="editorFotoTitulo"><span class="ico ico-users"></span> Ajustar a foto da família</h3>
        <p class="modal-sub">Arraste para posicionar. Use o controle, a rodinha do mouse ou dois dedos para aproximar.</p>
        <div class="editor-foto-quadro" tabindex="0" aria-label="Área da foto: arraste para posicionar">
          <canvas></canvas>
        </div>
        <div class="editor-foto-controles">
          <button type="button" class="btn-secundario editor-foto-btn" data-acao="menos" aria-label="Afastar">−</button>
          <input type="range" min="1" max="${ZOOM_MAXIMO}" step="0.01" value="1" aria-label="Aproximar ou afastar">
          <button type="button" class="btn-secundario editor-foto-btn" data-acao="mais" aria-label="Aproximar">+</button>
        </div>
        <div class="editor-foto-controles">
          <button type="button" class="btn-secundario editor-foto-btn-texto" data-acao="girar">↻ Girar</button>
          <button type="button" class="btn-secundario editor-foto-btn-texto" data-acao="centralizar">Centralizar</button>
        </div>
        <div class="modal-acoes">
          <button type="button" data-acao="salvar">Salvar foto</button>
          <button type="button" class="btn-secundario" data-acao="cancelar">Cancelar</button>
        </div>
      </div>`;
    document.body.appendChild(overlay);

    const quadro = overlay.querySelector('.editor-foto-quadro');
    const canvas = overlay.querySelector('canvas');
    const ctx = canvas.getContext('2d');
    const controle = overlay.querySelector('input[type=range]');

    // Tamanho da imagem já girada
    function dimensoes() {
      const deitada = estado.giro % 180 !== 0;
      return { w: deitada ? img.naturalHeight : img.naturalWidth, h: deitada ? img.naturalWidth : img.naturalHeight };
    }

    // Escala que faz a imagem cobrir o quadro inteiro com zoom 1
    function escalaBase(W, H) {
      const d = dimensoes();
      return Math.max(W / d.w, H / d.h);
    }

    // Não deixa sobrar espaço vazio: a foto sempre cobre o quadro
    function limitar() {
      const d = dimensoes();
      const s = escalaBase(PROPORCAO, 1) * estado.zoom; // quadro de referência 4:3 com altura 1
      const folgaX = Math.max(0, (d.w * s - PROPORCAO) / 2) / PROPORCAO;
      const folgaY = Math.max(0, (d.h * s - 1) / 2);
      estado.x = Math.min(folgaX, Math.max(-folgaX, estado.x));
      estado.y = Math.min(folgaY, Math.max(-folgaY, estado.y));
    }

    function desenhar(c, W, H) {
      const s = escalaBase(W, H) * estado.zoom;
      c.save();
      c.fillStyle = '#fff';
      c.fillRect(0, 0, W, H);
      c.translate(W / 2 + estado.x * W, H / 2 + estado.y * H);
      c.rotate(estado.giro * Math.PI / 180);
      c.scale(s, s);
      c.imageSmoothingQuality = 'high';
      c.drawImage(img, -img.naturalWidth / 2, -img.naturalHeight / 2);
      c.restore();
    }

    function redesenhar() {
      const r = quadro.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      const W = Math.max(1, Math.round(r.width * dpr)), H = Math.max(1, Math.round(r.height * dpr));
      if (canvas.width !== W || canvas.height !== H) { canvas.width = W; canvas.height = H; }
      desenhar(ctx, W, H);
    }

    function definirZoom(z) {
      estado.zoom = Math.min(ZOOM_MAXIMO, Math.max(1, z));
      controle.value = estado.zoom;
      limitar();
      redesenhar();
    }

    function mover(dxPx, dyPx) {
      const r = quadro.getBoundingClientRect();
      estado.x += dxPx / r.width;
      estado.y += dyPx / r.height;
      limitar();
      redesenhar();
    }

    // Arrastar (um dedo/mouse) e pinça (dois dedos)
    const ponteiros = new Map();
    let pinca = null;
    quadro.addEventListener('pointerdown', function (e) {
      quadro.setPointerCapture(e.pointerId);
      ponteiros.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (ponteiros.size === 2) {
        const [a, b] = [...ponteiros.values()];
        pinca = { distancia: Math.hypot(a.x - b.x, a.y - b.y), zoom: estado.zoom };
      }
    });
    quadro.addEventListener('pointermove', function (e) {
      const antes = ponteiros.get(e.pointerId);
      if (!antes) return;
      const agora = { x: e.clientX, y: e.clientY };
      ponteiros.set(e.pointerId, agora);
      if (ponteiros.size === 2 && pinca) {
        const [a, b] = [...ponteiros.values()];
        definirZoom(pinca.zoom * Math.hypot(a.x - b.x, a.y - b.y) / (pinca.distancia || 1));
      } else if (ponteiros.size === 1) {
        mover(agora.x - antes.x, agora.y - antes.y);
      }
    });
    function soltar(e) {
      ponteiros.delete(e.pointerId);
      if (ponteiros.size < 2) pinca = null;
    }
    quadro.addEventListener('pointerup', soltar);
    quadro.addEventListener('pointercancel', soltar);
    quadro.addEventListener('wheel', function (e) {
      e.preventDefault();
      definirZoom(estado.zoom * (e.deltaY < 0 ? 1.1 : 1 / 1.1));
    }, { passive: false });
    quadro.addEventListener('keydown', function (e) {
      const passo = 10;
      const teclas = { ArrowLeft: [passo, 0], ArrowRight: [-passo, 0], ArrowUp: [0, passo], ArrowDown: [0, -passo] };
      if (teclas[e.key]) { e.preventDefault(); mover(teclas[e.key][0], teclas[e.key][1]); }
      else if (e.key === '+' || e.key === '=') definirZoom(estado.zoom * 1.1);
      else if (e.key === '-') definirZoom(estado.zoom / 1.1);
    });

    controle.addEventListener('input', function () { definirZoom(parseFloat(controle.value)); });
    window.addEventListener('resize', redesenhar);

    return new Promise(function (resolver) {
      function fechar(resultado) {
        window.removeEventListener('resize', redesenhar);
        document.removeEventListener('keydown', teclaEsc);
        overlay.remove();
        if (url !== fonte) URL.revokeObjectURL(url);
        resolver(resultado);
      }
      function teclaEsc(e) { if (e.key === 'Escape') fechar(null); }
      document.addEventListener('keydown', teclaEsc);

      overlay.addEventListener('click', function (e) {
        const acao = e.target.closest('[data-acao]');
        if (!acao) return;
        switch (acao.getAttribute('data-acao')) {
          case 'mais': definirZoom(estado.zoom * 1.2); break;
          case 'menos': definirZoom(estado.zoom / 1.2); break;
          case 'girar':
            estado.giro = (estado.giro + 90) % 360;
            estado.x = 0; estado.y = 0;
            limitar(); redesenhar();
            break;
          case 'centralizar': estado.x = 0; estado.y = 0; definirZoom(1); break;
          case 'cancelar': fechar(null); break;
          case 'salvar': {
            const saida = document.createElement('canvas');
            saida.width = SAIDA_LARGURA;
            saida.height = SAIDA_ALTURA;
            desenhar(saida.getContext('2d'), SAIDA_LARGURA, SAIDA_ALTURA);
            saida.toBlob(function (blob) { fechar(blob); }, 'image/jpeg', 0.9);
            break;
          }
        }
      });

      requestAnimationFrame(redesenhar);
      quadro.focus();
    });
  };
})();
