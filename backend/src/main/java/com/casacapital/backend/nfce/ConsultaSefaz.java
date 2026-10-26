package com.casacapital.backend.nfce;

import com.casacapital.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Abre a página de consulta da nota (o endereço que está no QR code), que fica no site da Secretaria da
 * Fazenda do estado. Só acessa endereços de governo (.gov.br) e nunca endereços da rede interna, para o
 * QR code de um papel qualquer não conseguir fazer o servidor acessar outra coisa.
 */
@Component
class ConsultaSefaz {

    private static final int LIMITE_BYTES = 3 * 1024 * 1024;
    private static final int MAX_REDIRECIONAMENTOS = 4;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER) // cada redirecionamento passa pela validação de novo
            .build();

    /** Baixa o HTML da consulta. Devolve o conteúdo e o endereço final (depois de redirecionamentos). */
    Pagina baixar(String endereco) {
        URI uri = validar(endereco);
        try {
            for (int i = 0; i <= MAX_REDIRECIONAMENTOS; i++) {
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(25))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CasaCapital/1.0")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "pt-BR,pt;q=0.9")
                        .GET().build();
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                int status = resp.statusCode();
                if (status >= 300 && status < 400) {
                    resp.body().close();
                    String destino = resp.headers().firstValue("Location").orElseThrow(() -> erroConsulta(null));
                    uri = validar(uri.resolve(normalizar(destino)).toString());
                    continue;
                }
                try (InputStream corpo = resp.body()) {
                    if (status != 200) throw erroConsulta("a SEFAZ respondeu com o código " + status);
                    byte[] html = lerLimitado(corpo);
                    if (pedeVerificacaoHumana(uri.toString(), html)) throw new VerificacaoHumanaException();
                    return new Pagina(html, uri.toString(), resp.headers().firstValue("Content-Type").orElse(""));
                }
            }
            throw erroConsulta("redirecionamentos demais");
        } catch (IOException e) {
            throw erroConsulta(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw erroConsulta(null);
        }
    }

    /**
     * Alguns estados (SC, por exemplo) mostram uma verificação "não sou um robô" antes da nota. O sistema não
     * tenta passar por ela: a pessoa abre a nota no navegador dela e cola o texto da página.
     */
    static boolean pedeVerificacaoHumana(String endereco, byte[] html) {
        if (endereco.toLowerCase(Locale.ROOT).contains("securityverify")) return true;
        String inicio = new String(html, 0, Math.min(html.length, 200_000), java.nio.charset.StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        return inicio.contains("cf-turnstile") || inicio.contains("g-recaptcha") || inicio.contains("h-captcha")
                || inicio.contains("hcaptcha.com");
    }

    /** O link do QR code costuma ter "|" e às vezes espaços, que o Java não aceita num endereço. */
    static String normalizar(String endereco) {
        return endereco.trim().replace("|", "%7C").replace(" ", "%20");
    }

    /** Aceita só http(s) em domínio .gov.br que resolve para endereço público. */
    static URI validar(String endereco) {
        URI uri;
        try {
            uri = new URI(normalizar(endereco));
        } catch (URISyntaxException | NullPointerException e) {
            throw naoEhNota();
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!(esquema.equals("https") || esquema.equals("http")) || !host.endsWith(".gov.br") || uri.getUserInfo() != null) {
            throw naoEhNota();
        }
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) throw naoEhNota();
        try {
            for (InetAddress ip : InetAddress.getAllByName(host)) {
                if (ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress()
                        || ip.isAnyLocalAddress() || ip.isMulticastAddress() || ehRedeInternaIpv6(ip)) {
                    throw naoEhNota();
                }
            }
        } catch (UnknownHostException e) {
            throw erroConsulta("não foi possível encontrar " + host + " (o computador está sem internet?)");
        }
        return uri;
    }

    private static boolean ehRedeInternaIpv6(InetAddress ip) {
        byte[] b = ip.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc; // fc00::/7
    }

    private static byte[] lerLimitado(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > LIMITE_BYTES) throw new IOException("página grande demais");
        }
        return out.toByteArray();
    }

    static ApiException naoEhNota() {
        return new ApiException(HttpStatus.BAD_REQUEST, "NFCE_LINK_INVALIDO",
                "Esse QR code não é de uma nota fiscal (NFC-e). O link da nota começa com o site da Fazenda do estado (.gov.br).");
    }

    private static ApiException erroConsulta(String detalhe) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "NFCE_CONSULTA_FALHOU",
                "Não foi possível abrir a nota no site da Fazenda" + (detalhe != null ? " (" + detalhe + ")" : "")
                        + ". Tente de novo em alguns minutos — às vezes o site da SEFAZ fica fora do ar.");
    }

    record Pagina(byte[] html, String endereco, String contentType) {
    }

    /** O site da Fazenda pediu a verificação "não sou um robô". */
    static class VerificacaoHumanaException extends RuntimeException {
        VerificacaoHumanaException() {
            super("verificação humana");
        }
    }
}
