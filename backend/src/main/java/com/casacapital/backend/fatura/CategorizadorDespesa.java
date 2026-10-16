package com.casacapital.backend.fatura;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sugere a categoria de despesa pelo nome do estabelecimento impresso na fatura. */
final class CategorizadorDespesa {

    // Ordem importa: a primeira palavra encontrada decide (ex.: "UBER EATS" é Alimentação antes de "UBER").
    private static final Map<String, List<String>> PALAVRAS = new LinkedHashMap<>();

    static {
        // Lojas de tudo um pouco: vêm antes para "MERCADOLIVRE" não cair em Alimentação por causa de "MERCADO"
        PALAVRAS.put("Outros", List.of("MERCADOLIVRE", "MERCADO LIVRE", "MERCADOPAGO", "MERCADO PAGO", "SHOPEE",
                "ALIEXPRESS", "MAGALU", "MAGAZINE LUIZA", "AMERICANAS", "CASAS BAHIA", "PAYPAL"));
        PALAVRAS.put("Alimentação", List.of("IFOOD", "UBER EATS", "RAPPI", "ZE DELIVERY", "MERCADO", "SUPERMERC",
                "ATACAD", "ASSAI", "CARREFOUR", "EXTRA", "PAO DE ACUCAR", "COOPER ", "HORTIFRUTI", "SACOLAO", "PADARIA",
                "PANIFICADORA", "ACOUGUE", "RESTAURANTE", "LANCHONETE", "LANCHES", "PIZZA", "BURGER", "MCDONALD",
                "BK ", "SUBWAY", "HABIB", "OUTBACK", "CAFE", "CAFETERIA", "SORVETE", "DOCERIA", "BAR ", "EMPORIO",
                "FEIRA", "BISTRO", "CHURRASC", "SUSHI", "ACAI"));
        PALAVRAS.put("Transporte", List.of("UBER", "99APP", "99 TAXI", "99POP", "CABIFY", "POSTO", "COMBUST",
                "IPIRANGA", "SHELL", "PETROBRAS", " BR ", "ESTACION", "ESTAPAR", "SEM PARAR", "CONECTCAR", "VELOE",
                "PEDAGIO", "AUTOPASS", "METRO", "ONIBUS", "LATAM", "GOL LINHAS", "AZUL LINHAS", "AUTO PECAS",
                "OFICINA", "PNEU", "LAVA JATO", "DETRAN", "IPVA"));
        PALAVRAS.put("Saúde", List.of("DROGARIA", "DROGA", "FARMA", "PANVEL", "PAGUE MENOS", "RAIA", "DROGASIL",
                "HOSPITAL", "CLINICA", "LABORATORIO", "ODONTO", "DENTIST", "MEDIC", "OTICA", "PSICOLOG", "FISIOTER",
                "UNIMED", "AMIL", "HAPVIDA", "SULAMERICA SAUDE"));
        PALAVRAS.put("Educação", List.of("ESCOLA", "COLEGIO", "FACULDADE", "UNIVERSIDADE", "CURSO", "LIVRARIA",
                "PAPELARIA", "KALUNGA", "UDEMY", "ALURA", "COURSERA", "DUOLINGO", "SARAIVA"));
        PALAVRAS.put("Lazer", List.of("NETFLIX", "SPOTIFY", "DISNEY", "HBO", "MAX.COM", "PRIME VIDEO", "GLOBOPLAY",
                "DEEZER", "YOUTUBE", "STEAM", "PLAYSTATION", "XBOX", "NINTENDO", "CINEMA", "CINEMARK", "INGRESSO",
                "SYMPLA", "TEATRO", "SHOW", "PARQUE", "HOTEL", "POUSADA", "AIRBNB", "BOOKING", "DECOLAR", "VIAGEM",
                "CLUBE", "ACADEMIA", "SMART FIT", "BLUEFIT"));
        PALAVRAS.put("Vestuário", List.of("RENNER", "RIACHUELO", "C&A", "CEA ", "MARISA", "ZARA", "HERING",
                "CENTAURO", "NETSHOES", "DAFITI", "SHEIN", "CALCADOS", "SAPATARIA", "MODA", "BOUTIQUE", "ROUPA",
                "NIKE", "ADIDAS", "AREZZO", "VIVARA"));
        PALAVRAS.put("Contas & Serviços", List.of("ANUIDADE", "IOF", "JUROS", "MULTA", "ENCARGO", "TARIFA",
                "SEGURO", "VIVO", "CLARO", "TIM ", "OI ", "NET ", "SKY", "INTERNET", "TELEFON", "ENERGIA", "CEMIG",
                "ENEL", "LIGHT", "COPEL", "SABESP", "COPASA", "SANEAMENTO", "GOOGLE", "APPLE.COM", "MICROSOFT",
                "AMAZON PRIME", "ICLOUD", "DROPBOX"));
        PALAVRAS.put("Moradia", List.of("CONDOMINIO", "ALUGUEL", "IMOBILIARIA", "LEROY", "TELHANORTE",
                "MATERIAIS DE CONSTRUCAO", "MAT CONSTR", "CASA E CONSTRUCAO", "TOK STOK", "TOKSTOK", "ETNA",
                "CAMICADO", "MOVEIS", "ELETRO", "COLCHAO"));
    }

    private CategorizadorDespesa() {
    }

    /** descricaoNormalizada: maiúsculas, sem acento (ver LeitorFaturaPdf.normalizar). */
    static String sugerir(String descricaoNormalizada) {
        String texto = " " + descricaoNormalizada + " ";
        for (Map.Entry<String, List<String>> e : PALAVRAS.entrySet()) {
            for (String palavra : e.getValue()) {
                if (texto.contains(palavra)) return e.getKey();
            }
        }
        return "Outros";
    }
}
