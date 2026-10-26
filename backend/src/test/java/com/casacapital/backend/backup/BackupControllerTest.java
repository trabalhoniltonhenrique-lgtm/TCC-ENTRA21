package com.casacapital.backend.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupControllerTest {

    @Test
    void semPastaConfiguradaInformaQueNaoHaBackupAutomatico() throws Exception {
        assertFalse(new BackupController("").status().configurado());
    }

    @Test
    void mostraOUltimoBackupEOResultadoDaUltimaTentativa(@TempDir Path pasta) throws Exception {
        Files.writeString(pasta.resolve("banco-casacapital-2026-10-24_1200.sql"), "-- Dump completed");
        Files.writeString(pasta.resolve("banco-casacapital-2026-10-25_1200.sql"), "x".repeat(4096));
        Files.writeString(pasta.resolve("mysql.cnf"), "[client]");
        // formato gravado pelo backup.ps1 (ConvertTo-Json do PowerShell)
        Files.writeString(pasta.resolve("ultimo-backup.json"), """
                {
                    "ok":  false,
                    "data":  "2026-10-26T12:00:03",
                    "arquivo":  null,
                    "mensagem":  "mysqldump terminou com codigo 2."
                }""");

        BackupStatusResponse s = new BackupController(pasta.toString()).status();

        assertTrue(s.configurado());
        assertEquals(2, s.quantidade()); // o mysql.cnf e o json não contam
        assertEquals("banco-casacapital-2026-10-25_1200.sql", s.ultimoArquivo());
        assertEquals(4, s.tamanhoKb());
        assertFalse(s.ultimaTentativaOk());
        assertEquals("mysqldump terminou com codigo 2.", s.mensagem());
    }
}
