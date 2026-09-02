-- CasaCapital — schema inicial
-- Espelha o modelo de dados que hoje vive em localStorage (ver plano em
-- C:\Users\nhenr\.claude\plans\mossy-scribbling-origami.md), agora normalizado
-- e escopado por família (multi-tenant).

SET default_storage_engine = INNODB;

-- ── Família (tenant) e configurações ────────────────────────────────
CREATE TABLE familia (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  nome_familia        VARCHAR(120) NOT NULL,
  cidade              VARCHAR(120),
  responsavel         VARCHAR(120),
  plano               ENUM('ESSENCIAL','PREMIUM') NOT NULL DEFAULT 'ESSENCIAL',
  moeda               VARCHAR(5)  NOT NULL DEFAULT 'R$',
  separador_decimal   VARCHAR(1)  NOT NULL DEFAULT ',',
  dia_fechamento      INT         NOT NULL DEFAULT 1,
  mostrar_saldo       BOOLEAN     NOT NULL DEFAULT TRUE,
  alerta_contas       BOOLEAN     NOT NULL DEFAULT TRUE,
  confirmar_exclusao  BOOLEAN     NOT NULL DEFAULT TRUE,
  agrupar_cat         BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Usuários (contas de login) ──────────────────────────────────────
CREATE TABLE usuario (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id   BIGINT NOT NULL,
  nome         VARCHAR(120) NOT NULL,
  email        VARCHAR(180) NOT NULL,
  senha_hash   VARCHAR(60)  NOT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_usuario_email UNIQUE (email),
  CONSTRAINT fk_usuario_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_usuario_familia ON usuario(familia_id);

-- ── Membros da família (não são login, apenas pessoas p/ atribuir gastos/tarefas) ──
CREATE TABLE membro_familia (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id   BIGINT NOT NULL,
  nome         VARCHAR(120) NOT NULL,
  parentesco   VARCHAR(60),
  nascimento   DATE,
  renda        DECIMAL(12,2),
  obs          TEXT,
  cor          VARCHAR(7),
  CONSTRAINT fk_membro_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_membro_familia ON membro_familia(familia_id);

-- ── Receitas ─────────────────────────────────────────────────────────
CREATE TABLE receita (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id   BIGINT NOT NULL,
  membro_id    BIGINT,
  nome         VARCHAR(160) NOT NULL,
  valor        DECIMAL(12,2) NOT NULL,
  categoria    VARCHAR(40) NOT NULL,
  data         DATE NOT NULL,
  CONSTRAINT fk_receita_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT fk_receita_membro  FOREIGN KEY (membro_id)  REFERENCES membro_familia(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_receita_familia_data ON receita(familia_id, data);

-- ── Contas a pagar (precisa existir antes de despesa, que a referencia) ──
CREATE TABLE conta (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id        BIGINT NOT NULL,
  descricao         VARCHAR(160) NOT NULL,
  categoria         VARCHAR(40) NOT NULL,
  valor             DECIMAL(12,2) NOT NULL,
  vencimento        DATE NOT NULL,
  pago              BOOLEAN NOT NULL DEFAULT FALSE,
  data_pagamento    DATE,
  recorrencia       ENUM('UNICA','MENSAL','ANUAL','PARCELADA') NOT NULL DEFAULT 'UNICA',
  obs               TEXT,
  grupo_parcela_id  BIGINT,
  parcela_num       INT,
  parcela_total     INT,
  CONSTRAINT fk_conta_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_conta_familia_vencimento ON conta(familia_id, vencimento);
CREATE INDEX idx_conta_grupo_parcela ON conta(grupo_parcela_id);

-- ── Despesas ─────────────────────────────────────────────────────────
CREATE TABLE despesa (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id       BIGINT NOT NULL,
  membro_id        BIGINT,
  nome             VARCHAR(160) NOT NULL,
  valor            DECIMAL(12,2) NOT NULL,
  categoria        VARCHAR(40) NOT NULL,
  data             DATE NOT NULL,
  origem_conta_id  BIGINT,
  origem_conta     BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_despesa_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT fk_despesa_membro  FOREIGN KEY (membro_id)  REFERENCES membro_familia(id) ON DELETE SET NULL,
  CONSTRAINT fk_despesa_conta   FOREIGN KEY (origem_conta_id) REFERENCES conta(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_despesa_familia_data ON despesa(familia_id, data);
CREATE INDEX idx_despesa_origem_conta ON despesa(origem_conta_id);

-- ── Lista de compras ─────────────────────────────────────────────────
CREATE TABLE compra (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id   BIGINT NOT NULL,
  nome         VARCHAR(160) NOT NULL,
  qtd          INT NOT NULL DEFAULT 1,
  categoria    VARCHAR(40) NOT NULL,
  frequencia   ENUM('DIARIA','SEMANAL','QUINZENAL','MENSAL','SEM_FREQUENCIA') NOT NULL DEFAULT 'SEM_FREQUENCIA',
  CONSTRAINT fk_compra_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_compra_familia ON compra(familia_id);

-- ── Dispensa (estoque doméstico) ─────────────────────────────────────
CREATE TABLE item_dispensa (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id         BIGINT NOT NULL,
  nome               VARCHAR(160) NOT NULL,
  qtd                INT NOT NULL DEFAULT 0,
  categoria          VARCHAR(40) NOT NULL,
  data_entrada       DATE NOT NULL,
  data_atualizacao   DATE NOT NULL,
  CONSTRAINT fk_dispensa_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_dispensa_familia ON item_dispensa(familia_id);

-- ── Histórico de preços (uma linha por observação de preço) ──────────
CREATE TABLE historico_preco (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id      BIGINT NOT NULL,
  produto_chave   VARCHAR(180) NOT NULL,
  data            DATE NOT NULL,
  preco           DECIMAL(10,2) NOT NULL,
  CONSTRAINT fk_historico_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_historico_familia_produto ON historico_preco(familia_id, produto_chave, data);

-- ── Tarefas ──────────────────────────────────────────────────────────
CREATE TABLE tarefa (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id   BIGINT NOT NULL,
  membro_id    BIGINT,
  nome         VARCHAR(160) NOT NULL,
  prazo        DATE,
  concluida    BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_tarefa_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE,
  CONSTRAINT fk_tarefa_membro  FOREIGN KEY (membro_id)  REFERENCES membro_familia(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_tarefa_familia ON tarefa(familia_id);

-- ── Alertas de limite por categoria (Premium) ─────────────────────────
CREATE TABLE alerta_limite (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id   BIGINT NOT NULL,
  categoria    VARCHAR(40) NOT NULL,
  limite       DECIMAL(12,2) NOT NULL,
  CONSTRAINT uq_alerta_familia_categoria UNIQUE (familia_id, categoria),
  CONSTRAINT fk_alerta_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Orçamento mensal por categoria ─────────────────────────────────────
CREATE TABLE orcamento_mensal (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id   BIGINT NOT NULL,
  mes_ano      CHAR(7) NOT NULL,
  categoria    VARCHAR(40) NOT NULL,
  limite       DECIMAL(12,2) NOT NULL,
  CONSTRAINT uq_orcamento_familia_mes_categoria UNIQUE (familia_id, mes_ano, categoria),
  CONSTRAINT fk_orcamento_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Metas de economia + aportes ─────────────────────────────────────────
CREATE TABLE meta (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  familia_id   BIGINT NOT NULL,
  nome         VARCHAR(160) NOT NULL,
  valor_alvo   DECIMAL(12,2) NOT NULL,
  prazo        DATE,
  concluida    BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_meta_familia FOREIGN KEY (familia_id) REFERENCES familia(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_meta_familia ON meta(familia_id);

CREATE TABLE meta_aporte (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  meta_id      BIGINT NOT NULL,
  valor        DECIMAL(12,2) NOT NULL,
  data         DATE NOT NULL,
  CONSTRAINT fk_aporte_meta FOREIGN KEY (meta_id) REFERENCES meta(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_aporte_meta ON meta_aporte(meta_id);
