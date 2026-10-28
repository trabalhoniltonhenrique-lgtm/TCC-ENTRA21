// ── CasaCapital — app-analises.js: gráficos, tendências, comparativo e projeção (Premium, tela Análises) ──

// ══════════════════════════════════════════════
// ── PREMIUM: Gráficos ──
// ══════════════════════════════════════════════

// Os dados desta seção vêm exclusivamente de GET /api/analises/resumo (ver analises.js),
// que só responde para famílias Premium (403 para Essencial) — não há cálculo a partir de
// `receitas`/`despesas` locais aqui, para que o bloqueio de plano seja aplicado de verdade
// no servidor, e não apenas escondido na tela.
function renderGraficos(dados) {
  _saldoAtualAnalise = dados.saldoAtual;
  renderGraficoBarras(dados.seriesMensal);
  renderGraficoPizza(dados.despesasPorCategoria);
  renderTendencias(dados.tendenciaCategorias);
  renderComparativoMeses(dados.seriesMensal);
}

function renderGraficoBarras(seriesMensal) {
  const canvas = el('graficoBarras');
  if (!canvas) return;

  const ultimos6 = seriesMensal.slice(-6);
  const meses = ultimos6.map(s => {
    const [ano, mes] = s.mesAno.split('-');
    const label = new Date(Number(ano), Number(mes) - 1, 1).toLocaleDateString('pt-BR', { month: 'short', year: '2-digit' });
    return { label, key: s.mesAno };
  });

  const dadosR = ultimos6.map(s => s.receitas);
  const dadosD = ultimos6.map(s => s.despesas);
  const maxVal = Math.max(...dadosR, ...dadosD, 1);

  const W = canvas.offsetWidth || 560;
  const H = 220;
  canvas.width  = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, W, H);

  const pad  = { top:20, right:20, bottom:40, left:60 };
  const gW   = W - pad.left - pad.right;
  const gH   = H - pad.top  - pad.bottom;
  const grupW = gW / meses.length;
  const barW  = grupW * 0.32;

  ctx.strokeStyle = '#E5E7EB'; ctx.lineWidth = 1;
  for (let i = 0; i <= 4; i++) {
    const y = pad.top + gH - (gH / 4) * i;
    ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(W - pad.right, y); ctx.stroke();
    ctx.fillStyle = '#9CA3AF'; ctx.font = '10px Arial'; ctx.textAlign = 'right';
    ctx.fillText(fmt((maxVal / 4) * i), pad.left - 5, y + 3);
  }

  meses.forEach((m, idx) => {
    const x    = pad.left + grupW * idx + grupW / 2;
    const hR   = (dadosR[idx] / maxVal) * gH;
    const hD   = (dadosD[idx] / maxVal) * gH;
    const yBot = pad.top + gH;

    ctx.fillStyle = '#16A34A';
    ctx.fillRect(x - barW - 2, yBot - hR, barW, hR);
    ctx.fillStyle = '#DC2626';
    ctx.fillRect(x + 2, yBot - hD, barW, hD);

    ctx.fillStyle = '#6B7280'; ctx.font = '10px Arial'; ctx.textAlign = 'center';
    ctx.fillText(m.label, x, H - 8);
  });

  ctx.fillStyle = '#16A34A'; ctx.fillRect(pad.left, 4, 12, 10);
  ctx.fillStyle = '#374151'; ctx.font = '11px Arial'; ctx.textAlign = 'left';
  ctx.fillText('Receitas', pad.left + 16, 13);
  ctx.fillStyle = '#DC2626'; ctx.fillRect(pad.left + 80, 4, 12, 10);
  ctx.fillStyle = '#374151'; ctx.fillText('Despesas', pad.left + 96, 13);
}

function renderGraficoPizza(despesasPorCategoria) {
  const canvas = el('graficoPizza');
  if (!canvas || !despesasPorCategoria.length) return;

  const entries = despesasPorCategoria.map(c => [c.categoria, c.valor]);
  const total   = entries.reduce((a, [, v]) => a + v, 0);
  const cores   = ['#2563EB','#16A34A','#F59E0B','#DC2626','#7C3AED','#0891B2','#EA580C','#DB2777','#64748B'];

  const S = 200;
  canvas.width = S; canvas.height = S;
  const ctx  = canvas.getContext('2d');
  ctx.clearRect(0, 0, S, S);
  const cx   = S / 2, cy = S / 2, r = 80;
  let  angulo = -Math.PI / 2;

  entries.forEach(([cat, val], i) => {
    const fatia = (val / total) * 2 * Math.PI;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.arc(cx, cy, r, angulo, angulo + fatia);
    ctx.closePath();
    ctx.fillStyle = cores[i % cores.length];
    ctx.fill();
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 2; ctx.stroke();
    angulo += fatia;
  });

  ctx.beginPath(); ctx.arc(cx, cy, 45, 0, 2*Math.PI);
  ctx.fillStyle = '#fff'; ctx.fill();
  ctx.fillStyle = corTema('--azul-escuro') || '#1E3A8A'; ctx.font = 'bold 11px Arial'; ctx.textAlign = 'center';
  ctx.fillText('Total', cx, cy - 5);
  ctx.fillText(fmt(total), cx, cy + 10);

  const leg = el('legendaPizza');
  if (leg) {
    leg.innerHTML = entries.map(([cat, val], i) => {
      const pct = ((val/total)*100).toFixed(1);
      return `<div class="legenda-pizza-linha">
        <div class="legenda-pizza-cor" style="--cor-legenda:${cores[i%cores.length]};"></div>
        <span class="legenda-pizza-cat">${cat}</span>
        <span class="legenda-pizza-pct">${pct}%</span>
      </div>`;
    }).join('');
  }
}

// ── PREMIUM: Tendências ──
function renderTendencias(tendenciaCategorias) {
  const el2 = el('painelTendencias');
  if (!el2) return;

  const mesAtual = new Date();
  const mesAnt   = new Date(); mesAnt.setMonth(mesAnt.getMonth() - 1);

  if (!tendenciaCategorias.length) { el2.innerHTML = '<p class="texto-vazio-tendencia">Dados insuficientes. Adicione transações em meses diferentes.</p>'; return; }

  const linhas = tendenciaCategorias.map(t => {
    const cat = t.categoria;
    const a   = t.anterior;
    const b   = t.atual;
    const var_ = a ? ((b - a) / a * 100).toFixed(1) : null;
    const seta = b > a ? '↑' : b < a ? '↓' : '→';
    const corCls = b > a ? 'cor-subiu' : b < a ? 'cor-desceu' : 'cor-igual';
    return `<div class="linha-tendencia">
      <span class="tendencia-cat">${cat}</span>
      <div class="tendencia-valores">
        <span class="tendencia-anterior">${a ? fmt(a) : '—'}</span>
        <span class="tendencia-atual">${b ? fmt(b) : '—'}</span>
        <span class="tendencia-variacao ${corCls}">${seta} ${var_ !== null ? var_+'%' : ''}</span>
      </div>
    </div>`;
  }).join('');

  el2.innerHTML = `
    <div class="cabecalho-tendencia">
      <span>Categoria</span>
      <div class="cabecalho-tendencia-valores">
        <span>${mesAnt.toLocaleDateString('pt-BR',{month:'short'})}</span>
        <span>${mesAtual.toLocaleDateString('pt-BR',{month:'short'})}</span>
        <span class="cabecalho-tendencia-variacao">Variação</span>
      </div>
    </div>
    ${linhas}`;
}

// ── PREMIUM: Comparativo de Meses (6 ou 12 meses) ──
let _qtdMesesComparativo = 6;
let _seriesMensalAnalise = [];

function definirPeriodoComparativo(qtd) {
  _qtdMesesComparativo = qtd;
  document.querySelectorAll('.comp-periodo-btn').forEach(b => b.classList.remove('ativo'));
  const btn = el('compBtn' + qtd);
  if (btn) btn.classList.add('ativo');
  renderComparativoMeses(_seriesMensalAnalise);
}

function renderComparativoMeses(seriesMensal) {
  const cont = el('tabelaComparativo');
  if (!cont) return;
  _seriesMensalAnalise = seriesMensal;

  const qtd = _qtdMesesComparativo;
  const linhas = seriesMensal.slice(-qtd).map(s => {
    const [ano, mes] = s.mesAno.split('-');
    const label = new Date(Number(ano), Number(mes) - 1, 1).toLocaleDateString('pt-BR', { month: 'short', year: '2-digit' });
    return { label, receita: s.receitas, despesa: s.despesas, saldo: s.saldo };
  });

  function variacao(atual, anterior) {
    if (anterior === 0) return atual === 0 ? null : 100;
    return ((atual - anterior) / Math.abs(anterior)) * 100;
  }

  function celulaVar(atual, anterior, inverteCor) {
    const v = variacao(atual, anterior);
    if (v === null) return '<span class="var-cel-vazia">—</span>';
    const subiu = v > 0;
    const corBoa = inverteCor ? !subiu : subiu;
    const corCls = Math.abs(v) < 0.5 ? 'var-cel-neutra' : (corBoa ? 'var-cel-boa' : 'var-cel-ruim');
    const seta = Math.abs(v) < 0.5 ? '→' : (subiu ? '↑' : '↓');
    return `<span class="var-cel ${corCls}">${seta} ${Math.abs(v).toFixed(0)}%</span>`;
  }

  const totalReceitas = linhas.reduce((a,l) => a + l.receita, 0);
  const totalDespesas = linhas.reduce((a,l) => a + l.despesa, 0);
  const mediaReceitas  = totalReceitas / linhas.length;
  const mediaDespesas  = totalDespesas / linhas.length;

  cont.innerHTML = `
    <div class="tabela-comparativo-wrap">
      <table class="tabela-comparativo">
        <thead>
          <tr class="linha-cabecalho-comp">
            <th class="th-mes">Mês</th>
            <th class="th-receita">Receitas</th>
            <th class="th-var">Var.</th>
            <th class="th-despesa">Despesas</th>
            <th class="th-var">Var.</th>
            <th class="th-saldo">Saldo</th>
          </tr>
        </thead>
        <tbody>
          ${linhas.map((l, idx) => {
            const ant = idx > 0 ? linhas[idx-1] : null;
            return `
            <tr class="linha-comp">
              <td class="td-mes">${l.label}</td>
              <td class="td-receita">${fmt(l.receita)}</td>
              <td class="td-var">${ant ? celulaVar(l.receita, ant.receita, false) : '<span class="var-cel-vazia">—</span>'}</td>
              <td class="td-despesa">${fmt(l.despesa)}</td>
              <td class="td-var">${ant ? celulaVar(l.despesa, ant.despesa, true) : '<span class="var-cel-vazia">—</span>'}</td>
              <td class="td-saldo ${l.saldo<0?'saldo-negativo-cel':'saldo-positivo-cel'}">${fmt(l.saldo)}</td>
            </tr>`;
          }).join('')}
        </tbody>
        <tfoot>
          <tr class="linha-media-comp">
            <td class="td-media-label">Média</td>
            <td class="td-media-receita">${fmt(mediaReceitas)}</td>
            <td></td>
            <td class="td-media-despesa">${fmt(mediaDespesas)}</td>
            <td></td>
            <td class="td-media-saldo">${fmt(mediaReceitas-mediaDespesas)}</td>
          </tr>
        </tfoot>
      </table>
    </div>`;
}

// ── PREMIUM: Projeção Financeira ──
let _saldoAtualAnalise = 0;

function calcularProjecao() {
  const economia = parseFloat(el('projEconomia')?.value || 0);
  const meses    = parseInt(el('projMeses')?.value    || 12);
  const meta     = parseFloat(el('projMeta')?.value   || 0);
  const saldoAtual = _saldoAtualAnalise;
  const saldoMensal = economia > 0 ? economia : Math.max(saldoAtual, 0);

  const resultado = el('projResultado');
  if (!resultado) return;
  if (!saldoMensal && !saldoAtual) {
    resultado.innerHTML = '<p class="texto-vazio-projecao">Adicione receitas e despesas para calcular.</p>';
    return;
  }

  const total      = saldoAtual + saldoMensal * meses;
  const mesesMeta  = meta > saldoAtual && saldoMensal > 0 ? Math.ceil((meta - saldoAtual) / saldoMensal) : null;
  const atingeMeta = meta && total >= meta;

  const pontos = Array.from({length: meses + 1}, (_, i) => saldoAtual + saldoMensal * i);
  const maxP   = Math.max(...pontos, meta || 0);
  const minP   = Math.min(...pontos, 0);
  const rangeP = maxP - minP || 1;
  const W = 400, H = 100;
  const pts = pontos.map((v, i) => ({
    x: (i / meses) * (W - 40) + 20,
    y: H - 20 - ((v - minP) / rangeP) * (H - 30)
  }));

  const svg = `<svg viewBox="0 0 ${W} ${H}" class="svg-projecao">
    ${meta ? `<line x1="20" y1="${H-20-((meta-minP)/rangeP)*(H-30)}" x2="${W-20}" y2="${H-20-((meta-minP)/rangeP)*(H-30)}" stroke="#F59E0B" stroke-width="1.5" stroke-dasharray="4"/>` : ''}
    <polyline points="${pts.map(p=>`${p.x},${p.y}`).join(' ')}" fill="none" style="stroke:var(--azul)" stroke-width="2.5" stroke-linejoin="round"/>
    ${pts.map((p,i)=> i===0||i===meses ? `<circle cx="${p.x}" cy="${p.y}" r="4" style="fill:var(--azul)"/>` : '').join('')}
    <text x="20" y="${H-5}" font-size="10" fill="#9CA3AF">Hoje</text>
    <text x="${W-45}" y="${H-5}" font-size="10" fill="#9CA3AF">${meses}m</text>
    ${meta ? `<text x="${W-20}" y="${H-20-((meta-minP)/rangeP)*(H-30)-4}" font-size="10" fill="#F59E0B" text-anchor="end">meta</text>` : ''}
  </svg>`;

  resultado.innerHTML = `
    <div class="grafico-projecao-wrap">${svg}</div>
    <div class="grid-projecao-stats">
      <div class="stat-projecao cor-saldo-hoje">
        <div class="stat-projecao-label">Saldo hoje</div>
        <div class="stat-projecao-valor">${fmt(saldoAtual)}</div>
      </div>
      <div class="stat-projecao cor-saldo-futuro">
        <div class="stat-projecao-label">Em ${meses} meses</div>
        <div class="stat-projecao-valor">${fmt(total)}</div>
      </div>
      <div class="stat-projecao cor-economia-mes">
        <div class="stat-projecao-label">Economia/mês</div>
        <div class="stat-projecao-valor">${fmt(saldoMensal)}</div>
      </div>
      ${meta ? `<div class="stat-projecao ${atingeMeta ? 'cor-meta-atingida' : 'cor-meta-pendente'}">
        <div class="stat-projecao-label">Meta ${fmt(meta)}</div>
        <div class="stat-projecao-valor-meta ${atingeMeta ? 'cor-meta-atingida-txt' : 'cor-meta-pendente-txt'}">${atingeMeta ? '<span class="ico ico-check"></span> Atingida!' : mesesMeta ? `em ${mesesMeta} meses` : '—'}</div>
      </div>` : ''}
    </div>`;
}
