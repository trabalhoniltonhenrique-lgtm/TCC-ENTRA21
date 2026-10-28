-- CasaCapital — foto da família (Perfil da Família, em Configurações).
-- A imagem fica numa tabela própria para não pesar no GET /familia/me, feito em toda tela;
-- familia.foto_atualizada_em diz se há foto e serve de "versão" para o navegador guardar uma cópia.
-- A foto é do perfil, não um dado financeiro: "Apagar todos os dados" não a remove.

CREATE TABLE familia_foto (
  familia_id  BIGINT PRIMARY KEY,
  tipo        VARCHAR(40) NOT NULL,
  conteudo    MEDIUMBLOB NOT NULL,
  CONSTRAINT fk_familia_foto_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE familia ADD COLUMN foto_atualizada_em DATETIME NULL;
