package com.casacapital.backend.security;

import com.casacapital.backend.familia.PlanoFamilia;
import com.casacapital.backend.usuario.PerfilUsuario;

/**
 * Principal autenticado, derivado das claims do JWT em cada requisição.
 * familiaId é a única fonte de verdade para escopar consultas — nunca vem do corpo/query da requisição.
 * No dependente, membroId é o membro da família dono do login (escopa mesada e tarefas).
 */
public record SecurityUser(Long usuarioId, Long familiaId, String email, PlanoFamilia plano,
                           PerfilUsuario perfil, Long membroId) {

    public SecurityUser(Long usuarioId, Long familiaId, String email, PlanoFamilia plano) {
        this(usuarioId, familiaId, email, plano, PerfilUsuario.RESPONSAVEL, null);
    }

    public boolean dependente() {
        return perfil == PerfilUsuario.DEPENDENTE;
    }
}
