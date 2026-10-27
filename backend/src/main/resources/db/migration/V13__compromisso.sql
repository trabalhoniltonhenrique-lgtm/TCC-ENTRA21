-- CasaCapital — compromissos agendados (separados das tarefas do dia a dia), com aviso sonoro e visual
-- antecedencia_min antes do horário. aviso_dispensado = alguém clicou "Ciente"; adiado_ate = "lembrar depois".

CREATE TABLE compromisso (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id        BIGINT NOT NULL,
  membro_id         BIGINT NULL,
  titulo            VARCHAR(160) NOT NULL,
  inicio            DATETIME NOT NULL,
  local             VARCHAR(160),
  obs               TEXT,
  antecedencia_min  INT NOT NULL DEFAULT 15,
  aviso_dispensado  BOOLEAN NOT NULL DEFAULT FALSE,
  adiado_ate        DATETIME NULL,
  CONSTRAINT fk_compromisso_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT fk_compromisso_membro FOREIGN KEY (membro_id) REFERENCES membro_familia(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_compromisso_familia_inicio ON compromisso(familia_id, inicio);
