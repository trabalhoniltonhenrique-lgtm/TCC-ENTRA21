-- CasaCapital — estabelecimentos que a família marcou como "não é assinatura" no painel de assinaturas
-- (detectadas automaticamente pelas despesas que se repetem todo mês). chave = estabelecimento normalizado.

CREATE TABLE assinatura_ignorada (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id  BIGINT NOT NULL,
  chave       VARCHAR(40) NOT NULL,
  CONSTRAINT fk_assinatura_ignorada_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT uk_assinatura_ignorada UNIQUE (familia_id, chave)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
