package com.casacapital.backend.fatura;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê o texto extraído de uma fatura de cartão em PDF e encontra os lançamentos — sem serviço externo.
 * Cada lançamento é uma linha que começa com a data ("14/09", "14/09/2026", "14 SET" ou "14 de set. 2026") e termina
 * com o valor ("1.234,56"); parcelas aparecem na descrição ("03/10", "PARC 3/10", "(Parcela 03 de 10)").
 */
final class LeitorFaturaPdf {

    /**
     * total: valor a pagar impresso na fatura. totalLancamentos: soma das compras impressa pelo banco ("Despesas do mês",
     * "Lançamentos atuais"), quando existe — difere do total quando parte da fatura já foi paga antes (antecipação).
     */
    record FaturaLida(String emissor, LocalDate vencimento, BigDecimal total, BigDecimal totalLancamentos,
                      List<Lancamento> lancamentos) {
    }

    record Lancamento(LocalDate data, String descricao, BigDecimal valor, int parcelaAtual, int parcelaTotal,
                      String categoria, boolean credito) {
    }

    private static final String MESES = "JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ";
    private static final String VALOR = "(\\d{1,3}(?:\\.\\d{3})*,\\d{2})";

    // Data no início da linha, descrição, valor no fim. Sinal de menos colado no valor ("-912,34", "-R$ 25,48") ou "+"
    // indicam crédito; já " - R$ 487,89" (traço com espaço antes do R$, como no Inter) é só separador.
    private static final Pattern LINHA = Pattern.compile(
            "^(?<dia>\\d{1,2})\\s*(?:[/.-]\\s*(?<mesNum>\\d{1,2})|\\s(?:DE\\s)?(?<mesNome>" + MESES + ")[A-Z]*\\.?)"
                    + "(?:[/.-](?<ano>\\d{2,4})|\\s(?:DE\\s)?(?<ano2>20[2-9]\\d)(?=\\s))?\\s+(?<desc>.+?)\\s+"
                    + "(?:-\\s+(?=R\\$|\\+))?(?<mais>\\+\\s*)?(?<neg1>-\\s*)?(?:R\\$\\s*)?(?<neg2>-\\s*)?"
                    + "(?<valor>\\d{1,3}(?:\\.\\d{3})*,\\d{2})(?<neg3>\\s*-)?\\s*(?:[DC])?$");

    private static final Pattern VALOR_NO_FIM = Pattern.compile("\\s+(?:US\\$|U\\$|R\\$|USD|BRL)?\\s*-?" + VALOR + "$");
    private static final Pattern MOEDA_NO_FIM = Pattern.compile("\\s+(?:US\\$|U\\$|R\\$|USD|BRL)$");

    private static final Pattern[] PARCELA = {
            Pattern.compile("\\s*-?\\s*\\(?PARC(?:ELA)?\\.?\\s*(\\d{1,2})\\s*(?:/|DE)\\s*(\\d{1,2})\\b\\)?"),
            Pattern.compile("\\s*\\(?\\b(\\d{1,2})\\s*/\\s*(\\d{1,2})\\)?\\s*$"),
            Pattern.compile("\\s*\\(?\\b(\\d{1,2})\\s*/\\s*(\\d{1,2})\\)?\\s"),
    };

    // Linhas que não são compras: pagamento da fatura anterior, saldos, totais, limites…
    private static final Pattern IGNORAR = Pattern.compile(
            "^PAGAMENTO\\b|^PAGTO\\b|PAGAMENTO (?:EFETUADO|RECEBIDO|DE FATURA|DA FATURA|OBRIGADO)|"
                    + "\\bSALDO\\b|\\bTOTAL\\b|\\bSUBTOTAL\\b|\\bLIMITE\\b|VENCIMENTO|FECHAMENTO|"
                    + "PAGAMENTO MINIMO|VALOR MINIMO|MELHOR DATA");
    private static final Pattern CREDITO = Pattern.compile("ESTORNO|CREDITO|DEVOLUCAO|CASHBACK|DESCONTO|REEMBOLSO|AJUSTE A CREDITO");

    // Título de seção com parcelas de faturas FUTURAS (não são cobradas nesta fatura). Só no início da linha,
    // para não confundir com frases como "o saldo será cobrado na próxima fatura".
    private static final Pattern SECAO_FUTURA = Pattern.compile(
            "^(?:COMPRAS PARCELADAS\\s*-?\\s*)?(?:PROXIMAS FATURAS|LANCAMENTOS FUTUROS|PARCELAS FUTURAS|"
                    + "PARCELAMENTOS FUTUROS|DEMAIS FATURAS|COMPRAS PARCELADAS\\s*-\\s*PROXIMAS|PROXIMA FATURA$)");

    // Início de um bloco de compras: encerra uma seção de faturas futuras que veio antes na mesma página
    private static final Pattern SECAO_COMPRAS = Pattern.compile(
            "^(?:TRANSACOES|LANCAMENTOS)(?!.*FUTUR)|^DESPESAS DO MES|^COMPRAS NACIONAIS|^COMPRAS INTERNACIONAIS");

    // "Vencimento: 14/10/2026", "Data de vencimento: 14 OUT 2026" ou o rótulo numa linha e a data na de baixo
    private static final Pattern VENCIMENTO = Pattern.compile(
            "VENCIMENTO[^0-9]{0,40}(\\d{1,2})\\s*(?:/\\s*(\\d{2})\\s*/|\\s(" + MESES + ")[A-Z]*\\s)\\s*(\\d{2,4})");

    // Final do cartão antes da loja: "•••• 4939 Amazon", "**** 4939 Amazon", "XXXX 4939 Amazon"
    private static final Pattern FINAL_CARTAO = Pattern.compile("^(?:[•*X]{4}\\s*)(?:\\d{4}\\s+)?");

    // Em ordem de preferência: "Total desta fatura" antes de "Total da fatura anterior", que é outra coisa
    private static final Pattern[] TOTAL = {
            Pattern.compile("TOTAL DESTA FATURA[^0-9\\n]{0,40}" + VALOR),
            Pattern.compile("TOTAL DA SUA FATURA[^0-9]{0,40}" + VALOR), // o valor pode vir na linha de baixo
            Pattern.compile("(?:VALOR )?TOTAL DA FATURA(?! ANTERIOR)[^0-9\\n]{0,40}" + VALOR),
            Pattern.compile("(?:TOTAL A PAGAR|VALOR TOTAL(?! FINANCIADO))[^0-9\\n]{0,40}" + VALOR),
    };

    // Soma das compras impressa pelo banco (para conferir a leitura quando o total a pagar já desconta pagamentos)
    private static final Pattern[] TOTAL_LANCAMENTOS = {
            Pattern.compile("DESPESAS DO MES[^0-9]{0,20}" + VALOR),
            Pattern.compile("TOTAL DOS LANCAMENTOS ATUAIS[^0-9\\n]{0,20}" + VALOR),
            Pattern.compile("LANCAMENTOS ATUAIS[^0-9\\n]{0,20}" + VALOR),
    };

    // Categoria que alguns bancos (ex.: Itaú) imprimem na linha logo abaixo da compra
    private static final Pattern CATEGORIA_BANCO = Pattern.compile(
            "^(SUPERMERCADOS?|RESTAURANTES?|ALIMENTACAO|SAUDE|FARMACIA|VEICULOS|TRANSPORTE|VIAGEM|TURISMO|"
                    + "ENTRETENIMENTO|HOBBY|LAZER|VESTUARIO|EDUCACAO|SERVICOS|CASA|MORADIA|CONSTRUCAO)\\b");

    // Vence o banco citado mais vezes: o boleto costuma citar outros ("SR. CAIXA, NÃO ACEITAR…", banco recebedor)
    private static final String[][] EMISSORES = {
            {"NUBANK", "Nubank"}, {"NU PAGAMENTOS", "Nubank"}, {"ITAU", "Itaú"}, {"BRADESCO", "Bradesco"},
            {"SANTANDER", "Santander"}, {"OUROCARD", "Banco do Brasil"}, {"BANCO DO BRASIL", "Banco do Brasil"},
            {"CAIXA ECONOMICA", "Caixa"}, {"CARTAO CAIXA", "Caixa"}, {"CAIXA CARTOES", "Caixa"},
            {"BANCO INTER", "Inter"}, {"BANCOINTER", "Inter"}, {"CONTA INTER", "Inter"}, {"CARTAO INTER", "Inter"},
            {"DO INTER", "Inter"}, {"C6 BANK", "C6 Bank"}, {"BTG", "BTG Pactual"},
            {"XP INVESTIMENTOS", "XP"}, {"MERCADO PAGO", "Mercado Pago"}, {"PICPAY", "PicPay"},
            {"PORTO SEGURO", "Porto Seguro"}, {"SICREDI", "Sicredi"}, {"SICOOB", "Sicoob"}, {"NEON", "Neon"},
            {"BANCO PAN", "Banco Pan"}, {"CARREFOUR", "Carrefour"}, {"WILL BANK", "Will Bank"},
    };

    private LeitorFaturaPdf() {
    }

    /**
     * Cada banco monta o PDF de um jeito: o texto pode sair na ordem em que foi gravado ou ordenado pela posição na
     * página (colunas lado a lado se misturam). Lê as duas versões e fica com a que bate com o total impresso.
     */
    static FaturaLida lerMelhor(List<String> ordemDoArquivo, List<String> ordemDaPagina, LocalDate hoje) {
        FaturaLida a = ler(ordemDoArquivo, hoje);
        FaturaLida b = ler(ordemDaPagina, hoje);
        BigDecimal da = diferenca(a), db = diferenca(b);
        if (da != null && db != null && da.compareTo(db) != 0) return da.compareTo(db) < 0 ? a : b;
        return b.lancamentos().size() > a.lancamentos().size() ? b : a;
    }

    /** Menor |compras − créditos − total impresso| entre o total a pagar e a soma das compras impressa; null sem total. */
    private static BigDecimal diferenca(FaturaLida f) {
        BigDecimal soma = BigDecimal.ZERO;
        for (Lancamento l : f.lancamentos()) soma = l.credito() ? soma.subtract(l.valor()) : soma.add(l.valor());
        BigDecimal menor = null;
        for (BigDecimal t : new BigDecimal[]{f.total(), f.totalLancamentos()}) {
            if (t == null) continue;
            BigDecimal d = soma.subtract(t).abs();
            if (menor == null || d.compareTo(menor) < 0) menor = d;
        }
        return menor;
    }

    /** paginas: texto de cada página do PDF, na ordem. */
    static FaturaLida ler(List<String> paginas, LocalDate hoje) {
        String tudo = normalizar(String.join("\n", paginas));
        LocalDate vencimento = vencimento(tudo);
        LocalDate referencia = vencimento != null ? vencimento : hoje;

        List<Lancamento> lancamentos = new ArrayList<>();
        for (String pagina : paginas) {
            boolean secaoFutura = false; // vale até o fim da página
            boolean linhaAnteriorFoiLancamento = false;
            for (String bruta : pagina.split("\\R")) {
                String linha = normalizar(bruta).replaceAll("\\s+", " ").trim();
                if (linha.isEmpty()) continue;
                if (SECAO_FUTURA.matcher(linha).find()) secaoFutura = true;
                else if (SECAO_COMPRAS.matcher(linha).find()) secaoFutura = false;
                if (secaoFutura) continue;
                Lancamento l = lancamento(linha, bruta, referencia);
                if (l != null) {
                    lancamentos.add(l);
                } else if (linhaAnteriorFoiLancamento) {
                    Matcher c = CATEGORIA_BANCO.matcher(linha);
                    if (c.find()) {
                        Lancamento u = lancamentos.removeLast();
                        lancamentos.add(new Lancamento(u.data(), u.descricao(), u.valor(), u.parcelaAtual(),
                                u.parcelaTotal(), categoriaDoBanco(c.group(1)), u.credito()));
                    }
                }
                linhaAnteriorFoiLancamento = l != null;
            }
        }

        return new FaturaLida(emissor(tudo), vencimento, valor(tudo, TOTAL), valor(tudo, TOTAL_LANCAMENTOS), lancamentos);
    }

    private static Lancamento lancamento(String linha, String bruta, LocalDate referencia) {
        Matcher m = LINHA.matcher(linha);
        if (!m.matches()) return null;

        LocalDate data = data(m.group("dia"), m.group("mesNum"), m.group("mesNome"),
                m.group("ano") != null ? m.group("ano") : m.group("ano2"), referencia);
        if (data == null) return null;

        String descricao = m.group("desc");
        // compras internacionais: "LOJA US$ 10,00 52,30" -> tira o valor em moeda estrangeira da descrição
        Matcher v;
        while ((v = VALOR_NO_FIM.matcher(descricao)).find()) descricao = descricao.substring(0, v.start());
        descricao = MOEDA_NO_FIM.matcher(descricao).replaceAll("");

        int atual = 0, total = 0;
        for (Pattern p : PARCELA) {
            Matcher pm = p.matcher(descricao + " ");
            if (pm.find()) {
                int a = Integer.parseInt(pm.group(1)), t = Integer.parseInt(pm.group(2));
                if (t >= 2 && a >= 1 && a <= t) {
                    atual = a;
                    total = t;
                    descricao = ((descricao + " ").substring(0, pm.start()) + " " + (descricao + " ").substring(pm.end()))
                            .replaceAll("\\s+", " ").trim();
                    break;
                }
            }
        }

        descricao = FINAL_CARTAO.matcher(descricao).replaceFirst("");
        descricao = descricao.replaceAll("^[-*•\\s]+|[-*•\\s]+$", "");
        if (descricao.length() < 2 || IGNORAR.matcher(descricao).find()) return null;

        BigDecimal valor = new BigDecimal(m.group("valor").replace(".", "").replace(",", "."));
        if (valor.signum() == 0) return null;
        boolean negativo = m.group("mais") != null || m.group("neg1") != null || m.group("neg2") != null
                || m.group("neg3") != null;
        boolean credito = negativo || CREDITO.matcher(descricao).find();

        return new Lancamento(data, descricaoOriginal(bruta, descricao), valor, atual, total,
                CategorizadorDespesa.sugerir(descricao), credito);
    }

    /** Devolve a descrição com acentos/maiúsculas como impressa, quando ela aparece na linha original. */
    private static String descricaoOriginal(String bruta, String normalizada) {
        String linha = bruta.replaceAll("\\s+", " ").trim();
        String linhaNorm = normalizar(linha);
        int i = linhaNorm.indexOf(normalizada);
        return i >= 0 && linhaNorm.length() == linha.length()
                ? linha.substring(i, i + normalizada.length())
                : normalizada;
    }

    private static LocalDate data(String dia, String mesNum, String mesNome, String ano, LocalDate referencia) {
        try {
            int d = Integer.parseInt(dia);
            int mes = mesNum != null ? Integer.parseInt(mesNum) : ("|" + MESES + "|").indexOf("|" + mesNome + "|") / 4 + 1;
            if (ano != null) {
                int a = Integer.parseInt(ano);
                return LocalDate.of(a < 100 ? 2000 + a : a, mes, d);
            }
            // sem ano: o lançamento é anterior ao vencimento (dezembro numa fatura de janeiro = ano anterior)
            LocalDate dt = LocalDate.of(referencia.getYear(), mes, d);
            return dt.isAfter(referencia.plusDays(10)) ? dt.minusYears(1) : dt;
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private static LocalDate vencimento(String texto) {
        Matcher m = VENCIMENTO.matcher(texto);
        while (m.find()) {
            try {
                int a = Integer.parseInt(m.group(4));
                int mes = m.group(2) != null ? Integer.parseInt(m.group(2)) : ("|" + MESES + "|").indexOf("|" + m.group(3) + "|") / 4 + 1;
                return LocalDate.of(a < 100 ? 2000 + a : a, mes, Integer.parseInt(m.group(1)));
            } catch (DateTimeException ignored) {
                // tenta a próxima ocorrência
            }
        }
        return null;
    }

    private static BigDecimal valor(String texto, Pattern[] padroes) {
        for (Pattern p : padroes) {
            Matcher m = p.matcher(texto);
            if (m.find()) return new BigDecimal(m.group(1).replace(".", "").replace(",", "."));
        }
        return null;
    }

    private static String categoriaDoBanco(String c) {
        return switch (c) {
            case "SUPERMERCADO", "SUPERMERCADOS", "RESTAURANTE", "RESTAURANTES", "ALIMENTACAO" -> "Alimentação";
            case "SAUDE", "FARMACIA" -> "Saúde";
            case "VEICULOS", "TRANSPORTE" -> "Transporte";
            case "VESTUARIO" -> "Vestuário";
            case "EDUCACAO" -> "Educação";
            case "SERVICOS" -> "Contas & Serviços";
            case "CASA", "MORADIA", "CONSTRUCAO" -> "Moradia";
            default -> "Lazer"; // viagem, turismo, entretenimento, hobby, lazer
        };
    }

    private static String emissor(String texto) {
        java.util.Map<String, Integer> citacoes = new java.util.HashMap<>();
        for (String[] e : EMISSORES) {
            int n = 0;
            for (int i = texto.indexOf(e[0]); i >= 0; i = texto.indexOf(e[0], i + 1)) n++;
            if (n > 0) citacoes.merge(e[1], n, Integer::sum);
        }
        return citacoes.entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse(null);
    }

    /** Maiúsculas, sem acentos, sem o sinal de menos tipográfico. Mantém o mesmo tamanho na maioria dos casos. */
    static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('−', '-').replace('–', '-').replace(' ', ' ')
                .toUpperCase(Locale.ROOT);
    }
}
