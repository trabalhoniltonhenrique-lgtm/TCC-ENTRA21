// ── CasaCapital — backup-status.js: situação do backup automático diário em Configurações ──
(function () {
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  async function carregar() {
    const el = document.getElementById('statusBackup');
    if (!el) return;
    let s;
    try {
      s = await api.get('/backup/status');
    } catch {
      el.className = 'status-backup aviso';
      el.textContent = 'Não foi possível consultar o backup agora.';
      return;
    }

    if (!s.configurado) {
      el.className = 'status-backup aviso';
      el.innerHTML = 'O backup automático é configurado pelo instalador (<strong>Instalar.bat</strong>). '
        + 'Este sistema não foi instalado por ele, então não há backup diário.';
      return;
    }
    if (!s.ultimoArquivo) {
      el.className = 'status-backup erro';
      el.innerHTML = 'Nenhum backup feito ainda.' + (s.mensagem ? ` Última tentativa: ${esc(s.mensagem)}` : '');
      return;
    }

    const quando = new Date(s.ultimoEm);
    const horas = (Date.now() - quando.getTime()) / 36e5;
    const atrasado = horas > 48; // deveria rodar todo dia
    const falhou = s.ultimaTentativaOk === false;
    el.className = 'status-backup ' + (falhou || atrasado ? 'erro' : 'ok');
    el.innerHTML = `
      <span class="ico ${falhou || atrasado ? 'ico-alert-triangle' : 'ico-check-circle'}"></span>
      Último backup: <strong>${quando.toLocaleDateString('pt-BR')} às ${quando.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</strong>
      (${s.tamanhoKb} KB) · ${s.quantidade} backup(s) guardado(s)
      ${falhou ? `<div>A última tentativa falhou: ${esc(String(s.mensagem || '').replace(/\.$/, ''))}. Veja C:\\ProgramData\\CasaCapital\\logs\\backup.log.</div>` : ''}
      ${!falhou && atrasado ? '<div>O último backup tem mais de 2 dias. Confira se a tarefa "CasaCapital - Backup diario" está ativa no Agendador de Tarefas do Windows.</div>' : ''}`;
  }

  document.addEventListener('DOMContentLoaded', carregar);
})();
