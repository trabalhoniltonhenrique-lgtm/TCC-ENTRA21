// ── CasaCapital — dispensa.js (backend Java + MySQL) ──
// Usa o array compartilhado `dispensa` carregado por app.js (evento 'casacapital-dados-carregados').
const CATS = ['Geral','Hortifruti','Laticínios','Carnes','Bebidas','Higiene','Limpeza','Frios','Padaria','Outros'];

function popularCats() {
  const sc = document.getElementById('dispensaCat');
  if (sc) sc.innerHTML = CATS.map(c => `<option>${c}</option>`).join('');
}

async function adicionarDispensa() {
  const nome = document.getElementById('dispensaNome')?.value.trim();
  const cat  = document.getElementById('dispensaCat')?.value || 'Geral';
  const qtd  = parseInt(document.getElementById('dispensaQtd')?.value || 1);
  if (!nome) return;

  const item = await api.post('/dispensa', { nome, categoria: cat, qtd });
  const idx = dispensa.findIndex(x => x.id === item.id);
  if (idx >= 0) dispensa[idx] = item; else dispensa.push(item);

  document.getElementById('dispensaNome').value = '';
  document.getElementById('dispensaQtd').value  = '1';
  renderDispensa();
}

async function alterarQtd(id, delta) {
  const item = await api.post(`/dispensa/${id}/qtd?delta=${delta}`);
  const idx = dispensa.findIndex(x => x.id === id);
  if (idx >= 0) dispensa[idx] = item;
  renderDispensa();
}

async function excluirDispensa(id) {
  const item = dispensa.find(x => x.id === id);
  if (!confirm(`Remover "${item?.nome}" da dispensa?`)) return;
  await api.del('/dispensa/' + id);
  dispensa = dispensa.filter(x => x.id !== id);
  renderDispensa();
}

async function moverParaCompras(id) {
  const item = dispensa.find(x => x.id === id);
  if (!item) return;
  await api.post(`/dispensa/${id}/mover-para-compras`);
  const msg = document.createElement('div');
  msg.textContent = `🛒 "${item.nome}" adicionado à lista de compras!`;
  msg.className = 'toast-info';
  document.body.appendChild(msg);
  setTimeout(() => msg.remove(), 2500);
}

function statusQtd(qtd) {
  if (qtd === 0)  return '<span class="status-badge zero">Zerado</span>';
  if (qtd <= 2)   return '<span class="status-badge baixo">Baixo</span>';
  return '<span class="status-badge ok">OK</span>';
}

function fmtData(d) {
  if (!d) return '';
  return new Date(d + 'T12:00:00').toLocaleDateString('pt-BR');
}

function renderDispensa() {
  const busca  = document.getElementById('busca')?.value.toLowerCase() || '';
  const lista  = document.getElementById('listaDispensa');
  const resumo = document.getElementById('resumoDispensa');
  if (!lista) return;

  const d = dispensa;
  const filtrado = busca ? d.filter(x => x.nome.toLowerCase().includes(busca)) : d;

  const total   = d.length;
  const zerados = d.filter(x => x.qtd === 0).length;
  const baixos  = d.filter(x => x.qtd > 0 && x.qtd <= 2).length;
  resumo.innerHTML = `
    <div class="resumo-box"><div class="num">${total}</div><div class="txt">Itens cadastrados</div></div>
    <div class="resumo-box cor-ok"><div class="num">${total - zerados - baixos}</div><div class="txt">Com estoque OK</div></div>
    <div class="resumo-box cor-baixo"><div class="num">${baixos}</div><div class="txt">Estoque baixo</div></div>
    <div class="resumo-box cor-zero"><div class="num">${zerados}</div><div class="txt">Zerados</div></div>
  `;

  if (!filtrado.length) {
    lista.innerHTML = `<div class="lista-vazia">
      <div class="icone">🏠</div>
      <p>${busca ? 'Nenhum item encontrado.' : 'Dispensa vazia. Os itens comprados aparecerão aqui.'}</p>
      ${!busca ? `<a href="compras.html"><button class="btn-ir-compras">🛒 Ir para Compras</button></a>` : ''}
    </div>`;
    return;
  }

  const grupos = {};
  filtrado.forEach(x => { if (!grupos[x.categoria]) grupos[x.categoria] = []; grupos[x.categoria].push(x); });

  lista.innerHTML = Object.entries(grupos).map(([cat, itens]) => `
    <div class="cat-grupo">
      <span class="cat-label">📦 ${cat}</span>
      ${itens.map(x => `
        <div class="dispensa-item">
          <div class="info">
            <div class="nome">${x.nome}</div>
            <div class="meta">Entrada: ${fmtData(x.dataEntrada)} · Atualizado: ${fmtData(x.dataAtualizacao)}</div>
          </div>
          ${statusQtd(x.qtd)}
          ${typeof tagVariacaoPreco === 'function' ? tagVariacaoPreco(x.nome) : ''}
          <div class="qtd-ctrl">
            <button class="qtd-btn" data-action="diminuir" data-id="${x.id}">−</button>
            <span class="qtd-num">${x.qtd}</span>
            <button class="qtd-btn" data-action="aumentar" data-id="${x.id}">+</button>
          </div>
          <button class="btn-sm btn-secundario btn-acao-dispensa" data-action="mover-compras" data-id="${x.id}" title="Adicionar à lista de compras">🛒</button>
          <button class="btn-sm btn-perigo btn-acao-dispensa" data-action="excluir" data-id="${x.id}" title="Remover da dispensa">✕</button>
        </div>`).join('')}
    </div>`).join('');
}

// ── Exportar Dispensa em PDF ──
function exportarDispensaPDF() {
  if (!window.jspdf) {
    alert('Não foi possível carregar o gerador de PDF. Verifique sua conexão e tente novamente.');
    return;
  }
  const { jsPDF } = window.jspdf;
  const doc  = new jsPDF();
  const data = new Date().toLocaleDateString('pt-BR');
  const dispensaAtual = dispensa;

  doc.setFillColor(30, 58, 138); doc.rect(0, 0, 210, 28, 'F');
  doc.setTextColor(255,255,255); doc.setFontSize(16); doc.setFont('helvetica','bold');
  doc.text('CasaCapital', 14, 12);
  doc.setFontSize(9); doc.setFont('helvetica','normal');
  doc.text('Lista da Dispensa — ' + data, 14, 21);

  if (!dispensaAtual.length) {
    doc.setTextColor(107,114,128); doc.setFontSize(11);
    doc.text('Dispensa vazia. Nenhum item cadastrado.', 14, 45);
    doc.save('Dispensa_CasaCapital.pdf');
    return;
  }

  const total   = dispensaAtual.length;
  const zerados = dispensaAtual.filter(x => x.qtd === 0).length;
  const baixos  = dispensaAtual.filter(x => x.qtd > 0 && x.qtd <= 2).length;

  doc.setTextColor(30,58,138); doc.setFontSize(11); doc.setFont('helvetica','bold');
  doc.text('Resumo', 14, 38);
  doc.setDrawColor(191,219,254); doc.line(14, 40, 196, 40);

  const blocos = [
    { label: 'Itens cadastrados', val: String(total),   cor: [30,58,138] },
    { label: 'Estoque baixo',     val: String(baixos),   cor: [146,64,14] },
    { label: 'Zerados',           val: String(zerados),  cor: [220,38,38] },
  ];
  blocos.forEach((b, i) => {
    const x = 14 + i * 62;
    doc.setFillColor(239,246,255); doc.roundedRect(x, 44, 58, 18, 2, 2, 'F');
    doc.setTextColor(107,114,128); doc.setFontSize(8); doc.setFont('helvetica','normal');
    doc.text(b.label, x+4, 51);
    doc.setTextColor(...b.cor); doc.setFontSize(13); doc.setFont('helvetica','bold');
    doc.text(b.val, x+4, 58);
  });

  const grupos = {};
  dispensaAtual.forEach(x => { if (!grupos[x.categoria]) grupos[x.categoria] = []; grupos[x.categoria].push(x); });

  let y = 75;
  Object.entries(grupos).forEach(([cat, itens]) => {
    if (y > 270) { doc.addPage(); y = 20; }
    doc.setTextColor(30,58,138); doc.setFontSize(10); doc.setFont('helvetica','bold');
    doc.text('📦 ' + cat, 14, y); y += 4;
    doc.setDrawColor(191,219,254); doc.line(14, y, 196, y); y += 6;

    itens.sort((a,b) => a.nome.localeCompare(b.nome)).forEach(item => {
      if (y > 275) { doc.addPage(); y = 20; }
      const corStatus = item.qtd === 0 ? [220,38,38] : item.qtd <= 2 ? [146,64,14] : [22,163,74];
      const txtStatus = item.qtd === 0 ? 'Zerado' : item.qtd <= 2 ? 'Baixo' : 'OK';

      doc.setTextColor(55,65,81); doc.setFontSize(9); doc.setFont('helvetica','normal');
      doc.text(item.nome, 18, y);
      doc.setTextColor(107,114,128);
      doc.text('Qtd: ' + item.qtd, 130, y);
      doc.setTextColor(...corStatus); doc.setFont('helvetica','bold');
      doc.text(txtStatus, 165, y);
      y += 6.5;
    });
    y += 4;
  });

  const pages = doc.internal.getNumberOfPages();
  for (let p = 1; p <= pages; p++) {
    doc.setPage(p);
    doc.setFillColor(239,246,255); doc.rect(0,285,210,12,'F');
    doc.setTextColor(156,163,175); doc.setFontSize(8); doc.setFont('helvetica','normal');
    doc.text('CasaCapital — Gestão Financeira Doméstica', 14, 292);
    doc.text(`Página ${p} de ${pages}`, 196, 292, { align:'right' });
  }

  doc.save('Dispensa_CasaCapital.pdf');
}

document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('busca').addEventListener('input', renderDispensa);
  document.getElementById('btnExportarPdf').addEventListener('click', exportarDispensaPDF);
  document.getElementById('btnAddDispensa').addEventListener('click', adicionarDispensa);

  document.getElementById('listaDispensa').addEventListener('click', function (e) {
    const acaoEl = e.target.closest('[data-action]');
    if (!acaoEl) return;
    const acao = acaoEl.getAttribute('data-action');
    const id   = Number(acaoEl.getAttribute('data-id'));

    if (acao === 'diminuir')      alterarQtd(id, -1);
    if (acao === 'aumentar')      alterarQtd(id, +1);
    if (acao === 'mover-compras') moverParaCompras(id);
    if (acao === 'excluir')       excluirDispensa(id);
  });

  popularCats();
  document.addEventListener('casacapital-dados-carregados', renderDispensa);
});
