-- CasaCapital — fechamento do mês: a família pode receber por e-mail, todo dia 1, o resumo do mês anterior.
-- fechamento_enviado registra o mês já enviado, para não mandar duas vezes (o envio roda todo dia e na
-- inicialização, porque o computador pode estar desligado no dia 1).

ALTER TABLE familia ADD COLUMN fechamento_email BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE fechamento_enviado (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id  BIGINT NOT NULL,
  mes         CHAR(7) NOT NULL,
  enviado_em  DATETIME NOT NULL,
  CONSTRAINT fk_fechamento_enviado_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT uk_fechamento_enviado UNIQUE (familia_id, mes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
