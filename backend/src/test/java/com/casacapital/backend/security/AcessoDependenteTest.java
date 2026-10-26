package com.casacapital.backend.security;

import com.casacapital.backend.familia.PlanoFamilia;
import com.casacapital.backend.membro.MembroFamilia;
import com.casacapital.backend.mesada.MesadaController;
import com.casacapital.backend.mesada.MesadaService;
import com.casacapital.backend.tarefa.Tarefa;
import com.casacapital.backend.tarefa.TarefaController;
import com.casacapital.backend.tarefa.TarefaService;
import com.casacapital.backend.usuario.PerfilUsuario;
import com.casacapital.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O login do filho só alcança a própria mesada, as próprias tarefas e a lista de compras. */
@WebMvcTest(controllers = {TarefaController.class, MesadaController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class AcessoDependenteTest {

    private static final long FAMILIA = 7L, FILHO = 20L, IRMA = 21L;

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean TarefaService tarefas;
    @MockitoBean MesadaService mesadas;
    @MockitoBean UsuarioRepository usuarios;

    private String filho, pai;

    @BeforeEach
    void setUp() {
        filho = "Bearer " + jwt.gerarToken(30L, FAMILIA, "joao", PlanoFamilia.ESSENCIAL, PerfilUsuario.DEPENDENTE, FILHO);
        pai = "Bearer " + jwt.gerarToken(1L, FAMILIA, "pai@casa.com", PlanoFamilia.ESSENCIAL);
        when(tarefas.listar(FAMILIA)).thenReturn(List.of(tarefa(1L, FILHO), tarefa(2L, IRMA), tarefa(3L, null)));
        when(tarefas.buscar(FAMILIA, 2L)).thenReturn(tarefa(2L, IRMA));
    }

    @Test
    void filhoNaoVeAsFinancasDaFamilia() throws Exception {
        mvc.perform(get("/api/despesas").header("Authorization", filho)).andExpect(status().isForbidden());
        mvc.perform(get("/api/receitas").header("Authorization", filho)).andExpect(status().isForbidden());
        mvc.perform(get("/api/mesada").header("Authorization", filho)).andExpect(status().isForbidden());
        mvc.perform(get("/api/mesada/21").header("Authorization", filho)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/tarefas/1").header("Authorization", filho)).andExpect(status().isForbidden());
        mvc.perform(get("/api/membros").header("Authorization", filho)).andExpect(status().isForbidden());
    }

    @Test
    void filhoVeSoAsProprias() throws Exception {
        mvc.perform(get("/api/tarefas").header("Authorization", filho))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/mesada/minha").header("Authorization", filho)).andExpect(status().isOk());
        verify(mesadas).detalhe(FAMILIA, FILHO);
    }

    @Test
    void filhoNaoConcluiTarefaDaIrma() throws Exception {
        mvc.perform(post("/api/tarefas/2/concluir").header("Authorization", filho)).andExpect(status().isNotFound());
        verify(tarefas, never()).concluir(FAMILIA, 2L);
    }

    @Test
    void paisVeemTudoMasNaoUsamAAreaDoFilho() throws Exception {
        mvc.perform(get("/api/tarefas").header("Authorization", pai))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/mesada").header("Authorization", pai)).andExpect(status().isOk());
        mvc.perform(get("/api/mesada/minha").header("Authorization", pai)).andExpect(status().isForbidden());
    }

    @Test
    void semLoginNaoEntra() throws Exception {
        mvc.perform(get("/api/tarefas")).andExpect(status().isUnauthorized());
    }

    private static Tarefa tarefa(Long id, Long membroId) {
        Tarefa t = new Tarefa();
        t.setId(id);
        t.setNome("Tarefa " + id);
        if (membroId != null) {
            MembroFamilia m = new MembroFamilia();
            m.setId(membroId);
            t.setMembro(m);
        }
        return t;
    }
}
