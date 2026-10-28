package com.casacapital.backend.familia;

import com.casacapital.backend.security.JwtAuthenticationFilter;
import com.casacapital.backend.security.JwtService;
import com.casacapital.backend.security.SecurityConfig;
import com.casacapital.backend.usuario.PerfilUsuario;
import com.casacapital.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Foto da família: os pais trocam e removem; o filho só vê; sem login, nada. */
@WebMvcTest(controllers = FamiliaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class FamiliaFotoControllerTest {

    private static final long FAMILIA = 7L;
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean FamiliaService familiaService;
    @MockitoBean FamiliaDataService familiaDataService;
    @MockitoBean FamiliaFotoService fotos;
    @MockitoBean UsuarioRepository usuarios;

    private String filho, pai;
    private final MockMultipartFile arquivo = new MockMultipartFile("foto", "familia.jpg", "image/jpeg", JPEG);

    @BeforeEach
    void setUp() {
        filho = "Bearer " + jwt.gerarToken(30L, FAMILIA, "joao", PlanoFamilia.ESSENCIAL, PerfilUsuario.DEPENDENTE, 20L);
        pai = "Bearer " + jwt.gerarToken(1L, FAMILIA, "pai@casa.com", PlanoFamilia.ESSENCIAL);
        FamiliaFoto foto = new FamiliaFoto(FAMILIA);
        foto.setTipo("image/jpeg");
        foto.setConteudo(JPEG);
        when(fotos.buscar(FAMILIA)).thenReturn(foto);
        Familia familia = new Familia("Família Teste");
        familia.setFotoAtualizadaEm(LocalDateTime.of(2026, 10, 28, 14, 30, 5));
        when(fotos.salvar(anyLong(), any())).thenReturn(familia);
    }

    @Test
    void paisEnviamAFotoERecebemANovaVersao() throws Exception {
        mvc.perform(multipart("/api/familia/foto").file(arquivo).header("Authorization", pai))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoAtualizadaEm").value("2026-10-28T14:30:05"));
        verify(fotos).salvar(FAMILIA, JPEG);
    }

    @Test
    void todosDaFamiliaVeemAFotoSemCacheCompartilhado() throws Exception {
        for (String quem : new String[]{pai, filho}) {
            mvc.perform(get("/api/familia/foto").header("Authorization", quem))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/jpeg"))
                    .andExpect(content().bytes(JPEG))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        }
    }

    @Test
    void filhoNaoTrocaNemRemoveAFoto() throws Exception {
        mvc.perform(multipart("/api/familia/foto").file(arquivo).header("Authorization", filho))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/familia/foto").header("Authorization", filho)).andExpect(status().isForbidden());
        verify(fotos, never()).salvar(anyLong(), any());
        verify(fotos, never()).remover(anyLong());
    }

    @Test
    void semLoginNaoVeAFoto() throws Exception {
        mvc.perform(get("/api/familia/foto")).andExpect(status().isUnauthorized());
    }
}
