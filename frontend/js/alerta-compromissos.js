// ── CasaCapital — alerta-compromissos.js: alerta sonoro e visual dos compromissos ──
// Carregado pelo header.js em todas as telas. Consulta /compromissos/alertas a cada 30 s e mostra o aviso
// enquanto ninguém clicar "Ciente" (vale para todos os aparelhos: o servidor guarda quem dispensou).
(function () {
  if (window.CasaAlertas) return;
  const INTERVALO_MS = 30000;
  const REPETIR_SOM_MS = 60000;
  const MAX_REPETICOES = 5;
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  let alertas = [];
  const jaTocou = new Set();   // id|momento: alerta novo toca o som uma vez ao aparecer
  let repeticoes = 0, timerSom = null, timerTitulo = null;
  const tituloOriginal = document.title;

  // ── Som (Web Audio: não precisa de arquivo) ──
  // O navegador só deixa tocar som depois de um toque/clique na página: o primeiro toque libera.
  let audio = null;
  function contexto() {
    if (!audio) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return null;
      audio = new AC();
    }
    if (audio.state === 'suspended') audio.resume().catch(() => {});
    return audio;
  }
  ['pointerdown', 'keydown', 'touchstart'].forEach(ev =>
    document.addEventListener(ev, function liberar() {
      contexto();
      atualizarAvisoSom();
    }, { once: true, passive: true }));

  function somLiberado() { return !!audio && audio.state === 'running'; }

  function tocar() {
    const ctx = contexto();
    if (!ctx || ctx.state !== 'running') { atualizarAvisoSom(); return; }
    const t0 = ctx.currentTime + 0.05;
    // "ding-dong-ding" duas vezes
    [[880, 0], [660, 0.28], [988, 0.56], [880, 1.2], [660, 1.48], [988, 1.76]].forEach(([freq, t]) => {
      const osc = ctx.createOscillator(), vol = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;
      vol.gain.setValueAtTime(0.0001, t0 + t);
      vol.gain.exponentialRampToValueAtTime(0.35, t0 + t + 0.02);
      vol.gain.exponentialRampToValueAtTime(0.0001, t0 + t + 0.45);
      osc.connect(vol).connect(ctx.destination);
      osc.start(t0 + t);
      osc.stop(t0 + t + 0.5);
    });
    if (navigator.vibrate) navigator.vibrate([300, 120, 300, 120, 300]);
  }

  // ── Texto do horário ──
  function quando(c) {
    const ini = new Date(c.inicio);
    const hoje = new Date(); hoje.setHours(0, 0, 0, 0);
    const dia = new Date(ini); dia.setHours(0, 0, 0, 0);
    const dias = Math.round((dia - hoje) / 86400000);
    const hora = ini.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    const diaTxt = dias === 0 ? 'Hoje' : dias === 1 ? 'Amanhã' : dias === -1 ? 'Ontem' : ini.toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: '2-digit' });
    const min = Math.round((ini - Date.now()) / 60000);
    const falta = min > 0
      ? (min < 60 ? `em ${min} min` : min < 1440 ? `em ${Math.floor(min / 60)}h${String(min % 60).padStart(2, '0')}` : `em ${Math.round(min / 1440)} dia(s)`)
      : min === 0 ? 'agora' : `começou há ${-min} min`;
    return { texto: `${diaTxt} às ${hora}`, falta, atrasado: min < 0 };
  }

  // ── Janela do alerta ──
  function caixa() {
    let el = document.getElementById('alertaCompromissos');
    if (!el) {
      el = document.createElement('div');
      el.id = 'alertaCompromissos';
      el.className = 'alerta-comp-fundo oculto';
      el.setAttribute('role', 'alertdialog');
      el.setAttribute('aria-live', 'assertive');
      el.setAttribute('aria-label', 'Compromisso chegando');
      document.body.appendChild(el);
      el.addEventListener('click', acaoClique);
    }
    return el;
  }

  function atualizarAvisoSom() {
    const el = document.getElementById('alertaSomBloqueado');
    if (el) el.classList.toggle('oculto', somLiberado());
    const pag = document.getElementById('avisoSomCompromisso');
    if (pag) pag.textContent = somLiberado() ? 'Som do alerta ativado nesta tela.'
      : 'Dica: o navegador só libera o som depois do primeiro toque ou clique na tela do sistema.';
  }

  function render() {
    const el = caixa();
    if (!alertas.length) {
      el.classList.add('oculto');
      pararAtencao();
      return;
    }
    el.innerHTML = `<div class="alerta-comp-caixa">
      <div class="alerta-comp-sino"><span class="ico ico-bell"></span></div>
      <h3>${alertas.length > 1 ? alertas.length + ' compromissos chegando' : 'Compromisso chegando'}</h3>
      ${alertas.map(c => {
        const q = quando(c);
        return `<div class="alerta-comp-item" data-comp="${c.id}">
          <strong class="alerta-comp-titulo">${esc(c.titulo)}</strong>
          <div class="alerta-comp-hora ${q.atrasado ? 'atrasado' : ''}">${q.texto} · <b>${q.falta}</b></div>
          ${c.local ? `<div class="alerta-comp-det"><span class="ico ico-home"></span> ${esc(c.local)}</div>` : ''}
          ${c.membroNome ? `<div class="alerta-comp-det"><span class="ico ico-user"></span> ${esc(c.membroNome)}</div>` : ''}
          ${c.obs ? `<div class="alerta-comp-det">${esc(c.obs)}</div>` : ''}
          <div class="alerta-comp-acoes">
            <button data-ciente="${c.id}"><span class="ico ico-check"></span> Ciente</button>
            <button class="btn-secundario" data-adiar="${c.id}" data-min="5">Lembrar em 5 min</button>
            <button class="btn-secundario" data-adiar="${c.id}" data-min="15">15 min</button>
          </div>
        </div>`;
      }).join('')}
      <p id="alertaSomBloqueado" class="alerta-comp-som ${somLiberado() ? 'oculto' : ''}">🔇 Toque aqui para ouvir o som do alerta</p>
    </div>`;
    el.classList.remove('oculto');
  }

  function chamarAtencao(novo) {
    if (novo) { tocar(); repeticoes = 0; notificarSistema(); }
    if (!timerSom) {
      timerSom = setInterval(() => {
        if (!alertas.length || ++repeticoes > MAX_REPETICOES) { clearInterval(timerSom); timerSom = null; return; }
        tocar();
      }, REPETIR_SOM_MS);
    }
    if (!timerTitulo) {
      let alterna = false;
      timerTitulo = setInterval(() => {
        alterna = !alterna;
        document.title = alterna && alertas[0] ? '🔔 ' + alertas[0].titulo : tituloOriginal;
      }, 1000);
    }
  }

  function pararAtencao() {
    clearInterval(timerSom); timerSom = null;
    clearInterval(timerTitulo); timerTitulo = null;
    document.title = tituloOriginal;
  }

  // Aviso do Windows/Android (só quando a pessoa permitiu; o navegador só oferece em endereço seguro)
  function notificarSistema() {
    if (!('Notification' in window) || Notification.permission !== 'granted' || !document.hidden) return;
    alertas.forEach(c => {
      try { new Notification('CasaCapital — ' + c.titulo, { body: quando(c).texto + (c.local ? ' · ' + c.local : ''), tag: 'comp-' + c.id, requireInteraction: true }); }
      catch { /* navegador sem suporte */ }
    });
  }

  async function acaoClique(e) {
    if (e.target.closest('#alertaSomBloqueado')) { contexto(); setTimeout(() => { tocar(); atualizarAvisoSom(); }, 50); return; }
    const ciente = e.target.closest('[data-ciente]');
    const adiar = e.target.closest('[data-adiar]');
    const id = ciente ? ciente.dataset.ciente : adiar ? adiar.dataset.adiar : null;
    if (!id) return;
    e.target.closest('button').disabled = true;
    try {
      if (ciente) await api.post(`/compromissos/${id}/ciente`);
      else await api.post(`/compromissos/${id}/adiar?minutos=${adiar.dataset.min}`);
    } catch (erro) { alert(erro.message); }
    alertas = alertas.filter(c => String(c.id) !== String(id));
    render();
    document.dispatchEvent(new CustomEvent('compromissos-alterados'));
  }

  async function verificar() {
    if (!estaAutenticado()) return;
    let lista;
    try { lista = await api.get('/compromissos/alertas'); }
    catch { return; } // sem conexão: tenta na próxima
    let novo = false;
    lista.forEach(c => {
      const chave = c.id + '|' + c.momentoDoAviso;
      if (!jaTocou.has(chave)) { jaTocou.add(chave); novo = true; }
    });
    alertas = lista;
    render();
    if (alertas.length) chamarAtencao(novo);
  }

  window.CasaAlertas = {
    verificar,
    somLiberado,
    // Botão "Testar alerta": libera o som (é um clique) e toca uma vez
    testar() { contexto(); setTimeout(() => { tocar(); atualizarAvisoSom(); }, 50); },
    atualizarAvisoSom,
  };

  verificar();
  setInterval(verificar, INTERVALO_MS);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) verificar(); });
  // o relógio da tela (minutos que faltam) continua andando entre as consultas
  setInterval(() => { if (alertas.length) render(); }, 30000);
})();
