package com.casacapital.backend.backup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Mostra em Configurações quando foi o último backup diário (pasta definida pelo instalador em BACKUP_DIR). */
@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private static final Pattern OK = Pattern.compile("\"ok\"\\s*:\\s*(true|false)");
    private static final Pattern MENSAGEM = Pattern.compile("\"mensagem\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final String pasta;

    public BackupController(@Value("${casacapital.backup.dir:}") String pasta) {
        this.pasta = pasta == null ? "" : pasta.trim();
    }

    @GetMapping("/status")
    public BackupStatusResponse status() throws IOException {
        if (pasta.isEmpty() || !Files.isDirectory(Path.of(pasta))) {
            return new BackupStatusResponse(false, 0, null, null, 0, null, null);
        }
        List<Path> backups;
        try (Stream<Path> s = Files.list(Path.of(pasta))) {
            backups = s.filter(p -> p.getFileName().toString().matches("banco-casacapital-.*\\.sql"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).toList();
        }
        String nome = null;
        LocalDateTime em = null;
        long kb = 0;
        if (!backups.isEmpty()) {
            Path ultimo = backups.get(0);
            nome = ultimo.getFileName().toString();
            em = LocalDateTime.ofInstant(Files.getLastModifiedTime(ultimo).toInstant(), ZoneId.systemDefault());
            kb = Math.round(Files.size(ultimo) / 1024.0);
        }

        // ultimo-backup.json é escrito pelo backup.ps1 a cada tentativa (inclusive as que falham)
        Boolean ok = null;
        String mensagem = null;
        Path status = Path.of(pasta, "ultimo-backup.json");
        if (Files.isRegularFile(status)) {
            String json = Files.readString(status, StandardCharsets.UTF_8);
            Matcher m = OK.matcher(json);
            if (m.find()) ok = Boolean.parseBoolean(m.group(1));
            Matcher msg = MENSAGEM.matcher(json);
            if (msg.find()) mensagem = msg.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return new BackupStatusResponse(true, backups.size(), nome, em, kb, ok, mensagem);
    }
}
