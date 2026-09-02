package com.casacapital.backend.familia;

import com.casacapital.backend.familia.dto.FamiliaResponse;
import com.casacapital.backend.familia.dto.FamiliaUpdateRequest;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/familia")
public class FamiliaController {

    private final FamiliaService familiaService;
    private final FamiliaDataService familiaDataService;

    public FamiliaController(FamiliaService familiaService, FamiliaDataService familiaDataService) {
        this.familiaService = familiaService;
        this.familiaDataService = familiaDataService;
    }

    @GetMapping("/me")
    public FamiliaResponse me(@AuthenticationPrincipal SecurityUser user) {
        return FamiliaResponse.from(familiaService.buscar(user.familiaId()));
    }

    @PutMapping("/me")
    public FamiliaResponse atualizar(@AuthenticationPrincipal SecurityUser user,
                                      @Valid @RequestBody FamiliaUpdateRequest request) {
        return FamiliaResponse.from(familiaService.atualizar(user.familiaId(), request));
    }

    @PostMapping("/apagar-dados")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarDados(@AuthenticationPrincipal SecurityUser user) {
        familiaDataService.apagarDados(user.familiaId());
    }
}
