package com.casacapital.backend.fatura;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.conta.dto.ContaResponse;
import com.casacapital.backend.fatura.dto.ContaConsumoImportRequest;
import com.casacapital.backend.fatura.dto.FaturaAnaliseResponse;
import com.casacapital.backend.fatura.dto.FaturaImportRequest;
import com.casacapital.backend.fatura.dto.FaturaImportResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/faturas")
public class FaturaController {

    private final FaturaService service;

    public FaturaController(FaturaService service) {
        this.service = service;
    }

    /** Lê a fatura em PDF e devolve os lançamentos para revisão — ainda não grava nada. */
    @PostMapping(value = "/analisar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FaturaAnaliseResponse analisar(@AuthenticationPrincipal SecurityUser user,
                                          @RequestParam("arquivo") MultipartFile arquivo,
                                          @RequestParam(value = "senha", required = false) String senha) throws IOException {
        if (arquivo.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ARQUIVO_VAZIO", "Selecione o arquivo da fatura.");
        }
        return service.analisar(user.familiaId(), arquivo.getBytes(), senha);
    }

    /** Conta de luz/água: cadastra a conta a pagar (ou atualiza a conta em aberto do mesmo mês). */
    @PostMapping("/importar-conta")
    public ContaResponse importarConta(@AuthenticationPrincipal SecurityUser user,
                                       @Valid @RequestBody ContaConsumoImportRequest req) {
        return ContaResponse.from(service.importarContaConsumo(user.familiaId(), req));
    }

    @PostMapping("/importar")
    public FaturaImportResponse importar(@AuthenticationPrincipal SecurityUser user,
                                         @Valid @RequestBody FaturaImportRequest req) {
        return service.importar(user.familiaId(), req);
    }
}
