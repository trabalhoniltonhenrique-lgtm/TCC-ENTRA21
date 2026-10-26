-- CasaCapital — rentabilidade informada de cada investimento, usada só para projeções (rendimento por mês, em 12 meses
-- e no vencimento). tipo_taxa: MES (% ao mês), ANO (% ao ano) ou CDI (% do CDI). NULL = sem projeção.
-- familia.cdi_anual: CDI em % ao ano, informado pela família, para os investimentos em "% do CDI".

ALTER TABLE investimento ADD COLUMN taxa DECIMAL(9,4) NULL AFTER valor_atual;
ALTER TABLE investimento ADD COLUMN tipo_taxa VARCHAR(10) NULL AFTER taxa;
ALTER TABLE familia ADD COLUMN cdi_anual DECIMAL(6,3) NULL;
