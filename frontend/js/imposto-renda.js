// ── CasaCapital — imposto-renda.js: dados do ano para ajudar na declaração ──
(function () {
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const dinheiro = v => (Number(v) < 0 ? '-' : '') + 'R$ ' + Math.abs(Number(v) || 0).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const dataBR = d => d ? new Date(d + 'T12:00:00').toLocaleDateString('pt-BR') : '—';
  const SITUACAO = {
    PROVAVEL: ['provavelmente dedutível', 'sit-provavel'],
    NAO: ['em geral não dedutível', 'sit-nao'],
    CONFERIR: ['conferir', 'sit-conferir'],
  };

  const totais = (lista, vazio) => lista.length
    ? `<ul class="lista-resumo">${lista.map(t => `<li><span>${esc(t.nome)}</span><strong>${dinheiro(t.valor)}</strong></li>`).join('')}</ul>`
    : `<p class="dica-planejamento">${vazio}</p>`;

  function blocoDedutivel(titulo, d, dica) {
    return `<div class="card-box">
      <h3>${titulo}</h3>
      ${d.itens.length ? `
        <div class="resumo-topo resumo-topo-compacto">
          <div class="resumo-box cor-pago"><div class="num">${dinheiro(d.totalProvavel)}</div><div class="txt">Provavelmente dedutível</div></div>
          <div class="resumo-box cor-mes"><div class="num">${dinheiro(d.total)}</div><div class="txt">Total lançado na categoria</div></div>
        </div>
        <h4 class="subtitulo-ir">Por pessoa</h4>
        ${totais(d.porPessoa, '')}
        <h4 class="subtitulo-ir">Lançamentos</h4>
        <div class="tabela-ir-wrap"><table class="tabela-ir">
          <thead><tr><th>Data</th><th>Descrição</th><th>Pessoa</th><th class="dir">Valor</th><th>Situação</th></tr></thead>
          <tbody>${d.itens.map(i => `<tr>
            <td>${dataBR(i.data)}</td><td>${esc(i.descricao)}</td><td>${esc(i.pessoa)}</td><td class="dir">${dinheiro(i.valor)}</td>
            <td><span class="sit-ir ${SITUACAO[i.situacao][1]}" title="${esc(i.motivo)}">${SITUACAO[i.situacao][0]}</span></td></tr>`).join('')}</tbody>
        </table></div>
        <p class="dica-planejamento">${dica} Passe o mouse (ou toque) na situação para ver o motivo.
          Para a dedução valer, cada despesa precisa do recibo ou nota com CPF/CNPJ do prestador e de quem foi atendido.</p>`
        : '<p class="dica-planejamento">Nenhuma despesa lançada nesta categoria no ano.</p>'}
    </div>`;
  }

  function render(r) {
    window.__dadosIR = r; // usado pelo botão Imprimir / PDF (relatorio-modulo.js)
    const sel = document.getElementById('irAno');
    const anos = [...new Set([...r.anosDisponiveis, r.ano])].sort((a, b) => b - a);
    sel.innerHTML = anos.map(a => `<option value="${a}">Ano-calendário ${a} (declaração de ${a + 1})</option>`).join('');
    sel.value = r.ano;

    document.getElementById('irConteudo').innerHTML = `
      <div class="cards resumo-grade">
        <div class="card-box">
          <h3>Rendimentos recebidos em ${r.ano}</h3>
          <div class="patrimonio-total">${dinheiro(r.rendimentos.total)}</div>
          <h4 class="subtitulo-ir">Por tipo</h4>
          ${totais(r.rendimentos.porCategoria, 'Nenhuma receita lançada no ano.')}
          <h4 class="subtitulo-ir">Por pessoa</h4>
          ${totais(r.rendimentos.porPessoa, '')}
          <p class="dica-planejamento">Salário vai como rendimento tributável (o valor certo, com o imposto retido, está no informe do
            empregador). Rendimentos de poupança e de LCI/LCA são isentos; os de CDB e Tesouro, de tributação exclusiva — os valores
            vêm no informe de cada banco.</p>
        </div>
        ${blocoDedutivel('Saúde', r.saude, 'Consultas, exames, hospitais, dentistas e planos de saúde podem ser deduzidos sem limite; farmácia e ótica, em geral não.')}
        ${blocoDedutivel('Educação', r.educacao, 'Escola, faculdade e ensino técnico podem ser deduzidos até um limite anual por pessoa; cursos livres, idiomas e material, não.')}
        <div class="card-box">
          <h3>Bens e direitos (investimentos)</h3>
          ${r.bens.length ? `<div class="tabela-ir-wrap"><table class="tabela-ir">
            <thead><tr><th>Investimento</th><th>Tipo</th><th>Instituição</th><th>Aplicação</th><th class="dir">Valor aplicado</th><th class="dir">Valor atual</th></tr></thead>
            <tbody>${r.bens.map(b => `<tr><td>${esc(b.nome)}</td><td>${esc(b.tipo)}</td><td>${esc(b.instituicao || '—')}</td>
              <td>${dataBR(b.dataAplicacao)}</td><td class="dir">${dinheiro(b.valorAplicado)}</td><td class="dir">${dinheiro(b.valorAtual)}</td></tr>`).join('')}</tbody>
          </table></div>
          <p class="dica-planejamento">Aplicações feitas até 31/12/${r.ano}. Ações e FIIs são declarados pelo <strong>custo</strong> (valor aplicado);
            para renda fixa e poupança, use o <strong>saldo em 31/12</strong> que aparece no informe do banco — o "valor atual" aqui é o de hoje.</p>`
            : '<p class="dica-planejamento">Nenhum investimento cadastrado em Metas → Investimentos.</p>'}
        </div>
      </div>`;
  }

  async function carregar(ano) {
    document.getElementById('irConteudo').innerHTML = '<p class="dica-planejamento">Carregando…</p>';
    try {
      render(await api.get('/imposto-renda' + (ano ? '?ano=' + ano : '')));
    } catch (e) {
      document.getElementById('irConteudo').innerHTML = `<p class="dica-planejamento">Não foi possível montar o relatório: ${esc(e.message)}</p>`;
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('irAno').addEventListener('change', e => carregar(e.target.value));
    carregar(new URLSearchParams(location.search).get('ano'));
  });
})();
