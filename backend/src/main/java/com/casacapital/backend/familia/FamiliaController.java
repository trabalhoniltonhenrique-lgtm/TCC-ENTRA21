package com.casacapital.backend.familia;

import com.casacapital.backend.familia.dto.AlterarPlanoRequest;
import com.casacapital.backend.familia.dto.FamiliaResponse;
import com.casacapital.backend.familia.dto.FamiliaUpdateRequest;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/familia")
public class FamiliaController {

    private final FamiliaService familiaService;
    private final FamiliaDataService familiaDataService;
    private final FamiliaFotoService familiaFotoService;

    public FamiliaController(FamiliaService familiaService, FamiliaDataService familiaDataService,
                             FamiliaFotoService familiaFotoService) {
        this.familiaService = familiaService;
        this.familiaDataService = familiaDataService;
        this.familiaFotoService = familiaFotoService;
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

    @PutMapping("/plano")
    public FamiliaResponse alterarPlano(@AuthenticationPrincipal SecurityUser user,
                                         @Valid @RequestBody AlterarPlanoRequest request) {
        return FamiliaResponse.from(familiaService.alterarPlano(user.familiaId(), request.plano()));
    }

    /** A imagem da foto da família (JPEG). 404 quando a família não tem foto. */
    @GetMapping("/foto")
    public ResponseEntity<byte[]> foto(@AuthenticationPrincipal SecurityUser user) {
        FamiliaFoto foto = familiaFotoService.buscar(user.familiaId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.getTipo()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(foto.getConteudo());
    }

    /** Troca a foto da família (campo "foto" do formulário); devolve a família com a nova versão da foto. */
    @PostMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FamiliaResponse enviarFoto(@AuthenticationPrincipal SecurityUser user,
                                      @RequestParam("foto") MultipartFile foto) throws IOException {
        return FamiliaResponse.from(familiaFotoService.salvar(user.familiaId(), foto.getBytes()));
    }

    @DeleteMapping("/foto")
    public FamiliaResponse removerFoto(@AuthenticationPrincipal SecurityUser user) {
        return FamiliaResponse.from(familiaFotoService.remover(user.familiaId()));
    }

    @PostMapping("/apagar-dados")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarDados(@AuthenticationPrincipal SecurityUser user) {
        familiaDataService.apagarDados(user.familiaId());
    }
}
