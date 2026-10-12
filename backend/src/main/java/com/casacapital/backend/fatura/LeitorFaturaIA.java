package com.casacapital.backend.fatura;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaBase64PdfSource;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.Categorias;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.TreeSet;

/** Lê a fatura do cartão (PDF ou foto) com o Claude e devolve os lançamentos estruturados. */
@Component
public class LeitorFaturaIA {

    private static final Logger log = LoggerFactory.getLogger(LeitorFaturaIA.class);

    public enum TipoArquivo { PDF, JPEG, PNG, WEBP }

    public record FaturaExtraida(
            @JsonPropertyDescription("Banco ou emissor do cartão, ex.: Nubank, Itaú, Inter")
            String emissor,
            @JsonPropertyDescription("Data de vencimento da fatura no formato AAAA-MM-DD; string vazia se não aparecer")
            String vencimento,
            @JsonPropertyDescription("Valor total da fatura; 0 se não aparecer")
            double total,
            List<LancamentoExtraido> lancamentos
    ) {
    }

    public record LancamentoExtraido(
            @JsonPropertyDescription("Data do lançamento no formato AAAA-MM-DD")
            String data,
            @JsonPropertyDescription("Descrição como impressa na fatura, sem o indicador de parcela")
            String descricao,
            @JsonPropertyDescription("Valor cobrado nesta fatura (para parcelas, o valor da parcela), sempre positivo")
            double valor,
            @JsonPropertyDescription("Número da parcela cobrada nesta fatura; 0 se for compra à vista")
            int parcelaAtual,
            @JsonPropertyDescription("Total de parcelas da compra; 0 se for compra à vista")
            int parcelaTotal,
            @JsonPropertyDescription("Uma das categorias permitidas")
            String categoria,
            @JsonPropertyDescription("COMPRA para compras, tarifas, juros, IOF e anuidade; CREDITO para estornos, créditos e descontos")
            String tipo
    ) {
    }

    private final String apiKey;
    private final String modelo;
    private volatile AnthropicClient client;

    public LeitorFaturaIA(@Value("${casacapital.ia.anthropic-api-key:}") String apiKey,
                          @Value("${casacapital.ia.modelo:claude-opus-5-5}") String modelo) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelo = modelo;
    }

    public boolean disponivel() {
        return !apiKey.isEmpty();
    }

    public FaturaExtraida ler(byte[] arquivo, TipoArquivo tipo) {
        if (!disponivel()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IA_NAO_CONFIGURADA",
                    "A leitura de faturas ainda não foi configurada neste sistema (falta a chave ANTHROPIC_API_KEY).");
        }

        StructuredMessage<FaturaExtraida> resposta;
        try {
            resposta = client().beta().messages().create(montarParametros(arquivo, tipo));
        } catch (UnauthorizedException | PermissionDeniedException e) {
            log.warn("Leitura de fatura: chave da Anthropic recusada ({})", e.statusCode());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IA_CHAVE_INVALIDA",
                    "A chave da IA configurada no sistema foi recusada. Verifique a ANTHROPIC_API_KEY.");
        } catch (RateLimitException e) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "IA_OCUPADA",
                    "O serviço de leitura está ocupado. Tente de novo em alguns instantes.");
        } catch (AnthropicServiceException e) {
            log.warn("Leitura de fatura: erro da API ({}): {}", e.statusCode(), e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "IA_FALHOU",
                    "Não foi possível ler a fatura agora. Tente novamente.");
        } catch (AnthropicIoException e) {
            log.warn("Leitura de fatura: falha de conexão: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "IA_SEM_CONEXAO",
                    "Não foi possível conectar ao serviço de leitura. Verifique a internet do servidor.");
        }

        BetaStopReason motivo = resposta.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(motivo)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IA_RECUSOU",
                    "A IA não conseguiu processar este arquivo. Confira se é mesmo uma fatura de cartão.");
        }
        if (BetaStopReason.MAX_TOKENS.equals(motivo)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FATURA_GRANDE_DEMAIS",
                    "A fatura tem lançamentos demais para ler de uma vez. Envie as páginas em partes.");
        }

        return resposta.content().stream()
                .flatMap(bloco -> bloco.text().stream())
                .map(texto -> texto.text())
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_GATEWAY, "IA_SEM_RESPOSTA",
                        "A leitura da fatura não retornou resultado. Tente novamente."));
    }

    StructuredMessageCreateParams<FaturaExtraida> montarParametros(byte[] arquivo, TipoArquivo tipo) {
        String base64 = Base64.getEncoder().encodeToString(arquivo);
        BetaContentBlockParam documento = switch (tipo) {
            case PDF -> BetaContentBlockParam.ofDocument(BetaBase64PdfSource.builder().data(base64).build());
            case JPEG -> imagem(base64, BetaBase64ImageSource.MediaType.IMAGE_JPEG);
            case PNG -> imagem(base64, BetaBase64ImageSource.MediaType.IMAGE_PNG);
            case WEBP -> imagem(base64, BetaBase64ImageSource.MediaType.IMAGE_WEBP);
        };

        return MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(16000L)
                .outputConfig(FaturaExtraida.class, BetaOutputConfig.Effort.MEDIUM)
                .system(instrucoes())
                .addUserMessageOfBetaContentBlockParams(List.of(
                        documento,
                        BetaContentBlockParam.ofText("Extraia os lançamentos desta fatura.")))
                // Se a IA recusar o pedido, o próprio servidor da Anthropic tenta de novo com o modelo indicado.
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
    }

    private static BetaContentBlockParam imagem(String base64, BetaBase64ImageSource.MediaType tipo) {
        return BetaContentBlockParam.ofImage(BetaBase64ImageSource.builder().data(base64).mediaType(tipo).build());
    }

    private String instrucoes() {
        return """
                Você lê faturas de cartão de crédito brasileiras e extrai os lançamentos para um app de \
                finanças domésticas.

                Regras:
                - Liste cada lançamento cobrado nesta fatura, de todos os cartões (titular e adicionais).
                - Não inclua o pagamento da fatura anterior, saldo anterior, totais, subtotais nem limites.
                - Estornos, créditos e descontos entram com tipo CREDITO e valor positivo.
                - Juros, multa, IOF, anuidade e tarifas entram como COMPRA.
                - Parcelas aparecem como "03/10", "PARC 3/10", "Parcela 3 de 10" etc.: preencha parcelaAtual \
                e parcelaTotal e tire esse trecho da descrição. Compra à vista: os dois campos com 0.
                - Mantenha a descrição como impressa (ex.: "MERCADO EXTRA 123"), só sem o indicador de parcela.
                - Datas sem ano: deduza pelo período da fatura (compra de dezembro numa fatura de janeiro é do \
                ano anterior). Use AAAA-MM-DD.
                - Valores em reais como número (1.234,56 vira 1234.56). Compras em moeda estrangeira: use o \
                valor convertido em reais.
                - categoria: escolha uma destas pelo tipo de estabelecimento: %s. Na dúvida, "Outros".
                """.formatted(String.join(", ", new TreeSet<>(Categorias.DESPESA)));
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder()
                            .apiKey(apiKey)
                            .timeout(Duration.ofMinutes(5))
                            .build();
                }
            }
        }
        return client;
    }

    /** Converte "AAAA-MM-DD" vindo da IA; null se vazio ou inválido. */
    static LocalDate data(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
