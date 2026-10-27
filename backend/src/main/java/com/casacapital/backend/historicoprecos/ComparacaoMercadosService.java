package com.casacapital.backend.historicoprecos;

import com.casacapital.backend.compra.Compra;
import com.casacapital.backend.compra.CompraRepository;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse.Alta;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse.ItemLista;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse.Lista;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse.Mercado;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse.PrecoMercado;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse.Produto;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse.TotalMercado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compara os preços entre mercados usando o Histórico de Preços das notas fiscais (que guarda o mercado).
 * O mesmo produto é reconhecido pelo código de barras; sem ele, pelo nome igual.
 */
@Service
public class ComparacaoMercadosService {

    static final double ALTA_MINIMA_PCT = 10.0;
    static final int ALTA_ULTIMOS_DIAS = 90;
    private static final Set<String> PALAVRAS_VAZIAS = Set.of("de", "da", "do", "das", "dos", "e", "c", "com", "p", "para");

    private final HistoricoPrecoRepository historico;
    private final CompraRepository compras;

    public ComparacaoMercadosService(HistoricoPrecoRepository historico, CompraRepository compras) {
        this.historico = historico;
        this.compras = compras;
    }

    @Transactional(readOnly = true)
    public ComparacaoMercadosResponse comparar(Long familiaId) {
        return comparar(historico.findAllByFamiliaIdOrderByDataAsc(familiaId), compras.findAllByFamiliaId(familiaId),
                LocalDate.now());
    }

    /** Um produto: as compras dele (em ordem de data) e o último preço em cada mercado. */
    static final class Grupo {
        final List<HistoricoPreco> registros = new ArrayList<>();
        final Map<String, HistoricoPreco> ultimoPorMercado = new LinkedHashMap<>(); // chave do mercado → último
        String codigoBarras;

        String nome() {
            return exibir(registros.get(registros.size() - 1).getProdutoChave());
        }

        LocalDate ultimaData() {
            return registros.get(registros.size() - 1).getData();
        }
    }

    ComparacaoMercadosResponse comparar(List<HistoricoPreco> todos, List<Compra> lista, LocalDate hoje) {
        List<HistoricoPreco> ordenados = todos.stream()
                .sorted(Comparator.comparing(HistoricoPreco::getData)
                        .thenComparing(h -> h.getId() == null ? 0L : h.getId()))
                .toList();

        // nome do mercado: a grafia mais recente de cada um
        Map<String, String> nomeMercado = new HashMap<>();
        for (HistoricoPreco h : ordenados) {
            if (h.getMercado() != null) nomeMercado.put(chaveMercado(h.getMercado()), h.getMercado().trim());
        }

        // mesmo produto: pelo código de barras; registros sem código herdam o de outro registro com o mesmo nome
        Map<String, String> codigoPorNome = new HashMap<>();
        for (HistoricoPreco h : ordenados) {
            if (h.getCodigoBarras() != null) codigoPorNome.put(h.getProdutoChave(), h.getCodigoBarras());
        }
        Map<String, Grupo> grupos = new LinkedHashMap<>();
        for (HistoricoPreco h : ordenados) {
            String codigo = h.getCodigoBarras() != null ? h.getCodigoBarras() : codigoPorNome.get(h.getProdutoChave());
            Grupo g = grupos.computeIfAbsent(codigo != null ? "g:" + codigo : "n:" + h.getProdutoChave(), k -> new Grupo());
            g.codigoBarras = codigo;
            g.registros.add(h);
            if (h.getMercado() != null) g.ultimoPorMercado.put(chaveMercado(h.getMercado()), h);
        }

        return new ComparacaoMercadosResponse(mercados(ordenados, grupos, nomeMercado), produtos(grupos, nomeMercado),
                lista(lista, grupos, nomeMercado), altas(grupos, nomeMercado, hoje));
    }

    private static List<Mercado> mercados(List<HistoricoPreco> ordenados, Map<String, Grupo> grupos,
                                          Map<String, String> nomeMercado) {
        Map<String, List<HistoricoPreco>> porMercado = ordenados.stream().filter(h -> h.getMercado() != null)
                .collect(Collectors.groupingBy(h -> chaveMercado(h.getMercado()), LinkedHashMap::new, Collectors.toList()));
        return porMercado.entrySet().stream().map(e -> {
                    int compras = (int) e.getValue().stream().map(HistoricoPreco::getData).distinct().count();
                    LocalDate ultima = e.getValue().get(e.getValue().size() - 1).getData();
                    int produtos = (int) grupos.values().stream().filter(g -> g.ultimoPorMercado.containsKey(e.getKey())).count();
                    return new Mercado(nomeMercado.get(e.getKey()), compras, ultima, produtos);
                })
                .sorted(Comparator.comparing(Mercado::ultimaCompra).reversed())
                .toList();
    }

    private static List<Produto> produtos(Map<String, Grupo> grupos, Map<String, String> nomeMercado) {
        return grupos.values().stream()
                .filter(g -> g.ultimoPorMercado.size() >= 2)
                .map(g -> {
                    List<PrecoMercado> precos = precos(g, nomeMercado);
                    BigDecimal menor = precos.get(0).preco(), maior = precos.get(precos.size() - 1).preco();
                    BigDecimal economia = maior.subtract(menor);
                    Double pct = maior.signum() > 0 ? economia.multiply(BigDecimal.valueOf(100))
                            .divide(maior, 1, RoundingMode.HALF_UP).doubleValue() : null;
                    return new Produto(g.nome(), g.codigoBarras, precos, precos.get(0).mercado(), economia, pct);
                })
                .sorted(Comparator.comparing(Produto::economia).reversed().thenComparing(Produto::nome))
                .limit(500)
                .toList();
    }

    private static List<PrecoMercado> precos(Grupo g, Map<String, String> nomeMercado) {
        return g.ultimoPorMercado.entrySet().stream()
                .map(e -> new PrecoMercado(nomeMercado.get(e.getKey()), e.getValue().getPreco(), e.getValue().getData()))
                .sorted(Comparator.comparing(PrecoMercado::preco).thenComparing(PrecoMercado::mercado))
                .toList();
    }

    // ── Lista de compras em cada mercado ────────────────────────────────

    private static Lista lista(List<Compra> lista, Map<String, Grupo> grupos, Map<String, String> nomeMercado) {
        List<Grupo> comMercado = grupos.values().stream().filter(g -> !g.ultimoPorMercado.isEmpty()).toList();
        List<ItemLista> itens = new ArrayList<>();
        List<Grupo> achados = new ArrayList<>();
        for (Compra c : lista) {
            Grupo g = produtoDoItem(c.getNome(), comMercado);
            achados.add(g);
            int qtd = c.getQtd() != null && c.getQtd() > 0 ? c.getQtd() : 1;
            itens.add(new ItemLista(c.getNome(), qtd, g != null ? g.nome() : null,
                    g != null ? precos(g, nomeMercado) : List.of()));
        }

        Set<String> mercados = nomeMercado.keySet();
        int cestaComum = 0;
        for (Grupo g : achados) {
            if (g != null && mercados.size() >= 2 && g.ultimoPorMercado.keySet().containsAll(mercados)) cestaComum++;
        }

        List<TotalMercado> totais = new ArrayList<>();
        for (String m : mercados) {
            BigDecimal total = BigDecimal.ZERO, totalComum = BigDecimal.ZERO;
            int com = 0;
            for (int i = 0; i < itens.size(); i++) {
                Grupo g = achados.get(i);
                HistoricoPreco h = g != null ? g.ultimoPorMercado.get(m) : null;
                if (h == null) continue;
                BigDecimal valor = h.getPreco().multiply(BigDecimal.valueOf(itens.get(i).qtd()));
                total = total.add(valor);
                com++;
                if (g.ultimoPorMercado.keySet().containsAll(mercados)) totalComum = totalComum.add(valor);
            }
            totais.add(new TotalMercado(nomeMercado.get(m), total, com, itens.size() - com,
                    mercados.size() >= 2 ? totalComum : null));
        }
        // mais barato primeiro: pela cesta comum quando há, senão pelo total
        boolean usarCesta = cestaComum > 0;
        totais.sort(Comparator.comparing((TotalMercado t) -> t.totalCestaComum() != null && usarCesta ? t.totalCestaComum() : t.total())
                .thenComparing(TotalMercado::mercado));
        return new Lista(itens, totais, cestaComum);
    }

    /**
     * Produto das notas que corresponde ao item da lista ("Leite" → "LEITE UHT ITALAC 1L"): nome igual, ou o que
     * tem todas as palavras do item; entre vários, o comprado mais vezes.
     */
    static Grupo produtoDoItem(String nomeItem, List<Grupo> candidatos) {
        String alvo = normalizar(nomeItem);
        List<String> palavras = Arrays.stream(alvo.split(" "))
                .filter(p -> !p.isEmpty() && !PALAVRAS_VAZIAS.contains(p)).toList();
        if (palavras.isEmpty()) return null;

        Grupo melhor = null;
        for (Grupo g : candidatos) {
            boolean igual = g.registros.stream().anyMatch(h -> normalizar(h.getProdutoChave()).equals(alvo));
            if (igual) return g;
            List<String> doProduto = Arrays.asList(normalizar(g.registros.get(g.registros.size() - 1).getProdutoChave()).split(" "));
            boolean todas = palavras.stream().allMatch(p -> doProduto.stream().anyMatch(w -> combina(w, p)));
            if (!todas) continue;
            if (melhor == null || g.registros.size() > melhor.registros.size()
                    || (g.registros.size() == melhor.registros.size() && g.ultimaData().isAfter(melhor.ultimaData()))) {
                melhor = g;
            }
        }
        return melhor;
    }

    /** "leite" combina com "leite"; "bananas" com "banana"; "pao" com "paes" não (abreviações das notas variam). */
    private static boolean combina(String palavraProduto, String palavraItem) {
        if (palavraProduto.startsWith(palavraItem)) return true;
        return palavraItem.length() > 3 && palavraItem.endsWith("s")
                && palavraProduto.startsWith(palavraItem.substring(0, palavraItem.length() - 1));
    }

    // ── Preços que subiram ──────────────────────────────────────────────

    private static List<Alta> altas(Map<String, Grupo> grupos, Map<String, String> nomeMercado, LocalDate hoje) {
        List<Alta> altas = new ArrayList<>();
        for (Grupo g : grupos.values()) {
            for (Map.Entry<String, HistoricoPreco> e : g.ultimoPorMercado.entrySet()) {
                HistoricoPreco atual = e.getValue();
                if (atual.getData().isBefore(hoje.minusDays(ALTA_ULTIMOS_DIAS))) continue;
                // compra anterior no mesmo mercado (preço de outro mercado não é alta)
                HistoricoPreco anterior = null;
                for (HistoricoPreco h : g.registros) {
                    if (h == atual) break;
                    if (h.getMercado() != null && chaveMercado(h.getMercado()).equals(e.getKey())
                            && h.getData().isBefore(atual.getData())) {
                        anterior = h;
                    }
                }
                if (anterior == null || anterior.getPreco().signum() <= 0) continue;
                double pct = atual.getPreco().subtract(anterior.getPreco()).multiply(BigDecimal.valueOf(100))
                        .divide(anterior.getPreco(), 1, RoundingMode.HALF_UP).doubleValue();
                if (pct >= ALTA_MINIMA_PCT) {
                    altas.add(new Alta(g.nome(), nomeMercado.get(e.getKey()), anterior.getPreco(), anterior.getData(),
                            atual.getPreco(), atual.getData(), pct));
                }
            }
        }
        altas.sort(Comparator.comparingDouble(Alta::pct).reversed());
        return altas.size() > 20 ? altas.subList(0, 20) : altas;
    }

    // ── Textos ──────────────────────────────────────────────────────────

    static String chaveMercado(String nome) {
        return normalizar(nome);
    }

    static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** "arroz tio joao t1 5kg" → "Arroz Tio Joao T1 5kg". */
    static String exibir(String chave) {
        return Arrays.stream(chave.trim().split("\\s+"))
                .map(p -> p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1))
                .collect(Collectors.joining(" "));
    }
}
