-- CasaCapital — tokens de redefinição de senha ("esqueci minha senha")

CREATE TABLE password_reset_token (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id   BIGINT NOT NULL,
  token        VARCHAR(64) NOT NULL,
  expires_at   DATETIME NOT NULL,
  used_at      DATETIME NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_password_reset_token_token UNIQUE (token),
  CONSTRAINT fk_password_reset_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_password_reset_token_usuario ON password_reset_token(usuario_id);
