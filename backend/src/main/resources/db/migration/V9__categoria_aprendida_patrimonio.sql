-- CasaCapital — (1) categoria que a família escolheu para cada estabelecimento ao importar faturas: nas próximas
-- faturas a categoria já vem preenchida. chave = estabelecimento normalizado (ex.: "SUPERMERCADO SCHUTZE").
-- (2) foto mensal do patrimônio (saldo + investimentos − parcelas a vencer), para o gráfico de evolução.

CREATE TABLE categoria_aprendida (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id    BIGINT NOT NULL,
  chave         VARCHAR(40) NOT NULL,
  categoria     VARCHAR(40) NOT NULL,
  atualizado_em DATE NOT NULL,
  CONSTRAINT fk_categoria_aprendida_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT uk_categoria_aprendida UNIQUE (familia_id, chave)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patrimonio_mensal (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id    BIGINT NOT NULL,
  mes           CHAR(7) NOT NULL,
  saldo         DECIMAL(14,2) NOT NULL,
  investimentos DECIMAL(14,2) NOT NULL,
  dividas       DECIMAL(14,2) NOT NULL,
  total         DECIMAL(14,2) NOT NULL,
  atualizado_em DATE NOT NULL,
  CONSTRAINT fk_patrimonio_mensal_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT uk_patrimonio_mensal UNIQUE (familia_id, mes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
