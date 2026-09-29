// ── CasaCapital — historico-precos.js ──
let _expandidosHistorico = {}; // { nomeChave: true/false }

function listaProdutosComHistorico() {
  return Object.keys(historicoPrecos)
    .filter(chave => historicoPrecos[chave].length > 0)
    .map(chave => {
      const registros = historicoPrecos[chave];
      const ultimo     = registros[registros.length - 1];
      // Nome de exibição: usa o nome original mais recentemente visto na dispensa/compras,
      // com fallback para a própria chave (caso o item já tenha sido removido de todo lugar)
      const nomeExibicao = nomeOriginalDoProduto(chave);
      return { chave, nome: nomeExibicao, registros, ultimo: ultimo.preco, dataUltimo: ultimo.data };
    })
    .sort((a, b) => a.nome.localeCompare(b.nome));
}

function nomeOriginalDoProduto(chave) {
  const naDispensa = dispensa.find(d => d.nome.toLowerCase() === chave);
  if (naDispensa) return naDispensa.nome;
  const nasCompras = compras.find(c => c.nome.toLowerCase() === chave);
  if (nasCompras) return nasCompras.nome;
  // Sem ocorrência ativa: capitaliza a chave como fallback de exibição
  return chave.charAt(0).toUpperCase() + chave.slice(1);
}

function toggleHistoricoProduto(chave) {
  _expandidosHistorico[chave] = !_expandidosHistorico[chave];
  renderHistoricoPrecos();
}

function fmtDataHistorico(d) {
  return new Date(d + 'T12:00:00').toLocaleDateString('pt-BR');
}

function renderHistoricoPrecos() {
  const lista  = document.getElementById('listaHistoricoPrecos');
  const resumo = document.getElementById('resumoHistoricoPrecos');
  const busca  = document.getElementById('buscaHistoricoPreco')?.value.toLowerCase() || '';
  if (!lista) return;

  const produtos = listaProdutosComHistorico();
  const filtrados = busca ? produtos.filter(p => p.nome.toLowerCase().includes(busca)) : produtos;

  // Resumo geral
  const totalProdutos = produtos.length;
  const subiram = produtos.filter(p => { const v = variacaoPreco(p.nome); return v && v.subiu; }).length;
  const desceram = produtos.filter(p => { const v = variacaoPreco(p.nome); return v && v.desceu; }).length;
  resumo.innerHTML = `
    <div class="resumo-box"><div class="num">${totalProdutos}</div><div class="txt">Produtos monitorados</div></div>
    <div class="resumo-box cor-zero"><div class="num">${subiram}</div><div class="txt">Com aumento de preço</div></div>
    <div class="resumo-box cor-ok"><div class="num">${desceram}</div><div class="txt">Com redução de preço</div></div>
  `;

  if (!filtrados.length) {
    lista.innerHTML = `<div class="lista-vazia">
      <div class="icone"><span class="ico ico-tag"></span></div>
      <p>${busca ? 'Nenhum produto encontrado.' : 'Nenhum preço registrado ainda.<br>Informe o valor ao marcar itens como comprados na Lista de Compras.'}</p>
      ${!busca ? `<a href="compras.html"><button class="btn-ir-compras"><span class="ico ico-cart"></span> Ir para Compras</button></a>` : ''}
    </div>`;
    return;
  }

  lista.innerHTML = filtrados.map(p => {
    const v = variacaoPreco(p.nome);
    const corCls = v ? (v.subiu ? 'cor-atrasada' : v.desceu ? 'cor-completa' : 'cor-andamento') : 'cor-andamento';
    const expandido = !!_expandidosHistorico[p.chave];

    return `
    <div class="conta-card grupo historico-produto-card" data-action="toggle-historico" data-chave="${p.chave}">
      <div class="grupo-linha-topo">
        <div class="conta-info">
          <strong><span class="ico ico-cart"></span> ${p.nome}</strong>
          <span>${p.registros.length} compra${p.registros.length > 1 ? 's' : ''} registrada${p.registros.length > 1 ? 's' : ''} · Última: ${fmtDataHistorico(p.dataUltimo)}</span>
        </div>
        <div class="grupo-restante">
          <div class="rotulo">Preço atual</div>
          <div class="valor">${fmt(p.ultimo)}</div>
        </div>
        <span class="grupo-seta ${expandido ? 'expandido' : ''}">▾</span>
      </div>

      ${v ? `<div class="linha-variacao-historico">
        ${tagVariacaoPreco(p.nome)}
        <span class="texto-variacao-historico">em relação à compra anterior (${fmt(v.anterior)})</span>
      </div>` : ''}

      ${expandido ? `
        <div class="grupo-detalhes" data-action="stop-propagation">
          ${[...p.registros].reverse().map((r, i, arr) => {
            const anterior = arr[i + 1]; // próximo no array invertido = compra anterior cronologicamente
            let variacaoLinha = '';
            if (anterior && anterior.preco > 0) {
              const pct = ((r.preco - anterior.preco) / anterior.preco) * 100;
              const cls = pct > 0.05 ? 'preco-subiu' : pct < -0.05 ? 'preco-desceu' : 'preco-igual';
              const seta = pct > 0.05 ? '↑' : pct < -0.05 ? '↓' : '→';
              variacaoLinha = `<span class="tag-preco ${cls}">${seta} ${Math.abs(pct).toFixed(0)}%</span>`;
            }
            return `
            <div class="parcela-linha">
              <span class="desc">${fmtDataHistorico(r.data)}</span>
              ${variacaoLinha}
              <span class="valor cor-normal">${fmt(r.preco)}</span>
            </div>`;
          }).join('')}
        </div>
      ` : ''}
    </div>`;
  }).join('');
}

document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('buscaHistoricoPreco').addEventListener('input', renderHistoricoPrecos);

  document.getElementById('listaHistoricoPrecos').addEventListener('click', function (e) {
    const acaoEl = e.target.closest('[data-action]');
    if (!acaoEl) return;
    const acao = acaoEl.getAttribute('data-action');
    if (acao === 'stop-propagation') { e.stopPropagation(); return; }
    if (acao === 'toggle-historico') toggleHistoricoProduto(acaoEl.getAttribute('data-chave'));
  });

  document.addEventListener('casacapital-dados-carregados', renderHistoricoPrecos);
});
