package com.casacapital.backend.nfce;

import com.casacapital.backend.nfce.dto.NfceImportarRequest;
import com.casacapital.backend.nfce.dto.NfceImportarResponse;
import com.casacapital.backend.nfce.dto.NfceLeituraResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/nfce")
public class NfceController {

    private final NfceService service;
    private final NfceImportadaRepository importadas;

    public NfceController(NfceService service, NfceImportadaRepository importadas) {
        this.service = service;
        this.importadas = importadas;
    }

    /**
     * Lê a nota pela foto do QR code (campo "foto"), pelo link ("link") ou pelo texto copiado da página da nota
     * ("texto", com o link para a chave de acesso) e devolve para revisão — não grava nada.
     */
    @PostMapping(value = "/ler", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NfceLeituraResponse ler(@AuthenticationPrincipal SecurityUser user,
                                   @RequestParam(value = "foto", required = false) MultipartFile foto,
                                   @RequestParam(value = "link", required = false) String link,
                                   @RequestParam(value = "texto", required = false) String texto) throws IOException {
        return service.ler(user.familiaId(), foto != null ? foto.getBytes() : null, link, texto);
    }

    @PostMapping("/importar")
    public NfceImportarResponse importar(@AuthenticationPrincipal SecurityUser user,
                                         @Valid @RequestBody NfceImportarRequest req) {
        return service.importar(user.familiaId(), req);
    }

    /** Últimas notas importadas. */
    @GetMapping
    public List<Resumo> listar(@AuthenticationPrincipal SecurityUser user) {
        return importadas.findTop20ByFamiliaIdOrderByImportadaEmDesc(user.familiaId()).stream()
                .map(n -> new Resumo(n.getEmitente(), n.getDataEmissao(), n.getTotal(), n.getItens(), n.getImportadaEm()))
                .toList();
    }

    public record Resumo(String emitente, LocalDate dataEmissao, BigDecimal total, Integer itens, LocalDateTime importadaEm) {
    }
}
