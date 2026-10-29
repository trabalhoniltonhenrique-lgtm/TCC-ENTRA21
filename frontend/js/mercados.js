// ── CasaCapital — mercados.js: qual mercado é mais barato (notas fiscais importadas) ──
(function () {
  const dinheiro = v => formatarDinheiro(v, window.__familia); // util.js, com a moeda da família
  const pct = v => Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%';
  const dataBR = formatarData; // util.js
  const diasDesde = d => Math.floor((Date.now() - new Date(d + 'T12:00:00')) / 86400000);
  const antigo = d => diasDesde(d) > 90; // preço de mais de 3 meses: pode ter mudado

  let dados = null;

  function precoChip(p, maisBarato) {
    return `<span class="merc-chip ${maisBarato ? 'barato' : ''}" title="${esc(p.mercado)} em ${dataBR(p.data)}">
      <span class="merc-chip-nome">${esc(p.mercado)}</span>
      <strong>${dinheiro(p.preco)}</strong>${antigo(p.data) ? ' <small>(' + dataBR(p.data) + ')</small>' : ''}
    </span>`;
  }

  function blocoLista(l, mercados) {
    if (!l.itens.length) {
      return `<p class="dica-planejamento">A <a href="compras.html">lista de compras</a> está vazia. Adicione itens para ver quanto ela sairia em cada mercado.</p>`;
    }
    const usaCesta = mercados.length >= 2 && l.itensCestaComum > 0;
    const melhor = l.totais[0];
    const pior = l.totais[l.totais.length - 1];
    const valor = t => usaCesta ? t.totalCestaComum : t.total;
    const economia = l.totais.length >= 2 ? Number(valor(pior)) - Number(valor(melhor)) : 0;

    return `
      ${l.totais.length >= 2 && economia > 0.009 ? `<div class="fluxo-ok merc-destaque"><span class="ico ico-check-circle"></span>
        <span>O <strong>${esc(melhor.mercado)}</strong> sai mais barato: <strong>${dinheiro(economia)}</strong> a menos que o ${esc(pior.mercado)}
        ${usaCesta ? `nos ${l.itensCestaComum} itens que têm preço ${mercados.length === 2 ? 'nos dois' : 'em todos'}` : ''}.</span></div>` : ''}
      <div class="merc-totais">${l.totais.map((t, i) => `
        <div class="merc-total ${i === 0 && l.totais.length >= 2 ? 'melhor' : ''}">
          <div class="merc-total-nome">${esc(t.mercado)}</div>
          <div class="merc-total-valor">${dinheiro(valor(t))}</div>
          <div class="merc-total-sub">${usaCesta
            ? `cesta comum de ${l.itensCestaComum} itens · com todos os ${t.itensComPreco} que têm preço aqui: ${dinheiro(t.total)}`
            : `${t.itensComPreco} item(ns) com preço`}${t.itensSemPreco ? ` · ${t.itensSemPreco} sem preço aqui` : ''}</div>
        </div>`).join('')}</div>
      <details class="merc-detalhe-lista">
        <summary>Ver item por item</summary>
        <ul class="merc-itens">${l.itens.map(i => `
          <li>
            <div class="merc-item-nome"><strong>${i.qtd > 1 ? i.qtd + '× ' : ''}${esc(i.nome)}</strong>
              <small>${i.produto ? 'comparado com: ' + esc(i.produto) : 'ainda não comprado com nota fiscal'}</small></div>
            <div class="merc-chips">${i.precos.map((p, k) => precoChip(p, k === 0 && i.precos.length > 1)).join('')}</div>
          </li>`).join('')}</ul>
      </details>
      <p class="dica-planejamento">Estimativa com o último preço pago em cada mercado. A <strong>cesta comum</strong> só soma os itens com preço
        em todos os mercados, para a comparação ser justa.</p>`;
  }

  function renderProdutos(filtro) {
    const el = document.getElementById('mercProdutos');
    const f = filtro.trim().toLowerCase();
    const lista = dados.produtos.filter(p => !f || p.nome.toLowerCase().includes(f));
    el.innerHTML = lista.length ? lista.slice(0, 100).map(p => `
      <li>
        <div class="merc-item-nome"><strong>${esc(p.nome)}</strong>
          <small>${Number(p.economia) > 0 ? `no ${esc(p.maisBarato)} economiza ${dinheiro(p.economia)} (${pct(p.economiaPct)})` : 'mesmo preço'}</small></div>
        <div class="merc-chips">${p.precos.map((x, k) => precoChip(x, k === 0 && Number(p.economia) > 0)).join('')}</div>
      </li>`).join('') + (lista.length > 100 ? `<li class="dica-planejamento">Mostrando 100 de ${lista.length}. Use a busca.</li>` : '')
      : `<li class="dica-planejamento">${f ? 'Nenhum produto com esse nome.' : 'Ainda não há produto comprado em mais de um mercado.'}</li>`;
  }

  function render() {
    const el = document.getElementById('mercConteudo');
    const m = dados.mercados;
    if (!m.length) {
      el.innerHTML = `<div class="card-box"><p class="dica-planejamento">Ainda não há preços com o nome do mercado. Importe as notas fiscais
        das compras em <a href="nota-fiscal.html">Ler nota do mercado</a>: a cada nota, o sistema guarda o preço de cada produto e em
        qual mercado foi. Com notas de <strong>dois mercados ou mais</strong>, a comparação aparece aqui.</p></div>`;
      return;
    }

    el.innerHTML = `
      <div class="merc-mercados">${m.map(x => `
        <div class="resumo-box"><div class="num merc-nome-box">${esc(x.nome)}</div>
          <div class="txt">${x.compras} compra(s) · ${x.produtos} produtos · última em ${dataBR(x.ultimaCompra)}</div></div>`).join('')}</div>
      ${m.length < 2 ? `<p class="aviso-ir"><span class="ico ico-alert-circle"></span><span>Por enquanto só há notas do
        <strong>${esc(m[0].nome)}</strong>. Importe uma nota de outro mercado para comparar.</span></p>` : ''}

      <div class="card-box">
        <h3><span class="ico ico-cart"></span> Sua lista de compras em cada mercado</h3>
        ${blocoLista(dados.lista, m)}
      </div>

      <div class="card-box">
        <h3><span class="ico ico-tag"></span> Onde cada produto está mais barato</h3>
        ${dados.produtos.length ? `<input id="mercBusca" class="merc-busca" placeholder="Buscar produto..." aria-label="Buscar produto">` : ''}
        <ul id="mercProdutos" class="merc-itens"></ul>
        <p class="dica-planejamento">O mesmo produto é reconhecido pelo <strong>código de barras</strong> da nota, mesmo com nomes diferentes em
          cada mercado; sem ele, pelo nome igual. Preço com data entre parênteses tem mais de 3 meses.</p>
      </div>

      <div class="card-box">
        <h3><span class="ico ico-trending-up"></span> Preços que subiram</h3>
        ${dados.altas.length ? `<ul class="lista-resumo">${dados.altas.map(a => `
          <li><span>${esc(a.nome)} <small>${esc(a.mercado)} · ${dinheiro(a.precoAnterior)} em ${dataBR(a.dataAnterior)} → ${dinheiro(a.precoAtual)} em ${dataBR(a.data)}</small></span>
            <strong class="var-ruim">+${pct(a.pct)}</strong></li>`).join('')}</ul>
          <p class="dica-planejamento">Altas de 10% ou mais no mesmo mercado, nos últimos 3 meses.</p>`
          : '<p class="dica-planejamento">Nenhuma alta de 10% ou mais nos últimos 3 meses.</p>'}
      </div>`;

    renderProdutos('');
    const busca = document.getElementById('mercBusca');
    if (busca) busca.addEventListener('input', e => renderProdutos(e.target.value));
  }

  document.addEventListener('DOMContentLoaded', async () => {
    try {
      dados = await api.get('/mercados/comparacao');
      render();
    } catch (e) {
      document.getElementById('mercConteudo').innerHTML = `<p class="dica-planejamento">Não foi possível comparar agora: ${esc(e.message)}</p>`;
    }
  });
})();
