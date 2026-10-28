// ── CasaCapital — editor-foto.js ──
// Janela para enquadrar a foto da família antes de salvar: aproximar/afastar (controle, rodinha do mouse ou
// pinça com dois dedos), arrastar para posicionar e girar. A foto sai recortada no formato 4:3, o mesmo
// em que ela aparece no perfil e no menu. Dá para afastar até a imagem caber inteira (um brasão ou uma foto em pé);
// a sobra fica com uma cor de fundo — por padrão a das bordas da própria imagem. "Sem fundo" apaga o fundo da
// própria imagem (a cor lisa que encosta nas bordas, como o preto de um brasão) e deixa tudo transparente.
//
// abrirEditorFoto(fonte) — fonte é um File/Blob ou uma URL de imagem; devolve uma Promise com o Blob recortado
// (JPEG, ou PNG quando sem fundo), ou null se a pessoa cancelar. Rejeita se o navegador não conseguir abrir a imagem.

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

  // Cor que mais aparece na borda da imagem (o fundo de um brasão, de um logotipo...). Usa a mais frequente,
  // não a média, para um enfeite que encosta na borda não "sujar" a cor.
  // Devolve { hex, rgb, transparente } — transparente = a borda já não tem fundo (PNG sem fundo).
  function analisarBordas(img) {
    const branco = { hex: '#FFFFFF', rgb: [255, 255, 255], transparente: false };
    try {
      const N = 64;
      const c = document.createElement('canvas');
      c.width = N; c.height = N;
      const k = c.getContext('2d');
      k.drawImage(img, 0, 0, N, N);
      const px = k.getImageData(0, 0, N, N).data;
      const grupos = new Map(); // cores parecidas caem no mesmo grupo
      let opacos = 0, transparentes = 0;
      for (let y = 0; y < N; y++) {
        for (let x = 0; x < N; x++) {
          if (x > 0 && x < N - 1 && y > 0 && y < N - 1) continue;
          const i = (y * N + x) * 4;
          if (px[i + 3] < 128) { transparentes++; continue; }
          opacos++;
          const chave = (px[i] >> 5) + ',' + (px[i + 1] >> 5) + ',' + (px[i + 2] >> 5);
          const grupo = grupos.get(chave) || { n: 0, r: 0, g: 0, b: 0 };
          grupo.n++; grupo.r += px[i]; grupo.g += px[i + 1]; grupo.b += px[i + 2];
          grupos.set(chave, grupo);
        }
      }
      if (!opacos || transparentes > opacos) return Object.assign({}, branco, { transparente: true });
      const maior = [...grupos.values()].reduce((a, b) => (b.n > a.n ? b : a));
      const rgb = [maior.r, maior.g, maior.b].map(v => Math.round(v / maior.n));
      return { hex: ('#' + rgb.map(v => v.toString(16).padStart(2, '0')).join('')).toUpperCase(), rgb: rgb, transparente: false };
    } catch {
      return branco;
    }
  }

  // Apaga o fundo da imagem: parte das bordas e remove os pontos de cor parecida com a do fundo que estão ligados a
  // elas (o miolo do desenho, mesmo da mesma cor, fica). Depois devolve uma faixa fina em volta do desenho, para ele
  // manter o contorno — num brasão, o traço preto que separa o desenho do fundo preto.
  function removerFundo(img, corFundo) {
    const lado = Math.max(img.naturalWidth, img.naturalHeight);
    const escala = Math.min(1, 2048 / lado);
    const W = Math.max(1, Math.round(img.naturalWidth * escala)), H = Math.max(1, Math.round(img.naturalHeight * escala));
    const c = document.createElement('canvas');
    c.width = W; c.height = H;
    const k = c.getContext('2d');
    k.drawImage(img, 0, 0, W, H);
    const dados = k.getImageData(0, 0, W, H);
    const px = dados.data;
    const [fr, fg, fb] = corFundo;
    const TOLERANCIA = 60;
    const parecido = i => px[i + 3] < 128 ||
      (Math.abs(px[i] - fr) <= TOLERANCIA && Math.abs(px[i + 1] - fg) <= TOLERANCIA && Math.abs(px[i + 2] - fb) <= TOLERANCIA);

    // 1 = fundo. Busca em largura a partir de todos os pontos da borda.
    const fundo = new Uint8Array(W * H);
    const fila = new Int32Array(W * H);
    let ini = 0, fim = 0;
    function semear(p) { if (!fundo[p] && parecido(p * 4)) { fundo[p] = 1; fila[fim++] = p; } }
    for (let x = 0; x < W; x++) { semear(x); semear((H - 1) * W + x); }
    for (let y = 0; y < H; y++) { semear(y * W); semear(y * W + W - 1); }
    while (ini < fim) {
      const p = fila[ini++], x = p % W;
      if (x > 0) semear(p - 1);
      if (x < W - 1) semear(p + 1);
      if (p >= W) semear(p - W);
      if (p < W * (H - 1)) semear(p + W);
    }

    // Contorno: devolve ao desenho os pontos de fundo a até "espessura" pontos dele
    const espessura = Math.max(2, Math.round(Math.max(W, H) / 400));
    for (let passo = 0; passo < espessura; passo++) {
      const devolver = [];
      for (let p = 0; p < W * H; p++) {
        if (!fundo[p]) continue;
        const x = p % W;
        if ((x > 0 && !fundo[p - 1]) || (x < W - 1 && !fundo[p + 1]) ||
            (p >= W && !fundo[p - W]) || (p < W * (H - 1) && !fundo[p + W])) devolver.push(p);
      }
      if (!devolver.length) break;
      devolver.forEach(p => { fundo[p] = 0; });
    }

    let removidos = 0;
    for (let p = 0; p < W * H; p++) if (fundo[p]) { px[p * 4 + 3] = 0; removidos++; }
    k.putImageData(dados, 0, 0);
    return { desenho: c, removidos: removidos / (W * H) };
  }

  window.abrirEditorFoto = async function (fonte) {
    const { img, url } = await carregarImagem(fonte);

    // zoom 1 = a imagem cobre o quadro; abaixo de 1 ela vai encolhendo até caber inteira (zoomMinimo)
    // x/y: deslocamento do centro, em fração da largura/altura do quadro
    const bordas = analisarBordas(img);
    let semFundo = null; // imagem com o fundo apagado, feita só quando a pessoa escolhe "Sem fundo"
    // fundo: 'sem' (transparente) ou uma cor; desenho: a imagem original ou a sem fundo
    const estado = { zoom: 1, giro: 0, x: 0, y: 0, fundo: bordas.transparente ? 'sem' : bordas.hex, desenho: img };

    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
      <div class="modal-box editor-foto" role="dialog" aria-modal="true" aria-labelledby="editorFotoTitulo">
        <h3 id="editorFotoTitulo"><span class="ico ico-users"></span> Ajustar a foto da família</h3>
        <p class="modal-sub">Arraste para posicionar. Use o controle, a rodinha do mouse ou dois dedos para aproximar
          ou afastar — afastando, a imagem cabe inteira.</p>
        <div class="editor-foto-quadro" tabindex="0" aria-label="Área da foto: arraste para posicionar">
          <canvas></canvas>
        </div>
        <div class="editor-foto-controles">
          <button type="button" class="btn-secundario editor-foto-btn" data-acao="menos" aria-label="Afastar">−</button>
          <input type="range" min="0.1" max="${ZOOM_MAXIMO}" step="0.01" value="1" aria-label="Aproximar ou afastar">
          <button type="button" class="btn-secundario editor-foto-btn" data-acao="mais" aria-label="Aproximar">+</button>
        </div>
        <div class="editor-foto-controles editor-foto-atalhos">
          <button type="button" class="btn-secundario editor-foto-btn-texto" data-acao="inteira">Imagem inteira</button>
          <button type="button" class="btn-secundario editor-foto-btn-texto" data-acao="preencher">Preencher</button>
          <button type="button" class="btn-secundario editor-foto-btn-texto" data-acao="girar">↻ Girar</button>
        </div>
        <div class="editor-foto-fundo">
          <span>Fundo:</span>
          <button type="button" class="editor-foto-cor editor-foto-cor-sem ${bordas.transparente ? 'selecionada' : ''}" data-fundo="sem"
                  title="Apaga o fundo da imagem (a cor lisa das bordas)" aria-label="Sem fundo: apagar o fundo da imagem">Sem fundo</button>
          <button type="button" class="editor-foto-cor ${bordas.transparente ? '' : 'selecionada'}" data-fundo="auto" title="Cor das bordas da imagem"
                  aria-label="Fundo com a cor das bordas da imagem" style="--cor-fundo:${bordas.hex}">Auto</button>
          <button type="button" class="editor-foto-cor" data-fundo="#FFFFFF" aria-label="Fundo branco" style="--cor-fundo:#FFFFFF">Branco</button>
          <button type="button" class="editor-foto-cor" data-fundo="#000000" aria-label="Fundo preto" style="--cor-fundo:#000000">Preto</button>
        </div>
        <p class="editor-foto-aviso" hidden></p>
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

    // Zoom em que a imagem inteira cabe no quadro (1 quando ela já tem o formato 4:3)
    function zoomMinimo() {
      const d = dimensoes();
      return Math.min(PROPORCAO / d.w, 1 / d.h) / Math.max(PROPORCAO / d.w, 1 / d.h);
    }

    // Maior que o quadro: a foto não deixa espaço vazio. Menor (afastada): não sai do quadro.
    function limitar() {
      const d = dimensoes();
      const s = escalaBase(PROPORCAO, 1) * estado.zoom; // quadro de referência 4:3 com altura 1
      const folgaX = Math.abs(d.w * s - PROPORCAO) / 2 / PROPORCAO;
      const folgaY = Math.abs(d.h * s - 1) / 2;
      estado.x = Math.min(folgaX, Math.max(-folgaX, estado.x));
      estado.y = Math.min(folgaY, Math.max(-folgaY, estado.y));
    }

    function desenhar(c, W, H) {
      const s = escalaBase(W, H) * estado.zoom;
      c.save();
      c.clearRect(0, 0, W, H);
      if (estado.fundo !== 'sem') {
        c.fillStyle = estado.fundo;
        c.fillRect(0, 0, W, H);
      }
      c.translate(W / 2 + estado.x * W, H / 2 + estado.y * H);
      c.rotate(estado.giro * Math.PI / 180);
      c.scale(s, s);
      c.imageSmoothingQuality = 'high';
      c.drawImage(estado.desenho, -img.naturalWidth / 2, -img.naturalHeight / 2, img.naturalWidth, img.naturalHeight);
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
      controle.min = zoomMinimo();
      estado.zoom = Math.min(ZOOM_MAXIMO, Math.max(zoomMinimo(), z));
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
        const corFundo = e.target.closest('[data-fundo]');
        if (corFundo) {
          const valor = corFundo.getAttribute('data-fundo');
          const aviso = overlay.querySelector('.editor-foto-aviso');
          aviso.hidden = true;
          if (valor === 'sem') {
            if (!bordas.transparente && !semFundo) semFundo = removerFundo(img, bordas.rgb);
            estado.desenho = semFundo ? semFundo.desenho : img;
            if (semFundo && semFundo.removidos < 0.02) {
              aviso.textContent = 'Não foi encontrado um fundo de cor lisa nas bordas desta imagem para apagar.';
              aviso.hidden = false;
            }
          } else {
            estado.desenho = img;
          }
          estado.fundo = valor === 'auto' ? bordas.hex : valor;
          quadro.classList.toggle('transparente', estado.fundo === 'sem');
          overlay.querySelectorAll('[data-fundo]').forEach(b => b.classList.toggle('selecionada', b === corFundo));
          redesenhar();
          return;
        }
        const acao = e.target.closest('[data-acao]');
        if (!acao) return;
        switch (acao.getAttribute('data-acao')) {
          case 'mais': definirZoom(estado.zoom * 1.2); break;
          case 'menos': definirZoom(estado.zoom / 1.2); break;
          case 'girar':
            estado.giro = (estado.giro + 90) % 360;
            estado.x = 0; estado.y = 0;
            definirZoom(estado.zoom); // o zoom mínimo muda quando a imagem fica deitada/em pé
            break;
          case 'inteira': estado.x = 0; estado.y = 0; definirZoom(zoomMinimo()); break;
          case 'preencher': estado.x = 0; estado.y = 0; definirZoom(1); break;
          case 'cancelar': fechar(null); break;
          case 'salvar': {
            const saida = document.createElement('canvas');
            saida.width = SAIDA_LARGURA;
            saida.height = SAIDA_ALTURA;
            desenhar(saida.getContext('2d'), SAIDA_LARGURA, SAIDA_ALTURA);
            if (estado.fundo === 'sem') saida.toBlob(function (blob) { fechar(blob); }, 'image/png');
            else saida.toBlob(function (blob) { fechar(blob); }, 'image/jpeg', 0.9);
            break;
          }
        }
      });

      // Imagem bem fora do formato 4:3 (em pé, brasão, panorama): começa inteira, para nada ficar cortado sem querer
      definirZoom(zoomMinimo() < 0.75 ? zoomMinimo() : 1);
      quadro.classList.toggle('transparente', estado.fundo === 'sem');
      requestAnimationFrame(redesenhar);
      quadro.focus();
    });
  };
})();
