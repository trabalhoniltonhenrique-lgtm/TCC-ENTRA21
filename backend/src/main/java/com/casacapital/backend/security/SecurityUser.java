package com.casacapital.backend.security;

import com.casacapital.backend.familia.PlanoFamilia;

/**
 * Principal autenticado, derivado das claims do JWT em cada requisição.
 * familiaId é a única fonte de verdade para escopar consultas — nunca vem do corpo/query da requisição.
 */
public record SecurityUser(Long usuarioId, Long familiaId, String email, PlanoFamilia plano) {
}
