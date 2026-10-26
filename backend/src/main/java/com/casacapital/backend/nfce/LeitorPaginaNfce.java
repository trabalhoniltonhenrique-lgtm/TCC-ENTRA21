package com.casacapital.backend.nfce;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê a página de consulta da NFC-e no layout usado pela maioria dos estados (tabela "tabResult": nome,
 * código, quantidade, unidade, valor unitário e total de cada item; emitente, data de emissão e total no
 * topo/rodapé). Quando as classes não estão lá, tenta pelo texto de cada linha.
 */
final class LeitorPaginaNfce {

    private static final Pattern NUMERO = Pattern.compile("-?[\\d.]*\\d(?:,\\d+)?|-?\\d+(?:\\.\\d+)?");
    private static final Pattern CNPJ = Pattern.compile("CNPJ:?\\s*(\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2})");
    private static final Pattern EMISSAO = Pattern.compile("Emiss[ãa]o:?\\s*(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern CHAVE_TEXTO = Pattern.compile("(\\d{4}(?:\\s?\\d{4}){10})");
    private static final Pattern VALOR_PAGAR = Pattern.compile("Valor\\s+a\\s+pagar\\s*R\\$:?\\s*([\\d.]+,\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALOR_TOTAL = Pattern.compile("Valor\\s+total\\s*R\\$:?\\s*([\\d.]+,\\d{2})", Pattern.CASE_INSENSITIVE);
    // "ARROZ 5KG (Código: 123) Qtde.:1 UN: UN Vl. Unit.: 24,90 Vl. Total 24,90"
    private static final Pattern ITEM_TEXTO = Pattern.compile(
            "^(.+?)\\s*\\(\\s*C[óo]digo:\\s*([^)]*)\\)\\s*Qtde\\.?:\\s*([\\d.,]+)\\s*UN:\\s*(\\S+)\\s*Vl\\.\\s*Unit\\.?:\\s*([\\d.,]+)\\s*Vl\\.\\s*Total\\s*([\\d.,]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CODIGO = Pattern.compile("\\(\\s*C[óo]d(?:igo)?\\.?:?\\s*([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QTDE = Pattern.compile("Qtde\\.?:?\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UN = Pattern.compile("\\bUN:?\\s*([A-Za-z]{1,6})\\b");
    private static final Pattern VL_UNIT = Pattern.compile("Vl\\.?\\s*Unit\\.?:?\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VL_TOTAL = Pattern.compile("Vl\\.?\\s*Total:?\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private LeitorPaginaNfce() {
    }

    static Document abrir(byte[] html, String endereco) throws IOException {
        return Jsoup.parse(new ByteArrayInputStream(html), null, endereco);
    }

    static NotaFiscalLida ler(Document doc, String endereco) {
        List<NotaFiscalLida.Item> itens = lerItensPorClasse(doc);
        if (itens.isEmpty()) itens = lerItensPorTexto(doc);

        String texto = doc.text().replace(' ', ' ');
        String emitente = primeiroTexto(doc, "#u20", ".txtTopo", "#conteudo .txtCenter .txtTopo");
        String cnpj = grupo(CNPJ, texto);
        LocalDate emissao = data(grupo(EMISSAO, texto));

        BigDecimal total = null;
        Element totalPagar = elementoAposRotulo(doc, "Valor a pagar");
        if (totalPagar != null) total = numero(totalPagar.text());
        if (total == null) total = numero(grupo(VALOR_PAGAR, texto));
        if (total == null) total = numero(grupo(VALOR_TOTAL, texto));
        if (total == null && !itens.isEmpty()) {
            total = itens.stream().map(NotaFiscalLida.Item::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        String chave = chaveDoEndereco(endereco);
        if (chave == null) {
            Element el = doc.selectFirst(".chave");
            String bruto = el != null ? el.text() : grupo(CHAVE_TEXTO, texto);
            if (bruto != null) {
                String digitos = bruto.replaceAll("\\D", "");
                if (digitos.length() == 44) chave = digitos;
            }
        }
        return new NotaFiscalLida(chave, emitente, cnpj, emissao, total, itens);
    }

    private static List<NotaFiscalLida.Item> lerItensPorClasse(Document doc) {
        List<NotaFiscalLida.Item> itens = new ArrayList<>();
        for (Element tr : doc.select("#tabResult tr, table.tabResult tr")) {
            Element nome = tr.selectFirst("span.txtTit, .txtTit2");
            Element vlUnit = tr.selectFirst(".RvlUnit");
            if (nome == null || vlUnit == null) continue;
            BigDecimal unit = numero(depoisDe(vlUnit.text(), ":"));
            BigDecimal qtd = numero(depoisDe(texto(tr.selectFirst(".Rqtd")), ":"));
            BigDecimal totalItem = numero(texto(tr.selectFirst("span.valor")));
            String un = depoisDe(texto(tr.selectFirst(".RUN")), ":");
            String codigo = texto(tr.selectFirst(".RCod")).replaceAll("(?i)[()]|c[óo]digo:?", "").trim();
            NotaFiscalLida.Item item = item(nome.text(), codigo, qtd, un, unit, totalItem);
            if (item != null) itens.add(item);
        }
        return itens;
    }

    private static List<NotaFiscalLida.Item> lerItensPorTexto(Document doc) {
        List<NotaFiscalLida.Item> itens = new ArrayList<>();
        for (Element tr : doc.select("tr")) {
            if (!tr.select("tr").stream().skip(1).toList().isEmpty()) continue; // só linhas sem tabela dentro
            Matcher m = ITEM_TEXTO.matcher(tr.text().replace(' ', ' ').trim());
            if (!m.find()) continue;
            NotaFiscalLida.Item item = item(m.group(1), m.group(2).trim(), numero(m.group(3)), m.group(4),
                    numero(m.group(5)), numero(m.group(6)));
            if (item != null) itens.add(item);
        }
        return itens;
    }

    private static NotaFiscalLida.Item item(String nome, String codigo, BigDecimal qtd, String un,
                                            BigDecimal unit, BigDecimal total) {
        String n = nome == null ? "" : nome.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        if (n.isEmpty()) return null;
        if (qtd == null || qtd.signum() <= 0) qtd = BigDecimal.ONE;
        if (unit == null && total != null) unit = total.divide(qtd, 2, RoundingMode.HALF_UP);
        if (total == null && unit != null) total = unit.multiply(qtd).setScale(2, RoundingMode.HALF_UP);
        if (unit == null) return null;
        return new NotaFiscalLida.Item(n, codigo == null || codigo.isBlank() ? null : codigo, qtd,
                un == null || un.isBlank() ? null : un.trim().toUpperCase(), unit, total);
    }

    /**
     * Texto da página da nota copiado do navegador (Selecionar tudo → Copiar). Cada item tem "(Código: ...)"
     * depois do nome e, em seguida, quantidade, unidade, valor unitário e total. Se vier o HTML, lê como página.
     */
    static NotaFiscalLida lerTexto(String colado, String endereco) throws IOException {
        String t = colado.replace(' ', ' ').replace("\r", "");
        if (t.contains("<table") || t.contains("<html") || t.contains("<div")) {
            return ler(abrir(t.getBytes(StandardCharsets.UTF_8), endereco), endereco);
        }

        List<NotaFiscalLida.Item> itens = new ArrayList<>();
        List<int[]> codigos = new ArrayList<>();
        Matcher mc = CODIGO.matcher(t);
        while (mc.find()) codigos.add(new int[]{mc.start(), mc.end(), mc.start(1), mc.end(1)});

        int fimAnterior = 0;
        for (int k = 0; k < codigos.size(); k++) {
            int[] c = codigos.get(k);
            String nome = nomeAntes(t.substring(Math.min(fimAnterior, c[0]), c[0]));
            int limite = k + 1 < codigos.size() ? codigos.get(k + 1)[0] : t.length();
            String depois = t.substring(c[1], limite);
            Matcher q = QTDE.matcher(depois), u = UN.matcher(depois), vu = VL_UNIT.matcher(depois), vt = VL_TOTAL.matcher(depois);
            BigDecimal qtd = q.find() ? numero(q.group(1)) : null;
            String un = u.find() ? u.group(1) : null;
            BigDecimal unit = vu.find() ? numero(vu.group(1)) : null;
            BigDecimal total = null;
            fimAnterior = c[1];
            if (vt.find()) {
                total = numero(vt.group(1));
                fimAnterior = c[1] + vt.end();
            }
            NotaFiscalLida.Item item = item(nome, t.substring(c[2], c[3]).trim(), qtd, un, unit, total);
            if (item != null) itens.add(item);
        }

        String linear = t.replaceAll("\\s+", " ");
        BigDecimal total = numero(grupo(VALOR_PAGAR, linear));
        if (total == null) total = numero(grupo(VALOR_TOTAL, linear));
        if (total == null && !itens.isEmpty()) {
            total = itens.stream().map(NotaFiscalLida.Item::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        String chave = chaveDoEndereco(endereco);
        if (chave == null) {
            String bruto = grupo(CHAVE_TEXTO, linear);
            if (bruto != null && bruto.replaceAll("\\D", "").length() == 44) chave = bruto.replaceAll("\\D", "");
        }
        return new NotaFiscalLida(chave, null, grupo(CNPJ, linear), data(grupo(EMISSAO, linear)), total, itens);
    }

    /** Nome do produto: a última linha com texto antes do "(Código". */
    private static String nomeAntes(String trecho) {
        String[] linhas = trecho.split("\n");
        for (int i = linhas.length - 1; i >= 0; i--) {
            String l = linhas[i].replaceAll("\\s+", " ").trim();
            if (!l.isEmpty()) return l;
        }
        return "";
    }

    /** Chave de acesso (44 dígitos) no parâmetro p= (QR versão 2) ou chNFe= (versão 1) do link do QR code. */
    static String chaveDoEndereco(String endereco) {
        if (endereco == null) return null;
        int q = endereco.indexOf('?');
        if (q < 0) return null;
        for (String par : endereco.substring(q + 1).split("&")) {
            int igual = par.indexOf('=');
            if (igual < 0) continue;
            String nome = par.substring(0, igual).toLowerCase();
            if (!nome.equals("p") && !nome.equals("chnfe")) continue;
            String valor = URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\\d{44}").matcher(valor);
            if (m.find()) return m.group();
        }
        return null;
    }

    /** "1.234,56" → 1234.56; "0,535" → 0.535; "24.9" → 24.9. */
    static BigDecimal numero(String s) {
        if (s == null) return null;
        Matcher m = NUMERO.matcher(s.replace(' ', ' '));
        if (!m.find()) return null;
        String n = m.group();
        n = n.contains(",") ? n.replace(".", "").replace(',', '.') : n;
        try {
            return new BigDecimal(n);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Element elementoAposRotulo(Document doc, String rotulo) {
        for (Element el : doc.select("label, span, div")) {
            if (!el.ownText().toLowerCase().contains(rotulo.toLowerCase())) continue;
            Element alvo = el.nextElementSibling();
            if (alvo != null && alvo.hasClass("totalNumb")) return alvo;
            Element pai = el.parent();
            if (pai != null) {
                Element num = pai.selectFirst(".totalNumb");
                if (num != null && num != el) return num;
            }
        }
        return null;
    }

    private static String primeiroTexto(Document doc, String... seletores) {
        for (String s : seletores) {
            Element el = doc.selectFirst(s);
            if (el != null && !el.text().isBlank()) return el.text().replace(' ', ' ').trim();
        }
        return null;
    }

    private static String texto(Element el) {
        return el == null ? "" : el.text().replace(' ', ' ');
    }

    private static String depoisDe(String s, String marca) {
        int i = s.indexOf(marca);
        return (i >= 0 ? s.substring(i + 1) : s).trim();
    }

    private static String grupo(Pattern p, String texto) {
        Matcher m = p.matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    private static LocalDate data(String s) {
        try {
            return s == null ? null : LocalDate.parse(s, DATA_BR);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
