// ── CasaCapital — nota-fiscal.js: importação da nota do mercado (NFC-e) pelo QR code ──
(function () {
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const dinheiro = v => 'R$ ' + (Number(v) || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const qtdTxt = v => Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 3 });
  const dataBR = d => d ? new Date(d + 'T12:00:00').toLocaleDateString('pt-BR') : '—';
  const CATS = ['Geral', 'Hortifruti', 'Laticínios', 'Carnes', 'Bebidas', 'Higiene', 'Limpeza', 'Frios', 'Padaria', 'Outros'];
  const CATS_DESPESA = ['Alimentação', 'Moradia', 'Transporte', 'Saúde', 'Educação', 'Lazer', 'Vestuário', 'Contas & Serviços', 'Outros'];
  const $ = id => document.getElementById(id);

  let nota = null;      // resposta de /nfce/ler
  let linkAtual = null; // link do QR (para o texto colado achar a chave)

  function status(msg, erro) {
    const el = $('nfceStatus');
    el.textContent = msg || '';
    el.classList.toggle('oculto', !msg);
    el.classList.toggle('erro', !!erro);
  }

  function mostrar(passo) {
    ['passoColar', 'passoRevisar', 'nfceResultado'].forEach(id => $(id).classList.toggle('oculto', id !== passo));
  }

  // Foto grande de celular: reduz antes de enviar (o QR continua legível e o envio fica rápido)
  async function reduzirFoto(arquivo) {
    try {
      const img = await createImageBitmap(arquivo);
      const maior = Math.max(img.width, img.height);
      if (maior <= 2400) return arquivo;
      const escala = 2400 / maior;
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.width * escala);
      canvas.height = Math.round(img.height * escala);
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
      return await new Promise(ok => canvas.toBlob(b => ok(b || arquivo), 'image/jpeg', 0.92));
    } catch { return arquivo; } // formato que o navegador não abre: manda como está
  }

  async function ler(dados, mensagem) {
    status(mensagem);
    try {
      const r = await api.post('/nfce/ler', dados);
      status('');
      if (r.link) linkAtual = r.link;
      if (r.precisaColar) {
        $('nfceAbrirLink').href = r.link;
        $('nfceTexto').value = '';
        mostrar('passoColar');
        if (r.importadaEm) status(`Atenção: esta nota já foi importada em ${new Date(r.importadaEm).toLocaleString('pt-BR')}.`, true);
        return;
      }
      nota = r;
      renderRevisao();
      mostrar('passoRevisar');
      $('passoRevisar').scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (e) {
      status(e.message, true);
    }
  }

  function variacao(i) {
    if (!i.precoAnterior) return '<span class="nfce-novo">primeira compra</span>';
    const pct = (Number(i.valorUnitario) - Number(i.precoAnterior)) / Number(i.precoAnterior) * 100;
    const txt = `antes ${dinheiro(i.precoAnterior)} (${dataBR(i.dataPrecoAnterior)})`;
    if (Math.abs(pct) < 0.5) return `<span class="var-neutra">${txt} · igual</span>`;
    return `<span class="${pct > 0 ? 'var-ruim' : 'var-boa'}">${txt} · ${pct > 0 ? '+' : ''}${pct.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%</span>`;
  }

  function renderRevisao() {
    $('nfceEmitente').value = nota.emitente || '';
    $('nfceData').value = nota.dataEmissao || new Date().toLocaleDateString('sv-SE');
    $('nfceTotal').value = nota.total ?? '';
    $('nfceTodosPreco').checked = true;
    $('nfceTodosDespensa').checked = true;
    const aviso = $('nfceAvisoRepetida');
    aviso.classList.toggle('oculto', !nota.importadaEm);
    aviso.innerHTML = nota.importadaEm
      ? `<span class="ico ico-alert-triangle"></span> Esta nota já foi importada em ${new Date(nota.importadaEm).toLocaleString('pt-BR')}. Importar de novo duplicaria os lançamentos.` : '';
    $('btnImportarNfce').disabled = !!nota.importadaEm;

    $('nfceItens').innerHTML = nota.itens.map((i, n) => `
      <div class="nfce-item" data-i="${n}">
        <div class="nfce-item-topo">
          <input class="nfce-nome" value="${esc(i.nome)}" maxlength="160" aria-label="Nome do produto">
          <strong>${dinheiro(i.valorTotal)}</strong>
        </div>
        <div class="nfce-item-info">${qtdTxt(i.quantidade)} ${esc(i.unidade || 'UN')} × ${dinheiro(i.valorUnitario)} · ${variacao(i)}</div>
        <div class="nfce-item-opcoes">
          <label><input type="checkbox" class="nfce-preco" checked> Guardar preço</label>
          <label><input type="checkbox" class="nfce-despensa" checked> Despensa</label>
          <input type="number" class="nfce-qtd" min="1" max="999" value="${i.qtdDespensa}" aria-label="Quantidade na despensa">
          <select class="nfce-cat" aria-label="Categoria na despensa">${CATS.map(c => `<option ${c === i.categoria ? 'selected' : ''}>${c}</option>`).join('')}</select>
        </div>
      </div>`).join('');
  }

  async function importar() {
    const itens = [...document.querySelectorAll('.nfce-item')].map(div => ({
      nome: div.querySelector('.nfce-nome').value.trim(),
      codigo: nota.itens[div.dataset.i].codigo || null,
      valorUnitario: Number(nota.itens[div.dataset.i].valorUnitario),
      registrarPreco: div.querySelector('.nfce-preco').checked,
      despensa: div.querySelector('.nfce-despensa').checked,
      qtdDespensa: Math.max(1, Math.min(999, Number(div.querySelector('.nfce-qtd').value) || 1)),
      categoria: div.querySelector('.nfce-cat').value,
    })).filter(i => i.nome);
    const total = Number($('nfceTotal').value);
    if (!total || total <= 0) { alert('Informe o total pago.'); return; }

    const btn = $('btnImportarNfce');
    btn.disabled = true;
    try {
      const r = await api.post('/nfce/importar', {
        chave: nota.chave, emitente: $('nfceEmitente').value.trim() || null, cnpj: nota.cnpj || null,
        dataEmissao: $('nfceData').value || null,
        total, itens, lancarDespesa: $('nfceLancarDespesa').checked, categoriaDespesa: $('nfceCategoriaDespesa').value,
      });
      $('nfceResultado').innerHTML = `
        <h3><span class="ico ico-check-circle"></span> Nota importada</h3>
        <ul class="lista-resumo">
          <li><span>Preços guardados no histórico</span><strong>${r.precosRegistrados}</strong></li>
          <li><span>Itens que entraram na despensa</span><strong>${r.itensNaDespensa}</strong></li>
          <li><span>Despesa lançada</span><strong>${r.despesaId ? dinheiro(total) : 'não'}</strong></li>
        </ul>
        <div class="atalhos-financas">
          <a href="dispensa.html" class="atalho-financas"><span class="ico ico-package"></span> Ver despensa</a>
          <a href="historico-precos.html" class="atalho-financas"><span class="ico ico-tag"></span> Ver histórico de preços</a>
          <a href="mercados.html" class="atalho-financas"><span class="ico ico-chart-bar"></span> Comparar mercados</a>
          <a href="nota-fiscal.html" class="atalho-financas"><span class="ico ico-note"></span> Ler outra nota</a>
        </div>`;
      mostrar('nfceResultado');
      nota = null;
      carregarHistorico();
    } catch (e) {
      alert(e.message);
      btn.disabled = false;
    }
  }

  async function carregarHistorico() {
    const ul = $('nfceHistorico');
    try {
      const lista = await api.get('/nfce');
      ul.innerHTML = lista.length ? lista.map(n => `
        <li><span>${esc(n.emitente || 'Nota fiscal')} <small>${dataBR(n.dataEmissao)} · ${n.itens} itens</small></span><strong>${dinheiro(n.total)}</strong></li>`).join('')
        : '<li class="dica-planejamento">Nenhuma nota importada ainda.</li>';
    } catch { ul.innerHTML = ''; }
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('nfceCategoriaDespesa').innerHTML = CATS_DESPESA.map(c => `<option ${c === 'Alimentação' ? 'selected' : ''}>${c}</option>`).join('');

    $('nfceFoto').addEventListener('change', async e => {
      const arquivo = e.target.files[0];
      e.target.value = '';
      if (!arquivo) return;
      const fd = new FormData();
      fd.append('foto', await reduzirFoto(arquivo), 'qrcode.jpg');
      linkAtual = null;
      ler(fd, 'Lendo o QR code e abrindo a nota no site da Fazenda…');
    });

    $('formLinkNfce').addEventListener('submit', e => {
      e.preventDefault();
      const link = $('nfceLink').value.trim();
      if (!link) return;
      const fd = new FormData();
      fd.append('link', link);
      linkAtual = link;
      ler(fd, 'Abrindo a nota no site da Fazenda…');
    });

    $('formTextoNfce').addEventListener('submit', e => {
      e.preventDefault();
      const texto = $('nfceTexto').value;
      if (!texto.trim()) { alert('Cole o texto da página da nota.'); return; }
      const fd = new FormData();
      fd.append('texto', texto);
      if (linkAtual) fd.append('link', linkAtual);
      ler(fd, 'Lendo o texto da nota…');
    });

    $('nfceTodosPreco').addEventListener('change', e => document.querySelectorAll('.nfce-preco').forEach(c => { c.checked = e.target.checked; }));
    $('nfceTodosDespensa').addEventListener('change', e => document.querySelectorAll('.nfce-despensa').forEach(c => { c.checked = e.target.checked; }));
    $('btnImportarNfce').addEventListener('click', importar);
    $('btnCancelarNfce').addEventListener('click', () => { nota = null; mostrar(null); status(''); });

    carregarHistorico();
  });
})();
