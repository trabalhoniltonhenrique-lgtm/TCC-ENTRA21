-- CasaCapital — acesso dos filhos (perfil dependente), mesada e importação de nota fiscal (NFC-e).
--
-- usuario.perfil: RESPONSAVEL vê tudo; DEPENDENTE só vê a própria mesada, as próprias tarefas e a lista
-- de compras. O login do dependente fica ligado a um membro da família (apagar o membro apaga o login).
-- No dependente, a coluna email guarda o nome de usuário escolhido pelos pais (não precisa ser e-mail).

ALTER TABLE usuario ADD COLUMN perfil VARCHAR(20) NOT NULL DEFAULT 'RESPONSAVEL';
ALTER TABLE usuario ADD COLUMN membro_id BIGINT NULL;
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_membro FOREIGN KEY (membro_id) REFERENCES membro_familia(id) ON DELETE CASCADE;
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_membro UNIQUE (membro_id);

-- Mesada de um membro: valor creditado todo mês no dia escolhido, e um objetivo opcional
CREATE TABLE mesada (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id      BIGINT NOT NULL,
  membro_id       BIGINT NOT NULL,
  valor           DECIMAL(12,2) NOT NULL,
  dia_pagamento   INT NOT NULL,
  ativa           BOOLEAN NOT NULL DEFAULT TRUE,
  inicio          DATE NOT NULL,
  meta_nome       VARCHAR(120),
  meta_valor      DECIMAL(12,2),
  CONSTRAINT fk_mesada_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT fk_mesada_membro FOREIGN KEY (membro_id) REFERENCES membro_familia(id) ON DELETE CASCADE,
  CONSTRAINT uk_mesada_membro UNIQUE (membro_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Entradas (MESADA automática, EXTRA dada pelos pais) e saídas (GASTO) da mesada.
-- referencia (AAAA-MM) só nas entradas automáticas: garante uma mesada por mês.
CREATE TABLE mesada_movimento (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id   BIGINT NOT NULL,
  membro_id    BIGINT NOT NULL,
  tipo         VARCHAR(10) NOT NULL,
  valor        DECIMAL(12,2) NOT NULL,
  descricao    VARCHAR(160) NOT NULL,
  data         DATE NOT NULL,
  referencia   CHAR(7),
  criado_por   VARCHAR(120),
  CONSTRAINT fk_mesada_mov_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT fk_mesada_mov_membro FOREIGN KEY (membro_id) REFERENCES membro_familia(id) ON DELETE CASCADE,
  CONSTRAINT uk_mesada_mov_referencia UNIQUE (membro_id, referencia)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_mesada_mov_membro_data ON mesada_movimento(membro_id, data);

-- Notas fiscais do mercado já importadas pelo QR code (pela chave de acesso), para avisar se repetir
CREATE TABLE nfce_importada (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id      BIGINT NOT NULL,
  chave           CHAR(44) NOT NULL,
  emitente        VARCHAR(160),
  data_emissao    DATE,
  total           DECIMAL(12,2),
  itens           INT NOT NULL,
  importada_em    DATETIME NOT NULL,
  CONSTRAINT fk_nfce_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT uk_nfce_chave UNIQUE (familia_id, chave)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
