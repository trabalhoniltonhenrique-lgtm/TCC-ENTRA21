-- CasaCapital — categoria de despesa usada ao pagar a conta (parcelas importadas da fatura do cartão).
-- NULL = usa o mapeamento padrão da categoria da conta (ContaService.MAPA_CATEGORIA_DESPESA).

ALTER TABLE conta ADD COLUMN categoria_despesa VARCHAR(40) NULL AFTER categoria;
