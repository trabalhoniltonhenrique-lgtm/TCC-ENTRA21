-- CasaCapital — cartão (ou banco) da conta, ex.: "Itaú", "Nubank". Preenchido ao importar a fatura do cartão
-- ou à mão no cadastro; usado na aba "Cartões" de Contas a Pagar. NULL = conta sem cartão.

ALTER TABLE conta ADD COLUMN cartao VARCHAR(40) NULL AFTER categoria_despesa;
