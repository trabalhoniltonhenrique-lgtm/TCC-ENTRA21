-- CasaCapital — comparação de preços entre mercados.
-- historico_preco passa a guardar em qual mercado foi a compra (vem da nota fiscal) e o código de barras
-- (GTIN/EAN) do produto, que identifica o mesmo produto mesmo quando cada mercado escreve o nome de um jeito.
-- Compras registradas à mão ficam sem mercado. nfce_importada guarda o CNPJ para lembrar o nome que a família
-- deu ao mercado na próxima nota.

ALTER TABLE historico_preco ADD COLUMN mercado VARCHAR(160) NULL;
ALTER TABLE historico_preco ADD COLUMN codigo_barras VARCHAR(14) NULL;
CREATE INDEX idx_historico_familia_mercado ON historico_preco(familia_id, mercado);

ALTER TABLE nfce_importada ADD COLUMN cnpj VARCHAR(18) NULL;
