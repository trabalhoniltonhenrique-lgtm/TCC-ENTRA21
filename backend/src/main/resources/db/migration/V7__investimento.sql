-- CasaCapital — carteira de investimentos da família (valores informados à mão).
-- meta_id: investimento ligado a uma meta; o valor atual dele conta no progresso da meta.

CREATE TABLE investimento (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id      BIGINT NOT NULL,
  meta_id         BIGINT NULL,
  nome            VARCHAR(120) NOT NULL,
  tipo            VARCHAR(30) NOT NULL,
  instituicao     VARCHAR(60) NULL,
  valor_aplicado  DECIMAL(14,2) NOT NULL,
  valor_atual     DECIMAL(14,2) NOT NULL,
  data_aplicacao  DATE NULL,
  vencimento      DATE NULL,
  obs             VARCHAR(255) NULL,
  atualizado_em   DATE NOT NULL,
  CONSTRAINT fk_investimento_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT fk_investimento_meta FOREIGN KEY (meta_id) REFERENCES meta(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_investimento_familia ON investimento(familia_id);
