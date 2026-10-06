// ── CasaCapital — relatorio-modulo.js ──
// Botão "Relatório" de cada módulo. Ao clicar, busca os dados do módulo na API e abre uma
// janela com a prévia em tabelas e as opções Imprimir, Baixar TXT e Baixar PDF (Premium).
// Não depende de app.js (algumas páginas não o carregam), por isso tem a própria formatação.

(function () {
  const DIAS_SEMANA = ['Segunda-feira','Terça-feira','Quarta-feira','Quinta-feira','Sexta-feira','Sábado','Domingo'];
  const FREQUENCIAS = { DIARIA: 'Diária', SEMANAL: 'Semanal', QUINZENAL: 'Quinzenal', MENSAL: 'Mensal', SEM_FREQUENCIA: '—' };
  const RECORRENCIAS = { UNICA: 'Única', MENSAL: 'Mensal', ANUAL: 'Anual', PARCELADA: 'Parcelada' };

  // ── Formatação ──
  let familia = {};
  const hojeISO = () => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  };
  const mesAtual = () => hojeISO().slice(0, 7);
  function dinheiro(v) {
    const sep = familia.separadorDecimal || ',';
    const [inteiro, dec] = Math.abs(Number(v) || 0).toFixed(2).split('.');
    const milhar = sep === ',' ? '.' : ',';
    return (v < 0 ? '-' : '') + (familia.moeda || 'R$') + ' ' +
      inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, milhar) + sep + dec;
  }
  const data = d => d ? new Date(d + 'T12:00:00').toLocaleDateString('pt-BR') : '—';
  const nomeMes = chave => {
    const t = new Date(chave + '-15T12:00:00').toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
    return t.charAt(0).toUpperCase() + t.slice(1);
  };
  const soma = (lista, campo) => lista.reduce((a, x) => a + (Number(x[campo]) || 0), 0);
  const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const pct = (parte, total) => total > 0 ? Math.round(parte / total * 100) + '%' : '—';

  async function nomesMembros() {
    const membros = await api.get('/membros');
    const mapa = Object.fromEntries(membros.map(m => [m.id, m.nome]));
    return id => mapa[id] || '—';
  }

  function gastosPorCategoria(despesas, mes) {
    const mapa = {};
    despesas.filter(d => d.data?.startsWith(mes)).forEach(d => { mapa[d.categoria] = (mapa[d.categoria] || 0) + Number(d.valor); });
    return mapa;
  }

  // ── Relatório de cada módulo ──
  // montar() devolve { periodo?, resumo: [{rotulo, valor}], secoes: [{titulo, colunas, linhas, direita: [índices]}] }
  const MODULOS = {
    'financas.html': {
      titulo: 'Finanças',
      async montar() {
        const mes = document.getElementById('filtroMes')?.value || '';
        const doMes = l => mes ? l.filter(x => x.data?.startsWith(mes)) : l;
        const [receitas, despesas, membro] = await Promise.all([api.get('/receitas'), api.get('/despesas'), nomesMembros()]);
        const rec = doMes(receitas).sort((a, b) => a.data.localeCompare(b.data));
        const des = doMes(despesas).sort((a, b) => a.data.localeCompare(b.data));
        const tr = soma(rec, 'valor'), td = soma(des, 'valor');
        const cats = {};
        des.forEach(d => { cats[d.categoria] = (cats[d.categoria] || 0) + Number(d.valor); });
        const linha = x => [data(x.data), x.nome, x.categoria, membro(x.membroId), dinheiro(x.valor)];
        return {
          periodo: mes ? nomeMes(mes) : 'Todos os meses',
          resumo: [{ rotulo: 'Receitas', valor: dinheiro(tr) }, { rotulo: 'Despesas', valor: dinheiro(td) }, { rotulo: 'Saldo', valor: dinheiro(tr - td) }],
          secoes: [
            { titulo: 'Gastos por categoria', colunas: ['Categoria', 'Total', '% das despesas'], direita: [1, 2],
              linhas: Object.entries(cats).sort((a, b) => b[1] - a[1]).map(([c, v]) => [c, dinheiro(v), pct(v, td)]) },
            { titulo: 'Receitas', colunas: ['Data', 'Descrição', 'Categoria', 'Membro', 'Valor'], direita: [4], linhas: rec.map(linha) },
            { titulo: 'Despesas', colunas: ['Data', 'Descrição', 'Categoria', 'Membro', 'Valor'], direita: [4], linhas: des.map(linha) },
          ],
        };
      },
    },

    'contas.html': {
      titulo: 'Contas a Pagar',
      async montar() {
        const contas = (await api.get('/contas')).sort((a, b) => a.vencimento.localeCompare(b.vencimento));
        const hoje = hojeISO();
        const pendentes = contas.filter(c => !c.pago);
        const pagas = contas.filter(c => c.pago);
        const atrasadas = pendentes.filter(c => c.vencimento < hoje);
        const situacao = c => c.vencimento < hoje ? 'Atrasada' : c.vencimento === hoje ? 'Vence hoje' : 'Pendente';
        const descricao = c => c.descricao + (c.parcelaTotal ? ` (${c.parcelaNum}/${c.parcelaTotal})` : '');
        return {
          resumo: [
            { rotulo: 'A pagar', valor: dinheiro(soma(pendentes, 'valor')) },
            { rotulo: 'Atrasadas', valor: `${atrasadas.length} · ${dinheiro(soma(atrasadas, 'valor'))}` },
            { rotulo: 'Pagas', valor: dinheiro(soma(pagas, 'valor')) },
          ],
          secoes: [
            { titulo: 'Pendentes', colunas: ['Vencimento', 'Conta', 'Categoria', 'Recorrência', 'Situação', 'Valor'], direita: [5],
              linhas: pendentes.map(c => [data(c.vencimento), descricao(c), c.categoria, RECORRENCIAS[c.recorrencia] || c.recorrencia || '—', situacao(c), dinheiro(c.valor)]) },
            { titulo: 'Pagas', colunas: ['Vencimento', 'Conta', 'Categoria', 'Pago em', 'Valor'], direita: [4],
              linhas: pagas.map(c => [data(c.vencimento), descricao(c), c.categoria, data(c.dataPagamento), dinheiro(c.valor)]) },
          ],
        };
      },
    },

    'orcamento.html': {
      titulo: 'Orçamento Mensal',
      async montar() {
        const mes = mesAtual();
        const [itens, despesas] = await Promise.all([api.get('/orcamentos/' + mes), api.get('/despesas')]);
        const gastos = gastosPorCategoria(despesas, mes);
        const totalLimite = soma(itens, 'limite');
        const totalGasto = itens.reduce((a, o) => a + (gastos[o.categoria] || 0), 0);
        return {
          periodo: nomeMes(mes),
          resumo: [{ rotulo: 'Orçado', valor: dinheiro(totalLimite) }, { rotulo: 'Gasto', valor: dinheiro(totalGasto) }, { rotulo: 'Disponível', valor: dinheiro(totalLimite - totalGasto) }],
          secoes: [{
            titulo: 'Limites por categoria', colunas: ['Categoria', 'Limite', 'Gasto', 'Disponível', 'Uso'], direita: [1, 2, 3, 4],
            linhas: itens.map(o => {
              const g = gastos[o.categoria] || 0;
              return [o.categoria, dinheiro(o.limite), dinheiro(g), dinheiro(o.limite - g), pct(g, Number(o.limite))];
            }),
          }],
        };
      },
    },

    'metas.html': {
      titulo: 'Metas de Economia',
      async montar() {
        const metas = await api.get('/metas');
        const linha = m => [m.nome, dinheiro(m.valorAlvo), dinheiro(m.totalPoupado), dinheiro(Math.max(0, m.valorAlvo - m.totalPoupado)),
          pct(Number(m.totalPoupado), Number(m.valorAlvo)), data(m.prazo)];
        const colunas = ['Meta', 'Objetivo', 'Poupado', 'Falta', 'Progresso', 'Prazo'];
        return {
          resumo: [
            { rotulo: 'Metas', valor: String(metas.length) },
            { rotulo: 'Total poupado', valor: dinheiro(soma(metas, 'totalPoupado')) },
            { rotulo: 'Objetivo total', valor: dinheiro(soma(metas, 'valorAlvo')) },
          ],
          secoes: [
            { titulo: 'Em andamento', colunas, direita: [1, 2, 3, 4], linhas: metas.filter(m => !m.concluida).map(linha) },
            { titulo: 'Concluídas', colunas, direita: [1, 2, 3, 4], linhas: metas.filter(m => m.concluida).map(linha) },
          ],
        };
      },
    },

    'familia.html': {
      titulo: 'Membros da Família',
      async montar() {
        const membros = await api.get('/membros');
        return {
          resumo: [{ rotulo: 'Membros', valor: String(membros.length) }, { rotulo: 'Renda total', valor: dinheiro(soma(membros, 'renda')) }],
          secoes: [{
            titulo: 'Membros', colunas: ['Nome', 'Parentesco', 'Nascimento', 'Renda', 'Observações'], direita: [3],
            linhas: membros.map(m => [m.nome, m.parentesco || '—', data(m.nascimento), m.renda != null ? dinheiro(m.renda) : '—', m.obs || '—']),
          }],
        };
      },
    },

    'compras.html': {
      titulo: 'Lista de Compras',
      async montar() {
        const compras = (await api.get('/compras')).sort((a, b) => (a.categoria || '').localeCompare(b.categoria || '') || a.nome.localeCompare(b.nome));
        return {
          resumo: [{ rotulo: 'Itens', valor: String(compras.length) }, { rotulo: 'Unidades', valor: String(soma(compras, 'qtd')) }],
          secoes: [{
            titulo: 'Itens a comprar', colunas: ['Item', 'Categoria', 'Frequência', 'Qtd'], direita: [3],
            linhas: compras.map(c => [c.nome, c.categoria || '—', FREQUENCIAS[c.frequencia] || '—', String(c.qtd)]),
          }],
        };
      },
    },

    'dispensa.html': {
      titulo: 'Dispensa da Casa',
      async montar() {
        const itens = (await api.get('/dispensa')).sort((a, b) => (a.categoria || '').localeCompare(b.categoria || '') || a.nome.localeCompare(b.nome));
        const acabando = itens.filter(i => i.qtd <= 1);
        return {
          resumo: [{ rotulo: 'Itens', valor: String(itens.length) }, { rotulo: 'Acabando (1 ou menos)', valor: String(acabando.length) }],
          secoes: [{
            titulo: 'Itens em casa', colunas: ['Item', 'Categoria', 'Entrada', 'Atualizado', 'Qtd'], direita: [4],
            linhas: itens.map(i => [i.nome, i.categoria || '—', data(i.dataEntrada), data(i.dataAtualizacao), String(i.qtd)]),
          }],
        };
      },
    },

    'historico-precos.html': {
      titulo: 'Histórico de Preços',
      async montar() {
        const produtos = (await api.get('/historico-precos')).sort((a, b) => a.nome.localeCompare(b.nome));
        const variacao = v => !v || !v.anterior ? '—' : (v.percentual > 0 ? '+' : '') + v.percentual.toFixed(1).replace('.', ',') + '%';
        return {
          resumo: [
            { rotulo: 'Produtos', valor: String(produtos.length) },
            { rotulo: 'Subiram', valor: String(produtos.filter(p => p.variacao?.subiu).length) },
            { rotulo: 'Baixaram', valor: String(produtos.filter(p => p.variacao?.desceu).length) },
          ],
          secoes: [{
            titulo: 'Produtos', colunas: ['Produto', 'Último registro', 'Registros', 'Preço anterior', 'Preço atual', 'Variação'], direita: [2, 3, 4, 5],
            linhas: produtos.map(p => [p.nome, data(p.dataUltimo), String(p.registros?.length || 0),
              p.variacao?.anterior ? dinheiro(p.variacao.anterior) : '—', dinheiro(p.precoAtual), variacao(p.variacao)]),
          }],
        };
      },
    },

    'tarefas.html': {
      titulo: 'Tarefas Familiares',
      async montar() {
        const [tarefas, membro] = await Promise.all([api.get('/tarefas'), nomesMembros()]);
        const hoje = hojeISO();
        const dia = t => t.diaSemana || (t.prazo ? (new Date(t.prazo + 'T12:00:00').getDay() || 7) : 8);
        const situacao = t => t.concluida ? 'Concluída' : !t.prazo ? 'Pendente' : t.prazo < hoje ? 'Atrasada' : t.prazo === hoje ? 'Vence hoje' : 'Pendente';
        const ordenadas = [...tarefas].sort((a, b) => dia(a) - dia(b) || a.concluida - b.concluida || a.nome.localeCompare(b.nome));
        return {
          resumo: [
            { rotulo: 'Tarefas', valor: String(tarefas.length) },
            { rotulo: 'Concluídas', valor: String(tarefas.filter(t => t.concluida).length) },
            { rotulo: 'Atrasadas', valor: String(tarefas.filter(t => situacao(t) === 'Atrasada').length) },
          ],
          secoes: [{
            titulo: 'Tarefas por dia da semana', colunas: ['Dia', 'Tarefa', 'Responsável', 'Prazo', 'Situação'],
            linhas: ordenadas.map(t => [DIAS_SEMANA[dia(t) - 1] || 'Sem dia', t.nome, membro(t.membroId), data(t.prazo), situacao(t)]),
          }],
        };
      },
    },

    'alertas.html': {
      titulo: 'Alertas de Limite',
      async montar() {
        const mes = mesAtual();
        const [alertas, despesas] = await Promise.all([api.get('/alertas'), api.get('/despesas')]);
        const gastos = gastosPorCategoria(despesas, mes);
        const situacao = (g, l) => g > l ? 'Ultrapassado' : g >= l * 0.8 ? 'Atenção' : 'Dentro do limite';
        return {
          periodo: nomeMes(mes),
          resumo: [
            { rotulo: 'Alertas', valor: String(alertas.length) },
            { rotulo: 'Ultrapassados', valor: String(alertas.filter(a => (gastos[a.categoria] || 0) > a.limite).length) },
          ],
          secoes: [{
            titulo: 'Limites por categoria', colunas: ['Categoria', 'Limite', 'Gasto no mês', 'Uso', 'Situação'], direita: [1, 2, 3],
            linhas: alertas.map(a => {
              const g = gastos[a.categoria] || 0;
              return [a.categoria, dinheiro(a.limite), dinheiro(g), pct(g, Number(a.limite)), situacao(g, Number(a.limite))];
            }),
          }],
        };
      },
    },
  };

  const pagina = location.pathname.split('/').pop() || '';
  const modulo = MODULOS[pagina];
  if (!modulo) return;

  let relatorio = null;
  const emitidoEm = () => new Date().toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

  // ── Renderização em HTML (prévia e impressão) ──
  function htmlRelatorio(r) {
    return `
      <div class="rel-resumo">${r.resumo.map(x => `<div class="rel-resumo-item"><span>${esc(x.rotulo)}</span><strong>${esc(x.valor)}</strong></div>`).join('')}</div>
      ${r.secoes.map(s => `
        <h4 class="rel-secao">${esc(s.titulo)} <small>(${s.linhas.length})</small></h4>
        ${s.linhas.length ? `<div class="rel-tabela-wrap"><table class="rel-tabela">
          <thead><tr>${s.colunas.map((c, i) => `<th class="${(s.direita || []).includes(i) ? 'dir' : ''}">${esc(c)}</th>`).join('')}</tr></thead>
          <tbody>${s.linhas.map(l => `<tr>${l.map((v, i) => `<td class="${(s.direita || []).includes(i) ? 'dir' : ''}">${esc(v)}</td>`).join('')}</tr>`).join('')}</tbody>
        </table></div>` : '<p class="rel-vazio">Nenhum registro.</p>'}`).join('')}`;
  }

  function cabecalhoTexto(r) {
    return `${familia.nomeFamilia || 'CasaCapital'} — Relatório de ${modulo.titulo}\n` +
      (r.periodo ? `Período: ${r.periodo}\n` : '') + `Emitido em: ${emitidoEm()}\n`;
  }

  // ── TXT: tabelas com colunas alinhadas ──
  function textoRelatorio(r) {
    const sep = '─'.repeat(60);
    let t = cabecalhoTexto(r) + sep + '\n\n';
    t += r.resumo.map(x => `${x.rotulo}: ${x.valor}`).join('\n') + '\n';
    r.secoes.forEach(s => {
      t += `\n${s.titulo.toUpperCase()} (${s.linhas.length})\n`;
      if (!s.linhas.length) { t += '  (nenhum registro)\n'; return; }
      const larg = s.colunas.map((c, i) => Math.min(40, Math.max(c.length, ...s.linhas.map(l => String(l[i]).length))));
      const fmtLinha = l => '  ' + l.map((v, i) => {
        const txt = String(v).slice(0, larg[i]);
        return (s.direita || []).includes(i) ? txt.padStart(larg[i]) : txt.padEnd(larg[i]);
      }).join('  ').trimEnd();
      t += fmtLinha(s.colunas) + '\n' + '  ' + larg.map(n => '-'.repeat(n)).join('  ') + '\n';
      t += s.linhas.map(fmtLinha).join('\n') + '\n';
    });
    return t;
  }

  function nomeArquivo(ext) {
    const base = modulo.titulo.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^A-Za-z0-9]+/g, '_');
    return `Relatorio_${base}_${hojeISO()}.${ext}`;
  }

  function baixarTXT() {
    const blob = new Blob(['﻿' + textoRelatorio(relatorio)], { type: 'text/plain;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = nomeArquivo('txt');
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }

  function imprimir() {
    const w = window.open('', '', 'width=900,height=700');
    if (!w) { alert('O navegador bloqueou a janela de impressão. Permita pop-ups para este site.'); return; }
    w.document.write(`<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8"><title>Relatório — ${esc(modulo.titulo)}</title>
      <link rel="stylesheet" href="css/impressao.css"></head><body>
      <h1>${esc(familia.nomeFamilia || 'CasaCapital')}</h1>
      <h2>Relatório de ${esc(modulo.titulo)}</h2>
      ${relatorio.periodo ? `<p>Período: <strong>${esc(relatorio.periodo)}</strong></p>` : ''}
      <p>Emitido em: ${esc(emitidoEm())}</p><hr>
      ${htmlRelatorio(relatorio)}
      </body></html>`);
    w.document.close();
    w.focus();
    setTimeout(() => w.print(), 400);
  }

  // ── PDF (Premium) ──
  function carregarJsPDF() {
    if (window.jspdf) return Promise.resolve();
    return new Promise((ok, erro) => {
      const s = document.createElement('script');
      s.src = 'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js';
      s.onload = ok; s.onerror = erro;
      document.head.appendChild(s);
    });
  }

  async function baixarPDF() {
    // Confirma o plano com uma chamada nova, não com o que já está em memória.
    try {
      const atual = await api.get('/familia/me');
      if (atual.plano !== 'PREMIUM') { alert('Exportar relatórios em PDF é um recurso Premium.'); return; }
    } catch { alert('Não foi possível confirmar seu plano. Tente novamente.'); return; }
    try { await carregarJsPDF(); } catch {
      alert('Não foi possível carregar o gerador de PDF. Verifique sua conexão e tente novamente.'); return;
    }

    const { jsPDF } = window.jspdf;
    const doc = new jsPDF();
    const r = relatorio;
    const M = 14, LARG = 182, FIM = 278;

    doc.setFillColor(30, 58, 138); doc.rect(0, 0, 210, 28, 'F');
    doc.setTextColor(255, 255, 255); doc.setFont('helvetica', 'bold'); doc.setFontSize(16);
    doc.text(familia.nomeFamilia || 'CasaCapital', M, 12);
    doc.setFont('helvetica', 'normal'); doc.setFontSize(9);
    doc.text(`Relatório de ${modulo.titulo}${r.periodo ? ' — ' + r.periodo : ''} · Emitido em ${emitidoEm()}`, M, 21);

    let y = 36;
    const largBloco = (LARG - (r.resumo.length - 1) * 4) / r.resumo.length;
    r.resumo.forEach((x, i) => {
      const bx = M + i * (largBloco + 4);
      doc.setFillColor(239, 246, 255); doc.roundedRect(bx, y, largBloco, 16, 2, 2, 'F');
      doc.setTextColor(107, 114, 128); doc.setFontSize(8); doc.setFont('helvetica', 'normal');
      doc.text(x.rotulo, bx + 3, y + 6);
      doc.setTextColor(30, 58, 138); doc.setFontSize(10); doc.setFont('helvetica', 'bold');
      doc.text(doc.splitTextToSize(x.valor, largBloco - 6)[0], bx + 3, y + 12.5);
    });
    y += 24;

    r.secoes.forEach(s => {
      if (y > FIM - 20) { doc.addPage(); y = 20; }
      doc.setTextColor(30, 58, 138); doc.setFontSize(11); doc.setFont('helvetica', 'bold');
      doc.text(`${s.titulo} (${s.linhas.length})`, M, y); y += 2;
      doc.setDrawColor(191, 219, 254); doc.line(M, y, M + LARG, y); y += 5;
      if (!s.linhas.length) {
        doc.setTextColor(156, 163, 175); doc.setFontSize(9); doc.setFont('helvetica', 'normal');
        doc.text('Nenhum registro.', M, y); y += 9; return;
      }
      // Largura de cada coluna proporcional ao maior texto dela
      const pesos = s.colunas.map((c, i) => Math.min(30, Math.max(c.length, ...s.linhas.map(l => String(l[i]).length))) + 2);
      const totalPeso = pesos.reduce((a, b) => a + b, 0);
      const largs = pesos.map(p => p / totalPeso * LARG);
      const xs = largs.map((_, i) => M + largs.slice(0, i).reduce((a, b) => a + b, 0));
      const dir = i => (s.direita || []).includes(i);
      const celula = (txt, i, yy) => {
        const t = doc.splitTextToSize(String(txt), largs[i] - 2)[0];
        if (dir(i)) doc.text(t, xs[i] + largs[i] - 1, yy, { align: 'right' }); else doc.text(t, xs[i] + 1, yy);
      };
      const cabecalho = () => {
        doc.setFillColor(219, 234, 254); doc.rect(M, y - 4, LARG, 6.5, 'F');
        doc.setTextColor(30, 58, 138); doc.setFontSize(8); doc.setFont('helvetica', 'bold');
        s.colunas.forEach((c, i) => celula(c, i, y)); y += 6.5;
      };
      cabecalho();
      s.linhas.forEach((l, n) => {
        if (y > FIM) { doc.addPage(); y = 20; cabecalho(); }
        if (n % 2) { doc.setFillColor(248, 250, 252); doc.rect(M, y - 4, LARG, 6, 'F'); }
        doc.setTextColor(55, 65, 81); doc.setFontSize(8); doc.setFont('helvetica', 'normal');
        l.forEach((v, i) => celula(v, i, y)); y += 6;
      });
      y += 6;
    });

    const paginas = doc.internal.getNumberOfPages();
    for (let p = 1; p <= paginas; p++) {
      doc.setPage(p);
      doc.setFillColor(239, 246, 255); doc.rect(0, 285, 210, 12, 'F');
      doc.setTextColor(156, 163, 175); doc.setFontSize(8); doc.setFont('helvetica', 'normal');
      doc.text('CasaCapital — Gestão Financeira Doméstica', M, 292);
      doc.text(`Página ${p} de ${paginas}`, 196, 292, { align: 'right' });
    }
    doc.save(nomeArquivo('pdf'));
  }

  // ── Janela do relatório ──
  function fechar() {
    document.getElementById('modalRelatorioModulo')?.remove();
    document.removeEventListener('keydown', fecharComEsc);
  }
  function fecharComEsc(e) { if (e.key === 'Escape') fechar(); }

  async function abrir() {
    fechar();
    const overlay = document.createElement('div');
    overlay.id = 'modalRelatorioModulo';
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
      <div class="modal-box rel-modal" role="dialog" aria-modal="true" aria-labelledby="relModalTitulo">
        <div class="rel-modal-topo">
          <div>
            <h3 id="relModalTitulo"><span class="ico ico-file-text"></span> Relatório de ${esc(modulo.titulo)}</h3>
            <p class="modal-sub" id="relModalSub">Carregando dados...</p>
          </div>
          <button class="rel-fechar" title="Fechar"><span class="ico ico-x"></span></button>
        </div>
        <div class="rel-conteudo" id="relModalConteudo"></div>
        <div class="rel-acoes">
          <button class="btn-secundario" data-rel="imprimir" disabled><span class="ico ico-printer"></span> Imprimir</button>
          <button class="btn-secundario" data-rel="txt" disabled><span class="ico ico-download"></span> Baixar TXT</button>
          <button class="btn-secundario" data-rel="pdf" disabled><span class="ico ico-download"></span> Baixar PDF <span class="premium-tag">PREMIUM</span></button>
        </div>
      </div>`;
    document.body.appendChild(overlay);
    document.addEventListener('keydown', fecharComEsc);
    overlay.addEventListener('click', e => { if (e.target === overlay || e.target.closest('.rel-fechar')) fechar(); });
    overlay.querySelector('[data-rel="imprimir"]').addEventListener('click', imprimir);
    overlay.querySelector('[data-rel="txt"]').addEventListener('click', baixarTXT);
    overlay.querySelector('[data-rel="pdf"]').addEventListener('click', baixarPDF);

    try {
      familia = await api.get('/familia/me');
      relatorio = await modulo.montar();
    } catch (e) {
      document.getElementById('relModalSub').textContent = 'Não foi possível gerar o relatório: ' + (e.message || e);
      return;
    }
    if (!document.body.contains(overlay)) return;
    document.getElementById('relModalSub').textContent =
      (relatorio.periodo ? `Período: ${relatorio.periodo} · ` : '') + `Emitido em ${emitidoEm()}`;
    document.getElementById('relModalConteudo').innerHTML = htmlRelatorio(relatorio);
    overlay.querySelectorAll('.rel-acoes button').forEach(b => { b.disabled = false; });
    overlay.querySelector('[data-rel="pdf"]').hidden = familia.plano !== 'PREMIUM';
  }

  // ── Botão "Relatório" (fica no cabeçalho de cada módulo, no HTML) ──
  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-relatorio-modulo]').forEach(b => b.addEventListener('click', abrir));
  });
})();
