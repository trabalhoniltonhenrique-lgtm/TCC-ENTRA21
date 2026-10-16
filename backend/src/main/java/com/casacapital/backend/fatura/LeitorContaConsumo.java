package com.casacapital.backend.fatura;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconhece contas de consumo (luz, água) em PDF e lê o valor a pagar, o vencimento e a referência.
 * Diferente da fatura do cartão, a conta de consumo vira UMA conta a pagar, não uma lista de lançamentos.
 */
final class LeitorContaConsumo {

    record ContaLida(String categoria, String descricao, BigDecimal valor, LocalDate vencimento, String referencia) {
    }

    private static final String VALOR = "(?<![\\d.])(\\d{1,3}(?:\\.\\d{3})*,\\d{2})";

    private static final String[][] ENERGIA = {
            {"CELESC", "Celesc"}, {"CEMIG", "Cemig"}, {"ENEL ", "Enel"}, {"COPEL", "Copel"}, {"LIGHT S", "Light"},
            {"CPFL", "CPFL"}, {"ENERGISA", "Energisa"}, {"EQUATORIAL", "Equatorial"}, {"COELBA", "Coelba"},
            {"CELPE", "Celpe"}, {"COSERN", "Cosern"}, {"NEOENERGIA", "Neoenergia"}, {"RGE SUL", "RGE"},
            {"CEEE", "CEEE"}, {"ELEKTRO", "Elektro"}, {"AMAZONAS ENERGIA", "Amazonas Energia"}, {"EDP ", "EDP"},
    };
    private static final String[][] AGUA = {
            {"CASAN", "Casan"}, {"SABESP", "Sabesp"}, {"COPASA", "Copasa"}, {"SANEPAR", "Sanepar"},
            {"CEDAE", "Cedae"}, {"CAGECE", "Cagece"}, {"EMBASA", "Embasa"}, {"COMPESA", "Compesa"},
            {"CORSAN", "Corsan"}, {"SAMAE", "Samae"}, {"SAAE", "SAAE"}, {"AGUAS DE ", "Águas"},
    };

    // Em ordem de preferência. DOTALL: em muitas contas o valor vem algumas linhas abaixo do rótulo.
    private static final Pattern[] TOTAL = {
            Pattern.compile("TOTAL A PAGAR[^,]{0,80}?" + VALOR, Pattern.DOTALL),
            Pattern.compile("VALOR A PAGAR[^,]{0,80}?" + VALOR, Pattern.DOTALL),
            Pattern.compile("VALOR (?:TOTAL )?DO DOCUMENTO[^,]{0,40}?" + VALOR, Pattern.DOTALL),
            Pattern.compile("(?m)^TOTAL\\s+(?:R\\$\\s*)?" + VALOR + "\\s*$"),
    };
    private static final Pattern VENCIMENTO = Pattern.compile(
            "VENCIMENTO\\s*:?[^0-9]{0,20}(\\d{2})/(\\d{2})/(\\d{4})", Pattern.DOTALL);
    private static final Pattern REFERENCIA = Pattern.compile(
            "(?:REFERENCIA|MES/ANO|MES DE REFERENCIA)\\s*:?\\s*(\\d{2}/\\d{4})", Pattern.DOTALL);

    private LeitorContaConsumo() {
    }

    static Optional<ContaLida> ler(List<String> paginas) {
        String texto = LeitorFaturaPdf.normalizar(String.join("\n", paginas));

        String categoria;
        String descricao;
        String energia = nome(texto, ENERGIA);
        String agua = nome(texto, AGUA);
        if (texto.contains("KWH") && (energia != null || texto.contains("ENERGIA"))) {
            categoria = "Energia";
            descricao = "Energia elétrica" + (energia != null ? " - " + energia : "");
        } else if (texto.contains("AGUA") && (texto.contains("ESGOTO") || texto.contains("M3")) && agua != null) {
            categoria = "Água";
            descricao = "Água e esgoto - " + agua;
        } else {
            return Optional.empty();
        }

        BigDecimal valor = null;
        for (Pattern p : TOTAL) {
            Matcher m = p.matcher(texto);
            if (m.find()) {
                valor = new BigDecimal(m.group(1).replace(".", "").replace(",", "."));
                break;
            }
        }

        LocalDate vencimento = null;
        Matcher v = VENCIMENTO.matcher(texto);
        while (vencimento == null && v.find()) {
            try {
                vencimento = LocalDate.of(Integer.parseInt(v.group(3)), Integer.parseInt(v.group(2)), Integer.parseInt(v.group(1)));
            } catch (DateTimeException ignored) {
                // tenta a próxima ocorrência
            }
        }

        Matcher r = REFERENCIA.matcher(texto);
        String referencia = r.find() ? r.group(1) : null;
        if (referencia != null) descricao += " " + referencia;

        return Optional.of(new ContaLida(categoria, descricao, valor, vencimento, referencia));
    }

    private static String nome(String texto, String[][] lista) {
        for (String[] e : lista) {
            if (texto.contains(e[0])) return e[1];
        }
        return null;
    }
}
