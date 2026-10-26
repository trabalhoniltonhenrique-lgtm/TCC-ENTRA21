package com.casacapital.backend.backup;

import java.time.LocalDateTime;

/**
 * Situação do backup automático diário (feito pela tarefa agendada do Windows, não pelo sistema).
 * configurado = false quando o sistema não foi instalado pelo instalador (ex.: rodando em desenvolvimento).
 */
public record BackupStatusResponse(
        boolean configurado,
        int quantidade,
        String ultimoArquivo,
        LocalDateTime ultimoEm,
        long tamanhoKb,
        Boolean ultimaTentativaOk,
        String mensagem
) {
}
